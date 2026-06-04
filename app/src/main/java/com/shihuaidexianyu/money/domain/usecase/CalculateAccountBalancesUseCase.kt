package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.BalanceAdjustmentRecord
import com.shihuaidexianyu.money.domain.model.BalanceUpdateRecord
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.model.TransferRecord
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.util.DateTimeTextFormatter

class CalculateAccountBalancesUseCase(
    private val transactionRepository: TransactionRepository,
) {
    suspend operator fun invoke(
        accounts: List<Account>,
        atTimeMillis: Long = Long.MAX_VALUE,
    ): Map<Long, Long> {
        if (accounts.isEmpty()) return emptyMap()

        val accountIds = accounts.map(Account::id).toSet()
        val updatesByAccount = transactionRepository.queryAllBalanceUpdateRecords()
            .asSequence()
            .filter { it.accountId in accountIds && it.occurredAt <= atTimeMillis }
            .groupBy(BalanceUpdateRecord::accountId)
        val cashFlows = transactionRepository.queryAllActiveCashFlowRecords()
            .filter { it.accountId in accountIds && it.occurredAt <= atTimeMillis }
        val transfers = transactionRepository.queryAllActiveTransferRecords()
            .filter {
                (it.fromAccountId in accountIds || it.toAccountId in accountIds) &&
                    it.occurredAt <= atTimeMillis
            }
        val adjustments = transactionRepository.queryAllBalanceAdjustmentRecords()
            .filter {
                it.accountId in accountIds &&
                    it.sourceUpdateRecordId == 0L &&
                    it.occurredAt <= atTimeMillis
            }

        return accounts.associate { account ->
            account.id to calculateAccountBalance(
                account = account,
                updates = updatesByAccount[account.id].orEmpty(),
                cashFlows = cashFlows,
                transfers = transfers,
                adjustments = adjustments,
                atTimeMillis = atTimeMillis,
            )
        }
    }

    private fun calculateAccountBalance(
        account: Account,
        updates: List<BalanceUpdateRecord>,
        cashFlows: List<CashFlowRecord>,
        transfers: List<TransferRecord>,
        adjustments: List<BalanceAdjustmentRecord>,
        atTimeMillis: Long,
    ): Long {
        val latestUpdate = updates.maxWithOrNull(
            compareBy<BalanceUpdateRecord> { it.occurredAt }.thenBy { it.id },
        )
        val anchorBalance = latestUpdate?.actualBalance ?: account.initialBalance
        val anchorTime = latestUpdate?.occurredAt
            ?: (DateTimeTextFormatter.floorToMinute(account.createdAt) - 1L).coerceAtLeast(-1L)

        val inflow = cashFlows
            .filter {
                it.accountId == account.id &&
                    it.direction == CashFlowDirection.INFLOW.value &&
                    it.occurredAt > anchorTime
            }
            .sumOf(CashFlowRecord::amount)
        val outflow = cashFlows
            .filter {
                it.accountId == account.id &&
                    it.direction == CashFlowDirection.OUTFLOW.value &&
                    it.occurredAt > anchorTime
            }
            .sumOf(CashFlowRecord::amount)
        val transferIn = transfers
            .filter { it.toAccountId == account.id && it.occurredAt > anchorTime }
            .sumOf(TransferRecord::amount)
        val transferOut = transfers
            .filter { it.fromAccountId == account.id && it.occurredAt > anchorTime }
            .sumOf(TransferRecord::amount)
        val adjustment = adjustments
            .filter { it.accountId == account.id && it.occurredAt > anchorTime }
            .sumOf(BalanceAdjustmentRecord::delta)

        return anchorBalance + inflow - outflow + transferIn - transferOut + adjustment
    }
}
