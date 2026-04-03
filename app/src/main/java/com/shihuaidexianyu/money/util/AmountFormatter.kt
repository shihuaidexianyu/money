package com.shihuaidexianyu.money.util

import com.shihuaidexianyu.money.domain.model.AppSettings
import java.math.BigDecimal
import java.math.RoundingMode

object AmountFormatter {
    fun format(amountInMinor: Long, settings: AppSettings): String {
        val absolute = BigDecimal.valueOf(amountInMinor)
            .movePointLeft(2)
            .abs()
            .setScale(2, RoundingMode.DOWN)
            .toPlainString()
        val signed = if (amountInMinor < 0) "-" else ""
        return "${signed}${settings.currencySymbol}$absolute"
    }
}

