package com.shihuaidexianyu.money.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.SouthWest
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.ReminderType
import com.shihuaidexianyu.money.ui.common.AccountPickerDialog
import com.shihuaidexianyu.money.ui.common.MoneyListSection
import com.shihuaidexianyu.money.ui.common.MoneyPageTitle
import com.shihuaidexianyu.money.ui.common.MoneySectionDivider
import com.shihuaidexianyu.money.ui.common.MoneySectionHeader
import com.shihuaidexianyu.money.ui.common.MoneyStatusPill
import com.shihuaidexianyu.money.ui.theme.LocalMoneyColors
import com.shihuaidexianyu.money.util.AmountFormatter

@Composable
fun HomeScreen(
    state: HomeUiState,
    onStartCashFlow: (CashFlowDirection, Long) -> Unit,
    onStartTransfer: () -> Unit,
    onStartUpdateBalance: (Long) -> Unit,
    onReminderClick: (DueReminderUiModel) -> Unit,
    onAllRemindersClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pickerDirection by remember { mutableStateOf<CashFlowDirection?>(null) }
    var showUpdateBalancePicker by remember { mutableStateOf(false) }

    pickerDirection?.let { direction ->
        AccountPickerDialog(
            title = "选择${direction.displayName}账户",
            accounts = state.accountOptions,
            onDismiss = { pickerDirection = null },
            onPick = { accountId ->
                pickerDirection = null
                onStartCashFlow(direction, accountId)
            },
        )
    }

    if (showUpdateBalancePicker) {
        AccountPickerDialog(
            title = "选择更新余额账户",
            accounts = state.accountOptions,
            onDismiss = { showUpdateBalancePicker = false },
            onPick = { accountId ->
                showUpdateBalancePicker = false
                onStartUpdateBalance(accountId)
            },
        )
    }

    Column(modifier = modifier) {
        MoneyPageTitle(
            title = "首页",
            modifier = Modifier.padding(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 8.dp),
        )
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                val assetChangeAccent = when {
                    state.periodAssetChange > 0 -> LocalMoneyColors.current.income
                    state.periodAssetChange < 0 -> LocalMoneyColors.current.expense
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                TotalAssetsBlock(
                    totalAssets = AmountFormatter.format(state.totalAssets, state.settings),
                    assetChangeLabel = "${state.settings.homePeriod.displayName}资产变化",
                    assetChange = formatSignedAmount(state.periodAssetChange, state.settings),
                    assetChangeAccent = assetChangeAccent,
                    inflowLabel = "${state.settings.homePeriod.displayName}净流入",
                    inflowValue = AmountFormatter.format(state.periodNetInflow, state.settings),
                    outflowLabel = "${state.settings.homePeriod.displayName}净流出",
                    outflowValue = AmountFormatter.format(state.periodNetOutflow, state.settings),
                    accountCount = state.accountOptions.size,
                    staleCount = state.staleAccountCount,
                    showStaleMark = state.settings.showStaleMark,
                )
            }
            if (state.dueReminders.isNotEmpty()) {
                item {
                    MoneySectionHeader(
                        title = "待处理提醒",
                        trailingContent = {
                            Text(
                                text = "管理全部",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable(onClick = onAllRemindersClick),
                            )
                        },
                    )
                }
                item {
                    MoneyListSection {
                        state.dueReminders.forEachIndexed { index, reminder ->
                            ReminderRow(
                                reminder = reminder,
                                onClick = { onReminderClick(reminder) },
                            )
                            if (index != state.dueReminders.lastIndex) {
                                MoneySectionDivider()
                            }
                        }
                    }
                }
            }
            item {
                MoneySectionHeader(title = "快速记录")
            }
            item {
                ActionGrid(
                    onInflow = { pickerDirection = CashFlowDirection.INFLOW },
                    onOutflow = { pickerDirection = CashFlowDirection.OUTFLOW },
                    onTransfer = onStartTransfer,
                    onUpdateBalance = { showUpdateBalancePicker = true },
                    onReminders = onAllRemindersClick,
                    enabled = state.accountOptions.isNotEmpty(),
                    transferEnabled = state.accountOptions.size >= 2,
                )
            }
        }
    }
}

