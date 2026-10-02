package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryDevicePreferencesRepository
import com.shihuaidexianyu.money.data.repository.InMemoryPortableSettingsRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.DevicePreferences
import com.shihuaidexianyu.money.domain.model.HistoryBusinessSemantic
import com.shihuaidexianyu.money.domain.model.HistoryFilters
import com.shihuaidexianyu.money.domain.model.HistoryRecordType
import com.shihuaidexianyu.money.domain.model.MinimalProductPolicy
import com.shihuaidexianyu.money.ui.common.AccountOptionUiModel
import com.shihuaidexianyu.money.ui.common.accountPickerSections
import com.shihuaidexianyu.money.ui.history.AmountDirectionFilter
import com.shihuaidexianyu.money.ui.history.HistoryFilterState
import com.shihuaidexianyu.money.ui.history.HistoryViewModel
import com.shihuaidexianyu.money.ui.history.forMinimalLedger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MinimalLedgerPolicyTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `minimal filters keep search account and dates but remove invisible legacy conditions`() {
        val old = HistoryFilterState(
            keyword = "午餐", excludeKeyword = "退款", selectedAccountId = 7L,
            dateStartAt = 100L, dateEndAt = 200L,
            selectedRecordTypes = setOf(HistoryRecordType.TRANSFER),
            minAmountText = "100", maxAmountText = "500",
            amountDirectionFilter = AmountDirectionFilter.INCREASE,
        )
        assertEquals(
            HistoryFilterState(keyword = "午餐", selectedAccountId = 7L, dateStartAt = 100L, dateEndAt = 200L),
            old.forMinimalLedger(),
        )
        assertEquals(old.forMinimalLedger(), old.forMinimalLedger().forMinimalLedger())
    }

    @Test
    fun `minimal history loads legacy preferences without hidden restrictions`() = runTest(dispatcher) {
        val preferences = InMemoryDevicePreferencesRepository(DevicePreferences(historyFilters = HistoryFilters(
            keyword = "午餐", excludeKeyword = "退款", recordTypes = setOf(HistoryRecordType.TRANSFER.name),
            accountId = 7L, dateStartAt = 100L, dateEndAt = 200L, minAmountText = "100",
        )))
        val vm = HistoryViewModel(
            InMemoryAccountRepository(), InMemoryTransactionRepository(), InMemoryPortableSettingsRepository(),
            preferences, simplified = true,
        )
        advanceUntilIdle()
        with(vm.uiState.value) {
            assertEquals("午餐", keyword)
            assertEquals(7L, selectedAccountId)
            assertEquals(100L, dateStartAt)
            assertEquals(200L, dateEndAt)
            assertEquals("", excludeKeyword)
            assertEquals(emptySet(), selectedRecordTypes)
            assertEquals("", minAmountText)
            assertEquals(HistoryBusinessSemantic.ALL, businessSemantic)
        }
        vm.applyFilterDraft(HistoryFilterState(selectedAccountId = 9L))
        advanceUntilIdle()
        assertEquals("午餐", vm.uiState.value.keyword)
        assertEquals(9L, preferences.query().historyFilters.accountId)
        assertEquals(emptySet(), preferences.query().historyFilters.recordTypes)
    }

    @Test
    fun `all pickers use stable creation order rather than provided or recent order`() {
        val options = listOf(
            AccountOptionUiModel(3L, "新账户", lastUsedAt = 999L, createdAt = 20L),
            AccountOptionUiModel(2L, "旧账户二", createdAt = 10L),
            AccountOptionUiModel(5L, "隐藏新", isHidden = true, createdAt = 20L),
            AccountOptionUiModel(1L, "旧账户一", createdAt = 10L),
            AccountOptionUiModel(4L, "隐藏旧", isHidden = true, createdAt = 10L),
        )
        val sections = accountPickerSections(options, hiddenExpanded = true)
        assertEquals(listOf(1L, 2L, 3L), sections.visibleAccounts.map { it.id })
        assertEquals(listOf(4L, 5L), sections.hiddenAccounts.map { it.id })
    }

    @Test
    fun `retired features cannot run background reminders or computer connections`() {
        assertFalse(MinimalProductPolicy.remindersEnabled)
        assertFalse(MinimalProductPolicy.computerConnectionEnabled)
    }
}
