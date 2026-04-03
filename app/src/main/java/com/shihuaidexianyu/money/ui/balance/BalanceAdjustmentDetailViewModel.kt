package com.shihuaidexianyu.money.ui.balance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.data.repository.AccountRepository
import com.shihuaidexianyu.money.data.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BalanceAdjustmentDetailUiState(
    val accountName: String = "",
    val delta: Long = 0,
    val occurredAt: Long = 0,
    val sourceUpdateRecordId: Long = 0,
)

class BalanceAdjustmentDetailViewModel(
    recordId: Long,
    accountRepository: AccountRepository,
    transactionRepository: TransactionRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BalanceAdjustmentDetailUiState())
    val uiState: StateFlow<BalanceAdjustmentDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val record = requireNotNull(transactionRepository.getBalanceAdjustmentRecordById(recordId))
            val account = accountRepository.getAccountById(record.accountId)
            _uiState.value = BalanceAdjustmentDetailUiState(
                accountName = account?.name ?: "未知账户",
                delta = record.delta,
                occurredAt = record.occurredAt,
                sourceUpdateRecordId = record.sourceUpdateRecordId,
            )
        }
    }
}
