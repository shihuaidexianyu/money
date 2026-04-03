package com.shihuaidexianyu.money.ui.accounts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shihuaidexianyu.money.domain.model.AccountGroupType
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderWeekday
import com.shihuaidexianyu.money.ui.common.MoneyAmountField
import com.shihuaidexianyu.money.ui.common.MoneyCard
import com.shihuaidexianyu.money.ui.common.MoneyChoiceDialog
import com.shihuaidexianyu.money.ui.common.MoneyFormPage
import com.shihuaidexianyu.money.ui.common.MoneySelectionField
import com.shihuaidexianyu.money.ui.common.MoneyTimePickerDialogHost

private sealed interface CreateAccountDialog {
    data object AccountType : CreateAccountDialog
    data object ReminderWeekday : CreateAccountDialog
    data object ReminderTime : CreateAccountDialog
}

@Composable
fun CreateAccountScreen(
    viewModel: CreateAccountViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var dialog by remember { mutableStateOf<CreateAccountDialog?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.effectFlow.collect { effect ->
            when (effect) {
                CreateAccountEffect.Saved -> onBack()
                is CreateAccountEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    dialog?.let { currentDialog ->
        when (currentDialog) {
            CreateAccountDialog.AccountType -> {
                MoneyChoiceDialog(
                    title = "账户类型",
                    options = AccountGroupType.entries,
                    selected = state.groupType,
                    label = { it.displayName },
                    onSelect = {
                        viewModel.updateGroupType(it)
                        dialog = null
                    },
                    onDismiss = { dialog = null },
                )
            }

            CreateAccountDialog.ReminderWeekday -> {
                MoneyChoiceDialog(
                    title = "每周提醒日",
                    options = BalanceUpdateReminderWeekday.entries,
                    selected = state.reminderConfig.weekday,
                    label = { it.displayName },
                    onSelect = {
                        viewModel.updateReminderWeekday(it)
                        dialog = null
                    },
                    onDismiss = { dialog = null },
                )
            }

            CreateAccountDialog.ReminderTime -> {
                val initialTimeMillis = java.time.LocalDate.now()
                    .atTime(state.reminderConfig.hour, state.reminderConfig.minute)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
                MoneyTimePickerDialogHost(
                    initialTimeMillis = initialTimeMillis,
                    onDismiss = { dialog = null },
                    onConfirm = { hour, minute ->
                        viewModel.updateReminderTime(hour, minute)
                        dialog = null
                    },
                )
            }
        }
    }

    MoneyFormPage(
        title = "新建账户",
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) {
        item {
            MoneyCard {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::updateName,
                    label = { androidx.compose.material3.Text("账户名称") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                MoneyAmountField(
                    value = state.amountText,
                    onValueChange = viewModel::updateAmountText,
                    label = "当前余额",
                )
                MoneySelectionField(
                    label = "账户类型",
                    value = state.groupType.displayName,
                    modifier = Modifier.clickable { dialog = CreateAccountDialog.AccountType },
                )
                MoneySelectionField(
                    label = "每周提醒日",
                    value = state.reminderConfig.weekday.displayName,
                    subtitle = "到了提醒时间后未更新会标记为待更新",
                    modifier = Modifier.clickable { dialog = CreateAccountDialog.ReminderWeekday },
                )
                MoneySelectionField(
                    label = "提醒时间",
                    value = state.reminderConfig.timeText,
                    modifier = Modifier.clickable { dialog = CreateAccountDialog.ReminderTime },
                )
                Button(
                    onClick = viewModel::save,
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    androidx.compose.material3.Text(if (state.isSaving) "保存中..." else "保存")
                }
            }
        }
    }
}

