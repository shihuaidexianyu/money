package com.shihuaidexianyu.money.navigation

import java.net.URLDecoder
import java.net.URLEncoder

internal object NavigationQueryCodec {
    fun encode(value: String): String {
        return URLEncoder.encode(value, UTF_8).replace("+", "%20")
    }

    fun decode(value: String): String {
        return URLDecoder.decode(value, UTF_8)
    }

    private const val UTF_8 = "UTF-8"
}
