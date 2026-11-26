package com.pitakaview.app.data

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.pitakaview.app.data.db.PitakaDatabase
import com.pitakaview.app.data.db.PitakaDatabase_Impl
import com.pitakaview.app.data.db.dao.PayReceipt
import com.pitakaview.app.data.db.entity.CategoryEntity
import com.pitakaview.app.data.db.entity.PayScheduleEntity
import com.pitakaview.app.data.db.entity.TransactionEntity
import com.pitakaview.app.data.db.entity.WalletEntity
import com.pitakaview.app.data.repo.LedgerRepository
import com.pitakaview.app.domain.CategoryKind
import com.pitakaview.app.domain.PayFrequency
import com.pitakaview.app.domain.SearchQuery
import com.pitakaview.app.domain.TxPurpose
import com.pitakaview.app.domain.WalletType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** The M3 queries against real SQL: the month lists, search, and when pay was received. */
class EntryQueriesTest {
    private lateinit var db: PitakaDatabase
    private lateinit var ledger: LedgerRepository
    private val now = Instant.parse("2026-11-26T03:00:00Z")
    private fun nov(day: Int) = LocalDate.of(2026, 11, day)

    private var cash = 0L
    private var gcash = 0L
    private var food = 0L
    private var fare = 0L
    private var salary = 0L

    @Before
    fun open() = runBlocking {
        db = Room.inMemoryDatabaseBuilder { PitakaDatabase_Impl() }
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        ledger = LedgerRepository(db)
        cash = db.walletDao().insert(WalletEntity(name = "Cash", type = WalletType.CASH, createdAt = now))
        gcash = db.walletDao().insert(WalletEntity(name = "GCash", type = WalletType.E_WALLET, createdAt = now))
        food = db.categoryDao().insert(CategoryEntity(name = "Food", kind = CategoryKind.EXPENSE, iconKey = "food"))
        fare = db.categoryDao().insert(CategoryEntity(name = "Pamasahe", kind = CategoryKind.EXPENSE, iconKey = "fare"))
        salary = db.categoryDao().insert(CategoryEntity(name = "Salary", kind = CategoryKind.INCOME, iconKey = "salary"))
    }

    @After
    fun close() = db.close()

    private suspend fun expense(amount: Long, on: LocalDate, category: Long = food, wallet: Long = cash, note: String? = null) =
        ledger.add(TransactionEntity(purpose = TxPurpose.EXPENSE, amountCentavos = amount, occurredOn = on, fromWalletId = wallet, categoryId = category, note = note, createdAt = now))

    private suspend fun income(amount: Long, on: LocalDate, schedule: Long? = null, note: String? = null) =
        ledger.add(TransactionEntity(purpose = TxPurpose.INCOME, amountCentavos = amount, occurredOn = on, toWalletId = gcash, categoryId = salary, payScheduleId = schedule, note = note, createdAt = now))

    private suspend fun search(text: String, limit: Int = 50) =
        db.transactionDao().search(requireNotNull(SearchQuery.pattern(text)), SearchQuery.amountCentavos(text), limit).first()

    @Test
    fun aMonthListHoldsOnlyThatPurposeAndMonthNewestFirstWithNames() = runBlocking {
        expense(10_000, LocalDate.of(2026, 10, 31))
        val first = expense(12_500, nov(1), note = "lugaw")
        val second = expense(8_000, nov(20), category = fare, wallet = gcash)
        val sameDayLater = expense(5_000, nov(20))
        income(1_500_000, nov(15))
        expense(9_900, LocalDate.of(2026, 12, 1))

        val rows = db.transactionDao().observeEntries(TxPurpose.EXPENSE, nov(1), nov(30)).first()
        assertEquals(listOf(sameDayLater, second, first), rows.map { it.id })
        assertEquals(listOf("Food", "Pamasahe", "Food"), rows.map { it.categoryName })
        assertEquals(listOf("Cash", "GCash", "Cash"), rows.map { it.fromWalletName })
        assertEquals("lugaw", rows.last().note)

        val income = db.transactionDao().observeEntries(TxPurpose.INCOME, nov(1), nov(30)).first().single()
        assertEquals("Salary" to "GCash", income.categoryName to income.toWalletName)
    }

