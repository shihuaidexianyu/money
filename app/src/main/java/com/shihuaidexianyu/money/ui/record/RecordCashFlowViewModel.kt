package com.shihuaidexianyu.money.ui.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.data.entity.AccountEntity
import com.shihuaidexianyu.money.data.repository.AccountRepository
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.usecase.CreateCashFlowRecordUseCase
import com.shihuaidexianyu.money.ui.common.AccountOptionUiModel
import com.shihuaidexianyu.money.util.AmountInputParser
import com.shihuaidexianyu.money.util.DateTimeTextFormatter
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class RecordCashFlowUiState(
    val direction: CashFlowDirection,
    val accounts: List<AccountOptionUiModel> = emptyList(),
    val selectedAccountId: Long? = null,
    val amountText: String = "",
    val purpose: String = "",
    val occurredAtMillis: Long = DateTimeTextFormatter.floorToMinute(System.currentTimeMillis()),
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val showPurposeConfirm: Boolean = false,
)

sealed interface RecordCashFlowEvent {
    data object Saved : RecordCashFlowEvent
}

class RecordCashFlowViewModel(
    private val direction: CashFlowDirection,
    initialAccountId: Long?,
    private val accountRepository: AccountRepository,
    private val createCashFlowRecordUseCase: CreateCashFlowRecordUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        RecordCashFlowUiState(
            direction = direction,
            selectedAccountId = initialAccountId,
        ),
    )
    val uiState: StateFlow<RecordCashFlowUiState> = _uiState.asStateFlow()

    private val events = Channel<RecordCashFlowEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    init {
        viewModelScope.launch {
            val accounts = accountRepository.queryActiveAccounts()
            _uiState.value = _uiState.value.copy(
                accounts = accounts.map { it.toOption() },
                selectedAccountId = _uiState.value.selectedAccountId ?: accounts.firstOrNull()?.id,
            )
        }
    }

    fun updateAccount(accountId: Long) {
        _uiState.value = _uiState.value.copy(selectedAccountId = accountId, errorMessage = null)
    }

    fun updateAmount(value: String) {
        _uiState.value = _uiState.value.copy(amountText = value, errorMessage = null)
    }

    fun updatePurpose(value: String) {
        _uiState.value = _uiState.value.copy(purpose = value, errorMessage = null)
    }

    fun updateOccurredAt(value: Long) {
        _uiState.value = _uiState.value.copy(
            occurredAtMillis = DateTimeTextFormatter.floorToMinute(value),
            errorMessage = null,
        )
    }

    fun dismissPurposeConfirm() {
        _uiState.value = _uiState.value.copy(showPurposeConfirm = false)
    }

    fun save(confirmBlankPurpose: Boolean = false) {
        val state = _uiState.value
        if (state.purpose.isBlank() && !confirmBlankPurpose) {
            _uiState.value = state.copy(showPurposeConfirm = true)
            return
        }

        viewModelScope.launch {
            val accountId = state.selectedAccountId
            if (accountId == null) {
                _uiState.value = state.copy(errorMessage = "请选择账户")
                return@launch
            }

            val amount = AmountInputParser.parseToMinor(state.amountText)
            if (amount == null) {
                _uiState.value = state.copy(errorMessage = "金额不能为空")
                return@launch
            }
            if (amount <= 0) {
                _uiState.value = state.copy(errorMessage = "金额必须大于 0")
                return@launch
            }

            val occurredAt = state.occurredAtMillis
            if (occurredAt > System.currentTimeMillis()) {
                _uiState.value = state.copy(errorMessage = "时间不能晚于当前时间")
                return@launch
            }

            _uiState.value = state.copy(isSaving = true, errorMessage = null, showPurposeConfirm = false)
            runCatching {
                createCashFlowRecordUseCase(
                    accountId = accountId,
                    direction = direction,
                    amount = amount,
                    purpose = state.purpose,
                    occurredAt = occurredAt,
                )
            }.onSuccess {
                events.send(RecordCashFlowEvent.Saved)
            }.onFailure { throwable ->
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    errorMessage = throwable.message ?: "保存失败",
                )
            }
        }
    }

    private fun AccountEntity.toOption(): AccountOptionUiModel {
        return AccountOptionUiModel(id = id, name = name)
    }
}
