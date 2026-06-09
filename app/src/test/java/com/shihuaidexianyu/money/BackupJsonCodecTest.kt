package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.repository.InMemoryAccountReminderSettingsRepository
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryRecurringReminderRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.model.RecurringReminder
import com.shihuaidexianyu.money.domain.model.ReminderPeriodType
import com.shihuaidexianyu.money.domain.model.ReminderType
import com.shihuaidexianyu.money.domain.usecase.BuildExportJsonUseCase
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Test

class BackupJsonCodecTest {
    @Test
    fun `export json is parseable with empty collections`() = runBlocking {
        val json = buildUseCase()(exportedAt = 42L)
        val root = JSONObject(json)

        assertEquals(1, root.getJSONObject("metadata").getInt("schemaVersion"))
        assertEquals(42L, root.getJSONObject("metadata").getLong("exportedAt"))
        assertEquals(0, root.getJSONArray("accounts").length())
        assertEquals(0, root.getJSONArray("cashFlowRecords").length())
        assertEquals(0, root.getJSONArray("transferRecords").length())
        assertEquals(0, root.getJSONArray("balanceUpdateRecords").length())
        assertEquals(0, root.getJSONArray("balanceAdjustmentRecords").length())
        assertEquals(0, root.getJSONArray("recurringReminders").length())
        assertEquals(0, root.getJSONArray("accountReminderConfigs").length())
    }

    @Test
    fun `export json escapes text and preserves nulls and long values`() = runBlocking {
        val accountRepository = InMemoryAccountRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val reminderRepository = InMemoryRecurringReminderRepository()
        val accountName = "现金\"账户\\A\n第二行"
        val accountId = accountRepository.createAccount(
            Account(
                name = accountName,
                initialBalance = Long.MAX_VALUE,
                createdAt = 1L,
                colorName = "blue",
            ),
        )
        val purpose = "早餐\t包子\\豆浆\""
        transactionRepository.insertCashFlowRecord(
            CashFlowRecord(
                accountId = accountId,
                direction = CashFlowDirection.OUTFLOW.value,
                amount = Long.MAX_VALUE,
                purpose = purpose,
                occurredAt = 2L,
                createdAt = 2L,
                updatedAt = 2L,
            ),
        )
        reminderRepository.insertReminder(
            RecurringReminder(
                name = "订阅\n会员",
                type = ReminderType.SUBSCRIPTION.value,
                accountId = accountId,
                direction = CashFlowDirection.OUTFLOW.value,
                amount = 888L,
                periodType = ReminderPeriodType.MONTHLY.value,
                periodValue = 9,
                periodMonth = null,
                nextDueAt = 3L,
                createdAt = 3L,
                updatedAt = 3L,
            ),
        )

        val json = buildUseCase(
            accountRepository = accountRepository,
            transactionRepository = transactionRepository,
            reminderRepository = reminderRepository,
            settingsRepository = TestSettingsRepository(
                AppSettings(
                    currencySymbol = "￥\"\\\n",
                    lastHistoryKeyword = "咖啡\n引号\"",
                ),
            ),
        )(exportedAt = Long.MAX_VALUE)
        val root = JSONObject(json)

        assertEquals(Long.MAX_VALUE, root.getJSONObject("metadata").getLong("exportedAt"))
        assertEquals("￥\"\\\n", root.getJSONObject("settings").getString("currencySymbol"))
        assertEquals("咖啡\n引号\"", root.getJSONObject("settings").getString("lastHistoryKeyword"))
        assertEquals(accountName, root.getJSONArray("accounts").getJSONObject(0).getString("name"))
        assertEquals(Long.MAX_VALUE, root.getJSONArray("accounts").getJSONObject(0).getLong("initialBalance"))
        assertEquals(purpose, root.getJSONArray("cashFlowRecords").getJSONObject(0).getString("purpose"))
        assertEquals(Long.MAX_VALUE, root.getJSONArray("cashFlowRecords").getJSONObject(0).getLong("amount"))
        assertTrue(root.getJSONArray("recurringReminders").getJSONObject(0).isNull("periodMonth"))
    }

    private fun buildUseCase(
        accountRepository: InMemoryAccountRepository = InMemoryAccountRepository(),
        transactionRepository: InMemoryTransactionRepository = InMemoryTransactionRepository(),
        reminderRepository: InMemoryRecurringReminderRepository = InMemoryRecurringReminderRepository(),
        settingsRepository: TestSettingsRepository = TestSettingsRepository(),
        reminderSettingsRepository: InMemoryAccountReminderSettingsRepository = InMemoryAccountReminderSettingsRepository(),
    ): BuildExportJsonUseCase {
        return BuildExportJsonUseCase(
            accountReminderSettingsRepository = reminderSettingsRepository,
            accountRepository = accountRepository,
            recurringReminderRepository = reminderRepository,
            settingsRepository = settingsRepository,
            transactionRepository = transactionRepository,
        )
    }
}
