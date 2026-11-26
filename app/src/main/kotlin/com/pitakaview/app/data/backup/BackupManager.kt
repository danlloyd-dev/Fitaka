package com.pitakaview.app.data.backup

import androidx.room3.Transactor
import androidx.room3.useWriterConnection
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import com.pitakaview.app.data.db.PITAKA_DB_VERSION
import com.pitakaview.app.data.db.PitakaDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDate

/** What a backup file holds, shown before the owner agrees to restore it. */
data class BackupInfo(
    val schemaVersion: Int,
    val entries: Int,
    val wallets: Int,
    val lastEntryOn: LocalDate?,
)

sealed interface BackupCheck {
    data class Ok(val info: BackupInfo) : BackupCheck

    /** Not a PitakaView backup, or a damaged file. */
    data object NotABackup : BackupCheck

    /** Made by a newer version of the app than this one. */
    data object TooNew : BackupCheck
}

/** A restore failed before anything was touched: the database is still open and the app can carry on. */
class RestoreNotStartedException(cause: Throwable) : Exception("The restore could not start", cause)

/**
 * Backup and restore. A backup is a complete copy of the database as one file. Restoring an older
 * backup later goes through the same upgrade steps as an app update, so there is no second format.
 *
 * @param dbFile the live database file
 * @param driver opens a candidate file for inspection without going through Room
 * @param workDir a private folder for the short-lived snapshot
 * @param copyWholeFile forces the old-SQLite path; for tests
 */
