package com.shihuaidexianyu.money.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.SouthEast
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import com.shihuaidexianyu.money.domain.model.AmountVisibility
import com.shihuaidexianyu.money.domain.model.PortableSettings
import com.shihuaidexianyu.money.ui.theme.LocalMoneyColors
import com.shihuaidexianyu.money.util.AmountFormatter

/** User-configured currency symbol ("¥" by default), provided app-wide from PortableSettings. */
val LocalCurrencySymbol = compositionLocalOf { "¥" }
val LocalAmountVisibility = compositionLocalOf { AmountVisibility.VISIBLE }

@Composable
fun formatInAppAmount(
    amountInMinor: Long,
    settings: PortableSettings,
): String = AmountFormatter.format(
    amountInMinor = amountInMinor,
    settings = settings,
    visibility = LocalAmountVisibility.current,
)

/** Signed presentation for quantities where the plus sign carries meaning (P&L, net change). */
@Composable
fun signedFormatInAppAmount(
    amountInMinor: Long,
    settings: PortableSettings,
): String {
    val formatted = formatInAppAmount(amountInMinor, settings)
    return if (amountInMinor > 0L && LocalAmountVisibility.current == AmountVisibility.VISIBLE) "+$formatted" else formatted
}

@Composable
fun maskInAppAmount(formatted: String): String =
    if (LocalAmountVisibility.current == AmountVisibility.MASKED) "${LocalCurrencySymbol.current}••••" else formatted

/** Fit read-only money to available space without wrapping digits or shrinking below body text. */
@Composable
fun MoneyAmountText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
    textAlign: TextAlign? = null,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val candidates = listOf(style, MaterialTheme.typography.headlineSmall, MaterialTheme.typography.titleMedium,
        MaterialTheme.typography.bodyLarge).filter { it.fontSize <= style.fontSize }.distinct()
    BoxWithConstraints(modifier) {
        val availableWidth = with(density) { maxWidth.toPx() }
        val readableStyle = candidates.firstOrNull {
            measurer.measure(text, style = it, softWrap = false).size.width <= availableWidth - 2f
        } ?: candidates.last()
        val fits = measurer.measure(text, style = readableStyle, softWrap = false).size.width <= availableWidth - 2f
        val textModifier = if (fits) Modifier.fillMaxWidth()
            else Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
        Text(text, modifier = textModifier,
            style = readableStyle, color = color, textAlign = textAlign, maxLines = 1, softWrap = false)
    }
}

/**
 * Statement-style "before → after" balance transition. The before-value stays neutral while the
 * arrow and the after-value carry the semantic income/expense color, and the arrow itself turns
 * with the direction (up-right when the balance grew, down-right when it shrank). Currency symbols
 * are dropped (the row's amount carries it); privacy masking applies. Long pairs use two lines.
 */
@Composable
fun BalanceTransitionText(
    before: Long,
    after: Long,
    settings: PortableSettings,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign: TextAlign? = null,
) {
    @Composable
    fun part(value: Long) = formatInAppAmount(value, settings).removePrefix(settings.currencySymbol)
    val moneyColors = LocalMoneyColors.current
    val beforeText = part(before)
    val afterText = part(after)
    val stack = beforeText.length + afterText.length > 22 || LocalDensity.current.fontScale > 1.3f
    val style = MaterialTheme.typography.labelSmall
    val directionColor = when {
        after > before -> moneyColors.income
        after < before -> moneyColors.expense
        else -> color
    }
    val arrow = when {
        after > before -> Icons.Rounded.NorthEast
        after < before -> Icons.Rounded.SouthEast
        else -> Icons.AutoMirrored.Rounded.ArrowForward
    }
    val text = buildAnnotatedString {
        append(beforeText)
        append(if (stack) '\n' else ' ')
        appendInlineContent(balanceArrowInlineId, "→")
        append(' ')
        withStyle(SpanStyle(color = directionColor)) { append(afterText) }
    }
    val inlineContent = mapOf(
        balanceArrowInlineId to InlineTextContent(
            Placeholder(
                width = style.fontSize,
                height = style.fontSize,
                placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
            ),
        ) {
            Icon(imageVector = arrow, contentDescription = null, tint = directionColor)
        },
    )
    Text(
        text = text,
        inlineContent = inlineContent,
        style = style,
        color = color,
        textAlign = textAlign,
        maxLines = if (stack) 2 else 1,
        softWrap = false,
        modifier = modifier.horizontalScroll(rememberScrollState()),
    )
}

private const val balanceArrowInlineId = "balanceDirectionArrow"

/**
 * Integer share of [part] over [total] as a compact percent label ("82%" / "<1%").
 * Long-only arithmetic; this is presentation formatting, never money math.
 */
fun formatSharePercent(part: Long, total: Long): String {
    if (total <= 0L || part <= 0L) return ""
    val percent = part * 100L / total
    return if (percent < 1L) "<1%" else "$percent%"
}
