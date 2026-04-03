package com.shihuaidexianyu.money.ui.balance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.data.repository.AccountRepository
import com.shihuaidexianyu.money.data.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.usecase.InvestmentSettlementSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BalanceUpdateDetailUiState(
    val accountId: Long = 0,
    val accountName: String = "",
    val actualBalance: Long = 0,
    val systemBalanceBeforeUpdate: Long = 0,
    val delta: Long = 0,
    val occurredAt: Long = 0,
    val settlementSummary: InvestmentSettlementSummary? = null,
)

class BalanceUpdateDetailViewModel(
    recordId: Long,
    accountRepository: AccountRepository,
    transactionRepository: TransactionRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BalanceUpdateDetailUiState())
    val uiState: StateFlow<BalanceUpdateDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val record = requireNotNull(transactionRepository.getBalanceUpdateRecordById(recordId))
            val account = accountRepository.getAccountById(record.accountId)
            val settlement = transactionRepository.queryInvestmentSettlementsByAccountId(record.accountId)
                .firstOrNull { it.balanceUpdateRecordId == record.id }
                ?.let {
                    InvestmentSettlementSummary(
                        previousBalance = it.previousBalance,
                        currentBalance = it.currentBalance,
                        netTransferIn = it.netTransferIn,
                        netTransferOut = it.netTransferOut,
                        pnl = it.pnl,
                        returnRate = it.returnRate,
                        periodStartAt = it.periodStartAt,
                        periodEndAt = it.periodEndAt,
                    )
                }

            _uiState.value = BalanceUpdateDetailUiState(
                accountId = record.accountId,
                accountName = account?.name ?: "未知账户",
                actualBalance = record.actualBalance,
                systemBalanceBeforeUpdate = record.systemBalanceBeforeUpdate,
                delta = record.delta,
                occurredAt = record.occurredAt,
                settlementSummary = settlement,
            )
        }
    }
}
