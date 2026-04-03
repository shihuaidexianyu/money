package com.shihuaidexianyu.money.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object DateTimeTextFormatter {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private const val MINUTE_MILLIS = 60_000L

    fun format(
        timeMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String {
        return Instant.ofEpochMilli(timeMillis).atZone(zoneId).format(formatter)
    }

    fun parse(
        value: String,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long? {
        return runCatching {
            LocalDateTime.parse(value.trim(), formatter)
                .atZone(zoneId)
                .toInstant()
                .toEpochMilli()
        }.getOrNull()
    }

    fun floorToMinute(timeMillis: Long): Long {
        return timeMillis - Math.floorMod(timeMillis, MINUTE_MILLIS)
    }

    fun formatDateOnly(
        timeMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String {
        return Instant.ofEpochMilli(timeMillis).atZone(zoneId).format(dateFormatter)
    }

    fun startOfDayMillis(
        timeMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long {
        return Instant.ofEpochMilli(timeMillis)
            .atZone(zoneId)
            .toLocalDate()
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
    }

    fun endOfDayMillis(
        timeMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long {
        return Instant.ofEpochMilli(timeMillis)
            .atZone(zoneId)
            .toLocalDate()
            .plusDays(1)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli() - 1L
    }
}
