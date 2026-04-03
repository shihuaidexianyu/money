package com.shihuaidexianyu.money.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.data.repository.AccountReminderSettingsRepository
import com.shihuaidexianyu.money.domain.model.AccountGroupType
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderWeekday
import com.shihuaidexianyu.money.domain.usecase.UpdateAccountUseCase
import com.shihuaidexianyu.money.data.repository.AccountRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class EditAccountUiState(
    val isLoading: Boolean = true,
    val name: String = "",
    val groupType: AccountGroupType = AccountGroupType.PAYMENT,
    val reminderConfig: BalanceUpdateReminderConfig = BalanceUpdateReminderConfig(),
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)

sealed interface EditAccountEvent {
    data object Saved : EditAccountEvent
    data object Archived : EditAccountEvent
}

class EditAccountViewModel(
    private val accountId: Long,
    private val accountRepository: AccountRepository,
    private val accountReminderSettingsRepository: AccountReminderSettingsRepository,
    private val updateAccountUseCase: UpdateAccountUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(EditAccountUiState())
    val uiState: StateFlow<EditAccountUiState> = _uiState.asStateFlow()

    private val events = Channel<EditAccountEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    init {
        viewModelScope.launch {
            val account = requireNotNull(accountRepository.getAccountById(accountId)) { "账户不存在" }
            _uiState.value = EditAccountUiState(
                isLoading = false,
                name = account.name,
                groupType = AccountGroupType.fromValue(account.groupType),
                reminderConfig = accountReminderSettingsRepository.getReminderConfig(accountId),
            )
        }
    }

    fun updateName(value: String) {
        _uiState.value = _uiState.value.copy(name = value, errorMessage = null)
    }

    fun updateGroupType(value: AccountGroupType) {
        _uiState.value = _uiState.value.copy(groupType = value, errorMessage = null)
    }

    fun updateReminderWeekday(value: BalanceUpdateReminderWeekday) {
        _uiState.value = _uiState.value.copy(
            reminderConfig = _uiState.value.reminderConfig.copy(weekday = value),
            errorMessage = null,
        )
    }

    fun updateReminderTime(hour: Int, minute: Int) {
        _uiState.value = _uiState.value.copy(
            reminderConfig = _uiState.value.reminderConfig.copy(hour = hour, minute = minute),
            errorMessage = null,
        )
    }

    fun save() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.value = state.copy(isSaving = true, errorMessage = null)
            runCatching {
                updateAccountUseCase(
                    accountId = accountId,
                    name = state.name,
                    groupType = state.groupType,
                    balanceUpdateReminderConfig = state.reminderConfig,
                )
            }.onSuccess {
                events.send(EditAccountEvent.Saved)
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    errorMessage = error.message ?: "保存失败",
                )
            }
        }
    }

    fun archive() {
        viewModelScope.launch {
            runCatching {
                accountRepository.archiveAccount(accountId, System.currentTimeMillis())
            }.onSuccess {
                events.send(EditAccountEvent.Archived)
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    errorMessage = error.message ?: "归档失败",
                )
            }
        }
    }
}
