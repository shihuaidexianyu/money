package com.shihuaidexianyu.money.ui.record

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shihuaidexianyu.money.R
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.PortableSettings
import com.shihuaidexianyu.money.ui.balance.investmentDeltaText
import com.shihuaidexianyu.money.ui.common.AccountIconBadge
import com.shihuaidexianyu.money.ui.common.AsyncContentRenderer
import com.shihuaidexianyu.money.ui.common.CollectUiEffects
import com.shihuaidexianyu.money.ui.common.LocalRootSnackbarDispatcher
import com.shihuaidexianyu.money.ui.common.LocalRootSnackbarHostState
import com.shihuaidexianyu.money.ui.common.MoneyAmountHeroField
import com.shihuaidexianyu.money.ui.common.MoneyAmountKeypad
import com.shihuaidexianyu.money.ui.common.MoneyCard
import com.shihuaidexianyu.money.ui.common.MoneyDateTimeFields
import com.shihuaidexianyu.money.ui.common.MoneyDateTimePickerField
import com.shihuaidexianyu.money.ui.common.MoneyDateTimePickerHost
import com.shihuaidexianyu.money.ui.common.MoneyExpandableSection
import com.shihuaidexianyu.money.ui.common.MoneyFormPage
import com.shihuaidexianyu.money.ui.common.MoneyInlineLabelValue
import com.shihuaidexianyu.money.ui.common.MoneyListRow
import com.shihuaidexianyu.money.ui.common.MoneySaveButton
import com.shihuaidexianyu.money.ui.common.MoneySingleLineField
import com.shihuaidexianyu.money.ui.common.accountPickerSections
import com.shihuaidexianyu.money.ui.common.formAsyncContent
import com.shihuaidexianyu.money.ui.common.formatInAppAmount
import com.shihuaidexianyu.money.ui.common.rememberDirtyFormBackAction
import com.shihuaidexianyu.money.ui.common.rootSnackbarEffect
import com.shihuaidexianyu.money.ui.theme.LocalMoneyColors
import kotlinx.coroutines.delay
import kotlin.math.abs

