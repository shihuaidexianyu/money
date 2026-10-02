package com.shihuaidexianyu.money.ui.accounts

import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shihuaidexianyu.money.R
import com.shihuaidexianyu.money.ui.common.MoneyCard
import com.shihuaidexianyu.money.ui.common.AsyncContentRenderer
import com.shihuaidexianyu.money.ui.common.formAsyncContent
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.ui.common.CollectUiEffects
import com.shihuaidexianyu.money.ui.common.MoneyConfirmDialog
import com.shihuaidexianyu.money.ui.common.MoneyFormPage
import com.shihuaidexianyu.money.ui.common.MoneyListRow
import com.shihuaidexianyu.money.ui.common.MoneyListSection
import com.shihuaidexianyu.money.ui.common.MoneySaveButton
import com.shihuaidexianyu.money.ui.common.MoneySectionHeader
import com.shihuaidexianyu.money.ui.common.MoneySingleLineField
import com.shihuaidexianyu.money.ui.common.rememberDirtyFormBackAction
import com.shihuaidexianyu.money.ui.common.LocalRootSnackbarDispatcher
import com.shihuaidexianyu.money.ui.common.RootSnackbarAction
import com.shihuaidexianyu.money.ui.common.rootSnackbarEffect

private sealed interface EditAccountDialog {
    data object CloseConfirm : EditAccountDialog
}

@Composable
fun EditAccountScreen(
    viewModel: EditAccountViewModel,
    onBack: () -> Unit,
    onClosed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var dialog by remember { mutableStateOf<EditAccountDialog?>(null) }
    val guardedBack = rememberDirtyFormBackAction(state.isDirty, onBack, isSaving = state.isSaving)
    val rootDispatcher = LocalRootSnackbarDispatcher.current
    val hiddenDoneMessage = stringResource(R.string.account_hidden_done)
    val unhiddenDoneMessage = stringResource(R.string.account_unhidden_done)
    val undoLabel = stringResource(R.string.action_undo)

    CollectUiEffects(viewModel.effectFlow, snackbarHostState) { effect ->
        when (effect) {
            EditAccountEffect.Saved, EditAccountEffect.AccountClosed -> onBack()
            EditAccountEffect.Closed -> onClosed()
            is EditAccountEffect.HiddenChanged -> if (effect.hidden) {
                rootDispatcher?.dispatch(
                    rootSnackbarEffect(
                        message = hiddenDoneMessage,
                        actionLabel = undoLabel,
                        action = RootSnackbarAction.UnhideAccount(effect.accountId),
                    ),
                )
            } else {
                rootDispatcher?.dispatch(rootSnackbarEffect(message = unhiddenDoneMessage))
            }
            else -> {}
        }
    }

    dialog?.let { currentDialog ->
        when (currentDialog) {
            EditAccountDialog.CloseConfirm -> {
                MoneyConfirmDialog(
                    title = stringResource(R.string.account_close_title),
                    message = stringResource(R.string.account_close_message),
                    onConfirm = {
                        dialog = null
                        viewModel.closeAccount()
                    },
                    onDismiss = { dialog = null },
                    confirmLabel = stringResource(R.string.account_confirm_close),
                    destructive = true,
                )
            }
        }
    }

    MoneyFormPage(
        title = stringResource(R.string.account_management_title),
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        onBack = guardedBack,
        footer = {
            if (!state.isLoading && !state.isClosed) {
                MoneySaveButton(onClick = viewModel::save, isSaving = state.isSaving, enabled = !state.isLoading)
            }
        },
    ) {
        if (state.isLoading || state.loadErrorMessageRes != null) {
            item {
                AsyncContentRenderer(
                    content = formAsyncContent(state, state.isLoading, state.loadErrorMessageRes?.let { stringResource(it) }, "edit-account"),
                    onRetry = viewModel::retryLoad,
                    modifier = Modifier.heightIn(min = 240.dp),
                    data = { _, _ -> },
                )
            }
            return@MoneyFormPage
        }
        item { MoneySectionHeader(title = stringResource(R.string.account_information)) }
        item {
            // Inline name editing matches the create page; dirty tracking still flows through
            // viewModel.updateName, so the discard-on-back guard keeps working.
            MoneyCard {
                MoneySingleLineField(
                    value = state.name,
                    onValueChange = viewModel::updateName,
                    label = stringResource(R.string.account_name),
                    enabled = !state.isClosed && !state.isSaving,
                )
            }
        }
        if (state.isHidden && !state.isClosed) {
            item {
                MoneyListRow(title = stringResource(R.string.account_status_hidden),
                    trailing = stringResource(R.string.minimal_unhide_account), showChevron = false,
                    enabled = !state.isSaving && !state.isUpdatingHidden,
                    onClick = { viewModel.setHidden(false) })
            }
        }
        if (state.isClosed) {
            item {
                MoneyCard {
                    Text(
                        text = stringResource(R.string.account_closed_readonly_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            item { MoneySectionHeader(title = stringResource(R.string.account_close_section)) }
            item {
                MoneyListSection {
                    MoneyListRow(
                        title = stringResource(R.string.account_close_title),
                        subtitle = if (state.canClose) {
                            stringResource(R.string.account_close_available_description)
                        } else {
                            stringResource(R.string.account_close_nonzero_description)
                        },
                        showChevron = false,
                        isClickable = state.canClose,
                        enabled = state.canClose,
                        onClick = {
                            dialog = EditAccountDialog.CloseConfirm
                        },
                    )
                }
            }
        }
    }
}
