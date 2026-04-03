package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.data.entity.BalanceUpdateRecordEntity
import com.shihuaidexianyu.money.data.entity.InvestmentSettlementEntity
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.model.AccountGroupType

data class InvestmentSettlementSummary(
    val previousBalance: Long,
    val currentBalance: Long,
    val netTransferIn: Long,
    val netTransferOut: Long,
    val pnl: Long,
    val returnRate: Double,
    val periodStartAt: Long,
    val periodEndAt: Long,
)

data class UpdateBalanceResult(
    val accountId: Long,
    val accountName: String,
    val systemBalanceBeforeUpdate: Long,
    val actualBalance: Long,
    val delta: Long,
    val settlementSummary: InvestmentSettlementSummary? = null,
)

class UpdateBalanceUseCase(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val calculateCurrentBalanceUseCase: CalculateCurrentBalanceUseCase,
) {
    suspend operator fun invoke(
        accountId: Long,
        actualBalance: Long,
        occurredAt: Long,
    ): UpdateBalanceResult {
        require(occurredAt <= System.currentTimeMillis()) { "时间不能晚于当前时间" }

        val account = requireNotNull(accountRepository.getAccountById(accountId))
        val previousUpdate = transactionRepository.queryBalanceUpdateRecordsByAccountId(accountId)
            .filter { it.occurredAt <= occurredAt }
            .maxWithOrNull(compareBy({ it.occurredAt }, { it.id }))

        val systemBalanceBeforeUpdate = calculateCurrentBalanceUseCase(accountId, occurredAt)
        val delta = actualBalance - systemBalanceBeforeUpdate
        val now = System.currentTimeMillis()

        val updateRecordId = transactionRepository.insertBalanceUpdateRecord(
            BalanceUpdateRecordEntity(
                accountId = accountId,
                actualBalance = actualBalance,
                systemBalanceBeforeUpdate = systemBalanceBeforeUpdate,
                delta = delta,
                occurredAt = occurredAt,
                createdAt = now,
            ),
        )

        val settlementSummary = if (AccountGroupType.fromValue(account.groupType) == AccountGroupType.INVESTMENT) {
            val previousBalance = previousUpdate?.actualBalance ?: account.initialBalance
            val periodStartAt = previousUpdate?.occurredAt ?: account.createdAt
            val netTransferIn = transactionRepository.sumTransferInBetween(accountId, periodStartAt, occurredAt)
            val netTransferOut = transactionRepository.sumTransferOutBetween(accountId, periodStartAt, occurredAt)
            val pnl = actualBalance - previousBalance - netTransferIn + netTransferOut
            val denominator = maxOf(previousBalance + netTransferIn - netTransferOut, 1L)
            val returnRate = pnl.toDouble() / denominator.toDouble()

            transactionRepository.insertInvestmentSettlement(
                InvestmentSettlementEntity(
                    accountId = accountId,
                    balanceUpdateRecordId = updateRecordId,
                    previousBalance = previousBalance,
                    currentBalance = actualBalance,
                    netTransferIn = netTransferIn,
                    netTransferOut = netTransferOut,
                    pnl = pnl,
                    returnRate = returnRate,
                    periodStartAt = periodStartAt,
                    periodEndAt = occurredAt,
                    createdAt = now,
                ),
            )

            InvestmentSettlementSummary(
                previousBalance = previousBalance,
                currentBalance = actualBalance,
                netTransferIn = netTransferIn,
                netTransferOut = netTransferOut,
                pnl = pnl,
                returnRate = returnRate,
                periodStartAt = periodStartAt,
                periodEndAt = occurredAt,
            )
        } else {
            null
        }

        accountRepository.updateLastBalanceUpdateAt(accountId, occurredAt)
        val lastUsedAt = account.lastUsedAt ?: Long.MIN_VALUE
        if (occurredAt > lastUsedAt) {
            accountRepository.updateLastUsedAt(accountId, occurredAt)
        }

        return UpdateBalanceResult(
            accountId = accountId,
            accountName = account.name,
            systemBalanceBeforeUpdate = systemBalanceBeforeUpdate,
            actualBalance = actualBalance,
            delta = delta,
            settlementSummary = settlementSummary,
        )
    }
}

