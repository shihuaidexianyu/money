package com.shihuaidexianyu.money.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.ui.common.MoneyCard
import com.shihuaidexianyu.money.ui.common.MoneyDatePickerDialogHost
import com.shihuaidexianyu.money.ui.common.MoneyEmptyStateCard
import com.shihuaidexianyu.money.ui.common.MoneyFormPage
import com.shihuaidexianyu.money.ui.common.MoneySelectionField
import com.shihuaidexianyu.money.ui.common.MoneySectionHeader
import com.shihuaidexianyu.money.util.AmountFormatter
import com.shihuaidexianyu.money.util.DateTimeTextFormatter
import com.shihuaidexianyu.money.util.TimeRangeUtils
import java.time.LocalDate
import java.time.ZoneId

private enum class HistoryFilterSheet {
    ACCOUNT,
    DATE,
    AMOUNT,
}

private enum class HistoryDateField {
    START,
    END,
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onKeywordChange: (String) -> Unit,
    onAccountChange: (Long?) -> Unit,
    onDateRangeChange: (Long?, Long?) -> Unit,
    onMinAmountChange: (String) -> Unit,
    onMaxAmountChange: (String) -> Unit,
    onRecordClick: (HistoryRecordUiModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sheet by remember { mutableStateOf<HistoryFilterSheet?>(null) }
    var dateField by remember { mutableStateOf<HistoryDateField?>(null) }

    dateField?.let { currentField ->
        val initialSelection = when (currentField) {
            HistoryDateField.START -> state.dateStartAt ?: state.dateEndAt
            HistoryDateField.END -> state.dateEndAt ?: state.dateStartAt
        }
        MoneyDatePickerDialogHost(
            initialSelectedDateMillis = initialSelection,
            onDismiss = { dateField = null },
            onConfirm = { selected ->
                when (currentField) {
                    HistoryDateField.START -> {
                        onDateRangeChange(
                            selected?.let { DateTimeTextFormatter.startOfDayMillis(it) },
                            state.dateEndAt,
                        )
                    }
                    HistoryDateField.END -> {
                        onDateRangeChange(
                            state.dateStartAt,
                            selected?.let { DateTimeTextFormatter.endOfDayMillis(it) },
                        )
                    }
                }
                dateField = null
            },
        )
    }

    sheet?.let { current ->
        ModalBottomSheet(onDismissRequest = { sheet = null }) {
            when (current) {
                HistoryFilterSheet.ACCOUNT -> {
                    HistoryFilterSheetContent(title = "账户") {
                        FilterChip(
                            selected = state.selectedAccountId == null,
                            onClick = { onAccountChange(null) },
                            label = { Text("全部账户") },
                        )
                        state.accountOptions.forEach { account ->
                            FilterChip(
                                selected = state.selectedAccountId == account.id,
                                onClick = { onAccountChange(account.id) },
                                label = { Text(account.name) },
                            )
                        }
                    }
                }
                HistoryFilterSheet.DATE -> {
                    HistoryFilterSheetContent(title = "日期") {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            QuickDateChip(
                                label = "今天",
                                onClick = {
                                    val todayStart = DateTimeTextFormatter.startOfDayMillis(System.currentTimeMillis())
                                    val todayEnd = DateTimeTextFormatter.endOfDayMillis(System.currentTimeMillis())
                                    onDateRangeChange(todayStart, todayEnd)
                                },
                            )
                            QuickDateChip(
                                label = "最近 7 天",
                                onClick = {
                                    val today = LocalDate.now()
                                    val start = today.minusDays(6).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                    val end = DateTimeTextFormatter.endOfDayMillis(System.currentTimeMillis())
                                    onDateRangeChange(start, end)
                                },
                            )
                            QuickDateChip(
                                label = "本月",
                                onClick = {
                                    val range = TimeRangeUtils.currentMonthRange()
                                    onDateRangeChange(range.startAtMillis, range.endAtMillis)
                                },
                            )
                            QuickDateChip(
                                label = "清除",
                                onClick = { onDateRangeChange(null, null) },
                            )
                        }
                        MoneySelectionField(
                            label = "开始日期",
                            value = state.dateStartAt?.let(DateTimeTextFormatter::formatDateOnly) ?: "不限",
                            modifier = Modifier.clickable { dateField = HistoryDateField.START },
                        )
                        MoneySelectionField(
                            label = "结束日期",
                            value = state.dateEndAt?.let(DateTimeTextFormatter::formatDateOnly) ?: "不限",
                            modifier = Modifier.clickable { dateField = HistoryDateField.END },
                        )
                    }
                }
                HistoryFilterSheet.AMOUNT -> {
                    HistoryFilterSheetContent(title = "金额") {
                        OutlinedTextField(
                            value = state.minAmountText,
                            onValueChange = onMinAmountChange,
                            label = { Text("最小金额") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = state.maxAmountText,
                            onValueChange = onMaxAmountChange,
                            label = { Text("最大金额") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                    }
                }
            }
        }
    }

    MoneyFormPage(
        title = "历史",
        modifier = modifier,
        contentPadding = PaddingValues(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                text = "${state.records.size} 条记录",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            OutlinedTextField(
                value = state.keyword,
                onValueChange = onKeywordChange,
                label = { Text("搜索") },
                placeholder = { Text("搜索用途或备注") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = state.selectedAccountId != null,
                    onClick = { sheet = HistoryFilterSheet.ACCOUNT },
                    label = { Text(accountChipLabel(state)) },
                )
                FilterChip(
                    selected = state.dateStartAt != null || state.dateEndAt != null,
                    onClick = { sheet = HistoryFilterSheet.DATE },
                    label = { Text(dateChipLabel(state)) },
                )
                FilterChip(
                    selected = state.minAmountText.isNotBlank() || state.maxAmountText.isNotBlank(),
                    onClick = { sheet = HistoryFilterSheet.AMOUNT },
                    label = { Text(amountChipLabel(state)) },
                )
            }
        }
        if (state.records.isEmpty()) {
            item {
                MoneyEmptyStateCard(
                    title = "还没有记录",
                    subtitle = "记下第一笔入账、出账或转账后，这里会按时间线展示。",
                )
            }
        } else {
            items(state.records, key = { it.id }) { record ->
                HistoryRow(
                    record = record,
                    settings = state.settings,
                    onClick = { onRecordClick(record) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryFilterSheetContent(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MoneySectionHeader(title = title)
        content()
    }
}

@Composable
private fun HistoryRow(
    record: HistoryRecordUiModel,
    settings: com.shihuaidexianyu.money.domain.model.AppSettings,
    onClick: () -> Unit,
) {
    val amountColor = recordAmountColor(record)

    MoneyCard(
        modifier = Modifier.clickable(onClick = onClick),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(record.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${record.subtitle}｜${DateTimeTextFormatter.format(record.occurredAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = AmountFormatter.format(record.amount, settings),
                style = MaterialTheme.typography.titleLarge,
                color = amountColor,
            )
        }
    }
}

@Composable
private fun recordAmountColor(record: HistoryRecordUiModel): Color {
    return when (record.kind) {
        HistoryRecordKind.TRANSFER -> MaterialTheme.colorScheme.onSurfaceVariant
        HistoryRecordKind.CASH_FLOW,
        HistoryRecordKind.BALANCE_UPDATE,
        HistoryRecordKind.BALANCE_ADJUSTMENT,
        -> when {
            record.amount > 0 -> Color(0xFFC24A4A)
            record.amount < 0 -> Color(0xFF3F8A63)
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    }
}

private fun accountChipLabel(state: HistoryUiState): String {
    val account = state.accountOptions.firstOrNull { it.id == state.selectedAccountId }
    return account?.name ?: "全部账户"
}

private fun dateChipLabel(state: HistoryUiState): String {
    val start = state.dateStartAt
    val end = state.dateEndAt
    return if (start == null && end == null) {
        "日期"
    } else if (start != null && end != null) {
        "${DateTimeTextFormatter.formatDateOnly(start)} 至 ${DateTimeTextFormatter.formatDateOnly(end)}"
    } else if (start != null) {
        "${DateTimeTextFormatter.formatDateOnly(start)} 起"
    } else {
        "截至 ${DateTimeTextFormatter.formatDateOnly(requireNotNull(end))}"
    }
}

private fun amountChipLabel(state: HistoryUiState): String {
    return if (state.minAmountText.isBlank() && state.maxAmountText.isBlank()) {
        "金额"
    } else {
        "金额已筛选"
    }
}

@Composable
private fun QuickDateChip(
    label: String,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = false,
        onClick = onClick,
        label = { Text(label) },
    )
}

