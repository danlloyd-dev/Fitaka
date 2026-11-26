package com.pitakaview.app.ui.backup

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.pitakaview.app.data.backup.BackupManager
import com.pitakaview.app.data.db.PitakaDatabase
import com.pitakaview.app.data.db.PitakaDatabase_Impl
import com.pitakaview.app.data.db.entity.CategoryEntity
import com.pitakaview.app.data.db.entity.LoanEntity
import com.pitakaview.app.data.db.entity.PayScheduleEntity
import com.pitakaview.app.data.db.entity.SavingsGoalEntity
import com.pitakaview.app.data.db.entity.TransactionEntity
import com.pitakaview.app.data.db.entity.WalletEntity
import com.pitakaview.app.data.prefs.SettingsRepository
import com.pitakaview.app.data.repo.LedgerRepository
import com.pitakaview.app.domain.CategoryKind
import com.pitakaview.app.domain.LoanDirection
import com.pitakaview.app.domain.PayFrequency
import com.pitakaview.app.domain.TxPurpose
import com.pitakaview.app.domain.WalletType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger

/** The spreadsheet export end to end: the real query, the real CSV writer, the screen's messages. */
@OptIn(ExperimentalCoroutinesApi::class)
class SpreadsheetExportTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val at = Instant.parse("2026-11-26T03:00:00Z")
    private val storeJob = Job()
    private lateinit var db: PitakaDatabase
    private lateinit var vm: BackupViewModel

    @Before
    fun open() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val live = File(tmp.root, "live.db")
        db = Room.databaseBuilder<PitakaDatabase>(name = live.absolutePath, factory = { PitakaDatabase_Impl() })
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        val settings = SettingsRepository(
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(Dispatchers.IO + storeJob),
                produceFile = { File(tmp.root, "settings.preferences_pb") },
            ),
        )
        val work = tmp.newFolder()
        vm = BackupViewModel(BackupManager(db, live, BundledSQLiteDriver(), work), settings, db.transactionDao(), work, restartApp = {}, now = { at })

        // One entry of several kinds, on different days, so order and names are both checked.
        val cash = db.walletDao().insert(WalletEntity(name = "Cash", type = WalletType.CASH, createdAt = at))
        val gcash = db.walletDao().insert(WalletEntity(name = "GCash", type = WalletType.E_WALLET, createdAt = at))
        val food = db.categoryDao().insert(CategoryEntity(name = "Food", kind = CategoryKind.EXPENSE, iconKey = "food"))
        val salary = db.categoryDao().insert(CategoryEntity(name = "Salary", kind = CategoryKind.INCOME, iconKey = "salary"))
        val payday = db.payScheduleDao().insert(
            PayScheduleEntity(name = "Sahod", expectedAmountCentavos = 1_500_000, walletId = gcash, categoryId = salary, frequency = PayFrequency.SEMI_MONTHLY, createdAt = at),
        )
        val loan = db.loanDao().insert(
            LoanEntity(direction = LoanDirection.I_OWE, counterparty = "Tita Baby", principalCentavos = 1_000_000, totalPayableCentavos = 1_000_000, startDate = day(1), createdAt = at),
        )
        val goal = db.savingsGoalDao().insert(SavingsGoalEntity(name = "Laptop", createdAt = at))
        val ledger = LedgerRepository(db)
        fun row(purpose: TxPurpose, amount: Long, on: Int, from: Long? = null, to: Long? = null) =
            TransactionEntity(purpose = purpose, amountCentavos = amount, occurredOn = day(on), fromWalletId = from, toWalletId = to, createdAt = at)
        ledger.add(row(TxPurpose.LOAN_PAYMENT, 200_000, on = 20, from = cash).copy(loanId = loan))
        ledger.add(row(TxPurpose.INCOME, 1_500_000, on = 15, to = gcash).copy(categoryId = salary, payScheduleId = payday))
        ledger.add(row(TxPurpose.EXPENSE, 25_000, on = 26, from = cash).copy(categoryId = food, note = "lunch, with Tita"))
        ledger.add(row(TxPurpose.SAVINGS_DEPOSIT, 50_000, on = 16, from = gcash, to = gcash).copy(goalId = goal))
        ledger.add(row(TxPurpose.TRANSFER, 30_000, on = 16, from = gcash, to = cash))
        Unit
    }

    @After
    fun close() {
        runBlocking { storeJob.cancelAndJoin() }
        db.close()
        Dispatchers.resetMain()
    }

    private fun day(of: Int) = LocalDate.of(2026, 11, of)

    private fun settle() = runBlocking { withTimeout(10_000) { while (vm.busy) delay(10) } }

    @Test
    fun everyEntryIsWrittenOldestFirstWithTheNamesItsIdsStandFor() {
        val saved = ByteArrayOutputStream()
        vm.exportTo(open = { saved })
        settle()

        assertEquals(BackupNotice.EXPORTED, vm.notice)
        assertEquals(5, vm.exportedCount)
        val text = saved.toString(Charsets.UTF_8)
        assertTrue(text.startsWith("﻿"))
        assertEquals(
            listOf(
                "Date,Type,Category,Amount,In or out,From wallet,To wallet,For,Note",
                "2026-11-15,Income,Salary,15000.00,in,,GCash,Sahod,",
                "2026-11-16,Added to savings,,500.00,move,GCash,GCash,Laptop,",
                "2026-11-16,Transfer,,300.00,move,GCash,Cash,,",
                "2026-11-20,Loan payment,,2000.00,out,Cash,,Tita Baby,",
                "2026-11-26,Spending,Food,250.00,out,Cash,,,\"lunch, with Tita\"",
                "",
            ),
            text.removePrefix("﻿").split("\r\n"),
        )
    }

    @Test
    fun aSpreadsheetThatBreaksHalfwayIsRemoved() {
        val discarded = AtomicInteger()
        val runsOutOfSpace = object : OutputStream() {
            var written = 0
            override fun write(b: Int) {
                if (++written > 40) throw IOException("no space left")
            }
        }
        vm.exportTo(open = { runsOutOfSpace }, discardHalfWritten = { discarded.incrementAndGet() })
        settle()

        assertEquals(BackupNotice.EXPORT_FAILED, vm.notice)
        assertEquals("the cut-off file is removed", 1, discarded.get())
    }

    @Test
    fun aPlaceThatCannotBeOpenedIsNotDeletedAndABackupIsNotClaimed() {
        val discarded = AtomicInteger()
        vm.exportTo(open = { null }, discardHalfWritten = { discarded.incrementAndGet() })
        settle()

        assertEquals(BackupNotice.EXPORT_FAILED, vm.notice)
        assertEquals("nothing was written there, so whatever is there stays", 0, discarded.get())
        assertNull("exporting is not backing up", vm.lastBackupAt.value)
    }
}
