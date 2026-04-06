package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.entity.AccountEntity
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
import kotlin.test.assertEquals
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
