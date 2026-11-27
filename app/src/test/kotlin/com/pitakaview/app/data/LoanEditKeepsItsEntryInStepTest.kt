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
import com.pitakaview.app.domain.TxPurpose.LOAN_START
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
 * A loan's amount and day, and the entry for the money that changed hands, are one event. Found in the
 * review before 1.0 (2026-11-27): editing the loan changed only the loan, so the wallet kept the first amount.
 * Real Room and SQL, in memory.
 */
class LoanEditKeepsItsEntryInStepTest {
    private lateinit var db: FitakaDatabase
    private lateinit var ledger: LedgerRepository

    private val now = Instant.parse("2026-11-25T08:00:00Z")
    private val nov1 = LocalDate.of(2026, 11, 1)
    private val nov3 = LocalDate.of(2026, 11, 3)

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

    private suspend fun cash() = db.walletDao().insert(WalletEntity(name = "Cash", type = WalletType.CASH, createdAt = now))

    private suspend fun balance(walletId: Long) = db.walletDao().observeAllWithBalance().first().first { it.wallet.id == walletId }.balanceCentavos

    /** Borrowed 10,000.00 on the 1st, into [walletId] (or with no wallet: a loan from before the app). */
    private suspend fun borrow(walletId: Long?): LoanEntity {
        val loan = LoanEntity(
            direction = LoanDirection.I_OWE, counterparty = "Tita Baby", principalCentavos = 1_000_000, totalPayableCentavos = 1_000_000,
            startDate = nov1, createdAt = now,
        )
        val id = ledger.addLoan(loan) { loanId ->
            walletId?.let { TransactionEntity(purpose = LOAN_START, amountCentavos = 1_000_000, occurredOn = nov1, toWalletId = it, loanId = loanId, createdAt = now) }
        }
        return loan.copy(id = id)
    }

    private suspend fun start(loan: LoanEntity) = db.transactionDao().firstForLoan(loan.id, LOAN_START)

    @Test
    fun correctingTheAmountAndTheDayCorrectsTheEntryAndTheWallet() = runTest {
        val wallet = cash()
        val loan = borrow(wallet)
        assertEquals(1_000_000L, balance(wallet))

        ledger.updateLoan(loan.copy(principalCentavos = 1_200_000, totalPayableCentavos = 1_200_000, startDate = nov3))

        val entry = start(loan)!!
        assertEquals(1_200_000L, entry.amountCentavos)
        assertEquals(nov3, entry.occurredOn)
        assertEquals("the wallet has what was really borrowed", 1_200_000L, balance(wallet))
        assertEquals(1_200_000L, db.loanDao().get(loan.id)!!.principalCentavos)
    }

    @Test
    fun anEditOfAnythingElseLeavesTheEntryAsItIs() = runTest {
        val wallet = cash()
        val loan = borrow(wallet)
        val before = start(loan)

        ledger.updateLoan(loan.copy(counterparty = "Tita Baby (Cubao)", note = "pay on the 15th", dueDate = LocalDate.of(2026, 12, 15)))

        assertEquals(before, start(loan))
        assertEquals("Tita Baby (Cubao)", db.loanDao().get(loan.id)!!.counterparty)
    }

    /** Less arrived than was borrowed (a fee kept back), put right by the owner in the entry's own form. */
    @Test
    fun anEntryTheOwnerSetApartKeepsItsAmountAndItsDay() = runTest {
        val wallet = cash()
        val loan = borrow(wallet)
        ledger.update(start(loan)!!.copy(amountCentavos = 950_000, occurredOn = nov3))

        ledger.updateLoan(loan.copy(principalCentavos = 1_100_000, totalPayableCentavos = 1_100_000, startDate = LocalDate.of(2026, 11, 2)))

        val entry = start(loan)!!
        assertEquals("not in step before, so not moved", 950_000L, entry.amountCentavos)
        assertEquals(nov3, entry.occurredOn)
        assertEquals(950_000L, balance(wallet))
    }

    /** Only the part that was in step moves: here the amount, not the day. */
    @Test
    fun theAmountFollowsEvenWhenTheDayWasSetApart() = runTest {
        val wallet = cash()
        val loan = borrow(wallet)
        ledger.update(start(loan)!!.copy(occurredOn = nov3))

        ledger.updateLoan(loan.copy(principalCentavos = 1_100_000, totalPayableCentavos = 1_100_000, startDate = LocalDate.of(2026, 11, 2)))

        val entry = start(loan)!!
        assertEquals(1_100_000L, entry.amountCentavos)
        assertEquals(nov3, entry.occurredOn)
    }

    @Test
    fun aLoanFromBeforeTheAppGetsNoEntry() = runTest {
        val wallet = cash()
        val loan = borrow(walletId = null)

        ledger.updateLoan(loan.copy(principalCentavos = 1_200_000, totalPayableCentavos = 1_200_000))

        assertEquals(null, start(loan))
        assertEquals(0L, balance(wallet))
        assertEquals(1_200_000L, db.loanDao().get(loan.id)!!.principalCentavos)
    }
}
