package com.shihuaidexianyu.money.ui.record

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shihuaidexianyu.money.ui.common.AccountPickerDialog
import com.shihuaidexianyu.money.ui.common.MoneyCard
import com.shihuaidexianyu.money.ui.common.MoneyPageTitle
import com.shihuaidexianyu.money.ui.common.MoneySelectionField

private enum class TransferPickerTarget {
    FROM,
    TO,
}

@Composable
fun RecordTransferScreen(
    viewModel: RecordTransferViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var pickerTarget by remember { mutableStateOf<TransferPickerTarget?>(null) }
    val fromAccount = state.accounts.firstOrNull { it.id == state.fromAccountId }
    val toAccount = state.accounts.firstOrNull { it.id == state.toAccountId }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { onBack() }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    pickerTarget?.let { target ->
        AccountPickerDialog(
            title = if (target == TransferPickerTarget.FROM) "选择转出账户" else "选择转入账户",
            accounts = state.accounts,
            selectedAccountId = if (target == TransferPickerTarget.FROM) state.fromAccountId else state.toAccountId,
            onDismiss = { pickerTarget = null },
            onPick = {
                if (target == TransferPickerTarget.FROM) {
                    viewModel.updateFromAccount(it)
                } else {
                    viewModel.updateToAccount(it)
                }
                pickerTarget = null
            },
        )
    }

    Column(modifier = modifier) {
        SnackbarHost(hostState = snackbarHostState)
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item { MoneyPageTitle(title = "转账") }
            item {
                MoneyCard {
                    MoneySelectionField(
                        label = "转出账户",
                        value = fromAccount?.name ?: "请选择",
                        modifier = Modifier.clickable { pickerTarget = TransferPickerTarget.FROM },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = viewModel::swapAccounts) {
                            Icon(Icons.Outlined.SwapHoriz, contentDescription = null)
                            Text("互换", modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                    MoneySelectionField(
                        label = "转入账户",
                        value = toAccount?.name ?: "请选择",
                        modifier = Modifier.clickable { pickerTarget = TransferPickerTarget.TO },
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
                    OutlinedTextField(
                        value = state.note,
                        onValueChange = viewModel::updateNote,
                        label = { Text("备注") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = state.occurredAtText,
                        onValueChange = viewModel::updateOccurredAt,
                        label = { Text("时间") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Button(
                        onClick = viewModel::save,
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
