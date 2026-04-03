package com.shihuaidexianyu.money.ui.record

import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shihuaidexianyu.money.ui.common.AccountPickerDialog
import com.shihuaidexianyu.money.ui.common.MoneyCard
import com.shihuaidexianyu.money.ui.common.MoneyPageTitle
import com.shihuaidexianyu.money.ui.common.MoneySelectionField
import com.shihuaidexianyu.money.util.DateTimeTextFormatter

private enum class CashFlowDateTimeField {
    DATE,
    TIME,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordCashFlowScreen(
    viewModel: RecordCashFlowViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showAccountPicker by remember { mutableStateOf(false) }
    var dateTimeField by remember { mutableStateOf<CashFlowDateTimeField?>(null) }
    val selectedAccount = state.accounts.firstOrNull { it.id == state.selectedAccountId }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { onBack() }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    if (showAccountPicker) {
        AccountPickerDialog(
            title = "选择账户",
            accounts = state.accounts,
            selectedAccountId = state.selectedAccountId,
            onDismiss = { showAccountPicker = false },
            onPick = {
                viewModel.updateAccount(it)
                showAccountPicker = false
            },
        )
    }

    dateTimeField?.let { currentField ->
        when (currentField) {
            CashFlowDateTimeField.DATE -> {
                val pickerState = androidx.compose.material3.rememberDatePickerState(
                    initialSelectedDateMillis = state.occurredAtMillis,
                )
                DatePickerDialog(
                    onDismissRequest = { dateTimeField = null },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                pickerState.selectedDateMillis?.let { selectedDate ->
                                    viewModel.updateOccurredAt(
                                        DateTimeTextFormatter.replaceDate(
                                            baseTimeMillis = state.occurredAtMillis,
                                            selectedDateMillis = selectedDate,
                                        ),
                                    )
                                }
                                dateTimeField = null
                            },
                        ) {
                            Text("确定")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { dateTimeField = null }) {
                            Text("取消")
                        }
                    },
                ) {
                    DatePicker(state = pickerState)
                }
            }
            CashFlowDateTimeField.TIME -> {
                val zoneDateTime = java.time.Instant.ofEpochMilli(state.occurredAtMillis)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDateTime()
                LaunchedEffect(state.occurredAtMillis) {
                    TimePickerDialog(
                        context,
                        { _, hour, minute ->
                            viewModel.updateOccurredAt(
                                DateTimeTextFormatter.replaceTime(
                                    baseTimeMillis = state.occurredAtMillis,
                                    hour = hour,
                                    minute = minute,
                                ),
                            )
                            dateTimeField = null
                        },
                        zoneDateTime.hour,
                        zoneDateTime.minute,
                        true,
                    ).apply {
                        setOnDismissListener { dateTimeField = null }
                    }.show()
                }
            }
        }
    }

    if (state.showPurposeConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissPurposeConfirm,
            title = { Text("未填写用途") },
            text = { Text("仍要保存吗？") },
            confirmButton = {
                TextButton(onClick = { viewModel.save(confirmBlankPurpose = true) }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissPurposeConfirm) { Text("返回") }
            },
        )
    }

    Column(modifier = modifier) {
        SnackbarHost(hostState = snackbarHostState)
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                MoneyPageTitle(title = state.direction.displayName)
            }
            item {
                MoneyCard {
                    MoneySelectionField(
                        label = "账户",
                        value = selectedAccount?.name ?: "请选择",
                        modifier = Modifier.clickable { showAccountPicker = true },
                    )
                }
            }
            item {
                MoneyCard {
                    OutlinedTextField(
                        value = state.amountText,
                        onValueChange = viewModel::updateAmount,
                        label = { Text("金额") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.displayMedium,
                    )
                }
            }
            item {
                MoneyCard {
                    OutlinedTextField(
                        value = state.purpose,
                        onValueChange = viewModel::updatePurpose,
                        label = { Text("用途") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    MoneySelectionField(
                        label = "日期",
                        value = DateTimeTextFormatter.formatDateOnly(state.occurredAtMillis),
                        modifier = Modifier.clickable { dateTimeField = CashFlowDateTimeField.DATE },
                    )
                    MoneySelectionField(
                        label = "时间",
                        value = DateTimeTextFormatter.formatTimeOnly(state.occurredAtMillis),
                        subtitle = "默认当前时间",
                        modifier = Modifier.clickable { dateTimeField = CashFlowDateTimeField.TIME },
                    )
                    Button(
                        onClick = { viewModel.save() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isSaving,
                    ) {
                        Text(if (state.isSaving) "保存中..." else "保存")
                    }
                }
            }
        }
    }
}
