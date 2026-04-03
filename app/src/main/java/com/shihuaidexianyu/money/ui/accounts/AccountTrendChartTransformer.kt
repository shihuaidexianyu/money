package com.shihuaidexianyu.money.ui.accounts

import com.shihuaidexianyu.money.data.entity.AccountEntity
import com.shihuaidexianyu.money.data.entity.BalanceUpdateRecordEntity
import com.shihuaidexianyu.money.data.entity.InvestmentSettlementEntity
import kotlin.math.abs
import kotlin.math.max

enum class AccountTrendMarkerType {
    INITIAL,
    UPDATE,
    SETTLEMENT,
    CURRENT,
}

data class AccountTrendPointUiModel(
    val timeMillis: Long,
    val balance: Long,
    val markerType: AccountTrendMarkerType,
    val label: String,
)

data class AccountTrendChartUiModel(
    val points: List<AccountTrendPointUiModel> = emptyList(),
    val rawMinBalance: Long = 0,
    val rawMaxBalance: Long = 0,
    val paddedMinBalance: Double = 0.0,
    val paddedMaxBalance: Double = 0.0,
    val startAt: Long? = null,
    val endAt: Long? = null,
) {
    val hasData: Boolean
        get() = points.isNotEmpty()
}

object AccountTrendChartTransformer {
    fun build(
        account: AccountEntity,
        currentBalance: Long,
        balanceUpdates: List<BalanceUpdateRecordEntity>,
        settlements: List<InvestmentSettlementEntity>,
        nowMillis: Long = System.currentTimeMillis(),
    ): AccountTrendChartUiModel {
        val settlementUpdateIds = settlements.map(InvestmentSettlementEntity::balanceUpdateRecordId).toSet()
        val orderedUpdates = balanceUpdates.sortedWith(
            compareBy<BalanceUpdateRecordEntity> { it.occurredAt }.thenBy { it.id },
        )

        val points = mutableListOf(
            AccountTrendPointUiModel(
                timeMillis = account.createdAt,
                balance = account.initialBalance,
                markerType = AccountTrendMarkerType.INITIAL,
                label = "初始余额",
            ),
        )

        orderedUpdates.forEach { update ->
            val markerType = if (settlementUpdateIds.contains(update.id)) {
                AccountTrendMarkerType.SETTLEMENT
            } else {
                AccountTrendMarkerType.UPDATE
            }
            points += AccountTrendPointUiModel(
                timeMillis = update.occurredAt,
                balance = update.actualBalance,
                markerType = markerType,
                label = if (markerType == AccountTrendMarkerType.SETTLEMENT) "结算点" else "更新余额",
            )
        }

        val finalTime = max(nowMillis, points.lastOrNull()?.timeMillis ?: nowMillis)
        val lastPoint = points.lastOrNull()
        if (lastPoint == null || lastPoint.timeMillis != finalTime || lastPoint.balance != currentBalance) {
            points += AccountTrendPointUiModel(
                timeMillis = finalTime,
                balance = currentBalance,
                markerType = AccountTrendMarkerType.CURRENT,
                label = "当前余额",
            )
        }

        val rawMinBalance = points.minOf(AccountTrendPointUiModel::balance)
        val rawMaxBalance = points.maxOf(AccountTrendPointUiModel::balance)
        val (paddedMinBalance, paddedMaxBalance) = buildYAxisRange(rawMinBalance, rawMaxBalance)

        return AccountTrendChartUiModel(
            points = points,
            rawMinBalance = rawMinBalance,
            rawMaxBalance = rawMaxBalance,
            paddedMinBalance = paddedMinBalance,
            paddedMaxBalance = paddedMaxBalance,
            startAt = points.firstOrNull()?.timeMillis,
            endAt = points.lastOrNull()?.timeMillis,
        )
    }

    private fun buildYAxisRange(minBalance: Long, maxBalance: Long): Pair<Double, Double> {
        if (minBalance == maxBalance) {
            val flatPadding = max(abs(minBalance).toDouble() * 0.05, 100.0)
            return minBalance - flatPadding to maxBalance + flatPadding
        }

        val span = (maxBalance - minBalance).toDouble()
        val padding = max(span * 0.05, 100.0)
        return minBalance - padding to maxBalance + padding
    }
}

