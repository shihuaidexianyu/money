package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.util.AmountFormatter
import kotlin.test.assertEquals
import org.junit.Test

class AmountFormatterTest {
    @Test
    fun `formats long min value without duplicated minus sign`() {
        assertEquals(
            "-¥92233720368547758.08",
            AmountFormatter.format(Long.MIN_VALUE, AppSettings()),
        )
    }
}

