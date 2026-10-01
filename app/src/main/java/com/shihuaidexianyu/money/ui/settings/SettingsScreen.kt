package com.shihuaidexianyu.money.ui.settings

import android.content.ClipData
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.R
import com.shihuaidexianyu.money.domain.model.AmountColorMode
import com.shihuaidexianyu.money.domain.model.AppRelockDelay
import com.shihuaidexianyu.money.domain.model.MAX_CURRENCY_SYMBOL_LENGTH
import com.shihuaidexianyu.money.domain.model.ThemeMode
import com.shihuaidexianyu.money.domain.usecase.BackupValidationResult
import com.shihuaidexianyu.money.data.backup.ImportReceiptKind
import com.shihuaidexianyu.money.data.backup.ImportReceipt
import com.shihuaidexianyu.money.ui.reminder.NotificationPermissionUiState
import com.shihuaidexianyu.money.ui.reminder.NotificationSettingsTarget
import com.shihuaidexianyu.money.ui.common.CollectUiEffects
import com.shihuaidexianyu.money.ui.common.MoneyChoiceDialog
import com.shihuaidexianyu.money.ui.common.MoneyConfirmDialog
import com.shihuaidexianyu.money.ui.common.MoneyFormPage
import com.shihuaidexianyu.money.ui.common.MoneyListRow
import com.shihuaidexianyu.money.ui.common.MoneyListSection
import com.shihuaidexianyu.money.ui.common.MoneySectionDivider
import com.shihuaidexianyu.money.ui.common.MoneySectionHeader
import com.shihuaidexianyu.money.ui.common.MoneyTextInputDialog
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import com.shihuaidexianyu.money.util.DateTimeTextFormatter

