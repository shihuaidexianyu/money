package com.shihuaidexianyu.money.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.data.entity.AccountEntity
import com.shihuaidexianyu.money.data.repository.AccountReminderSettingsRepository
import com.shihuaidexianyu.money.data.repository.AccountRepository
import com.shihuaidexianyu.money.data.repository.SettingsRepository
import com.shihuaidexianyu.money.data.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.model.AccountGroupType
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.domain.model.DEFAULT_BALANCE_UPDATE_REMINDER_DAYS
import com.shihuaidexianyu.money.domain.usecase.CalculateCurrentBalanceUseCase
import com.shihuaidexianyu.money.domain.usecase.InvestmentSettlementSummary
import com.shihuaidexianyu.money.util.AccountStatusUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

private data class AccountRecordLine(
    val occurredAt: Long,
    val recordId: Long,
    val text: String,
)

data class AccountDetailUiState(
    val isLoading: Boolean = true,
    val accountId: Long = 0,
    val name: String = "",
    val groupType: AccountGroupType = AccountGroupType.PAYMENT,
    val currentBalance: Long = 0,
    val lastBalanceUpdateAt: Long? = null,
    val reminderDays: Int = DEFAULT_BALANCE_UPDATE_REMINDER_DAYS,
    val isStale: Boolean = false,
    val settings: AppSettings = AppSettings(),
    val latestSettlement: InvestmentSettlementSummary? = null,
    val trendChart: AccountTrendChartUiModel = AccountTrendChartUiModel(),
    val records: List<String> = emptyList(),
)

class AccountDetailViewModel(
    private val accountId: Long,
    private val accountReminderSettingsRepository: AccountReminderSettingsRepository,
    private val accountRepository: AccountRepository,
    private val settingsRepository: SettingsRepository,
    private val transactionRepository: TransactionRepository,
    private val calculateCurrentBalanceUseCase: CalculateCurrentBalanceUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AccountDetailUiState(accountId = accountId))
    val uiState: StateFlow<AccountDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                accountRepository.observeActiveAccounts(),
                accountRepository.observeArchivedAccounts(),
                accountReminderSettingsRepository.observeReminderDays(),
                settingsRepository.observeSettings(),
                transactionRepository.observeChangeVersion(),
            ) { active, archived, reminderDays, settings, _ ->
                val account = (active + archived).firstOrNull { it.id == accountId }
                buildState(account, settings, reminderDays)
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun archiveAccount() {
        viewModelScope.launch {
            accountRepository.archiveAccount(accountId, System.currentTimeMillis())
        }
    }

    private suspend fun buildState(
        account: AccountEntity?,
        settings: AppSettings,
        reminderDays: Map<Long, Int>,
    ): AccountDetailUiState {
        if (account == null) {
            return AccountDetailUiState(
                isLoading = false,
                accountId = accountId,
                settings = settings,
            )
        }

        val balanceUpdates = transactionRepository.queryBalanceUpdateRecordsByAccountId(account.id)
        val settlements = transactionRepository.queryInvestmentSettlementsByAccountId(account.id)
        val currentBalance = calculateCurrentBalanceUseCase(account.id)
        val latestSettlement = settlements.maxWithOrNull(
            compareBy<com.shihuaidexianyu.money.data.entity.InvestmentSettlementEntity> { it.periodEndAt }
                .thenBy { it.id },
        )

        return AccountDetailUiState(
            isLoading = false,
            accountId = account.id,
            name = account.name,
            groupType = AccountGroupType.fromValue(account.groupType),
            currentBalance = currentBalance,
            lastBalanceUpdateAt = account.lastBalanceUpdateAt,
            reminderDays = reminderDays[account.id] ?: DEFAULT_BALANCE_UPDATE_REMINDER_DAYS,
            isStale = AccountStatusUtils.isStale(
                account,
                reminderDays = reminderDays[account.id] ?: DEFAULT_BALANCE_UPDATE_REMINDER_DAYS,
            ),
            settings = settings,
            latestSettlement = latestSettlement?.let { settlement ->
                InvestmentSettlementSummary(
                    previousBalance = settlement.previousBalance,
                    currentBalance = settlement.currentBalance,
                    netTransferIn = settlement.netTransferIn,
                    netTransferOut = settlement.netTransferOut,
                    pnl = settlement.pnl,
                    returnRate = settlement.returnRate,
                    periodStartAt = settlement.periodStartAt,
                    periodEndAt = settlement.periodEndAt,
                )
            },
            trendChart = AccountTrendChartTransformer.build(
                account = account,
                currentBalance = currentBalance,
                balanceUpdates = balanceUpdates,
                settlements = settlements,
            ),
            records = buildRecordLines(account.id),
        )
    }

    private suspend fun buildRecordLines(accountId: Long): List<String> {
        val cash = transactionRepository.queryCashFlowRecordsByAccountId(accountId).map {
            AccountRecordLine(
                occurredAt = it.occurredAt,
                recordId = it.id,
                text = "${if (it.direction == "inflow") "入账" else "出账"} · ${it.purpose.ifBlank { "未填写用途" }}",
            )
        }
        val transfers = transactionRepository.queryTransferRecordsByAccountId(accountId).map {
            AccountRecordLine(
                occurredAt = it.occurredAt,
                recordId = it.id,
                text = "转账 · ${it.note.ifBlank { "账户间转移" }}",
            )
        }
        val updates = transactionRepository.queryBalanceUpdateRecordsByAccountId(accountId).map {
            AccountRecordLine(
                occurredAt = it.occurredAt,
                recordId = it.id,
                text = "更新余额 · 差额 ${it.delta}",
            )
        }
        val adjustments = transactionRepository.queryBalanceAdjustmentRecordsByAccountId(accountId).map {
            AccountRecordLine(
                occurredAt = it.occurredAt,
                recordId = it.id,
                text = "余额矫正 · ${it.delta}",
            )
        }
        return (cash + transfers + updates + adjustments)
            .sortedWith(compareByDescending<AccountRecordLine> { it.occurredAt }.thenByDescending { it.recordId })
            .take(10)
            .map(AccountRecordLine::text)
    }
}
