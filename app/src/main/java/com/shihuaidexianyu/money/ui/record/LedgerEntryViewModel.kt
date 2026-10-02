package com.shihuaidexianyu.money.ui.record

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.R
import com.shihuaidexianyu.money.di.SystemClockProvider
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.ledgerSubtractExact
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.DevicePreferencesRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.time.ClockProvider
import com.shihuaidexianyu.money.domain.usecase.CalculateAccountBalancesUseCase
import com.shihuaidexianyu.money.domain.usecase.CreateCashFlowRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.CreateTransferRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.LedgerOperationIdFactory
import com.shihuaidexianyu.money.domain.usecase.ProcessDueReminderUseCase
import com.shihuaidexianyu.money.domain.usecase.ResolveBalanceUpdateContextUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateBalanceUseCase
import com.shihuaidexianyu.money.domain.usecase.ValidationErrorText
import com.shihuaidexianyu.money.ui.common.AccountOptionUiModel
import com.shihuaidexianyu.money.ui.common.FormTerminalKind
import com.shihuaidexianyu.money.ui.common.PendingFormTerminal
import com.shihuaidexianyu.money.ui.common.PENDING_FORM_TERMINAL_KEY
import com.shihuaidexianyu.money.ui.common.UiEffect
import com.shihuaidexianyu.money.ui.common.pendingFormTerminal
import com.shihuaidexianyu.money.ui.common.toAccountOptionUiModel
import com.shihuaidexianyu.money.util.AmountFormatter
import com.shihuaidexianyu.money.util.AmountInputParser
import com.shihuaidexianyu.money.util.DateTimeTextFormatter
import com.shihuaidexianyu.money.util.RecordValidator
import java.io.Serializable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class LedgerEntryKind { INCOME, EXPENSE, TRANSFER, RECONCILE }

enum class LedgerEntryStep(val number: Int) {
    TYPE(1), ACCOUNT(2), TO_ACCOUNT(2), AMOUNT(3), CONFIRM(4),
}

private data class LedgerEntryDraft(
    val kind: LedgerEntryKind?,
    val step: LedgerEntryStep,
    val accountId: Long?,
    val toAccountId: Long?,
    val amountText: String,
    val actualBalanceText: String,
    val note: String,
    val occurredAtMillis: Long,
    val timeEdited: Boolean,
    val isDirty: Boolean,
    val completed: Boolean,
    val operationId: String,
) : Serializable

data class LedgerEntryUiState(
    val kind: LedgerEntryKind? = null,
    val step: LedgerEntryStep = LedgerEntryStep.TYPE,
    val kindLocked: Boolean = false,
    val isLoading: Boolean = true,
    val loadErrorMessageRes: Int? = null,
    val accounts: List<AccountOptionUiModel> = emptyList(),
    val accountId: Long? = null,
    val toAccountId: Long? = null,
    val amountText: String = "",
    val actualBalanceText: String = "",
    val note: String = "",
    val noteSuggestions: List<String> = emptyList(),
    val occurredAtMillis: Long = 0,
    val timeEdited: Boolean = false,
    val systemBalance: Long? = null,
    val previewLoading: Boolean = false,
    val previewError: String? = null,
    val accountError: String? = null,
    val amountError: String? = null,
    val noteError: String? = null,
    val occurredAtError: String? = null,
    val isDirty: Boolean = false,
    val isSaving: Boolean = false,
    val completed: Boolean = false,
    val pendingTerminal: PendingFormTerminal? = null,
) {
    val selectedAccount: AccountOptionUiModel? get() = accounts.firstOrNull { it.id == accountId }
    val toAccount: AccountOptionUiModel? get() = accounts.firstOrNull { it.id == toAccountId }
    val inputText: String get() = if (kind == LedgerEntryKind.RECONCILE) actualBalanceText else amountText
    val amount: Long? get() = if (kind == LedgerEntryKind.RECONCILE) {
        AmountInputParser.parseSignedToMinor(actualBalanceText)
    } else {
        AmountInputParser.parseUnsignedToMinor(amountText)
    }
    val delta: Long? get() = systemBalance?.let { system ->
        amount?.let { actual -> runCatching { ledgerSubtractExact(actual, system) }.getOrNull() }
    }
    val deltaOverflow: Boolean get() = kind == LedgerEntryKind.RECONCILE && systemBalance != null && amount != null && delta == null
    val editable: Boolean get() = !isLoading && loadErrorMessageRes == null && !isSaving && !completed && pendingTerminal == null
}

