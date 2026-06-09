package com.shihuaidexianyu.money.data.backup

import android.content.Context
import android.net.Uri
import com.shihuaidexianyu.money.domain.model.backup.MoneyBackupSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BackupFileReader(
    private val context: Context,
) {
    suspend fun readSnapshot(uri: Uri): MoneyBackupSnapshot {
        return BackupJsonCodec.decode(readText(uri))
    }

    private suspend fun readText(uri: Uri): String {
        return withContext(Dispatchers.IO) {
            val inputStream = requireNotNull(context.contentResolver.openInputStream(uri)) {
                "无法读取所选文件"
            }
            inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
    }
}
