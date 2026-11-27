package com.pitakaview.app.data.backup

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.pitakaview.app.data.db.FITAKA_DB_VERSION
import com.pitakaview.app.data.db.FITAKA_DB_IDENTITY
import com.pitakaview.app.data.db.FITAKA_DB_IDENTITY_V1
import com.pitakaview.app.data.db.FitakaDatabase
import com.pitakaview.app.data.db.FitakaDatabase_Impl
import com.pitakaview.app.data.db.entity.CategoryEntity
import com.pitakaview.app.data.db.entity.TransactionEntity
import com.pitakaview.app.data.db.entity.WalletEntity
import com.pitakaview.app.data.repo.LedgerRepository
import com.pitakaview.app.domain.CategoryKind
import com.pitakaview.app.domain.TxPurpose
import com.pitakaview.app.domain.WalletType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.time.Instant
import java.time.LocalDate

/** Real database files on disk (desktop SQLite), so the copy, the checks and the swap are all exercised. */
class BackupManagerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val now = Instant.parse("2026-11-26T03:00:00Z")
    private val day = LocalDate.of(2026, 11, 26)
    private val seeded = mapOf("Cash" to 45_000L, "GCash" to 30_000L)

    private fun open(file: File): FitakaDatabase =
        Room.databaseBuilder<FitakaDatabase>(name = file.absolutePath, factory = { FitakaDatabase_Impl() })
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()

    private suspend fun balances(db: FitakaDatabase) =
        db.walletDao().observeAllWithBalance().first().associate { it.wallet.name to it.balanceCentavos }

    /** Two wallets, a ₱250 expense and a ₱300 transfer. Returns (cash, food) ids. */
    private suspend fun seed(db: FitakaDatabase): Pair<Long, Long> {
        val cash = db.walletDao().insert(WalletEntity(name = "Cash", type = WalletType.CASH, openingBalanceCentavos = 100_000, createdAt = now))
        val gcash = db.walletDao().insert(WalletEntity(name = "GCash", type = WalletType.E_WALLET, createdAt = now))
        val food = db.categoryDao().insert(CategoryEntity(name = "Food", kind = CategoryKind.EXPENSE, iconKey = "food"))
        val ledger = LedgerRepository(db)
        ledger.add(expense(25_000, cash, food))
        ledger.add(TransactionEntity(purpose = TxPurpose.TRANSFER, amountCentavos = 30_000, occurredOn = day, fromWalletId = cash, toWalletId = gcash, createdAt = now))
        return cash to food
    }

    private fun expense(amount: Long, wallet: Long, category: Long) = TransactionEntity(
        purpose = TxPurpose.EXPENSE, amountCentavos = amount, occurredOn = day, fromWalletId = wallet, categoryId = category, createdAt = now,
    )

    private fun backUpChangeRestore(copyWholeFile: Boolean) = runBlocking {
        val live = File(tmp.root, "live.db")
        val work = tmp.newFolder()
        var db = open(live)
        val (cash, food) = seed(db)
        val atBackup = balances(db)
        assertEquals(seeded, atBackup)

        val manager = BackupManager(db, live, BundledSQLiteDriver(), work, copyWholeFile)
        val backupFile = File(tmp.root, "backup.pitaka")
        manager.exportTo { backupFile.outputStream() }
        assertTrue("no leftover snapshot", work.listFiles().orEmpty().isEmpty())

        // The phone's data moves on after the backup.
        LedgerRepository(db).add(expense(40_000, cash, food))
        assertNotEquals(atBackup, balances(db))

        val candidate = File(work, "candidate.db")
        assertTrue(backupFile.inputStream().use { manager.stage(it, candidate) })
        assertEquals(
            BackupCheck.Ok(BackupInfo(schemaVersion = FITAKA_DB_VERSION, entries = 2, wallets = 2, lastEntryOn = day)),
            manager.inspect(candidate),
        )

        manager.replaceDatabaseWith(candidate)
        assertFalse("no half-way file is left next to the database", File(tmp.root, "live.db.restoring").exists())
        db = open(live) // what the restarted app does
        assertEquals("balances are exactly as they were at backup time", atBackup, balances(db))
        assertEquals(2, db.transactionDao().observeRecent(10).first().size)
        db.close()
    }

    @Test
    fun backUpChangeRestoreGivesBackTheSameBalances() = backUpChangeRestore(copyWholeFile = false)

    @Test
    fun theOlderSqlitePathRestoresTheSameWay() = backUpChangeRestore(copyWholeFile = true)

    /**
     * A backup the app made while it was called PitakaView: the file in src/test/resources/backups was
     * written by the code at commit c116ab9 (2026-11-26) from the same made-up data as [seed]. The owner's
     * own backups are such files, so Fitaka must go on reading them, with every balance as it was.
     */
    @Test
    fun aBackupMadeWhenTheAppWasCalledPitakaViewRestoresWithItsBalances() = runBlocking {
        val live = File(tmp.root, "live.db")
        val work = tmp.newFolder()
        var db = open(live)
        // What the phone holds now is something else entirely.
        db.walletDao().insert(WalletEntity(name = "Something else", type = WalletType.CASH, openingBalanceCentavos = 1, createdAt = now))
        val manager = BackupManager(db, live, BundledSQLiteDriver(), work)

        val old = File("src/test/resources/backups/made-by-pitakaview-0.1.0.backup")
        assertTrue("the old backup file is among the test files", old.length() > 0)
        val candidate = File(work, "candidate.db")
        assertTrue(old.inputStream().use { manager.stage(it, candidate) })
        assertEquals(BackupCheck.Ok(BackupInfo(schemaVersion = 1, entries = 2, wallets = 2, lastEntryOn = day)), manager.inspect(candidate))

        manager.replaceDatabaseWith(candidate)
        db = open(live) // what the restarted app does
        assertEquals("the old backup's balances, to the centavo", seeded, balances(db))
        assertEquals(2, db.transactionDao().observeRecent(10).first().size)
        db.close()
    }

    @Test
    fun filesThatAreNotUsableBackupsAreRefused() = runBlocking {
        val live = File(tmp.root, "live.db")
        val db = open(live)
        seed(db)
        val manager = BackupManager(db, live, BundledSQLiteDriver(), tmp.newFolder())
        fun file(name: String, bytes: ByteArray) = File(tmp.root, name).apply { writeBytes(bytes) }
        fun sqlite(name: String, vararg statements: String) = File(tmp.root, name).also { target ->
            BundledSQLiteDriver().open(target.absolutePath).use { c -> statements.forEach { sql -> c.prepare(sql).use { it.step() } } }
        }

        assertEquals("a photo", BackupCheck.NotABackup, manager.inspect(file("photo.jpg", ByteArray(4096) { 7 })))
        assertEquals("an empty file", BackupCheck.NotABackup, manager.inspect(file("empty.pitaka", ByteArray(0))))
        assertEquals(
            "a SQLite header followed by garbage",
            BackupCheck.NotABackup,
            manager.inspect(file("broken.pitaka", "SQLite format 3\u0000".toByteArray() + ByteArray(4096) { 9 })),
        )
        assertEquals(
            "some other app's database",
            BackupCheck.NotABackup,
            manager.inspect(sqlite("notes.db", "CREATE TABLE notes (body TEXT)")),
        )
        assertEquals(
            "the right version number but not our tables",
            BackupCheck.NotABackup,
            manager.inspect(sqlite("fake.db", "CREATE TABLE notes (body TEXT)", "PRAGMA user_version = $FITAKA_DB_VERSION")),
        )
        assertEquals(
            "our version number and our table names, but not Room's layout (added 2026-11-27: it used to pass)",
            BackupCheck.NotABackup,
            manager.inspect(
                sqlite(
                    "lookalike.db", "CREATE TABLE transactions (id INTEGER, occurredOn INTEGER)", "CREATE TABLE wallets (id INTEGER)",
                    "PRAGMA user_version = $FITAKA_DB_VERSION",
                ),
            ),
        )
        assertEquals(
            "the same with Room's table, but another layout's fingerprint",
            BackupCheck.NotABackup,
            manager.inspect(
                sqlite(
                    "otherlayout.db", "CREATE TABLE transactions (id INTEGER, occurredOn INTEGER)", "CREATE TABLE wallets (id INTEGER)",
                    "CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)",
                    "INSERT INTO room_master_table VALUES (42, '00000000000000000000000000000000')",
                    "PRAGMA user_version = $FITAKA_DB_VERSION",
                ),
            ),
        )
        fun roomFile(name: String, version: Int, identity: String) = sqlite(
            name, "CREATE TABLE transactions (id INTEGER, occurredOn INTEGER)", "CREATE TABLE wallets (id INTEGER)",
            "CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)",
            "INSERT INTO room_master_table VALUES (42, '$identity')",
            "PRAGMA user_version = $version",
        )
        // Since layout version 2 (2026-11-27) each backup is checked against its own version's fingerprint.
        assertEquals("version 1, with version 2's fingerprint", BackupCheck.NotABackup, manager.inspect(roomFile("v1as2.db", 1, FITAKA_DB_IDENTITY)))
        assertEquals("version 2, with version 1's fingerprint", BackupCheck.NotABackup, manager.inspect(roomFile("v2as1.db", 2, FITAKA_DB_IDENTITY_V1)))
        assertEquals(
            "a backup from a newer app",
            BackupCheck.TooNew,
            manager.inspect(sqlite("future.db", "CREATE TABLE transactions (id INTEGER)", "PRAGMA user_version = ${FITAKA_DB_VERSION + 1}")),
        )

        // None of that touched the live data.
        assertEquals(seeded, balances(db))
        db.close()
    }

    @Test
    fun aWrongPickIsRefusedAfterItsFirstBytesInsteadOfBeingCopied() = runBlocking {
        val live = File(tmp.root, "live.db")
        val db = open(live)
        seed(db)
        val manager = BackupManager(db, live, BundledSQLiteDriver(), tmp.newFolder())
        val target = File(tmp.root, "candidate.db")

        var bytesRead = 0L
        val longVideo = object : InputStream() { // 50 MB that is not a database
            var left = 50L * 1024 * 1024
            override fun read(): Int = if (left <= 0) -1 else 7.also { left--; bytesRead++ }
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (left <= 0) return -1
                val n = minOf(len.toLong(), left).toInt()
                b.fill(7, off, off + n)
                left -= n
                bytesRead += n
                return n
            }
        }
        assertFalse(manager.stage(longVideo, target))
        assertEquals("only the start of the file was read", 16L, bytesRead)
        assertFalse("nothing was copied", target.exists())
        db.close()
    }

    @Test
    fun aBackupThatCannotBeMadeNeverOpensTheChosenPlace() = runBlocking {
        val live = File(tmp.root, "live.db")
        val db = open(live)
        seed(db)
        // The work folder is unusable (a file sits where it should be), so no copy can be made.
        val notAFolder = File(tmp.root, "not-a-folder").apply { writeText("x") }
        val manager = BackupManager(db, live, BundledSQLiteDriver(), notAFolder)

        var opened = false
        val failure = runCatching {
            manager.exportTo {
                opened = true
                ByteArrayOutputStream()
            }
        }.exceptionOrNull()

        assertTrue("making the copy fails: $failure", failure != null)
        assertFalse("an older backup at the chosen place is never opened, so never emptied", opened)
        db.close()
    }

    @Test
    fun aRestoreThatCannotStartLeavesTheCurrentDataInPlace() = runBlocking {
        val live = File(tmp.root, "live.db")
        val db = open(live)
        seed(db)
        val manager = BackupManager(db, live, BundledSQLiteDriver(), tmp.newFolder())

        val missing = File(tmp.root, "gone.pitaka")
        val failure = runCatching { manager.replaceDatabaseWith(missing) }.exceptionOrNull()
        assertTrue("it says nothing was touched: $failure", failure is RestoreNotStartedException)
        assertFalse(File(tmp.root, "live.db.restoring").exists())

        // The database was not closed or replaced: it still answers.
        assertEquals(seeded, balances(db))
        db.close()
    }

    @Test
    fun aSwapThatFailsAfterClosingIsNotReportedAsUntouchedAndLeavesNoHalfWayFile() = runBlocking {
        val live = File(tmp.root, "live.db")
        val db = open(live)
        seed(db)
        val backupFile = File(tmp.root, "backup.pitaka")
        BackupManager(db, live, BundledSQLiteDriver(), tmp.newFolder()).exportTo { backupFile.outputStream() }

        // A non-empty folder sits where the database file should go, so the last step of the swap fails.
        val blocked = File(tmp.root, "blocked.db").apply {
            mkdirs()
            File(this, "x").writeText("x")
        }
        val manager = BackupManager(db, blocked, BundledSQLiteDriver(), tmp.newFolder())
        val failure = runCatching { manager.replaceDatabaseWith(backupFile) }.exceptionOrNull()

        assertTrue("it fails: $failure", failure != null)
        assertFalse("the caller must restart the app, so this is not 'nothing was touched'", failure is RestoreNotStartedException)
        assertFalse(File(tmp.root, "blocked.db.restoring").exists())

        // The real data is whole on disk for the restart.
        val reopened = open(live)
        assertEquals(seeded, balances(reopened))
        reopened.close()
    }
}
