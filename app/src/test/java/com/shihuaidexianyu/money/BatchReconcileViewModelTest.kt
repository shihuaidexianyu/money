package com.shihuaidexianyu.money

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.data.repository.InMemoryAccountReminderSettingsRepository
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryPortableSettingsRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.time.ClockProvider
import com.shihuaidexianyu.money.domain.usecase.LedgerOperationIdFactory
import com.shihuaidexianyu.money.domain.usecase.UpdateBalanceUseCase
import com.shihuaidexianyu.money.ui.balance.BatchReconcileUiState
import com.shihuaidexianyu.money.ui.balance.BatchReconcileViewModel
import com.shihuaidexianyu.money.ui.balance.review
import com.shihuaidexianyu.money.ui.common.FormTerminalKind
import com.shihuaidexianyu.money.util.DateTimeTextFormatter
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BatchReconcileViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val viewModels = mutableListOf<BatchReconcileViewModel>()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = runBlocking {
        viewModels.forEach { it.viewModelScope.coroutineContext[Job]?.cancelAndJoin() }
        Dispatchers.resetMain()
    }

    @Test
    fun `confirm time is pre-filled from the clock when the draft is fresh`() = runBlocking {
        val now = 4_102_444_800_000L
        val vm = buildViewModel(clockProvider = testClockProvider(now))

        val state = awaitLoaded(vm)

        assertEquals(DateTimeTextFormatter.floorToMinute(now), state.confirmTimeMillis)
    }

    @Test
    fun `editing confirm time is dirty without selecting untouched accounts`() = runBlocking {
        val accountRepo = InMemoryAccountRepository()
        accountRepo.createAccount(Account(name = "现金", initialBalance = 10_000, createdAt = 1L))
        val vm = buildViewModel(accountRepo = accountRepo)
        val loaded = awaitLoaded(vm)
        assertEquals(false, loaded.isDirty)

        vm.updateConfirmTime(1_000_042L) // 16m 42s → floor to 960_000

        assertEquals(960_000L, vm.uiState.value.confirmTimeMillis)
        assertEquals(true, vm.uiState.value.isDirty)
        assertEquals(0, vm.uiState.value.selectedCount)
    }

    @Test
    fun `save writes balance updates at the picked confirm time`() = runBlocking {
        val accountRepo = InMemoryAccountRepository()
        accountRepo.createAccount(Account(name = "现金", initialBalance = 10_000, createdAt = 1L))
        val txnRepo = InMemoryTransactionRepository()
        val vm = buildViewModel(accountRepo = accountRepo, txnRepo = txnRepo)
        val loaded = awaitLoaded(vm)
        assertEquals(1, loaded.accounts.size)
        vm.updateConfirmTime(1_200_000L)
        withTimeout(5_000L) { vm.uiState.first { !it.isRecalculating } }
        vm.setAllSelected(true)

        vm.saveSelected()
        val terminal = withTimeout(5_000L) {
            vm.uiState.first { it.pendingTerminal != null }
        }

        assertEquals(FormTerminalKind.SAVED, terminal.pendingTerminal?.kind)
        val records = txnRepo.queryAllBalanceUpdateRecords()
        assertEquals(1, records.size)
        assertEquals(1_200_000L, records.single().occurredAt)
    }

    @Test
    fun `actual balances are validated as a whole before any account is written`() = runBlocking {
        val accounts = InMemoryAccountRepository()
        val first = accounts.createAccount(Account(name = "现金", initialBalance = 10_000, createdAt = 1L))
        val second = accounts.createAccount(Account(name = "银行卡", initialBalance = 20_000, createdAt = 1L))
        val transactions = InMemoryTransactionRepository()
        val vm = buildViewModel(accountRepo = accounts, txnRepo = transactions)
        val loaded = awaitLoaded(vm)
        assertEquals(0, loaded.selectedCount)
        vm.updateActualBalance(first, "95.50")
        vm.updateActualBalance(second, "20+")
        vm.saveSelected()
        assertEquals(0, transactions.queryAllBalanceUpdateRecords().size)
        assertEquals(true, vm.uiState.value.accounts.single { it.accountId == second }.amountError)
        vm.updateActualBalance(second, "-1.25")
        assertEquals(-125L, vm.uiState.value.accounts.single { it.accountId == second }.actualBalance)
        vm.saveSelected()
        withTimeout(5_000L) { vm.uiState.first { it.pendingTerminal != null } }
        val saved = transactions.queryAllBalanceUpdateRecords().associateBy { it.accountId }
        assertEquals(9_550L, saved.getValue(first).actualBalance)
        assertEquals(-450L, saved.getValue(first).delta)
        assertEquals(-125L, saved.getValue(second).actualBalance)
    }

    @Test
    fun `actual balance draft survives recreation and a stale review cannot be saved`() = runBlocking {
        val accounts = InMemoryAccountRepository()
        val accountId = accounts.createAccount(Account(name = "现金", initialBalance = 10_000, createdAt = 1L))
        val transactions = InMemoryTransactionRepository()
        val handle = SavedStateHandle()
        val first = buildViewModel(accountRepo = accounts, txnRepo = transactions, handle = handle)
        awaitLoaded(first)
        first.updateActualBalance(accountId, "95.50")
        val recreated = buildViewModel(accountRepo = accounts, txnRepo = transactions, handle = handle)
        val restored = awaitLoaded(recreated)
        assertEquals("95.50", restored.accounts.single().actualBalanceText)
        assertEquals(1, restored.selectedCount)
        val review = restored.review()
        recreated.updateActualBalance(accountId, "90")
        recreated.saveSelected(review)
        assertEquals(0, transactions.queryAllBalanceUpdateRecords().size)
        assertEquals(null, recreated.uiState.value.pendingTerminal)
    }

    // buildItems uses a real dispatcher; await the emission rather than only the test scheduler.
    private suspend fun awaitLoaded(vm: BatchReconcileViewModel): BatchReconcileUiState =
        withTimeout(5_000L) {
            vm.uiState.first { !it.isLoading || it.loadErrorMessageRes != null }
        }.also { state ->
            assertEquals(null, state.loadErrorMessageRes)
        }

    private fun buildViewModel(
        accountRepo: InMemoryAccountRepository = InMemoryAccountRepository(),
        txnRepo: InMemoryTransactionRepository = InMemoryTransactionRepository(),
        clockProvider: ClockProvider = testClockProvider,
        handle: SavedStateHandle = SavedStateHandle(),
    ): BatchReconcileViewModel {
        val refreshUseCase = RefreshAccountActivityStateUseCase(accountRepo, txnRepo)
        val resolveUseCase = ResolveBalanceUpdateContextUseCase(accountRepo, txnRepo)
        return BatchReconcileViewModel(
            accountReminderSettingsRepository = InMemoryAccountReminderSettingsRepository(),
            accountRepository = accountRepo,
            portableSettingsRepository = InMemoryPortableSettingsRepository(),
            transactionRepository = txnRepo,
            calculateAccountBalancesUseCase = CalculateAccountBalancesUseCase(txnRepo, clockProvider),
            updateBalanceUseCase = UpdateBalanceUseCase(
                accountRepo,
                txnRepo,
                resolveUseCase,
                refreshUseCase,
                clockProvider,
            ),
            savedStateHandle = handle,
            operationIdFactory = LedgerOperationIdFactory { testOperationId() },
            clockProvider = clockProvider,
        ).also { viewModels += it }
    }
}
