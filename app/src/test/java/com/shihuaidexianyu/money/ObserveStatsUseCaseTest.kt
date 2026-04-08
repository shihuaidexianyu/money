package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.entity.AccountEntity
import com.shihuaidexianyu.money.data.entity.BalanceAdjustmentRecordEntity
import com.shihuaidexianyu.money.data.entity.CashFlowRecordEntity
import com.shihuaidexianyu.money.data.entity.InvestmentSettlementEntity
import com.shihuaidexianyu.money.data.entity.TransferRecordEntity
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.AccountGroupType
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.domain.model.HomePeriod
import com.shihuaidexianyu.money.domain.model.StatsPeriod
import com.shihuaidexianyu.money.domain.model.ThemeMode
import com.shihuaidexianyu.money.domain.repository.SettingsRepository
import com.shihuaidexianyu.money.domain.usecase.CalculateCurrentBalanceUseCase
import com.shihuaidexianyu.money.domain.usecase.ObserveStatsUseCase
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Test

class ObserveStatsUseCaseTest {
    @Test
    fun `net asset points exclude account balance before account is created`() = runBlocking {
        val zoneId = ZoneId.systemDefault()
        val nowMillis = millisAt(zoneId, 2026, 4, 20, 12, 0)
        val createdAt = millisAt(zoneId, 2026, 4, 10, 9, 0)

        val accountRepository = InMemoryAccountRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val settingsRepository = FakeSettingsRepository()
        accountRepository.createAccount(
            AccountEntity(
                name = "新账户",
                groupType = AccountGroupType.BANK.value,
                initialBalance = 5_000L,
                createdAt = createdAt,
            ),
        )
        val calculateCurrentBalanceUseCase = CalculateCurrentBalanceUseCase(
            accountRepository = accountRepository,
            transactionRepository = transactionRepository,
        )
        val useCase = ObserveStatsUseCase(
            accountRepository = accountRepository,
            settingsRepository = settingsRepository,
            transactionRepository = transactionRepository,
            calculateCurrentBalanceUseCase = calculateCurrentBalanceUseCase,
            nowProvider = { nowMillis },
        )

        val snapshot = useCase.invoke(flowOf(StatsPeriod.MONTH)).first()

        assertEquals(0L, snapshot.netAssetPoints.first { it.label == "9" }.totalBalance)
        assertEquals(5_000L, snapshot.netAssetPoints.first { it.label == "10" }.totalBalance)
    }

    @Test
    fun `overview cash flow excludes transfers and manual adjustments`() = runBlocking {
        val zoneId = ZoneId.systemDefault()
        val nowMillis = millisAt(zoneId, 2026, 4, 20, 12, 0)
        val accountRepository = InMemoryAccountRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val settingsRepository = FakeSettingsRepository()

        val accountOneId = accountRepository.createAccount(
            AccountEntity(
                name = "银行卡",
                groupType = AccountGroupType.BANK.value,
                initialBalance = 10_000L,
                createdAt = millisAt(zoneId, 2026, 4, 1, 8, 0),
            ),
        )
        val accountTwoId = accountRepository.createAccount(
            AccountEntity(
                name = "备用金",
                groupType = AccountGroupType.BANK.value,
                initialBalance = 2_000L,
                createdAt = millisAt(zoneId, 2026, 4, 1, 8, 30),
            ),
        )

        transactionRepository.insertCashFlowRecord(
            CashFlowRecordEntity(
                accountId = accountOneId,
                direction = "inflow",
                amount = 3_000L,
                purpose = "工资",
                occurredAt = millisAt(zoneId, 2026, 4, 5, 10, 0),
                createdAt = nowMillis,
                updatedAt = nowMillis,
            ),
        )
        transactionRepository.insertCashFlowRecord(
            CashFlowRecordEntity(
                accountId = accountOneId,
                direction = "outflow",
                amount = 1_200L,
                purpose = "消费",
                occurredAt = millisAt(zoneId, 2026, 4, 6, 10, 0),
                createdAt = nowMillis,
                updatedAt = nowMillis,
            ),
        )
        transactionRepository.insertTransferRecord(
            TransferRecordEntity(
                fromAccountId = accountOneId,
                toAccountId = accountTwoId,
                amount = 700L,
                note = "调拨",
                occurredAt = millisAt(zoneId, 2026, 4, 7, 11, 0),
                createdAt = nowMillis,
                updatedAt = nowMillis,
            ),
        )
        transactionRepository.insertBalanceAdjustmentRecord(
            BalanceAdjustmentRecordEntity(
                accountId = accountOneId,
                delta = 400L,
                sourceUpdateRecordId = 0L,
                occurredAt = millisAt(zoneId, 2026, 4, 8, 9, 0),
                createdAt = nowMillis,
            ),
        )

        val useCase = ObserveStatsUseCase(
            accountRepository = accountRepository,
            settingsRepository = settingsRepository,
            transactionRepository = transactionRepository,
            calculateCurrentBalanceUseCase = CalculateCurrentBalanceUseCase(
                accountRepository = accountRepository,
                transactionRepository = transactionRepository,
            ),
            nowProvider = { nowMillis },
        )

        val snapshot = useCase.invoke(flowOf(StatsPeriod.MONTH)).first()

        assertEquals(3_000L, snapshot.overview.totalInflow)
        assertEquals(1_200L, snapshot.overview.totalOutflow)
        assertEquals(1_800L, snapshot.overview.netCashFlow)
        assertEquals(14_200L, snapshot.overview.currentNetAssets)
    }

