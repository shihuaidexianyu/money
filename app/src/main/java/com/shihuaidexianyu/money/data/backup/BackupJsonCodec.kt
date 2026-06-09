package com.shihuaidexianyu.money.data.backup

import com.shihuaidexianyu.money.domain.model.backup.MoneyBackupSnapshot
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object BackupJsonCodec {
    @OptIn(ExperimentalSerializationApi::class)
    val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = true
        prettyPrint = false
    }

    fun encode(snapshot: MoneyBackupSnapshot): String =
        json.encodeToString(snapshot)

    fun decode(raw: String): MoneyBackupSnapshot =
        json.decodeFromString(raw)
}
