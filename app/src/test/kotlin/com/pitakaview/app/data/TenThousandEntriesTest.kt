package com.pitakaview.app.data

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.pitakaview.app.data.db.PitakaDatabase
import com.pitakaview.app.data.db.PitakaDatabase_Impl
import com.pitakaview.app.data.db.entity.CategoryEntity
import com.pitakaview.app.data.db.entity.LoanEntity
import com.pitakaview.app.data.db.entity.SavingsGoalEntity
import com.pitakaview.app.data.db.entity.TransactionEntity
import com.pitakaview.app.data.db.entity.WalletEntity
import com.pitakaview.app.data.repo.LedgerRepository
import com.pitakaview.app.domain.CategoryKind
import com.pitakaview.app.domain.LoanDirection
import com.pitakaview.app.domain.PaydayTracker
import com.pitakaview.app.domain.TxPurpose
import com.pitakaview.app.domain.WalletType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * The queries behind every screen, run against 10,000 entries spread over almost three years: several
 * times what a person logging ten entries a day builds up in a year.
 *
 * Two things are checked. The answers are still right (the totals match what was put in). And no query
 * is slow: each must finish well inside one-tenth of a second here. This runs on a desktop SQLite, which
 * is faster than a phone, so the limit is set far below what a phone would need to feel instant; its
 * job is to catch a query that stops using its index or starts reading the whole table per row.
 */
class TenThousandEntriesTest {
    private lateinit var db: PitakaDatabase
    private lateinit var ledger: LedgerRepository
    private val now = Instant.parse("2026-11-26T04:00:00Z")
    private val today = LocalDate.of(2026, 11, 26)
    private val firstDay = LocalDate.of(2024, 1, 1)
    private val days = ChronoUnit.DAYS.between(firstDay, today).toInt() + 1
    private val wallets = ArrayList<Long>()
    private val expenseCategories = ArrayList<Long>()
    private var salary = 0L
    private var loan = 0L
    private var goal = 0L

    private var expectedSpentThisMonth = 0L
    private var expectedEarnedThisMonth = 0L
    private var expectedEntriesThisMonth = 0
    private val expectedBalance = HashMap<Long, Long>()

    private companion object {
        const val ENTRIES = 10_000
        const val LIMIT_MS = 100L
    }

    @Before
    fun fill() = runBlocking {
        db = Room.inMemoryDatabaseBuilder { PitakaDatabase_Impl() }
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        ledger = LedgerRepository(db)
        listOf("Cash" to WalletType.CASH, "GCash" to WalletType.E_WALLET, "MariBank" to WalletType.BANK).forEach { (name, type) ->
            wallets += db.walletDao().insert(WalletEntity(name = name, type = type, createdAt = now))
        }
        listOf("Food", "Transport", "Bills", "Groceries", "Health", "Fun", "Other").forEach { name ->
            expenseCategories += db.categoryDao().insert(CategoryEntity(name = name, kind = CategoryKind.EXPENSE, iconKey = "x"))
        }
        salary = db.categoryDao().insert(CategoryEntity(name = "Salary", kind = CategoryKind.INCOME, iconKey = "x"))
        loan = db.loanDao().insert(
            LoanEntity(direction = LoanDirection.I_OWE, counterparty = "Tita Baby", principalCentavos = 90_000_000, totalPayableCentavos = 90_000_000, startDate = firstDay, createdAt = now),
        )
        goal = db.savingsGoalDao().insert(SavingsGoalEntity(name = "House", createdAt = now))

        // The same entries every run: no randomness, so a failure can be reproduced.
        val monthStart = today.withDayOfMonth(1)
        val rows = (0 until ENTRIES).map { i ->
            val date = firstDay.plusDays((i.toLong() * 7_919) % days)
            val wallet = wallets[i % wallets.size]
            val amount = 1_000L + (i % 97) * 250L
            val row = when (i % 20) {
                0, 1 -> TransactionEntity(purpose = TxPurpose.INCOME, amountCentavos = amount * 20, occurredOn = date, toWalletId = wallet, categoryId = salary, note = "Pay $i", createdAt = now)
                2 -> TransactionEntity(purpose = TxPurpose.TRANSFER, amountCentavos = amount, occurredOn = date, fromWalletId = wallet, toWalletId = wallets[(i + 1) % wallets.size], createdAt = now)
                3 -> TransactionEntity(purpose = TxPurpose.LOAN_PAYMENT, amountCentavos = amount, occurredOn = date, fromWalletId = wallet, loanId = loan, createdAt = now)
                4 -> TransactionEntity(purpose = TxPurpose.SAVINGS_DEPOSIT, amountCentavos = amount, occurredOn = date, fromWalletId = wallet, toWalletId = wallet, goalId = goal, createdAt = now)
                else -> TransactionEntity(
                    purpose = TxPurpose.EXPENSE, amountCentavos = amount, occurredOn = date, fromWalletId = wallet,
                    categoryId = expenseCategories[i % expenseCategories.size], // (4,245 is one of the rows that is spending: 4,245 % 20 = 5.)
                    note = if (i == 4_245) "Lechon for the fiesta" else "Entry $i", createdAt = now,
                )
            }
            row.fromWalletId?.let { expectedBalance[it] = (expectedBalance[it] ?: 0) - row.amountCentavos }
            row.toWalletId?.let { expectedBalance[it] = (expectedBalance[it] ?: 0) + row.amountCentavos }
            if (date >= monthStart) {
                expectedEntriesThisMonth++
                if (row.purpose == TxPurpose.EXPENSE) expectedSpentThisMonth += row.amountCentavos
                if (row.purpose == TxPurpose.INCOME) expectedEarnedThisMonth += row.amountCentavos
            }
            row
        }
        rows.chunked(1_000).forEach { ledger.addAll(it) }
    }

