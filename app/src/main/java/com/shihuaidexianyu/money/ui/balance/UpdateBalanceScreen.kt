package com.shihuaidexianyu.money.ui.balance

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.ui.common.AccountPickerDialog
import com.shihuaidexianyu.money.ui.common.MoneyCard
import com.shihuaidexianyu.money.ui.common.MoneyInlineLabelValue
import com.shihuaidexianyu.money.ui.common.MoneyPageTitle
import com.shihuaidexianyu.money.ui.common.MoneySelectionField
import com.shihuaidexianyu.money.util.AmountFormatter

@Composable
fun UpdateBalanceScreen(
    viewModel: UpdateBalanceViewModel,
    settings: AppSettings,
    onShowResult: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAccountPicker by remember { mutableStateOf(false) }
    val selectedAccount = state.accounts.firstOrNull { it.id == state.selectedAccountId }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { onShowResult() }
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

    Column(modifier = modifier) {
        SnackbarHost(hostState = snackbarHostState)
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item { MoneyPageTitle(title = "更新余额") }
            item {
                MoneyCard {
                    MoneySelectionField(
                        label = "账户",
                        value = selectedAccount?.name ?: "请选择",
                        modifier = Modifier.clickable { showAccountPicker = true },
                    )
                    MoneyInlineLabelValue(
                        label = "系统余额",
                        value = AmountFormatter.format(state.systemBalanceBeforeUpdate, settings),
                    )
                }
            }
            item {
                MoneyCard {
                    OutlinedTextField(
                        value = state.actualBalanceText,
                        onValueChange = viewModel::updateActualBalance,
                        label = { Text("实际余额") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.displayMedium,
                    )
                    OutlinedTextField(
                        value = state.occurredAtText,
                        onValueChange = viewModel::updateOccurredAt,
                        label = { Text("时间") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
            }
            item {
                MoneyCard {
                    MoneyInlineLabelValue(
                        label = "系统余额",
                        value = AmountFormatter.format(state.systemBalanceBeforeUpdate, settings),
                    )
                    MoneyInlineLabelValue(
                        label = "实际余额",
                        value = state.actualBalancePreview?.let { AmountFormatter.format(it, settings) } ?: "-",
                    )
                    MoneyInlineLabelValue(
                        label = "差额",
                        value = state.deltaPreview?.let { AmountFormatter.format(it, settings) } ?: "-",
                    )
                    state.deltaPreview?.let {
                        Text(
                            text = when {
                                it > 0 -> "高于系统记录"
                                it < 0 -> "低于系统记录"
                                else -> "与系统记录一致"
                            },
                            color = when {
                                it > 0 -> Color(0xFFC24A4A)
                                it < 0 -> Color(0xFF3F8A63)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Button(
                        onClick = viewModel::save,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isSaving,
                    ) {
                        Text(if (state.isSaving) "保存中..." else "确认更新余额")
                    }
                }
            }
        }
    }
}
