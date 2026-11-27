package com.pitakaview.app.data

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.pitakaview.app.data.db.FitakaDatabase
import com.pitakaview.app.data.db.FitakaDatabase_Impl
import com.pitakaview.app.data.db.entity.CategoryEntity
import com.pitakaview.app.data.db.entity.ImportBatchEntity
import com.pitakaview.app.data.db.entity.TransactionEntity
import com.pitakaview.app.data.db.entity.WalletEntity
import com.pitakaview.app.data.repo.LedgerRepository
import com.pitakaview.app.domain.CategoryKind
import com.pitakaview.app.domain.ImportSource
import com.pitakaview.app.domain.TxPurpose
import com.pitakaview.app.domain.TxPurpose.EXPENSE
import com.pitakaview.app.domain.TxPurpose.INCOME
import com.pitakaview.app.domain.TxPurpose.OWN_MOVE_IN
import com.pitakaview.app.domain.TxPurpose.OWN_MOVE_OUT
import com.pitakaview.app.domain.TxPurpose.TRANSFER
import com.pitakaview.app.domain.WalletType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * A move between two of the owner's own wallets, as two bank statements show it, against real Room and
 * SQL in memory: pairing, what the totals then count, and every way a pair comes apart again (a file
 * taken back out, a half deleted, the owner saying it was spending after all). Balances never move.
 */
class OwnMovesDatabaseTest {
    private lateinit var db: FitakaDatabase
    private lateinit var ledger: LedgerRepository

    private val now = Instant.parse("2026-11-25T08:00:00Z")
    private val nov1 = LocalDate.of(2026, 11, 1)
    private val nov3 = LocalDate.of(2026, 11, 3)
    private val nov4 = LocalDate.of(2026, 11, 4)
    private val nov30 = LocalDate.of(2026, 11, 30)

    private var gcash = 0L
    private var mari = 0L
    private var food = 0L
    private var otherSpending = 0L
    private var otherIncome = 0L

    @Before
    fun open() = runTest {
        db = Room.inMemoryDatabaseBuilder { FitakaDatabase_Impl() }
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        ledger = LedgerRepository(db)
        gcash = db.walletDao().insert(WalletEntity(name = "GCash", type = WalletType.E_WALLET, createdAt = now))
        mari = db.walletDao().insert(WalletEntity(name = "MariBank", type = WalletType.BANK, createdAt = now))
        food = db.categoryDao().insert(CategoryEntity(name = "Food", kind = CategoryKind.EXPENSE, iconKey = "x"))
        otherSpending = db.categoryDao().insert(CategoryEntity(name = "Other", kind = CategoryKind.EXPENSE, iconKey = "x"))
        otherIncome = db.categoryDao().insert(CategoryEntity(name = "Other", kind = CategoryKind.INCOME, iconKey = "x"))
    }

    @After
    fun close() = db.close()

    private suspend fun total(purpose: TxPurpose) = db.transactionDao().observeTotalsByPurpose(nov1, nov30).first().firstOrNull { it.purpose == purpose }?.totalCentavos ?: 0L

    private suspend fun balance(wallet: Long) = db.walletDao().observeAllWithBalance().first().first { it.wallet.id == wallet }.balanceCentavos

    private suspend fun byRef(ref: String) = db.transactionDao().get(db.transactionDao().observeRecent(50).first().first { it.note == ref }.id)!!

    private fun out(centavos: Long, on: LocalDate, note: String, batch: Long?, category: Long = otherSpending) =
        TransactionEntity(purpose = EXPENSE, amountCentavos = centavos, occurredOn = on, fromWalletId = gcash, categoryId = category, note = note, importBatchId = batch, externalRef = batch?.let { "G:$note" }, createdAt = now)

    private fun into(centavos: Long, on: LocalDate, note: String, batch: Long, purpose: TxPurpose = OWN_MOVE_IN) =
        TransactionEntity(purpose = purpose, amountCentavos = centavos, occurredOn = on, toWalletId = mari, categoryId = otherIncome, note = note, importBatchId = batch, externalRef = "M:$note", createdAt = now)

