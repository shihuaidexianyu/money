package com.shihuaidexianyu.money

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.domain.model.PortableSettings
import com.shihuaidexianyu.money.ui.accounts.AccountListItemUiModel
import com.shihuaidexianyu.money.ui.accounts.AccountRow
import com.shihuaidexianyu.money.ui.common.MoneyAmountKeypad
import com.shihuaidexianyu.money.ui.common.MoneyAmountHeroField
import com.shihuaidexianyu.money.ui.common.MoneyAmountText
import com.shihuaidexianyu.money.ui.common.MoneyChoiceDialog
import com.shihuaidexianyu.money.ui.common.signedFormatInAppAmount
import com.shihuaidexianyu.money.ui.theme.MoneyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class UiRefinementRegressionTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun allThirtyOneDaysAreReachableAndSelectableAtLargeText() {
        var selected = 1
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                MoneyTheme {
                    MoneyChoiceDialog("每月几号", (1..31).toList(), selected = 1,
                        label = { "${it}日" }, onSelect = { selected = it }, onDismiss = {})
                }
            }
        }
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("31日"))
        composeRule.onNodeWithText("31日").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(31, selected) }
    }

    @Test
    fun accountAmountIsNotSplitAtTwoHundredPercentFont() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                MoneyTheme {
                    Box(Modifier.size(width = 360.dp, height = 240.dp)) {
                        AccountRow(
                            AccountListItemUiModel(1L, "微信零钱", "green", "wallet", balance = 3_110_300L,
                                isClosed = false, isStale = true, displayOrder = 0),
                            PortableSettings(), totalBalance = 3_110_300L, onClick = {},
                        )
                    }
                }
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText("¥31,103.00", useUnmergedTree = true)
            .assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(1, layouts.single().lineCount)
        val layout = layouts.single()
        assertFalse("Amount overflow: width=${layout.didOverflowWidth}, height=${layout.didOverflowHeight}, size=${layout.size}, lineHeight=${layout.getLineBottom(0)}, text=${layout.layoutInput.text}", layout.hasVisualOverflow)
    }

    @Test
    fun normalAccountRowKeepsBothNameAndBalanceVisible() {
        composeRule.setContent {
            MoneyTheme {
                Box(Modifier.size(width = 360.dp, height = 160.dp)) {
                    AccountRow(
                        AccountListItemUiModel(1L, "微信零钱", "green", "wallet", balance = 3_110_300L,
                            isClosed = false, isStale = true, displayOrder = 0),
                        PortableSettings(), totalBalance = 3_110_300L, onClick = {},
                    )
                }
            }
        }
        composeRule.onNodeWithText("微信零钱", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("¥31,103.00", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun compactKeypadRetainsZeroDecimalAndCommitAction() {
        var saved = false
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                MoneyTheme {
                    Box(Modifier.size(width = 360.dp, height = 260.dp)) {
                        MoneyAmountKeypad("", {}, { saved = true }, compact = true, doneLabel = "保存")
                    }
                }
            }
        }
        composeRule.onNodeWithContentDescription("0").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("小数点").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("保存").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(true, saved) }
    }

    @Test
    fun inlineKeypadShowsCalculatedResultAndCommitsWithoutSheet() {
        var saved = false
        composeRule.setContent {
            var amount by remember { mutableStateOf("") }
            MoneyTheme {
                Column {
                    MoneyAmountHeroField(
                        value = amount, label = "金额", accent = MaterialTheme.colorScheme.primary,
                        onValueChange = { amount = it }, inlineKeypad = true,
                    )
                    MoneyAmountKeypad(
                        amount, { amount = it }, { saved = true }, doneLabel = "保存",
                    )
                }
            }
        }
        listOf("1", "2", "加", "3").forEach {
            composeRule.onNodeWithContentDescription(it).assertIsDisplayed().performClick()
        }
        composeRule.onNodeWithContentDescription("金额")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "12+3，= ¥15.00"))
        composeRule.onNodeWithContentDescription("保存").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(true, saved) }
    }

    @Test
    fun maskedReadOnlyMoneyDoesNotExposeAmountInTextOrSemantics() {
        composeRule.setContent {
            MoneyTheme(hideInAppAmounts = true) {
                MoneyAmountText(signedFormatInAppAmount(1_234_567L, PortableSettings()))
            }
        }
        composeRule.onNodeWithText("¥••••").assertIsDisplayed()
        composeRule.onNodeWithText("12,345.67", substring = true).assertDoesNotExist()
    }
}
