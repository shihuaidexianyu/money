package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.data.entity.TransferRecordEntity
import com.shihuaidexianyu.money.data.repository.AccountRepository
import com.shihuaidexianyu.money.data.repository.TransactionRepository

class CreateTransferRecordUseCase(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
) {
    suspend operator fun invoke(
        fromAccountId: Long,
        toAccountId: Long,
        amount: Long,
        note: String,
        occurredAt: Long,
    ): Long {
        require(fromAccountId != toAccountId) { "请选择不同的转出和转入账户" }
        require(amount > 0) { "金额必须大于 0" }
        require(occurredAt <= System.currentTimeMillis()) { "时间不能晚于当前时间" }
        requireNotNull(accountRepository.getAccountById(fromAccountId))
        requireNotNull(accountRepository.getAccountById(toAccountId))

        val now = System.currentTimeMillis()
        val recordId = transactionRepository.insertTransferRecord(
            TransferRecordEntity(
                fromAccountId = fromAccountId,
                toAccountId = toAccountId,
                amount = amount,
                note = note.trim(),
                occurredAt = occurredAt,
                createdAt = now,
                updatedAt = now,
            ),
        )

        val lastUsed = maxOf(occurredAt, now)
        accountRepository.updateLastUsedAt(fromAccountId, lastUsed)
        accountRepository.updateLastUsedAt(toAccountId, lastUsed)
        return recordId
    }
}
