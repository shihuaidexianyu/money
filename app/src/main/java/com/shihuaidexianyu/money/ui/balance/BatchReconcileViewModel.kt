package com.shihuaidexianyu.money.ui.balance

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.R
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.AccountKind
import com.shihuaidexianyu.money.domain.model.PortableSettings
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.repository.AccountReminderSettingsRepository
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.PortableSettingsRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.usecase.CalculateAccountBalancesUseCase
import com.shihuaidexianyu.money.domain.usecase.LedgerOperationIdFactory
import com.shihuaidexianyu.money.domain.usecase.UpdateBalanceUseCase
import com.shihuaidexianyu.money.domain.usecase.savedOperationId
import com.shihuaidexianyu.money.domain.time.ClockProvider
import com.shihuaidexianyu.money.ui.common.FormTerminalKind
import com.shihuaidexianyu.money.ui.common.PendingFormTerminal
import com.shihuaidexianyu.money.ui.common.PENDING_FORM_TERMINAL_KEY
import com.shihuaidexianyu.money.ui.common.pendingFormTerminal
import com.shihuaidexianyu.money.util.AccountStatusUtils
import com.shihuaidexianyu.money.util.DateTimeTextFormatter
import com.shihuaidexianyu.money.util.AmountFormatter
import com.shihuaidexianyu.money.util.AmountInputParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BatchReconcileAccountUiModel(
    val accountId: Long,
    val name: String,
    val systemBalance: Long,
    val lastBalanceUpdateAt: Long?,
    val isSelected: Boolean = false,
    val isFailed: Boolean = false,
    val isInvestment: Boolean = false,
    val actualBalanceText: String = "",
    val amountError: Boolean = false,
    val isInputLocked: Boolean = false,
) {
    val actualBalance: Long? get() = if (actualBalanceText.isBlank()) systemBalance
        else AmountInputParser.parseSignedToMinor(actualBalanceText)
    val delta: Long? get() = actualBalance?.let { actual ->
        runCatching { Math.subtractExact(actual, systemBalance) }.getOrNull()
    }
}

data class BatchReconcileUiState(
    val isLoading: Boolean = true,
    val loadErrorMessageRes: Int? = null,
    val settings: PortableSettings = PortableSettings(),
    val accounts: List<BatchReconcileAccountUiModel> = emptyList(),
    val isDirty: Boolean = false,
    val isSaving: Boolean = false,
    val pendingTerminal: PendingFormTerminal? = null,
    val confirmTimeMillis: Long? = null,
    val isRecalculating: Boolean = false,
    val isTimeLocked: Boolean = false,
) {
    val selectedCount: Int
        get() = accounts.count { it.isSelected }
}

data class BatchReconcileReview(
    val timestamp: Long?,
    val balances: Map<Long, Pair<Long, Long>>,
) : java.io.Serializable {
    val changedCount: Int get() = balances.count { it.value.first != it.value.second }
}

fun BatchReconcileUiState.review() = BatchReconcileReview(confirmTimeMillis,
    accounts.filter { it.isSelected }.associate { it.accountId to (it.systemBalance to requireNotNull(it.actualBalance)) })

sealed interface BatchReconcileEffect {
    data class ShowMessage(
        override val message: String,
        @param:androidx.annotation.StringRes override val messageRes: Int? = null,
    ) : BatchReconcileEffect, com.shihuaidexianyu.money.ui.common.UiEffect.HasMessage
}

