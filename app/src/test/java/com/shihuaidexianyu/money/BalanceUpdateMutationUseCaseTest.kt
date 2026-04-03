package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.entity.AccountEntity
import com.shihuaidexianyu.money.data.entity.CashFlowRecordEntity
import com.shihuaidexianyu.money.data.entity.TransferRecordEntity
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.usecase.CalculateCurrentBalanceUseCase
import com.shihuaidexianyu.money.domain.usecase.DeleteBalanceUpdateRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.RefreshAccountActivityStateUseCase
import com.shihuaidexianyu.money.domain.usecase.RecalculateInvestmentSettlementsUseCase
import com.shihuaidexianyu.money.domain.usecase.ResolveBalanceUpdateContextUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateBalanceUpdateRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateBalanceUseCase
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import org.junit.Test

class BalanceUpdateMutationUseCaseTest {
    @Test
    fun `editing balance update recalculates baseline and current balance`() = runBlocking {
        val accountRepository = InMemoryAccountRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val resolveContext = ResolveBalanceUpdateContextUseCase(accountRepository, transactionRepository)
        val refreshActivity = RefreshAccountActivityStateUseCase(accountRepository, transactionRepository)
        val updateBalanceUseCase = UpdateBalanceUseCase(
            accountRepository = accountRepository,
            transactionRepository = transactionRepository,
            resolveBalanceUpdateContextUseCase = resolveContext,
            refreshAccountActivityStateUseCase = refreshActivity,
        )
        val updateBalanceUpdateRecordUseCase = UpdateBalanceUpdateRecordUseCase(
            transactionRepository = transactionRepository,
            resolveBalanceUpdateContextUseCase = resolveContext,
            recalculateInvestmentSettlementsUseCase = RecalculateInvestmentSettlementsUseCase(accountRepository, transactionRepository),
            refreshAccountActivityStateUseCase = refreshActivity,
        )
        val calculateCurrentBalanceUseCase = CalculateCurrentBalanceUseCase(accountRepository, transactionRepository)
        val accountId = accountRepository.createAccount(
            AccountEntity(
                name = "银行卡",
                groupType = "bank",
                initialBalance = 10_000,
                createdAt = 1_000,
            ),
        )
        transactionRepository.insertCashFlowRecord(
            CashFlowRecordEntity(
                accountId = accountId,
                direction = "inflow",
                amount = 2_000,
                purpose = "工资",
                occurredAt = 2_000,
                createdAt = 2_000,
                updatedAt = 2_000,
            ),
        )

        updateBalanceUseCase(accountId = accountId, actualBalance = 11_000, occurredAt = 3_000)
        val recordId = transactionRepository.getLatestBalanceUpdate(accountId)?.id ?: error("missing record")

        updateBalanceUpdateRecordUseCase(
            recordId = recordId,
            actualBalance = 9_500,
            occurredAt = 1_500,
        )

        val updatedRecord = transactionRepository.getBalanceUpdateRecordById(recordId) ?: error("missing updated record")
        assertEquals(10_000, updatedRecord.systemBalanceBeforeUpdate)
        assertEquals(-500, updatedRecord.delta)
        assertEquals(11_500, calculateCurrentBalanceUseCase(accountId))
    }

    @Test
    fun `deleting latest balance update restores last balance update timestamp`() = runBlocking {
        val accountRepository = InMemoryAccountRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val resolveContext = ResolveBalanceUpdateContextUseCase(accountRepository, transactionRepository)
        val refreshActivity = RefreshAccountActivityStateUseCase(accountRepository, transactionRepository)
        val updateBalanceUseCase = UpdateBalanceUseCase(
            accountRepository = accountRepository,
            transactionRepository = transactionRepository,
            resolveBalanceUpdateContextUseCase = resolveContext,
            refreshAccountActivityStateUseCase = refreshActivity,
        )
        val deleteBalanceUpdateRecordUseCase = DeleteBalanceUpdateRecordUseCase(
            transactionRepository = transactionRepository,
            recalculateInvestmentSettlementsUseCase = RecalculateInvestmentSettlementsUseCase(accountRepository, transactionRepository),
            refreshAccountActivityStateUseCase = refreshActivity,
        )
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

        updateBalanceUseCase(accountId = accountId, actualBalance = 130_000, occurredAt = 3_000)
        updateBalanceUseCase(accountId = accountId, actualBalance = 140_000, occurredAt = 5_000)
        val latestRecordId = transactionRepository.getLatestBalanceUpdate(accountId)?.id ?: error("missing latest record")

        deleteBalanceUpdateRecordUseCase(latestRecordId)

        assertEquals(3_000, accountRepository.getAccountById(accountId)?.lastBalanceUpdateAt)
        assertEquals(1, transactionRepository.queryInvestmentSettlementsByAccountId(accountId).size)
        assertNull(
            transactionRepository.queryInvestmentSettlementsByAccountId(accountId)
                .firstOrNull { it.balanceUpdateRecordId == latestRecordId },
        )
    }
}