private sealed interface SettingsDialog {
    data object ExportWarning : SettingsDialog
    data object ShareWarning : SettingsDialog
    data class SafetyWarning(val receiptId: String) : SettingsDialog
    data object ThemeMode : SettingsDialog
    data object AmountColorMode : SettingsDialog
    data object CurrencySymbol : SettingsDialog
    data object RelockDelay : SettingsDialog
    data class ImportSuccess(val receiptId: String) : SettingsDialog
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    effectFlow: SharedFlow<SettingsEffect>,
    onBack: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onAmountColorModeChange: (AmountColorMode) -> Unit,
    onCurrencySymbolChange: (String) -> Unit,
    onBiometricLockChange: (Boolean) -> Unit,
    onRelockDelayChange: (AppRelockDelay) -> Unit,
    onHideNotificationAmountsChange: (Boolean) -> Unit,
    onHideRecentTasksChange: (Boolean) -> Unit,
    onHideInAppAmountsChange: (Boolean) -> Unit,
    notificationPermissionState: NotificationPermissionUiState,
    onRequestNotificationPermission: () -> Unit,
    onOpenNotificationSettings: (NotificationSettingsTarget) -> Unit,
    onManageReminders: () -> Unit,
    onManageAccountReminderConfigs: () -> Unit,
    onOpenLanAi: () -> Unit,
    onExportData: () -> Unit,
    onImportData: (Uri) -> Unit,
    onConfirmImport: (String) -> Unit,
    onRollbackImport: (String) -> Unit,
    modifier: Modifier = Modifier,
    onShareBackup: () -> Unit = {},
    onSaveExportToDocument: (Uri?) -> Unit = {},
    onResumeExport: () -> Unit = {},
    onDismissImportPreview: () -> Unit = {},
    onRefreshImportHistory: () -> Unit = {},
    onExportSafetySnapshot: (String) -> Unit = {},
) {
    val settings = state.portableSettings
    val devicePreferences = state.devicePreferences
    val context = LocalContext.current
    val versionInfo = remember(context) { context.applicationVersionInfo() }
    val unknownLabel = stringResource(R.string.settings_unknown)
    val versionText = versionInfo?.let {
        stringResource(R.string.settings_version_format, it.name ?: unknownLabel, it.code)
    } ?: unknownLabel
    val exportChooserTitle = stringResource(R.string.settings_export_chooser)
    val relockDelayLabels = mapOf(
        AppRelockDelay.IMMEDIATELY to stringResource(R.string.settings_relock_immediately),
        AppRelockDelay.THIRTY_SECONDS to stringResource(R.string.settings_relock_30_seconds),
        AppRelockDelay.ONE_MINUTE to stringResource(R.string.settings_relock_1_minute),
        AppRelockDelay.FIVE_MINUTES to stringResource(R.string.settings_relock_5_minutes),
    )
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    fun launchFileAction(action: () -> Unit) {
        try {
            action()
        } catch (_: ActivityNotFoundException) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.settings_file_action_unavailable))
            }
        }
    }
    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }
    var rollbackTarget by remember { mutableStateOf<String?>(null) }
    var receiptActionsTarget by rememberSaveable { mutableStateOf<String?>(null) }
    val busy = state.isExporting || state.isImporting
    BackHandler(enabled = busy) {}
    var currencyDraft by remember(settings.currencySymbol) { mutableStateOf(settings.currencySymbol) }
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let(onImportData)
    }
    val saveDocumentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        onSaveExportToDocument(it)
    }

    CollectUiEffects(effectFlow, snackbarHostState) { effect ->
        when (effect) {
            is SettingsEffect.ExportReady -> {
                launchFileAction {
                    if (effect.saveToDocument) {
                        saveDocumentLauncher.launch(effect.fileName)
                    } else {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = effect.mimeType
                            putExtra(Intent.EXTRA_STREAM, effect.uri)
                            putExtra(Intent.EXTRA_TITLE, effect.fileName)
                            putExtra(Intent.EXTRA_SUBJECT, effect.fileName)
                            clipData = ClipData.newUri(context.contentResolver, effect.fileName, effect.uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, exportChooserTitle))
                    }
                }
            }

            is SettingsEffect.ImportFinished -> {
                dialog = SettingsDialog.ImportSuccess(effect.receipt.id)
            }

            is SettingsEffect.RollbackFinished -> Unit
            is SettingsEffect.ShowMessage -> Unit
        }
    }

    dialog?.let { currentDialog ->
        when (currentDialog) {
            SettingsDialog.ExportWarning, SettingsDialog.ShareWarning -> {
                MoneyConfirmDialog(
                    title = stringResource(R.string.settings_export_plaintext_title),
                    message = stringResource(R.string.settings_plaintext_export_message),
                    onConfirm = {
                        if (currentDialog == SettingsDialog.ShareWarning) onShareBackup() else onExportData()
                        dialog = null
                    },
                    onDismiss = { dialog = null },
                    confirmLabel = stringResource(R.string.settings_continue_export),
                    dismissLabel = stringResource(R.string.action_cancel),
                )
            }

            is SettingsDialog.SafetyWarning -> {
                MoneyConfirmDialog(
                    title = stringResource(R.string.settings_safety_export),
                    message = stringResource(R.string.settings_safety_export_hint),
                    onConfirm = { dialog = null; onExportSafetySnapshot(currentDialog.receiptId) },
                    onDismiss = { dialog = null },
                )
            }

            SettingsDialog.ThemeMode -> {
                MoneyChoiceDialog(
                    title = stringResource(R.string.settings_theme_mode),
                    options = ThemeMode.entries,
                    selected = devicePreferences.themeMode,
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
                    title = stringResource(R.string.settings_amount_color),
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
                    title = stringResource(R.string.settings_currency_symbol),
                    value = currencyDraft,
                    onValueChange = { currencyDraft = it.take(MAX_CURRENCY_SYMBOL_LENGTH) },
                    onConfirm = {
                        onCurrencySymbolChange(currencyDraft)
                        dialog = null
                    },
                    onDismiss = { dialog = null },
                    confirmLabel = stringResource(R.string.action_save),
                )
            }

            SettingsDialog.RelockDelay -> {
                MoneyChoiceDialog(
                    title = stringResource(R.string.settings_relock_title),
                    options = AppRelockDelay.entries,
                    selected = devicePreferences.relockDelay,
                    label = { relockDelayLabels.getValue(it) },
                    onSelect = {
                        onRelockDelayChange(it)
                        dialog = null
                    },
                    onDismiss = { dialog = null },
                )
            }

            is SettingsDialog.ImportSuccess -> {
                // The success dialog only acknowledges completion. Rollback stays on the
                // import-history row, which has its own confirmation dialog.
                MoneyConfirmDialog(
                    title = stringResource(R.string.settings_import_complete),
                    message = stringResource(R.string.settings_import_complete_message),
                    onConfirm = { dialog = null },
                    onDismiss = { dialog = null },
                    confirmLabel = stringResource(R.string.action_done),
                    showDismissAction = false,
                )
            }
        }
    }

    rollbackTarget?.let { receiptId ->
        MoneyConfirmDialog(
            title = stringResource(R.string.settings_rollback_confirm_title),
            message = stringResource(R.string.settings_rollback_confirm_message),
            onConfirm = {
                rollbackTarget = null
                onRollbackImport(receiptId)
            },
            onDismiss = { rollbackTarget = null },
            confirmLabel = stringResource(R.string.settings_rollback),
            destructive = true,
        )
    }

    val notificationPresentation = notificationSettingsPresentation(notificationPermissionState)
    val importReceiptRows = importReceiptHistoryRows(
        receipts = state.importHistory,
        rollbackEligibleReceiptId = state.rollbackEligibleReceiptId.takeUnless { state.isHistoryLoading },
        availableSafetyReceiptIds = state.availableSafetyReceiptIds,
    )
    state.pendingImportPreview?.takeUnless { state.isImporting }?.let { preview ->
        MoneyConfirmDialog(
            title = stringResource(R.string.settings_import_overwrite_title),
            message = stringResource(R.string.settings_import_backup_time,
                DateTimeTextFormatter.format(preview.validation.exportedAt)) + "\n\n" + preview.validation.confirmMessage(),
            onConfirm = { onConfirmImport(preview.stageId) },
            onDismiss = onDismissImportPreview,
            confirmLabel = stringResource(R.string.settings_confirm_import),
            destructive = true,
        )
    }
    importReceiptRows.firstOrNull { it.receipt.id == receiptActionsTarget }?.let { row ->
        val actions = buildList {
            if (row.canRollback) add(R.string.settings_rollback)
            if (row.canExportSafety) add(R.string.settings_safety_export)
        }
        MoneyChoiceDialog(
            title = stringResource(R.string.settings_import_history), options = actions,
            label = { stringResource(it) },
            onSelect = { action ->
                receiptActionsTarget = null
                if (action == R.string.settings_rollback) rollbackTarget = row.receipt.id
                else dialog = SettingsDialog.SafetyWarning(row.receipt.id)
            }, onDismiss = { receiptActionsTarget = null },
        )
    }
    MoneyFormPage(
        title = stringResource(R.string.settings_title),
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        // Settings is a pushed sub-page (not a bottom-bar tab) — without this it was the only
        // sub-page with no visible way back.
        onBack = { if (!busy) onBack() },
        footer = if (busy) ({
            Column {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(R.string.settings_backup_busy), modifier = Modifier.padding(top = 8.dp))
            }
        }) else null,
    ) {
        item {
            MoneySectionHeader(title = stringResource(SETTINGS_SECTION_CONTRACTS[0].titleRes))
        }
        item {
            MoneyListSection {
                MoneyListRow(
                    title = stringResource(R.string.settings_theme_mode),
                    trailing = devicePreferences.themeMode.displayName,
                    onClick = { dialog = SettingsDialog.ThemeMode },
                )
                MoneySectionDivider()
                MoneyListRow(
                    title = stringResource(R.string.settings_amount_color),
                    trailing = settings.amountColorMode.displayName,
                    onClick = { dialog = SettingsDialog.AmountColorMode },
                )
                MoneySectionDivider()
                MoneyListRow(
                    title = stringResource(R.string.settings_currency_symbol),
                    subtitle = stringResource(R.string.settings_currency_description),
                    trailing = settings.currencySymbol,
                    onClick = {
                        currencyDraft = settings.currencySymbol
                        dialog = SettingsDialog.CurrencySymbol
                    },
                )
            }
        }

        item {
            MoneySectionHeader(title = stringResource(SETTINGS_SECTION_CONTRACTS[1].titleRes))
        }
        item {
            MoneyListSection {
                MoneyListRow(
                    title = stringResource(R.string.settings_biometric_lock),
                    subtitle = stringResource(R.string.settings_biometric_description),
                    showChevron = false,
                    switchChecked = devicePreferences.biometricLock,
                    onClick = { onBiometricLockChange(!devicePreferences.biometricLock) },
                    accessory = {
                        Switch(
                            checked = devicePreferences.biometricLock,
                            onCheckedChange = null,
                        )
                    },
                )
                MoneySectionDivider()
                MoneyListRow(
                    title = stringResource(R.string.settings_relock_time),
                    subtitle = stringResource(R.string.settings_relock_description),
                    trailing = relockDelayLabels.getValue(devicePreferences.relockDelay),
                    onClick = { dialog = SettingsDialog.RelockDelay },
                )
                MoneySectionDivider()
                PrivacySwitchRow(
                    title = stringResource(R.string.settings_hide_recents),
                    checked = devicePreferences.hideRecentTasks,
                    onCheckedChange = onHideRecentTasksChange,
                )
                MoneySectionDivider()
                PrivacySwitchRow(
                    title = stringResource(R.string.settings_hide_notifications),
                    checked = devicePreferences.hideNotificationAmounts,
                    onCheckedChange = onHideNotificationAmountsChange,
                )
                MoneySectionDivider()
                PrivacySwitchRow(
                    title = stringResource(R.string.settings_hide_in_app_amounts),
                    subtitle = stringResource(R.string.settings_hide_in_app_amounts_description),
                    checked = devicePreferences.hideInAppAmounts,
                    onCheckedChange = onHideInAppAmountsChange,
                )
            }
        }

        item {
            MoneySectionHeader(title = stringResource(SETTINGS_SECTION_CONTRACTS[2].titleRes))
        }
        item {
            MoneyListSection {
                MoneyListRow(
                    title = stringResource(R.string.settings_notification_permission_channels),
                    subtitle = stringResource(notificationPresentation.statusRes),
                    trailing = if (notificationPresentation.action == NotificationSettingsAction.REQUEST_PERMISSION) {
                        stringResource(R.string.settings_request_permission)
                    } else {
                        stringResource(R.string.settings_open_system_settings)
                    },
                    onClick = {
                        when (notificationPresentation.action) {
                            NotificationSettingsAction.REQUEST_PERMISSION -> onRequestNotificationPermission()
                            NotificationSettingsAction.OPEN_SETTINGS -> onOpenNotificationSettings(
                                notificationPresentation.settingsTarget ?: NotificationSettingsTarget.APPLICATION,
                            )
                        }
                    },
                )
                MoneySectionDivider()
                MoneyListRow(
                    title = stringResource(R.string.settings_reminder_management),
                    subtitle = stringResource(R.string.settings_reminder_management_description),
                    onClick = onManageReminders,
                )
                MoneySectionDivider()
                MoneyListRow(
                    title = stringResource(R.string.settings_account_reminder_config),
                    subtitle = stringResource(R.string.settings_account_reminder_description),
                    onClick = onManageAccountReminderConfigs,
                )
            }
        }

        item {
            MoneySectionHeader(title = stringResource(SETTINGS_SECTION_CONTRACTS[3].titleRes))
        }
        item {
            MoneyListSection {
                MoneyListRow(
                    title = stringResource(R.string.settings_lan_ai),
                    subtitle = stringResource(R.string.settings_lan_ai_description),
                    onClick = onOpenLanAi,
                )
                MoneySectionDivider()
                MoneyListRow(
                    title = stringResource(R.string.settings_save_backup),
                    subtitle = stringResource(R.string.settings_plaintext_warning),
                    trailing = if (state.isExporting) stringResource(R.string.settings_exporting) else "JSON",
                    onClick = { dialog = SettingsDialog.ExportWarning },
                    enabled = !state.isExporting && !state.isImporting,
                )
                MoneySectionDivider()
                MoneyListRow(
                    title = stringResource(R.string.settings_share_backup),
                    subtitle = stringResource(R.string.settings_plaintext_warning),
                    onClick = { dialog = SettingsDialog.ShareWarning }, enabled = !busy,
                )
                state.pendingExport?.let { pending ->
                    MoneySectionDivider()
                    MoneyListRow(
                        title = stringResource(R.string.settings_backup_pending),
                        subtitle = pending.fileName,
                        trailing = stringResource(R.string.settings_backup_resume),
                        onClick = onResumeExport, enabled = !busy,
                    )
                }
                MoneySectionDivider()
                MoneyListRow(
                    title = stringResource(R.string.settings_import_data),
                    subtitle = stringResource(R.string.settings_import_description),
                    trailing = if (state.isImporting) stringResource(R.string.settings_importing) else "JSON",
                    onClick = {
                        launchFileAction { openDocumentLauncher.launch(arrayOf("application/json", "text/*")) }
                    },
                    enabled = !state.isImporting && !state.isExporting,
                )
                if (importReceiptRows.isNotEmpty()) {
                    MoneySectionDivider()
                    importReceiptRows.forEachIndexed { index, row ->
                        val receipt = row.receipt
                        MoneyListRow(
                            title = stringResource(
                                if (receipt.kind == ImportReceiptKind.IMPORT) {
                                    R.string.settings_import_history
                                } else {
                                    R.string.settings_restore_history
                                },
                            ),
                            subtitle = receipt.historySubtitle() + if (row.canRollback) "" else
                                "\n" + stringResource(R.string.settings_receipt_unavailable),
                            trailing = if (row.canRollback || row.canExportSafety) {
                                stringResource(R.string.settings_receipt_options)
                            } else {
                                null
                            },
                            onClick = if (row.canRollback || row.canExportSafety) {
                                { receiptActionsTarget = receipt.id }
                            } else {
                                null
                            },
                            enabled = !state.isImporting && !state.isExporting,
                            showChevron = row.canRollback || row.canExportSafety,
                            isClickable = row.canRollback || row.canExportSafety,
                        )
                        if (index != importReceiptRows.lastIndex) MoneySectionDivider()
                    }
                }
                if (state.isHistoryLoading || state.historyLoadFailed) {
                    MoneySectionDivider()
                    MoneyListRow(
                        title = stringResource(if (state.historyLoadFailed) R.string.settings_history_failed else R.string.settings_history_loading),
                        onClick = onRefreshImportHistory.takeIf { state.historyLoadFailed },
                        enabled = !busy, showChevron = state.historyLoadFailed,
                    )
                }
            }
        }

        item {
            MoneySectionHeader(title = stringResource(SETTINGS_SECTION_CONTRACTS[4].titleRes))
        }
        item {
            MoneyListSection {
                MoneyListRow(
                    title = stringResource(R.string.settings_version),
                    trailing = versionText,
                    showChevron = false,
                )
                MoneySectionDivider()
                MoneyListRow(
                    title = stringResource(R.string.settings_offline_safety),
                    subtitle = stringResource(R.string.settings_offline_safety_copy),
                    showChevron = false,
                )
            }
        }
    }
}

