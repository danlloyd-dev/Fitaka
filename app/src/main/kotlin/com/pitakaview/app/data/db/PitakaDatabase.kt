package com.pitakaview.app.data.db

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.ColumnTypeConverters
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.pitakaview.app.data.db.dao.BaonDao
import com.pitakaview.app.data.db.dao.CategoryDao
import com.pitakaview.app.data.db.dao.ImportBatchDao
import com.pitakaview.app.data.db.dao.LoanDao
import com.pitakaview.app.data.db.dao.PayScheduleDao
import com.pitakaview.app.data.db.dao.SavingsGoalDao
import com.pitakaview.app.data.db.dao.TransactionDao
import com.pitakaview.app.data.db.dao.WalletDao
import com.pitakaview.app.data.db.entity.BaonEntity
import com.pitakaview.app.data.db.entity.CategoryEntity
import com.pitakaview.app.data.db.entity.ImportBatchEntity
import com.pitakaview.app.data.db.entity.LoanEntity
import com.pitakaview.app.data.db.entity.PayScheduleEntity
import com.pitakaview.app.data.db.entity.SavingsGoalEntity
import com.pitakaview.app.data.db.entity.TransactionEntity
import com.pitakaview.app.data.db.entity.WalletEntity
import kotlinx.coroutines.Dispatchers

/** The database layout version. A backup file made by a higher version cannot be read by this app. */
const val PITAKA_DB_VERSION = 1

/**
 * All data lives in this one on-device file. Schema versions are exported to app/schemas; every
 * version bump needs a real Migration — never destructive fallback, because this is someone's money history.
 */
@Database(
    entities = [
        WalletEntity::class,
        CategoryEntity::class,
        PayScheduleEntity::class,
        BaonEntity::class,
        SavingsGoalEntity::class,
        LoanEntity::class,
        ImportBatchEntity::class,
        TransactionEntity::class,
    ],
    version = PITAKA_DB_VERSION,
    exportSchema = true,
)
@ColumnTypeConverters(Converters::class)
abstract class PitakaDatabase : RoomDatabase() {
    abstract fun walletDao(): WalletDao
    abstract fun categoryDao(): CategoryDao
    abstract fun payScheduleDao(): PayScheduleDao
    abstract fun baonDao(): BaonDao
    abstract fun savingsGoalDao(): SavingsGoalDao
    abstract fun loanDao(): LoanDao
    abstract fun importBatchDao(): ImportBatchDao
    abstract fun transactionDao(): TransactionDao

    companion object {
        const val FILE_NAME = "pitakaview.db"
    }
}

/** Opens the on-device database file using the phone's built-in SQLite. */
fun createPitakaDatabase(context: Context): PitakaDatabase =
    Room.databaseBuilder(context.applicationContext, PitakaDatabase::class.java, PitakaDatabase.FILE_NAME)
        .setDriver(AndroidSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
