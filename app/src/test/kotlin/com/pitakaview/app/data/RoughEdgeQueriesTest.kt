package com.pitakaview.app.data

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.pitakaview.app.data.db.FitakaDatabase
import com.pitakaview.app.data.db.FitakaDatabase_Impl
import com.pitakaview.app.data.db.entity.CategoryEntity
import com.pitakaview.app.data.db.entity.TransactionEntity
import com.pitakaview.app.data.db.entity.WalletEntity
import com.pitakaview.app.data.repo.LedgerRepository
import com.pitakaview.app.domain.CategoryKind
import com.pitakaview.app.domain.SearchQuery
import com.pitakaview.app.domain.TxPurpose
import com.pitakaview.app.domain.WalletType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** The queries behind two rough-edge fixes, against real SQL: a row's category icon, and search with ñ. */
class RoughEdgeQueriesTest {
    private lateinit var db: FitakaDatabase
    private lateinit var ledger: LedgerRepository
    private val now = Instant.parse("2026-11-26T03:00:00Z")
    private fun nov(day: Int) = LocalDate.of(2026, 11, day)

    private var cash = 0L
    private var gcash = 0L
    private var food = 0L

    @Before
    fun open() = runBlocking {
        db = Room.inMemoryDatabaseBuilder { FitakaDatabase_Impl() }
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        ledger = LedgerRepository(db)
        cash = db.walletDao().insert(WalletEntity(name = "Cash", type = WalletType.CASH, createdAt = now))
        gcash = db.walletDao().insert(WalletEntity(name = "GCash", type = WalletType.E_WALLET, createdAt = now))
        food = db.categoryDao().insert(CategoryEntity(name = "Food", kind = CategoryKind.EXPENSE, iconKey = "food"))
    }

    @After
    fun close() = db.close()

    private suspend fun expense(amount: Long, on: LocalDate, note: String? = null) =
        ledger.add(TransactionEntity(purpose = TxPurpose.EXPENSE, amountCentavos = amount, occurredOn = on, fromWalletId = cash, categoryId = food, note = note, createdAt = now))

    @Test
    fun everyListOfEntriesCarriesTheCategorysIconAndNoneForAnEntryWithoutACategory() = runBlocking {
        val lunch = expense(12_500, nov(20), note = "lugaw")
        val move = ledger.add(TransactionEntity(purpose = TxPurpose.TRANSFER, amountCentavos = 50_000, occurredOn = nov(21), fromWalletId = cash, toWalletId = gcash, createdAt = now))

        val recent = db.transactionDao().observeRecent(10).first().associate { it.id to it.categoryIconKey }
        assertEquals(mapOf(lunch to "food", move to null), recent)
        assertEquals("the month list", "food", db.transactionDao().observeEntries(TxPurpose.EXPENSE, nov(1), nov(30)).first().single().categoryIconKey)
        assertEquals("search", "food", db.transactionDao().search("%lugaw%", null, 10).first().single().categoryIconKey)

        // A change of icon reaches the rows that are already there.
        db.categoryDao().update(db.categoryDao().get(food)!!.copy(iconKey = "coffee"))
        assertEquals("coffee", db.transactionDao().observeRecent(10).first().first { it.id == lunch }.categoryIconKey)
    }

    @Test
    fun forALetterLikeEnyeTheDatabaseGivesEveryNearMatchAndTheAppKeepsTheRealOnes() = runBlocking {
        val pina = expense(18_000, nov(20), note = "Piña colada")
        val capitals = expense(7_000, nov(21), note = "LAS PIÑAS toll")
        val near = expense(9_000, nov(22), note = "pixa")
        expense(5_000, nov(23), note = "pinya")

        val text = "PIÑA"
        val found = db.transactionDao().search(requireNotNull(SearchQuery.pattern(text)), SearchQuery.amountCentavos(text), Int.MAX_VALUE).first()
        // The database treats Ñ and ñ as two letters, so it is asked for any one character there...
        assertEquals("newest first, with the near match still in", listOf(near, capitals, pina), found.map { it.id })
        // ...and the app keeps the rows that really have the letter, whichever way it was typed.
        assertEquals(listOf(capitals, pina), found.filter { SearchQuery.matches(text, it.note, it.categoryName, it.fromWalletName, it.toWalletName) }.map { it.id })
    }
}
