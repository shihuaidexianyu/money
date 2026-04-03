package com.shihuaidexianyu.money.util

import com.shihuaidexianyu.money.domain.model.AmountDisplayStyle
import com.shihuaidexianyu.money.domain.model.AppSettings
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

object AmountFormatter {
    fun format(amountInMinor: Long, settings: AppSettings): String {
        val absolute = BigDecimal.valueOf(abs(amountInMinor), 2)
            .setScale(2, RoundingMode.DOWN)
            .toPlainString()
        val signed = if (amountInMinor < 0) "-" else ""

        return when (settings.amountDisplayStyle) {
            AmountDisplayStyle.SYMBOL_BEFORE -> "${signed}${settings.currencySymbol}$absolute"
            AmountDisplayStyle.SYMBOL_AFTER -> "${signed}$absolute${settings.currencySymbol}"
        }
    }
}
