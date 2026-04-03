package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.repository.TransactionRepository

class DeleteBalanceUpdateRecordUseCase(
    private val transactionRepository: TransactionRepository,
    private val recalculateInvestmentSettlementsUseCase: RecalculateInvestmentSettlementsUseCase,
    private val refreshAccountActivityStateUseCase: RefreshAccountActivityStateUseCase,
) {
    suspend operator fun invoke(recordId: Long) {
        val existing = transactionRepository.getBalanceUpdateRecordById(recordId) ?: return
        transactionRepository.deleteBalanceUpdateRecord(recordId)
        recalculateInvestmentSettlementsUseCase(existing.accountId)
        refreshAccountActivityStateUseCase(existing.accountId)
    }
}
