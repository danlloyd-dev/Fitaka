package com.pitakaview.app.data

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.pitakaview.app.data.db.PitakaDatabase
import com.pitakaview.app.data.db.PitakaDatabase_Impl
import com.pitakaview.app.data.db.dao.remainingCentavos
import com.pitakaview.app.data.db.entity.BaonEntity
import com.pitakaview.app.data.db.entity.CategoryEntity
import com.pitakaview.app.data.db.entity.ImportBatchEntity
import com.pitakaview.app.data.db.entity.LoanEntity
import com.pitakaview.app.data.db.entity.SavingsGoalEntity
import com.pitakaview.app.data.db.entity.TransactionEntity
import com.pitakaview.app.data.db.entity.WalletEntity
import com.pitakaview.app.data.repo.LedgerRepository
import com.pitakaview.app.domain.BaonDirection
import com.pitakaview.app.domain.BaonMath
import com.pitakaview.app.domain.BaonPeriod
import com.pitakaview.app.domain.CategoryKind
import com.pitakaview.app.domain.ImportSource
import com.pitakaview.app.domain.LoanDirection
import com.pitakaview.app.domain.TxPurpose
import com.pitakaview.app.domain.TxPurpose.BAON_RECEIVED
import com.pitakaview.app.domain.TxPurpose.EXPENSE
import com.pitakaview.app.domain.TxPurpose.INCOME
import com.pitakaview.app.domain.TxPurpose.LOAN_PAYMENT
import com.pitakaview.app.domain.TxPurpose.LOAN_START
import com.pitakaview.app.domain.TxPurpose.SAVINGS_DEPOSIT
import com.pitakaview.app.domain.TxPurpose.TRANSFER
import com.pitakaview.app.domain.WalletType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * Runs the real Room DAOs and SQL against an in-memory SQLite (the desktop build of the bundled
 * driver), so balances and totals are checked end to end without an emulator.
 */
class LedgerDatabaseTest {
    private lateinit var db: PitakaDatabase
    private lateinit var ledger: LedgerRepository

