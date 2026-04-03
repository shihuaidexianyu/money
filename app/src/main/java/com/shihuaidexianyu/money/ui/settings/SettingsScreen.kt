package com.shihuaidexianyu.money.ui.settings

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.shihuaidexianyu.money.domain.model.HomePeriod
import com.shihuaidexianyu.money.domain.model.ThemeMode
import com.shihuaidexianyu.money.ui.common.MoneyCard
import com.shihuaidexianyu.money.ui.common.MoneyChoiceDialog
import com.shihuaidexianyu.money.ui.common.MoneyFormPage
import com.shihuaidexianyu.money.ui.common.MoneyListRow
import com.shihuaidexianyu.money.ui.common.MoneyListSection
import com.shihuaidexianyu.money.ui.common.MoneySectionDivider
import com.shihuaidexianyu.money.ui.common.MoneySectionHeader
import com.shihuaidexianyu.money.ui.common.MoneyTextInputDialog
import com.shihuaidexianyu.money.util.DateTimeTextFormatter

private sealed interface SettingsDialog {
    data object HomePeriod : SettingsDialog
    data object ThemeMode : SettingsDialog
    data object CurrencySymbol : SettingsDialog
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onHomePeriodChange: (HomePeriod) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onCurrencySymbolChange: (String) -> Unit,
    onShowStaleMarkChange: (Boolean) -> Unit,
    onManageAccountOrder: () -> Unit,
    onExportJson: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val settings = state.settings
    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }
    var currencyDraft by remember(settings.currencySymbol) { mutableStateOf(settings.currencySymbol) }

    dialog?.let { currentDialog ->
        when (currentDialog) {
            SettingsDialog.HomePeriod -> {
                MoneyChoiceDialog(
                    title = "首页默认周期",
                    options = HomePeriod.entries,
                    selected = settings.homePeriod,
                    label = { it.displayName },
                    onSelect = {
                        onHomePeriodChange(it)
                        dialog = null
                    },
                    onDismiss = { dialog = null },
                )
            }

            SettingsDialog.ThemeMode -> {
                MoneyChoiceDialog(
                    title = "主题模式",
                    options = ThemeMode.entries,
                    selected = settings.themeMode,
                    label = { it.displayName },
                    onSelect = {
                        onThemeModeChange(it)
                        dialog = null
                    },
                    onDismiss = { dialog = null },
                )
            }

            SettingsDialog.CurrencySymbol -> {
                MoneyTextInputDialog(
                    title = "货币符号",
                    value = currencyDraft,
                    onValueChange = { currencyDraft = it },
                    onConfirm = {
                        onCurrencySymbolChange(currencyDraft)
                        dialog = null
                    },
                    onDismiss = { dialog = null },
                    confirmLabel = "保存",
                )
            }
        }
    }

    MoneyFormPage(
        title = "设置",
        modifier = modifier,
    ) {
        item { MoneySectionHeader(title = "显示") }
        item {
            MoneyListSection {
                MoneyListRow(
                    title = "首页默认周期",
                    trailing = settings.homePeriod.displayName,
                    modifier = Modifier.clickable { dialog = SettingsDialog.HomePeriod },
                )
                MoneySectionDivider()
                MoneyListRow(
                    title = "主题模式",
                    trailing = settings.themeMode.displayName,
                    modifier = Modifier.clickable { dialog = SettingsDialog.ThemeMode },
                )
                MoneySectionDivider()
                MoneyListRow(
                    title = "货币符号",
                    trailing = settings.currencySymbol,
                    modifier = Modifier.clickable {
                        currencyDraft = settings.currencySymbol
                        dialog = SettingsDialog.CurrencySymbol
                    },
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
                    title = "账户顺序",
                    trailing = "自定义",
                    modifier = Modifier.clickable(onClick = onManageAccountOrder),
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
                    androidx.compose.material3.Text(if (state.isExporting) "正在导出..." else "导出 JSON 备份")
                }
                state.exportError?.let {
                    androidx.compose.material3.Text(
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
                        androidx.compose.material3.Text("分享备份文件")
                    }
                }
            }
        }
    }
}

