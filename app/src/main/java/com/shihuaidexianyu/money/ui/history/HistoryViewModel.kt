package com.shihuaidexianyu.money.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.data.entity.AccountEntity
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.SettingsRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.ui.common.AccountOptionUiModel
import com.shihuaidexianyu.money.ui.common.toAccountOptionUiModel
import com.shihuaidexianyu.money.util.AmountInputParser
import com.shihuaidexianyu.money.util.DateTimeTextFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class HistoryRecordKind {
    CASH_FLOW,
    TRANSFER,
    BALANCE_UPDATE,
    BALANCE_ADJUSTMENT,
}

data class HistoryRecordUiModel(
    val id: String,
    val recordId: Long,
    val kind: HistoryRecordKind,
    val title: String,
    val subtitle: String,
    val amount: Long,
    val occurredAt: Long,
    val accountIds: Set<Long>,
    val keywordSource: String,
)

data class HistoryUiState(
    val settings: AppSettings = AppSettings(),
    val accountOptions: List<AccountOptionUiModel> = emptyList(),
    val keyword: String = "",
    val selectedAccountId: Long? = null,
    val dateStartAt: Long? = null,
    val dateEndAt: Long? = null,
    val minAmountText: String = "",
    val maxAmountText: String = "",
    val records: List<HistoryRecordUiModel> = emptyList(),
)

class HistoryViewModel(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()
    private var allRecords: List<HistoryRecordUiModel> = emptyList()

    init {
        viewModelScope.launch {
            try {
                combine(
                    settingsRepository.observeSettings(),
                    transactionRepository.observeChangeVersion(),
                ) { settings, _ ->
                    buildState(settings)
                }.collect { newState ->
                    allRecords = newState.records
                    _uiState.update { current ->
                        newState.copy(
                            keyword = current.keyword,
                            selectedAccountId = current.selectedAccountId,
                            dateStartAt = current.dateStartAt,
                            dateEndAt = current.dateEndAt,
                            minAmountText = current.minAmountText,
                            maxAmountText = current.maxAmountText,
                            records = applyFilters(newState.records, current),
                        )
                    }
                }
            } catch (_: Exception) {
                // leave current state as-is
            }
        }
    }

    fun updateKeyword(value: String) = applyLocalFilter { copy(keyword = value) }
    fun updateAccount(accountId: Long?) = applyLocalFilter { copy(selectedAccountId = accountId) }
    fun updateDateRange(startAt: Long?, endAt: Long?) {
        val normalizedStart = startAt
        val normalizedEnd = endAt
        if (normalizedStart != null && normalizedEnd != null && normalizedStart > normalizedEnd) {
            applyLocalFilter { copy(dateStartAt = normalizedEnd, dateEndAt = normalizedStart) }
        } else {
            applyLocalFilter { copy(dateStartAt = normalizedStart, dateEndAt = normalizedEnd) }
        }
    }

    fun updateMinAmount(value: String) = applyLocalFilter { copy(minAmountText = value) }
    fun updateMaxAmount(value: String) = applyLocalFilter { copy(maxAmountText = value) }

    private fun applyLocalFilter(transform: HistoryUiState.() -> HistoryUiState) {
        _uiState.update { current ->
            val updated = current.transform()
            updated.copy(records = applyFilters(allRecords, updated))
        }
    }

    private suspend fun buildState(settings: AppSettings): HistoryUiState {
        val accounts = (accountRepository.queryActiveAccounts() + accountRepository.queryArchivedAccounts())
            .distinctBy { it.id }
            .associateBy { it.id }
        val records = buildAllRecords(accounts)
        return HistoryUiState(
            settings = settings,
            accountOptions = accounts.values
                .sortedBy { it.name }
                .map { it.toAccountOptionUiModel() },
            records = records,
        )
    }

    private suspend fun buildAllRecords(
        accounts: Map<Long, AccountEntity>,
    ): List<HistoryRecordUiModel> {
        val cashFlowRecords = transactionRepository.queryAllActiveCashFlowRecords().map { record ->
            HistoryRecordUiModel(
                id = "cash_${record.id}",
                recordId = record.id,
                kind = HistoryRecordKind.CASH_FLOW,
                title = if (record.purpose.isBlank()) {
                    "未填写用途"
                } else {
                    record.purpose
                },
                subtitle = accounts[record.accountId]?.name ?: "未知账户",
                amount = if (record.direction == "inflow") record.amount else -record.amount,
                occurredAt = record.occurredAt,
                accountIds = setOf(record.accountId),
                keywordSource = record.purpose,
            )
        }
        val transferRecords = transactionRepository.queryAllActiveTransferRecords().map { record ->
            HistoryRecordUiModel(
                id = "transfer_${record.id}",
                recordId = record.id,
                kind = HistoryRecordKind.TRANSFER,
                title = record.note.ifBlank { "账户间转移" },
                subtitle = "${(accounts[record.fromAccountId]?.name ?: "未知")} → ${(accounts[record.toAccountId]?.name ?: "未知")}",
                amount = record.amount,
                occurredAt = record.occurredAt,
                accountIds = setOf(record.fromAccountId, record.toAccountId),
                keywordSource = record.note,
            )
        }
        val updateRecords = transactionRepository.queryAllBalanceUpdateRecords()
            .filter { it.delta != 0L }
            .map { record ->
            HistoryRecordUiModel(
                id = "balance_update_${record.id}",
                recordId = record.id,
                kind = HistoryRecordKind.BALANCE_UPDATE,
                title = "更新余额",
                subtitle = accounts[record.accountId]?.name ?: "未知账户",
                amount = record.delta,
                occurredAt = record.occurredAt,
                accountIds = setOf(record.accountId),
                keywordSource = "",
            )
        }
        val adjustmentRecords = transactionRepository.queryAllBalanceAdjustmentRecords()
            .filter { it.sourceUpdateRecordId == 0L }
            .map { record ->
            HistoryRecordUiModel(
                id = "balance_adjustment_${record.id}",
                recordId = record.id,
                kind = HistoryRecordKind.BALANCE_ADJUSTMENT,
                title = "余额矫正",
                subtitle = accounts[record.accountId]?.name ?: "未知账户",
                amount = record.delta,
                occurredAt = record.occurredAt,
                accountIds = setOf(record.accountId),
                keywordSource = "",
            )
            }
        return (cashFlowRecords + transferRecords + updateRecords + adjustmentRecords)
            .sortedByDescending { it.occurredAt }
    }

    private fun applyFilters(
        source: List<HistoryRecordUiModel>,
        state: HistoryUiState,
    ): List<HistoryRecordUiModel> {
        val keyword = state.keyword.trim().lowercase()
        val startAt = state.dateStartAt
        val endAt = state.dateEndAt
        val minAmount = AmountInputParser.parseToMinor(state.minAmountText)
        val maxAmount = AmountInputParser.parseToMinor(state.maxAmountText)

        return source.filter { record ->
            val keywordOk = keyword.isBlank() || record.keywordSource.lowercase().contains(keyword)
            val accountOk = state.selectedAccountId == null || state.selectedAccountId in record.accountIds
            val startOk = startAt == null || record.occurredAt >= startAt
            val endOk = endAt == null || record.occurredAt <= endAt
            val amountAbs = kotlin.math.abs(record.amount)
            val minOk = minAmount == null || amountAbs >= minAmount
            val maxOk = maxAmount == null || amountAbs <= maxAmount
            keywordOk && accountOk && startOk && endOk && minOk && maxOk
        }
    }
}