    @Test
    fun `investment points aggregate settlements inside the same interval`() = runBlocking {
        val zoneId = ZoneId.systemDefault()
        val nowMillis = millisAt(zoneId, 2026, 4, 20, 12, 0)
        val accountRepository = InMemoryAccountRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val settingsRepository = FakeSettingsRepository()

        val investmentAccountId = accountRepository.createAccount(
            AccountEntity(
                name = "基金账户",
                groupType = AccountGroupType.INVESTMENT.value,
                initialBalance = 100_000L,
                createdAt = millisAt(zoneId, 2026, 1, 1, 8, 0),
            ),
        )

        transactionRepository.insertInvestmentSettlement(
            InvestmentSettlementEntity(
                accountId = investmentAccountId,
                balanceUpdateRecordId = 1L,
                previousBalance = 100_000L,
                currentBalance = 100_500L,
                netTransferIn = 0L,
                netTransferOut = 0L,
                pnl = 500L,
                returnRate = 0.005,
                periodStartAt = millisAt(zoneId, 2026, 1, 1, 8, 0),
                periodEndAt = millisAt(zoneId, 2026, 1, 10, 20, 0),
                createdAt = nowMillis,
            ),
        )
        transactionRepository.insertInvestmentSettlement(
            InvestmentSettlementEntity(
                accountId = investmentAccountId,
                balanceUpdateRecordId = 2L,
                previousBalance = 100_500L,
                currentBalance = 101_300L,
                netTransferIn = 1_000L,
                netTransferOut = 0L,
                pnl = -200L,
                returnRate = -0.0019704433497536944,
                periodStartAt = millisAt(zoneId, 2026, 1, 10, 20, 0),
                periodEndAt = millisAt(zoneId, 2026, 1, 25, 20, 0),
                createdAt = nowMillis,
            ),
        )
        transactionRepository.insertInvestmentSettlement(
            InvestmentSettlementEntity(
                accountId = investmentAccountId,
                balanceUpdateRecordId = 3L,
                previousBalance = 100_300L,
                currentBalance = 100_600L,
                netTransferIn = 0L,
                netTransferOut = 500L,
                pnl = 300L,
                returnRate = 0.003006012024048096,
                periodStartAt = millisAt(zoneId, 2026, 1, 25, 20, 0),
                periodEndAt = millisAt(zoneId, 2026, 2, 12, 20, 0),
                createdAt = nowMillis,
            ),
        )

        val useCase = ObserveStatsUseCase(
            accountRepository = accountRepository,
            settingsRepository = settingsRepository,
            transactionRepository = transactionRepository,
            calculateCurrentBalanceUseCase = CalculateCurrentBalanceUseCase(
                accountRepository = accountRepository,
                transactionRepository = transactionRepository,
            ),
            nowProvider = { nowMillis },
        )

        val snapshot = useCase.invoke(flowOf(StatsPeriod.YEAR)).first()
        val januaryPoint = snapshot.investmentPoints.first { it.label == "1月" }
        val februaryPoint = snapshot.investmentPoints.first { it.label == "2月" }

        assertEquals(600L, snapshot.investmentOverview.totalPnl)
        assertEquals(300L, januaryPoint.pnl)
        assertEquals(300L, februaryPoint.pnl)
        assertTrue(abs(januaryPoint.returnRate - (300.0 / 201_500.0)) < 0.0000001)
        assertTrue(abs(februaryPoint.returnRate - (300.0 / 99_800.0)) < 0.0000001)
    }

    private fun millisAt(
        zoneId: ZoneId,
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
    ): Long {
        return LocalDateTime.of(year, month, day, hour, minute)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
    }

    private class FakeSettingsRepository : SettingsRepository {
        private val state = MutableStateFlow(AppSettings())

        override fun observeSettings(): Flow<AppSettings> = state.asStateFlow()

        override suspend fun updateHomePeriod(period: HomePeriod) {
            state.value = state.value.copy(homePeriod = period)
        }

        override suspend fun updateCurrencySymbol(symbol: String) {
            state.value = state.value.copy(currencySymbol = symbol)
        }

        override suspend fun updateShowStaleMark(show: Boolean) {
            state.value = state.value.copy(showStaleMark = show)
        }

        override suspend fun updateThemeMode(themeMode: ThemeMode) {
            state.value = state.value.copy(themeMode = themeMode)
        }

        override suspend fun updateAccountGroupOrder(order: List<AccountGroupType>) {
            state.value = state.value.copy(accountGroupOrder = order)
        }
    }
}
