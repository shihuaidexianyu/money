package com.shihuaidexianyu.money.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.shihuaidexianyu.money.data.entity.InvestmentSettlementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InvestmentSettlementDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: InvestmentSettlementEntity): Long

    @Update
    suspend fun update(record: InvestmentSettlementEntity)

    @Query("SELECT * FROM investment_settlements WHERE id = :id LIMIT 1")
    suspend fun queryById(id: Long): InvestmentSettlementEntity?

    @Query("SELECT * FROM investment_settlements ORDER BY periodEndAt DESC, id DESC")
    suspend fun queryAllActive(): List<InvestmentSettlementEntity>

    @Query("SELECT * FROM investment_settlements ORDER BY periodEndAt DESC, id DESC")
    fun observeAllActive(): Flow<List<InvestmentSettlementEntity>>

    @Query(
        """
        SELECT * FROM investment_settlements
        WHERE accountId = :accountId
        ORDER BY periodEndAt DESC, id DESC
        """,
    )
    suspend fun queryByAccountId(accountId: Long): List<InvestmentSettlementEntity>

    @Query(
        """
        SELECT * FROM investment_settlements
        WHERE accountId = :accountId
        ORDER BY periodEndAt DESC, id DESC
        LIMIT 1
        """,
    )
    suspend fun getLatestForAccount(accountId: Long): InvestmentSettlementEntity?

    @Query("DELETE FROM investment_settlements WHERE accountId = :accountId")
    suspend fun deleteByAccountId(accountId: Long)
}