data class LedgerEntryMessage(override val message: String) : UiEffect.HasMessage {
    override val messageRes: Int? = null
}

/** One persisted draft owns all entry steps; only CONFIRM is allowed to write the ledger. */
class LedgerEntryViewModel(
    initialKind: LedgerEntryKind? = null,
    initialAccountId: Long? = null,
    prefillAmount: Long? = null,
    prefillNote: String? = null,
    private val reminderId: Long? = null,
    private val expectedDueAt: Long? = null,
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val devicePreferencesRepository: DevicePreferencesRepository,
    private val calculateAccountBalancesUseCase: CalculateAccountBalancesUseCase,
    private val resolveBalanceUpdateContextUseCase: ResolveBalanceUpdateContextUseCase,
    private val createCashFlowRecordUseCase: CreateCashFlowRecordUseCase,
    private val createTransferRecordUseCase: CreateTransferRecordUseCase,
    private val updateBalanceUseCase: UpdateBalanceUseCase,
    private val processDueReminderUseCase: ProcessDueReminderUseCase,
    private val savedStateHandle: SavedStateHandle,
    operationIdFactory: LedgerOperationIdFactory,
    private val clockProvider: ClockProvider = SystemClockProvider,
    prefillOccurredAt: Long? = null,
) : ViewModel() {
    private val restored = savedStateHandle.get<LedgerEntryDraft>(DRAFT_KEY)
    private val operationId = restored?.operationId ?: operationIdFactory.create()
    private val _uiState = MutableStateFlow(
        LedgerEntryUiState(
            isLoading = restored?.completed != true,
            kind = restored?.kind ?: initialKind,
            step = restored?.step ?: when {
                initialKind == null -> LedgerEntryStep.TYPE
                initialAccountId != null && initialKind != LedgerEntryKind.TRANSFER -> LedgerEntryStep.AMOUNT
                initialAccountId != null -> LedgerEntryStep.TO_ACCOUNT
                else -> LedgerEntryStep.ACCOUNT
            },
            kindLocked = reminderId != null,
            accountId = if (restored != null) restored.accountId else initialAccountId,
            toAccountId = restored?.toAccountId,
            amountText = restored?.amountText ?: prefillAmount?.let(AmountFormatter::formatPlain).orEmpty(),
            actualBalanceText = restored?.actualBalanceText.orEmpty(),
            note = restored?.note ?: prefillNote.orEmpty(),
            occurredAtMillis = restored?.occurredAtMillis ?: DateTimeTextFormatter.floorToMinute(prefillOccurredAt ?: clockProvider.nowMillis()),
            timeEdited = restored?.timeEdited ?: (prefillOccurredAt != null),
            isDirty = restored?.isDirty ?: false,
            completed = restored?.completed ?: false,
            pendingTerminal = savedStateHandle[PENDING_FORM_TERMINAL_KEY],
        ),
    )
    val uiState = _uiState.asStateFlow()
    private val effects = MutableSharedFlow<LedgerEntryMessage>(extraBufferCapacity = 1)
    val effectFlow = effects.asSharedFlow()
    private var loadJob: Job? = null
    private var previewJob: Job? = null
    private var noteJob: Job? = null

    init {
        persistDraft()
        reloadAccounts()
    }

    fun reloadAccounts() {
        if (_uiState.value.isSaving || _uiState.value.completed) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                val accounts = accountRepository.queryOpenAccounts()
                    .sortedWith(compareBy<Account> { it.createdAt }.thenBy { it.id })
                val balances = calculateAccountBalancesUseCase(accounts)
                val options = accounts.map { it.toAccountOptionUiModel(balances.getValue(it.id)) }
                val current = _uiState.value
                val accountId = current.accountId?.takeIf { id -> options.any { it.id == id } }
                val toId = current.toAccountId?.takeIf { id -> options.any { it.id == id } && id != accountId }
                val step = when {
                    current.step.number > 2 && accountId == null -> LedgerEntryStep.ACCOUNT
                    current.step == LedgerEntryStep.TO_ACCOUNT && accountId == null -> LedgerEntryStep.ACCOUNT
                    current.step.number > 2 && current.kind == LedgerEntryKind.TRANSFER && toId == null -> LedgerEntryStep.TO_ACCOUNT
                    else -> current.step
                }
                _uiState.value = current.copy(
                    isLoading = false, loadErrorMessageRes = null, accounts = options,
                    accountId = accountId, toAccountId = toId, step = step,
                    accountError = if (current.accountId != null && accountId == null) "账户已不可用，请重新选择" else current.accountError,
                )
                persistDraft()
                refreshPreview()
                refreshNotes()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, loadErrorMessageRes = R.string.open_accounts_load_failed)
            }
        }
    }

    fun chooseKind(kind: LedgerEntryKind) {
        val state = _uiState.value
        if (!state.editable || state.step != LedgerEntryStep.TYPE || state.kindLocked) return
        if (kind == LedgerEntryKind.TRANSFER && state.accounts.size < 2) {
            change { copy(accountError = "转账至少需要两个可用账户") }
            return
        }
        change { copy(kind = kind, step = LedgerEntryStep.ACCOUNT, accountError = null, amountError = null, isDirty = true) }
        refreshPreview()
        refreshNotes()
    }

    fun chooseAccount(accountId: Long, expectedStep: LedgerEntryStep) {
        val state = _uiState.value
        if (!state.editable || state.step != expectedStep || expectedStep !in listOf(LedgerEntryStep.ACCOUNT, LedgerEntryStep.TO_ACCOUNT)) return
        if (state.accounts.none { it.id == accountId }) return
        if (expectedStep == LedgerEntryStep.TO_ACCOUNT) {
            if (accountId == state.accountId) {
                change { copy(accountError = ValidationErrorText.SAME_TRANSFER_ACCOUNTS) }
                return
            }
            change { copy(toAccountId = accountId, step = LedgerEntryStep.AMOUNT, accountError = null, isDirty = true) }
        } else {
            change {
                copy(
                    accountId = accountId,
                    toAccountId = toAccountId?.takeIf { it != accountId },
                    step = if (kind == LedgerEntryKind.TRANSFER) LedgerEntryStep.TO_ACCOUNT else LedgerEntryStep.AMOUNT,
                    accountError = null, isDirty = true,
                )
            }
        }
        refreshPreview()
        refreshNotes()
    }

    fun updateAmount(value: String) {
        if (_uiState.value.step != LedgerEntryStep.AMOUNT) return
        change {
            if (kind == LedgerEntryKind.RECONCILE) copy(actualBalanceText = value, amountError = null, isDirty = true)
            else copy(amountText = value, amountError = null, isDirty = true)
        }
    }

    fun useAccountBalance() {
        val state = _uiState.value
        if (state.kind == LedgerEntryKind.RECONCILE) {
            state.systemBalance?.let { updateAmount(AmountFormatter.formatPlain(it)) }
        } else if (state.kind == LedgerEntryKind.TRANSFER) {
            state.selectedAccount?.balance?.takeIf { it > 0 }?.let { updateAmount(AmountFormatter.formatPlain(it)) }
        }
    }

    fun continueToConfirmation() {
        val state = _uiState.value
        if (!state.editable || state.step != LedgerEntryStep.AMOUNT) return
        if (!validateAccounts() || !validateAmount()) return
        change {
            copy(
                step = LedgerEntryStep.CONFIRM,
                occurredAtMillis = if (timeEdited) occurredAtMillis else DateTimeTextFormatter.floorToMinute(clockProvider.nowMillis()),
            )
        }
        refreshPreview()
    }

    fun back(): Boolean {
        val state = _uiState.value
        if (!state.editable) return true
        val previous = when (state.step) {
            LedgerEntryStep.TYPE -> return false
            LedgerEntryStep.ACCOUNT -> if (state.kindLocked) return false else LedgerEntryStep.TYPE
            LedgerEntryStep.TO_ACCOUNT -> LedgerEntryStep.ACCOUNT
            LedgerEntryStep.AMOUNT -> if (state.kind == LedgerEntryKind.TRANSFER) LedgerEntryStep.TO_ACCOUNT else LedgerEntryStep.ACCOUNT
            LedgerEntryStep.CONFIRM -> LedgerEntryStep.AMOUNT
        }
        change { copy(step = previous) }
        return true
    }

    fun editAccount() {
        change { copy(step = LedgerEntryStep.ACCOUNT) }
    }

    fun updateNote(value: String) {
        if (_uiState.value.step != LedgerEntryStep.CONFIRM || _uiState.value.kind == LedgerEntryKind.RECONCILE) return
        change { copy(note = value, noteError = ledgerNoteLengthError(value), isDirty = true) }
    }

    fun updateOccurredAt(value: Long) {
        if (_uiState.value.step != LedgerEntryStep.CONFIRM) return
        change { copy(occurredAtMillis = DateTimeTextFormatter.floorToMinute(value), timeEdited = true, occurredAtError = null, isDirty = true) }
        refreshPreview()
    }

    fun refreshPreview() {
        previewJob?.cancel()
        val snapshot = _uiState.value
        if (snapshot.kind != LedgerEntryKind.RECONCILE || snapshot.accountId == null || snapshot.completed) {
            _uiState.value = snapshot.copy(previewLoading = false, previewError = null, systemBalance = null)
            return
        }
        _uiState.value = snapshot.copy(previewLoading = true, previewError = null, systemBalance = null)
        previewJob = viewModelScope.launch {
            try {
                val balance = resolveBalanceUpdateContextUseCase(snapshot.accountId, snapshot.occurredAtMillis).systemBalanceBeforeUpdate
                val current = _uiState.value
                if (current.kind == snapshot.kind && current.accountId == snapshot.accountId && current.occurredAtMillis == snapshot.occurredAtMillis) {
                    _uiState.value = current.copy(systemBalance = balance, previewLoading = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(previewLoading = false, previewError = e.message ?: "账面余额加载失败，请重试")
            }
        }
    }

    fun save() {
        val state = _uiState.value
        if (!state.editable || state.step != LedgerEntryStep.CONFIRM) return
        if (!validateAccounts() || !validateAmount()) return
        if (state.kind == LedgerEntryKind.RECONCILE && (state.previewLoading || state.previewError != null || state.delta == null)) return
        val timeError = when {
            state.occurredAtMillis > clockProvider.nowMillis() -> ValidationErrorText.OCCURRED_AT_IN_FUTURE
            else -> null
        }
        if (timeError != null) {
            change { copy(occurredAtError = timeError) }
            return
        }
        val normalizedNote = if (state.kind == LedgerEntryKind.RECONCILE) "" else {
            runCatching { normalizeLedgerNote(state.note) }.getOrElse { error ->
                change { copy(noteError = error.message) }
                return
            }
        }
        // Set synchronously, before launching: double taps cannot start two mutations.
        _uiState.value = state.copy(isSaving = true, noteError = null)
        viewModelScope.launch {
            try {
                val accountId = requireNotNull(state.accountId)
                val amount = requireNotNull(state.amount)
                val result = when (state.kind) {
                    LedgerEntryKind.INCOME, LedgerEntryKind.EXPENSE -> {
                        val direction = if (state.kind == LedgerEntryKind.INCOME) CashFlowDirection.INFLOW else CashFlowDirection.OUTFLOW
                        if (reminderId != null) {
                            processDueReminderUseCase(
                                reminderId = reminderId,
                                expectedDueAt = requireNotNull(expectedDueAt) { "提醒时间不存在" },
                                accountId = accountId, direction = direction,
                                occurredAt = state.occurredAtMillis, amount = amount, note = normalizedNote,
                            )
                        } else {
                            createCashFlowRecordUseCase(accountId, direction, amount, normalizedNote, state.occurredAtMillis, operationId)
                        }
                        null
                    }
                    LedgerEntryKind.TRANSFER -> {
                        createTransferRecordUseCase(accountId, requireNotNull(state.toAccountId), amount, normalizedNote, state.occurredAtMillis, operationId)
                        null
                    }
                    LedgerEntryKind.RECONCILE -> {
                        val latestSystemBalance = resolveBalanceUpdateContextUseCase(accountId, state.occurredAtMillis).systemBalanceBeforeUpdate
                        if (latestSystemBalance != state.systemBalance) {
                            _uiState.value = _uiState.value.copy(isSaving = false, systemBalance = latestSystemBalance)
                            effects.emit(LedgerEntryMessage("账面余额已变化，请核对差额后再次确认"))
                            return@launch
                        }
                        updateBalanceUseCase(accountId, amount, state.occurredAtMillis, operationId)
                    }
                    null -> error("请选择记账方式")
                }
                val terminal = pendingFormTerminal(FormTerminalKind.SAVED, balanceResult = result)
                savedStateHandle[PENDING_FORM_TERMINAL_KEY] = terminal
                _uiState.value = _uiState.value.copy(isSaving = false, completed = true, isDirty = false, pendingTerminal = terminal)
                persistDraft()
                runCatching {
                    val recentIds = devicePreferencesRepository.query().recentAccountIds
                    devicePreferencesRepository.updateRecentAccountIds(listOfNotNull(accountId, state.toAccountId.takeIf { state.kind == LedgerEntryKind.TRANSFER }) + recentIds)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isSaving = false)
                val message = e.message ?: "保存失败，请重试"
                when {
                    message.contains("时间") -> change { copy(occurredAtError = message) }
                    message.contains("账户") -> {
                        change { copy(accountError = message, step = LedgerEntryStep.ACCOUNT) }
                        reloadAccounts()
                    }
                    else -> effects.emit(LedgerEntryMessage(message))
                }
            }
        }
    }

    fun ackTerminal(token: String) {
        if (_uiState.value.pendingTerminal?.token != token) return
        savedStateHandle.remove<PendingFormTerminal>(PENDING_FORM_TERMINAL_KEY)
        _uiState.value = _uiState.value.copy(pendingTerminal = null)
    }

    private fun validateAccounts(): Boolean {
        val state = _uiState.value
        if (state.selectedAccount == null) {
            change { copy(accountError = "请选择账户", step = LedgerEntryStep.ACCOUNT) }
            return false
        }
        if (state.kind == LedgerEntryKind.TRANSFER && (state.toAccount == null || state.accountId == state.toAccountId)) {
            change { copy(accountError = "请选择不同的转入账户", step = LedgerEntryStep.TO_ACCOUNT) }
            return false
        }
        return true
    }

    private fun validateAmount(): Boolean {
        val state = _uiState.value
        return runCatching {
            if (state.kind == LedgerEntryKind.RECONCILE) RecordValidator.requireSignedAmount(state.inputText)
            else RecordValidator.requireAmount(state.inputText)
        }.fold(
            onSuccess = { true },
            onFailure = { error -> change { copy(amountError = error.message, step = LedgerEntryStep.AMOUNT) }; false },
        )
    }

    private fun refreshNotes() {
        noteJob?.cancel()
        val state = _uiState.value
        _uiState.value = state.copy(noteSuggestions = emptyList())
        noteJob = viewModelScope.launch {
            val notes = runCatching {
                when (state.kind) {
                    LedgerEntryKind.INCOME, LedgerEntryKind.EXPENSE -> {
                        val direction = if (state.kind == LedgerEntryKind.INCOME) CashFlowDirection.INFLOW else CashFlowDirection.OUTFLOW
                        transactionRepository.queryRecentCashFlowNotes(direction.value, state.accountId, 6)
                    }
                    LedgerEntryKind.TRANSFER -> transactionRepository.queryRecentTransferNotes(state.accountId, state.toAccountId, 6)
                    else -> emptyList()
                }
            }.getOrDefault(emptyList())
            val current = _uiState.value
            if (current.kind == state.kind && current.accountId == state.accountId && current.toAccountId == state.toAccountId) {
                _uiState.value = current.copy(noteSuggestions = notes)
            }
        }
    }

    private fun change(transform: LedgerEntryUiState.() -> LedgerEntryUiState) {
        if (!_uiState.value.editable) return
        _uiState.value = _uiState.value.transform()
        persistDraft()
    }

    private fun persistDraft() {
        val state = _uiState.value
        savedStateHandle[DRAFT_KEY] = LedgerEntryDraft(
            state.kind, state.step, state.accountId, state.toAccountId, state.amountText,
            state.actualBalanceText, state.note, state.occurredAtMillis, state.timeEdited,
            state.isDirty, state.completed, operationId,
        )
    }

    private companion object {
        const val DRAFT_KEY = "ledger_entry_draft"
    }
}
