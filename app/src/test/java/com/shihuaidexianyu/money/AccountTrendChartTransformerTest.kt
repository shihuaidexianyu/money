package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.entity.AccountEntity
import com.shihuaidexianyu.money.data.entity.BalanceUpdateRecordEntity
import com.shihuaidexianyu.money.data.entity.InvestmentSettlementEntity
import com.shihuaidexianyu.money.ui.accounts.AccountTrendChartTransformer
import com.shihuaidexianyu.money.ui.accounts.AccountTrendMarkerType
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class AccountTrendChartTransformerTest {
    @Test
    fun `investment chart marks settlement points and appends current balance`() {
        val account = AccountEntity(
            id = 1,
            name = "证券账户",
            groupType = "investment",
            initialBalance = 100_000,
            createdAt = 1_000,
        )
        val updates = listOf(
            BalanceUpdateRecordEntity(
                id = 11,
                accountId = 1,
                actualBalance = 110_000,
                systemBalanceBeforeUpdate = 108_000,
                delta = 2_000,
                occurredAt = 2_000,
                createdAt = 2_000,
            ),
            BalanceUpdateRecordEntity(
                id = 12,
                accountId = 1,
                actualBalance = 125_000,
                systemBalanceBeforeUpdate = 122_000,
                delta = 3_000,
                occurredAt = 3_000,
                createdAt = 3_000,
            ),
        )
        val settlements = listOf(
            InvestmentSettlementEntity(
                id = 21,
                accountId = 1,
                balanceUpdateRecordId = 11,
                previousBalance = 100_000,
                currentBalance = 110_000,
                netTransferIn = 5_000,
                netTransferOut = 0,
                pnl = 5_000,
                returnRate = 0.05,
                periodStartAt = 1_000,
                periodEndAt = 2_000,
                createdAt = 2_000,
            ),
        )

        val chart = AccountTrendChartTransformer.build(
            account = account,
            currentBalance = 130_000,
            balanceUpdates = updates,
            settlements = settlements,
            nowMillis = 5_000,
        )

        assertEquals(4, chart.points.size)
        assertEquals(AccountTrendMarkerType.INITIAL, chart.points[0].markerType)
        assertEquals(AccountTrendMarkerType.SETTLEMENT, chart.points[1].markerType)
        assertEquals(AccountTrendMarkerType.UPDATE, chart.points[2].markerType)
        assertEquals(AccountTrendMarkerType.CURRENT, chart.points[3].markerType)
        assertEquals(5_000, chart.endAt)
        assertTrue(chart.paddedMaxBalance > chart.rawMaxBalance)
        assertTrue(chart.paddedMinBalance < chart.rawMinBalance)
    }

    @Test
    fun `flat balances still get expanded y axis`() {
        val account = AccountEntity(
            id = 1,
            name = "现金",
            groupType = "payment",
            initialBalance = 10_000,
            createdAt = 1_000,
        )

        val chart = AccountTrendChartTransformer.build(
            account = account,
            currentBalance = 10_000,
            balanceUpdates = emptyList(),
            settlements = emptyList(),
            nowMillis = 2_000,
        )

        assertEquals(10_000, chart.rawMinBalance)
        assertEquals(10_000, chart.rawMaxBalance)
        assertEquals(9_500.0, chart.paddedMinBalance)
        assertEquals(10_500.0, chart.paddedMaxBalance)
    }
}
