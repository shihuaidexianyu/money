package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.data.repository.AccountRepository
import com.shihuaidexianyu.money.data.repository.TransactionRepository
import com.shihuaidexianyu.money.util.DateTimeTextFormatter

class CalculateCurrentBalanceUseCase(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
) {
    suspend operator fun invoke(
        accountId: Long,
        atTimeMillis: Long = Long.MAX_VALUE,
    ): Long {
        val account = requireNotNull(accountRepository.getAccountById(accountId))
        val latestUpdate = transactionRepository.getLatestBalanceUpdateAtOrBefore(accountId, atTimeMillis)

        val anchorBalance = latestUpdate?.actualBalance ?: account.initialBalance
        val anchorTime = latestUpdate?.occurredAt
            ?: DateTimeTextFormatter.floorToMinute(account.createdAt) - 1L

        val inflow = transactionRepository.sumInflowBetween(accountId, anchorTime, atTimeMillis)
        val outflow = transactionRepository.sumOutflowBetween(accountId, anchorTime, atTimeMillis)
        val transferIn = transactionRepository.sumTransferInBetween(accountId, anchorTime, atTimeMillis)
        val transferOut = transactionRepository.sumTransferOutBetween(accountId, anchorTime, atTimeMillis)
        val adjustment = transactionRepository.sumAdjustmentBetween(accountId, anchorTime, atTimeMillis)

        return anchorBalance + inflow - outflow + transferIn - transferOut + adjustment
    }
}
