package com.shihuaidexianyu.money.ui.balance

import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shihuaidexianyu.money.R
import com.shihuaidexianyu.money.ui.common.CollectUiEffects
import com.shihuaidexianyu.money.ui.common.AsyncContentRenderer
import com.shihuaidexianyu.money.ui.common.formAsyncContent
import com.shihuaidexianyu.money.ui.common.FormTerminalKind
import com.shihuaidexianyu.money.ui.common.MoneyCard
import com.shihuaidexianyu.money.ui.common.MoneyDateTimeFields
import com.shihuaidexianyu.money.ui.common.MoneyDateTimePickerField
import com.shihuaidexianyu.money.ui.common.MoneyDateTimePickerHost
import com.shihuaidexianyu.money.ui.common.MoneyEmptyStateCard
import com.shihuaidexianyu.money.ui.common.MoneyFormPage
import com.shihuaidexianyu.money.ui.common.MoneySaveButton
import com.shihuaidexianyu.money.ui.common.MoneyStatusPill
import com.shihuaidexianyu.money.ui.common.MoneyConfirmDialog
import com.shihuaidexianyu.money.ui.common.MoneySelectionField
import com.shihuaidexianyu.money.ui.common.MoneyAmountKeypadSheet
import com.shihuaidexianyu.money.ui.common.MoneyAmountText
import com.shihuaidexianyu.money.ui.common.maskInAppAmount
import com.shihuaidexianyu.money.ui.common.signedFormatInAppAmount
import com.shihuaidexianyu.money.util.AmountFormatter
import com.shihuaidexianyu.money.ui.common.formatInAppAmount
import com.shihuaidexianyu.money.ui.common.rememberDirtyFormBackAction
import com.shihuaidexianyu.money.util.DateTimeTextFormatter

