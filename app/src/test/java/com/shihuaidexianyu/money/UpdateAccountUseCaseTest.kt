package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.entity.AccountEntity
import com.shihuaidexianyu.money.data.entity.BalanceUpdateRecordEntity
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryAccountReminderSettingsRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.AccountGroupType
import com.shihuaidexianyu.money.domain.usecase.RecalculateInvestmentSettlementsUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateAccountUseCase
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.Test

class UpdateAccountUseCaseTest {
    @Test
    fun `changing account to investment recalculates settlements`() = runBlocking {
        val accountRepository = InMemoryAccountRepository()
        val reminderRepository = InMemoryAccountReminderSettingsRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val accountId = accountRepository.createAccount(
            AccountEntity(
                name = "银行卡",
                groupType = "bank",
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

        UpdateAccountUseCase(
            accountRepository = accountRepository,
            accountReminderSettingsRepository = reminderRepository,
            transactionRepository = transactionRepository,
            recalculateInvestmentSettlementsUseCase = RecalculateInvestmentSettlementsUseCase(
                accountRepository,
                transactionRepository,
            ),
        )(
            accountId = accountId,
            name = "理财账户",
            groupType = AccountGroupType.INVESTMENT,
            balanceUpdateReminderDays = 14,
        )

        val updated = accountRepository.getAccountById(accountId)
        assertEquals("理财账户", updated?.name)
        assertEquals("investment", updated?.groupType)
        assertEquals(14, reminderRepository.getReminderDays(accountId))
        assertEquals(1, transactionRepository.queryInvestmentSettlementsByAccountId(accountId).size)
    }

    @Test
    fun `changing away from investment clears settlements`() = runBlocking {
        val accountRepository = InMemoryAccountRepository()
        val reminderRepository = InMemoryAccountReminderSettingsRepository()
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
        RecalculateInvestmentSettlementsUseCase(accountRepository, transactionRepository)(accountId)

        UpdateAccountUseCase(
            accountRepository = accountRepository,
            accountReminderSettingsRepository = reminderRepository,
            transactionRepository = transactionRepository,
            recalculateInvestmentSettlementsUseCase = RecalculateInvestmentSettlementsUseCase(
                accountRepository,
                transactionRepository,
            ),
        )(
            accountId = accountId,
            name = "银行卡",
            groupType = AccountGroupType.BANK,
            balanceUpdateReminderDays = 7,
        )

        assertEquals(0, transactionRepository.queryInvestmentSettlementsByAccountId(accountId).size)
        assertEquals("bank", accountRepository.getAccountById(accountId)?.groupType)
        assertEquals(7, reminderRepository.getReminderDays(accountId))
    }
}
