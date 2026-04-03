package com.shihuaidexianyu.money.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP INDEX IF EXISTS `index_balance_adjustment_records_sourceUpdateRecordId`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_balance_adjustment_records_sourceUpdateRecordId` ON `balance_adjustment_records` (`sourceUpdateRecordId`)")
    }
}

@Database(
    entities = [
        AccountEntity::class,
        CashFlowRecordEntity::class,
        TransferRecordEntity::class,
        BalanceUpdateRecordEntity::class,
        BalanceAdjustmentRecordEntity::class,
        InvestmentSettlementEntity::class,
    ],
    version = 2,
    exportSchema = true,
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
                ).addMigrations(MIGRATION_1_2).build().also { INSTANCE = it }
            }
        }
    }
}