    /** GCash's statement, brought in first: 5,000.00 left it on the 3rd, and there was a 250.00 lunch. */
    private suspend fun gcashStatement(): Long =
        ledger.importBatch(ImportBatchEntity(source = ImportSource.GCASH, walletId = gcash, fileName = "gcash.pdf", rowCount = 2, importedAt = now)) { batch ->
            listOf(out(500_000, nov3, "to-mari", batch), out(25_000, nov3, "lunch", batch, category = food))
        }

    /** MariBank's statement: the 5,000.00 arrives on the 4th, brought in as the other half of the GCash entry. */
    private suspend fun mariStatementPairedWith(entryId: Long): Long =
        ledger.importBatch(ImportBatchEntity(source = ImportSource.MARIBANK, walletId = mari, fileName = "mari.pdf", rowCount = 1, importedAt = now), pairWith = listOf(entryId)) { batch ->
            listOf(into(500_000, nov4, "from-gcash", batch))
        }

    @Test
    fun aPairedMoveCountsAsNeitherSpendingNorIncomeAndNoBalanceMoves() = runTest {
        gcashStatement()
        assertEquals("before the other statement, the money that left counts as spending", 525_000L, total(EXPENSE))

        // What the review of MariBank's file is given to look through: GCash's plain entries of those days.
        val offered = db.transactionDao().ownMoveCandidates(mari, nov3.minusDays(1), nov4.plusDays(1), EXPENSE, INCOME)
        assertEquals(listOf(500_000L to "GCash", 25_000L to "GCash"), offered.map { it.amountCentavos to it.walletName })
        assertTrue("money that left a wallet", offered.none { it.moneyIn })

        mariStatementPairedWith(offered.first().entryId)

        assertEquals("only the lunch is spending", 25_000L, total(EXPENSE))
        assertEquals("and nothing is income", 0L, total(INCOME))
        assertEquals(OWN_MOVE_OUT, byRef("to-mari").purpose)
        assertEquals(OWN_MOVE_IN, byRef("from-gcash").purpose)
        assertEquals("the half keeps the category it had, to go back to", otherSpending, byRef("to-mari").categoryId)
        assertEquals("both wallets are as their statements say", -525_000L to 500_000L, balance(gcash) to balance(mari))
    }

    @Test
    fun takingTheSecondFileBackOutMakesTheFirstFilesLineSpendingAgain() = runTest {
        gcashStatement()
        val mariBatch = mariStatementPairedWith(byRef("to-mari").id)

        ledger.undoImport(mariBatch)

        assertEquals(EXPENSE, byRef("to-mari").purpose)
        assertEquals(otherSpending, byRef("to-mari").categoryId)
        assertEquals(525_000L to 0L, total(EXPENSE) to total(INCOME))
        assertEquals(-525_000L to 0L, balance(gcash) to balance(mari))
    }

    @Test
    fun takingTheFirstFileBackOutMakesTheSecondFilesLineIncomeAgain() = runTest {
        val gcashBatch = gcashStatement()
        mariStatementPairedWith(byRef("to-mari").id)

        ledger.undoImport(gcashBatch)

        assertEquals(INCOME, byRef("from-gcash").purpose)
        assertEquals(0L to 500_000L, total(EXPENSE) to total(INCOME))
        assertEquals(0L to 500_000L, balance(gcash) to balance(mari))
    }

    @Test
    fun deletingOneHalfMakesTheOtherPlainAgain() = runTest {
        gcashStatement()
        mariStatementPairedWith(byRef("to-mari").id)

        ledger.delete(byRef("from-gcash"))

        assertEquals(EXPENSE, byRef("to-mari").purpose)
        assertEquals(525_000L, total(EXPENSE))
    }

    @Test
    fun sayingAHalfWasSpendingAfterAllTakesItsOtherHalfBackToo() = runTest {
        gcashStatement()
        mariStatementPairedWith(byRef("to-mari").id)
        val half = byRef("to-mari")

        ledger.updateHalf(was = half, tx = half.copy(purpose = EXPENSE))

        assertEquals(EXPENSE, byRef("to-mari").purpose)
        assertEquals(INCOME, byRef("from-gcash").purpose)
        assertEquals(525_000L to 500_000L, total(EXPENSE) to total(INCOME))
    }

