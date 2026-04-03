package com.shihuaidexianyu.money.ui.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.data.entity.AccountEntity
import com.shihuaidexianyu.money.data.repository.AccountRepository
import com.shihuaidexianyu.money.data.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.usecase.DeleteCashFlowRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateCashFlowRecordUseCase
import com.shihuaidexianyu.money.ui.common.AccountOptionUiModel
import com.shihuaidexianyu.money.util.DateTimeTextFormatter
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode

data class EditCashFlowUiState(
    val isLoading: Boolean = true,
    val direction: CashFlowDirection = CashFlowDirection.INFLOW,
    val accounts: List<AccountOptionUiModel> = emptyList(),
    val selectedAccountId: Long? = null,
    val amountText: String = "",
    val purpose: String = "",
    val occurredAtText: String = "",
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val showDeleteConfirm: Boolean = false,
)

sealed interface EditCashFlowEvent {
    data object Finished : EditCashFlowEvent
}

class EditCashFlowViewModel(
    private val recordId: Long,
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val updateCashFlowRecordUseCase: UpdateCashFlowRecordUseCase,
    private val deleteCashFlowRecordUseCase: DeleteCashFlowRecordUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(EditCashFlowUiState())
    val uiState: StateFlow<EditCashFlowUiState> = _uiState.asStateFlow()

    private val events = Channel<EditCashFlowEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    init {
        viewModelScope.launch {
            val record = requireNotNull(transactionRepository.queryCashFlowRecordById(recordId))
            val accounts = accountRepository.queryActiveAccounts()
            _uiState.value = EditCashFlowUiState(
                isLoading = false,
                direction = CashFlowDirection.fromValue(record.direction),
                accounts = accounts.map { it.toOption() },
                selectedAccountId = record.accountId,
                amountText = record.amount.toAmountText(),
                purpose = record.purpose,
                occurredAtText = DateTimeTextFormatter.format(record.occurredAt),
            )
        }
    }

    fun updateAccount(accountId: Long) = updateState { copy(selectedAccountId = accountId, errorMessage = null) }
    fun updateAmount(value: String) = updateState { copy(amountText = value, errorMessage = null) }
    fun updatePurpose(value: String) = updateState { copy(purpose = value, errorMessage = null) }
    fun updateOccurredAt(value: String) = updateState { copy(occurredAtText = value, errorMessage = null) }
    fun showDeleteConfirm() = updateState { copy(showDeleteConfirm = true) }
    fun dismissDeleteConfirm() = updateState { copy(showDeleteConfirm = false) }

    fun save() {
        val state = _uiState.value
        viewModelScope.launch {
            val accountId = state.selectedAccountId ?: run {
                updateState { copy(errorMessage = "请选择账户") }
                return@launch
            }
            val amount = com.shihuaidexianyu.money.util.AmountInputParser.parseToMinor(state.amountText) ?: run {
                updateState { copy(errorMessage = "金额不能为空") }
                return@launch
            }
            if (amount <= 0) {
                updateState { copy(errorMessage = "金额必须大于 0") }
                return@launch
            }
            val occurredAt = DateTimeTextFormatter.parse(state.occurredAtText) ?: run {
                updateState { copy(errorMessage = "请输入正确的时间，格式如 2026-04-02 16:30") }
                return@launch
            }

            updateState { copy(isSaving = true, errorMessage = null) }
            runCatching {
                updateCashFlowRecordUseCase(
                    recordId = recordId,
                    accountId = accountId,
                    direction = state.direction,
                    amount = amount,
                    purpose = state.purpose,
                    occurredAt = occurredAt,
                )
            }.onSuccess {
                events.send(EditCashFlowEvent.Finished)
            }.onFailure { updateState { copy(isSaving = false, errorMessage = it.message ?: "保存失败") } }
        }
    }

    fun delete() {
        viewModelScope.launch {
            runCatching {
                deleteCashFlowRecordUseCase(recordId)
            }.onSuccess {
                events.send(EditCashFlowEvent.Finished)
            }.onFailure { updateState { copy(errorMessage = it.message ?: "删除失败", showDeleteConfirm = false) } }
        }
    }

    private fun updateState(transform: EditCashFlowUiState.() -> EditCashFlowUiState) {
        _uiState.value = _uiState.value.transform()
    }

    private fun AccountEntity.toOption() = AccountOptionUiModel(id = id, name = name)

    private fun Long.toAmountText(): String {
        return BigDecimal.valueOf(this, 2).setScale(2, RoundingMode.DOWN).toPlainString()
    }
}
