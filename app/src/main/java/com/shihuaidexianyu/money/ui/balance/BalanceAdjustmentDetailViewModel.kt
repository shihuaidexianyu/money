package com.shihuaidexianyu.money.ui.balance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BalanceAdjustmentDetailUiState(
    val isLoading: Boolean = true,
    val accountName: String = "",
    val delta: Long = 0,
    val occurredAt: Long = 0,
    val sourceUpdateRecordId: Long = 0,
)

sealed interface BalanceAdjustmentDetailEffect {
    data object Closed : BalanceAdjustmentDetailEffect
}

class BalanceAdjustmentDetailViewModel(
    recordId: Long,
    accountRepository: AccountRepository,
    transactionRepository: TransactionRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BalanceAdjustmentDetailUiState())
    val uiState: StateFlow<BalanceAdjustmentDetailUiState> = _uiState.asStateFlow()
    private val effects = MutableSharedFlow<BalanceAdjustmentDetailEffect>(extraBufferCapacity = 1)
    val effectFlow = effects.asSharedFlow()
    private var closed = false

    init {
        viewModelScope.launch {
            val record = transactionRepository.getBalanceAdjustmentRecordById(recordId)
            if (record == null) {
                emitClosedOnce()
                return@launch
            }
            val account = accountRepository.getAccountById(record.accountId)
            _uiState.value = BalanceAdjustmentDetailUiState(
                isLoading = false,
                accountName = account?.name ?: "未知账户",
                delta = record.delta,
                occurredAt = record.occurredAt,
                sourceUpdateRecordId = record.sourceUpdateRecordId,
            )
        }
    }

    private suspend fun emitClosedOnce() {
        if (closed) return
        closed = true
        effects.emit(BalanceAdjustmentDetailEffect.Closed)
    }
}

