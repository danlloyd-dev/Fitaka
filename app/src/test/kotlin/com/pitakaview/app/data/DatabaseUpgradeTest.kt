package com.pitakaview.app.data

import androidx.room3.Room
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.pitakaview.app.data.db.FITAKA_DB_IDENTITY
import com.pitakaview.app.data.db.FITAKA_DB_IDENTITY_V1
import com.pitakaview.app.data.db.FITAKA_DB_VERSION
import com.pitakaview.app.data.db.FitakaDatabase
import com.pitakaview.app.data.db.FitakaDatabase_Impl
import com.pitakaview.app.domain.LoanDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.LocalDate

/**
 * The upgrade from layout version 1 (every Fitaka from 0.1.0 to 1.1.0, the owner's phone included) to 2.
 *
 * The version 1 database is made exactly as Room made it, from Room's own record of that layout
 * (app/schemas/.../1.json: every table, index and fingerprint), and filled with a loan, a wallet and a
 * payment the way 1.1.0 stored them. Then the app as it is now opens it, through Room's migration, as it
 * will on the owner's phone after the update. Robolectric only for the phone's JSON reader.
 */
@RunWith(RobolectricTestRunner::class)
class DatabaseUpgradeTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val layoutDir = File("schemas/com.pitakaview.app.data.db.FitakaDatabase")

    /** A version 1 database file, as Room 3.0.3 wrote it for 0.1.0 to 1.1.0. */
    private fun versionOneFile(): File {
        val file = File(tmp.root, "pitakaview.db")
        val layout = JSONObject(File(layoutDir, "1.json").readText()).getJSONObject("database")
        assertEquals(1, layout.getInt("version"))
        BundledSQLiteDriver().open(file.absolutePath).use { c ->
            val entities = layout.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                c.run(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices")
                for (j in 0 until (indices?.length() ?: 0)) c.run(indices!!.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            val setup = layout.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) c.run(setup.getString(i))
            c.run("PRAGMA user_version = 1")
            // What 1.1.0 stored: a wallet, a monthly loan from before the app, and one payment from the wallet.
            c.run("INSERT INTO wallets (id, name, type, provider, openingBalanceCentavos, sortOrder, isArchived, createdAt) VALUES (1, 'Cash', 'CASH', 'NONE', 500000, 0, 0, 0)")
            c.run(
                "INSERT INTO loans (id, direction, counterparty, principalCentavos, totalPayableCentavos, startDate, dueDate, " +
                    "installmentCentavos, dueDayOfMonth, note, remind, closedAt, createdAt) VALUES " +
                    "(7, 'I_OWE', 'Tita Baby', 1200000, 1200000, ${LocalDate.of(2026, 6, 1).toEpochDay()}, ${LocalDate.of(2026, 7, 15).toEpochDay()}, " +
                    "100000, 15, 'from June', 1, NULL, 0)",
            )
            c.run(
                "INSERT INTO transactions (id, purpose, amountCentavos, occurredOn, fromWalletId, toWalletId, loanId, createdAt) VALUES " +
                    "(1, 'LOAN_PAYMENT', 100000, ${LocalDate.of(2026, 11, 15).toEpochDay()}, 1, NULL, 7, 0)",
            )
        }
        return file
    }

    private fun SQLiteConnection.run(sql: String) = prepare(sql).use { it.step() }

    private fun SQLiteConnection.text(sql: String) = prepare(sql).use { it.step(); it.getText(0) }

    private fun open(file: File): FitakaDatabase =
        Room.databaseBuilder<FitakaDatabase>(name = file.absolutePath, factory = { FitakaDatabase_Impl() })
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()

    @Test
    fun theVersionOneFileIsWhatTheOwnersPhoneHolds() {
        val file = versionOneFile()
        BundledSQLiteDriver().open(file.absolutePath).use { c ->
            assertEquals(FITAKA_DB_IDENTITY_V1, c.text("SELECT identity_hash FROM room_master_table WHERE id = 42"))
        }
    }

    @Test
    fun aVersionOneDatabaseOpensWithEveryLoanWalletAndEntryKept() = runBlocking {
        val db = open(versionOneFile())
        val loan = db.loanDao().observeWithPaid(7).first()!!
        assertEquals("Tita Baby", loan.loan.counterparty)
        assertEquals(LoanDirection.I_OWE, loan.loan.direction)
        assertEquals(1_200_000L, loan.loan.totalPayableCentavos)
        assertEquals(LocalDate.of(2026, 7, 15), loan.loan.dueDate)
        assertEquals(100_000L, loan.loan.installmentCentavos)
        assertEquals("from June", loan.loan.note)
        assertEquals("nothing was paid before the app on a loan from version 1", 0L, loan.loan.paidBeforeCentavos)
        assertNull("and it is not paid twice a month", loan.loan.secondDueDay)
        assertEquals("paid = the one payment, as before", 100_000L, loan.paidCentavos)
        assertEquals("Cash still 5,000 less the payment", 400_000L, db.walletDao().observeActiveWithBalance().first().single().balanceCentavos)
        db.close()
    }

    @Test
    fun afterTheUpgradeTheFileIsVersionTwoWithItsFingerprint() = runBlocking {
        val file = versionOneFile()
        open(file).apply { loanDao().observeOpen().first(); close() }
        BundledSQLiteDriver().open(file.absolutePath).use { c ->
            assertEquals(FITAKA_DB_VERSION.toString(), c.text("PRAGMA user_version"))
            assertEquals(FITAKA_DB_IDENTITY, c.text("SELECT identity_hash FROM room_master_table WHERE id = 42"))
            assertEquals("ok", c.text("PRAGMA integrity_check"))
        }
    }
}
