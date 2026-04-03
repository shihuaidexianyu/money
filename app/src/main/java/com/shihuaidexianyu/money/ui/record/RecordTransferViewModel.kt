package com.shihuaidexianyu.money.ui.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.data.entity.AccountEntity
import com.shihuaidexianyu.money.data.repository.AccountRepository
import com.shihuaidexianyu.money.domain.usecase.CreateTransferRecordUseCase
import com.shihuaidexianyu.money.ui.common.AccountOptionUiModel
import com.shihuaidexianyu.money.util.AmountInputParser
import com.shihuaidexianyu.money.util.DateTimeTextFormatter
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class RecordTransferUiState(
    val accounts: List<AccountOptionUiModel> = emptyList(),
    val fromAccountId: Long? = null,
    val toAccountId: Long? = null,
    val amountText: String = "",
    val note: String = "",
    val occurredAtText: String = DateTimeTextFormatter.format(System.currentTimeMillis()),
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)

sealed interface RecordTransferEvent {
    data object Saved : RecordTransferEvent
}

class RecordTransferViewModel(
    initialFromAccountId: Long?,
    private val accountRepository: AccountRepository,
    private val createTransferRecordUseCase: CreateTransferRecordUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RecordTransferUiState(fromAccountId = initialFromAccountId))
    val uiState: StateFlow<RecordTransferUiState> = _uiState.asStateFlow()

    private val events = Channel<RecordTransferEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    init {
        viewModelScope.launch {
            val accounts = accountRepository.queryActiveAccounts()
            _uiState.value = _uiState.value.copy(
                accounts = accounts.map { it.toOption() },
                fromAccountId = _uiState.value.fromAccountId ?: accounts.firstOrNull()?.id,
                toAccountId = accounts.firstOrNull { it.id != _uiState.value.fromAccountId }?.id,
            )
        }
    }

    fun updateFromAccount(accountId: Long) {
        _uiState.value = _uiState.value.copy(fromAccountId = accountId, errorMessage = null)
    }

    fun updateToAccount(accountId: Long) {
        _uiState.value = _uiState.value.copy(toAccountId = accountId, errorMessage = null)
    }

    fun swapAccounts() {
        val state = _uiState.value
        _uiState.value = state.copy(
            fromAccountId = state.toAccountId,
            toAccountId = state.fromAccountId,
            errorMessage = null,
        )
    }

    fun updateAmount(value: String) {
        _uiState.value = _uiState.value.copy(amountText = value, errorMessage = null)
    }

    fun updateNote(value: String) {
        _uiState.value = _uiState.value.copy(note = value, errorMessage = null)
    }

    fun updateOccurredAt(value: String) {
        _uiState.value = _uiState.value.copy(occurredAtText = value, errorMessage = null)
    }

    fun save() {
        val state = _uiState.value
        viewModelScope.launch {
            val fromId = state.fromAccountId
            val toId = state.toAccountId
            if (fromId == null || toId == null) {
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

            val occurredAt = DateTimeTextFormatter.parse(state.occurredAtText)
            if (occurredAt == null) {
                _uiState.value = state.copy(errorMessage = "请输入正确的时间，格式如 2026-04-02 16:30")
                return@launch
            }

            _uiState.value = state.copy(isSaving = true, errorMessage = null)
            runCatching {
                createTransferRecordUseCase(
                    fromAccountId = fromId,
                    toAccountId = toId,
                    amount = amount,
                    note = state.note,
                    occurredAt = occurredAt,
                )
            }.onSuccess {
                events.send(RecordTransferEvent.Saved)
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
