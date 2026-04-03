package com.shihuaidexianyu.money.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.domain.model.AccountGroupType
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderWeekday
import com.shihuaidexianyu.money.domain.usecase.CreateAccountUseCase
import com.shihuaidexianyu.money.util.AmountInputParser
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class CreateAccountUiState(
    val name: String = "",
    val groupType: AccountGroupType = AccountGroupType.PAYMENT,
    val reminderConfig: BalanceUpdateReminderConfig = BalanceUpdateReminderConfig(),
    val amountText: String = "",
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)

sealed interface CreateAccountEvent {
    data object Saved : CreateAccountEvent
}

class CreateAccountViewModel(
    private val createAccountUseCase: CreateAccountUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CreateAccountUiState())
    val uiState: StateFlow<CreateAccountUiState> = _uiState.asStateFlow()

    private val events = Channel<CreateAccountEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

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

    fun updateAmountText(value: String) {
        _uiState.value = _uiState.value.copy(amountText = value, errorMessage = null)
    }

    fun save() {
        viewModelScope.launch {
            val amount = AmountInputParser.parseToMinor(_uiState.value.amountText)
            if (amount == null) {
                _uiState.value = _uiState.value.copy(errorMessage = "金额不能为空")
                return@launch
            }

            _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null)
            runCatching {
                createAccountUseCase(
                    name = _uiState.value.name,
                    groupType = _uiState.value.groupType,
                    initialBalance = amount,
                    balanceUpdateReminderConfig = _uiState.value.reminderConfig,
                )
            }.onSuccess {
                events.send(CreateAccountEvent.Saved)
            }.onFailure { throwable ->
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    errorMessage = throwable.message ?: "创建账户失败",
                )
            }
        }
    }
}