    @Test
    fun searchFindsByNoteCategoryWalletAndAmount() = runBlocking {
        val lunch = expense(25_000, nov(20), note = "Lunch with Tita")
        val jeep = expense(1_300, nov(21), category = fare, wallet = gcash)
        val pay = income(1_500_000, nov(15), note = "kinsenas")

        assertEquals("a word in the note, in any letter case", listOf(lunch), search("lunch").map { it.id })
        assertEquals("a category name", listOf(jeep), search("pamasahe").map { it.id })
        assertEquals("a wallet name, on either side; newest first", listOf(jeep, pay), search("gcash").map { it.id })
        assertEquals("an amount as typed", listOf(lunch), search("250").map { it.id })
        assertEquals("an amount with a peso sign and comma", listOf(pay), search("₱15,000").map { it.id })
        assertTrue("nothing matches", search("zzz").isEmpty())
    }

    @Test
    fun searchTakesPercentAndUnderscoreLiterally() = runBlocking {
        val discount = expense(10_000, nov(20), note = "50% off sale")
        val snake = expense(10_100, nov(21), note = "ref a_b")
        expense(10_200, nov(22), note = "plain axb note")

        assertEquals("% is not 'anything'", listOf(discount), search("%").map { it.id })
        assertEquals("_ is not 'any one letter'", listOf(snake), search("a_b").map { it.id })
    }

    @Test
    fun searchStopsAtItsLimit() = runBlocking {
        repeat(8) { expense(1_000L + it, nov(1 + it), note = "kape") }
        assertEquals(5, search("kape", limit = 5).size)
        assertEquals("the newest ones are kept", nov(8), search("kape", limit = 5).first().occurredOn)
    }

    @Test
    fun payReceiptsAreTheIncomeRowsLinkedToASchedule() = runBlocking {
        val schedule = db.payScheduleDao().insert(
            PayScheduleEntity(name = "Sahod", expectedAmountCentavos = 1_500_000, walletId = gcash, categoryId = salary, frequency = PayFrequency.SEMI_MONTHLY, createdAt = now),
        )
        income(1_500_000, LocalDate.of(2026, 9, 30), schedule) // older than the look-back
        income(1_500_000, nov(15), schedule)
        income(200_000, nov(16)) // income, but not for a schedule
        expense(5_000, nov(15))

        assertEquals(listOf(PayReceipt(schedule, nov(15))), db.transactionDao().observePayReceipts(since = nov(1)).first())
    }

    @Test
    fun removingAPaydayKeepsTheIncomeAlreadyLogged() = runBlocking {
        val scheduleId = db.payScheduleDao().insert(
            PayScheduleEntity(name = "Sahod", expectedAmountCentavos = 1_500_000, walletId = gcash, categoryId = salary, frequency = PayFrequency.SEMI_MONTHLY, createdAt = now),
        )
        val pay = income(1_500_000, nov(15), scheduleId)

        db.payScheduleDao().delete(requireNotNull(db.payScheduleDao().get(scheduleId)))

        assertNull(db.payScheduleDao().get(scheduleId))
        val row = requireNotNull(db.transactionDao().get(pay))
        assertEquals("the money is still counted", 1_500_000L, row.amountCentavos)
        assertNull("only the link is gone", row.payScheduleId)
        assertTrue(db.transactionDao().observePayReceipts(since = nov(1)).first().isEmpty())
    }

    @Test
    fun searchStaysQuickWithTenThousandEntries() = runBlocking {
        val rows = (0 until 10_000).map { i ->
            TransactionEntity(
                purpose = TxPurpose.EXPENSE, amountCentavos = 1_000L + i, occurredOn = LocalDate.of(2024, 1, 1).plusDays(i % 1000L),
                fromWalletId = if (i % 2 == 0) cash else gcash, categoryId = if (i % 3 == 0) fare else food,
                note = if (i % 500 == 0) "birthday gift $i" else "note $i", createdAt = now,
            )
        }
        rows.chunked(1_000).forEach { ledger.addAll(it) }

        search("warm-up")
        val started = System.nanoTime()
        val found = search("birthday")
        val textMillis = (System.nanoTime() - started) / 1_000_000
        val startedMonth = System.nanoTime()
        val month = db.transactionDao().observeEntries(TxPurpose.EXPENSE, LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 31)).first()
        val monthMillis = (System.nanoTime() - startedMonth) / 1_000_000
        println("M3_TIMING search over 10000 rows: $textMillis ms for ${found.size} hits; one month list: $monthMillis ms for ${month.size} rows")

        assertEquals(20, found.size)
        assertTrue("search took $textMillis ms", textMillis < 2_000)
        assertTrue("the month list took $monthMillis ms", monthMillis < 2_000)
    }
}
