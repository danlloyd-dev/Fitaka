package com.pitakaview.app.data.db

import android.content.Context
import androidx.room3.AutoMigration
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

/**
 * The database layout version. A backup file made by a higher version cannot be read by this app.
 *  - 1: the first layout (2026-11-26), on the owner's phone from 0.1.0 to 1.1.0.
 *  - 2 (2026-11-27): loans gain `paidBeforeCentavos` (paid before the app, never from a wallet) and
 *    `secondDueDay` (loans paid twice a month). Added columns only: Room's automatic migration from 1.
 */
const val FITAKA_DB_VERSION = 2

/**
 * Room's fingerprint of version [FITAKA_DB_VERSION]'s layout, which Room writes into every Fitaka database
 * (room_master_table) and checks at every opening. A restore checks a backup against the fingerprint of
 * the backup's own version ([FITAKA_DB_IDENTITIES]) before replacing anything (BackupManager.inspect): a
 * file with the right version number and the right table names but another layout would otherwise replace
 * the owner's data and then stop the app from starting. An older version's backup is restored as it is,
 * and Room carries it forward through the migrations when the app opens it, as it does after an update.
 * StoredNamesTest holds each fingerprint to its schema file.
 */
const val FITAKA_DB_IDENTITY = "f811eca95fe795975cadfbfa8cc0e227"

/** Version 1's fingerprint: every database and backup made by 0.1.0 to 1.1.0 carries it. */
const val FITAKA_DB_IDENTITY_V1 = "dcffe3a34ad6eab4e0ceef41a2738b77"

/** Each layout version this app can open, with its fingerprint. */
val FITAKA_DB_IDENTITIES: Map<Int, String> = mapOf(1 to FITAKA_DB_IDENTITY_V1, FITAKA_DB_VERSION to FITAKA_DB_IDENTITY)

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
    version = FITAKA_DB_VERSION,
    exportSchema = true,
    // Worked out by Room from the two exported layouts (app/schemas) and checked against them when built.
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
@ColumnTypeConverters(Converters::class)
abstract class FitakaDatabase : RoomDatabase() {
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
fun createFitakaDatabase(context: Context): FitakaDatabase =
    Room.databaseBuilder(context.applicationContext, FitakaDatabase::class.java, FitakaDatabase.FILE_NAME)
        .setDriver(AndroidSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
