package com.shihuaidexianyu.money.data.export

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ExportShareFile(
    val uri: Uri,
    val fileName: String,
    val mimeType: String = "application/json",
)

class ExportJsonFileWriter(
    private val context: Context,
) {
    suspend fun write(json: String, timestamp: Long = System.currentTimeMillis()): ExportShareFile {
        return withContext(Dispatchers.IO) {
            val exportDir = File(context.cacheDir, EXPORT_DIR_NAME).apply { mkdirs() }
            val fileName = buildFileName(timestamp)
            val exportFile = File(exportDir, fileName)
            exportFile.writeText(json, Charsets.UTF_8)
            ExportShareFile(
                uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    exportFile,
                ),
                fileName = fileName,
            )
        }
    }

    private fun buildFileName(timestamp: Long): String {
        val dateText = FILE_TIME_FORMATTER.format(
            Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()),
        )
        return "money-export-$dateText.json"
    }

    private companion object {
        const val EXPORT_DIR_NAME = "exports"
        val FILE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    }
}
