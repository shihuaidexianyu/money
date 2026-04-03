package com.shihuaidexianyu.money.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "balance_adjustment_records",
    indices = [
        Index(value = ["accountId"]),
        Index(value = ["occurredAt"]),
        Index(value = ["sourceUpdateRecordId"], unique = true),
    ],
)
data class BalanceAdjustmentRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val accountId: Long,
    val delta: Long,
    val sourceUpdateRecordId: Long,
    val occurredAt: Long,
    val createdAt: Long,
)
