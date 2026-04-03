package com.shihuaidexianyu.money.ui.accounts

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
import com.shihuaidexianyu.money.domain.model.DEFAULT_BALANCE_UPDATE_REMINDER_DAYS
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
    val lastUsedAt: Long?,
    val archivedAt: Long?,
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
                accountReminderSettingsRepository.observeReminderDays(),
                settingsRepository.observeSettings(),
                transactionRepository.observeChangeVersion(),
            ) { active, archived, reminderDays, settings, _ ->
                AccountsSnapshot(
                    settings = settings,
                    activeAccounts = buildItems(active, reminderDays, settings.accountSortMode),
                    archivedAccounts = archived.map { mapItem(it, reminderDays[it.id]) },
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

    fun archiveAccount(accountId: Long) {
        viewModelScope.launch {
            accountRepository.archiveAccount(accountId, System.currentTimeMillis())
        }
    }

    private suspend fun buildItems(
        accounts: List<AccountEntity>,
        reminderDays: Map<Long, Int>,
        sortMode: AccountSortMode,
    ): List<AccountListItemUiModel> {
        val items = accounts.map { mapItem(it, reminderDays[it.id]) }
        return when (sortMode) {
            AccountSortMode.RECENT_USED -> items.sortedWith(compareByDescending<AccountListItemUiModel> { it.lastUsedAt ?: 0L }.thenBy { it.displayOrder })
            AccountSortMode.MANUAL -> items.sortedBy { it.displayOrder }
            AccountSortMode.BALANCE_DESC -> items.sortedByDescending { it.balance }
        }
    }

    private suspend fun mapItem(account: AccountEntity, reminderDays: Int?): AccountListItemUiModel {
        return AccountListItemUiModel(
            id = account.id,
            name = account.name,
            groupType = AccountGroupType.fromValue(account.groupType),
            balance = calculateCurrentBalanceUseCase(account.id),
            isArchived = account.isArchived,
            isStale = AccountStatusUtils.isStale(
                account,
                reminderDays = reminderDays ?: DEFAULT_BALANCE_UPDATE_REMINDER_DAYS,
            ),
            lastUsedAt = account.lastUsedAt,
            archivedAt = account.archivedAt,
            displayOrder = account.displayOrder,
        )
    }
}
