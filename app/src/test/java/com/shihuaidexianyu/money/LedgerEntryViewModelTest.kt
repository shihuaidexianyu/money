package com.shihuaidexianyu.money

import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryDevicePreferencesRepository
import com.shihuaidexianyu.money.data.repository.InMemoryRecurringReminderRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.AccountKind
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.model.DevicePreferences
import com.shihuaidexianyu.money.domain.model.LedgerInsertResult
import com.shihuaidexianyu.money.domain.model.RecurringReminder
import com.shihuaidexianyu.money.domain.model.ReminderPeriodType
import com.shihuaidexianyu.money.domain.model.ReminderType
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.LedgerAggregateRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.usecase.CreateCashFlowRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.CreateTransferRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.LedgerOperationIdFactory
import com.shihuaidexianyu.money.domain.usecase.ProcessDueReminderUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateBalanceUseCase
import com.shihuaidexianyu.money.ui.record.LedgerEntryKind
import com.shihuaidexianyu.money.ui.record.LedgerEntryStep
import com.shihuaidexianyu.money.ui.record.LedgerEntryViewModel
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LedgerEntryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val now = 1_800_000L
    private val clock = testClockProvider(now)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
            override fun postToMainThread(runnable: Runnable) = runnable.run()
            override fun isMainThread() = true
        })
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        ArchTaskExecutor.getInstance().setDelegate(null)
    }

    @Test
    fun `global entry requires explicit kind and account despite recent accounts`() = runTest(dispatcher) {
        val f = fixture()
        f.preferences.updateRecentAccountIds(listOf(2L, 1L))
        val vm = f.vm()
        advanceUntilIdle()
        assertEquals(LedgerEntryStep.TYPE, vm.uiState.value.step)
        assertNull(vm.uiState.value.accountId)
        assertEquals(2L, vm.uiState.value.accounts.first().id)
        vm.chooseKind(LedgerEntryKind.EXPENSE)
        assertEquals(LedgerEntryStep.ACCOUNT, vm.uiState.value.step)
        assertNull(vm.uiState.value.accountId)
    }

    @Test
    fun `explicit cash account skips known steps but preserves editing navigation`() = runTest(dispatcher) {
        val f = fixture()
        val vm = f.vm(LedgerEntryKind.INCOME, 2L)
        advanceUntilIdle()
        assertEquals(LedgerEntryStep.AMOUNT, vm.uiState.value.step)
        vm.updateAmount("12.34")
        assertTrue(vm.back())
        assertEquals(LedgerEntryStep.ACCOUNT, vm.uiState.value.step)
        vm.chooseAccount(1L, LedgerEntryStep.ACCOUNT)
        assertEquals("12.34", vm.uiState.value.amountText)
    }

    @Test
    fun `explicit transfer skips only the known source account`() = runTest(dispatcher) {
        val vm = fixture().vm(LedgerEntryKind.TRANSFER, 1L)
        advanceUntilIdle()
        assertEquals(LedgerEntryStep.TO_ACCOUNT, vm.uiState.value.step)
        assertNull(vm.uiState.value.toAccountId)
        vm.chooseAccount(1L, LedgerEntryStep.TO_ACCOUNT)
        assertEquals(LedgerEntryStep.TO_ACCOUNT, vm.uiState.value.step)
        assertNotNull(vm.uiState.value.accountError)
        vm.chooseAccount(2L, LedgerEntryStep.TO_ACCOUNT)
        assertEquals(LedgerEntryStep.AMOUNT, vm.uiState.value.step)
    }

    @Test
    fun `invalid explicit account returns to selection rather than silently substituting`() = runTest(dispatcher) {
        val vm = fixture().vm(LedgerEntryKind.EXPENSE, 999L)
        advanceUntilIdle()
        assertNull(vm.uiState.value.accountId)
        assertEquals(LedgerEntryStep.ACCOUNT, vm.uiState.value.step)
        assertNotNull(vm.uiState.value.accountError)
    }

    @Test
    fun `income and expense take four stages and update account balances correctly`() = runTest(dispatcher) {
        listOf(LedgerEntryKind.INCOME, LedgerEntryKind.EXPENSE).forEach { kind ->
            val f = fixture()
            val vm = f.vm()
            advanceUntilIdle()
            vm.chooseKind(kind)
            vm.chooseAccount(1L, LedgerEntryStep.ACCOUNT)
            vm.updateAmount("12.34")
            vm.continueToConfirmation()
            assertTrue(f.ledger.queryAllCashFlowRecords().isEmpty())
            assertEquals(LedgerEntryStep.CONFIRM, vm.uiState.value.step)
            vm.save()
            advanceUntilIdle()
            val record = f.ledger.queryAllCashFlowRecords().single()
            assertEquals(1234L, record.amount)
            assertEquals(if (kind == LedgerEntryKind.INCOME) CashFlowDirection.INFLOW.value else CashFlowDirection.OUTFLOW.value, record.direction)
            assertEquals(if (kind == LedgerEntryKind.INCOME) 11234L else 8766L, CalculateCurrentBalanceUseCase(f.accounts, f.ledger, clock)(1L))
        }
    }

    @Test
    fun `transfer is one record with two balance effects and no cash flow`() = runTest(dispatcher) {
        val f = fixture()
        val vm = f.vm(LedgerEntryKind.TRANSFER, 1L)
        advanceUntilIdle()
        vm.chooseAccount(2L, LedgerEntryStep.TO_ACCOUNT)
        vm.updateAmount("15.50")
        vm.continueToConfirmation()
        vm.save()
        advanceUntilIdle()
        assertEquals(1550L, f.ledger.queryAllTransferRecords().single().amount)
        assertTrue(f.ledger.queryAllCashFlowRecords().isEmpty())
        val calculate = CalculateCurrentBalanceUseCase(f.accounts, f.ledger, clock)
        assertEquals(8450L, calculate(1L))
        assertEquals(21550L, calculate(2L))
    }

    @Test
    fun `reconciliation accepts positive zero negative and unchanged balances`() = runTest(dispatcher) {
        listOf("120.00" to 12000L, "0" to 0L, "-15.50" to -1550L, "100" to 10000L).forEach { (text, amount) ->
            val f = fixture()
            val vm = f.vm(LedgerEntryKind.RECONCILE, 1L)
            advanceUntilIdle()
            assertEquals("", vm.uiState.value.inputText)
            vm.updateAmount(text)
            vm.continueToConfirmation()
            advanceUntilIdle()
            assertEquals(amount - 10000L, vm.uiState.value.delta)
            vm.save()
            advanceUntilIdle()
            val record = f.ledger.queryAllBalanceUpdateRecords().single()
            assertEquals(amount, record.actualBalance)
            assertEquals(amount - 10000L, record.delta)
            assertEquals(amount, CalculateCurrentBalanceUseCase(f.accounts, f.ledger, clock)(1L))
            assertTrue(f.ledger.queryAllCashFlowRecords().isEmpty())
        }
    }

    @Test
    fun `investment reconciliation retains investment result semantics`() = runTest(dispatcher) {
        val f = fixture(investment = true)
        val vm = f.vm(LedgerEntryKind.RECONCILE, 1L)
        advanceUntilIdle()
        vm.updateAmount("125")
        vm.continueToConfirmation()
        advanceUntilIdle()
        vm.save()
        advanceUntilIdle()
        assertTrue(requireNotNull(vm.uiState.value.pendingTerminal?.balanceResult).isInvestmentAccount)
        assertEquals(2500L, f.ledger.queryAllBalanceUpdateRecords().single().delta)
    }

    @Test
    fun `save is ignored before final confirmation and rapid repeated actions cannot advance twice`() = runTest(dispatcher) {
        val f = fixture()
        val vm = f.vm()
        advanceUntilIdle()
        vm.save()
        vm.chooseKind(LedgerEntryKind.INCOME)
        vm.chooseKind(LedgerEntryKind.EXPENSE)
        assertEquals(LedgerEntryKind.INCOME, vm.uiState.value.kind)
        vm.chooseAccount(1L, LedgerEntryStep.ACCOUNT)
        vm.chooseAccount(2L, LedgerEntryStep.ACCOUNT)
        assertEquals(1L, vm.uiState.value.accountId)
        vm.updateAmount("10")
        vm.save()
        assertTrue(f.ledger.queryAllCashFlowRecords().isEmpty())
        vm.continueToConfirmation()
        vm.continueToConfirmation()
        vm.save()
        vm.save()
        vm.updateNote("不应写入")
        assertTrue(vm.uiState.value.isSaving)
        advanceUntilIdle()
        assertEquals(1, f.ledger.queryAllCashFlowRecords().size)
        assertEquals("", f.ledger.queryAllCashFlowRecords().single().note)
        vm.ackTerminal(requireNotNull(vm.uiState.value.pendingTerminal).token)
        vm.save()
        advanceUntilIdle()
        assertEquals(1, f.ledger.queryAllCashFlowRecords().size)
    }

    @Test
    fun `invalid cash amounts stay on amount page without writing`() = runTest(dispatcher) {
        val f = fixture()
        val vm = f.vm(LedgerEntryKind.EXPENSE, 1L)
        advanceUntilIdle()
        listOf("", "0", "-1", "1.234", "abc", "99999999999999999999", "1+").forEach { text ->
            vm.updateAmount(text)
            vm.continueToConfirmation()
            assertEquals(LedgerEntryStep.AMOUNT, vm.uiState.value.step, text)
            assertNotNull(vm.uiState.value.amountError, text)
        }
        assertTrue(f.ledger.queryAllCashFlowRecords().isEmpty())
    }

    @Test
    fun `amount calculator expression is resolved before confirmation`() = runTest(dispatcher) {
        val f = fixture()
        val vm = f.vm(LedgerEntryKind.EXPENSE, 1L)
        advanceUntilIdle()
        vm.updateAmount("12.50+3.25")
        vm.continueToConfirmation()
        assertEquals(1575L, vm.uiState.value.amount)
        vm.save()
        advanceUntilIdle()
        assertEquals(1575L, f.ledger.queryAllCashFlowRecords().single().amount)
    }

    @Test
    fun `back and changing type preserve notes time accounts and separate actual balance input`() = runTest(dispatcher) {
        val vm = fixture().vm(LedgerEntryKind.EXPENSE, 1L)
        advanceUntilIdle()
        vm.updateAmount("12.34")
        vm.continueToConfirmation()
        vm.updateNote("午餐")
        vm.updateOccurredAt(120042L)
        repeat(3) { vm.back() }
        assertEquals(LedgerEntryStep.TYPE, vm.uiState.value.step)
        vm.chooseKind(LedgerEntryKind.RECONCILE)
        vm.chooseAccount(1L, LedgerEntryStep.ACCOUNT)
        assertEquals("", vm.uiState.value.inputText)
        vm.updateAmount("-5")
        repeat(2) { vm.back() }
        vm.chooseKind(LedgerEntryKind.INCOME)
        vm.chooseAccount(2L, LedgerEntryStep.ACCOUNT)
        assertEquals("12.34", vm.uiState.value.inputText)
        assertEquals("-5", vm.uiState.value.actualBalanceText)
        assertEquals("午餐", vm.uiState.value.note)
        assertEquals(120000L, vm.uiState.value.occurredAtMillis)
        assertTrue(vm.uiState.value.timeEdited)
    }

    @Test
    fun `full draft survives serializable process restoration at confirmation`() = runTest(dispatcher) {
        val f = fixture()
        val handle = SavedStateHandle()
        val vm = f.vm(LedgerEntryKind.TRANSFER, 1L, handle)
        advanceUntilIdle()
        vm.chooseAccount(2L, LedgerEntryStep.TO_ACCOUNT)
        vm.updateAmount("15")
        vm.continueToConfirmation()
        vm.updateNote("转存")
        vm.updateOccurredAt(120000L)
        val restored = f.vm(handle = roundTrip(handle))
        advanceUntilIdle()
        assertEquals(LedgerEntryKind.TRANSFER, restored.uiState.value.kind)
        assertEquals(LedgerEntryStep.CONFIRM, restored.uiState.value.step)
        assertEquals(1L, restored.uiState.value.accountId)
        assertEquals(2L, restored.uiState.value.toAccountId)
        assertEquals("15", restored.uiState.value.amountText)
        assertEquals("转存", restored.uiState.value.note)
        assertEquals(120000L, restored.uiState.value.occurredAtMillis)
        restored.save()
        advanceUntilIdle()
        assertEquals(1, f.ledger.queryAllTransferRecords().size)
    }

    @Test
    fun `save terminal survives restoration and cannot replay after acknowledgement`() = runTest(dispatcher) {
        val f = fixture()
        val handle = SavedStateHandle()
        val vm = f.vm(LedgerEntryKind.INCOME, 1L, handle)
        advanceUntilIdle()
        vm.updateAmount("1")
        vm.continueToConfirmation()
        vm.save()
        advanceUntilIdle()
        val restored = f.vm(handle = roundTrip(handle))
        advanceUntilIdle()
        val terminal = requireNotNull(restored.uiState.value.pendingTerminal)
        restored.ackTerminal("wrong-token")
        assertEquals(terminal, restored.uiState.value.pendingTerminal)
        restored.ackTerminal(terminal.token)
        restored.save()
        advanceUntilIdle()
        assertTrue(restored.uiState.value.completed)
        assertEquals(1, f.ledger.queryAllCashFlowRecords().size)
    }

    @Test
    fun `future time is rejected and corrected time permits saving`() = runTest(dispatcher) {
        val f = fixture()
        val vm = f.vm(LedgerEntryKind.INCOME, 1L)
        advanceUntilIdle()
        vm.updateAmount("1")
        vm.continueToConfirmation()
        vm.updateOccurredAt(now + 60000L)
        vm.save()
        assertNotNull(vm.uiState.value.occurredAtError)
        assertTrue(f.ledger.queryAllCashFlowRecords().isEmpty())
        vm.updateOccurredAt(now)
        vm.save()
        advanceUntilIdle()
        assertEquals(1, f.ledger.queryAllCashFlowRecords().size)
    }

    @Test
    fun `time before account creation fails without leaving confirmation`() = runTest(dispatcher) {
        val f = fixture(createdAt = 60000L)
        val vm = f.vm(LedgerEntryKind.EXPENSE, 1L)
        advanceUntilIdle()
        vm.updateAmount("1")
        vm.continueToConfirmation()
        vm.updateOccurredAt(0L)
        vm.save()
        advanceUntilIdle()
        assertEquals(LedgerEntryStep.CONFIRM, vm.uiState.value.step)
        assertNotNull(vm.uiState.value.occurredAtError)
        assertTrue(f.ledger.queryAllCashFlowRecords().isEmpty())
    }

    @Test
    fun `optional notes are trimmed and overlong notes block save`() = runTest(dispatcher) {
        val f = fixture()
        val vm = f.vm(LedgerEntryKind.EXPENSE, 1L)
        advanceUntilIdle()
        vm.updateAmount("1")
        vm.continueToConfirmation()
        vm.updateNote("记".repeat(201))
        vm.save()
        assertNotNull(vm.uiState.value.noteError)
        assertFalse(vm.uiState.value.isSaving)
        vm.updateNote("  午餐  ")
        vm.save()
        advanceUntilIdle()
        assertEquals("午餐", f.ledger.queryAllCashFlowRecords().single().note)
    }

    @Test
    fun `hidden open accounts are selectable and closed accounts are excluded`() = runTest(dispatcher) {
        val f = fixture()
        val hiddenId = f.accounts.createAccount(Account(name = "隐藏", initialBalance = 0, createdAt = 0, isHidden = true))
        f.accounts.closeAccount(2L, 1L)
        val vm = f.vm()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.accounts.any { it.id == hiddenId && it.isHidden })
        assertTrue(vm.uiState.value.accounts.none { it.id == 2L })
        vm.chooseKind(LedgerEntryKind.INCOME)
        vm.chooseAccount(hiddenId, LedgerEntryStep.ACCOUNT)
        assertEquals(LedgerEntryStep.AMOUNT, vm.uiState.value.step)
    }

    @Test
    fun `closing selected account while drafting returns to account page preserving input`() = runTest(dispatcher) {
        val f = fixture()
        val vm = f.vm(LedgerEntryKind.INCOME, 1L)
        advanceUntilIdle()
        vm.updateAmount("12")
        vm.continueToConfirmation()
        f.accounts.closeAccount(1L, now)
        vm.reloadAccounts()
        advanceUntilIdle()
        assertEquals(LedgerEntryStep.ACCOUNT, vm.uiState.value.step)
        assertNull(vm.uiState.value.accountId)
        assertEquals("12", vm.uiState.value.amountText)
        assertNotNull(vm.uiState.value.accountError)
    }

    @Test
    fun `closed account mutation failure returns to selection and creates no record`() = runTest(dispatcher) {
        val f = fixture()
        val vm = f.vm(LedgerEntryKind.INCOME, 1L)
        advanceUntilIdle()
        vm.updateAmount("12")
        vm.continueToConfirmation()
        f.accounts.closeAccount(1L, now)
        vm.save()
        advanceUntilIdle()
        assertEquals(LedgerEntryStep.ACCOUNT, vm.uiState.value.step)
        assertTrue(f.ledger.queryAllCashFlowRecords().isEmpty())
    }

    @Test
    fun `zero and one account transfer entry never silently creates a destination`() = runTest(dispatcher) {
        val f = fixture()
        f.accounts.closeAccount(2L, now)
        val vm = f.vm()
        advanceUntilIdle()
        vm.chooseKind(LedgerEntryKind.TRANSFER)
        assertEquals(LedgerEntryStep.TYPE, vm.uiState.value.step)
        assertNotNull(vm.uiState.value.accountError)
        f.accounts.closeAccount(1L, now)
        vm.reloadAccounts()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.accounts.isEmpty())
        vm.chooseKind(LedgerEntryKind.EXPENSE)
        vm.chooseAccount(1L, LedgerEntryStep.ACCOUNT)
        assertEquals(LedgerEntryStep.ACCOUNT, vm.uiState.value.step)
        vm.save()
        assertTrue(f.ledger.queryAllCashFlowRecords().isEmpty())
    }

    @Test
    fun `newly created account appears on reload without discarding stage`() = runTest(dispatcher) {
        val f = fixture()
        val vm = f.vm(LedgerEntryKind.INCOME)
        advanceUntilIdle()
        val id = f.accounts.createAccount(Account(name = "新账户", initialBalance = 0L, createdAt = now))
        vm.reloadAccounts()
        advanceUntilIdle()
        assertEquals(LedgerEntryStep.ACCOUNT, vm.uiState.value.step)
        assertTrue(vm.uiState.value.accounts.any { it.id == id })
        assertNull(vm.uiState.value.accountId)
    }

    @Test
    fun `all balance shortcuts require an explicit action`() = runTest(dispatcher) {
        val f = fixture()
        val transfer = f.vm(LedgerEntryKind.TRANSFER, 1L)
        advanceUntilIdle()
        transfer.chooseAccount(2L, LedgerEntryStep.TO_ACCOUNT)
        assertEquals("", transfer.uiState.value.amountText)
        transfer.useAccountBalance()
        assertEquals("100.00", transfer.uiState.value.amountText)
        val reconcile = f.vm(LedgerEntryKind.RECONCILE, 1L)
        advanceUntilIdle()
        reconcile.useAccountBalance()
        assertEquals("100.00", reconcile.uiState.value.actualBalanceText)
    }

    @Test
    fun `reconciliation date changes reproject evidence without overwriting actual input`() = runTest(dispatcher) {
        val f = fixture()
        f.ledger.insertCashFlowRecord(CashFlowRecord(accountId = 1L, direction = "inflow", amount = 2500L, note = "", occurredAt = 120000L, createdAt = now, updatedAt = now, operationId = "old-income"))
        val vm = f.vm(LedgerEntryKind.RECONCILE, 1L)
        advanceUntilIdle()
        vm.updateAmount("150")
        vm.continueToConfirmation()
        advanceUntilIdle()
        assertEquals(12500L, vm.uiState.value.systemBalance)
        vm.updateOccurredAt(60000L)
        advanceUntilIdle()
        assertEquals(10000L, vm.uiState.value.systemBalance)
        assertEquals("150", vm.uiState.value.actualBalanceText)
        assertEquals(5000L, vm.uiState.value.delta)
    }

    @Test
    fun `account load failure exposes retry and does not auto select on recovery`() = runTest(dispatcher) {
        val f = fixture()
        var fail = true
        val accounts = object : AccountRepository by f.accounts {
            override suspend fun queryOpenAccounts(): List<Account> {
                if (fail) error("读取失败")
                return f.accounts.queryOpenAccounts()
            }
        }
        val vm = f.vm(accountRepository = accounts)
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.loadErrorMessageRes)
        fail = false
        vm.reloadAccounts()
        advanceUntilIdle()
        assertNull(vm.uiState.value.loadErrorMessageRes)
        assertNull(vm.uiState.value.accountId)
    }

    @Test
    fun `failure retry and recreation reuse operation id and write only once`() = runTest(dispatcher) {
        val f = fixture()
        var fail = true
        val calls = mutableListOf<String>()
        val repository = object : TransactionRepository by f.ledger, LedgerAggregateRepository by f.ledger {
            override suspend fun insertCashFlowRecord(record: CashFlowRecord): LedgerInsertResult {
                calls += record.operationId
                if (fail) { fail = false; error("模拟保存失败") }
                return f.ledger.insertCashFlowRecord(record)
            }
        }
        val handle = SavedStateHandle()
        val vm = f.vm(LedgerEntryKind.INCOME, 1L, handle, repository)
        advanceUntilIdle()
        vm.updateAmount("1")
        vm.continueToConfirmation()
        vm.effectFlow.test {
            vm.save()
            advanceUntilIdle()
            assertEquals("模拟保存失败", awaitItem().message)
        }
        assertFalse(vm.uiState.value.isSaving)
        assertEquals(LedgerEntryStep.CONFIRM, vm.uiState.value.step)
        val restored = f.vm(handle = roundTrip(handle), repository = repository)
        advanceUntilIdle()
        restored.save()
        advanceUntilIdle()
        assertEquals(listOf("entry-operation", "entry-operation"), calls)
        assertEquals(1, f.ledger.queryAllCashFlowRecords().size)
    }

    @Test
    fun `reminder prefills amount note locks type and advances due date once`() = runTest(dispatcher) {
        val f = fixture()
        val id = f.reminders.insertReminder(RecurringReminder(
            name = "订阅", type = ReminderType.SUBSCRIPTION.value, accountId = 1L,
            direction = CashFlowDirection.OUTFLOW.value, amount = 1200L,
            periodType = ReminderPeriodType.CUSTOM_DAYS.value, periodValue = 1,
            periodMonth = null, nextDueAt = now, anchorDueAt = now, createdAt = 0L, updatedAt = 0L,
        ))
        val vm = f.vm(LedgerEntryKind.EXPENSE, 1L, reminderId = id)
        advanceUntilIdle()
        assertEquals("12.00", vm.uiState.value.amountText)
        assertEquals("订阅", vm.uiState.value.note)
        assertTrue(vm.uiState.value.kindLocked)
        vm.back()
        assertFalse(vm.back())
        vm.chooseKind(LedgerEntryKind.INCOME)
        assertEquals(LedgerEntryKind.EXPENSE, vm.uiState.value.kind)
        vm.chooseAccount(2L, LedgerEntryStep.ACCOUNT)
        vm.continueToConfirmation()
        vm.save()
        vm.save()
        advanceUntilIdle()
        val record = f.ledger.queryAllCashFlowRecords().single()
        assertEquals(2L, record.accountId)
        assertEquals("cash:reminder:$id:$now", record.operationId)
        assertTrue(requireNotNull(f.reminders.getReminderById(id)).nextDueAt > now)
    }

    private suspend fun fixture(investment: Boolean = false, createdAt: Long = 0L): Fixture {
        val accounts = InMemoryAccountRepository()
        accounts.createAccount(Account(name = "现金", initialBalance = 10000L, createdAt = createdAt, kind = if (investment) AccountKind.INVESTMENT else AccountKind.FUNDING))
        accounts.createAccount(Account(name = "银行卡", initialBalance = 20000L, createdAt = createdAt))
        return Fixture(accounts, InMemoryTransactionRepository(), InMemoryDevicePreferencesRepository(DevicePreferences()), InMemoryRecurringReminderRepository())
    }

    @Test
    fun `every stage can be restored without advancing or writing`() = runTest(dispatcher) {
        LedgerEntryStep.entries.forEach { step ->
            val f = fixture()
            val handle = SavedStateHandle()
            val vm = f.vm(handle = handle)
            advanceUntilIdle()
            if (step != LedgerEntryStep.TYPE) vm.chooseKind(if (step == LedgerEntryStep.TO_ACCOUNT) LedgerEntryKind.TRANSFER else LedgerEntryKind.EXPENSE)
            if (step.number >= 3 || step == LedgerEntryStep.TO_ACCOUNT) vm.chooseAccount(1L, LedgerEntryStep.ACCOUNT)
            if (step.number >= 3) vm.updateAmount("12")
            if (step == LedgerEntryStep.CONFIRM) vm.continueToConfirmation()
            val restored = f.vm(handle = roundTrip(handle))
            advanceUntilIdle()
            assertEquals(step, restored.uiState.value.step)
            assertTrue(f.ledger.queryAllCashFlowRecords().isEmpty())
            assertTrue(f.ledger.queryAllTransferRecords().isEmpty())
        }
    }

    @Test
    fun `changing ledger while reconciliation confirmation is open requires confirmation again`() = runTest(dispatcher) {
        val f = fixture()
        val vm = f.vm(LedgerEntryKind.RECONCILE, 1L)
        advanceUntilIdle()
        vm.updateAmount("120")
        vm.continueToConfirmation()
        advanceUntilIdle()
        f.ledger.insertCashFlowRecord(CashFlowRecord(accountId = 1L, direction = "inflow", amount = 1000L, note = "", occurredAt = now, createdAt = now, updatedAt = now, operationId = "concurrent-income"))
        vm.effectFlow.test {
            vm.save()
            advanceUntilIdle()
            assertTrue(awaitItem().message.contains("账面余额已变化"))
        }
        assertTrue(f.ledger.queryAllBalanceUpdateRecords().isEmpty())
        assertEquals(11000L, vm.uiState.value.systemBalance)
        assertEquals(1000L, vm.uiState.value.delta)
        vm.save()
        advanceUntilIdle()
        assertEquals(1000L, f.ledger.queryAllBalanceUpdateRecords().single().delta)
    }

    @Test
    fun `preview failure blocks reconciliation save and supports retry`() = runTest(dispatcher) {
        val f = fixture()
        var fail = false
        val repository = object : TransactionRepository by f.ledger, LedgerAggregateRepository by f.ledger {
            override suspend fun queryBefore(accounts: List<Account>, endExclusive: Long, excludingBalanceUpdateId: Long?): Map<Long, com.shihuaidexianyu.money.domain.model.AccountLedgerAggregate> {
                if (fail) error("余额读取失败")
                return f.ledger.queryBefore(accounts, endExclusive, excludingBalanceUpdateId)
            }
        }
        val vm = f.vm(LedgerEntryKind.RECONCILE, 1L, repository = repository)
        advanceUntilIdle()
        fail = true
        vm.updateAmount("120")
        vm.continueToConfirmation()
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.previewError)
        vm.save()
        advanceUntilIdle()
        assertTrue(f.ledger.queryAllBalanceUpdateRecords().isEmpty())
        fail = false
        vm.refreshPreview()
        advanceUntilIdle()
        vm.save()
        advanceUntilIdle()
        assertEquals(1, f.ledger.queryAllBalanceUpdateRecords().size)
    }

    @Test
    fun `overflowing reconciliation delta never wraps or writes and can be corrected`() = runTest(dispatcher) {
        val f = fixture()
        val account = requireNotNull(f.accounts.getAccountById(1L))
        f.accounts.updateAccount(account.copy(initialBalance = Long.MIN_VALUE))
        val vm = f.vm(LedgerEntryKind.RECONCILE, 1L)
        advanceUntilIdle()
        vm.updateAmount("0")
        vm.continueToConfirmation()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.deltaOverflow)
        assertNull(vm.uiState.value.delta)
        vm.save()
        advanceUntilIdle()
        assertTrue(f.ledger.queryAllBalanceUpdateRecords().isEmpty())
        vm.back()
        vm.useAccountBalance()
        vm.continueToConfirmation()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.deltaOverflow)
        assertEquals(0L, vm.uiState.value.delta)
        vm.save()
        advanceUntilIdle()
        assertEquals(0L, f.ledger.queryAllBalanceUpdateRecords().single().delta)
    }

    private inner class Fixture(
        val accounts: InMemoryAccountRepository,
        val ledger: InMemoryTransactionRepository,
        val preferences: InMemoryDevicePreferencesRepository,
        val reminders: InMemoryRecurringReminderRepository,
    ) {
        fun vm(
            kind: LedgerEntryKind? = null, accountId: Long? = null,
            handle: SavedStateHandle = SavedStateHandle(), repository: TransactionRepository = ledger,
            accountRepository: AccountRepository = accounts, reminderId: Long? = null,
        ): LedgerEntryViewModel {
            val refresh = RefreshAccountActivityStateUseCase(accountRepository, repository)
            val context = ResolveBalanceUpdateContextUseCase(accountRepository, repository)
            return LedgerEntryViewModel(
                initialKind = kind, initialAccountId = accountId,
                prefillAmount = if (reminderId != null) 1200L else null,
                prefillNote = if (reminderId != null) "订阅" else null,
                reminderId = reminderId, expectedDueAt = if (reminderId != null) now else null,
                accountRepository = accountRepository, transactionRepository = repository,
                devicePreferencesRepository = preferences,
                calculateAccountBalancesUseCase = CalculateAccountBalancesUseCase(repository, clock),
                resolveBalanceUpdateContextUseCase = context,
                createCashFlowRecordUseCase = CreateCashFlowRecordUseCase(accountRepository, repository, refresh, clock),
                createTransferRecordUseCase = CreateTransferRecordUseCase(accountRepository, repository, refresh, clock),
                updateBalanceUseCase = UpdateBalanceUseCase(accountRepository, repository, context, refresh, clock),
                processDueReminderUseCase = ProcessDueReminderUseCase(accountRepository, repository, reminders, refresh, clock, testZoneIdProvider()),
                savedStateHandle = handle, operationIdFactory = LedgerOperationIdFactory { "entry-operation" }, clockProvider = clock,
            )
        }
    }

    private fun roundTrip(handle: SavedStateHandle): SavedStateHandle {
        val values = handle.keys().associateWith { handle.get<Any>(it) }
        val bytes = ByteArrayOutputStream().also { output -> ObjectOutputStream(output).use { it.writeObject(values) } }.toByteArray()
        @Suppress("UNCHECKED_CAST")
        val restored = ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() } as Map<String, Any?>
        return SavedStateHandle(restored)
    }
}
