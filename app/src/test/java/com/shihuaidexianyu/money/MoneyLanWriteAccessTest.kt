package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.lan.MoneyLanErrorCodes
import com.shihuaidexianyu.money.lan.MoneyLanProtocolException
import com.shihuaidexianyu.money.lan.MoneyLanWriteAccess
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MoneyLanWriteAccessTest {
    @Test
    fun `new connection denies writes until explicitly enabled`() = runTest {
        val access = MoneyLanWriteAccess()
        var mutations = 0
        val failure = runCatching { access.withWriteAccess { mutations++ } }.exceptionOrNull()
        assertEquals(MoneyLanErrorCodes.WRITE_DISABLED, (failure as MoneyLanProtocolException).code)
        assertEquals(0, mutations)
        access.setAllowed(true)
        access.withWriteAccess { mutations++ }
        assertEquals(1, mutations)
    }

    @Test
    fun `revocation waits for admitted operation but rejects queued operation`() = runTest {
        val access = MoneyLanWriteAccess(initiallyAllowed = true)
        val finishTransaction = CompletableDeferred<Unit>()
        var mutations = 0
        val active = async { access.withWriteAccess { finishTransaction.await(); mutations++ } }
        runCurrent()
        val queued = async { runCatching { access.withWriteAccess { mutations++ } } }
        runCurrent()
        val revoke = async { access.setAllowed(false) }
        runCurrent()
        assertFalse(access.isAllowed)
        assertFalse(revoke.isCompleted)
        finishTransaction.complete(Unit)
        active.await()
        val failure = queued.await().exceptionOrNull()
        assertEquals(MoneyLanErrorCodes.WRITE_DISABLED, (failure as MoneyLanProtocolException).code)
        revoke.await()
        assertEquals(1, mutations)
        assertFalse(access.isAllowed)
    }

    @Test
    fun `failed mutation releases permission lock and can be revoked`() = runTest {
        val access = MoneyLanWriteAccess(initiallyAllowed = true)
        assertTrue(runCatching { access.withWriteAccess { error("rollback") } }.isFailure)
        access.setAllowed(false)
        assertFalse(access.isAllowed)
    }
}
