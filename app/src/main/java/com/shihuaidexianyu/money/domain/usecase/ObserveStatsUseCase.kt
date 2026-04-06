package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.data.entity.AccountEntity
import com.shihuaidexianyu.money.data.entity.BalanceAdjustmentRecordEntity
import com.shihuaidexianyu.money.data.entity.BalanceUpdateRecordEntity
import com.shihuaidexianyu.money.data.entity.CashFlowRecordEntity
import com.shihuaidexianyu.money.data.entity.InvestmentSettlementEntity
import com.shihuaidexianyu.money.data.entity.TransferRecordEntity
import com.shihuaidexianyu.money.domain.model.AccountGroupType
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.domain.model.StatsPeriod
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.SettingsRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.util.TimeRange
import com.shihuaidexianyu.money.util.TimeRangeUtils
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

data class CashFlowBar(
    val label: String,
    val inflow: Long,
    val outflow: Long,
)

data class NetAssetPoint(
    val label: String,
    val timestamp: Long,
    val totalBalance: Long,
)

data class AccountShare(
    val accountName: String,
    val groupType: AccountGroupType,
    val balance: Long,
)

data class InvestmentPoint(
    val label: String,
    val timestamp: Long,
    val pnl: Long,
    val returnRate: Double,
)

data class StatsSnapshot(
    val settings: AppSettings,
    val period: StatsPeriod,
    val cashFlowBars: List<CashFlowBar>,
    val netAssetPoints: List<NetAssetPoint>,
    val accountShares: List<AccountShare>,
    val investmentPoints: List<InvestmentPoint>,
)

