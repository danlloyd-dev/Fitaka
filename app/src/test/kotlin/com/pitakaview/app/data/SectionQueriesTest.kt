package com.pitakaview.app.data

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.pitakaview.app.data.db.FitakaDatabase
import com.pitakaview.app.data.db.FitakaDatabase_Impl
import com.pitakaview.app.data.db.entity.BaonEntity
import com.pitakaview.app.data.db.entity.CategoryEntity
import com.pitakaview.app.data.db.entity.LoanEntity
import com.pitakaview.app.data.db.entity.SavingsGoalEntity
import com.pitakaview.app.data.db.entity.TransactionEntity
import com.pitakaview.app.data.db.entity.WalletEntity
import com.pitakaview.app.data.repo.LedgerRepository
import com.pitakaview.app.domain.BaonDirection
import com.pitakaview.app.domain.BaonPeriod
import com.pitakaview.app.domain.CategoryKind
import com.pitakaview.app.domain.LoanDirection
import com.pitakaview.app.domain.LoanForm
import com.pitakaview.app.domain.LoanFormLogic
import com.pitakaview.app.domain.MoveForm
import com.pitakaview.app.domain.MoveFormLogic
import com.pitakaview.app.domain.MoveTarget
import com.pitakaview.app.domain.TxPurpose
import com.pitakaview.app.domain.WalletType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** The M4 queries against real SQL: loans with what is paid, goals with what is saved, and each one's entries. */
class SectionQueriesTest {
    private lateinit var db: FitakaDatabase
    private lateinit var ledger: LedgerRepository
    private val now = Instant.parse("2026-11-26T04:00:00Z")
    private val day = LocalDate.of(2026, 11, 26)
    private var cash = 0L
    private var bank = 0L
    private var food = 0L

    @Before
    fun open() = runBlocking {
        db = Room.inMemoryDatabaseBuilder { FitakaDatabase_Impl() }
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        ledger = LedgerRepository(db)
        cash = db.walletDao().insert(WalletEntity(name = "Cash", type = WalletType.CASH, openingBalanceCentavos = 500_000, createdAt = now))
        bank = db.walletDao().insert(WalletEntity(name = "MariBank", type = WalletType.BANK, createdAt = now))
        food = db.categoryDao().insert(CategoryEntity(name = "Food", kind = CategoryKind.EXPENSE, iconKey = "food"))
    }

    @After
    fun close() = db.close()

    private suspend fun balances() = db.walletDao().observeAllWithBalance().first().associate { it.wallet.name to it.balanceCentavos }

    private suspend fun move(target: MoveTarget, pesos: String, wallet: Long = cash, on: LocalDate = day) =
        ledger.add(MoveFormLogic.toTransaction(MoveForm(pesos, wallet, on), target, null, now))

    @Test
    fun aBorrowedLoanAndItsPaymentsMoveTheWalletAndCountAsPaid() = runBlocking {
        val form = LoanForm(who = "Tita Baby", amountText = "10000", totalText = "11000", startDate = day, walletId = cash)
        val loanId = ledger.addLoan(LoanFormLogic.toEntity(form, null, now)) { LoanFormLogic.startRow(form, it, now) }
        assertEquals("the borrowed money is in the wallet", 1_500_000L, balances().getValue("Cash"))

        val target = MoveTarget(TxPurpose.LOAN_PAYMENT, loanId = loanId, loanDirection = LoanDirection.I_OWE)
        move(target, "2000")
        move(target, "1500", on = day.plusDays(1))

        val row = db.loanDao().observeAllWithPaid().first().single()
        assertEquals("payments count; the money borrowed at the start does not", 350_000L, row.paidCentavos)
        assertEquals(1_100_000L, row.loan.totalPayableCentavos)
        assertEquals(row, db.loanDao().observeWithPaid(loanId).first())
        assertEquals(1_150_000L, balances().getValue("Cash"))

        val history = db.transactionDao().observeForLoan(loanId).first()
        assertEquals("newest first", listOf(TxPurpose.LOAN_PAYMENT, TxPurpose.LOAN_PAYMENT, TxPurpose.LOAN_START), history.map { it.purpose })
        assertEquals(listOf(150_000L, 200_000L, 1_000_000L), history.map { it.amountCentavos })
        assertEquals(3, db.transactionDao().countForLoan(loanId))
    }

    @Test
    fun aLoanFromBeforeTheAppMovesNoMoneyAndSettledLoansAreStillListed() = runBlocking {
        val old = LoanForm(direction = LoanDirection.OWED_TO_ME, who = "Kuya Jun", amountText = "3000", startDate = day.minusMonths(6))
        val oldId = ledger.addLoan(LoanFormLogic.toEntity(old, null, now)) { LoanFormLogic.startRow(old, it, now) }
        assertEquals(500_000L, balances().getValue("Cash"))
        assertEquals(0, db.transactionDao().countForLoan(oldId))

        db.loanDao().update(requireNotNull(db.loanDao().get(oldId)).copy(closedAt = now))
        assertTrue("the open-loans list (Home) leaves it out", db.loanDao().observeOpen().first().isEmpty())
        assertEquals("the Utang tab still has it", listOf(oldId), db.loanDao().observeAllWithPaid().first().map { it.loan.id })
    }

