package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.repository.TransactionRepository

class DeleteCashFlowRecordUseCase(
    private val transactionRepository: TransactionRepository,
    private val recalculateInvestmentSettlementsUseCase: RecalculateInvestmentSettlementsUseCase,
) {
    suspend operator fun invoke(recordId: Long) {
        val existing = requireNotNull(transactionRepository.queryCashFlowRecordById(recordId))
        transactionRepository.softDeleteCashFlowRecord(recordId, System.currentTimeMillis())
        recalculateInvestmentSettlementsUseCase(existing.accountId)
    }
}