class BatchReconcileViewModel(
    private val accountReminderSettingsRepository: AccountReminderSettingsRepository,
    private val accountRepository: AccountRepository,
    private val portableSettingsRepository: PortableSettingsRepository,
    private val transactionRepository: TransactionRepository,
    private val calculateAccountBalancesUseCase: CalculateAccountBalancesUseCase,
    private val updateBalanceUseCase: UpdateBalanceUseCase,
    private val savedStateHandle: SavedStateHandle,
    private val operationIdFactory: LedgerOperationIdFactory,
    private val clockProvider: ClockProvider,
) : ViewModel() {
    private var draft = savedStateHandle.get<BatchReconcileDraft>(DRAFT_KEY)
        // Pre-fill the confirmation timestamp so the page shows (and can edit) it before saving.
        ?: BatchReconcileDraft(
            occurredAtMillis = DateTimeTextFormatter.floorToMinute(clockProvider.nowMillis()),
        ).also { savedStateHandle[DRAFT_KEY] = it }
    private val _uiState = MutableStateFlow(
        BatchReconcileUiState(
            pendingTerminal = savedStateHandle[PENDING_FORM_TERMINAL_KEY],
        ),
    )
    val uiState: StateFlow<BatchReconcileUiState> = _uiState.asStateFlow()

    private val effects = MutableSharedFlow<BatchReconcileEffect>(extraBufferCapacity = 1)
    val effectFlow = effects.asSharedFlow()
    private var saveInFlight = false
    private var observationJob: Job? = null
    private val balanceTime = MutableStateFlow(draft.occurredAtMillis ?: DateTimeTextFormatter.floorToMinute(clockProvider.nowMillis()))

    init {
        observeAccounts()
    }

    fun retryLoad() {
        observeAccounts()
    }

    private fun observeAccounts() {
        observationJob?.cancel()
        _uiState.value = _uiState.value.copy(isLoading = true, loadErrorMessageRes = null)
        observationJob = viewModelScope.launch {
            try {
                combine(
                    accountRepository.observeOpenAccounts(),
                    accountReminderSettingsRepository.observeReminderConfigs(),
                    portableSettingsRepository.observe(),
                    transactionRepository.observeChangeVersion(),
                    balanceTime,
                ) { accounts, reminderConfigs, settings, _, timestamp ->
                    Triple(accounts, reminderConfigs, settings) to timestamp
                }.collectLatest { (inputs, timestamp) ->
                    _uiState.value = _uiState.value.copy(isRecalculating = true)
                    val (accounts, reminderConfigs, settings) = inputs
                    val items = buildItems(accounts, reminderConfigs, timestamp)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        settings = settings,
                        accounts = items,
                        isDirty = draft.isDirty,
                        loadErrorMessageRes = null,
                        confirmTimeMillis = timestamp,
                        isRecalculating = false,
                        isTimeLocked = draft.operationIds.isNotEmpty(),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                runCatching { android.util.Log.e("BatchReconcileViewModel", "Failed to load stale accounts", e) }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    loadErrorMessageRes = R.string.batch_reconcile_load_failed,
                )
            }
        }
    }

    fun toggleAccount(accountId: Long) {
        if (saveInFlight || _uiState.value.pendingTerminal != null) return
        val next = _uiState.value.copy(
            accounts = _uiState.value.accounts.map { account ->
                if (account.accountId == accountId) {
                    account.copy(isSelected = !account.isSelected, isFailed = false)
                } else {
                    account
                }
            },
            isDirty = true,
        )
        _uiState.value = next
        persistDraft(
            draft.copy(
                selectedAccountIds = next.accounts
                    .filter(BatchReconcileAccountUiModel::isSelected)
                    .map(BatchReconcileAccountUiModel::accountId),
                isDirty = true,
            ),
        )
    }

    fun setAllSelected(selected: Boolean) {
        if (saveInFlight || _uiState.value.pendingTerminal != null) return
        val next = _uiState.value.copy(
            accounts = _uiState.value.accounts.map { it.copy(isSelected = selected, isFailed = false) },
            isDirty = true,
        )
        _uiState.value = next
        persistDraft(
            draft.copy(
                selectedAccountIds = next.accounts
                    .filter(BatchReconcileAccountUiModel::isSelected)
                    .map(BatchReconcileAccountUiModel::accountId),
                isDirty = true,
            ),
        )
    }

    fun updateConfirmTime(value: Long) {
        if (saveInFlight || _uiState.value.pendingTerminal != null || draft.operationIds.isNotEmpty()) return
        val occurredAt = DateTimeTextFormatter.floorToMinute(value)
        if (occurredAt == draft.occurredAtMillis) return
        persistDraft(draft.copy(occurredAtMillis = occurredAt, timeEdited = true, isDirty = true))
        _uiState.value = _uiState.value.copy(confirmTimeMillis = occurredAt, isDirty = true, isRecalculating = true)
        balanceTime.value = occurredAt
    }

    fun updateActualBalance(accountId: Long, value: String) {
        if (saveInFlight || _uiState.value.pendingTerminal != null || accountId in draft.actualBalances) return
        val next = _uiState.value.accounts.map {
            if (it.accountId == accountId) it.copy(actualBalanceText = value, amountError = false, isSelected = true) else it
        }
        persistDraft(draft.copy(actualBalanceTexts = draft.actualBalanceTexts + (accountId to value),
            selectedAccountIds = next.filter { it.isSelected }.map { it.accountId }, isDirty = true))
        _uiState.value = _uiState.value.copy(accounts = next, isDirty = true)
    }

    fun saveSelected(review: BatchReconcileReview? = null) {
        if (saveInFlight || _uiState.value.pendingTerminal != null) return
        if (_uiState.value.isRecalculating || _uiState.value.isLoading) {
            if (review != null) effects.tryEmit(BatchReconcileEffect.ShowMessage("", messageRes = R.string.batch_reconcile_review_changed))
            return
        }
        saveInFlight = true
        val state = _uiState.value
        val selectedAccounts = state.accounts.filter { it.isSelected }
        if (review != null && (state.confirmTimeMillis != review.timestamp ||
                selectedAccounts.associate { it.accountId to (it.systemBalance to it.actualBalance) } != review.balances)) {
            saveInFlight = false
            effects.tryEmit(BatchReconcileEffect.ShowMessage("", messageRes = R.string.batch_reconcile_review_changed))
            return
        }
        if (selectedAccounts.isEmpty()) {
            saveInFlight = false
            effects.tryEmit(BatchReconcileEffect.ShowMessage("", messageRes = R.string.batch_select_at_least_one))
            return
        }
        if (selectedAccounts.any { it.actualBalance == null || it.delta == null }) {
            saveInFlight = false
            _uiState.value = state.copy(accounts = state.accounts.map {
                it.copy(amountError = it.isSelected && (it.actualBalance == null || it.delta == null))
            })
            effects.tryEmit(BatchReconcileEffect.ShowMessage("", messageRes = R.string.batch_invalid_amounts))
            return
        }

        _uiState.value = state.copy(isSaving = true)
        viewModelScope.launch {
            val occurredAt = draft.occurredAtMillis
                ?: DateTimeTextFormatter.floorToMinute(clockProvider.nowMillis()).also { timestamp ->
                    persistDraft(draft.copy(occurredAtMillis = timestamp))
                }
            val failedIds = mutableSetOf<Long>()
            val selectedIds = selectedAccounts.map(BatchReconcileAccountUiModel::accountId).toSet()
            var savedCount = 0
            selectedAccounts.forEach { account ->
                runCatching {
                    updateBalanceUseCase(
                        accountId = account.accountId,
                        actualBalance = actualBalanceFor(account.accountId, requireNotNull(account.actualBalance)),
                        occurredAt = occurredAt,
                        operationId = operationIdFor(account.accountId),
                    )
                }.onSuccess {
                    savedCount += 1
                }.onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    failedIds += account.accountId
                    runCatching {
                        android.util.Log.e(
                            "BatchReconcileViewModel",
                            "Failed to reconcile account ${account.accountId}",
                            throwable,
                        )
                    }
                }
            }

            if (failedIds.isEmpty()) {
                // Re-arm before the terminal: if the screen survives the ack, save must not
                // stay a silent no-op.
                saveInFlight = false
                setPendingTerminal(
                    pendingFormTerminal(
                        kind = FormTerminalKind.SAVED,
                        count = savedCount,
                    ),
                )
            } else {
                saveInFlight = false
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    isTimeLocked = true,
                    accounts = _uiState.value.accounts
                        .filterNot { it.accountId !in failedIds && it.accountId in selectedIds }
                        .map { it.copy(isFailed = it.accountId in failedIds, isSelected = it.accountId in failedIds) },
                )
                persistDraft(
                    draft.copy(
                        selectedAccountIds = failedIds.toList(),
                        isDirty = true,
                    ),
                )
                effects.emit(
                    BatchReconcileEffect.ShowMessage(
                        "",
                        messageRes = if (failedIds.size == selectedIds.size) {
                            R.string.batch_all_save_failed
                        } else {
                            R.string.batch_partial_save_failed
                        },
                    ),
                )
            }
        }
    }

    private fun operationIdFor(accountId: Long): String {
        return draft.operationIds[accountId] ?: savedOperationId(
            existing = null,
            factory = operationIdFactory,
        ).also { operationId ->
            persistDraft(draft.copy(operationIds = draft.operationIds + (accountId to operationId)))
        }
    }

    fun ackTerminal(token: String) {
        if (_uiState.value.pendingTerminal?.token != token) return
        savedStateHandle.remove<PendingFormTerminal>(PENDING_FORM_TERMINAL_KEY)
        _uiState.value = _uiState.value.copy(pendingTerminal = null)
    }

    private fun setPendingTerminal(terminal: PendingFormTerminal) {
        savedStateHandle[PENDING_FORM_TERMINAL_KEY] = terminal
        _uiState.value = _uiState.value.copy(isSaving = false, pendingTerminal = terminal)
    }

    private fun actualBalanceFor(accountId: Long, currentBalance: Long): Long {
        return draft.actualBalances[accountId]
            ?: currentBalance.also { actualBalance ->
                persistDraft(draft.copy(actualBalances = draft.actualBalances + (accountId to actualBalance)))
            }
    }

    private companion object {
        const val DRAFT_KEY = "batch_reconcile_draft"
    }

    private fun persistDraft(next: BatchReconcileDraft) {
        draft = next
        savedStateHandle[DRAFT_KEY] = next
    }

    private suspend fun buildItems(
        accounts: List<Account>,
        reminderConfigs: Map<Long, BalanceUpdateReminderConfig>,
        timestamp: Long,
    ): List<BatchReconcileAccountUiModel> = withContext(Dispatchers.Default) {
        val staleAccounts = accounts.filter { account ->
            AccountStatusUtils.isStale(
                account,
                reminderConfig = reminderConfigs[account.id] ?: BalanceUpdateReminderConfig(),
            )
        }
        val balances = calculateAccountBalancesUseCase(staleAccounts, timestamp)
        // Rebuilds are triggered by every ledger change — including the partial-save's own
        // successful writes. Carry the failure markers over, or the rebuild that follows a
        // partial failure erases them and the user sees "部分账户保存失败" with nothing marked.
        val failedIds = _uiState.value.accounts
            .filter(BatchReconcileAccountUiModel::isFailed)
            .mapTo(mutableSetOf(), BatchReconcileAccountUiModel::accountId)
        staleAccounts.map { account ->
            BatchReconcileAccountUiModel(
                accountId = account.id,
                name = account.name,
                systemBalance = balances[account.id] ?: account.initialBalance,
                lastBalanceUpdateAt = account.lastBalanceUpdateAt,
                isSelected = account.id in draft.selectedAccountIds,
                isFailed = account.id in failedIds,
                isInvestment = account.kind == AccountKind.INVESTMENT,
                actualBalanceText = draft.actualBalances[account.id]?.let(AmountFormatter::formatPlain)
                    ?: draft.actualBalanceTexts[account.id].orEmpty(),
                amountError = _uiState.value.accounts.firstOrNull { it.accountId == account.id }?.amountError == true,
                isInputLocked = account.id in draft.actualBalances,
            )
        }
    }
}
