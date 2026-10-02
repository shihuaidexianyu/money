package com.shihuaidexianyu.money.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.spring
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import com.shihuaidexianyu.money.R
import com.shihuaidexianyu.money.util.AmountFormatter
import com.shihuaidexianyu.money.util.AmountKey
import com.shihuaidexianyu.money.util.appendAmountKey
import com.shihuaidexianyu.money.util.parseAmountKeypadPreview

private data class AmountKeypadButtonSpec(
    val label: String? = null,
    val key: AmountKey? = null,
    val isOperator: Boolean = false,
    val isClear: Boolean = false,
    val isDone: Boolean = false,
    val weight: Float = 1f,
    @param:StringRes val labelRes: Int? = null,
)

private val amountKeypadRows = listOf(
    listOf(
        AmountKeypadButtonSpec("7", AmountKey.Digit(7)),
        AmountKeypadButtonSpec("8", AmountKey.Digit(8)),
        AmountKeypadButtonSpec("9", AmountKey.Digit(9)),
        AmountKeypadButtonSpec(labelRes = R.string.action_delete, key = AmountKey.Delete),
    ),
    listOf(
        AmountKeypadButtonSpec("4", AmountKey.Digit(4)),
        AmountKeypadButtonSpec("5", AmountKey.Digit(5)),
        AmountKeypadButtonSpec("6", AmountKey.Digit(6)),
        AmountKeypadButtonSpec("+", AmountKey.Plus, isOperator = true),
    ),
    listOf(
        AmountKeypadButtonSpec("1", AmountKey.Digit(1)),
        AmountKeypadButtonSpec("2", AmountKey.Digit(2)),
        AmountKeypadButtonSpec("3", AmountKey.Digit(3)),
        AmountKeypadButtonSpec("-", AmountKey.Minus, isOperator = true),
    ),
    listOf(
        AmountKeypadButtonSpec("C", AmountKey.Clear, isClear = true),
        AmountKeypadButtonSpec("0", AmountKey.Digit(0)),
        AmountKeypadButtonSpec(".", AmountKey.Decimal),
        AmountKeypadButtonSpec(labelRes = R.string.action_done, isDone = true),
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MoneyAmountKeypadSheet(
    value: String,
    label: String,
    allowSigned: Boolean,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val currencySymbol = LocalCurrencySymbol.current
    val previewAmount = remember(value, allowSigned) {
        parseAmountKeypadPreview(value, allowSigned)
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            val compact = maxHeight < 400.dp
            val contentModifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp)
            if (compact) {
                Row(
                    modifier = contentModifier,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    AmountKeypadDisplay(label, value, previewAmount, currencySymbol, Modifier.weight(0.35f))
                    MoneyAmountKeypad(
                        value, onValueChange, onDismiss,
                        modifier = Modifier.weight(0.65f), allowSigned = allowSigned, compact = true,
                    )
                }
            } else {
                Column(modifier = contentModifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    AmountKeypadDisplay(label, value, previewAmount, currencySymbol)
                    MoneyAmountKeypad(value, onValueChange, onDismiss, allowSigned = allowSigned)
                }
            }
        }
    }
}

/** Reusable keypad: the commit action is supplied by the host, not coupled to sheet dismissal. */
@Composable
internal fun MoneyAmountKeypad(
    value: String,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    allowSigned: Boolean = false,
    compact: Boolean = false,
    enabled: Boolean = true,
    doneLabel: String = stringResource(R.string.action_done),
    isSaving: Boolean = false,
) {
    val haptics = LocalHapticFeedback.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        amountKeypadRows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { spec ->
                    AmountKeypadButton(
                        spec = spec,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                            if (spec.isDone) onDone()
                            else onValueChange(appendAmountKey(value, requireNotNull(spec.key), allowSigned))
                        },
                        modifier = Modifier.weight(spec.weight),
                        keyHeight = if (compact) 48.dp else 58.dp,
                        enabled = enabled,
                        doneLabel = doneLabel,
                        isSaving = isSaving,
                    )
                }
            }
        }
    }
}