@Composable
private fun TotalAssetsBlock(
    totalAssets: String,
    assetChangeLabel: String,
    assetChange: String,
    assetChangeAccent: Color,
    inflowLabel: String,
    inflowValue: String,
    outflowLabel: String,
    outflowValue: String,
    accountCount: Int,
    staleCount: Int,
    showStaleMark: Boolean,
) {
    val borderColor = if (staleCount > 0 && showStaleMark) {
        MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.52f)
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp),
            ),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "总资产",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = totalAssets,
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val statusText = if (staleCount > 0 && showStaleMark) {
                    "$staleCount 个待更新"
                } else {
                    "$accountCount 个账户"
                }
                MoneyStatusPill(
                    text = statusText,
                    accent = if (staleCount > 0 && showStaleMark) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PeriodMetric(
                    label = assetChangeLabel,
                    value = assetChange,
                    accent = assetChangeAccent,
                    modifier = Modifier.weight(1f),
                )
                PeriodMetric(
                    label = inflowLabel,
                    value = inflowValue,
                    accent = LocalMoneyColors.current.income,
                    modifier = Modifier.weight(1f),
                )
                PeriodMetric(
                    label = outflowLabel,
                    value = outflowValue,
                    accent = LocalMoneyColors.current.expense,
                    modifier = Modifier.weight(1f),
                )
            }
            if (staleCount > 0 && showStaleMark) {
                Text(
                    text = "有账户余额需要确认，更新后总资产会更准确。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formatSignedAmount(amount: Long, settings: AppSettings): String {
    return when {
        amount > 0 -> "+${AmountFormatter.format(amount, settings)}"
        else -> AmountFormatter.format(amount, settings)
    }
}

@Composable
private fun PeriodMetric(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = accent.copy(alpha = 0.07f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ReminderRow(
    reminder: DueReminderUiModel,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = reminder.name,
                    modifier = Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                MoneyStatusPill(
                    text = when (reminder.type) {
                        ReminderType.MANUAL -> "待缴费"
                        ReminderType.SUBSCRIPTION -> "待确认"
                    },
                    accent = MaterialTheme.colorScheme.error,
                )
            }
            Text(
                text = "轻点处理",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = reminder.amountFormatted,
            modifier = Modifier.padding(start = 12.dp),
            style = MaterialTheme.typography.titleMedium,
            color = LocalMoneyColors.current.current,
            maxLines = 1,
        )
    }
}

@Composable
private fun ActionGrid(
    onInflow: () -> Unit,
    onOutflow: () -> Unit,
    onTransfer: () -> Unit,
    onUpdateBalance: () -> Unit,
    onReminders: () -> Unit,
    enabled: Boolean,
    transferEnabled: Boolean,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.52f),
                shape = RoundedCornerShape(12.dp),
            ),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ActionTile(
                label = "入账",
                icon = Icons.Rounded.SouthWest,
                tint = LocalMoneyColors.current.income,
                onClick = onInflow,
                enabled = enabled,
                modifier = Modifier.weight(1f),
            )
            ActionTile(
                label = "出账",
                icon = Icons.Rounded.NorthEast,
                tint = LocalMoneyColors.current.expense,
                onClick = onOutflow,
                enabled = enabled,
                modifier = Modifier.weight(1f),
            )
            ActionTile(
                label = "转账",
                icon = Icons.Rounded.SwapHoriz,
                tint = LocalMoneyColors.current.transfer,
                onClick = onTransfer,
                enabled = transferEnabled,
                modifier = Modifier.weight(1f),
            )
            ActionTile(
                label = "余额",
                icon = Icons.Rounded.Sync,
                tint = LocalMoneyColors.current.current,
                onClick = onUpdateBalance,
                enabled = enabled,
                modifier = Modifier.weight(1f),
            )
            ActionTile(
                label = "提醒",
                icon = Icons.Rounded.Notifications,
                tint = LocalMoneyColors.current.reminder,
                onClick = onReminders,
                enabled = true,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ActionTile(
    label: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(
                    color = tint.copy(alpha = if (enabled) 0.08f else 0.05f),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (enabled) tint else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (enabled) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}
