package com.shihuaidexianyu.money.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.shihuaidexianyu.money.data.dao.AccountDao
import com.shihuaidexianyu.money.data.dao.BalanceAdjustmentRecordDao
import com.shihuaidexianyu.money.data.dao.BalanceUpdateRecordDao
import com.shihuaidexianyu.money.data.dao.CashFlowRecordDao
import com.shihuaidexianyu.money.data.dao.InvestmentSettlementDao
import com.shihuaidexianyu.money.data.dao.TransferRecordDao
import com.shihuaidexianyu.money.data.entity.AccountEntity
import com.shihuaidexianyu.money.data.entity.BalanceAdjustmentRecordEntity
import com.shihuaidexianyu.money.data.entity.BalanceUpdateRecordEntity
import com.shihuaidexianyu.money.data.entity.CashFlowRecordEntity
import com.shihuaidexianyu.money.data.entity.InvestmentSettlementEntity
import com.shihuaidexianyu.money.data.entity.TransferRecordEntity

@Database(
    entities = [
        AccountEntity::class,
        CashFlowRecordEntity::class,
        TransferRecordEntity::class,
        BalanceUpdateRecordEntity::class,
        BalanceAdjustmentRecordEntity::class,
        InvestmentSettlementEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class MoneyDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun cashFlowRecordDao(): CashFlowRecordDao
    abstract fun transferRecordDao(): TransferRecordDao
    abstract fun balanceUpdateRecordDao(): BalanceUpdateRecordDao
    abstract fun balanceAdjustmentRecordDao(): BalanceAdjustmentRecordDao
    abstract fun investmentSettlementDao(): InvestmentSettlementDao

    companion object {
        @Volatile
        private var INSTANCE: MoneyDatabase? = null

        fun getInstance(context: Context): MoneyDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context,
                    MoneyDatabase::class.java,
                    "money.db",
                ).build().also { INSTANCE = it }
            }
        }
    }
}

