package com.shihuaidexianyu.money

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import com.shihuaidexianyu.money.data.migration.StartupMigrationState
import com.shihuaidexianyu.money.ui.launch.AndroidAppLaunchIntentParser
import com.shihuaidexianyu.money.ui.record.LedgerEntryKind
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** End-to-end entry routing against MainActivity, its real DI graph and the Room ledger. */
class LedgerEntryNavigationTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()
    private lateinit var container: MoneyAppContainer
    private var fromId = 0L
    private var toId = 0L
    private lateinit var accountName: String

    @Before
    fun prepareOwnedAccounts() {
        container = (composeRule.activity.application as MoneyApplication).container
        composeRule.waitUntil(15000) { container.startupMigrationCoordinator.state.value == StartupMigrationState.Ready }
        val suffix = UUID.randomUUID().toString().take(6)
        accountName = "流程验证$suffix"
        runBlocking {
            fromId = container.createAccountUseCase(accountName, 10000L)
            toId = container.createAccountUseCase("转入验证$suffix", 10000L)
        }
        awaitHome()
    }

    @Test
    fun rapidEntryTapsOpenOnlyOnePageAndCloseReturnsToAccounts() {
        val openEntry = composeRule.onNodeWithTag("ledger_entry_fab")
            .fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        // Invoke the same visible target twice before Compose can replace its hit targets.
        composeRule.runOnUiThread {
            assertTrue(openEntry())
            assertTrue(openEntry())
        }
        awaitPage("TYPE")
        click(hasText("关闭") and hasClickAction())
        awaitHome()
        runBlocking {
            assertTrue(container.transactionRepository.queryAllCashFlowRecords().none { it.accountId == fromId })
        }
    }

    @Test
    fun minimalAccountCreationRenameCloseAndReopenUseOnlyCoreFields() {
        val createdName = "简洁${UUID.randomUUID().toString().take(6)}"
        val renamedName = "$createdName 改"
        click(hasText("新建"))
        composeRule.onNode(hasSetTextAction()).performTextReplacement(createdName)
        composeRule.onNode(hasText("图标")).assertDoesNotExist()
        composeRule.onNode(hasText("账户用途")).assertDoesNotExist()
        composeRule.onNode(hasText("提醒设置")).assertDoesNotExist()
        click(hasText("保存") and hasClickAction())
        awaitHome()
        val createdId = runBlocking {
            container.accountRepository.queryAllAccounts().single { it.name == createdName }.id
        }
        click(hasText(createdName))
        click(hasText("管理"))
        composeRule.onNode(hasSetTextAction()).performTextReplacement(renamedName)
        click(hasText("保存") and hasClickAction())
        composeRule.waitUntil(15000) { displayed(hasText(renamedName)) }
        click(hasText("管理"))
        click(hasText("关闭账户") and hasClickAction())
        click(hasText("确认关闭"))
        composeRule.waitUntil(15000) { displayed(hasText("重新开启账户")) }
        runBlocking { assertEquals(true, container.accountRepository.getAccountById(createdId)?.isClosed) }
        click(hasText("重新开启账户"))
        composeRule.waitUntil(15000) { displayed(hasText("管理")) }
        runBlocking {
            val reopened = container.accountRepository.getAccountById(createdId)!!
            assertEquals(false, reopened.isClosed)
            assertEquals(renamedName, reopened.name)
            assertEquals(0L, container.calculateCurrentBalanceUseCase(createdId))
        }
    }

    @Test
    fun scopedHistoryCanEditDeleteAndUndoWithoutChangingLedgerSemantics() {
        val cashId = runBlocking {
            container.createCashFlowRecordUseCase(fromId, com.shihuaidexianyu.money.domain.model.CashFlowDirection.OUTFLOW,
                100L, "修改验证", System.currentTimeMillis(), UUID.randomUUID().toString()).recordId
        }
        click(hasText(accountName))
        click(hasText("查看账户流水"))
        click(hasTestTag("history_row_cash_flow_$cashId"))
        click(hasText("修改记录"))
        composeRule.onNode(hasSetTextAction()).performTextReplacement("修改后的备注")
        click(hasText("保存修改"))
        composeRule.waitUntil(15000) { displayed(hasTestTag("history_row_cash_flow_$cashId")) }
        runBlocking {
            assertEquals("修改后的备注", container.transactionRepository.queryAllCashFlowRecords().single { it.id == cashId }.note)
            assertEquals(9900L, container.calculateCurrentBalanceUseCase(fromId))
        }
        click(hasTestTag("history_row_cash_flow_$cashId"))
        click(hasText("修改记录"))
        click(hasText("删除记录") and hasClickAction())
        click(hasText("确认删除"))
        composeRule.waitUntil(15000) { displayed(hasText("撤销") and hasClickAction()) }
        runBlocking { assertEquals(10000L, container.calculateCurrentBalanceUseCase(fromId)) }
        click(hasText("撤销") and hasClickAction())
        composeRule.waitUntil(15000) { displayed(hasTestTag("history_row_cash_flow_$cashId")) }
        runBlocking { assertEquals(9900L, container.calculateCurrentBalanceUseCase(fromId)) }
    }

    @Test
    fun globalEntryRoutesAllFourKindsAndPersistsCorrectRoomRecords() {
        listOf(
            LedgerEntryKind.INCOME to "1", LedgerEntryKind.EXPENSE to "2",
            LedgerEntryKind.TRANSFER to "3", LedgerEntryKind.RECONCILE to "0",
        ).forEach { (kind, amount) ->
            click(hasTestTag("ledger_entry_fab"))
            click(hasTestTag("entry_kind_${kind.name}"))
            click(hasTestTag("entry_account_$fromId"))
            if (kind == LedgerEntryKind.TRANSFER) click(hasTestTag("entry_account_$toId"))
            enterAmount(amount)
            click(hasText("继续"))
            awaitPage("CONFIRM")
            click(hasTestTag("entry_save"))
            awaitHome()
        }
        runBlocking {
            val cash = container.transactionRepository.queryAllCashFlowRecords().filter { it.accountId == fromId }
            assertEquals(2, cash.size)
            assertEquals(100L, cash.single { it.direction == "inflow" }.amount)
            assertEquals(200L, cash.single { it.direction == "outflow" }.amount)
            val transfer = container.transactionRepository.queryAllTransferRecords().single { it.fromAccountId == fromId }
            assertEquals(toId, transfer.toAccountId)
            assertEquals(300L, transfer.amount)
            val reconcile = container.transactionRepository.queryAllBalanceUpdateRecords().single { it.accountId == fromId }
            assertEquals(0L, reconcile.actualBalance)
            assertEquals(-9600L, reconcile.delta)
            assertEquals(0L, container.calculateCurrentBalanceUseCase(fromId))
            assertEquals(10300L, container.calculateCurrentBalanceUseCase(toId))
        }
    }

    @Test
    fun incomeExpenseAndTransferShortcutsSkipTypeButStillRequireAccounts() {
        listOf("record_inflow", "record_outflow", "record_transfer").forEach { shortcut ->
            sendShortcut(shortcut)
            awaitPage("ACCOUNT")
            click(hasTestTag("entry_account_$fromId"))
            if (shortcut == "record_transfer") click(hasTestTag("entry_account_$toId"))
            enterAmount("4")
            click(hasText("继续"))
            awaitPage("CONFIRM")
            click(hasTestTag("entry_save"))
            awaitHome()
        }
        runBlocking {
            assertEquals(setOf("inflow", "outflow"), container.transactionRepository.queryAllCashFlowRecords().filter { it.accountId == fromId }.map { it.direction }.toSet())
            assertEquals(400L, container.transactionRepository.queryAllTransferRecords().single { it.fromAccountId == fromId }.amount)
        }
    }

    @Test
    fun accountDetailExpenseSkipsKnownTypeAndAccountAndReturnsToOrigin() {
        click(hasText("账户") and hasClickAction())
        click(hasText(accountName))
        click(hasText("支出") and hasClickAction())
        awaitPage("AMOUNT")
        enterAmount("5")
        click(hasText("继续"))
        awaitPage("CONFIRM")
        click(hasTestTag("entry_save"))
        composeRule.waitUntil(15000) { displayed(hasText(accountName)) && !displayed(hasTestTag("entry_page_CONFIRM")) }
        runBlocking { assertEquals(500L, container.transactionRepository.queryAllCashFlowRecords().single { it.accountId == fromId }.amount) }
    }

    @Test
    fun activityRecreationRestoresConfirmationAndSavesTheOriginalDraftOnce() {
        click(hasTestTag("ledger_entry_fab"))
        click(hasTestTag("entry_kind_EXPENSE"))
        click(hasTestTag("entry_account_$fromId"))
        enterAmount("47")
        click(hasText("继续"))
        awaitPage("CONFIRM")
        composeRule.activityRule.scenario.recreate()
        awaitPage("CONFIRM")
        click(hasTestTag("entry_save"))
        awaitHome()
        runBlocking {
            val record = container.transactionRepository.queryAllCashFlowRecords().single { it.accountId == fromId }
            assertEquals(4700L, record.amount)
            assertEquals("outflow", record.direction)
        }
    }

    @Test
    fun supplementalCashEntryReturnsToReconciliationAndRefreshesItsEvidence() {
        click(hasTestTag("ledger_entry_fab"))
        click(hasTestTag("entry_kind_RECONCILE"))
        click(hasTestTag("entry_account_$fromId"))
        enterAmount("90")
        click(hasText("继续"))
        awaitPage("CONFIRM")
        click(hasText("补记支出"))
        awaitPage("AMOUNT")
        // The child entry already carries the exact difference and its optional note.
        click(hasText("继续"))
        awaitPage("CONFIRM")
        click(hasTestTag("entry_save"))
        awaitPage("CONFIRM")
        // Wait for the parent preview to finish, rather than clicking through a stale verdict.
        try {
            composeRule.waitUntil(15000) {
                displayed(hasText("对账")) && !displayed(hasText("补记支出")) &&
                    runCatching { composeRule.onNodeWithTag("entry_save").assertIsEnabled() }.isSuccess
            }
        } catch (error: Throwable) {
            throw AssertionError("Supplemental preview did not settle:\n" + composeRule.onRoot().printToString(), error)
        }
        click(hasTestTag("entry_save"))
        awaitHome()
        runBlocking {
            val cash = container.transactionRepository.queryAllCashFlowRecords().single { it.accountId == fromId }
            assertEquals("余额核对补记", cash.note)
            assertEquals(1000L, cash.amount)
            val reconcile = container.transactionRepository.queryAllBalanceUpdateRecords().single { it.accountId == fromId }
            assertEquals(9000L, reconcile.actualBalance)
            assertEquals(0L, reconcile.delta)
        }
    }

    private fun sendShortcut(value: String) {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.startActivity(Intent(activity, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(AndroidAppLaunchIntentParser.SHORTCUT_ACTION_EXTRA, value))
        }
    }

    private fun enterAmount(text: String) {
        awaitPage("AMOUNT")
        text.forEach { digit -> click(hasText(digit.toString())) }
    }

    private fun awaitHome() {
        try {
            composeRule.waitUntil(15000) { displayed(hasTestTag("ledger_entry_fab")) }
        } catch (error: Throwable) {
            throw AssertionError("Home did not return. Current UI:\n" + composeRule.onRoot().printToString(), error)
        }
    }

    private fun awaitPage(step: String) {
        composeRule.waitUntil(15000) { displayed(hasTestTag("entry_page_$step")) }
        composeRule.waitForIdle()
    }

    private fun displayed(matcher: SemanticsMatcher): Boolean =
        runCatching { composeRule.onNode(matcher).assertIsDisplayed() }.isSuccess

    private fun click(matcher: SemanticsMatcher) {
        // Wait for both NavHost and stage animations before injecting a physical tap.
        composeRule.mainClock.advanceTimeBy(300)
        composeRule.waitForIdle()
        if (composeRule.onAllNodes(matcher).fetchSemanticsNodes().isEmpty()) {
            composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex) and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
                .performScrollToNode(matcher)
        }
        val insideScroll = matcher and hasAnyAncestor(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
        if (composeRule.onAllNodes(insideScroll).fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNode(matcher).performScrollTo()
        }
        composeRule.waitUntil(15000) { runCatching { composeRule.onNode(matcher).assertIsEnabled() }.isSuccess }
        composeRule.onNode(matcher).performClick()
        composeRule.waitForIdle()
    }
}
