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
import com.pitakaview.app.domain.WalletType
import com.pitakaview.app.domain.importing.ImportPlanner
import com.pitakaview.app.domain.importing.LedgerMark
import com.pitakaview.app.domain.importing.StatementRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** An import against real SQL: stored whole or not at all, recognised the next time, and taken back out in one step. */
class ImportDatabaseTest {
    private lateinit var db: FitakaDatabase
    private lateinit var ledger: LedgerRepository
    private val now = Instant.parse("2026-11-26T04:00:00Z")
    private val nov3 = LocalDate.of(2026, 11, 3)
    private var cash = 0L
    private var gcash = 0L
    private lateinit var food: CategoryEntity
    private lateinit var salary: CategoryEntity

    @Before
    fun open() = runBlocking {
        db = Room.inMemoryDatabaseBuilder { FitakaDatabase_Impl() }
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        ledger = LedgerRepository(db)
        cash = db.walletDao().insert(WalletEntity(name = "Cash", type = WalletType.CASH, createdAt = now))
        gcash = db.walletDao().insert(WalletEntity(name = "GCash", type = WalletType.E_WALLET, createdAt = now))
        food = CategoryEntity(name = "Food", kind = CategoryKind.EXPENSE, iconKey = "food").let { it.copy(id = db.categoryDao().insert(it)) }
        salary = CategoryEntity(name = "Salary", kind = CategoryKind.INCOME, iconKey = "salary").let { it.copy(id = db.categoryDao().insert(it)) }
    }

    @After
    fun close() = db.close()

    private fun row(description: String, centavos: Long, moneyIn: Boolean = false, reference: String? = null, date: LocalDate = nov3) =
        StatementRow(date, description, reference, centavos, moneyIn)

    private suspend fun import(walletId: Long, fileName: String, rows: List<StatementRow>): Long {
        val refs = rows.mapNotNull { ImportPlanner.refFor(ImportSource.CSV, walletId, it) }
        val plan = ImportPlanner.plan(
            rows, ImportSource.CSV, walletId, ledger.knownRefs(refs),
            db.transactionDao().marksFor(walletId, rows.minOf { it.date }, rows.maxOf { it.date }), listOf(food), listOf(salary),
        )
        return ledger.importBatch(ImportBatchEntity(source = ImportSource.CSV, walletId = walletId, fileName = fileName, rowCount = plan.count { it.picked }, importedAt = now)) { id ->
            ImportPlanner.toTransactions(plan, walletId, id, now)
        }
    }

    private suspend fun entries() = db.transactionDao().observeRecent(50).first()

    private suspend fun balance(walletId: Long) = db.walletDao().observeActiveWithBalance().first().first { it.wallet.id == walletId }.balanceCentavos

    @Test
    fun anImportAddsItsEntriesAndIsListedWithItsWallet() = runBlocking {
        import(gcash, "november.csv", listOf(row("Jollibee", 25_000, reference = "R1"), row("Payroll", 1_500_000, moneyIn = true, reference = "R2")))

        assertEquals(2, entries().size)
        assertEquals(1_475_000L, balance(gcash))
        assertEquals(0L, balance(cash))
        val listed = db.importBatchDao().observeAll().first().single()
        assertEquals("november.csv", listed.fileName)
        assertEquals("GCash", listed.walletName)
        assertEquals(2, listed.entryCount)
    }

    @Test
    fun theSameFileASecondTimeAddsNothing() = runBlocking {
        val file = listOf(row("Jollibee", 25_000, reference = "R1"), row("Fare", 2_500, reference = "R2"))
        import(gcash, "november.csv", file)
        import(gcash, "november (1).csv", file + row("New line", 1_000, reference = "R3"))

        assertEquals("only the new line was added", 3, entries().size)
        assertEquals(setOf("CSV:$gcash:R1:out:25000"), ledger.knownRefs(listOf("CSV:$gcash:R1:out:25000", "CSV:$gcash:R9:out:1")))
        // The same file into another wallet is another statement: its lines are new there.
        import(cash, "november.csv", file)
        assertEquals(5, entries().size)
    }

