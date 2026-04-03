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
import com.shihuaidexianyu.money.ui.common.MoneyPageTitle
import com.shihuaidexianyu.money.ui.common.MoneySelectionField

@Composable
fun CreateAccountScreen(
    viewModel: CreateAccountViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showTypeDialog by remember { mutableStateOf(false) }
    var showReminderDialog by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            if (event == CreateAccountEvent.Saved) onBack()
        }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { snackbarHostState.showSnackbar(it) }
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

    Column(modifier = modifier) {
        SnackbarHost(hostState = snackbarHostState)
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item { MoneyPageTitle(title = "新建账户") }
            item {
                MoneyCard {
                    OutlinedTextField(
                        value = state.name,
                        onValueChange = viewModel::updateName,
                        label = { Text("账户名称") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = state.amountText,
                        onValueChange = viewModel::updateAmountText,
                        label = { Text("当前余额") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    MoneySelectionField(
                        label = "账户类型",
                        value = state.groupType.displayName,
                        modifier = Modifier.clickable { showTypeDialog = true },
                    )
                    MoneySelectionField(
                        label = "每周提醒日",
                        value = state.reminderConfig.weekday.displayName,
                        subtitle = "到了提醒时间后未更新会标记为待更新",
                        modifier = Modifier.clickable { showReminderDialog = true },
                    )
                    MoneySelectionField(
                        label = "提醒时间",
                        value = state.reminderConfig.timeText,
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
                    Button(
                        onClick = viewModel::save,
                        enabled = !state.isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (state.isSaving) "保存中..." else "保存")
                    }
                }
            }
        }
    }
}
