package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.repository.TransactionRepository

class DeleteCashFlowRecordUseCase(
    private val transactionRepository: TransactionRepository,
    private val recalculateInvestmentSettlementsUseCase: RecalculateInvestmentSettlementsUseCase,
    private val refreshAccountActivityStateUseCase: RefreshAccountActivityStateUseCase,
) {
    suspend operator fun invoke(recordId: Long) {
        val existing = transactionRepository.queryCashFlowRecordById(recordId) ?: return
        transactionRepository.softDeleteCashFlowRecord(recordId, System.currentTimeMillis())
        recalculateInvestmentSettlementsUseCase(existing.accountId)
        refreshAccountActivityStateUseCase(existing.accountId)
    }
}