    @Test
    fun takingAnImportBackOutRemovesOnlyWhatItAdded() = runBlocking {
        val typed = ledger.add(
            TransactionEntity(purpose = TxPurpose.EXPENSE, amountCentavos = 9_900, occurredOn = nov3, fromWalletId = gcash, categoryId = food.id, createdAt = now),
        )
        val first = import(gcash, "one.csv", listOf(row("A", 1_000, reference = "A1"), row("B", 2_000, reference = "B1")))
        import(gcash, "two.csv", listOf(row("C", 3_000, reference = "C1")))
        assertEquals(-15_900L, balance(gcash))

        ledger.undoImport(first)

        assertEquals(listOf("C", null), entries().map { it.note })
        assertTrue(entries().any { it.id == typed })
        assertEquals(-12_900L, balance(gcash))
        assertEquals(listOf("two.csv"), db.importBatchDao().observeAll().first().map { it.fileName })
        // Taken out, its lines can be brought in again.
        assertEquals(emptySet<String>(), ledger.knownRefs(listOf("CSV:$gcash:A1:out:1000")))
        // Undoing something that is no longer there does nothing.
        ledger.undoImport(first)
        ledger.undoImport(9_999)
        assertEquals(2, entries().size)
    }

    @Test
    fun aFileIsStoredWholeOrNotAtAll() = runBlocking {
        val batch = ImportBatchEntity(source = ImportSource.CSV, walletId = gcash, fileName = "broken.csv", rowCount = 2, importedAt = now)
        val good = TransactionEntity(purpose = TxPurpose.EXPENSE, amountCentavos = 1_000, occurredOn = nov3, fromWalletId = gcash, categoryId = food.id, createdAt = now)
        try {
            // The second row breaks a ledger rule (spending needs a category).
            ledger.importBatch(batch) { id -> listOf(good.copy(importBatchId = id), good.copy(importBatchId = id, categoryId = null)) }
            fail("a row that breaks the ledger's rules must be refused")
        } catch (expected: IllegalArgumentException) {
            // refused
        }
        assertEquals("neither the first row nor the record of the file was kept", 0, entries().size)
        assertEquals(0, db.importBatchDao().observeAll().first().size)

        try {
            // Two rows with the same key: the database's own guard against a double import.
            ledger.importBatch(batch) { id -> listOf(good.copy(importBatchId = id, externalRef = "K"), good.copy(importBatchId = id, externalRef = "K")) }
            fail("the same key twice must be refused")
        } catch (expected: Exception) {
            // refused by the unique index
        }
        assertEquals(0, entries().size)
        assertEquals(0, db.importBatchDao().observeAll().first().size)
    }

    @Test
    fun whatAWalletAlreadyHasIsReadAsDayAmountAndDirection() = runBlocking {
        ledger.add(TransactionEntity(purpose = TxPurpose.EXPENSE, amountCentavos = 25_000, occurredOn = nov3, fromWalletId = gcash, categoryId = food.id, createdAt = now))
        ledger.add(TransactionEntity(purpose = TxPurpose.INCOME, amountCentavos = 1_500_000, occurredOn = nov3.plusDays(2), toWalletId = gcash, categoryId = salary.id, createdAt = now))
        // A move from Cash to GCash is money in for GCash and money out for Cash.
        ledger.add(TransactionEntity(purpose = TxPurpose.TRANSFER, amountCentavos = 50_000, occurredOn = nov3.plusDays(1), fromWalletId = cash, toWalletId = gcash, createdAt = now))
        // Outside the dates asked for.
        ledger.add(TransactionEntity(purpose = TxPurpose.EXPENSE, amountCentavos = 1, occurredOn = nov3.minusDays(1), fromWalletId = gcash, categoryId = food.id, createdAt = now))

        assertEquals(
            setOf(LedgerMark(nov3, 25_000, false), LedgerMark(nov3.plusDays(1), 50_000, true), LedgerMark(nov3.plusDays(2), 1_500_000, true)),
            db.transactionDao().marksFor(gcash, nov3, nov3.plusDays(2)).toSet(),
        )
        assertEquals(listOf(LedgerMark(nov3.plusDays(1), 50_000, false)), db.transactionDao().marksFor(cash, nov3, nov3.plusDays(2)))

        // So a statement line for the lunch typed by hand is not added a second time.
        import(gcash, "november.csv", listOf(row("Lunch", 25_000), row("Fare", 2_500)))
        assertEquals("only the fare is new", 5, entries().size)
    }

    @Test
    fun manyReferencesAreLookedUpInChunks() = runBlocking {
        val rows = (1..1_200).map { row("Line $it", 100L + it, reference = "R$it") }
        import(gcash, "big.csv", rows)
        val keys = rows.map { ImportPlanner.refFor(ImportSource.CSV, gcash, it)!! }
        assertEquals(1_200, ledger.knownRefs(keys).size)
        assertEquals(1_200, db.importBatchDao().observeAll().first().single().entryCount)
    }
}
