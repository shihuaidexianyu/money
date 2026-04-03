package com.shihuaidexianyu.money.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shihuaidexianyu.money.ui.common.MoneyCard
import com.shihuaidexianyu.money.ui.common.MoneyEmptyStateCard
import com.shihuaidexianyu.money.ui.common.MoneyListRow
import com.shihuaidexianyu.money.ui.common.MoneyListSection
import com.shihuaidexianyu.money.ui.common.MoneyPageTitle
import com.shihuaidexianyu.money.ui.common.MoneySectionDivider

@Composable
fun ReorderAccountsScreen(
    viewModel: ReorderAccountsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            if (event == ReorderAccountsEvent.Saved) onBack()
        }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            MoneyPageTitle(title = "调整顺序")
        }
        item {
            SnackbarHost(hostState = snackbarHostState)
        }
        if (!state.isManualSortMode) {
            item {
                MoneyEmptyStateCard(
                    title = "当前不是手动排序",
                    subtitle = "先到设置里把账户排序切到手动排序。",
                )
            }
        } else if (state.accounts.isEmpty()) {
            item {
                MoneyEmptyStateCard(
                    title = "还没有账户",
                    subtitle = "创建账户后才能调整顺序。",
                )
            }
        } else {
            item {
                MoneyListSection {
                    state.accounts.forEachIndexed { index, account ->
                        MoneyListRow(
                            title = account.name,
                            subtitle = account.groupType.displayName,
                            showChevron = false,
                            accessory = {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { viewModel.moveUp(account.id) },
                                        enabled = index > 0,
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    ) {
                                        Text("上移")
                                    }
                                    OutlinedButton(
                                        onClick = { viewModel.moveDown(account.id) },
                                        enabled = index < state.accounts.lastIndex,
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    ) {
                                        Text("下移")
                                    }
                                }
                            },
                        )
                        if (index != state.accounts.lastIndex) {
                            MoneySectionDivider()
                        }
                    }
                }
            }
            item {
                MoneyCard {
                    Button(
                        onClick = viewModel::save,
                        enabled = !state.isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (state.isSaving) "保存中..." else "保存顺序")
                    }
                }
            }
        }
    }
}
