package com.shihuaidexianyu.money.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "investment_settlements",
    indices = [
        Index(value = ["accountId"]),
        Index(value = ["balanceUpdateRecordId"], unique = true),
        Index(value = ["periodStartAt", "periodEndAt"]),
    ],
)
data class InvestmentSettlementEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val accountId: Long,
    val balanceUpdateRecordId: Long,
    val previousBalance: Long,
    val currentBalance: Long,
    val netTransferIn: Long,
    val netTransferOut: Long,
    val pnl: Long,
    val returnRate: Double,
    val periodStartAt: Long,
    val periodEndAt: Long,
    val createdAt: Long,
)
