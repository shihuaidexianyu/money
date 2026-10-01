package com.shihuaidexianyu.money.ui.record

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.shihuaidexianyu.money.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.ui.common.AccountPickerDialog
import com.shihuaidexianyu.money.ui.common.AsyncContentRenderer
import com.shihuaidexianyu.money.ui.common.formAsyncContent
import com.shihuaidexianyu.money.ui.common.CollectUiEffects
import com.shihuaidexianyu.money.ui.common.FormTerminalKind
import com.shihuaidexianyu.money.ui.common.MoneyAmountHeroField
import com.shihuaidexianyu.money.ui.common.MoneyAmountKeypad
import com.shihuaidexianyu.money.ui.common.MoneyExpandableSection
import com.shihuaidexianyu.money.ui.common.MoneyCard
import com.shihuaidexianyu.money.ui.common.MoneyDateTimeFields
import com.shihuaidexianyu.money.ui.common.MoneyDateTimePickerHost
import com.shihuaidexianyu.money.ui.common.MoneyDateTimePickerField
import com.shihuaidexianyu.money.ui.common.MoneyFormPage
import com.shihuaidexianyu.money.ui.common.MoneySaveButton
import com.shihuaidexianyu.money.ui.common.MoneySelectionField
import com.shihuaidexianyu.money.ui.common.MoneySingleLineField
import com.shihuaidexianyu.money.ui.common.rememberDirtyFormBackAction
import com.shihuaidexianyu.money.ui.theme.LocalMoneyColors
import com.shihuaidexianyu.money.util.DateTimeTextFormatter

