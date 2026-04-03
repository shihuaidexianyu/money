package com.shihuaidexianyu.money.ui.balance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.data.entity.AccountEntity
import com.shihuaidexianyu.money.data.repository.AccountRepository
import com.shihuaidexianyu.money.domain.usecase.CalculateCurrentBalanceUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateBalanceResult
import com.shihuaidexianyu.money.domain.usecase.UpdateBalanceUseCase
import com.shihuaidexianyu.money.ui.common.AccountOptionUiModel
import com.shihuaidexianyu.money.util.AmountInputParser
import com.shihuaidexianyu.money.util.DateTimeTextFormatter
import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class UpdateBalanceUiState(
    val accounts: List<AccountOptionUiModel> = emptyList(),
    val selectedAccountId: Long? = null,
    val actualBalanceText: String = "",
    val occurredAtText: String = DateTimeTextFormatter.format(System.currentTimeMillis()),
    val systemBalanceBeforeUpdate: Long = 0,
    val actualBalancePreview: Long? = null,
    val deltaPreview: Long? = null,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val latestResult: UpdateBalanceResult? = null,
)

sealed interface UpdateBalanceEvent {
    data object Saved : UpdateBalanceEvent
}

class UpdateBalanceViewModel(
    initialAccountId: Long?,
    private val accountRepository: AccountRepository,
    private val calculateCurrentBalanceUseCase: CalculateCurrentBalanceUseCase,
    private val updateBalanceUseCase: UpdateBalanceUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(UpdateBalanceUiState(selectedAccountId = initialAccountId))
    val uiState: StateFlow<UpdateBalanceUiState> = _uiState.asStateFlow()

    private val events = Channel<UpdateBalanceEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    init {
        viewModelScope.launch {
            val accounts = accountRepository.queryActiveAccounts()
            val selected = _uiState.value.selectedAccountId ?: accounts.firstOrNull()?.id
            val systemBalance = selected?.let { calculateCurrentBalanceUseCase(it) } ?: 0L
            _uiState.value = _uiState.value.copy(
                accounts = accounts.map { it.toOption() },
                selectedAccountId = selected,
                actualBalanceText = systemBalance.toInputAmountText(),
                systemBalanceBeforeUpdate = systemBalance,
                actualBalancePreview = systemBalance,
                deltaPreview = 0,
            )
        }
    }

    fun updateAccount(accountId: Long) {
        viewModelScope.launch {
            val systemBalance = calculateCurrentBalanceUseCase(accountId)
            _uiState.value = _uiState.value.copy(
                selectedAccountId = accountId,
                actualBalanceText = systemBalance.toInputAmountText(),
                systemBalanceBeforeUpdate = systemBalance,
                actualBalancePreview = systemBalance,
                deltaPreview = 0,
                errorMessage = null,
            )
        }
    }

    fun updateActualBalance(value: String) {
        val actual = AmountInputParser.parseToMinor(value)
        val systemBalance = _uiState.value.systemBalanceBeforeUpdate
        _uiState.value = _uiState.value.copy(
            actualBalanceText = value,
            actualBalancePreview = actual,
            deltaPreview = actual?.minus(systemBalance),
            errorMessage = null,
        )
    }

    fun updateOccurredAt(value: String) {
        _uiState.value = _uiState.value.copy(occurredAtText = value, errorMessage = null)
    }

    fun save() {
        val state = _uiState.value
        viewModelScope.launch {
            val accountId = state.selectedAccountId
            if (accountId == null) {
                _uiState.value = state.copy(errorMessage = "请选择账户")
                return@launch
            }

            val actualBalance = AmountInputParser.parseToMinor(state.actualBalanceText)
            if (actualBalance == null) {
                _uiState.value = state.copy(errorMessage = "金额不能为空")
                return@launch
            }

            val occurredAt = DateTimeTextFormatter.parse(state.occurredAtText)
            if (occurredAt == null) {
                _uiState.value = state.copy(errorMessage = "请输入正确的时间，格式如 2026-04-02 16:30")
                return@launch
            }

            _uiState.value = state.copy(isSaving = true, errorMessage = null)
            runCatching {
                updateBalanceUseCase(
                    accountId = accountId,
                    actualBalance = actualBalance,
                    occurredAt = occurredAt,
                )
            }.onSuccess { result ->
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    latestResult = result,
                    actualBalanceText = result.actualBalance.toInputAmountText(),
                    systemBalanceBeforeUpdate = result.actualBalance,
                    actualBalancePreview = result.actualBalance,
                    deltaPreview = 0,
                )
                events.send(UpdateBalanceEvent.Saved)
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

    private fun Long.toInputAmountText(): String {
        return BigDecimal.valueOf(this, 2)
            .setScale(2, RoundingMode.DOWN)
            .toPlainString()
    }
}
