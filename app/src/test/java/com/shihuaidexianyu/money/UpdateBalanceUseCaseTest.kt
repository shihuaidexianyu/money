package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.entity.AccountEntity
import com.shihuaidexianyu.money.data.entity.TransferRecordEntity
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.usecase.CalculateCurrentBalanceUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateBalanceUseCase
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Test

class UpdateBalanceUseCaseTest {
    @Test
    fun `matching actual balance does not create adjustment`() = runBlocking {
        val accountRepository = InMemoryAccountRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val accountId = accountRepository.createAccount(
            AccountEntity(
                name = "银行卡",
                groupType = "bank",
                initialBalance = 10_000,
                createdAt = 1_000,
            ),
        )
        val calculateCurrentBalanceUseCase = CalculateCurrentBalanceUseCase(accountRepository, transactionRepository)
        val updateBalanceUseCase = UpdateBalanceUseCase(
            accountRepository = accountRepository,
            transactionRepository = transactionRepository,
            calculateCurrentBalanceUseCase = calculateCurrentBalanceUseCase,
        )

        val result = updateBalanceUseCase(
            accountId = accountId,
            actualBalance = 10_000,
            occurredAt = System.currentTimeMillis() - 1_000,
        )

        assertEquals(0, result.delta)
        assertNull(result.settlementSummary)
        assertEquals(1, transactionRepository.queryBalanceUpdateRecordsByAccountId(accountId).size)
        assertTrue(transactionRepository.queryBalanceAdjustmentRecordsByAccountId(accountId).isEmpty())
    }

    @Test
    fun `investment update creates adjustment and settlement`() = runBlocking {
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
        transactionRepository.insertTransferRecord(
            TransferRecordEntity(
                fromAccountId = 99,
                toAccountId = accountId,
                amount = 20_000,
                note = "入金",
                occurredAt = 2_000,
                createdAt = 2_000,
                updatedAt = 2_000,
            ),
        )

        val calculateCurrentBalanceUseCase = CalculateCurrentBalanceUseCase(accountRepository, transactionRepository)
        val updateBalanceUseCase = UpdateBalanceUseCase(
            accountRepository = accountRepository,
            transactionRepository = transactionRepository,
            calculateCurrentBalanceUseCase = calculateCurrentBalanceUseCase,
        )

        val result = updateBalanceUseCase(
            accountId = accountId,
            actualBalance = 130_000,
            occurredAt = System.currentTimeMillis() - 1_000,
        )

        assertEquals(10_000, result.delta)
        val settlement = assertNotNull(result.settlementSummary)
        assertEquals(100_000, settlement.previousBalance)
        assertEquals(20_000, settlement.netTransferIn)
        assertEquals(10_000, settlement.pnl)
        assertEquals(0, transactionRepository.queryBalanceAdjustmentRecordsByAccountId(accountId).size)
        assertEquals(1, transactionRepository.queryInvestmentSettlementsByAccountId(accountId).size)
    }
}