@Composable
fun RecordCashFlowScreen(
    viewModel: RecordCashFlowViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onSaved: () -> Unit = onBack,
    onTransfer: () -> Unit = {},
    onReconcile: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAccountPicker by remember { mutableStateOf(false) }
    var dateTimeField by remember { mutableStateOf<MoneyDateTimePickerField?>(null) }
    val selectedAccount = state.accounts.firstOrNull { it.id == state.selectedAccountId }
    val guardedBack = rememberDirtyFormBackAction(state.isDirty, onBack, isSaving = state.isSaving)
    val moneyColors = LocalMoneyColors.current
    val windowSize = LocalWindowInfo.current.containerSize
    val shortScreen = with(LocalDensity.current) { windowSize.height.toDp() } < 480.dp
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val haptics = LocalHapticFeedback.current

    CollectUiEffects(viewModel.effectFlow, snackbarHostState) {}
    state.pendingTerminal?.let { terminal ->
        LaunchedEffect(terminal.token) {
            if (terminal.kind == FormTerminalKind.SAVED) {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onSaved()
            }
            viewModel.ackTerminal(terminal.token)
        }
    }

    if (showAccountPicker) {
        AccountPickerDialog(
            title = stringResource(R.string.account_choose),
            accounts = state.accounts,
            selectedAccountId = state.selectedAccountId,
            onDismiss = { showAccountPicker = false },
            onPick = {
                viewModel.updateAccount(it)
                showAccountPicker = false
            },
        )
    }

    MoneyDateTimePickerHost(
        field = dateTimeField,
        currentMillis = state.occurredAtMillis,
        onPick = viewModel::updateOccurredAt,
        onDismiss = { dateTimeField = null },
    )

    MoneyFormPage(
        title = stringResource(
            if (state.direction == CashFlowDirection.INFLOW) {
                R.string.ledger_record_income_title
            } else {
                R.string.ledger_record_expense_title
            },
        ),
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        onBack = guardedBack,
        footer = {
            if (!state.isLoading && state.loadErrorMessageRes == null) {
                if (shortScreen || imeVisible) {
                    MoneySaveButton(
                        onClick = { viewModel.save() },
                        isSaving = state.isSaving,
                        enabled = state.pendingTerminal == null,
                    )
                } else {
                    MoneyAmountKeypad(
                        value = state.amountText,
                        onValueChange = viewModel::updateAmount,
                        onDone = { viewModel.save() },
                        doneLabel = stringResource(R.string.action_save),
                        enabled = !state.isSaving && state.pendingTerminal == null,
                        isSaving = state.isSaving,
                    )
                }
            }
        },
    ) {
        if (state.isLoading || state.loadErrorMessageRes != null) {
            item {
                AsyncContentRenderer(
                    content = formAsyncContent(state, state.isLoading, state.loadErrorMessageRes?.let { stringResource(it) }, "record-cash"),
                    onRetry = viewModel::retryLoad,
                    modifier = Modifier.heightIn(min = 240.dp),
                    data = { _, _ -> },
                )
            }
            return@MoneyFormPage
        }
        if (state.canChangeDirection) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(selected = state.direction == CashFlowDirection.OUTFLOW,
                            onClick = { viewModel.updateDirection(CashFlowDirection.OUTFLOW) }, enabled = !state.isSaving,
                            label = { Text(stringResource(R.string.ledger_expense)) })
                    }
                    item {
                        FilterChip(selected = state.direction == CashFlowDirection.INFLOW,
                            onClick = { viewModel.updateDirection(CashFlowDirection.INFLOW) }, enabled = !state.isSaving,
                            label = { Text(stringResource(R.string.ledger_income)) })
                    }
                    item {
                        SuggestionChip(onClick = onTransfer, enabled = !state.isDirty && !state.isSaving,
                            label = { Text(stringResource(R.string.history_transfer)) })
                    }
                    item {
                        SuggestionChip(onClick = onReconcile, enabled = !state.isDirty && !state.isSaving,
                            label = { Text(stringResource(R.string.ledger_reconcile)) })
                    }
                }
            }
        }
        item {
            MoneyCard {
                MoneyAmountHeroField(
                    value = state.amountText,
                    onValueChange = viewModel::updateAmount,
                    label = stringResource(R.string.field_amount),
                    accent = if (state.direction == CashFlowDirection.INFLOW) {
                        moneyColors.income
                    } else {
                        moneyColors.expense
                    },
                    isError = state.amountError != null,
                    supportingText = state.amountError,
                    enabled = !state.isSaving,
                    autoOpenKeypad = shortScreen,
                    inlineKeypad = !shortScreen,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.52f))
                MoneySelectionField(
                    label = stringResource(R.string.account_single),
                    value = selectedAccount?.name ?: stringResource(R.string.field_please_choose),
                    onClick = { showAccountPicker = true },
                    isError = state.accountError != null,
                    supportingText = state.accountError,
                )
            }
        }
        item {
            MoneyExpandableSection(
                title = stringResource(R.string.quick_record_optional_fields),
                summary = if (state.timeEdited) DateTimeTextFormatter.format(state.occurredAtMillis)
                    else stringResource(R.string.ledger_default_current_time),
                initiallyExpanded = state.note.isNotBlank(),
                forceExpanded = state.noteError != null || state.occurredAtError != null,
            ) {
                MoneySingleLineField(
                    value = state.note,
                    onValueChange = viewModel::updateNote,
                    label = stringResource(R.string.field_optional_note),
                    isError = state.noteError != null,
                    supportingText = state.noteError,
                )
                if (state.noteSuggestions.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.heightIn(min = 36.dp),
                    ) {
                        items(state.noteSuggestions) { suggestion ->
                            SuggestionChip(
                                onClick = { viewModel.applyNoteSuggestion(suggestion) },
                                label = { Text(suggestion) },
                            )
                        }
                    }
                }
                MoneyDateTimeFields(
                    valueMillis = state.occurredAtMillis,
                    onDateClick = { dateTimeField = MoneyDateTimePickerField.DATE },
                    onTimeClick = { dateTimeField = MoneyDateTimePickerField.TIME },
                    timeSubtitle = if (state.timeEdited) {
                        null
                    } else {
                        stringResource(R.string.ledger_default_current_time)
                    },
                    errorText = state.occurredAtError,
                )

            }
        }
    }
}
