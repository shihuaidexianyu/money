package com.shihuaidexianyu.money.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.domain.model.AccountGroupType
import com.shihuaidexianyu.money.domain.usecase.AccountShare
import com.shihuaidexianyu.money.domain.usecase.AssetGroupShare
import com.shihuaidexianyu.money.util.AmountFormatter
import kotlin.math.abs

private fun groupColor(groupType: AccountGroupType): Color {
    return when (groupType) {
        AccountGroupType.PAYMENT -> Color(0xFF3F8CFF)
        AccountGroupType.BANK -> Color(0xFF20A464)
        AccountGroupType.INVESTMENT -> Color(0xFFF28C28)
    }
}

@Composable
fun AccountShareChart(
    groupShares: List<AssetGroupShare>,
    topAccounts: List<AccountShare>,
    settings: AppSettings,
    modifier: Modifier = Modifier,
) {
    if (groupShares.isEmpty()) {
        Text(
            text = "暂无资产分布数据",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    val totalBalance = groupShares.sumOf { abs(it.balance) }
    if (totalBalance == 0L) {
        Text(
            text = "暂无资产分布数据",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    var selectedGroup by rememberSaveable { mutableStateOf<AccountGroupType?>(null) }
    val selectedGroupShare = groupShares.firstOrNull { it.groupType == selectedGroup }
    val filteredAccounts = topAccounts.filter { selectedGroup == null || it.groupType == selectedGroup }
    val displayedAccounts = if (selectedGroup == null) filteredAccounts.take(5) else filteredAccounts
    val centerBalance = selectedGroupShare?.balance ?: groupShares.sumOf { it.balance }
    val centerLabel = selectedGroupShare?.groupType?.displayName ?: "当前净资产"

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(188.dp)) {
                val strokeWidth = 34.dp.toPx()
                val diameter = size.minDimension - strokeWidth
                val topLeft = Offset(
                    (size.width - diameter) / 2f,
                    (size.height - diameter) / 2f,
                )
                val arcSize = Size(diameter, diameter)

                var startAngle = -90f
                groupShares.forEach { share ->
                    val sweep = (abs(share.balance).toFloat() / totalBalance.toFloat()) * 360f
                    drawArc(
                        color = groupColor(share.groupType),
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth),
                    )
                    startAngle += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = centerLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = AmountFormatter.format(centerBalance, settings),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = selectedGroup == null,
                onClick = { selectedGroup = null },
                label = { Text("全部") },
            )
            groupShares.forEach { share ->
                FilterChip(
                    selected = selectedGroup == share.groupType,
                    onClick = {
                        selectedGroup = if (selectedGroup == share.groupType) null else share.groupType
                    },
                    label = { Text(share.groupType.displayName) },
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            groupShares.forEach { share ->
                val isSelected = selectedGroup == share.groupType
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = if (isSelected) {
                        groupColor(share.groupType).copy(alpha = 0.08f)
                    } else {
                        Color.Transparent
                    },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedGroup = if (isSelected) null else share.groupType
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Canvas(modifier = Modifier.size(12.dp)) {
                                drawCircle(color = groupColor(share.groupType))
                            }
                            Column {
                                Text(
                                    text = share.groupType.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isSelected) groupColor(share.groupType) else MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "${share.accountCount} 个账户",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = AmountFormatter.format(share.balance, settings),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isSelected) groupColor(share.groupType) else MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "${"%.1f".format(abs(share.balance).toDouble() * 100 / totalBalance.toDouble())}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        if (displayedAccounts.isNotEmpty()) {
            Text(
                text = if (selectedGroup == null) "账户排行" else "${selectedGroup?.displayName}账户明细",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = if (selectedGroup == null) {
                    "展示当前资产规模最大的 5 个账户。"
                } else {
                    "已切换到分组下钻，显示该分组的全部非零账户。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                displayedAccounts.forEachIndexed { index, share ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = "${index + 1}. ${share.accountName}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = share.groupType.displayName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = AmountFormatter.format(share.balance, settings),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}
