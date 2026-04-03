package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.util.AmountInputParser
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class AmountInputParserTest {
    @Test
    fun `decimal amount converts to minor units`() {
        assertEquals(1234, AmountInputParser.parseToMinor("12.34"))
    }

    @Test
    fun `negative amount is rejected`() {
        assertNull(AmountInputParser.parseToMinor("-12.34"))
    }
}

