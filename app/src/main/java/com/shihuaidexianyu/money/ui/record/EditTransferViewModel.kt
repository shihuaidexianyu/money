package com.shihuaidexianyu.money.ui.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.data.entity.AccountEntity
import com.shihuaidexianyu.money.data.repository.AccountRepository
import com.shihuaidexianyu.money.data.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.usecase.DeleteTransferRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateTransferRecordUseCase
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

data class EditTransferUiState(
    val isLoading: Boolean = true,
    val accounts: List<AccountOptionUiModel> = emptyList(),
    val fromAccountId: Long? = null,
    val toAccountId: Long? = null,
    val amountText: String = "",
    val note: String = "",
    val occurredAtText: String = "",
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val showDeleteConfirm: Boolean = false,
)

sealed interface EditTransferEvent {
    data object Finished : EditTransferEvent
}

class EditTransferViewModel(
    private val recordId: Long,
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val updateTransferRecordUseCase: UpdateTransferRecordUseCase,
    private val deleteTransferRecordUseCase: DeleteTransferRecordUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(EditTransferUiState())
    val uiState: StateFlow<EditTransferUiState> = _uiState.asStateFlow()

    private val events = Channel<EditTransferEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    init {
        viewModelScope.launch {
            val record = requireNotNull(transactionRepository.queryTransferRecordById(recordId))
            val accounts = accountRepository.queryActiveAccounts()
            _uiState.value = EditTransferUiState(
                isLoading = false,
                accounts = accounts.map { it.toOption() },
                fromAccountId = record.fromAccountId,
                toAccountId = record.toAccountId,
                amountText = record.amount.toAmountText(),
                note = record.note,
                occurredAtText = DateTimeTextFormatter.format(record.occurredAt),
            )
        }
    }

    fun updateFromAccount(accountId: Long) = updateState { copy(fromAccountId = accountId, errorMessage = null) }
    fun updateToAccount(accountId: Long) = updateState { copy(toAccountId = accountId, errorMessage = null) }
    fun updateAmount(value: String) = updateState { copy(amountText = value, errorMessage = null) }
    fun updateNote(value: String) = updateState { copy(note = value, errorMessage = null) }
    fun updateOccurredAt(value: String) = updateState { copy(occurredAtText = value, errorMessage = null) }
    fun showDeleteConfirm() = updateState { copy(showDeleteConfirm = true) }
    fun dismissDeleteConfirm() = updateState { copy(showDeleteConfirm = false) }

    fun save() {
        val state = _uiState.value
        viewModelScope.launch {
            val fromId = state.fromAccountId ?: run { updateState { copy(errorMessage = "请选择账户") }; return@launch }
            val toId = state.toAccountId ?: run { updateState { copy(errorMessage = "请选择账户") }; return@launch }
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
                updateTransferRecordUseCase(
                    recordId = recordId,
                    fromAccountId = fromId,
                    toAccountId = toId,
                    amount = amount,
                    note = state.note,
                    occurredAt = occurredAt,
                )
            }.onSuccess { events.send(EditTransferEvent.Finished) }
                .onFailure { updateState { copy(isSaving = false, errorMessage = it.message ?: "保存失败") } }
        }
    }

    fun delete() {
        viewModelScope.launch {
            runCatching { deleteTransferRecordUseCase(recordId) }
                .onSuccess { events.send(EditTransferEvent.Finished) }
                .onFailure { updateState { copy(errorMessage = it.message ?: "删除失败", showDeleteConfirm = false) } }
        }
    }

    private fun updateState(transform: EditTransferUiState.() -> EditTransferUiState) {
        _uiState.value = _uiState.value.transform()
    }

    private fun AccountEntity.toOption() = AccountOptionUiModel(id = id, name = name)

    private fun Long.toAmountText(): String {
        return BigDecimal.valueOf(this, 2).setScale(2, RoundingMode.DOWN).toPlainString()
    }
}