class BackupManager(
    private val db: PitakaDatabase,
    private val dbFile: File,
    private val driver: SQLiteDriver,
    private val workDir: File,
    private val copyWholeFile: Boolean = false,
) {

    /**
     * Writes a complete, consistent copy of the database to the place [openDestination] gives.
     * The copy is made first, so a failure while making it never touches the destination, which may be
     * an older backup the owner chose to overwrite. The app can stay in use meanwhile.
     */
    suspend fun exportTo(openDestination: () -> OutputStream) = withContext(Dispatchers.IO) {
        workDir.mkdirs()
        val snapshot = File(workDir, "backup-snapshot.tmp")
        deleteWithSideFiles(snapshot)
        try {
            db.useWriterConnection { connection ->
                if (!copyWholeFile && connection.supportsVacuumInto()) {
                    // SQLite writes the copy itself, in one consistent step.
                    val target = snapshot.absolutePath.replace("'", "''")
                    connection.usePrepared("VACUUM INTO '$target'") { it.step() }
                } else {
                    // SQLite before 3.27 (Android 10 and older): fold the write-ahead log into the main
                    // file, then copy it while this connection, the only writer, is held.
                    check(connection.checkpoint() == 0L) { "The database was busy, so no backup was written" }
                    dbFile.copyTo(snapshot, overwrite = true)
                }
            }
            check(snapshot.length() > 0) { "The backup copy came out empty" }
            openDestination().use { out -> snapshot.inputStream().use { it.copyTo(out) } }
        } finally {
            deleteWithSideFiles(snapshot)
        }
    }

    /**
     * Copies a picked file into [target] for [inspect], but only if it starts like a database file.
     * A wrong pick (a photo, a long video) is refused after its first 16 bytes instead of being copied whole.
     */
    fun stage(input: InputStream, target: File): Boolean {
        val start = ByteArray(SQLITE_HEADER.size)
        var read = 0
        while (read < start.size) {
            val n = input.read(start, read, start.size - read)
            if (n < 0) break
            read += n
        }
        if (read != start.size || !start.contentEquals(SQLITE_HEADER)) return false
        target.parentFile?.mkdirs()
        target.outputStream().use { out ->
            out.write(start)
            input.copyTo(out)
        }
        return true
    }

    /**
     * Looks inside a candidate file without touching the live database. Any file that cannot be read
     * as a PitakaView database is [BackupCheck.NotABackup]; the reason does not matter to the owner.
     */
    fun inspect(candidate: File): BackupCheck {
        if (!candidate.hasSqliteHeader()) return BackupCheck.NotABackup
        return try {
            driver.open(candidate.absolutePath).use { connection ->
                val version = connection.long("PRAGMA user_version")?.toInt() ?: 0
                when {
                    version > PITAKA_DB_VERSION -> BackupCheck.TooNew
                    version < 1 -> BackupCheck.NotABackup
                    connection.text("PRAGMA quick_check") != "ok" -> BackupCheck.NotABackup
                    else -> BackupCheck.Ok(
                        BackupInfo(
                            schemaVersion = version,
                            entries = connection.long("SELECT COUNT(*) FROM transactions")?.toInt() ?: 0,
                            wallets = connection.long("SELECT COUNT(*) FROM wallets")?.toInt() ?: 0,
                            lastEntryOn = connection.long("SELECT MAX(occurredOn) FROM transactions")?.let(LocalDate::ofEpochDay),
                        ),
                    )
                }
            }
        } catch (e: Exception) {
            // An untrusted file: a SQLite header followed by garbage, or missing tables, lands here.
            BackupCheck.NotABackup
        } finally {
            sideFiles(candidate).forEach { it.delete() }
        }
    }

    /**
     * Replaces the live database with [candidate] (already checked with [inspect]).
     *
     * Throws [RestoreNotStartedException] when nothing was touched: the app can carry on. Any other
     * failure happens after the database was closed, so the app must restart; the old data is then
     * still whole on disk. After a normal return the app must restart too: everything in memory
     * points at the closed database.
     */
    suspend fun replaceDatabaseWith(candidate: File) = withContext(Dispatchers.IO) {
        val staged = File(dbFile.parentFile, dbFile.name + ".restoring")
        try {
            candidate.copyTo(staged, overwrite = true)
            // Fold everything into the main file first, so the old data is whole if the swap below fails.
            check(db.useWriterConnection { it.checkpoint() } == 0L) { "The database was busy" }
        } catch (e: Exception) {
            staged.delete()
            throw RestoreNotStartedException(e)
        }
        try {
            db.close()
            sideFiles(dbFile).forEach { it.delete() } // the old data's log must never be replayed onto the new file
            Files.move(staged.toPath(), dbFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            staged.delete() // already gone when the move worked
        }
        deleteWithSideFiles(candidate)
    }

    /** Folds the write-ahead log into the main file. Returns 0 when all of it went in. */
    private suspend fun Transactor.checkpoint(): Long =
        usePrepared("PRAGMA wal_checkpoint(TRUNCATE)") { if (it.step()) it.getLong(0) else 0L }

    private suspend fun Transactor.supportsVacuumInto(): Boolean {
        val version = usePrepared("SELECT sqlite_version()") { if (it.step()) it.getText(0) else "" }
        val (major, minor) = version.split(".").map { it.toIntOrNull() ?: 0 }.let { (it.getOrNull(0) ?: 0) to (it.getOrNull(1) ?: 0) }
        return major > 3 || (major == 3 && minor >= 27)
    }

    private fun SQLiteConnection.long(sql: String): Long? =
        prepare(sql).use { if (it.step() && !it.isNull(0)) it.getLong(0) else null }

    private fun SQLiteConnection.text(sql: String): String? =
        prepare(sql).use { if (it.step() && !it.isNull(0)) it.getText(0) else null }

    private fun File.hasSqliteHeader(): Boolean {
        if (!isFile || length() < SQLITE_HEADER.size) return false
        val start = ByteArray(SQLITE_HEADER.size)
        inputStream().use { if (it.read(start) != start.size) return false }
        return start.contentEquals(SQLITE_HEADER)
    }

    private fun sideFiles(file: File) = listOf("-wal", "-shm", "-journal").map { File(file.path + it) }

    private fun deleteWithSideFiles(file: File) {
        file.delete()
        sideFiles(file).forEach { it.delete() }
    }

    private companion object {
        val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
    }
}