    @Test
    fun changingSomethingElseOnAHalfLeavesThePairAlone() = runTest {
        gcashStatement()
        mariStatementPairedWith(byRef("to-mari").id)
        val half = byRef("to-mari")

        ledger.updateHalf(was = half, tx = half.copy(categoryId = food))

        assertEquals(OWN_MOVE_OUT, byRef("to-mari").purpose)
        assertEquals(OWN_MOVE_IN, byRef("from-gcash").purpose)
        assertEquals(25_000L to 0L, total(EXPENSE) to total(INCOME))
    }

    /** Two moves of the same amount a day apart: when one half goes, one pair comes apart, the nearest, and the other stays. */
    @Test
    fun withTwoMovesOfOneAmountOnlyTheNearestPairComesApart() = runTest {
        val gcashBatch = ledger.importBatch(ImportBatchEntity(source = ImportSource.GCASH, walletId = gcash, fileName = "gcash.pdf", rowCount = 2, importedAt = now)) { batch ->
            listOf(out(500_000, nov3, "out-3rd", batch), out(500_000, nov4, "out-4th", batch))
        }
        ledger.importBatch(
            ImportBatchEntity(source = ImportSource.MARIBANK, walletId = mari, fileName = "mari.pdf", rowCount = 2, importedAt = now),
            pairWith = listOf(byRef("out-3rd").id, byRef("out-4th").id),
        ) { batch -> listOf(into(500_000, nov3, "in-3rd", batch), into(500_000, nov4, "in-4th", batch)) }
        assertEquals(0L to 0L, total(EXPENSE) to total(INCOME))

        ledger.delete(byRef("in-3rd"))

        assertEquals("the half of that day is spending again", EXPENSE, byRef("out-3rd").purpose)
        assertEquals("the other pair is whole", OWN_MOVE_OUT to OWN_MOVE_IN, byRef("out-4th").purpose to byRef("in-4th").purpose)

        // And taking GCash's file out leaves the one half that still had a pair as income.
        ledger.undoImport(gcashBatch)
        assertEquals(INCOME, byRef("in-4th").purpose)
    }

    /** All or nothing: if the entry to pair with is gone (or no longer plain) when Import is tapped, nothing of the file is stored. */
    @Test
    fun aFileIsNotStoredAtAllWhenTheEntryItPairsWithHasChanged() = runTest {
        gcashStatement()
        val entry = byRef("to-mari")
        ledger.delete(entry)

        try {
            mariStatementPairedWith(entry.id)
            fail("the import should have been refused")
        } catch (expected: IllegalStateException) {
            // refused
        }
        assertEquals("nothing reached MariBank", 0L, balance(mari))
        assertTrue(db.transactionDao().marksFor(mari, nov1, nov30).isEmpty())
        assertNull("and no record of the file is kept", db.importBatchDao().get(2))
    }

    @Test
    fun onlyPlainSpendingAndIncomeInOtherWalletsIsLookedThrough() = runTest {
        gcashStatement()
        // In the wallet being imported into: never its own other half.
        ledger.add(TransactionEntity(purpose = INCOME, amountCentavos = 500_000, occurredOn = nov3, toWalletId = mari, categoryId = otherIncome, createdAt = now))
        // A move typed in as one entry is a move already.
        ledger.add(TransactionEntity(purpose = TRANSFER, amountCentavos = 500_000, occurredOn = nov3, fromWalletId = gcash, toWalletId = mari, createdAt = now))
        // Outside the days asked for.
        ledger.add(out(500_000, nov3.minusDays(5), "long-before", batch = null))

        val offered = db.transactionDao().ownMoveCandidates(mari, nov3.minusDays(1), nov4.plusDays(1), EXPENSE, INCOME)
        assertEquals(listOf("GCash", "GCash"), offered.map { it.walletName })
        assertEquals(listOf(500_000L, 25_000L), offered.map { it.amountCentavos })

        // Once paired, a half is not offered again.
        mariStatementPairedWith(offered.first().entryId)
        assertEquals(listOf(25_000L), db.transactionDao().ownMoveCandidates(mari, nov3.minusDays(1), nov4.plusDays(1), EXPENSE, INCOME).map { it.amountCentavos })
    }
}
