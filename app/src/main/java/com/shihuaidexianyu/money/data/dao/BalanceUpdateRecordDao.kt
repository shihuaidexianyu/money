package com.shihuaidexianyu.money.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.shihuaidexianyu.money.data.entity.BalanceUpdateRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BalanceUpdateRecordDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: BalanceUpdateRecordEntity): Long

    @Update
    suspend fun update(record: BalanceUpdateRecordEntity)

    @Query("SELECT * FROM balance_update_records WHERE id = :id LIMIT 1")
    suspend fun queryById(id: Long): BalanceUpdateRecordEntity?

    @Query("SELECT * FROM balance_update_records ORDER BY occurredAt DESC, id DESC")
    suspend fun queryAllActive(): List<BalanceUpdateRecordEntity>

    @Query("SELECT * FROM balance_update_records ORDER BY occurredAt DESC, id DESC")
    fun observeAllActive(): Flow<List<BalanceUpdateRecordEntity>>

    @Query("SELECT COUNT(*) FROM balance_update_records")
    fun observeCount(): Flow<Int>

    @Query(
        """
        SELECT * FROM balance_update_records
        WHERE accountId = :accountId
        ORDER BY occurredAt DESC, id DESC
        """,
    )
    suspend fun queryByAccountId(accountId: Long): List<BalanceUpdateRecordEntity>

    @Query(
        """
        SELECT * FROM balance_update_records
        WHERE accountId = :accountId AND occurredAt <= :occurredAt
        ORDER BY occurredAt DESC, id DESC
        LIMIT 1
        """,
    )
    suspend fun getLatestForAccountAtOrBefore(
        accountId: Long,
        occurredAt: Long,
    ): BalanceUpdateRecordEntity?

    @Query(
        """
        SELECT * FROM balance_update_records
        WHERE accountId = :accountId
        ORDER BY occurredAt DESC, id DESC
        LIMIT 1
        """,
    )
    suspend fun getLatestForAccount(accountId: Long): BalanceUpdateRecordEntity?
}