    @Test
    fun aLoanWhoseFirstEntryIsRefusedIsNotSavedAtAll() = runBlocking {
        val form = LoanForm(who = "Bank", amountText = "5000", startDate = day, walletId = cash)
        val failure = runCatching {
            ledger.addLoan(LoanFormLogic.toEntity(form, null, now)) { id ->
                // A broken first entry: no wallet on either side.
                TransactionEntity(purpose = TxPurpose.LOAN_START, amountCentavos = 500_000, occurredOn = day, loanId = id, createdAt = now)
            }
        }.exceptionOrNull()
        assertTrue("refused: $failure", failure is IllegalArgumentException)
        assertTrue("no half-saved loan is left behind", db.loanDao().observeAllWithPaid().first().isEmpty())
    }

    @Test
    fun savingsAreDepositsMinusWithdrawalsAndHiddenGoalsStayOutOfHome() = runBlocking {
        val laptop = db.savingsGoalDao().insert(SavingsGoalEntity(name = "Laptop", targetCentavos = 4_500_000, walletId = bank, createdAt = now))
        val trip = db.savingsGoalDao().insert(SavingsGoalEntity(name = "Trip", createdAt = now))
        move(MoveTarget(TxPurpose.SAVINGS_DEPOSIT, goalId = laptop, goalWalletId = bank), "3000")
        move(MoveTarget(TxPurpose.SAVINGS_WITHDRAWAL, goalId = laptop, goalWalletId = bank), "500")
        move(MoveTarget(TxPurpose.SAVINGS_DEPOSIT, goalId = trip), "1000")

        assertEquals("money moved into the goal's wallet and back", mapOf("Cash" to 250_000L, "MariBank" to 250_000L), balances())
        val all = db.savingsGoalDao().observeAllProgress().first()
        assertEquals(listOf("Laptop" to 250_000L, "Trip" to 100_000L), all.map { it.name to it.savedCentavos })
        assertEquals("set aside only: the wallet still holds it", 250_000L, balances().getValue("Cash"))
        assertEquals(250_000L, db.savingsGoalDao().observeProgressOf(laptop).first()?.savedCentavos)
        assertEquals(listOf(TxPurpose.SAVINGS_WITHDRAWAL, TxPurpose.SAVINGS_DEPOSIT), db.transactionDao().observeForGoal(laptop).first().map { it.purpose })

        db.savingsGoalDao().update(requireNotNull(db.savingsGoalDao().get(trip)).copy(isArchived = true))
        assertEquals(listOf("Laptop"), db.savingsGoalDao().observeProgress().first().map { it.name })
        val withHidden = db.savingsGoalDao().observeAllProgress().first()
        assertEquals("hidden goals are listed last and flagged", listOf("Laptop" to false, "Trip" to true), withHidden.map { it.name to it.isArchived })
    }

    @Test
    fun aBaonListsWhatWasReceivedGivenAndSpentForIt() = runBlocking {
        val mine = db.baonDao().insert(BaonEntity(name = "My baon", direction = BaonDirection.RECEIVE, amountCentavos = 50_000, period = BaonPeriod.WEEKLY, startDate = day, createdAt = now))
        val bunso = db.baonDao().insert(BaonEntity(name = "Bunso", direction = BaonDirection.GIVE, amountCentavos = 20_000, period = BaonPeriod.DAILY, startDate = day, isActive = false, createdAt = now))
        move(MoveTarget(TxPurpose.BAON_RECEIVED, baonId = mine), "500")
        ledger.add(TransactionEntity(purpose = TxPurpose.EXPENSE, amountCentavos = 12_000, occurredOn = day, fromWalletId = cash, categoryId = food, baonId = mine, createdAt = now))
        move(MoveTarget(TxPurpose.BAON_GIVEN, baonId = bunso), "200")

        assertEquals(listOf(TxPurpose.EXPENSE, TxPurpose.BAON_RECEIVED), db.transactionDao().observeForBaon(mine).first().map { it.purpose })
        assertEquals("Food", db.transactionDao().observeForBaon(mine).first().first().categoryName)
        assertEquals(2 to 1, db.transactionDao().countForBaon(mine) to db.transactionDao().countForBaon(bunso))
        assertEquals("the stopped one is listed last", listOf("My baon", "Bunso"), db.baonDao().observeAll().first().map { it.name })
        assertEquals(listOf("My baon"), db.baonDao().observeActive().first().map { it.name })
        assertEquals("Bunso", db.baonDao().observe(bunso).first()?.name)
    }

    @Test
    fun nothingWithEntriesCanBeDeletedButAnEmptyOneCan() = runBlocking {
        val goal = db.savingsGoalDao().insert(SavingsGoalEntity(name = "Trip", createdAt = now))
        val deposit = move(MoveTarget(TxPurpose.SAVINGS_DEPOSIT, goalId = goal), "1000")

        val refused = runCatching { db.savingsGoalDao().delete(requireNotNull(db.savingsGoalDao().get(goal))) }.exceptionOrNull()
        assertTrue("the database refuses: $refused", refused != null)
        assertFalse(db.savingsGoalDao().observeAllProgress().first().isEmpty())

        ledger.delete(requireNotNull(db.transactionDao().get(deposit)))
        db.savingsGoalDao().delete(requireNotNull(db.savingsGoalDao().get(goal)))
        assertNull(db.savingsGoalDao().get(goal))
    }
}
