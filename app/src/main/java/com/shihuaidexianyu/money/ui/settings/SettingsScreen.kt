package com.shihuaidexianyu.money.ui.settings

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.domain.model.AccountSortMode
import com.shihuaidexianyu.money.domain.model.AmountDisplayStyle
import com.shihuaidexianyu.money.domain.model.HomePeriod
import com.shihuaidexianyu.money.domain.model.WeekStart
import com.shihuaidexianyu.money.ui.common.MoneyCard
import com.shihuaidexianyu.money.ui.common.MoneyListRow
import com.shihuaidexianyu.money.ui.common.MoneyPageTitle
import com.shihuaidexianyu.money.ui.common.MoneySectionDivider
import com.shihuaidexianyu.money.ui.common.MoneySectionHeader
import com.shihuaidexianyu.money.ui.common.MoneyListSection
import com.shihuaidexianyu.money.util.DateTimeTextFormatter

private enum class SettingsChoiceSheet {
    HOME_PERIOD,
    WEEK_START,
    AMOUNT_STYLE,
    ACCOUNT_SORT,
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onHomePeriodChange: (HomePeriod) -> Unit,
    onWeekStartChange: (WeekStart) -> Unit,
    onCurrencySymbolChange: (String) -> Unit,
    onAmountDisplayStyleChange: (AmountDisplayStyle) -> Unit,
    onShowStaleMarkChange: (Boolean) -> Unit,
    onAccountSortModeChange: (AccountSortMode) -> Unit,
    onExportJson: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val settings = state.settings
    var choiceSheet by remember { mutableStateOf<SettingsChoiceSheet?>(null) }
    var currencyDraft by remember(settings.currencySymbol) { mutableStateOf(settings.currencySymbol) }
    var showCurrencyDialog by remember { mutableStateOf(false) }

    choiceSheet?.let { sheet ->
        when (sheet) {
            SettingsChoiceSheet.HOME_PERIOD -> {
                SettingsChoiceDialog(
                    title = "首页默认周期",
                    options = HomePeriod.entries,
                    selected = settings.homePeriod,
                    label = { it.displayName },
                    onSelect = {
                        onHomePeriodChange(it)
                        choiceSheet = null
                    },
                    onDismiss = { choiceSheet = null },
                )
            }
            SettingsChoiceSheet.WEEK_START -> {
                SettingsChoiceDialog(
                    title = "一周起始日",
                    options = WeekStart.entries,
                    selected = settings.weekStart,
                    label = { it.displayName },
                    onSelect = {
                        onWeekStartChange(it)
                        choiceSheet = null
                    },
                    onDismiss = { choiceSheet = null },
                )
            }
            SettingsChoiceSheet.AMOUNT_STYLE -> {
                SettingsChoiceDialog(
                    title = "金额显示格式",
                    options = AmountDisplayStyle.entries,
                    selected = settings.amountDisplayStyle,
                    label = { it.displayName },
                    onSelect = {
                        onAmountDisplayStyleChange(it)
                        choiceSheet = null
                    },
                    onDismiss = { choiceSheet = null },
                )
            }
            SettingsChoiceSheet.ACCOUNT_SORT -> {
                SettingsChoiceDialog(
                    title = "账户排序",
                    options = AccountSortMode.entries,
                    selected = settings.accountSortMode,
                    label = { it.displayName },
                    onSelect = {
                        onAccountSortModeChange(it)
                        choiceSheet = null
                    },
                    onDismiss = { choiceSheet = null },
                )
            }
        }
    }

    if (showCurrencyDialog) {
        AlertDialog(
            onDismissRequest = { showCurrencyDialog = false },
            title = { Text("货币符号") },
            text = {
                OutlinedTextField(
                    value = currencyDraft,
                    onValueChange = { currencyDraft = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onCurrencySymbolChange(currencyDraft)
                        showCurrencyDialog = false
                    },
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCurrencyDialog = false }) {
                    Text("取消")
                }
            },
        )
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item { MoneyPageTitle(title = "设置") }

        item { MoneySectionHeader(title = "显示") }
        item {
            MoneyListSection {
                MoneyListRow(
                    title = "首页默认周期",
                    trailing = settings.homePeriod.displayName,
                    modifier = Modifier.clickable { choiceSheet = SettingsChoiceSheet.HOME_PERIOD },
                )
                MoneySectionDivider()
                MoneyListRow(
                    title = "一周起始日",
                    trailing = settings.weekStart.displayName,
                    modifier = Modifier.clickable { choiceSheet = SettingsChoiceSheet.WEEK_START },
                )
                MoneySectionDivider()
                MoneyListRow(
                    title = "货币符号",
                    trailing = settings.currencySymbol,
                    modifier = Modifier.clickable {
                        currencyDraft = settings.currencySymbol
                        showCurrencyDialog = true
                    },
                )
                MoneySectionDivider()
                MoneyListRow(
                    title = "金额显示格式",
                    trailing = settings.amountDisplayStyle.displayName,
                    modifier = Modifier.clickable { choiceSheet = SettingsChoiceSheet.AMOUNT_STYLE },
                )
                MoneySectionDivider()
                MoneyListRow(
                    title = "显示过期账户标记",
                    showChevron = false,
                    accessory = {
                        Switch(
                            checked = settings.showStaleMark,
                            onCheckedChange = onShowStaleMarkChange,
                        )
                    },
                )
            }
        }

        item { MoneySectionHeader(title = "账户") }
        item {
            MoneyListSection {
                MoneyListRow(
                    title = "账户排序",
                    trailing = settings.accountSortMode.displayName,
                    modifier = Modifier.clickable { choiceSheet = SettingsChoiceSheet.ACCOUNT_SORT },
                )
            }
        }

        item { MoneySectionHeader(title = "数据") }
        item {
            MoneyCard {
                Button(
                    onClick = onExportJson,
                    enabled = !state.isExporting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (state.isExporting) "正在导出..." else "导出 JSON 备份")
                }
                state.exportError?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (state.lastExportFileName != null && state.lastExportUri != null) {
                    MoneyListSection {
                        MoneyListRow(
                            title = "最近导出",
                            trailing = state.lastExportFileName,
                            showChevron = false,
                        )
                        MoneySectionDivider()
                        MoneyListRow(
                            title = "保存位置",
                            trailing = state.lastExportRelativePath ?: "下载目录",
                            showChevron = false,
                        )
                        MoneySectionDivider()
                        MoneyListRow(
                            title = "导出时间",
                            trailing = state.lastExportedAt?.let(DateTimeTextFormatter::format) ?: "-",
                            showChevron = false,
                        )
                    }
                    Button(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/json"
                                putExtra(Intent.EXTRA_STREAM, state.lastExportUri)
                                putExtra(Intent.EXTRA_SUBJECT, state.lastExportFileName)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "分享备份文件"))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("分享备份文件")
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> SettingsChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                options.forEach { option ->
                    TextButton(
                        onClick = { onSelect(option) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        val titleColor = if (option == selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                        Text(
                            text = label(option),
                            color = titleColor,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
    )
}
