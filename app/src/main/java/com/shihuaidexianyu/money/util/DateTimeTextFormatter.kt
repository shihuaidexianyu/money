package com.shihuaidexianyu.money.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object DateTimeTextFormatter {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private const val MINUTE_MILLIS = 60_000L

    fun format(
        timeMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String {
        return Instant.ofEpochMilli(timeMillis).atZone(zoneId).format(formatter)
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

    fun formatTimeOnly(
        timeMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String {
        return Instant.ofEpochMilli(timeMillis).atZone(zoneId).format(timeFormatter)
    }

    fun replaceDate(
        baseTimeMillis: Long,
        selectedDateMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val baseDateTime = Instant.ofEpochMilli(baseTimeMillis).atZone(zoneId).toLocalDateTime()
        val selectedDate = Instant.ofEpochMilli(selectedDateMillis).atZone(zoneId).toLocalDate()
        return selectedDate
            .atTime(baseDateTime.toLocalTime().withSecond(0).withNano(0))
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
    }

    fun replaceTime(
        baseTimeMillis: Long,
        hour: Int,
        minute: Int,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val baseDate = Instant.ofEpochMilli(baseTimeMillis).atZone(zoneId).toLocalDate()
        return baseDate
            .atTime(hour, minute)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
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