    private val now = Instant.parse("2026-11-25T08:00:00Z")
    private val nov1 = LocalDate.of(2026, 11, 1)
    private val nov15 = LocalDate.of(2026, 11, 15)
    private val nov30 = LocalDate.of(2026, 11, 30)

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder { PitakaDatabase_Impl() }
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        ledger = LedgerRepository(db)
    }

    @After
    fun close() = db.close()

    private suspend fun wallet(name: String, type: WalletType, opening: Long = 0) =
        db.walletDao().insert(WalletEntity(name = name, type = type, openingBalanceCentavos = opening, createdAt = now))

    private suspend fun category(name: String, kind: CategoryKind) =
        db.categoryDao().insert(CategoryEntity(name = name, kind = kind, iconKey = "x"))

    private fun tx(
        purpose: TxPurpose,
        amount: Long,
        from: Long? = null,
        to: Long? = null,
        category: Long? = null,
        baon: Long? = null,
        loan: Long? = null,
        on: LocalDate = nov15,
    ) = TransactionEntity(
        purpose = purpose, amountCentavos = amount, occurredOn = on, fromWalletId = from, toWalletId = to,
        categoryId = category, baonId = baon, loanId = loan, createdAt = now,
    )

    private suspend fun balances() =
        db.walletDao().observeActiveWithBalance().first().associate { it.wallet.name to it.balanceCentavos }

    private suspend inline fun <reified T : Throwable> assertThrowsSuspend(block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            if (e is T) return
            throw AssertionError("Expected ${T::class.simpleName} but got $e", e)
        }
        throw AssertionError("Expected ${T::class.simpleName}, but nothing was thrown")
    }

    @Test
    fun balancesTotalsAndGoalsAllComeFromTheLedger() = runTest {
        val cash = wallet("Cash", WalletType.CASH, opening = 100_000)
        val gcash = wallet("GCash", WalletType.E_WALLET)
        val mari = wallet("MariBank", WalletType.BANK, opening = 500_000)
        val salary = category("Salary", CategoryKind.INCOME)
        val food = category("Food", CategoryKind.EXPENSE)

        ledger.add(tx(INCOME, 2_000_000, to = mari, category = salary))
        ledger.add(tx(TRANSFER, 300_000, from = mari, to = gcash)) // GCash cash-in
        ledger.add(tx(EXPENSE, 25_000, from = gcash, category = food))
        ledger.add(tx(EXPENSE, 15_000, from = cash, category = food))
        val emergency = db.savingsGoalDao().insert(
            SavingsGoalEntity(name = "Emergency fund", targetCentavos = 5_000_000, walletId = mari, createdAt = now),
        )
        val laptop = db.savingsGoalDao().insert(SavingsGoalEntity(name = "Laptop", targetCentavos = 4_000_000, createdAt = now))
        ledger.depositToGoal(emergency, fromWalletId = mari, amountCentavos = 500_000, on = nov15, now = now)
        ledger.depositToGoal(laptop, fromWalletId = gcash, amountCentavos = 100_000, on = nov15, now = now)

        // Earmarked deposits stay in their wallet, so they change no balance.
        assertEquals(mapOf("Cash" to 85_000L, "GCash" to 275_000L, "MariBank" to 2_200_000L), balances())
        assertEquals(
            mapOf("Emergency fund" to 500_000L, "Laptop" to 100_000L),
            db.savingsGoalDao().observeProgress().first().associate { it.name to it.savedCentavos },
        )
        assertEquals(
            mapOf(INCOME to 2_000_000L, TRANSFER to 300_000L, EXPENSE to 40_000L, SAVINGS_DEPOSIT to 600_000L),
            db.transactionDao().observeTotalsByPurpose(nov1, nov30).first().associate { it.purpose to it.totalCentavos },
        )
        assertEquals(
            listOf("Food" to 40_000L),
            db.transactionDao().observeCategoryTotals(EXPENSE, nov1, nov30).first().map { it.name to it.totalCentavos },
        )
        assertEquals(
            setOf("2026-11"),
            db.transactionDao().observeMonthlyTotals(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))
                .first().map { it.month }.toSet(),
        )
    }

    @Test
    fun loansShowWhatIsLeftBothWays() = runTest {
        val cash = wallet("Cash", WalletType.CASH, opening = 500_000)
        val gcash = wallet("GCash", WalletType.E_WALLET)
        val iOwe = db.loanDao().insert(
            LoanEntity(
                direction = LoanDirection.I_OWE, counterparty = "Tita Baby", principalCentavos = 1_000_000,
                totalPayableCentavos = 1_100_000, startDate = nov1, dueDate = LocalDate.of(2027, 2, 28), createdAt = now,
            ),
        )
        val owedToMe = db.loanDao().insert(
            LoanEntity(
                direction = LoanDirection.OWED_TO_ME, counterparty = "Kuya Jun", principalCentavos = 300_000,
                totalPayableCentavos = 300_000, startDate = nov1, dueDate = LocalDate.of(2026, 12, 15), createdAt = now,
            ),
        )
        ledger.add(tx(LOAN_START, 1_000_000, to = gcash, loan = iOwe))
        ledger.add(tx(LOAN_PAYMENT, 200_000, from = gcash, loan = iOwe))
        ledger.add(tx(LOAN_START, 300_000, from = cash, loan = owedToMe))
        ledger.add(tx(LOAN_PAYMENT, 100_000, to = cash, loan = owedToMe))

        // Soonest due first.
        assertEquals(
            listOf("Kuya Jun" to 200_000L, "Tita Baby" to 900_000L),
            db.loanDao().observeOpen().first().map { it.loan.counterparty to it.remainingCentavos },
        )
        assertEquals(mapOf("Cash" to 300_000L, "GCash" to 800_000L), balances())
        // Paying a loan I owe INTO my wallet is rejected before it reaches the database.
        assertThrowsSuspend<IllegalArgumentException> { ledger.add(tx(LOAN_PAYMENT, 1, to = gcash, loan = iOwe)) }
    }

    @Test
    fun baonLeftCountsOnlyThisPeriod() = runTest {
        val cash = wallet("Cash", WalletType.CASH)
        val food = category("Food", CategoryKind.EXPENSE)
        val baonId = db.baonDao().insert(
            BaonEntity(
                name = "My baon", direction = BaonDirection.RECEIVE, amountCentavos = 50_000,
                period = BaonPeriod.WEEKLY, startDate = LocalDate.of(2026, 11, 2), walletId = cash, createdAt = now,
            ),
        )
        ledger.add(tx(BAON_RECEIVED, 50_000, to = cash, baon = baonId, on = LocalDate.of(2026, 11, 16)))
        ledger.add(tx(BAON_RECEIVED, 50_000, to = cash, baon = baonId, on = LocalDate.of(2026, 11, 23)))
        ledger.add(tx(EXPENSE, 12_000, from = cash, category = food, baon = baonId, on = LocalDate.of(2026, 11, 24)))
        ledger.add(tx(EXPENSE, 9_999, from = cash, category = food, on = LocalDate.of(2026, 11, 24))) // not from baon

        val rows = db.transactionDao().observeBaonActivity(since = LocalDate.of(2026, 10, 25)).first()
        val summary = BaonMath.summarize(db.baonDao().get(baonId)!!, rows, today = LocalDate.of(2026, 11, 25))
        assertEquals(50_000L, summary.budgetCentavos)
        assertEquals(12_000L, summary.usedCentavos)
        assertEquals(38_000L, summary.leftCentavos)
    }

    @Test
    fun databaseRefusesBrokenLinksAndDuplicateImports() = runTest {
        val gcash = wallet("GCash", WalletType.E_WALLET)
        val food = category("Food", CategoryKind.EXPENSE)

        // Foreign keys are enforced: a row pointing at a wallet that does not exist is refused.
        assertThrowsSuspend<Exception> {
            db.transactionDao().insert(tx(EXPENSE, 1_000, from = 999, category = food))
        }

        val batch = ImportBatchEntity(
            source = ImportSource.GCASH, walletId = gcash, fileName = "gcash-nov.pdf", rowCount = 2, importedAt = now,
        )
        val batchId = db.importBatchDao().insert(batch)
        ledger.add(tx(EXPENSE, 10_000, from = gcash, category = food).copy(importBatchId = batchId, externalRef = "GCASH:1001"))
        ledger.add(tx(EXPENSE, 20_000, from = gcash, category = food).copy(importBatchId = batchId, externalRef = "GCASH:1002"))
        assertEquals(mapOf("GCash" to -30_000L), balances())

        // Importing the same GCash reference twice is refused.
        assertThrowsSuspend<Exception> {
            ledger.add(tx(EXPENSE, 10_000, from = gcash, category = food).copy(externalRef = "GCASH:1001"))
        }

        // Undoing the import removes its rows (ON DELETE CASCADE).
        db.importBatchDao().delete(batch.copy(id = batchId))
        assertFalse(db.transactionDao().existsExternalRef("GCASH:1001"))
        assertEquals(mapOf("GCash" to 0L), balances())
    }

    @Test
    fun savingsWithoutATargetStillAddUp() = runTest {
        val cash = wallet("Cash", WalletType.CASH, opening = 300_000)
        val pot = db.savingsGoalDao().insert(SavingsGoalEntity(name = "Rainy day", createdAt = now))
        ledger.depositToGoal(pot, fromWalletId = cash, amountCentavos = 50_000, on = nov15, now = now)

        val progress = db.savingsGoalDao().observeProgress().first().single()
        assertEquals(null, progress.targetCentavos)
        assertEquals(50_000L, progress.savedCentavos)
    }

    @Test
    fun addAllKeepsEveryRowOrNone() = runTest {
        val cash = wallet("Cash", WalletType.CASH, opening = 100_000)
        val food = category("Food", CategoryKind.EXPENSE)
        val good = tx(EXPENSE, 10_000, from = cash, category = food)
        // Passes the ledger rules but points at a wallet that does not exist, so the database refuses it.
        val refusedByDatabase = tx(EXPENSE, 5_000, from = 999, category = food)

        // The second row fails, so the first must be undone too.
        assertThrowsSuspend<Exception> { ledger.addAll(listOf(good, refusedByDatabase)) }
        assertEquals(mapOf("Cash" to 100_000L), balances())

        assertEquals(2, ledger.addAll(listOf(good, good.copy(amountCentavos = 2_500))).size)
        assertEquals(mapOf("Cash" to 87_500L), balances())
    }

    @Test
    fun defaultDataIsAddedOnlyOnce() = runTest {
        DefaultData.seedIfEmpty(db, now)
        DefaultData.seedIfEmpty(db, now)
        assertEquals(DefaultData.expenseCategories.size + DefaultData.incomeCategories.size, db.categoryDao().count())
        assertEquals(1, db.walletDao().count())
        assertTrue(db.categoryDao().observeActive(CategoryKind.EXPENSE).first().any { it.name == "Food" })
    }
}
