package com.shihuaidexianyu.money.ui.settings

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.domain.model.AmountColorMode
import com.shihuaidexianyu.money.domain.model.HomePeriod
import com.shihuaidexianyu.money.domain.model.MAX_CURRENCY_SYMBOL_LENGTH
import com.shihuaidexianyu.money.domain.model.ThemeMode
import com.shihuaidexianyu.money.ui.common.CollectUiEffects
import com.shihuaidexianyu.money.ui.common.MoneyCard
import com.shihuaidexianyu.money.ui.common.MoneyChoiceDialog
import com.shihuaidexianyu.money.ui.common.MoneyFormPage
import com.shihuaidexianyu.money.ui.common.MoneyListRow
import com.shihuaidexianyu.money.ui.common.MoneySectionDivider
import com.shihuaidexianyu.money.ui.common.MoneySectionHeader
import com.shihuaidexianyu.money.ui.common.MoneyTextInputDialog
import kotlinx.coroutines.flow.SharedFlow

private sealed interface SettingsDialog {
    data object HomePeriod : SettingsDialog
    data object ThemeMode : SettingsDialog
    data object AmountColorMode : SettingsDialog
    data object CurrencySymbol : SettingsDialog
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    effectFlow: SharedFlow<SettingsEffect>,
    onHomePeriodChange: (HomePeriod) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onAmountColorModeChange: (AmountColorMode) -> Unit,
    onCurrencySymbolChange: (String) -> Unit,
    onShowStaleMarkChange: (Boolean) -> Unit,
    onManageAccountOrder: () -> Unit,
    onExportData: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = state.settings
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }
    var currencyDraft by remember(settings.currencySymbol) { mutableStateOf(settings.currencySymbol) }

    CollectUiEffects(effectFlow, snackbarHostState) { effect ->
        when (effect) {
            is SettingsEffect.ExportReady -> {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = effect.mimeType
                    putExtra(Intent.EXTRA_STREAM, effect.uri)
                    putExtra(Intent.EXTRA_TITLE, effect.fileName)
                    putExtra(Intent.EXTRA_SUBJECT, effect.fileName)
                    clipData = ClipData.newUri(context.contentResolver, effect.fileName, effect.uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "导出数据"))
            }

            is SettingsEffect.ShowMessage -> Unit
        }
    }

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

            SettingsDialog.AmountColorMode -> {
                MoneyChoiceDialog(
                    title = "金额颜色习惯",
                    options = AmountColorMode.entries,
                    selected = settings.amountColorMode,
                    label = { it.displayName },
                    onSelect = {
                        onAmountColorModeChange(it)
                        dialog = null
                    },
                    onDismiss = { dialog = null },
                )
            }

            SettingsDialog.CurrencySymbol -> {
                MoneyTextInputDialog(
                    title = "货币符号",
                    value = currencyDraft,
                    onValueChange = { currencyDraft = it.take(MAX_CURRENCY_SYMBOL_LENGTH) },
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
        snackbarHostState = snackbarHostState,
    ) {
        item { MoneySectionHeader(title = "显示") }
        item {
            MoneyCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                MoneyListRow(
                    title = "首页默认周期",
                    subtitle = "控制首页净流入和净流出的默认周期",
                    trailing = settings.homePeriod.displayName,
                    modifier = Modifier.clickable { dialog = SettingsDialog.HomePeriod },
                )
                MoneySectionDivider()
                MoneyListRow(
                    title = "主题模式",
                    subtitle = "跟随系统，或固定为浅色 / 深色",
                    trailing = settings.themeMode.displayName,
                    modifier = Modifier.clickable { dialog = SettingsDialog.ThemeMode },
                )
                MoneySectionDivider()
                MoneyListRow(
                    title = "金额颜色习惯",
                    subtitle = "统一首页、历史和金额差额的红绿显示",
                    trailing = settings.amountColorMode.displayName,
                    modifier = Modifier.clickable { dialog = SettingsDialog.AmountColorMode },
                )
                MoneySectionDivider()
                MoneyListRow(
                    title = "货币符号",
                    subtitle = "影响金额显示格式，最多 4 个字符",
                    trailing = settings.currencySymbol,
                    modifier = Modifier.clickable {
                        currencyDraft = settings.currencySymbol
                        dialog = SettingsDialog.CurrencySymbol
                    },
                )
                MoneySectionDivider()
                MoneyListRow(
                    title = "显示过期账户标记",
                    subtitle = "在首页和账户页提醒哪些余额需要更新",
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

        item { MoneySectionHeader(title = "数据") }
        item {
            MoneyCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                MoneyListRow(
                    title = "导出数据",
                    subtitle = "生成 JSON 文件并分享",
                    trailing = if (state.isExporting) "导出中" else "JSON",
                    modifier = Modifier.clickable(
                        enabled = !state.isExporting,
                        onClick = onExportData,
                    ),
                )
            }
        }

        item { MoneySectionHeader(title = "账户管理") }
        item {
            MoneyCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                MoneyListRow(
                    title = "账户顺序",
                    subtitle = "调整账户页和选择器里的展示顺序",
                    trailing = "自定义",
                    modifier = Modifier.clickable(onClick = onManageAccountOrder),
                )
            }
        }
    }
}
