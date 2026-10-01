package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryDevicePreferencesRepository
import com.shihuaidexianyu.money.data.repository.InMemoryPortableSettingsRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.HistoryPageCursor
import com.shihuaidexianyu.money.domain.model.HistoryRecord
import com.shihuaidexianyu.money.domain.model.HistoryRecordFilters
import com.shihuaidexianyu.money.domain.model.HistoryRecordType
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.ui.history.HistoryFilterState
import com.shihuaidexianyu.money.ui.history.HistoryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryFilterDraftTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a filter draft produces one query and preserves the active search`() = runTest(dispatcher) {
        val repository = RecordingRepository()
        val vm = viewModel(repository)
        advanceUntilIdle()
        vm.updateKeyword("午餐")
        advanceUntilIdle()
        repository.queries.clear()
        vm.applyFilterDraft(HistoryFilterState(excludeKeyword = "退款", selectedAccountId = 1L,
            selectedRecordTypes = setOf(HistoryRecordType.CASH_FLOW), minAmountText = "10", maxAmountText = "20"))
        advanceUntilIdle()
        assertEquals(1, repository.queries.size)
        val query = repository.queries.single()
        assertEquals("午餐", query.keyword)
        assertEquals("退款", query.excludeKeyword)
        assertEquals(1L, query.accountId)
        assertEquals(1_000L, query.minAmount)
        assertEquals(2_000L, query.maxAmount)
    }

    @Test
    fun `invalid amounts are not committed and a locked account cannot be changed by the editor`() = runTest(dispatcher) {
        val repository = RecordingRepository()
        val vm = viewModel(repository, lockedAccountId = 7L)
        advanceUntilIdle()
        val queryCount = repository.queries.size
        vm.applyFilterDraft(HistoryFilterState(minAmountText = "20", maxAmountText = "10"))
        advanceUntilIdle()
        assertEquals(queryCount, repository.queries.size)
        assertEquals("", vm.uiState.value.minAmountText)
        vm.applyFilterDraft(HistoryFilterState(selectedAccountId = 9L, excludeKeyword = "退款"))
        advanceUntilIdle()
        assertEquals(7L, repository.queries.last().accountId)
    }

    private fun viewModel(repository: TransactionRepository, lockedAccountId: Long? = null) = HistoryViewModel(
        InMemoryAccountRepository(), repository, InMemoryPortableSettingsRepository(),
        InMemoryDevicePreferencesRepository(), lockedAccountId,
    )

    private class RecordingRepository(
        private val delegate: InMemoryTransactionRepository = InMemoryTransactionRepository(),
    ) : TransactionRepository by delegate {
        val queries = mutableListOf<HistoryRecordFilters>()
        override suspend fun queryHistoryRecords(filters: HistoryRecordFilters, cursor: HistoryPageCursor?, limit: Int): List<HistoryRecord> {
            queries += filters
            return delegate.queryHistoryRecords(filters, cursor, limit)
        }
    }
}
