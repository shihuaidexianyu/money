package com.shihuaidexianyu.money.ui.accounts

import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shihuaidexianyu.money.domain.model.AccountGroupType
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderWeekday
import com.shihuaidexianyu.money.ui.common.MoneyCard
import com.shihuaidexianyu.money.ui.common.MoneyListRow
import com.shihuaidexianyu.money.ui.common.MoneyListSection
import com.shihuaidexianyu.money.ui.common.MoneyPageTitle
import com.shihuaidexianyu.money.ui.common.MoneySectionDivider
import com.shihuaidexianyu.money.ui.common.MoneySectionHeader

@Composable
fun EditAccountScreen(
    viewModel: EditAccountViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showArchiveConfirm by remember { mutableStateOf(false) }
    var showNameDialog by remember { mutableStateOf(false) }
    var showTypeDialog by remember { mutableStateOf(false) }
    var showReminderDialog by remember { mutableStateOf(false) }
    var nameDraft by remember(state.name) { mutableStateOf(state.name) }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            if (event == EditAccountEvent.Saved || event == EditAccountEvent.Archived) onBack()
        }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    if (showNameDialog) {
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text("账户名称") },
            text = {
                OutlinedTextField(
                    value = nameDraft,
                    onValueChange = { nameDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateName(nameDraft)
                        showNameDialog = false
                    },
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) {
                    Text("取消")
                }
            },
        )
    }

    if (showTypeDialog) {
        AlertDialog(
            onDismissRequest = { showTypeDialog = false },
            title = { Text("账户类型") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AccountGroupType.entries.forEach { group ->
                        TextButton(
                            onClick = {
                                viewModel.updateGroupType(group)
                                showTypeDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(text = group.displayName, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showTypeDialog = false }) {
                    Text("关闭")
                }
            },
        )
    }

    if (showReminderDialog) {
        AlertDialog(
            onDismissRequest = { showReminderDialog = false },
            title = { Text("每周提醒日") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BalanceUpdateReminderWeekday.entries.forEach { weekday ->
                        TextButton(
                            onClick = {
                                viewModel.updateReminderWeekday(weekday)
                                showReminderDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(text = weekday.displayName, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showReminderDialog = false }) {
                    Text("关闭")
                }
            },
        )
    }

    if (showArchiveConfirm) {
        AlertDialog(
            onDismissRequest = { showArchiveConfirm = false },
            title = { Text("归档账户") },
            text = { Text("归档后，这个账户会移到已归档列表。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showArchiveConfirm = false
                        viewModel.archive()
                    },
                ) {
                    Text("确认归档")
                }
            },
            dismissButton = {
                TextButton(onClick = { showArchiveConfirm = false }) {
                    Text("取消")
                }
            },
        )
    }

    Column(modifier = modifier) {
        SnackbarHost(hostState = snackbarHostState)
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item { MoneyPageTitle(title = "账户管理") }
            item { MoneySectionHeader(title = "账户信息") }
            item {
                MoneyListSection {
                    MoneyListRow(
                        title = "账户名称",
                        trailing = state.name,
                        modifier = Modifier.clickable {
                            nameDraft = state.name
                            showNameDialog = true
                        },
                    )
                    MoneySectionDivider()
                    MoneyListRow(
                        title = "账户类型",
                        trailing = state.groupType.displayName,
                        modifier = Modifier.clickable { showTypeDialog = true },
                    )
                    MoneySectionDivider()
                    MoneyListRow(
                        title = "每周提醒日",
                        subtitle = "到了提醒时间后未更新会标记为待更新",
                        trailing = state.reminderConfig.weekday.displayName,
                        modifier = Modifier.clickable { showReminderDialog = true },
                    )
                    MoneySectionDivider()
                    MoneyListRow(
                        title = "提醒时间",
                        trailing = state.reminderConfig.timeText,
                        modifier = Modifier.clickable {
                            TimePickerDialog(
                                context,
                                { _, hour, minute -> viewModel.updateReminderTime(hour, minute) },
                                state.reminderConfig.hour,
                                state.reminderConfig.minute,
                                true,
                            ).show()
                        },
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
                        Text(if (state.isSaving) "保存中..." else "保存")
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
                        modifier = Modifier.clickable { showArchiveConfirm = true },
                    )
                }
            }
        }
    }
}
