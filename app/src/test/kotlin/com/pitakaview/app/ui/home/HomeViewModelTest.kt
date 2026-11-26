package com.pitakaview.app.ui.home

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.pitakaview.app.data.db.PitakaDatabase
import com.pitakaview.app.data.db.PitakaDatabase_Impl
import com.pitakaview.app.data.db.entity.CategoryEntity
import com.pitakaview.app.data.db.entity.TransactionEntity
import com.pitakaview.app.data.db.entity.WalletEntity
import com.pitakaview.app.data.repo.LedgerRepository
import com.pitakaview.app.domain.CategoryKind
import com.pitakaview.app.domain.TxPurpose
import com.pitakaview.app.domain.WalletType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** Regression: Home fixed "today" when it was created, so after midnight it kept showing the old month. */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private lateinit var db: PitakaDatabase
    private val now = Instant.parse("2026-11-30T08:00:00Z")

    @Before
    fun open() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        db = Room.inMemoryDatabaseBuilder { PitakaDatabase_Impl() }
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    }

    @After
    fun close() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun thisMonthFollowsTheClockPastMidnightIntoANewMonth() = runBlocking {
        val cash = db.walletDao().insert(WalletEntity(name = "Cash", type = WalletType.CASH, createdAt = now))
        val salary = db.categoryDao().insert(CategoryEntity(name = "Salary", kind = CategoryKind.INCOME, iconKey = "x"))
        val ledger = LedgerRepository(db)
        fun income(amount: Long, on: LocalDate) = TransactionEntity(
            purpose = TxPurpose.INCOME, amountCentavos = amount, occurredOn = on, toWalletId = cash,
            categoryId = salary, createdAt = now,
        )
        ledger.add(income(2_000_000, LocalDate.of(2026, 11, 30)))
        ledger.add(income(500_000, LocalDate.of(2026, 12, 1)))

        val today = MutableStateFlow(LocalDate.of(2026, 11, 30))
        val vm = HomeViewModel(db, today, lastBackupAt = flowOf(null))

        val november = withTimeout(10_000) { vm.state.first { it.incomeCentavos == 2_000_000L } }
        // Wallet balances are not tied to a month: both entries count.
        assertEquals(2_500_000L, november.totalCentavos)

        today.value = LocalDate.of(2026, 12, 1) // midnight passes while the app stays open

        val december = withTimeout(10_000) { vm.state.first { it.incomeCentavos == 500_000L } }
        assertEquals(2_500_000L, december.totalCentavos)
    }
}
