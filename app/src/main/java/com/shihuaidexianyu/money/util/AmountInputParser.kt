package com.shihuaidexianyu.money.util

import java.math.RoundingMode

object AmountInputParser {
    fun parseToMinor(text: String): Long? {
        val normalized = text.trim()
        if (normalized.isEmpty()) return null
        if (normalized.startsWith("-")) return null

        return normalized.toBigDecimalOrNull()
            ?.setScale(2, RoundingMode.DOWN)
            ?.movePointRight(2)
            ?.longValueExact()
    }
}

