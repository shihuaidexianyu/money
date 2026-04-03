package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.repository.TransactionRepository

class DeleteTransferRecordUseCase(
    private val transactionRepository: TransactionRepository,
    private val recalculateInvestmentSettlementsUseCase: RecalculateInvestmentSettlementsUseCase,
) {
    suspend operator fun invoke(recordId: Long) {
        val existing = requireNotNull(transactionRepository.queryTransferRecordById(recordId))
        transactionRepository.softDeleteTransferRecord(recordId, System.currentTimeMillis())
        recalculateInvestmentSettlementsUseCase(existing.fromAccountId)
        recalculateInvestmentSettlementsUseCase(existing.toAccountId)
    }
}