@Composable
internal fun LedgerEntryScreen(
    viewModel: LedgerEntryViewModel,
    onBack: () -> Unit,
    onManageAccounts: () -> Unit,
    modifier: Modifier = Modifier,
    settings: PortableSettings = PortableSettings(),
    onSaved: () -> Unit = onBack,
    onStartCashFlow: (CashFlowDirection, Long, Long) -> Unit = { _, _, _ -> },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val rootSnackbar = LocalRootSnackbarDispatcher.current
    val rootSnackbarHostState = LocalRootSnackbarHostState.current
    val guardedExit = rememberDirtyFormBackAction(state.isDirty, onBack, state.isSaving || state.completed)
    val goBack = {
        if (state.isLoading || state.loadErrorMessageRes != null || !viewModel.back()) guardedExit()
    }
    BackHandler { goBack() }
    CollectUiEffects(viewModel.effectFlow, snackbar) {}

    val savedMessage = stringResource(
        when {
            state.kind != LedgerEntryKind.RECONCILE -> R.string.entry_saved
            state.selectedAccount?.isInvestment == true || state.pendingTerminal?.balanceResult?.isInvestmentAccount == true -> R.string.balance_value_saved_brief
            else -> R.string.balance_saved_brief
        },
    )
    state.pendingTerminal?.let { terminal ->
        LaunchedEffect(terminal.token) {
            rootSnackbar?.dispatch(rootSnackbarEffect(savedMessage, token = terminal.token))
            onSaved()
            viewModel.ackTerminal(terminal.token)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.reloadAccounts()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var picker by remember { mutableStateOf<MoneyDateTimePickerField?>(null) }
    MoneyDateTimePickerHost(
        field = picker, currentMillis = state.occurredAtMillis,
        onPick = viewModel::updateOccurredAt, onDismiss = { picker = null },
    )
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    // A new page has its own hit targets. Briefly disarm them so the previous page's
    // rapid second tap cannot become an account choice, keypad key or final Save.
    var pageArmed by remember(state.step) { mutableStateOf(false) }
    LaunchedEffect(state.step) {
        focus.clearFocus(force = true)
        keyboard?.hide()
        delay(180)
        pageArmed = true
    }
    val enabled = state.editable && pageArmed && state.loadErrorMessageRes == null
    var hiddenExpanded by rememberSaveable { mutableStateOf(false) }

    AnimatedContent(
        targetState = state.step,
        modifier = modifier.fillMaxSize(),
        transitionSpec = { fadeIn(tween(120)) togetherWith fadeOut(tween(90)) },
        label = "ledgerEntryPage",
    ) { step ->
        val pageEnabled = enabled && step == state.step
        MoneyFormPage(
            title = stringResource(step.titleRes()),
            modifier = Modifier.testTag("entry_page_${step.name}"),
            snackbarHostState = snackbar,
            rootSnackbarHostState = rootSnackbarHostState,
            compactWhenImeVisible = true,
            onBack = goBack,
            trailing = {
                TextButton(onClick = guardedExit, enabled = !state.isSaving && !state.completed) {
                    Text(stringResource(R.string.action_close))
                }
            },
            header = {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                    Text(
                        stringResource(R.string.entry_progress, step.number),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (step != LedgerEntryStep.TYPE && state.kind != null) {
                        Text(
                            listOfNotNull(
                                stringResource(state.kind!!.labelRes()),
                                state.selectedAccount?.name,
                                state.toAccount?.name?.takeIf { state.kind == LedgerEntryKind.TRANSFER }?.let { "→ $it" },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            },
            footer = if (step == LedgerEntryStep.CONFIRM && !state.isLoading && state.loadErrorMessageRes == null) {
                {
                    MoneySaveButton(
                        onClick = viewModel::save, isSaving = state.isSaving,
                        enabled = pageEnabled && (state.kind != LedgerEntryKind.RECONCILE ||
                            (!state.previewLoading && state.previewError == null && state.delta != null)),
                        label = stringResource(R.string.entry_confirm_save),
                        modifier = Modifier.testTag("entry_save"),
                    )
                }
            } else null,
        ) {
            if (state.isLoading || state.loadErrorMessageRes != null) {
                item {
                    AsyncContentRenderer(
                        content = formAsyncContent(state, state.isLoading, state.loadErrorMessageRes?.let { stringResource(it) }, "ledger-entry"),
                        onRetry = viewModel::reloadAccounts,
                        modifier = Modifier.heightIn(min = 240.dp),
                        data = { _, _ -> },
                    )
                }
            } else when (step) {
                LedgerEntryStep.TYPE -> {
                    item { Text(stringResource(R.string.entry_type_hint)) }
                    LedgerEntryKind.entries.forEach { kind ->
                        item(key = kind) {
                            MoneyCard {
                                MoneyListRow(
                                    title = stringResource(kind.labelRes()),
                                    subtitle = stringResource(kind.hintRes()),
                                    onClick = { viewModel.chooseKind(kind) }, enabled = pageEnabled,
                                    modifier = Modifier.testTag("entry_kind_${kind.name}"),
                                )
                            }
                        }
                    }
                    entryAccountError(state, onManageAccounts)
                }
                LedgerEntryStep.ACCOUNT, LedgerEntryStep.TO_ACCOUNT -> {
                    accountStep(state, step, pageEnabled, settings, viewModel, onManageAccounts, hiddenExpanded) {
                        hiddenExpanded = !hiddenExpanded
                    }
                }
                LedgerEntryStep.AMOUNT -> {
                    item {
                        MoneyAmountHeroField(
                            value = state.inputText, onValueChange = viewModel::updateAmount,
                            label = stringResource(if (state.kind == LedgerEntryKind.RECONCILE) {
                                if (state.selectedAccount?.isInvestment == true) R.string.balance_current_market_value else R.string.balance_actual
                            } else R.string.field_amount),
                            accent = if (state.kind == LedgerEntryKind.EXPENSE) LocalMoneyColors.current.expense else LocalMoneyColors.current.income,
                            inlineKeypad = true, allowSigned = state.kind == LedgerEntryKind.RECONCILE,
                            enabled = pageEnabled, isError = state.amountError != null, supportingText = state.amountError,
                        )
                    }
                    if (state.kind == LedgerEntryKind.RECONCILE || state.kind == LedgerEntryKind.TRANSFER) {
                        item {
                            TextButton(onClick = viewModel::useAccountBalance, enabled = pageEnabled && !state.previewLoading && state.previewError == null) {
                                Text(stringResource(if (state.kind == LedgerEntryKind.RECONCILE) R.string.balance_set_unchanged else R.string.transfer_all))
                            }
                        }
                    }
                    item {
                        MoneyAmountKeypad(
                            value = state.inputText, onValueChange = viewModel::updateAmount,
                            onDone = viewModel::continueToConfirmation,
                            allowSigned = state.kind == LedgerEntryKind.RECONCILE,
                            enabled = pageEnabled, doneLabel = stringResource(R.string.entry_continue),
                        )
                    }
                    item {
                        TextButton(onClick = viewModel::editAccount, enabled = pageEnabled) {
                            Text(stringResource(R.string.entry_change_account))
                        }
                    }
                }
                LedgerEntryStep.CONFIRM -> {
                    item {
                        MoneyCard {
                            MoneyInlineLabelValue(
                                stringResource(R.string.entry_record_kind), stringResource(requireNotNull(state.kind).labelRes()),
                            )
                            MoneyInlineLabelValue(
                                stringResource(if (state.kind == LedgerEntryKind.TRANSFER) R.string.transfer_from_account else R.string.account_single),
                                state.selectedAccount?.name.orEmpty(),
                            )
                            if (state.kind == LedgerEntryKind.TRANSFER) {
                                MoneyInlineLabelValue(stringResource(R.string.transfer_to_account), state.toAccount?.name.orEmpty())
                            }
                            MoneyInlineLabelValue(
                                stringResource(if (state.kind == LedgerEntryKind.RECONCILE) {
                                    if (state.selectedAccount?.isInvestment == true) R.string.balance_current_market_value else R.string.balance_actual
                                } else R.string.field_amount),
                                state.amount?.let { formatInAppAmount(it, settings) }.orEmpty(),
                            )
                            TextButton(onClick = { viewModel.back() }, enabled = pageEnabled) {
                                Text(stringResource(R.string.entry_change_amount))
                            }
                        }
                    }
                    if (state.kind == LedgerEntryKind.RECONCILE) {
                        item {
                            ReconciliationEvidence(state, settings, pageEnabled, viewModel::refreshPreview, onStartCashFlow)
                        }
                    }
                    item {
                        MoneyCard {
                            MoneyDateTimeFields(
                                valueMillis = state.occurredAtMillis,
                                onDateClick = { if (pageEnabled) picker = MoneyDateTimePickerField.DATE },
                                onTimeClick = { if (pageEnabled) picker = MoneyDateTimePickerField.TIME },
                                timeSubtitle = if (state.timeEdited) null else stringResource(R.string.ledger_default_current_time),
                                errorText = state.occurredAtError,
                            )
                        }
                    }
                    if (state.kind != LedgerEntryKind.RECONCILE) {
                        item {
                            MoneyExpandableSection(
                                title = stringResource(R.string.field_optional_note),
                                summary = state.note.ifBlank { stringResource(R.string.entry_note_hint) },
                                initiallyExpanded = state.note.isNotBlank(), forceExpanded = state.noteError != null,
                            ) {
                                MoneySingleLineField(
                                    state.note, viewModel::updateNote, stringResource(R.string.field_optional_note),
                                    modifier = Modifier.testTag("entry_note"),
                                    enabled = pageEnabled, isError = state.noteError != null, supportingText = state.noteError,
                                )
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(state.noteSuggestions) { note ->
                                        SuggestionChip(onClick = { viewModel.updateNote(note) }, enabled = pageEnabled, label = { Text(note) })
                                    }
                                }
                            }
                        }
                    }
                    item { Text(stringResource(R.string.entry_not_saved_hint), style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

private fun LazyListScope.accountStep(
    state: LedgerEntryUiState,
    step: LedgerEntryStep,
    enabled: Boolean,
    settings: PortableSettings,
    viewModel: LedgerEntryViewModel,
    onManageAccounts: () -> Unit,
    hiddenExpanded: Boolean,
    onToggleHidden: () -> Unit,
) {
    item {
        Text(stringResource(if (step == LedgerEntryStep.TO_ACCOUNT) R.string.entry_to_hint else if (state.kind == LedgerEntryKind.TRANSFER) R.string.entry_from_hint else R.string.entry_account_hint))
    }
    val sections = accountPickerSections(state.accounts, hiddenExpanded)
    items(sections.visibleAccounts + sections.hiddenAccounts, key = { "account_${it.id}" }) { account ->
        MoneyCard {
            MoneyListRow(
                title = account.name,
                subtitle = account.balance?.let { formatInAppAmount(it, settings) },
                trailing = when {
                    step == LedgerEntryStep.TO_ACCOUNT && account.id == state.accountId -> stringResource(R.string.transfer_from_account)
                    account.id == (if (step == LedgerEntryStep.TO_ACCOUNT) state.toAccountId else state.accountId) -> stringResource(R.string.entry_selected)
                    else -> null
                },
                leading = { AccountIconBadge(account.iconName, account.colorName, size = 30.dp, iconSize = 17.dp) },
                enabled = enabled && (step != LedgerEntryStep.TO_ACCOUNT || account.id != state.accountId),
                onClick = { viewModel.chooseAccount(account.id, step) },
                modifier = Modifier.testTag("entry_account_${account.id}"),
            )
        }
    }
    if (sections.hiddenAccountCount > 0) {
        item {
            TextButton(onClick = onToggleHidden, enabled = enabled) {
                Text(stringResource(if (hiddenExpanded) R.string.account_picker_collapse_hidden else R.string.account_picker_show_hidden_format, sections.hiddenAccountCount))
            }
        }
    }
    entryAccountError(state, onManageAccounts)
}

private fun LazyListScope.entryAccountError(state: LedgerEntryUiState, onManageAccounts: () -> Unit) {
    if (state.accountError != null || state.accounts.isEmpty() || (state.kind == LedgerEntryKind.TRANSFER && state.accounts.size < 2)) {
        item {
            Text(state.accountError ?: stringResource(if (state.kind == LedgerEntryKind.TRANSFER && state.accounts.isNotEmpty()) R.string.ledger_fab_need_second_message else R.string.ledger_fab_create_first_message), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onManageAccounts) { Text(stringResource(R.string.accounts_create)) }
        }
    }
}

@Composable
private fun ReconciliationEvidence(
    state: LedgerEntryUiState,
    settings: PortableSettings,
    enabled: Boolean,
    onRetry: () -> Unit,
    onStartCashFlow: (CashFlowDirection, Long, Long) -> Unit,
) {
    MoneyCard {
        when {
            state.previewLoading -> Text(stringResource(R.string.entry_preview_loading))
            state.previewError != null -> {
                Text(state.previewError, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry, enabled = enabled) { Text(stringResource(R.string.action_retry)) }
            }
            state.deltaOverflow -> Text(stringResource(R.string.entry_delta_out_of_range), color = MaterialTheme.colorScheme.error)
            else -> {
                val investment = state.selectedAccount?.isInvestment == true
                MoneyInlineLabelValue(
                    stringResource(if (investment) R.string.balance_recorded_market_value else R.string.balance_system),
                    state.systemBalance?.let { formatInAppAmount(it, settings) } ?: "—",
                )
                MoneyInlineLabelValue(
                    stringResource(if (investment) R.string.balance_delta_investment else R.string.balance_delta),
                    state.delta?.let { formatInAppAmount(it, settings) } ?: "—",
                )
                val delta = state.delta
                if (delta != null) {
                    Text(if (investment) investmentDeltaText(delta, state.systemBalance ?: 0L)
                    else stringResource(when {
                        delta > 0 -> R.string.balance_above_system
                        delta < 0 -> R.string.balance_below_system
                        else -> R.string.balance_unchanged_hint
                    }))
                }
                Text(stringResource(if (investment) R.string.balance_investment_hint else R.string.balance_correction_hint), style = MaterialTheme.typography.bodySmall)
                if (!investment && delta != null && delta != 0L && delta != Long.MIN_VALUE) {
                    listOf(CashFlowDirection.OUTFLOW, CashFlowDirection.INFLOW).forEach { direction ->
                        TextButton(onClick = { onStartCashFlow(direction, requireNotNull(state.accountId), abs(delta)) }, enabled = enabled) {
                            Text(stringResource(if (direction == CashFlowDirection.INFLOW) R.string.balance_record_income else R.string.balance_record_expense))
                        }
                    }
                }
            }
        }
    }
}

private fun LedgerEntryKind.labelRes(): Int = when (this) {
    LedgerEntryKind.INCOME -> R.string.entry_income
    LedgerEntryKind.EXPENSE -> R.string.entry_expense
    LedgerEntryKind.TRANSFER -> R.string.history_transfer
    LedgerEntryKind.RECONCILE -> R.string.entry_reconcile
}

private fun LedgerEntryKind.hintRes(): Int = when (this) {
    LedgerEntryKind.INCOME -> R.string.entry_income_hint
    LedgerEntryKind.EXPENSE -> R.string.entry_expense_hint
    LedgerEntryKind.TRANSFER -> R.string.entry_transfer_hint
    LedgerEntryKind.RECONCILE -> R.string.entry_reconcile_hint
}

private fun LedgerEntryStep.titleRes(): Int = when (this) {
    LedgerEntryStep.TYPE -> R.string.entry_type_title
    LedgerEntryStep.ACCOUNT -> R.string.account_choose
    LedgerEntryStep.TO_ACCOUNT -> R.string.entry_to_title
    LedgerEntryStep.AMOUNT -> R.string.entry_amount_title
    LedgerEntryStep.CONFIRM -> R.string.entry_confirm_title
}
