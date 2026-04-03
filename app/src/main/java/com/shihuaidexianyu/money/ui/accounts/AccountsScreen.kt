package com.shihuaidexianyu.money.ui.accounts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.domain.model.AccountGroupType
import com.shihuaidexianyu.money.domain.model.AccountSortMode
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.ui.common.MoneyCard
import com.shihuaidexianyu.money.ui.common.MoneyEmptyStateCard
import com.shihuaidexianyu.money.ui.common.MoneyListRow
import com.shihuaidexianyu.money.ui.common.MoneyPageTitle
import com.shihuaidexianyu.money.ui.common.MoneySectionDivider
import com.shihuaidexianyu.money.ui.common.MoneySectionHeader
import com.shihuaidexianyu.money.ui.common.MoneyStatusPill
import com.shihuaidexianyu.money.util.AmountFormatter

@Composable
fun AccountsScreen(
    state: AccountsUiState,
    onCreateAccount: () -> Unit,
    onAccountClick: (Long) -> Unit,
    onReorderAccounts: () -> Unit,
    onToggleArchiveVisibility: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val groupedActiveAccounts = state.activeAccounts.groupBy { it.groupType }
    val groupedArchivedAccounts = state.archivedAccounts.groupBy { it.groupType }
    val hasArchivedAccounts = state.archivedAccounts.isNotEmpty()
    val showReorderEntry = state.settings.accountSortMode == AccountSortMode.MANUAL && state.activeAccounts.size > 1

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            MoneyPageTitle(
                title = "账户",
                trailing = {
                    OutlinedButton(onClick = onCreateAccount) {
                        androidx.compose.material3.Icon(Icons.Outlined.Add, contentDescription = null)
                        Text("新建账户", modifier = Modifier.padding(start = 6.dp))
                    }
                },
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                MoneyStatusPill(text = "活跃 ${state.activeAccounts.size}")
                if (hasArchivedAccounts) {
                    Text(
                        text = "已归档 ${state.archivedAccounts.size}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (showReorderEntry) {
            item {
                MoneyCard(contentPadding = PaddingValues(0.dp)) {
                    MoneyListRow(
                        title = "调整顺序",
                        subtitle = "当前按手动排序显示",
                        modifier = Modifier.clickable(onClick = onReorderAccounts),
                    )
                }
            }
        }
        if (state.activeAccounts.isEmpty()) {
            item {
                MoneyEmptyStateCard(
                    title = if (hasArchivedAccounts) "还没有活跃账户" else "还没有账户",
                    subtitle = if (hasArchivedAccounts) "归档账户可以在下方查看。" else "创建账户后，就能开始记录资金流和查看总资产。",
                ) {
                    if (hasArchivedAccounts) {
                        OutlinedButton(onClick = onToggleArchiveVisibility) {
                            Text(if (state.showArchived) "收起已归档" else "查看已归档")
                        }
                    } else {
                        OutlinedButton(onClick = onCreateAccount) { Text("创建第一个账户") }
                    }
                }
            }
        } else {
            groupedActiveAccounts.forEach { (groupType, accounts) ->
                item {
                    MoneySectionHeader(title = groupType.displayName)
                }
                item {
                    MoneyCard(contentPadding = PaddingValues(0.dp)) {
                        accounts.forEachIndexed { index, account ->
                            AccountRow(
                                account = account,
                                currencySettings = state.settings,
                                onClick = { onAccountClick(account.id) },
                            )
                            if (index != accounts.lastIndex) {
                                MoneySectionDivider()
                            }
                        }
                    }
                }
            }
        }
        if (hasArchivedAccounts) {
            item {
                MoneyCard(contentPadding = PaddingValues(0.dp)) {
                    MoneyListRow(
                        title = "已归档账户",
                        subtitle = if (state.showArchived) "已展开" else "默认收起",
                        trailing = "${state.archivedAccounts.size} 个",
                        showChevron = false,
                        accessory = {
                            Text(
                                text = if (state.showArchived) "收起" else "查看",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        },
                        modifier = Modifier.clickable(onClick = onToggleArchiveVisibility),
                    )
                }
            }
        }
        if (state.showArchived) {
            groupedArchivedAccounts.forEach { (groupType, accounts) ->
                item {
                    MoneySectionHeader(title = "${groupType.displayName} · 已归档")
                }
                item {
                    MoneyCard(contentPadding = PaddingValues(0.dp)) {
                        accounts.forEachIndexed { index, account ->
                            AccountRow(
                                account = account,
                                currencySettings = state.settings,
                                onClick = { onAccountClick(account.id) },
                            )
                            if (index != accounts.lastIndex) {
                                MoneySectionDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountRow(
    account: AccountListItemUiModel,
    currencySettings: AppSettings,
    onClick: () -> Unit,
) {
    val status = when {
        account.groupType == AccountGroupType.INVESTMENT && account.isStale -> "已过期"
        account.isStale -> "待更新"
        account.isArchived -> "已归档"
        account.lastUsedAt != null -> "最近有记录"
        else -> "尚无记录"
    }

    MoneyListRow(
        title = account.name,
        subtitle = "$status · ${account.groupType.displayName}",
        trailing = AmountFormatter.format(account.balance, currencySettings),
        modifier = Modifier.clickable(onClick = onClick),
    )
}
