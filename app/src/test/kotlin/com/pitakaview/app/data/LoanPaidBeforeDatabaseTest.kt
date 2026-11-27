package com.pitakaview.app.data

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.pitakaview.app.data.db.FitakaDatabase
import com.pitakaview.app.data.db.FitakaDatabase_Impl
import com.pitakaview.app.data.db.entity.LoanEntity
import com.pitakaview.app.data.db.entity.TransactionEntity
import com.pitakaview.app.data.db.entity.WalletEntity
import com.pitakaview.app.data.repo.LedgerRepository
import com.pitakaview.app.domain.LoanDirection
import com.pitakaview.app.domain.TxPurpose
import com.pitakaview.app.domain.WalletType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * What was paid on a loan before the app, in the real queries (Room and SQL in memory): it counts as paid
 * on the Utang tab, on the loan's page and for reminders, and it leaves every wallet as it is.
 */
class LoanPaidBeforeDatabaseTest {
    private lateinit var db: FitakaDatabase
    private lateinit var ledger: LedgerRepository
    private val now = Instant.parse("2026-11-27T08:00:00Z")

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder { FitakaDatabase_Impl() }
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        ledger = LedgerRepository(db)
    }

    @After
    fun close() = db.close()

    @Test
    fun paidCountsWhatWasPaidBeforeTheAppAndNoWalletMovesForIt() = runTest {
        val cash = db.walletDao().insert(WalletEntity(name = "Cash", type = WalletType.CASH, openingBalanceCentavos = 500_000, createdAt = now))
        // Twelve months of 1,000, four of them paid before the app; no wallet for the loan itself.
        val loanId = ledger.addLoan(
            LoanEntity(
                direction = LoanDirection.I_OWE, counterparty = "Tita Baby", principalCentavos = 1_200_000, totalPayableCentavos = 1_200_000,
                startDate = LocalDate.of(2026, 6, 1), dueDate = LocalDate.of(2026, 7, 15), installmentCentavos = 100_000, dueDayOfMonth = 15,
                createdAt = now, paidBeforeCentavos = 400_000,
            ),
        ) { null }
        assertEquals("nothing moved in Cash for the loan or what was paid before", 500_000L, balance())

        // The November payment, logged in Fitaka, from Cash.
        ledger.add(
            TransactionEntity(
                purpose = TxPurpose.LOAN_PAYMENT, amountCentavos = 100_000, occurredOn = LocalDate.of(2026, 11, 15),
                fromWalletId = cash, loanId = loanId, createdAt = now,
            ),
        )
        assertEquals(500_000L, db.loanDao().observeWithPaid(loanId).first()!!.paidCentavos)
        assertEquals(500_000L, db.loanDao().observeOpen().first().single().paidCentavos)
        assertEquals(500_000L, db.loanDao().observeAllWithPaid().first().single().paidCentavos)
        assertEquals("only the payment logged in Fitaka left Cash", 400_000L, balance())
        assertEquals("and only it is an entry", 1, db.transactionDao().countForLoan(loanId))
    }

    private suspend fun balance() = db.walletDao().observeActiveWithBalance().first().single().balanceCentavos
}
