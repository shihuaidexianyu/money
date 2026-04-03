package com.shihuaidexianyu.money.ui.balance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.util.AmountFormatter
import com.shihuaidexianyu.money.util.DateTimeTextFormatter

@Composable
fun BalanceUpdateDetailScreen(
    state: BalanceUpdateDetailUiState,
    settings: AppSettings,
    onBack: () -> Unit,
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
                    Text("更新余额详情", style = MaterialTheme.typography.headlineSmall)
                    Text(state.accountName, style = MaterialTheme.typography.titleMedium)
                    Text("时间：${DateTimeTextFormatter.format(state.occurredAt)}")
                    Text("更新前系统余额：${AmountFormatter.format(state.systemBalanceBeforeUpdate, settings)}")
                    Text("本次确认余额：${AmountFormatter.format(state.actualBalance, settings)}")
                    Text("差额：${AmountFormatter.format(state.delta, settings)}")
                }
            }
        }
        state.settlementSummary?.let { settlement ->
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("结算信息", style = MaterialTheme.typography.titleMedium)
                        Text("本周期盈亏：${AmountFormatter.format(settlement.pnl, settings)}")
                        Text("本周期收益率：${"%.2f".format(settlement.returnRate * 100)}%")
                        Text("本周期净转入：${AmountFormatter.format(settlement.netTransferIn, settings)}")
                        Text("本周期净转出：${AmountFormatter.format(settlement.netTransferOut, settings)}")
                    }
                }
            }
        }
        item {
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("返回") }
        }
    }
}

