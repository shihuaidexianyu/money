package com.shihuaidexianyu.money.util

import com.shihuaidexianyu.money.data.entity.AccountEntity
import com.shihuaidexianyu.money.domain.model.DEFAULT_BALANCE_UPDATE_REMINDER_DAYS
import java.util.concurrent.TimeUnit

object AccountStatusUtils {
    fun isStale(
        account: AccountEntity,
        reminderDays: Int = DEFAULT_BALANCE_UPDATE_REMINDER_DAYS,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        val anchor = account.lastBalanceUpdateAt ?: account.createdAt
        return nowMillis - anchor >= TimeUnit.DAYS.toMillis(reminderDays.coerceAtLeast(1).toLong())
    }

    fun staleDays(account: AccountEntity, nowMillis: Long = System.currentTimeMillis()): Long {
        val anchor = account.lastBalanceUpdateAt ?: account.createdAt
        return TimeUnit.MILLISECONDS.toDays((nowMillis - anchor).coerceAtLeast(0))
    }
}
