package com.shihuaidexianyu.money.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.util.AmountFormatter
import com.shihuaidexianyu.money.util.parseAmountKeypadPreview

/**
 * Primary amount input for ledger forms. The Material 3 clickable surface preserves proper
 * interaction semantics while keeping the amount aligned with the rest of the form.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun MoneyAmountHeroField(
    value: String,
    label: String,
    accent: Color,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    allowSigned: Boolean = false,
    enabled: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null,
    autoOpenKeypad: Boolean = false,
    inlineKeypad: Boolean = false,
) {
    var showKeypad by remember { mutableStateOf(autoOpenKeypad) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val expressionScrollState = rememberScrollState()
    val currencySymbol = LocalCurrencySymbol.current
    val previewAmount = remember(value, allowSigned, inlineKeypad) {
        if (inlineKeypad && value.drop(1).any { it == '+' || it == '-' }) {
            parseAmountKeypadPreview(value, allowSigned)
        } else null
    }
    val previewText = previewAmount?.let { "= $currencySymbol${AmountFormatter.formatPlain(it)}" }

    LaunchedEffect(value, expressionScrollState.maxValue) {
        expressionScrollState.scrollTo(expressionScrollState.maxValue)
    }

    if (showKeypad) {
        MoneyAmountKeypadSheet(
            value = value,
            label = label,
            allowSigned = allowSigned,
            onValueChange = onValueChange,
            onDismiss = { showKeypad = false },
        )
    }

    val displayValue = value.ifBlank { "0" }
    val amountStyle = when {
        displayValue.length > 14 -> MaterialTheme.typography.headlineSmall
        displayValue.length > 9 -> MaterialTheme.typography.displayMedium
        else -> MaterialTheme.typography.displayLarge
    }
    val amountColor = when {
        isError -> MaterialTheme.colorScheme.error
        value.isBlank() -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> accent
    }

    Surface(
        onClick = {
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
            showKeypad = !inlineKeypad
        },
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = label
                stateDescription = displayValue + if (previewText != null) "，$previewText" else ""
                if (isError && supportingText != null) error(supportingText)
            },
        enabled = enabled,
        color = Color.Transparent,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(vertical = MoneyDimens.SpacingXl),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = label + " · " + LocalCurrencySymbol.current,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clearAndSetSemantics {},
            )
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val availableWidth = with(density) { maxWidth.toPx() }
                val candidates = listOf(amountStyle, MaterialTheme.typography.displayMedium, MaterialTheme.typography.headlineSmall)
                val readableStyle = candidates.firstOrNull {
                    textMeasurer.measure(displayValue, style = it, softWrap = false).size.width <= availableWidth
                } ?: MaterialTheme.typography.headlineSmall
                Text(
                    text = displayValue,
                    style = readableStyle,
                    color = amountColor,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.horizontalScroll(expressionScrollState).clearAndSetSemantics {},
                )
            }
            if (previewText != null) {
                MoneyAmountText(
                    text = previewText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = accent,
                    modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
                )
            }
            if (isError && supportingText != null) {
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
        }
    }
}
