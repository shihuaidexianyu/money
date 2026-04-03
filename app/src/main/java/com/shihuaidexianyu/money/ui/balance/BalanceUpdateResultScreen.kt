package com.shihuaidexianyu.money.ui.balance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.domain.usecase.UpdateBalanceResult
import com.shihuaidexianyu.money.util.AmountFormatter

@Composable
fun BalanceUpdateResultScreen(
    result: UpdateBalanceResult,
    settings: AppSettings,
    onDone: () -> Unit,
    onOpenAccount: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("更新结果", style = MaterialTheme.typography.headlineSmall)
                    Text(result.accountName, style = MaterialTheme.typography.titleMedium)
                    Text("更新前系统余额：${AmountFormatter.format(result.systemBalanceBeforeUpdate, settings)}")
                    Text("本次确认余额：${AmountFormatter.format(result.actualBalance, settings)}")
                    Text("差额：${AmountFormatter.format(result.delta, settings)}")
                    Text(if (result.adjustmentCreated) "已生成余额矫正记录" else "本次无需生成余额矫正")
                }
            }
        }
        result.settlementSummary?.let { settlement ->
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("投资账户结算", style = MaterialTheme.typography.titleMedium)
                        Text("上次结算余额：${AmountFormatter.format(settlement.previousBalance, settings)}")
                        Text("本次结算余额：${AmountFormatter.format(settlement.currentBalance, settings)}")
                        Text("本周期净转入：${AmountFormatter.format(settlement.netTransferIn, settings)}")
                        Text("本周期净转出：${AmountFormatter.format(settlement.netTransferOut, settings)}")
                        Text("本周期盈亏：${AmountFormatter.format(settlement.pnl, settings)}")
                        Text("本周期收益率：${"%.2f".format(settlement.returnRate * 100)}%")
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("完成") }
                OutlinedButton(
                    onClick = { onOpenAccount(result.accountId) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("查看账户详情") }
            }
        }
    }
}
