package com.shihuaidexianyu.money.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.shihuaidexianyu.money.data.entity.CashFlowRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CashFlowRecordDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: CashFlowRecordEntity): Long

    @Update
    suspend fun update(record: CashFlowRecordEntity)

    @Query("UPDATE cash_flow_records SET isDeleted = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: Long, updatedAt: Long)

    @Query("SELECT * FROM cash_flow_records WHERE id = :id LIMIT 1")
    suspend fun queryById(id: Long): CashFlowRecordEntity?

    @Query("SELECT * FROM cash_flow_records WHERE isDeleted = 0 ORDER BY occurredAt DESC, id DESC")
    fun observeAllActive(): Flow<List<CashFlowRecordEntity>>

    @Query("SELECT * FROM cash_flow_records ORDER BY occurredAt DESC, id DESC")
    suspend fun queryAll(): List<CashFlowRecordEntity>

    @Query("SELECT * FROM cash_flow_records WHERE isDeleted = 0 ORDER BY occurredAt DESC, id DESC")
    suspend fun queryAllActive(): List<CashFlowRecordEntity>

    @Query(
        """
        SELECT * FROM cash_flow_records
        WHERE accountId = :accountId AND isDeleted = 0
        ORDER BY occurredAt DESC, id DESC
        """,
    )
    suspend fun queryByAccountId(accountId: Long): List<CashFlowRecordEntity>

    @Query(
        """
        SELECT COALESCE(SUM(amount), 0) FROM cash_flow_records
        WHERE accountId = :accountId
            AND direction = 'inflow'
            AND isDeleted = 0
            AND occurredAt > :startAt
            AND occurredAt <= :endAt
        """,
    )
    suspend fun sumInflowBetween(accountId: Long, startAt: Long, endAt: Long): Long

    @Query(
        """
        SELECT COALESCE(SUM(amount), 0) FROM cash_flow_records
        WHERE accountId = :accountId
            AND direction = 'outflow'
            AND isDeleted = 0
            AND occurredAt > :startAt
            AND occurredAt <= :endAt
        """,
    )
    suspend fun sumOutflowBetween(accountId: Long, startAt: Long, endAt: Long): Long
}
