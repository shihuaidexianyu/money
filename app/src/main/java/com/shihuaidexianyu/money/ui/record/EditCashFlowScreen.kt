package com.shihuaidexianyu.money.ui.record

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun EditCashFlowScreen(
    viewModel: EditCashFlowViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { onBack() }
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    if (state.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteConfirm,
            title = { Text("删除记录") },
            text = { Text("删除后将重新计算相关账户余额与后续统计，确认删除？") },
            confirmButton = { TextButton(onClick = viewModel::delete) { Text("确认删除") } },
            dismissButton = { TextButton(onClick = viewModel::dismissDeleteConfirm) { Text("取消") } },
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        SnackbarHost(hostState = snackbarHostState)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("编辑${state.direction.displayName}", style = MaterialTheme.typography.headlineSmall)
                        Text("此修改会影响当前余额与后续统计", style = MaterialTheme.typography.bodyMedium)
                        Text("账户", style = MaterialTheme.typography.titleMedium)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.accounts.forEach { account ->
                                FilterChip(
                                    selected = state.selectedAccountId == account.id,
                                    onClick = { viewModel.updateAccount(account.id) },
                                    label = { Text(account.name) },
                                )
                            }
                        }
                        OutlinedTextField(state.amountText, viewModel::updateAmount, label = { Text("金额") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(state.purpose, viewModel::updatePurpose, label = { Text("用途") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(state.occurredAtText, viewModel::updateOccurredAt, label = { Text("时间") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth(), enabled = !state.isSaving) {
                            Text(if (state.isSaving) "保存中..." else "保存修改")
                        }
                        OutlinedButton(onClick = viewModel::showDeleteConfirm, modifier = Modifier.fillMaxWidth()) {
                            Text("删除记录")
                        }
                    }
                }
            }
        }
    }
}