@Composable
private fun PrivacySwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
) {
    MoneyListRow(
        title = title,
        subtitle = subtitle,
        showChevron = false,
        switchChecked = checked,
        onClick = { onCheckedChange(!checked) },
        accessory = {
            Switch(
                checked = checked,
                onCheckedChange = null,
            )
        },
    )
}

@Composable
private fun ImportReceipt.historySubtitle(): String {
    val ledgerCount = counts.cashFlowCount + counts.transferCount +
        counts.balanceUpdateCount + counts.balanceAdjustmentCount
    return stringResource(
        R.string.settings_receipt_summary_format,
        DateTimeTextFormatter.format(importedAt),
        counts.accountCount,
        ledgerCount,
        schemaVersion,
    )
}

private data class ApplicationVersionInfo(val name: String?, val code: Long)

private fun Context.applicationVersionInfo(): ApplicationVersionInfo? = runCatching {
    val info = if (Build.VERSION.SDK_INT >= 33) {
        packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
    } else {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, 0)
    }
    ApplicationVersionInfo(info.versionName, info.longVersionCode)
}.getOrNull()

@Composable
private fun BackupValidationResult.confirmMessage(): String = stringResource(
    R.string.settings_import_confirm_message,
    accountCount,
    cashFlowCount,
    transferCount,
    balanceUpdateCount,
    balanceAdjustmentCount,
    reminderCount,
)
