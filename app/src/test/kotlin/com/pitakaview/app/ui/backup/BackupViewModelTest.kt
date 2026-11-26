package com.pitakaview.app.ui.backup

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.pitakaview.app.data.backup.BackupManager
import com.pitakaview.app.data.db.PitakaDatabase
import com.pitakaview.app.data.db.PitakaDatabase_Impl
import com.pitakaview.app.data.prefs.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

/** What the Backup screen does when saving or restoring goes wrong part-way. Real database and settings files. */
@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val at = Instant.parse("2026-11-26T03:00:00Z")
    private val storeJob = Job()
    private val restarts = AtomicInteger()
    private lateinit var live: File
    private lateinit var work: File
    private lateinit var db: PitakaDatabase
    private lateinit var settings: SettingsRepository

    @Before
    fun open() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        live = File(tmp.root, "live.db")
        db = Room.databaseBuilder<PitakaDatabase>(name = live.absolutePath, factory = { PitakaDatabase_Impl() })
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        settings = SettingsRepository(
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(Dispatchers.IO + storeJob),
                produceFile = { File(tmp.root, "settings.preferences_pb") },
            ),
        )
    }

    @After
    fun close() {
        runBlocking { storeJob.cancelAndJoin() }
        runCatching { db.close() }
        Dispatchers.resetMain()
    }

    private fun viewModel(dbFile: File = live): BackupViewModel {
        work = tmp.newFolder()
        return BackupViewModel(
            BackupManager(db, dbFile, BundledSQLiteDriver(), work), settings, db.transactionDao(), work,
            restartApp = { restarts.incrementAndGet() }, now = { at },
        )
    }

    private fun BackupViewModel.settle() = runBlocking { withTimeout(10_000) { while (busy) delay(10) } }

    private fun lastBackupAt() = runBlocking { settings.lastBackupAt.first() }

    @Test
    fun aGoodSaveIsRecordedAsTheLastBackup() {
        val vm = viewModel()
        val saved = ByteArrayOutputStream()
        val discarded = AtomicInteger()
        vm.backUpTo(open = { saved }, discardHalfWritten = { discarded.incrementAndGet() })
        vm.settle()

        assertEquals(BackupNotice.SAVED, vm.notice)
        assertTrue(saved.size() > 0)
        assertEquals(0, discarded.get())
        assertEquals(at, lastBackupAt())
    }

    @Test
    fun aSaveThatBreaksHalfwayIsRemovedAndNotCountedAsABackup() {
        val vm = viewModel()
        val discarded = AtomicInteger()
        val runsOutOfSpace = object : OutputStream() {
            var written = 0
            override fun write(b: Int) {
                if (++written > 100) throw IOException("no space left")
            }
        }
        vm.backUpTo(open = { runsOutOfSpace }, discardHalfWritten = { discarded.incrementAndGet() })
        vm.settle()

        assertEquals(BackupNotice.SAVE_FAILED, vm.notice)
        assertEquals("the cut-off file is removed", 1, discarded.get())
        assertNull("a failed save is not a backup", lastBackupAt())
    }

    @Test
    fun aPlaceThatCannotBeOpenedIsNotDeleted() {
        val vm = viewModel()
        val discarded = AtomicInteger()
        vm.backUpTo(open = { null }, discardHalfWritten = { discarded.incrementAndGet() })
        vm.settle()

        assertEquals(BackupNotice.SAVE_FAILED, vm.notice)
        assertEquals("nothing was written there, so whatever is there stays", 0, discarded.get())
        assertNull(lastBackupAt())
    }

    @Test
    fun aRestoreThatCannotStartSaysSoAndTheAppCarriesOn() {
        val vm = viewModel()
        val saved = ByteArrayOutputStream()
        vm.backUpTo(open = { saved })
        vm.settle()
        vm.stageRestore { ByteArrayInputStream(saved.toByteArray()) }
        vm.settle()
        assertNotNull("the backup is offered for restore", vm.pending)

        // The staged copy disappears (the phone cleared the app's cache) before the owner says yes.
        assertTrue(File(work, "restore-candidate.db").delete())
        vm.confirmRestore()
        vm.settle()

        assertEquals(BackupNotice.RESTORE_FAILED, vm.notice)
        assertNull(vm.pending)
        assertEquals("no restart: nothing was touched", 0, restarts.get())
        assertTrue("the database still answers", runBlocking { db.walletDao().observeAllWithBalance().first() }.isEmpty())
    }

    @Test
    fun aSwapThatFailsAfterTheDatabaseIsClosedStillRestartsTheApp() {
        val saved = ByteArrayOutputStream()
        viewModel().run {
            backUpTo(open = { saved })
            settle()
        }
        // A non-empty folder sits where the database file should go, so the last step of the swap fails.
        val blocked = File(tmp.root, "blocked.db").apply {
            mkdirs()
            File(this, "x").writeText("x")
        }
        val vm = viewModel(dbFile = blocked)
        vm.stageRestore { ByteArrayInputStream(saved.toByteArray()) }
        vm.settle()
        assertNotNull(vm.pending)

        vm.confirmRestore()
        runBlocking { withTimeout(10_000) { while (restarts.get() == 0) delay(10) } }
        assertEquals("a closed database cannot be used, so the app restarts on its old data", 1, restarts.get())
    }
}
