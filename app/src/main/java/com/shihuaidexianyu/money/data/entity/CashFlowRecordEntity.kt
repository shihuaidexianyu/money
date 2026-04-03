package com.shihuaidexianyu.money.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cash_flow_records",
    indices = [
        Index(value = ["accountId"]),
        Index(value = ["occurredAt"]),
        Index(value = ["isDeleted"]),
    ],
)
data class CashFlowRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val accountId: Long,
    val direction: String,
    val amount: Long,
    val purpose: String,
    val occurredAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
)

