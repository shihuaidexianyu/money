package com.shihuaidexianyu.money.data.repository

import com.shihuaidexianyu.money.data.entity.BalanceAdjustmentRecordEntity
import com.shihuaidexianyu.money.data.entity.BalanceUpdateRecordEntity
import com.shihuaidexianyu.money.data.entity.CashFlowRecordEntity
import com.shihuaidexianyu.money.data.entity.InvestmentSettlementEntity
import com.shihuaidexianyu.money.data.entity.TransferRecordEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PersistentTransactionRepository(
    private val store: PersistentMoneyStore,
) : TransactionRepository {
    override fun observeChangeVersion(): Flow<Long> {
        return store.snapshot.map { it.changeVersion }
    }

    override suspend fun insertCashFlowRecord(record: CashFlowRecordEntity): Long {
        val updated = store.update { snapshot ->
            val id = snapshot.nextCashFlowId
            snapshot.copy(
                nextCashFlowId = id + 1,
                changeVersion = snapshot.changeVersion + 1,
                cashFlowRecords = snapshot.cashFlowRecords + record.copy(id = id),
            )
        }
        return updated.nextCashFlowId - 1
    }

    override suspend fun updateCashFlowRecord(record: CashFlowRecordEntity) {
        store.update { snapshot ->
            snapshot.copy(
                changeVersion = snapshot.changeVersion + 1,
                cashFlowRecords = snapshot.cashFlowRecords.replace(record.id, record),
            )
        }
    }

    override suspend fun softDeleteCashFlowRecord(id: Long, updatedAt: Long) {
        val existing = queryCashFlowRecordById(id) ?: return
        updateCashFlowRecord(existing.copy(isDeleted = true, updatedAt = updatedAt))
    }

    override suspend fun queryCashFlowRecordById(id: Long): CashFlowRecordEntity? {
        return store.snapshot.value.cashFlowRecords.firstOrNull { it.id == id }
    }

    override suspend fun queryAllCashFlowRecords(): List<CashFlowRecordEntity> {
        return store.snapshot.value.cashFlowRecords
    }

    override suspend fun queryAllActiveCashFlowRecords(): List<CashFlowRecordEntity> {
        return store.snapshot.value.cashFlowRecords.filterNot(CashFlowRecordEntity::isDeleted)
    }

    override suspend fun queryCashFlowRecordsByAccountId(accountId: Long): List<CashFlowRecordEntity> {
        return queryAllActiveCashFlowRecords().filter { it.accountId == accountId }
    }

    override suspend fun insertTransferRecord(record: TransferRecordEntity): Long {
        val updated = store.update { snapshot ->
            val id = snapshot.nextTransferId
            snapshot.copy(
                nextTransferId = id + 1,
                changeVersion = snapshot.changeVersion + 1,
                transferRecords = snapshot.transferRecords + record.copy(id = id),
            )
        }
        return updated.nextTransferId - 1
    }

    override suspend fun updateTransferRecord(record: TransferRecordEntity) {
        store.update { snapshot ->
            snapshot.copy(
                changeVersion = snapshot.changeVersion + 1,
                transferRecords = snapshot.transferRecords.replace(record.id, record),
            )
        }
    }

    override suspend fun softDeleteTransferRecord(id: Long, updatedAt: Long) {
        val existing = queryTransferRecordById(id) ?: return
        updateTransferRecord(existing.copy(isDeleted = true, updatedAt = updatedAt))
    }

    override suspend fun queryTransferRecordById(id: Long): TransferRecordEntity? {
        return store.snapshot.value.transferRecords.firstOrNull { it.id == id }
    }

    override suspend fun queryAllTransferRecords(): List<TransferRecordEntity> {
        return store.snapshot.value.transferRecords
    }

    override suspend fun queryAllActiveTransferRecords(): List<TransferRecordEntity> {
        return store.snapshot.value.transferRecords.filterNot(TransferRecordEntity::isDeleted)
    }

    override suspend fun queryTransferRecordsByAccountId(accountId: Long): List<TransferRecordEntity> {
        return queryAllActiveTransferRecords().filter { it.fromAccountId == accountId || it.toAccountId == accountId }
    }

    override suspend fun insertBalanceUpdateRecord(record: BalanceUpdateRecordEntity): Long {
        val updated = store.update { snapshot ->
            val id = snapshot.nextBalanceUpdateId
            snapshot.copy(
                nextBalanceUpdateId = id + 1,
                changeVersion = snapshot.changeVersion + 1,
                balanceUpdates = snapshot.balanceUpdates + record.copy(id = id),
            )
        }
        return updated.nextBalanceUpdateId - 1
    }

    override suspend fun updateBalanceUpdateRecord(record: BalanceUpdateRecordEntity) {
        store.update { snapshot ->
            snapshot.copy(
                changeVersion = snapshot.changeVersion + 1,
                balanceUpdates = snapshot.balanceUpdates.replace(record.id, record),
            )
        }
    }

    override suspend fun getBalanceUpdateRecordById(id: Long): BalanceUpdateRecordEntity? {
        return store.snapshot.value.balanceUpdates.firstOrNull { it.id == id }
    }

    override suspend fun queryAllBalanceUpdateRecords(): List<BalanceUpdateRecordEntity> {
        return store.snapshot.value.balanceUpdates
    }

    override suspend fun queryBalanceUpdateRecordsByAccountId(accountId: Long): List<BalanceUpdateRecordEntity> {
        return store.snapshot.value.balanceUpdates.filter { it.accountId == accountId }
    }

    override suspend fun getLatestBalanceUpdate(accountId: Long): BalanceUpdateRecordEntity? {
        return queryBalanceUpdateRecordsByAccountId(accountId)
            .maxWithOrNull(compareBy<BalanceUpdateRecordEntity> { it.occurredAt }.thenBy { it.id })
    }

    override suspend fun getLatestBalanceUpdateAtOrBefore(
        accountId: Long,
        occurredAt: Long,
    ): BalanceUpdateRecordEntity? {
        return queryBalanceUpdateRecordsByAccountId(accountId)
            .filter { it.occurredAt <= occurredAt }
            .maxWithOrNull(compareBy<BalanceUpdateRecordEntity> { it.occurredAt }.thenBy { it.id })
    }

    override suspend fun insertBalanceAdjustmentRecord(record: BalanceAdjustmentRecordEntity): Long {
        val updated = store.update { snapshot ->
            val id = snapshot.nextAdjustmentId
            snapshot.copy(
                nextAdjustmentId = id + 1,
                changeVersion = snapshot.changeVersion + 1,
                adjustments = snapshot.adjustments + record.copy(id = id),
            )
        }
        return updated.nextAdjustmentId - 1
    }

    override suspend fun updateBalanceAdjustmentRecord(record: BalanceAdjustmentRecordEntity) {
        store.update { snapshot ->
            snapshot.copy(
                changeVersion = snapshot.changeVersion + 1,
                adjustments = snapshot.adjustments.replace(record.id, record),
            )
        }
    }

    override suspend fun getBalanceAdjustmentRecordById(id: Long): BalanceAdjustmentRecordEntity? {
        return store.snapshot.value.adjustments.firstOrNull { it.id == id }
    }

    override suspend fun queryAllBalanceAdjustmentRecords(): List<BalanceAdjustmentRecordEntity> {
        return store.snapshot.value.adjustments
    }

    override suspend fun queryBalanceAdjustmentRecordsByAccountId(accountId: Long): List<BalanceAdjustmentRecordEntity> {
        return store.snapshot.value.adjustments.filter { it.accountId == accountId }
    }

    override suspend fun insertInvestmentSettlement(record: InvestmentSettlementEntity): Long {
        val updated = store.update { snapshot ->
            val id = snapshot.nextSettlementId
            snapshot.copy(
                nextSettlementId = id + 1,
                changeVersion = snapshot.changeVersion + 1,
                settlements = snapshot.settlements + record.copy(id = id),
            )
        }
        return updated.nextSettlementId - 1
    }

    override suspend fun updateInvestmentSettlement(record: InvestmentSettlementEntity) {
        store.update { snapshot ->
            snapshot.copy(
                changeVersion = snapshot.changeVersion + 1,
                settlements = snapshot.settlements.replace(record.id, record),
            )
        }
    }

    override suspend fun getInvestmentSettlementById(id: Long): InvestmentSettlementEntity? {
        return store.snapshot.value.settlements.firstOrNull { it.id == id }
    }

    override suspend fun queryAllInvestmentSettlements(): List<InvestmentSettlementEntity> {
        return store.snapshot.value.settlements
    }

    override suspend fun queryInvestmentSettlementsByAccountId(accountId: Long): List<InvestmentSettlementEntity> {
        return store.snapshot.value.settlements.filter { it.accountId == accountId }
    }

    override suspend fun getLatestInvestmentSettlement(accountId: Long): InvestmentSettlementEntity? {
        return queryInvestmentSettlementsByAccountId(accountId)
            .maxWithOrNull(compareBy<InvestmentSettlementEntity> { it.periodEndAt }.thenBy { it.id })
    }

    override suspend fun deleteInvestmentSettlementsByAccountId(accountId: Long) {
        store.update { snapshot ->
            snapshot.copy(
                changeVersion = snapshot.changeVersion + 1,
                settlements = snapshot.settlements.filterNot { it.accountId == accountId },
            )
        }
    }

    override suspend fun sumInflowBetween(accountId: Long, startAt: Long, endAt: Long): Long {
        return queryCashFlowRecordsByAccountId(accountId)
            .filter { it.direction == "inflow" && it.occurredAt > startAt && it.occurredAt <= endAt }
            .sumOf { it.amount }
    }

    override suspend fun sumOutflowBetween(accountId: Long, startAt: Long, endAt: Long): Long {
        return queryCashFlowRecordsByAccountId(accountId)
            .filter { it.direction == "outflow" && it.occurredAt > startAt && it.occurredAt <= endAt }
            .sumOf { it.amount }
    }

    override suspend fun sumTransferInBetween(accountId: Long, startAt: Long, endAt: Long): Long {
        return queryTransferRecordsByAccountId(accountId)
            .filter { it.toAccountId == accountId && it.occurredAt > startAt && it.occurredAt <= endAt }
            .sumOf { it.amount }
    }

    override suspend fun sumTransferOutBetween(accountId: Long, startAt: Long, endAt: Long): Long {
        return queryTransferRecordsByAccountId(accountId)
            .filter { it.fromAccountId == accountId && it.occurredAt > startAt && it.occurredAt <= endAt }
            .sumOf { it.amount }
    }

    override suspend fun sumAdjustmentBetween(accountId: Long, startAt: Long, endAt: Long): Long {
        return queryBalanceAdjustmentRecordsByAccountId(accountId)
            .filter { it.occurredAt > startAt && it.occurredAt <= endAt }
            .sumOf { it.delta }
    }
}

private fun <T> List<T>.replace(id: Long, replacement: T): List<T> {
    return map { existing ->
        val existingId = when (existing) {
            is CashFlowRecordEntity -> existing.id
            is TransferRecordEntity -> existing.id
            is BalanceUpdateRecordEntity -> existing.id
            is BalanceAdjustmentRecordEntity -> existing.id
            is InvestmentSettlementEntity -> existing.id
            else -> Long.MIN_VALUE
        }
        if (existingId == id) replacement else existing
    }
}
