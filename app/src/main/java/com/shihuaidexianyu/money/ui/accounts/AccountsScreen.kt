package com.shihuaidexianyu.money.ui.accounts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.domain.model.AccountGroupType
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.ui.common.MoneyCard
import com.shihuaidexianyu.money.ui.common.MoneyEmptyStateCard
import com.shihuaidexianyu.money.ui.common.MoneyListRow
import com.shihuaidexianyu.money.ui.common.MoneyPageTitle
import com.shihuaidexianyu.money.ui.common.MoneySectionDivider
import com.shihuaidexianyu.money.ui.common.MoneySectionHeader
import com.shihuaidexianyu.money.util.AmountFormatter

@Composable
fun AccountsScreen(
    state: AccountsUiState,
    onCreateAccount: () -> Unit,
    onAccountClick: (Long) -> Unit,
    onToggleArchiveVisibility: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val groupedActiveAccounts = state.activeAccounts.groupBy { it.groupType }
    val groupedArchivedAccounts = state.archivedAccounts.groupBy { it.groupType }
    val hasArchivedAccounts = state.archivedAccounts.isNotEmpty()

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
            state.settings.accountGroupOrder.forEach { groupType ->
                val accounts = groupedActiveAccounts[groupType].orEmpty()
                if (accounts.isEmpty()) return@forEach
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
            state.settings.accountGroupOrder.forEach { groupType ->
                val accounts = groupedArchivedAccounts[groupType].orEmpty()
                if (accounts.isEmpty()) return@forEach
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
    val statusColor = when {
        account.isArchived -> MaterialTheme.colorScheme.outline
        account.isStale -> Color(0xFFC24A4A)
        else -> Color(0xFF3F8A63)
    }
    val statusText = when {
        account.isArchived -> "已归档"
        account.isStale -> "待更新"
        else -> "已更新"
    }

    MoneyListRow(
        title = account.name,
        trailing = AmountFormatter.format(account.balance, currencySettings),
        accessory = {
            Row(
                modifier = Modifier.padding(start = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(color = statusColor, shape = CircleShape),
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor,
                )
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

