package com.shihuaidexianyu.money.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.data.entity.AccountEntity
import com.shihuaidexianyu.money.domain.repository.AccountReminderSettingsRepository
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.SettingsRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.model.AccountGroupType
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.usecase.CalculateCurrentBalanceUseCase
import com.shihuaidexianyu.money.util.AccountStatusUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AccountListItemUiModel(
    val id: Long,
    val name: String,
    val groupType: AccountGroupType,
    val balance: Long,
    val isArchived: Boolean,
    val isStale: Boolean,
    val displayOrder: Int,
)

data class AccountsUiState(
    val isLoading: Boolean = true,
    val settings: AppSettings = AppSettings(),
    val showArchived: Boolean = false,
    val activeAccounts: List<AccountListItemUiModel> = emptyList(),
    val archivedAccounts: List<AccountListItemUiModel> = emptyList(),
)

private data class AccountsSnapshot(
    val settings: AppSettings,
    val activeAccounts: List<AccountListItemUiModel>,
    val archivedAccounts: List<AccountListItemUiModel>,
)

class AccountsViewModel(
    private val accountReminderSettingsRepository: AccountReminderSettingsRepository,
    private val accountRepository: AccountRepository,
    private val settingsRepository: SettingsRepository,
    private val transactionRepository: TransactionRepository,
    private val calculateCurrentBalanceUseCase: CalculateCurrentBalanceUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AccountsUiState())
    val uiState: StateFlow<AccountsUiState> = _uiState.asStateFlow()
    private val showArchivedFlow = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            val snapshotFlow = combine(
                accountRepository.observeActiveAccounts(),
                accountRepository.observeArchivedAccounts(),
                accountReminderSettingsRepository.observeReminderConfigs(),
                settingsRepository.observeSettings(),
                transactionRepository.observeChangeVersion(),
            ) { active, archived, reminderConfigs, settings, _ ->
                AccountsSnapshot(
                    settings = settings,
                    activeAccounts = buildItems(active, reminderConfigs, settings),
                    archivedAccounts = buildItems(archived, reminderConfigs, settings),
                )
            }
            combine(snapshotFlow, showArchivedFlow) { snapshot, showArchived ->
                AccountsUiState(
                    isLoading = false,
                    settings = snapshot.settings,
                    showArchived = showArchived,
                    activeAccounts = snapshot.activeAccounts,
                    archivedAccounts = snapshot.archivedAccounts,
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun toggleArchiveVisibility() {
        showArchivedFlow.update { !it }
    }

    private suspend fun buildItems(
        accounts: List<AccountEntity>,
        reminderConfigs: Map<Long, BalanceUpdateReminderConfig>,
        settings: AppSettings,
    ): List<AccountListItemUiModel> {
        val items = accounts.map { mapItem(it, reminderConfigs[it.id]) }
        val groupOrderIndex = settings.accountGroupOrder.withIndex().associate { it.value to it.index }
        return items.sortedWith(
            compareBy<AccountListItemUiModel> { groupOrderIndex[it.groupType] ?: Int.MAX_VALUE }
                .thenBy { it.displayOrder },
        )
    }

    private suspend fun mapItem(
        account: AccountEntity,
        reminderConfig: BalanceUpdateReminderConfig?,
    ): AccountListItemUiModel {
        return AccountListItemUiModel(
            id = account.id,
            name = account.name,
            groupType = AccountGroupType.fromValue(account.groupType),
            balance = calculateCurrentBalanceUseCase(account.id),
            isArchived = account.isArchived,
            isStale = AccountStatusUtils.isStale(
                account,
                reminderConfig = reminderConfig ?: BalanceUpdateReminderConfig(),
            ),
            displayOrder = account.displayOrder,
        )
    }
}

