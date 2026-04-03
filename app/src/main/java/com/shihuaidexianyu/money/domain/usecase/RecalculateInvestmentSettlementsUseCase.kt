package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.data.entity.InvestmentSettlementEntity
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.model.AccountGroupType

class RecalculateInvestmentSettlementsUseCase(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
) {
    suspend operator fun invoke(accountId: Long) {
        val account = accountRepository.getAccountById(accountId) ?: return
        if (AccountGroupType.fromValue(account.groupType) != AccountGroupType.INVESTMENT) return

        val updates = transactionRepository.queryBalanceUpdateRecordsByAccountId(accountId)
            .sortedWith(compareBy({ it.occurredAt }, { it.id }))
        transactionRepository.deleteInvestmentSettlementsByAccountId(accountId)

        updates.forEachIndexed { index, update ->
            val previousUpdate = updates.getOrNull(index - 1)
            val previousBalance = previousUpdate?.actualBalance ?: account.initialBalance
            val periodStartAt = previousUpdate?.occurredAt ?: account.createdAt
            val netTransferIn = transactionRepository.sumTransferInBetween(accountId, periodStartAt, update.occurredAt)
            val netTransferOut = transactionRepository.sumTransferOutBetween(accountId, periodStartAt, update.occurredAt)
            val pnl = update.actualBalance - previousBalance - netTransferIn + netTransferOut
            val denominator = maxOf(previousBalance + netTransferIn - netTransferOut, 1L)
            transactionRepository.insertInvestmentSettlement(
                InvestmentSettlementEntity(
                    accountId = accountId,
                    balanceUpdateRecordId = update.id,
                    previousBalance = previousBalance,
                    currentBalance = update.actualBalance,
                    netTransferIn = netTransferIn,
                    netTransferOut = netTransferOut,
                    pnl = pnl,
                    returnRate = pnl.toDouble() / denominator.toDouble(),
                    periodStartAt = periodStartAt,
                    periodEndAt = update.occurredAt,
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
    }
}

