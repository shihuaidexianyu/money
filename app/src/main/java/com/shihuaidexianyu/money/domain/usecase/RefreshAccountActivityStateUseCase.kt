package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository

class RefreshAccountActivityStateUseCase(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
) {
    suspend operator fun invoke(accountId: Long) {
        val account = accountRepository.getAccountById(accountId) ?: return
        val lastUsedAt = listOfNotNull(
            transactionRepository.queryCashFlowRecordsByAccountId(accountId).maxOfOrNull { it.occurredAt },
            transactionRepository.queryTransferRecordsByAccountId(accountId).maxOfOrNull { it.occurredAt },
            transactionRepository.queryBalanceUpdateRecordsByAccountId(accountId).maxOfOrNull { it.occurredAt },
            transactionRepository.queryBalanceAdjustmentRecordsByAccountId(accountId).maxOfOrNull { it.occurredAt },
            account.createdAt,
        ).maxOrNull() ?: account.createdAt
        val lastBalanceUpdateAt = transactionRepository.queryBalanceUpdateRecordsByAccountId(accountId)
            .maxOfOrNull { it.occurredAt }

        accountRepository.updateLastUsedAt(accountId, lastUsedAt)
        accountRepository.updateLastBalanceUpdateAt(accountId, lastBalanceUpdateAt)
    }
}
