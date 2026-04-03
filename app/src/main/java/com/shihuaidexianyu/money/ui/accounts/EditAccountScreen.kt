package com.shihuaidexianyu.money.ui.accounts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
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
import com.shihuaidexianyu.money.ui.common.MoneyCard
import com.shihuaidexianyu.money.ui.common.MoneyChoiceDialog
import com.shihuaidexianyu.money.ui.common.MoneyConfirmDialog
import com.shihuaidexianyu.money.ui.common.MoneyFormPage
import com.shihuaidexianyu.money.ui.common.MoneyListRow
import com.shihuaidexianyu.money.ui.common.MoneyListSection
import com.shihuaidexianyu.money.ui.common.MoneySectionDivider
import com.shihuaidexianyu.money.ui.common.MoneySectionHeader
import com.shihuaidexianyu.money.ui.common.MoneyTextInputDialog
import com.shihuaidexianyu.money.ui.common.MoneyTimePickerDialogHost

private sealed interface EditAccountDialog {
    data object Name : EditAccountDialog
    data object AccountType : EditAccountDialog
    data object ReminderWeekday : EditAccountDialog
    data object ReminderTime : EditAccountDialog
    data object ArchiveConfirm : EditAccountDialog
}

@Composable
fun EditAccountScreen(
    viewModel: EditAccountViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var dialog by remember { mutableStateOf<EditAccountDialog?>(null) }
    var nameDraft by remember(state.name) { mutableStateOf(state.name) }

    LaunchedEffect(viewModel) {
        viewModel.effectFlow.collect { effect ->
            when (effect) {
                EditAccountEffect.Saved,
                EditAccountEffect.Archived,
                -> onBack()

                is EditAccountEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    dialog?.let { currentDialog ->
        when (currentDialog) {
            EditAccountDialog.Name -> {
                MoneyTextInputDialog(
                    title = "账户名称",
                    value = nameDraft,
                    onValueChange = { nameDraft = it },
                    onConfirm = {
                        viewModel.updateName(nameDraft)
                        dialog = null
                    },
                    onDismiss = { dialog = null },
                )
            }

            EditAccountDialog.AccountType -> {
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

            EditAccountDialog.ReminderWeekday -> {
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

            EditAccountDialog.ArchiveConfirm -> {
                MoneyConfirmDialog(
                    title = "归档账户",
                    message = "归档后，这个账户会移到已归档列表。",
                    onConfirm = {
                        dialog = null
                        viewModel.archive()
                    },
                    onDismiss = { dialog = null },
                    confirmLabel = "确认归档",
                )
            }

            EditAccountDialog.ReminderTime -> {
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
        title = "账户管理",
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) {
        item { MoneySectionHeader(title = "账户信息") }
        item {
            MoneyListSection {
                MoneyListRow(
                    title = "账户名称",
                    trailing = state.name,
                    modifier = Modifier.clickable {
                        nameDraft = state.name
                        dialog = EditAccountDialog.Name
                    },
                )
                MoneySectionDivider()
                MoneyListRow(
                    title = "账户类型",
                    trailing = state.groupType.displayName,
                    modifier = Modifier.clickable { dialog = EditAccountDialog.AccountType },
                )
                MoneySectionDivider()
                MoneyListRow(
                    title = "每周提醒日",
                    subtitle = "到了提醒时间后未更新会标记为待更新",
                    trailing = state.reminderConfig.weekday.displayName,
                    modifier = Modifier.clickable { dialog = EditAccountDialog.ReminderWeekday },
                )
                MoneySectionDivider()
                MoneyListRow(
                    title = "提醒时间",
                    trailing = state.reminderConfig.timeText,
                    modifier = Modifier.clickable { dialog = EditAccountDialog.ReminderTime },
                )
            }
        }
        item {
            MoneyCard {
                Button(
                    onClick = viewModel::save,
                    enabled = !state.isSaving && !state.isLoading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    androidx.compose.material3.Text(if (state.isSaving) "保存中..." else "保存")
                }
            }
        }
        item { MoneySectionHeader(title = "归档") }
        item {
            MoneyListSection {
                MoneyListRow(
                    title = "归档账户",
                    subtitle = "移到已归档列表",
                    showChevron = false,
                    modifier = Modifier.clickable { dialog = EditAccountDialog.ArchiveConfirm },
                )
            }
        }
    }
}

