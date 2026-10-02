package com.shihuaidexianyu.money.ui.accounts

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.shihuaidexianyu.money.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shihuaidexianyu.money.ui.common.MoneyAmountField
import com.shihuaidexianyu.money.ui.common.MoneyCard
import com.shihuaidexianyu.money.ui.common.CollectUiEffects
import com.shihuaidexianyu.money.ui.common.MoneyFormPage
import com.shihuaidexianyu.money.ui.common.MoneySaveButton
import com.shihuaidexianyu.money.ui.common.MoneySingleLineField
import com.shihuaidexianyu.money.ui.common.rememberDirtyFormBackAction

@Composable
fun CreateAccountScreen(
    viewModel: CreateAccountViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val guardedBack = rememberDirtyFormBackAction(state.isDirty, onBack, isSaving = state.isSaving)

    CollectUiEffects(viewModel.effectFlow, snackbarHostState) { effect ->
        if (effect is CreateAccountEffect.Saved) onBack()
    }

    MoneyFormPage(
        title = stringResource(R.string.account_create_title),
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        onBack = guardedBack,
        footer = {
            MoneySaveButton(onClick = viewModel::save, isSaving = state.isSaving)
        },
    ) {
        item {
            MoneyCard {
                MoneySingleLineField(
                    value = state.name,
                    onValueChange = viewModel::updateName,
                    label = stringResource(R.string.account_name),
                    enabled = !state.isSaving,
                    isError = state.nameError != null,
                    supportingText = state.nameError,
                )
                MoneyAmountField(
                    value = state.amountText,
                    onValueChange = viewModel::updateAmountText,
                    label = stringResource(R.string.account_current_balance),
                    allowSigned = true,
                    enabled = !state.isSaving,
                )
            }
        }
    }
}