class ObserveStatsUseCase(
    private val accountRepository: AccountRepository,
    private val settingsRepository: SettingsRepository,
    private val transactionRepository: TransactionRepository,
    private val calculateCurrentBalanceUseCase: CalculateCurrentBalanceUseCase,
    private val nowProvider: () -> Long = System::currentTimeMillis,
) {
    @Suppress("OPT_IN_USAGE")
    fun invoke(periodFlow: Flow<StatsPeriod>): Flow<StatsSnapshot> {
        return combine(
            periodFlow,
            settingsRepository.observeSettings(),
            transactionRepository.observeChangeVersion(),
        ) { period, settings, _ -> period to settings }
            .flatMapLatest { (period, settings) ->
                flow { emit(buildSnapshot(period, settings)) }
            }
    }

    private suspend fun buildSnapshot(
        period: StatsPeriod,
        settings: AppSettings,
    ): StatsSnapshot = coroutineScope {
        val now = nowProvider()
        val subPeriods = TimeRangeUtils.splitIntoPeriods(period, nowMillis = now)
        val overallRange = TimeRangeUtils.currentStatsRange(period, nowMillis = now)
        val statsData = loadStatsData(overallRange)

        val cashFlowBarsJob = async { buildCashFlowBars(subPeriods, statsData.cashFlowsInRange) }
        val netAssetJob = async { buildNetAssetPoints(subPeriods, statsData) }
        val accountSharesJob = async { buildAccountShares(statsData) }
        val investmentJob = async { buildInvestmentPoints(statsData) }

        StatsSnapshot(
            settings = settings,
            period = period,
            cashFlowBars = cashFlowBarsJob.await(),
            netAssetPoints = netAssetJob.await(),
            accountShares = accountSharesJob.await(),
            investmentPoints = investmentJob.await(),
        )
    }

    private suspend fun loadStatsData(
        overallRange: TimeRange,
    ): StatsData = coroutineScope {
        val accounts = accountRepository.queryActiveAccounts()
        val rangeStart = overallRange.startAtMillis
        val rangeEnd = overallRange.endAtMillis

        val cashFlowsJob = async {
            transactionRepository.queryActiveCashFlowRecordsBetween(rangeStart, rangeEnd)
        }
        val transfersJob = async {
            transactionRepository.queryActiveTransferRecordsBetween(rangeStart, rangeEnd)
        }
        val adjustmentsJob = async {
            transactionRepository.queryManualBalanceAdjustmentRecordsBetween(rangeStart, rangeEnd)
        }
        val balanceUpdatesJob = async {
            transactionRepository.queryBalanceUpdateRecordsBetween(rangeStart, rangeEnd)
        }
        val settlementsJob = async { transactionRepository.queryInvestmentSettlementsBetween(rangeStart, rangeEnd) }
        val currentBalancesJob = async {
            accounts.associate { account -> account.id to calculateCurrentBalanceUseCase(account.id) }
        }
        val rangeStartBalancesJob = async {
            accounts.associate { account ->
                val balance = if (rangeStart <= account.createdAt) {
                    0L
                } else {
                    calculateCurrentBalanceUseCase(account.id, rangeStart - 1L)
                }
                account.id to balance
            }
        }

        StatsData(
            overallRange = overallRange,
            accounts = accounts,
            cashFlowsInRange = cashFlowsJob.await(),
            transfersInRange = transfersJob.await(),
            manualAdjustmentsInRange = adjustmentsJob.await(),
            balanceUpdatesInRange = balanceUpdatesJob.await(),
            allInvestmentSettlements = settlementsJob.await(),
            currentBalances = currentBalancesJob.await(),
            rangeStartBalances = rangeStartBalancesJob.await(),
        )
    }

    private fun buildCashFlowBars(
        subPeriods: List<TimeRangeUtils.SubPeriod>,
        cashFlowsInRange: List<CashFlowRecordEntity>,
    ): List<CashFlowBar> {
        val inflowTotals = LongArray(subPeriods.size)
        val outflowTotals = LongArray(subPeriods.size)

        cashFlowsInRange.forEach { record ->
            val index = subPeriods.indexOfFirst { record.occurredAt in it.range.startAtMillis..it.range.endAtMillis }
            if (index < 0) return@forEach
            when (record.direction) {
                "inflow" -> inflowTotals[index] += record.amount
                "outflow" -> outflowTotals[index] += record.amount
            }
        }

        return subPeriods.mapIndexed { index, subPeriod ->
            CashFlowBar(
                label = subPeriod.label,
                inflow = inflowTotals[index],
                outflow = outflowTotals[index],
            )
        }
    }

    private fun buildNetAssetPoints(
        subPeriods: List<TimeRangeUtils.SubPeriod>,
        statsData: StatsData,
    ): List<NetAssetPoint> {
        if (statsData.accounts.isEmpty()) return emptyList()

        val eventsByAccount = buildBalanceEventsByAccount(statsData)
        val runningBalances = statsData.rangeStartBalances.toMutableMap()
        val pointers = statsData.accounts.associate { it.id to 0 }.toMutableMap()

        return subPeriods.map { subPeriod ->
            var total = 0L
            for (account in statsData.accounts) {
                val events = eventsByAccount[account.id].orEmpty()
                var pointer = pointers[account.id] ?: 0
                var balance = runningBalances[account.id] ?: 0L
                while (pointer < events.size && events[pointer].occurredAt <= subPeriod.range.endAtMillis) {
                    balance = events[pointer].applyTo(balance)
                    pointer += 1
                }
                pointers[account.id] = pointer
                runningBalances[account.id] = balance
                total += balance
            }

            NetAssetPoint(
                label = subPeriod.label,
                timestamp = subPeriod.range.endAtMillis,
                totalBalance = total,
            )
        }
    }

    private fun buildAccountShares(
        statsData: StatsData,
    ): List<AccountShare> {
        return statsData.accounts.map { account ->
            val balance = statsData.currentBalances[account.id] ?: 0L
            AccountShare(
                accountName = account.name,
                groupType = AccountGroupType.fromValue(account.groupType),
                balance = balance,
            )
        }.filter { it.balance != 0L }
    }

    private fun buildInvestmentPoints(
        statsData: StatsData,
    ): List<InvestmentPoint> {
        val investmentAccountIds = statsData.accounts
            .filter { AccountGroupType.fromValue(it.groupType) == AccountGroupType.INVESTMENT }
            .map { it.id }
            .toSet()
        if (investmentAccountIds.isEmpty()) return emptyList()

        return statsData.allInvestmentSettlements
            .filter { it.accountId in investmentAccountIds }
            .sortedBy { it.periodEndAt }
            .map { settlement ->
                InvestmentPoint(
                    label = java.time.Instant.ofEpochMilli(settlement.periodEndAt)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDate()
                        .let { "${it.monthValue}/${it.dayOfMonth}" },
                    timestamp = settlement.periodEndAt,
                    pnl = settlement.pnl,
                    returnRate = settlement.returnRate,
                )
            }
    }

    private fun buildBalanceEventsByAccount(
        statsData: StatsData,
    ): Map<Long, List<BalanceEvent>> {
        val eventsByAccount = mutableMapOf<Long, MutableList<BalanceEvent>>()

        fun addEvent(accountId: Long, event: BalanceEvent) {
            eventsByAccount.getOrPut(accountId) { mutableListOf() }.add(event)
        }

        statsData.accounts.forEach { account ->
            if (account.createdAt in statsData.overallRange.startAtMillis..statsData.overallRange.endAtMillis) {
                addEvent(
                    account.id,
                    BalanceEvent(
                        occurredAt = account.createdAt,
                        orderKey = -1_000_000L,
                        effect = BalanceEffect.Add(account.initialBalance),
                    ),
                )
            }
        }

        statsData.cashFlowsInRange.forEach { record ->
            addEvent(
                record.accountId,
                BalanceEvent(
                    occurredAt = record.occurredAt,
                    orderKey = 0L,
                    effect = if (record.direction == "inflow") {
                        BalanceEffect.Add(record.amount)
                    } else {
                        BalanceEffect.Add(-record.amount)
                    },
                ),
            )
        }
        statsData.transfersInRange.forEach { record ->
            addEvent(
                record.fromAccountId,
                BalanceEvent(
                    occurredAt = record.occurredAt,
                    orderKey = 0L,
                    effect = BalanceEffect.Add(-record.amount),
                ),
            )
            addEvent(
                record.toAccountId,
                BalanceEvent(
                    occurredAt = record.occurredAt,
                    orderKey = 0L,
                    effect = BalanceEffect.Add(record.amount),
                ),
            )
        }
        statsData.manualAdjustmentsInRange.forEach { record ->
            addEvent(
                record.accountId,
                BalanceEvent(
                    occurredAt = record.occurredAt,
                    orderKey = 0L,
                    effect = BalanceEffect.Add(record.delta),
                ),
            )
        }
        statsData.balanceUpdatesInRange.forEach { record ->
            addEvent(
                record.accountId,
                BalanceEvent(
                    occurredAt = record.occurredAt,
                    orderKey = 1_000_000L + record.id,
                    effect = BalanceEffect.Reset(record.actualBalance),
                ),
            )
        }

        return eventsByAccount.mapValues { (_, events) ->
            events.sortedWith(compareBy<BalanceEvent> { it.occurredAt }.thenBy { it.orderKey })
        }
    }
}

private data class StatsData(
    val overallRange: TimeRange,
    val accounts: List<AccountEntity>,
    val cashFlowsInRange: List<CashFlowRecordEntity>,
    val transfersInRange: List<TransferRecordEntity>,
    val manualAdjustmentsInRange: List<BalanceAdjustmentRecordEntity>,
    val balanceUpdatesInRange: List<BalanceUpdateRecordEntity>,
    val allInvestmentSettlements: List<InvestmentSettlementEntity>,
    val currentBalances: Map<Long, Long>,
    val rangeStartBalances: Map<Long, Long>,
)

private data class BalanceEvent(
    val occurredAt: Long,
    val orderKey: Long,
    val effect: BalanceEffect,
) {
    fun applyTo(balance: Long): Long = when (effect) {
        is BalanceEffect.Add -> balance + effect.delta
        is BalanceEffect.Reset -> effect.value
    }
}

private sealed interface BalanceEffect {
    data class Add(val delta: Long) : BalanceEffect
    data class Reset(val value: Long) : BalanceEffect
}
