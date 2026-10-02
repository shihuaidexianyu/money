package com.shihuaidexianyu.money

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.data.backup.ImportReceipt
import com.shihuaidexianyu.money.data.backup.ImportReceiptCounts
import com.shihuaidexianyu.money.data.backup.ImportReceiptKind
import com.shihuaidexianyu.money.data.backup.ImportReceiptStatus
import com.shihuaidexianyu.money.data.backup.StagedImportPreview
import com.shihuaidexianyu.money.domain.usecase.BackupValidationResult
import com.shihuaidexianyu.money.ui.balance.BatchReconcileAccountRow
import com.shihuaidexianyu.money.ui.balance.BatchReconcileAccountUiModel
import com.shihuaidexianyu.money.ui.balance.BatchReconcileUiState
import com.shihuaidexianyu.money.ui.history.HistoryFilterState
import com.shihuaidexianyu.money.ui.history.HistoryFiltersSheet
import com.shihuaidexianyu.money.ui.reminder.NotificationPermissionUiState
import com.shihuaidexianyu.money.ui.settings.SettingsEffect
import com.shihuaidexianyu.money.ui.settings.SettingsScreen
import com.shihuaidexianyu.money.ui.settings.SettingsUiState
import com.shihuaidexianyu.money.ui.theme.MoneyTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

class WorkflowRefinementTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun filterDraftWaitsForApplyAndSurvivesRecreation() {
        var applied: HistoryFilterState? = null
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            MoneyTheme {
                HistoryFiltersSheet(HistoryFilterState(), emptyList(), null, { applied = it }, {})
            }
        }
        composeRule.onNodeWithText("今天").performClick()
        composeRule.runOnIdle { assertNull(applied) }
        restoration.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithText("今天").assertIsSelected()
        composeRule.onNodeWithText("应用筛选").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertNotNull(applied?.dateStartAt) }
    }

    @Test
    fun retiredAmountBoundsAreDiscardedAndLockedScopeCannotBeEdited() {
        var applied: HistoryFilterState? = null
        composeRule.setContent {
            MoneyTheme {
                HistoryFiltersSheet(HistoryFilterState(minAmountText = "20", maxAmountText = "10"),
                    emptyList(), 7L, { applied = it }, {})
            }
        }
        composeRule.onNodeWithText("应用筛选").assertIsDisplayed().assertIsEnabled().performClick()
        composeRule.runOnIdle { assertEquals(HistoryFilterState(selectedAccountId = 7L), applied) }
        composeRule.onNodeWithText("全部账户").assertDoesNotExist()
    }

    @Test
    fun batchBalanceAndDeltaRemainReadableAtTwoHundredPercentFont() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                MoneyTheme {
                    Box(Modifier.size(360.dp, 700.dp)) {
                        BatchReconcileAccountRow(
                            BatchReconcileAccountUiModel(1L, "微信零钱", 3_110_300L, null,
                                actualBalanceText = "31000.00"), BatchReconcileUiState(), {}, {},
                        )
                    }
                }
            }
        }
        val results = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText("¥31,103.00", useUnmergedTree = true).assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        assertEquals(1, results.single().lineCount)
        assertFalse(results.single().hasVisualOverflow)
        composeRule.onNodeWithText("-¥103.00", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun importPreviewIsRenderedFromRetainedStateAfterUiRecreation() {
        val preview = StagedImportPreview("stage-1", "a".repeat(64), 1_000L,
            BackupValidationResult(1, 2, 3, 4, 5, 6, 1_700_000_000_000L))
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent { TestSettings(SettingsUiState(pendingImportPreview = preview)) }
        composeRule.onNodeWithText("覆盖导入", substring = true).assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithText("备份生成时间：", substring = true).assertIsDisplayed()
    }

    @Test
    fun ineligibleImportStillOffersReadOnlyRecoveryExportWithWarning() {
        var exports = 0
        val receipt = ImportReceipt("receipt-1", ImportReceiptKind.IMPORT, ImportReceiptStatus.COMMITTED,
            1_700_000_000_000L, "a".repeat(64), "b".repeat(64), "safety.json", "c".repeat(64), 5,
            ImportReceiptCounts(1, 1, 0, 0, 0, 0))
        composeRule.setContent {
            TestSettings(SettingsUiState(importHistory = listOf(receipt), availableSafetyReceiptIds = setOf(receipt.id)),
                onRecoveryExport = { exports++ })
        }
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("导入记录"))
        composeRule.onNodeWithText("恢复选项").assertIsDisplayed()
        composeRule.onNodeWithText("导入记录").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("另存操作前备份").performClick()
        composeRule.runOnIdle { assertEquals(0, exports) }
        composeRule.onNodeWithText("确定").performClick()
        composeRule.runOnIdle { assertEquals(1, exports) }
    }

    @Composable
    private fun TestSettings(state: SettingsUiState, onRecoveryExport: (String) -> Unit = {}) {
        MoneyTheme {
            SettingsScreen(state, MutableSharedFlow<SettingsEffect>(), {}, {}, {}, {}, {}, {}, {}, {}, {},
                NotificationPermissionUiState.NotRequested, {}, {}, {}, {}, {}, {}, {}, {}, {},
                onExportSafetySnapshot = onRecoveryExport)
        }
    }
}