@Composable
private fun AmountKeypadDisplay(
    label: String,
    value: String,
    previewAmount: Long?,
    currencySymbol: String,
    modifier: Modifier = Modifier,
) {
    val expressionScrollState = rememberScrollState()
    val hasPreview = previewAmount != null && value.isNotBlank()
    val previewText = if (hasPreview) {
        "= $currencySymbol${AmountFormatter.formatPlain(requireNotNull(previewAmount))}"
    } else {
        ""
    }

    LaunchedEffect(value, expressionScrollState.maxValue) {
        expressionScrollState.scrollTo(expressionScrollState.maxValue)
    }

    Surface(
        modifier = modifier.fillMaxWidth().clearAndSetSemantics {
            contentDescription = label
            stateDescription = value.ifBlank { "0" } + if (hasPreview) "，$previewText" else ""
        },
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(
                    text = value.ifBlank { "0" },
                    modifier = Modifier
                        .horizontalScroll(expressionScrollState)
                        .clearAndSetSemantics {},
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
            if (previewText.isNotEmpty()) {
                Text(
                    text = previewText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 1,
                    textAlign = TextAlign.End,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}

@Composable
private fun AmountKeypadButton(
    spec: AmountKeypadButtonSpec,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    keyHeight: Dp = 58.dp,
    enabled: Boolean = true,
    doneLabel: String = stringResource(R.string.action_done),
    isSaving: Boolean = false,
) {
    val resolvedLabel = if (spec.isDone) doneLabel else spec.labelRes?.let { stringResource(it) } ?: requireNotNull(spec.label)
    val accessibleLabel = when (spec.key) {
        AmountKey.Clear -> stringResource(R.string.amount_keypad_clear)
        AmountKey.Plus -> stringResource(R.string.amount_keypad_plus)
        AmountKey.Minus -> stringResource(R.string.amount_keypad_minus)
        AmountKey.Decimal -> stringResource(R.string.amount_keypad_decimal)
        else -> resolvedLabel
    }
    val isDelete = spec.key == AmountKey.Delete
    // Three key weights: digits on a quiet surface, the operator column (delete included) on the
    // secondary container, and clear on the error container.
    val containerColor = when {
        spec.isClear -> MaterialTheme.colorScheme.errorContainer
        spec.isOperator || isDelete -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = when {
        spec.isClear -> MaterialTheme.colorScheme.error
        spec.isOperator || isDelete -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    // Keys are shorter than the 56dp save pill, so a full capsule would pinch into a lozenge;
    // 20dp is the roundest corner the 58dp height still holds as a slab.
    val keyShape = MaterialTheme.shapes.large
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = 1f, stiffness = 800f),
        label = "amountKeyPressScale",
    )

    val buttonModifier = modifier
        .height(keyHeight)
        .scale(pressScale)
        .semantics { contentDescription = accessibleLabel }
    val content: @Composable () -> Unit = {
        Box(contentAlignment = Alignment.Center) {
            when {
                spec.isDone && isSaving -> CircularProgressIndicator(
                    modifier = Modifier.size(20.dp), strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                isDelete -> Text(
                    stringResource(R.string.minimal_backspace),
                    style = MaterialTheme.typography.titleMedium,
                )
                spec.isDone -> Text(
                    text = resolvedLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                else -> Text(
                    text = resolvedLabel,
                    style = if (keyHeight <= 48.dp || LocalDensity.current.fontScale > 1.3f)
                        MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
    if (spec.isDone) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = buttonModifier,
            contentPadding = PaddingValues(0.dp),
            // The keypad's commit key is a capsule, echoing the form's save pill one step below.
            shape = CircleShape,
            interactionSource = interactionSource,
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 2.dp,
                pressedElevation = 0.dp,
                disabledElevation = 0.dp,
            ),
            content = { content() },
        )
    } else {
        FilledTonalButton(
            onClick = onClick,
            enabled = enabled,
            modifier = buttonModifier,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = containerColor,
                contentColor = contentColor,
            ),
            contentPadding = PaddingValues(0.dp),
            shape = keyShape,
            interactionSource = interactionSource,
            elevation = ButtonDefaults.filledTonalButtonElevation(
                defaultElevation = 1.dp,
                pressedElevation = 0.dp,
                disabledElevation = 0.dp,
            ),
            content = { content() },
        )
    }
}
