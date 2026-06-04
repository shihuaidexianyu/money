package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.repository.InMemoryAccountReminderSettingsRepository
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryRecurringReminderRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.AmountColorMode
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.domain.model.BalanceAdjustmentRecord
import com.shihuaidexianyu.money.domain.model.BalanceUpdateRecord
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderWeekday
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.model.HomePeriod
import com.shihuaidexianyu.money.domain.model.RecurringReminder
import com.shihuaidexianyu.money.domain.model.ReminderPeriodType
import com.shihuaidexianyu.money.domain.model.ReminderType
import com.shihuaidexianyu.money.domain.model.ThemeMode
import com.shihuaidexianyu.money.domain.model.TransferRecord
import com.shihuaidexianyu.money.domain.repository.SettingsRepository
import com.shihuaidexianyu.money.domain.usecase.BuildExportJsonUseCase
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Test

class BuildExportJsonUseCaseTest {
    @Test
    fun `export json includes metadata settings records reminders and deletion markers`() = runBlocking {
        val accountRepository = InMemoryAccountRepository()
        val reminderSettingsRepository = InMemoryAccountReminderSettingsRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val reminderRepository = InMemoryRecurringReminderRepository()
        val accountId = accountRepository.createAccount(
            Account(
                name = "现金",
                initialBalance = 12_345,
                createdAt = 1L,
                colorName = "green",
            ),
        )
        val archivedAccountId = accountRepository.createAccount(
            Account(
                name = "旧账户",
                initialBalance = 9_999,
                createdAt = 2L,
                colorName = "red",
            ),
        )
        accountRepository.archiveAccount(archivedAccountId, 9L)
        reminderSettingsRepository.updateReminderConfig(
            accountId,
            BalanceUpdateReminderConfig(
                weekday = BalanceUpdateReminderWeekday.MONDAY,
                hour = 8,
                minute = 30,
            ),
        )
        val deletedCashFlowId = transactionRepository.insertCashFlowRecord(
            CashFlowRecord(
                accountId = accountId,
                direction = CashFlowDirection.OUTFLOW.value,
                amount = 123,
                purpose = "早餐",
                occurredAt = 3L,
                createdAt = 3L,
                updatedAt = 3L,
            ),
        )
        transactionRepository.softDeleteCashFlowRecord(deletedCashFlowId, 4L)
        val deletedTransferId = transactionRepository.insertTransferRecord(
            TransferRecord(
                fromAccountId = accountId,
                toAccountId = archivedAccountId,
                amount = 456,
                note = "转出",
                occurredAt = 4L,
                createdAt = 4L,
                updatedAt = 4L,
            ),
        )
        transactionRepository.softDeleteTransferRecord(deletedTransferId, 5L)
        transactionRepository.insertBalanceUpdateRecord(
            BalanceUpdateRecord(
                accountId = accountId,
                actualBalance = 12_000,
                systemBalanceBeforeUpdate = 12_345,
                delta = -345,
                occurredAt = 6L,
                createdAt = 6L,
            ),
        )
        transactionRepository.insertBalanceAdjustmentRecord(
            BalanceAdjustmentRecord(
                accountId = accountId,
                delta = 100,
                sourceUpdateRecordId = 0L,
                occurredAt = 7L,
                createdAt = 7L,
            ),
        )
        transactionRepository.insertBalanceAdjustmentRecord(
            BalanceAdjustmentRecord(
                accountId = accountId,
                delta = -50,
                sourceUpdateRecordId = 99L,
                occurredAt = 8L,
                createdAt = 8L,
            ),
        )
        reminderRepository.insertReminder(
            RecurringReminder(
                name = "订阅",
                type = ReminderType.SUBSCRIPTION.value,
                accountId = accountId,
                direction = CashFlowDirection.OUTFLOW.value,
                amount = 888,
                periodType = ReminderPeriodType.MONTHLY.value,
                periodValue = 15,
                periodMonth = null,
                nextDueAt = 10L,
                createdAt = 10L,
                updatedAt = 10L,
            ),
        )

        val json = BuildExportJsonUseCase(
            accountReminderSettingsRepository = reminderSettingsRepository,
            accountRepository = accountRepository,
            recurringReminderRepository = reminderRepository,
            settingsRepository = FakeSettingsRepository(AppSettings(currencySymbol = "元")),
            transactionRepository = transactionRepository,
        )(exportedAt = 42L)

        assertContainsJson(json, "\"metadata\":{\"schemaVersion\":1,\"databaseVersion\":7,\"exportedAt\":42}")
        assertContainsJson(json, "\"settings\":{\"homePeriod\":\"week\",\"currencySymbol\":\"元\"")
        assertContainsJson(json, "\"accounts\":[")
        assertContainsJson(json, "\"name\":\"旧账户\"")
        assertContainsJson(json, "\"initialBalance\":12345")
        assertContainsJson(json, "\"cashFlowRecords\":[")
        assertContainsJson(json, "\"amount\":123")
        assertContainsJson(json, "\"isDeleted\":true")
        assertContainsJson(json, "\"transferRecords\":[")
        assertContainsJson(json, "\"balanceUpdateRecords\":[")
        assertContainsJson(json, "\"actualBalance\":12000")
        assertContainsJson(json, "\"balanceAdjustmentRecords\":[")
        assertContainsJson(json, "\"sourceUpdateRecordId\":99")
        assertContainsJson(json, "\"recurringReminders\":[")
        assertContainsJson(json, "\"accountReminderConfigs\":[")
        assertContainsJson(json, "\"weekday\":\"monday\",\"hour\":8,\"minute\":30")
    }

    private fun assertContainsJson(json: String, expected: String) {
        assertTrue(json.contains(expected), "Missing JSON fragment: $expected\n$json")
    }

    private class FakeSettingsRepository(
        initial: AppSettings,
    ) : SettingsRepository {
        private val state = MutableStateFlow(initial)

        override fun observeSettings(): Flow<AppSettings> = state.asStateFlow()

        override suspend fun updateHomePeriod(period: HomePeriod) {
            state.value = state.value.copy(homePeriod = period)
        }

        override suspend fun updateCurrencySymbol(symbol: String) {
            state.value = state.value.copy(currencySymbol = symbol)
        }

        override suspend fun updateShowStaleMark(show: Boolean) {
            state.value = state.value.copy(showStaleMark = show)
        }

        override suspend fun updateThemeMode(themeMode: ThemeMode) {
            state.value = state.value.copy(themeMode = themeMode)
        }

        override suspend fun updateAmountColorMode(amountColorMode: AmountColorMode) {
            state.value = state.value.copy(amountColorMode = amountColorMode)
        }

        override suspend fun updateLastHistoryFilters(
            keyword: String,
            accountId: Long,
            dateStartAt: Long,
            dateEndAt: Long,
            minAmountText: String,
            maxAmountText: String,
            amountDirection: String,
        ) {
            state.value = state.value.copy(
                lastHistoryKeyword = keyword,
                lastHistoryAccountId = accountId,
                lastHistoryDateStartAt = dateStartAt,
                lastHistoryDateEndAt = dateEndAt,
                lastHistoryMinAmountText = minAmountText,
                lastHistoryMaxAmountText = maxAmountText,
                lastHistoryAmountDirection = amountDirection,
            )
        }
    }
}