@Composable
fun BatchReconcileScreen(
    viewModel: BatchReconcileViewModel,
    onBack: () -> Unit,
    onSaved: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var dateTimeField by remember { mutableStateOf<MoneyDateTimePickerField?>(null) }
    var editingAccountId by rememberSaveable { mutableStateOf<Long?>(null) }
    var review by rememberSaveable { mutableStateOf<BatchReconcileReview?>(null) }
    val guardedBack = rememberDirtyFormBackAction(state.isDirty, onBack, isSaving = state.isSaving)

    CollectUiEffects(viewModel.effectFlow, snackbarHostState) {}
    state.accounts.firstOrNull { it.accountId == editingAccountId }?.let { account ->
        MoneyAmountKeypadSheet(
            value = account.actualBalanceText.ifBlank { AmountFormatter.formatPlain(account.systemBalance) },
            label = account.name + " · " + stringResource(R.string.batch_actual_balance), allowSigned = true,
            onValueChange = { viewModel.updateActualBalance(account.accountId, it) },
            onDismiss = { editingAccountId = null },
        )
    }
    review?.let { snapshot ->
        MoneyConfirmDialog(
            title = stringResource(R.string.batch_reconcile_review),
            message = stringResource(R.string.batch_reconcile_review_message,
                snapshot.timestamp?.let(DateTimeTextFormatter::format).orEmpty(), snapshot.balances.size,
                snapshot.changedCount),
            onConfirm = { review = null; viewModel.saveSelected(snapshot) },
            onDismiss = { review = null },
            confirmLabel = stringResource(R.string.action_save),
        )
    }
    state.pendingTerminal?.let { terminal ->
        LaunchedEffect(terminal.token) {
            if (terminal.kind == FormTerminalKind.SAVED) onSaved(requireNotNull(terminal.count))
            viewModel.ackTerminal(terminal.token)
        }
    }

    state.confirmTimeMillis?.let { confirmMillis ->
        MoneyDateTimePickerHost(
            field = dateTimeField,
            currentMillis = confirmMillis,
            onPick = viewModel::updateConfirmTime,
            onDismiss = { dateTimeField = null },
        )
    }

    MoneyFormPage(
        title = stringResource(R.string.batch_reconcile_title),
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        onBack = guardedBack,
        footer = {
            if (!state.isLoading && state.loadErrorMessageRes == null && state.accounts.isNotEmpty()) {
                MoneySaveButton(
                    onClick = {
                        if (state.accounts.any { it.isSelected && (it.actualBalance == null || it.delta == null) })
                            viewModel.saveSelected() else review = state.review()
                    },
                    isSaving = state.isSaving,
                    enabled = state.selectedCount > 0 && !state.isRecalculating && state.pendingTerminal == null,
                    label = stringResource(R.string.batch_reconcile_commit_count, state.selectedCount),
                )
            }
        },
    ) {
        if (state.isLoading || state.loadErrorMessageRes != null) {
            item {
                AsyncContentRenderer(
                    content = formAsyncContent(state, state.isLoading, state.loadErrorMessageRes?.let { stringResource(it) }, "batch-reconcile"),
                    onRetry = viewModel::retryLoad,
                    modifier = Modifier.heightIn(min = 240.dp),
                    data = { _, _ -> },
                )
            }
            return@MoneyFormPage
        }

        if (state.accounts.isEmpty()) {
            item {
                MoneyEmptyStateCard(
                    title = stringResource(R.string.batch_reconcile_empty),
                    subtitle = stringResource(R.string.batch_reconcile_empty_description),
                    action = {
                        Button(
                            onClick = guardedBack,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.action_back_home))
                        }
                    },
                )
            }
            return@MoneyFormPage
        }

        item {
            MoneyCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MoneyStatusPill(
                            text = stringResource(R.string.batch_reconcile_pending_format, state.accounts.size),
                        )
                        Text(
                            text = stringResource(R.string.batch_reconcile_selected_format, state.selectedCount),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = { viewModel.setAllSelected(state.selectedCount != state.accounts.size) },
                            enabled = !state.isSaving,
                        ) {
                            Text(
                                stringResource(
                                    if (state.selectedCount == state.accounts.size) {
                                        R.string.batch_deselect_all
                                    } else {
                                        R.string.batch_select_all
                                    },
                                ),
                            )
                        }
                    }
                    state.confirmTimeMillis?.let { millis ->
                        if (state.isTimeLocked) {
                            MoneySelectionField(
                                label = stringResource(R.string.batch_reconcile_review),
                                value = DateTimeTextFormatter.format(millis),
                            )
                        } else MoneyDateTimeFields(
                            valueMillis = millis,
                            onDateClick = { dateTimeField = MoneyDateTimePickerField.DATE },
                            onTimeClick = { dateTimeField = MoneyDateTimePickerField.TIME },
                            dateLabel = stringResource(R.string.batch_confirm_date),
                            timeLabel = stringResource(R.string.batch_confirm_time),
                        )
                    }
                }
            }
        }
        item {
            Text(
                text = stringResource(R.string.batch_actual_balance_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        items(state.accounts, key = { it.accountId }) { account ->
            MoneyCard {
                BatchReconcileAccountRow(
                    account = account, state = state,
                    onToggle = { viewModel.toggleAccount(account.accountId) },
                    onEditBalance = { editingAccountId = account.accountId },
                )
            }
        }

    }
}

@Composable
internal fun BatchReconcileAccountRow(
    account: BatchReconcileAccountUiModel,
    state: BatchReconcileUiState,
    onToggle: () -> Unit,
    onEditBalance: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().toggleable(
                value = account.isSelected, enabled = !state.isSaving, role = Role.Checkbox,
            ) { onToggle() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Checkbox(checked = account.isSelected, onCheckedChange = null, enabled = !state.isSaving)
            Text(account.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            if (account.isFailed) MoneyStatusPill(stringResource(R.string.status_failed),
                accent = MaterialTheme.colorScheme.error)
        }
        Text(stringResource(if (account.isInvestment) R.string.account_kind_investment else R.string.account_kind_funding)
            + " · " + (account.lastBalanceUpdateAt?.let {
                stringResource(R.string.batch_reconcile_last_format, DateTimeTextFormatter.format(it))
            } ?: stringResource(R.string.batch_reconcile_never)),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        BatchBalanceLine(stringResource(R.string.batch_system_balance),
            formatInAppAmount(account.systemBalance, state.settings))
        MoneySelectionField(
            label = stringResource(R.string.batch_actual_balance),
            value = account.actualBalance?.let { formatInAppAmount(it, state.settings) }
                ?: maskInAppAmount(account.actualBalanceText),
            onClick = onEditBalance.takeIf { !state.isSaving && !account.isInputLocked },
            isError = account.amountError,
            supportingText = if (account.amountError) stringResource(R.string.validation_valid_amount) else null,
        )
        BatchBalanceLine(
            stringResource(if (account.isInvestment) R.string.batch_investment_delta_label else R.string.batch_delta_label),
            account.delta?.let { signedFormatInAppAmount(it, state.settings) } ?: "—",
        )
        if (account.isInputLocked) Text(
            stringResource(R.string.batch_retry_input_locked), style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun BatchBalanceLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MoneyAmountText(value, Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleMedium)
    }
}
