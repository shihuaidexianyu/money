package com.shihuaidexianyu.money.data.backup

import android.content.Context
import androidx.core.content.FileProvider
import com.shihuaidexianyu.money.data.export.ExportShareFile
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PreImportBackupWriter(
    private val context: Context,
) {
    suspend fun write(json: String, timestamp: Long = System.currentTimeMillis()): ExportShareFile {
        return withContext(Dispatchers.IO) {
            val backupDir = File(context.filesDir, BACKUP_DIR_NAME).apply { mkdirs() }
            val fileName = buildFileName(timestamp)
            val backupFile = File(backupDir, fileName)
            backupFile.writeText(json, Charsets.UTF_8)
            ExportShareFile(
                uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    backupFile,
                ),
                fileName = fileName,
            )
        }
    }

    private fun buildFileName(timestamp: Long): String {
        val dateText = FILE_TIME_FORMATTER.format(
            Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()),
        )
        return "money-pre-import-backup-$dateText.json"
    }

    private companion object {
        const val BACKUP_DIR_NAME = "pre_import_backups"
        val FILE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    }
}
