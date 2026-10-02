package com.shihuaidexianyu.money

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.domain.model.ThemeMode
import com.shihuaidexianyu.money.ui.accounts.AccountsScreen
import com.shihuaidexianyu.money.ui.accounts.AccountsUiState
import com.shihuaidexianyu.money.ui.common.MoneyFormPage
import com.shihuaidexianyu.money.ui.common.MoneySkeleton
import com.shihuaidexianyu.money.ui.history.HistoryRecordKind
import com.shihuaidexianyu.money.ui.history.HistoryRecordUiModel
import com.shihuaidexianyu.money.ui.history.HistoryScreen
import com.shihuaidexianyu.money.ui.history.HistoryUiState
import com.shihuaidexianyu.money.ui.theme.MoneyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MotionStabilityTest {
    @get:Rule val composeRule = createComposeRule()

    private fun assertOpaqueBackdrop(tag: String, expected: () -> Color) {
        val image = composeRule.onNodeWithTag(tag).captureToImage()
        val pixel = image.toPixelMap()[2, image.height / 2]
        assertEquals("The outgoing magenta page must not bleed through", expected().toArgb(), pixel.toArgb())
    }

    @Test
    fun formPaintsItsOwnBackdropInBothThemes() {
        var mode by mutableStateOf(ThemeMode.LIGHT)
        var background = Color.Unspecified
        composeRule.setContent {
            MoneyTheme(themeMode = mode) {
                val color = MaterialTheme.colorScheme.background
                SideEffect { background = color }
                Box(Modifier.requiredSize(360.dp, 600.dp).background(Color.Magenta)) {
                    MoneyFormPage("表单", modifier = Modifier.testTag("form")) {}
                }
            }
        }
        assertOpaqueBackdrop("form") { background }
        composeRule.runOnIdle { mode = ThemeMode.DARK }
        assertOpaqueBackdrop("form") { background }
    }

    @Test
    fun accountsPaintTheirOwnBackdropInBothThemes() {
        var mode by mutableStateOf(ThemeMode.LIGHT)
        var background = Color.Unspecified
        composeRule.setContent {
            MoneyTheme(themeMode = mode) {
                val color = MaterialTheme.colorScheme.background
                SideEffect { background = color }
                Box(Modifier.requiredSize(360.dp, 600.dp).background(Color.Magenta)) {
                    AccountsScreen(
                        state = AccountsUiState(isLoading = false, hasCommittedContent = true),
                        onCreateAccount = {}, onAccountClick = {}, onToggleClosedVisibility = {},
                        modifier = Modifier.testTag("accounts"),
                    )
                }
            }
        }
        assertOpaqueBackdrop("accounts") { background }
        composeRule.runOnIdle { mode = ThemeMode.DARK }
        assertOpaqueBackdrop("accounts") { background }
    }

    private fun record(id: Int, amount: Long = -100L) = HistoryRecordUiModel(
        id = "motion_$id", recordId = id.toLong(), kind = HistoryRecordKind.CASH_FLOW,
        title = "流水 $id", subtitle = "现金", amount = amount,
        occurredAt = System.currentTimeMillis(), accountIds = setOf(1L), keywordSource = "",
    )

    @androidx.compose.runtime.Composable
    private fun History(state: HistoryUiState) {
        HistoryScreen(
            state = state, onKeywordChange = {}, onExcludeKeywordChange = {}, onRecordTypesChange = {},
            onAccountChange = {}, onDateRangeChange = { _, _ -> }, onMinAmountChange = {},
            onMaxAmountChange = {}, onAmountDirectionChange = {}, onClearAllFilters = {},
            onLoadMore = {}, onRecordClick = {},
        )
    }

    @Test
    fun refreshKeepsTheVisibleRecordAndItsPixelOffset() {
        var state by mutableStateOf(HistoryUiState(
            records = (1..30).map { record(it) }, isLoading = false, hasCommittedContent = true,
        ))
        composeRule.setContent {
            MoneyTheme { Box(Modifier.requiredSize(360.dp, 600.dp)) { History(state) } }
        }
        composeRule.onNode(hasScrollAction()).performScrollToIndex(16)
        val row = composeRule.onNodeWithTag("history_row_motion_15")
        val original = row.fetchSemanticsNode().boundsInRoot.top
        val slot = composeRule.onNodeWithTag("history_refresh_slot").fetchSemanticsNode().boundsInRoot
        composeRule.runOnIdle { state = state.copy(isRefreshing = true) }
        assertEquals(original, row.fetchSemanticsNode().boundsInRoot.top, 1.5f)
        assertEquals(slot, composeRule.onNodeWithTag("history_refresh_slot").fetchSemanticsNode().boundsInRoot)
        composeRule.runOnIdle {
            state = state.copy(isRefreshing = false, records = state.records.map { it.copy(title = it.title + "改") })
        }
        row.assertIsDisplayed()
        assertEquals(original, row.fetchSemanticsNode().boundsInRoot.top, 1.5f)
    }

    @Test
    fun dailyTotalOverflowDoesNotCrashOrLeakMaskedAmounts() {
        var masked by mutableStateOf(false)
        val state = HistoryUiState(
            records = listOf(record(1, Long.MAX_VALUE), record(2, 1L), record(3, -200L)),
            isLoading = false, hasCommittedContent = true,
        )
        composeRule.setContent {
            MoneyTheme(hideInAppAmounts = masked) {
                Box(Modifier.requiredSize(360.dp, 600.dp)) { History(state) }
            }
        }
        composeRule.onNodeWithText("汇总超出显示范围").assertIsDisplayed()
        composeRule.onNodeWithTag("history_row_motion_1").assertIsDisplayed()
        composeRule.runOnIdle { masked = true }
        composeRule.onNodeWithText("汇总超出显示范围").assertDoesNotExist()
        assertTrue(composeRule.onAllNodesWithText("¥••••", substring = true).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun loadingAnimationDoesNotChangeItsViewport() {
        composeRule.setContent {
            MoneyTheme { Box(Modifier.requiredSize(360.dp, 600.dp)) { MoneySkeleton(Modifier.testTag("loading")) } }
        }
        val original = composeRule.onNodeWithTag("loading").fetchSemanticsNode().boundsInRoot
        composeRule.mainClock.autoAdvance = false
        try {
            repeat(8) {
                composeRule.mainClock.advanceTimeBy(160)
                assertEquals(original, composeRule.onNodeWithTag("loading").fetchSemanticsNode().boundsInRoot)
            }
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
    }
}
