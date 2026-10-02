package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.ui.reminder.NotificationPermissionUiState
import com.shihuaidexianyu.money.ui.reminder.NotificationSettingsTarget
import com.shihuaidexianyu.money.ui.settings.NotificationSettingsAction
import com.shihuaidexianyu.money.ui.settings.SETTINGS_SECTION_CONTRACTS
import com.shihuaidexianyu.money.ui.settings.notificationSettingsPresentation
import com.shihuaidexianyu.money.ui.settings.importReceiptHistoryRows
import com.shihuaidexianyu.money.data.backup.ImportReceipt
import com.shihuaidexianyu.money.data.backup.ImportReceiptCounts
import com.shihuaidexianyu.money.data.backup.ImportReceiptKind
import com.shihuaidexianyu.money.data.backup.ImportReceiptStatus
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class SettingsSectionContractTest {
    @Test
    fun `all receipts remain visible but only eligible receipt can rollback`() {
        val older = receipt("older", importedAt = 100L)
        val latest = receipt("latest", importedAt = 200L)

        val rows = importReceiptHistoryRows(
            receipts = listOf(latest, older),
            rollbackEligibleReceiptId = older.id,
            availableSafetyReceiptIds = setOf(latest.id),
        )

        assertEquals(listOf("latest", "older"), rows.map { it.receipt.id })
        assertFalse(rows.first().canRollback)
        assertTrue(rows.first().canExportSafety)
        assertTrue(rows.last().canRollback)
        assertFalse(rows.last().canExportSafety)
    }

    @Test
    fun `settings only exposes appearance privacy backup and compatibility`() {
        assertEquals(
            listOf(
                R.string.settings_section_display to listOf("theme"),
                R.string.settings_section_privacy to listOf("biometric", "relock", "hide_recents", "hide_in_app"),
                R.string.settings_section_data to listOf("export_backup", "pending_export", "import_preview", "receipt_rollback"),
                R.string.settings_section_about to listOf("version", "offline_data_safety", "legacy_features"),
            ),
            SETTINGS_SECTION_CONTRACTS.map { it.titleRes to it.itemKeys },
        )
    }

    @Test
    fun `notification status tells the user whether to request or open exact settings`() {
        val notRequested = notificationSettingsPresentation(NotificationPermissionUiState.NotRequested)
        assertEquals(R.string.settings_notifications_not_authorized, notRequested.statusRes)
        assertEquals(NotificationSettingsAction.REQUEST_PERMISSION, notRequested.action)

        val denied = notificationSettingsPresentation(NotificationPermissionUiState.Denied(canRequestAgain = false))
        assertEquals(R.string.settings_notifications_denied_settings, denied.statusRes)
        assertEquals(NotificationSettingsAction.OPEN_SETTINGS, denied.action)
        assertEquals(NotificationSettingsTarget.APPLICATION, denied.settingsTarget)

        val channel = notificationSettingsPresentation(
            NotificationPermissionUiState.SettingsRequired(NotificationSettingsTarget.RECURRING_CHANNEL),
        )
        assertEquals(R.string.settings_recurring_channel_disabled, channel.statusRes)
        assertEquals(NotificationSettingsTarget.RECURRING_CHANNEL, channel.settingsTarget)

        val granted = notificationSettingsPresentation(NotificationPermissionUiState.Granted)
        assertEquals(R.string.settings_notifications_available, granted.statusRes)
        assertEquals(NotificationSettingsAction.OPEN_SETTINGS, granted.action)
    }

    @Test
    fun `backup and about copy describe the actual offline plaintext model`() {
        val copy = java.io.File("src/main/res/values/strings.xml").readText()
        assertTrue(copy.contains("未加密 JSON"))
        assertTrue(copy.contains("账目保存在这台设备上"))
        assertTrue(copy.contains("本版不再运行提醒和电脑连接服务"))
        assertTrue(copy.contains("不会自动备份"))
        listOf("AES", ".enc", "精确闹钟", "多个目标", "云同步").forEach { forbidden ->
            assertFalse(copy.contains(forbidden), forbidden)
        }
    }

    private fun receipt(id: String, importedAt: Long) = ImportReceipt(
        id = id,
        kind = ImportReceiptKind.IMPORT,
        status = ImportReceiptStatus.COMMITTED,
        importedAt = importedAt,
        sourceFileSha256 = "a".repeat(64),
        targetContentSha256 = "b".repeat(64),
        safetySnapshotFileName = "money-pre-import-$importedAt-test.json",
        safetySnapshotSha256 = "c".repeat(64),
        schemaVersion = 4,
        counts = ImportReceiptCounts(1, 2, 3, 4, 5, 6),
        commitSequence = 0L,
    )
}
