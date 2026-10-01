package com.shihuaidexianyu.money.lan

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal val moneyLanWriteActions = setOf(
    "journal.undo_latest", "cashflow.create", "cashflow.update", "cashflow.delete",
    "transfer.create", "transfer.update", "transfer.delete", "sync.push",
)

/** Revocation rejects queued writes immediately and waits for the admitted transaction to finish. */
class MoneyLanWriteAccess(initiallyAllowed: Boolean = false) {
    private val allowed = AtomicBoolean(initiallyAllowed)
    private val mutationMutex = Mutex()
    val isAllowed: Boolean get() = allowed.get()

    suspend fun setAllowed(enabled: Boolean) {
        if (!enabled) allowed.set(false)
        mutationMutex.withLock { allowed.set(enabled) }
    }

    suspend fun <T> withWriteAccess(block: suspend () -> T): T = mutationMutex.withLock {
        if (!allowed.get()) {
            throw MoneyLanProtocolException(MoneyLanErrorCodes.WRITE_DISABLED, "手机当前未允许 AI 修改账目")
        }
        block()
    }
}
