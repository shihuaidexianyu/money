package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.navigation.NavigationQueryCodec
import kotlin.test.assertEquals
import org.junit.Test

class NavigationQueryCodecTest {
    @Test
    fun `query codec round trips chinese spaces and reserved characters`() {
        val values = listOf(
            "房租",
            "房租 & 水电?#100%",
            "a b",
            "空 格 & ? # %",
        )

        values.forEach { value ->
            assertEquals(value, NavigationQueryCodec.decode(NavigationQueryCodec.encode(value)))
        }
        assertEquals("a%20b", NavigationQueryCodec.encode("a b"))
    }
}
