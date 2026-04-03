package com.shihuaidexianyu.money.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.data.entity.AccountEntity
import com.shihuaidexianyu.money.data.repository.AccountReminderSettingsRepository
import com.shihuaidexianyu.money.data.repository.AccountRepository
import com.shihuaidexianyu.money.data.repository.SettingsRepository
import com.shihuaidexianyu.money.data.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.model.AccountGroupType
import com.shihuaidexianyu.money.domain.model.AccountSortMode
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.usecase.CalculateCurrentBalanceUseCase
import com.shihuaidexianyu.money.ui.common.AccountOptionUiModel
import com.shihuaidexianyu.money.util.AccountStatusUtils
import com.shihuaidexianyu.money.util.TimeRangeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class HomeAccountUiModel(
    val id: Long,
    val name: String,
    val groupType: AccountGroupType,
    val balance: Long,
    val isStale: Boolean,
    val staleDays: Long,
    val lastUsedAt: Long?,
    val displayOrder: Int,
)

data class HomeGroupSection(
    val groupType: AccountGroupType,
    val accounts: List<HomeAccountUiModel>,
)

data class HomeUiState(
    val isLoading: Boolean = true,
    val settings: AppSettings = AppSettings(),
    val totalAssets: Long = 0,
    val periodNetInflow: Long = 0,
    val periodNetOutflow: Long = 0,
    val staleAccountCount: Int = 0,
    val accountOptions: List<AccountOptionUiModel> = emptyList(),
    val groupSections: List<HomeGroupSection> = emptyList(),
)

class HomeViewModel(
    private val accountReminderSettingsRepository: AccountReminderSettingsRepository,
    private val accountRepository: AccountRepository,
    private val settingsRepository: SettingsRepository,
    private val transactionRepository: TransactionRepository,
    private val calculateCurrentBalanceUseCase: CalculateCurrentBalanceUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                accountRepository.observeActiveAccounts(),
                accountReminderSettingsRepository.observeReminderConfigs(),
                settingsRepository.observeSettings(),
                transactionRepository.observeChangeVersion(),
            ) { accounts, reminderConfigs, settings, _ ->
                buildUiState(accounts, reminderConfigs, settings)
            }.collect { _uiState.value = it }
        }
    }

    private suspend fun buildUiState(
        accounts: List<AccountEntity>,
        reminderConfigs: Map<Long, BalanceUpdateReminderConfig>,
        settings: AppSettings,
    ): HomeUiState {
        val range = TimeRangeUtils.currentRange(settings.homePeriod)
        val items = accounts.map { account ->
            val accountReminderConfig = reminderConfigs[account.id] ?: BalanceUpdateReminderConfig()
            HomeAccountUiModel(
                id = account.id,
                name = account.name,
                groupType = AccountGroupType.fromValue(account.groupType),
                balance = calculateCurrentBalanceUseCase(account.id),
                isStale = AccountStatusUtils.isStale(account, reminderConfig = accountReminderConfig),
                staleDays = AccountStatusUtils.staleDays(account),
                lastUsedAt = account.lastUsedAt,
                displayOrder = account.displayOrder,
            )
        }

        val grouped = AccountGroupType.entries.map { groupType ->
            HomeGroupSection(
                groupType = groupType,
                accounts = sortItems(items.filter { it.groupType == groupType }, settings.accountSortMode),
            )
        }

        val cashFlowRecords = transactionRepository.queryAllActiveCashFlowRecords()
        val periodNetInflow = cashFlowRecords
            .filter { it.direction == "inflow" && it.occurredAt in range.startAtMillis..range.endAtMillis }
            .sumOf { it.amount }
        val periodNetOutflow = cashFlowRecords
            .filter { it.direction == "outflow" && it.occurredAt in range.startAtMillis..range.endAtMillis }
            .sumOf { it.amount }

        return HomeUiState(
            isLoading = false,
            settings = settings,
            totalAssets = items.sumOf { it.balance },
            periodNetInflow = periodNetInflow,
            periodNetOutflow = periodNetOutflow,
            staleAccountCount = items.count { it.isStale },
            accountOptions = accounts.map { AccountOptionUiModel(id = it.id, name = it.name) },
            groupSections = grouped,
        )
    }

    private fun sortItems(
        items: List<HomeAccountUiModel>,
        mode: AccountSortMode,
    ): List<HomeAccountUiModel> {
        return when (mode) {
            AccountSortMode.RECENT_USED -> items.sortedWith(compareByDescending<HomeAccountUiModel> { it.lastUsedAt ?: 0L }.thenBy { it.displayOrder })
            AccountSortMode.MANUAL -> items.sortedBy { it.displayOrder }
            AccountSortMode.BALANCE_DESC -> items.sortedByDescending { it.balance }
        }
    }
}
