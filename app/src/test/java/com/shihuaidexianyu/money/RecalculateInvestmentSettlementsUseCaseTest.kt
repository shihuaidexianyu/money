package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.entity.AccountEntity
import com.shihuaidexianyu.money.data.entity.BalanceUpdateRecordEntity
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.usecase.RecalculateInvestmentSettlementsUseCase
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.Test

class RecalculateInvestmentSettlementsUseCaseTest {
    @Test
    fun `same timestamp updates still use previous update as previous balance`() = runBlocking {
        val accountRepository = InMemoryAccountRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val accountId = accountRepository.createAccount(
            AccountEntity(
                name = "证券账户",
                groupType = "investment",
                initialBalance = 100_000,
                createdAt = 1_000,
            ),
        )

        transactionRepository.insertBalanceUpdateRecord(
            BalanceUpdateRecordEntity(
                accountId = accountId,
                actualBalance = 110_000,
                systemBalanceBeforeUpdate = 100_000,
                delta = 10_000,
                occurredAt = 2_000,
                createdAt = 2_000,
            ),
        )
        transactionRepository.insertBalanceUpdateRecord(
            BalanceUpdateRecordEntity(
                accountId = accountId,
                actualBalance = 120_000,
                systemBalanceBeforeUpdate = 110_000,
                delta = 10_000,
                occurredAt = 2_000,
                createdAt = 2_100,
            ),
        )

        RecalculateInvestmentSettlementsUseCase(accountRepository, transactionRepository)(accountId)

        val settlements = transactionRepository.queryInvestmentSettlementsByAccountId(accountId)
            .sortedBy { it.id }
        assertEquals(2, settlements.size)
        assertEquals(100_000, settlements[0].previousBalance)
        assertEquals(110_000, settlements[1].previousBalance)
    }
}
