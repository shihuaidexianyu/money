package com.shihuaidexianyu.money.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

fun minuteTickerFlow(): Flow<Long> = flow {
    while (true) {
        val now = System.currentTimeMillis()
        emit(now)
        val delayMillis = (60_000L - (now % 60_000L)).coerceAtLeast(1L)
        delay(delayMillis)
    }
}
