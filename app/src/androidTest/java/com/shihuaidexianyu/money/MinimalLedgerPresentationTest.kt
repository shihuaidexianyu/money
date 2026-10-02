package com.shihuaidexianyu.money

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.ui.accounts.AccountListItemUiModel
import com.shihuaidexianyu.money.ui.accounts.AccountsScreen
import com.shihuaidexianyu.money.ui.accounts.AccountsUiState
import com.shihuaidexianyu.money.ui.history.HistoryFilterState
import com.shihuaidexianyu.money.ui.history.HistoryFiltersSheet
import com.shihuaidexianyu.money.ui.reminder.NotificationPermissionUiState
import com.shihuaidexianyu.money.ui.settings.SettingsScreen
import com.shihuaidexianyu.money.ui.settings.SettingsUiState
import com.shihuaidexianyu.money.ui.theme.MoneyTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MinimalLedgerPresentationTest {
    @get:Rule val composeRule = createComposeRule()

    private fun account(id: Long, name: String, balance: Long, hidden: Boolean = false, closed: Boolean = false) =
        AccountListItemUiModel(
            id = id, name = name, colorName = "blue", iconName = "wallet", balance = balance,
            isHidden = hidden, isClosed = closed, isStale = false, displayOrder = (10 - id).toInt(),
            createdAt = id, requiresReopenAndSettle = closed && balance != 0L,
        )

    private fun renderAccounts(state: AccountsUiState, fontScale: Float = 1f, masked: Boolean = false) {
        composeRule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, fontScale),
            ) {
                MoneyTheme(hideInAppAmounts = masked) {
                    Box(Modifier.requiredSize(360.dp, 640.dp)) {
                        AccountsScreen(
                            state = state, onCreateAccount = {}, onAccountClick = {},
                            onToggleClosedVisibility = {}, onToggleAmountVisibility = {},
                        )
                    }
                }
            }
        }
    }

    @Test
    fun accountsShowTotalIncludingHiddenAndUseCreationOrderWithoutExtraControls() {
        renderAccounts(AccountsUiState(isLoading = false, hasCommittedContent = true, openAccounts = listOf(
            account(2L, "银行卡", 200L), account(1L, "现金", 100L), account(3L, "备用金", 300L, hidden = true),
        )))
        composeRule.onNodeWithText("总余额").assertIsDisplayed()
        composeRule.onNodeWithText("¥6.00").assertIsDisplayed()
        composeRule.onNodeWithText("包含隐藏账户的余额").assertIsDisplayed()
        composeRule.onNodeWithText("备用金").assertDoesNotExist()
        val cash = composeRule.onNodeWithTag("account_row_1").fetchSemanticsNode().boundsInRoot
        val bank = composeRule.onNodeWithTag("account_row_2").fetchSemanticsNode().boundsInRoot
        assertTrue(cash.top < bank.top)
        composeRule.onNodeWithText("隐藏账户").performClick()
        composeRule.onNodeWithText("备用金").assertIsDisplayed()
        composeRule.onNodeWithText("排序").assertDoesNotExist()
        composeRule.onNodeWithText("批量核对").assertDoesNotExist()
        composeRule.onNodeWithText("设置").assertHasClickAction()
        composeRule.onNodeWithText("新建").assertHasClickAction()
    }

    @Test
    fun totalOverflowDoesNotCrashOrInventAnAmount() {
        renderAccounts(AccountsUiState(isLoading = false, hasCommittedContent = true,
            openAccounts = listOf(account(1L, "账户一", Long.MAX_VALUE), account(2L, "账户二", 1L))))
        composeRule.onNodeWithText("合计超出支持范围，请查看各账户余额").assertIsDisplayed()
        composeRule.onNodeWithTag("account_row_1").assertHasClickAction()
    }

    @Test
    fun invalidLegacyClosedBalanceIsVisibleWithoutExpandingClosedAccounts() {
        renderAccounts(AccountsUiState(isLoading = false, hasCommittedContent = true,
            closedAccounts = listOf(account(1L, "需要恢复", 100L, closed = true), account(2L, "已结清", 0L, closed = true))))
        composeRule.onNodeWithTag("account_row_1").assertIsDisplayed()
        composeRule.onNodeWithTag("account_row_2").assertDoesNotExist()
    }

    @Test
    fun largeTextKeepsLongBalancesAndPrimaryActionsReachable() {
        renderAccounts(AccountsUiState(isLoading = false, hasCommittedContent = true,
            openAccounts = listOf(account(1L, "长名称账户", Long.MAX_VALUE))), fontScale = 2f)
        composeRule.onNodeWithText("设置").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithText("新建").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("account_row_1").assertIsDisplayed().assertHeightIsAtLeast(64.dp)
        composeRule.onNodeWithText("隐藏金额").assertIsDisplayed().assertHasClickAction()
    }

    @Test
    fun maskedAccountsDoNotExposeBalancesInVisibleTextOrSpokenDescriptions() {
        renderAccounts(AccountsUiState(isLoading = false, hasCommittedContent = true,
            openAccounts = listOf(account(1L, "现金", 12345L))), masked = true)
        composeRule.onNodeWithText("¥123.45").assertDoesNotExist()
        val row = composeRule.onNodeWithTag("account_row_1").fetchSemanticsNode()
        assertTrue(!row.config.toString().contains("123.45"))
        composeRule.onNodeWithText("显示金额").assertIsDisplayed()
    }

    @Test
    fun filterResetAndApplyKeepOnlyAccountAndDates() {
        var applied: HistoryFilterState? = null
        composeRule.setContent {
            MoneyTheme {
                HistoryFiltersSheet(
                    initialFilters = HistoryFilterState(excludeKeyword = "退款", minAmountText = "100"),
                    accounts = emptyList(), lockedAccountId = null, onApply = { applied = it }, onDismiss = {},
                )
            }
        }
        composeRule.onNodeWithContentDescription("账户，全部账户").assertIsDisplayed()
        composeRule.onNodeWithText("高级筛选").assertDoesNotExist()
        composeRule.onNodeWithText("重置条件").performClick()
        composeRule.onNodeWithText("应用筛选").performClick()
        composeRule.runOnIdle { assertEquals(HistoryFilterState(), applied) }
    }

    @Test
    fun exportDestinationStillRequiresPlaintextWarningBeforeWriting() {
        var saved = 0
        var shared = 0
        composeRule.setContent {
            MoneyTheme {
                SettingsScreen(
                    state = SettingsUiState(), effectFlow = MutableSharedFlow(), onBack = {},
                    onThemeModeChange = {}, onAmountColorModeChange = {}, onCurrencySymbolChange = {},
                    onBiometricLockChange = {}, onRelockDelayChange = {}, onHideNotificationAmountsChange = {},
                    onHideRecentTasksChange = {}, onHideInAppAmountsChange = {},
                    notificationPermissionState = NotificationPermissionUiState.NotRequested,
                    onRequestNotificationPermission = {}, onOpenNotificationSettings = {},
                    onManageReminders = {}, onManageAccountReminderConfigs = {}, onOpenLanAi = {},
                    onExportData = { saved++ }, onShareBackup = { shared++ },
                    onImportData = {}, onConfirmImport = {}, onRollbackImport = {},
                )
            }
        }
        fun openExport() {
            composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("导出备份"))
            composeRule.onNodeWithText("导出备份").performClick()
        }
        openExport()
        composeRule.onNodeWithText("保存到文件").performClick()
        composeRule.runOnIdle { assertEquals(0, saved) }
        composeRule.onNodeWithText("继续导出").performClick()
        composeRule.runOnIdle { assertEquals(1, saved) }
        openExport()
        composeRule.onNodeWithText("分享备份").performClick()
        composeRule.runOnIdle { assertEquals(0, shared) }
        composeRule.onNodeWithText("取消").performClick()
        composeRule.runOnIdle { assertEquals(0, shared) }
        openExport()
        composeRule.onNodeWithText("分享备份").performClick()
        composeRule.onNodeWithText("继续导出").performClick()
        composeRule.runOnIdle { assertEquals(1, shared) }
    }
}