    @After
    fun close() = db.close()

    /** The middle time of five runs after one to warm up, in milliseconds. */
    private suspend fun <T> timed(name: String, results: MutableList<Pair<String, Long>>, query: suspend () -> T): T {
        var last = query()
        val times = (1..5).map {
            val start = System.nanoTime()
            last = query()
            (System.nanoTime() - start) / 1_000_000
        }.sorted()
        results += name to times[2]
        return last
    }

    @Test
    fun everyScreensQueriesStayRightAndFast() = runBlocking {
        val tx = db.transactionDao()
        val monthStart = today.withDayOfMonth(1)
        val monthEnd = today.withDayOfMonth(today.lengthOfMonth())
        val took = mutableListOf<Pair<String, Long>>()

        val balances = timed("Home: wallet balances", took) { db.walletDao().observeActiveWithBalance().first() }
        val totals = timed("Home: this month's totals", took) { tx.observeTotalsByPurpose(monthStart, monthEnd).first() }
        val recent = timed("Home: the five latest entries", took) { tx.observeRecent(5).first() }
        timed("Home: open loans with what is paid", took) { db.loanDao().observeOpen().first() }
        timed("Home: savings goals with what is saved", took) { db.savingsGoalDao().observeProgress().first() }
        timed("Home: pay already received", took) { tx.observePayReceipts(today.minusDays(PaydayTracker.LOOK_BACK_DAYS)).first() }
        val month = timed("Gastos: this month's spending", took) { tx.observeEntries(TxPurpose.EXPENSE, monthStart, monthEnd).first() }
        val byCategory = timed("Gastos: this month by category", took) { tx.observeCategoryTotals(TxPurpose.EXPENSE, monthStart, monthEnd).first() }
        val sixMonths = timed("Summary: six months of totals", took) { tx.observeMonthlyTotals(monthStart.minusMonths(5), monthEnd).first() }
        val found = timed("Search: a word in a note", took) { tx.search("%lechon%", null, 100).first() }
        val byAmount = timed("Search: an amount", took) { tx.search("%1250%", 1_250, 100).first() }
        timed("Utang: one loan's entries", took) { tx.observeForLoan(loan).first() }
        val marks = timed("Import: what a wallet has in a month", took) { tx.marksFor(wallets[0], monthStart, monthEnd) }
        val exported = timed("Export: every entry", took) { tx.exportRows() }

        println("With $ENTRIES entries (desktop SQLite, middle of 5 runs):")
        took.forEach { (name, ms) -> println("  ${ms.toString().padStart(4)} ms  $name") }

        // Still right.
        assertEquals(expectedBalance, balances.associate { it.wallet.id to it.balanceCentavos })
        assertEquals(expectedSpentThisMonth, totals.first { it.purpose == TxPurpose.EXPENSE }.totalCentavos)
        assertEquals(expectedEarnedThisMonth, totals.first { it.purpose == TxPurpose.INCOME }.totalCentavos)
        assertEquals(5, recent.size)
        assertTrue("newest first", recent.zipWithNext().all { (a, b) -> a.occurredOn >= b.occurredOn })
        assertEquals(expectedSpentThisMonth, month.sumOf { it.amountCentavos })
        assertEquals(expectedSpentThisMonth, byCategory.sumOf { it.totalCentavos })
        assertTrue("only this month is read for the month's list", month.size < expectedEntriesThisMonth + 1 && month.all { it.occurredOn >= monthStart })
        assertEquals(6, sixMonths.map { it.month }.distinct().size)
        assertEquals("Lechon for the fiesta", found.single().note)
        assertTrue(byAmount.isNotEmpty() && byAmount.size <= 100)
        assertTrue(marks.all { it.date >= monthStart })
        assertEquals(ENTRIES, exported.size)

        // Still fast.
        val slow = took.filter { it.second > LIMIT_MS }
        assertTrue("slower than $LIMIT_MS ms: $slow", slow.isEmpty())
    }
}
