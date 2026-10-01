package com.shihuaidexianyu.money

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.lifecycle.SavedStateHandle
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryDevicePreferencesRepository
import com.shihuaidexianyu.money.data.repository.InMemoryRecurringReminderRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.AccountKind
import com.shihuaidexianyu.money.domain.time.ClockProvider
import com.shihuaidexianyu.money.domain.time.ZoneIdProvider
import com.shihuaidexianyu.money.domain.usecase.CalculateAccountBalancesUseCase
import com.shihuaidexianyu.money.domain.usecase.CreateCashFlowRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.CreateTransferRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.LedgerOperationIdFactory
import com.shihuaidexianyu.money.domain.usecase.ProcessDueReminderUseCase
import com.shihuaidexianyu.money.domain.usecase.RefreshAccountActivityStateUseCase
import com.shihuaidexianyu.money.domain.usecase.ResolveBalanceUpdateContextUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateBalanceUseCase
import com.shihuaidexianyu.money.ui.record.LedgerEntryKind
import com.shihuaidexianyu.money.ui.record.LedgerEntryScreen
import com.shihuaidexianyu.money.ui.record.LedgerEntryStep
import com.shihuaidexianyu.money.ui.record.LedgerEntryViewModel
import com.shihuaidexianyu.money.ui.theme.MoneyTheme
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LedgerEntryWorkflowTest {
    @get:Rule val composeRule = createComposeRule()
    private val accounts = InMemoryAccountRepository()
    private val ledger = InMemoryTransactionRepository()
    private val preferences = InMemoryDevicePreferencesRepository()
    private lateinit var vm: LedgerEntryViewModel
    private var exits = 0
    private var saves = 0
    private var createAccounts = 0

    @Test
    fun globalIncomeRequiresEachDecisionAndOnlyFinalSaveWrites() {
        show()
        composeRule.onNodeWithText("选择记账方式").assertIsDisplayed()
        tap("entry_kind_INCOME")
        composeRule.onNodeWithText("选择账户").assertIsDisplayed()
        composeRule.onNodeWithText("确认保存").assertDoesNotExist()
        tap("entry_account_1")
        typeAmount("12")
        runBlocking { assertTrue(ledger.queryAllCashFlowRecords().isEmpty()) }
        continueEntry()
        composeRule.onNodeWithText("确认这笔记录").assertIsDisplayed()
        runBlocking { assertTrue(ledger.queryAllCashFlowRecords().isEmpty()) }
        tap("entry_save")
        composeRule.waitUntil(5000) { saves == 1 }
        runBlocking {
            val record = ledger.queryAllCashFlowRecords().single()
            assertEquals(1200L, record.amount)
            assertEquals("inflow", record.direction)
        }
    }

    @Test
    fun expenseAccountShortcutStartsAtAmountWithoutDefaultAccountSubstitution() {
        show(LedgerEntryKind.EXPENSE, 2L)
        composeRule.onNodeWithText("输入金额").assertIsDisplayed()
        typeAmount("38")
        continueEntry()
        tap("entry_save")
        composeRule.waitUntil(5000) { saves == 1 }
        runBlocking {
            val record = ledger.queryAllCashFlowRecords().single()
            assertEquals(2L, record.accountId)
            assertEquals("outflow", record.direction)
        }
    }

    @Test
    fun transferSeparatesSourceAndDestinationAndDisablesSameAccount() {
        show()
        tap("entry_kind_TRANSFER")
        tap("entry_account_1")
        awaitEnabled("entry_account_2")
        composeRule.onNodeWithText("选择转入账户").assertIsDisplayed()
        composeRule.onNodeWithTag("entry_account_1").assertIsNotEnabled()
        tap("entry_account_2")
        typeAmount("15")
        continueEntry()
        tap("entry_save")
        composeRule.waitUntil(5000) { saves == 1 }
        runBlocking {
            val record = ledger.queryAllTransferRecords().single()
            assertEquals(1L, record.fromAccountId)
            assertEquals(2L, record.toAccountId)
            assertEquals(1500L, record.amount)
            assertTrue(ledger.queryAllCashFlowRecords().isEmpty())
        }
    }

    @Test
    fun zeroBalanceReconciliationRequiresExplicitZeroAndShowsEvidence() {
        show(LedgerEntryKind.RECONCILE, 1L)
        typeAmount("0")
        continueEntry()
        composeRule.onNodeWithText("系统余额").performScrollTo().assertIsDisplayed()
        tap("entry_save")
        composeRule.waitUntil(5000) { saves == 1 }
        runBlocking {
            val record = ledger.queryAllBalanceUpdateRecords().single()
            assertEquals(0L, record.actualBalance)
            assertEquals(-10000L, record.delta)
        }
    }

    @Test
    fun investmentReconciliationDoesNotOfferMissingCashFlowActions() {
        show(LedgerEntryKind.RECONCILE, 1L, investment = true)
        typeAmount("125")
        continueEntry()
        composeRule.onNodeWithText("账面市值").assertIsDisplayed()
        composeRule.onNodeWithText("补记收入").assertDoesNotExist()
        composeRule.onNodeWithText("补记支出").assertDoesNotExist()
        tap("entry_save")
        composeRule.waitUntil(5000) { saves == 1 }
        runBlocking { assertEquals(2500L, ledger.queryAllBalanceUpdateRecords().single().delta) }
    }

    @Test
    fun invalidAmountCannotAdvanceOrWrite() {
        show(LedgerEntryKind.EXPENSE, 1L)
        typeAmount("0")
        continueEntry(expectConfirmation = false)
        composeRule.onNodeWithText("输入金额").assertIsDisplayed()
        composeRule.onNodeWithText("确认保存").assertDoesNotExist()
        composeRule.runOnIdle { assertTrue(vm.uiState.value.amountError != null) }
        runBlocking { assertTrue(ledger.queryAllCashFlowRecords().isEmpty()) }
    }

    @Test
    fun backToEarlierPagesKeepsAmountAndNoteWithoutWriting() {
        show(LedgerEntryKind.EXPENSE, 1L, prefillNote = "午餐")
        typeAmount("38")
        continueEntry()
        awaitEnabled("entry_note")
        composeRule.onNodeWithTag("entry_note").performScrollTo().performTextInput("补充")
        pressBack()
        composeRule.runOnIdle {
            assertEquals(LedgerEntryStep.AMOUNT, vm.uiState.value.step)
            assertEquals("38", vm.uiState.value.amountText)
            assertTrue(vm.uiState.value.note.contains("补充"))
        }
        pressBack()
        tap("entry_account_2")
        continueEntry()
        composeRule.runOnIdle {
            assertEquals(2L, vm.uiState.value.accountId)
            assertEquals("38", vm.uiState.value.amountText)
            assertTrue(vm.uiState.value.note.contains("补充"))
        }
        runBlocking { assertTrue(ledger.queryAllCashFlowRecords().isEmpty()) }
    }

    @Test
    fun closingDirtyFlowAsksToDiscardAndCanContinueEditing() {
        show()
        tap("entry_kind_EXPENSE")
        composeRule.onNodeWithText("关闭").performClick()
        composeRule.onNodeWithText("继续编辑").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(0, exits) }
        composeRule.onNodeWithText("关闭").performClick()
        composeRule.onNodeWithText("放弃").performClick()
        composeRule.runOnIdle { assertEquals(1, exits) }
        runBlocking { assertTrue(ledger.queryAllCashFlowRecords().isEmpty()) }
    }

    @Test
    fun hiddenAccountNeedsExplicitExpansionButCanBeSelected() {
        show(LedgerEntryKind.INCOME, hidden = true)
        composeRule.onNodeWithTag("entry_account_2").assertDoesNotExist()
        composeRule.waitUntil(5000) { runCatching { composeRule.onNodeWithText("显示隐藏账户", substring = true).assertIsEnabled() }.isSuccess }
        composeRule.onNodeWithText("显示隐藏账户", substring = true).performScrollTo().performClick()
        tap("entry_account_2")
        composeRule.runOnIdle { assertEquals(2L, vm.uiState.value.accountId) }
    }

    @Test
    fun tooFewAccountsExplainsTransferAndOffersAccountCreation() {
        show(accountCount = 1)
        tap("entry_kind_TRANSFER")
        composeRule.onNodeWithText("转账至少需要两个可用账户").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("新建账户").performClick()
        composeRule.runOnIdle { assertEquals(1, createAccounts) }
    }

    @Test
    fun largeTextShortWindowStillAllowsKeypadContinueAndFinalSave() {
        show(LedgerEntryKind.EXPENSE, 1L, fontScale = 2f, heightDp = 480)
        typeAmount("8")
        continueEntry()
        composeRule.onNodeWithTag("entry_save").assertIsDisplayed()
        tap("entry_save")
        composeRule.waitUntil(5000) { saves == 1 }
    }

    @Test
    fun shortWindowKeepsSaveReachableWithNoteKeyboardOpen() {
        show(LedgerEntryKind.EXPENSE, 1L, prefillNote = "午餐", heightDp = 480)
        typeAmount("8")
        continueEntry()
        awaitEnabled("entry_note")
        composeRule.onNodeWithTag("entry_note").performScrollTo().performClick().performTextInput("补充")
        composeRule.onNodeWithTag("entry_save").assertIsDisplayed()
        tap("entry_save")
        composeRule.waitUntil(5000) { saves == 1 }
    }

    private fun show(
        kind: LedgerEntryKind? = null, accountId: Long? = null, prefillNote: String? = null,
        investment: Boolean = false, hidden: Boolean = false, accountCount: Int = 2,
        fontScale: Float = 1f, heightDp: Int = 760,
    ) {
        runBlocking {
            accounts.createAccount(Account(name = "现金", initialBalance = 10000L, createdAt = 0L, kind = if (investment) AccountKind.INVESTMENT else AccountKind.FUNDING))
            if (accountCount > 1) accounts.createAccount(Account(name = "银行卡", initialBalance = 20000L, createdAt = 0L, isHidden = hidden))
        }
        composeRule.runOnIdle {
            val clock = ClockProvider { System.currentTimeMillis() }
            val refresh = RefreshAccountActivityStateUseCase(accounts, ledger)
            val context = ResolveBalanceUpdateContextUseCase(accounts, ledger)
            vm = LedgerEntryViewModel(
                initialKind = kind, initialAccountId = accountId, prefillNote = prefillNote,
                accountRepository = accounts, transactionRepository = ledger, devicePreferencesRepository = preferences,
                calculateAccountBalancesUseCase = CalculateAccountBalancesUseCase(ledger, clock),
                resolveBalanceUpdateContextUseCase = context,
                createCashFlowRecordUseCase = CreateCashFlowRecordUseCase(accounts, ledger, refresh, clock),
                createTransferRecordUseCase = CreateTransferRecordUseCase(accounts, ledger, refresh, clock),
                updateBalanceUseCase = UpdateBalanceUseCase(accounts, ledger, context, refresh, clock),
                processDueReminderUseCase = ProcessDueReminderUseCase(accounts, ledger, InMemoryRecurringReminderRepository(), refresh, clock, ZoneIdProvider { ZoneId.systemDefault() }),
                savedStateHandle = SavedStateHandle(), operationIdFactory = LedgerOperationIdFactory { UUID.randomUUID().toString() },
            )
        }
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MoneyTheme {
                    Box(Modifier.size(360.dp, heightDp.dp)) {
                        LedgerEntryScreen(vm, onBack = { exits++ }, onSaved = { saves++ }, onManageAccounts = { createAccounts++ })
                    }
                }
            }
        }
        composeRule.waitUntil(5000) { !vm.uiState.value.isLoading }
        composeRule.waitForIdle()
    }

    private fun tap(tag: String) {
        awaitEnabled(tag)
        composeRule.onNodeWithTag(tag).performClick()
        composeRule.waitForIdle()
    }

    private fun awaitEnabled(tag: String) {
        if (composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()) {
            composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex) and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
                .performScrollToNode(hasTestTag(tag))
        }
        if (runCatching { composeRule.onNodeWithTag(tag).assertIsDisplayed() }.isFailure) {
            composeRule.onNodeWithTag(tag).performScrollTo()
        }
        composeRule.waitUntil(5000) { runCatching { composeRule.onNodeWithTag(tag).assertIsEnabled() }.isSuccess }
    }

    private fun typeAmount(text: String) {
        composeRule.waitUntil(5000) { runCatching { composeRule.onNodeWithText("继续").assertIsEnabled() }.isSuccess }
        text.forEach { digit -> composeRule.onNodeWithText(digit.toString()).performScrollTo().performClick() }
    }

    private fun continueEntry(expectConfirmation: Boolean = true) {
        composeRule.waitUntil(5000) { runCatching { composeRule.onNodeWithText("继续").assertIsEnabled() }.isSuccess }
        composeRule.onNodeWithText("继续").performScrollTo().performClick()
        composeRule.waitForIdle()
        if (expectConfirmation) composeRule.waitUntil(5000) { vm.uiState.value.step == LedgerEntryStep.CONFIRM }
    }

    private fun pressBack() {
        composeRule.onNodeWithContentDescription("返回").performClick()
        composeRule.waitForIdle()
    }
}
