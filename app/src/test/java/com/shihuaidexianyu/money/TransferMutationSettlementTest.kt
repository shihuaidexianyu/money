package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.entity.AccountEntity
import com.shihuaidexianyu.money.domain.usecase.CalculateCurrentBalanceUseCase
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.usecase.CreateTransferRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.RecalculateInvestmentSettlementsUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateBalanceUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateTransferRecordUseCase
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.Test

class TransferMutationSettlementTest {
    @Test
    fun `editing transfer recalculates investment settlement`() = runBlocking {
        val accountRepository = InMemoryAccountRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val bankId = accountRepository.createAccount(
            AccountEntity(name = "银行卡", groupType = "bank", initialBalance = 500_000, createdAt = 1_000),
        )
        val investmentId = accountRepository.createAccount(
            AccountEntity(name = "证券账户", groupType = "investment", initialBalance = 100_000, createdAt = 1_000),
        )

        val createTransfer = CreateTransferRecordUseCase(accountRepository, transactionRepository)
        val transferId = createTransfer(
            fromAccountId = bankId,
            toAccountId = investmentId,
            amount = 20_000,
            note = "入金",
            occurredAt = 2_000,
        )

        val calculateBalance = CalculateCurrentBalanceUseCase(accountRepository, transactionRepository)
        val updateBalance = UpdateBalanceUseCase(accountRepository, transactionRepository, calculateBalance)
        updateBalance(
            accountId = investmentId,
            actualBalance = 130_000,
            occurredAt = 3_000,
        )

        val recalc = RecalculateInvestmentSettlementsUseCase(accountRepository, transactionRepository)
        val updateTransfer = UpdateTransferRecordUseCase(accountRepository, transactionRepository, recalc)
        updateTransfer(
            recordId = transferId,
            fromAccountId = bankId,
            toAccountId = investmentId,
            amount = 10_000,
            note = "改成 1 万",
            occurredAt = 2_000,
        )

        val settlement = transactionRepository.getLatestInvestmentSettlement(investmentId)
        assertEquals(10_000, settlement?.netTransferIn)
        assertEquals(20_000, settlement?.pnl)
    }
}

