package com.pitakaview.app.data

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.pitakaview.app.data.db.FitakaDatabase
import com.pitakaview.app.data.db.FitakaDatabase_Impl
import com.pitakaview.app.data.db.entity.SavingsGoalEntity
import com.pitakaview.app.data.db.entity.TransactionEntity
import com.pitakaview.app.data.db.entity.WalletEntity
import com.pitakaview.app.data.repo.GoalBelowZeroException
import com.pitakaview.app.data.repo.LedgerRepository
import com.pitakaview.app.domain.TxPurpose.SAVINGS_DEPOSIT
import com.pitakaview.app.domain.TxPurpose.SAVINGS_WITHDRAWAL
import com.pitakaview.app.domain.WalletType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * A savings goal never has less than nothing saved in it. Found in the review before 1.0 (2026-11-27): a
 * deposit could be lowered or deleted after its money was taken out, and the goal then showed less than
 * nothing saved while the wallet it was kept in was left short. The ledger, the only write path, refuses
 * such a change. Real Room and SQL, in memory.
 */
class GoalNeverBelowZeroTest {
    private lateinit var db: FitakaDatabase
    private lateinit var ledger: LedgerRepository

    private val now = Instant.parse("2026-11-25T08:00:00Z")
    private val nov3 = LocalDate.of(2026, 11, 3)

    private var cash = 0L
    private var mari = 0L
    private var goal = 0L
    private var deposit = 0L
    private var withdrawal = 0L

    /** 10,000.00 in Cash. 5,000.00 saved into a goal kept in MariBank, and 3,000.00 of it taken back out. */
    @Before
    fun open() = runTest {
        db = Room.inMemoryDatabaseBuilder { FitakaDatabase_Impl() }
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        ledger = LedgerRepository(db)
        cash = db.walletDao().insert(WalletEntity(name = "Cash", type = WalletType.CASH, openingBalanceCentavos = 1_000_000, createdAt = now))
        mari = db.walletDao().insert(WalletEntity(name = "MariBank", type = WalletType.BANK, createdAt = now))
        goal = db.savingsGoalDao().insert(SavingsGoalEntity(name = "Laptop", walletId = mari, createdAt = now))
        deposit = ledger.depositToGoal(goal, cash, 500_000, nov3, now)
        withdrawal = ledger.withdrawFromGoal(goal, cash, 300_000, nov3, now)
    }

    @After
    fun close() = db.close()

    private suspend fun saved() = db.savingsGoalDao().savedIn(goal)

    private suspend fun balances(): Pair<Long, Long> {
        val rows = db.walletDao().observeAllWithBalance().first()
        return rows.first { it.wallet.id == cash }.balanceCentavos to rows.first { it.wallet.id == mari }.balanceCentavos
    }

    private suspend fun row(id: Long) = db.transactionDao().get(id)!!

    private suspend fun refused(change: suspend () -> Unit) {
        val before = saved() to balances()
        try {
            change()
            fail("the change should have been refused")
        } catch (expected: GoalBelowZeroException) {
            assertEquals(goal, expected.goalId)
        }
        assertEquals("nothing changed", before, saved() to balances())
    }

    @Test
    fun aDepositWhoseMoneyWasTakenOutCannotBeDeleted() = runTest {
        assertEquals(200_000L, saved())
        assertEquals(800_000L to 200_000L, balances())
        refused { ledger.delete(row(deposit)) }
    }

    @Test
    fun aDepositCannotBeLoweredBelowWhatWasTakenOut() = runTest {
        refused { ledger.update(row(deposit).copy(amountCentavos = 299_999)) }
        ledger.update(row(deposit).copy(amountCentavos = 300_000))
        assertEquals(0L, saved())
        // Cash: 10,000.00 less the 3,000.00 put in, plus the 3,000.00 taken back out. MariBank holds what the goal holds.
        assertEquals(1_000_000L to 0L, balances())
    }

    @Test
    fun noMoreCanBeTakenOutThanIsSaved() = runTest {
        refused { ledger.withdrawFromGoal(goal, cash, 200_001, nov3, now) }
        refused { ledger.update(row(withdrawal).copy(amountCentavos = 500_001)) }
        ledger.withdrawFromGoal(goal, cash, 200_000, nov3, now)
        assertEquals(0L, saved())
    }

    @Test
    fun changesThatLeaveEnoughSavedGoThrough() = runTest {
        ledger.update(row(deposit).copy(amountCentavos = 600_000))
        ledger.delete(row(withdrawal))
        assertEquals(600_000L, saved())
        ledger.delete(row(deposit))
        assertEquals("with nothing taken out, a deposit can go", 0L, saved())
        assertEquals(1_000_000L to 0L, balances())
    }

    /** A goal left below zero by a build before this rule can still be put right, one step at a time. */
    @Test
    fun aGoalAlreadyBelowZeroCanBePutRightButNotMadeWorse() = runTest {
        // As an older build could leave it: another 3,000.00 taken out, past what was saved.
        db.transactionDao().insert(
            TransactionEntity(purpose = SAVINGS_WITHDRAWAL, amountCentavos = 300_000, occurredOn = nov3, fromWalletId = mari, toWalletId = cash, goalId = goal, createdAt = now),
        )
        assertEquals(-100_000L, saved())

        val topUp = ledger.depositToGoal(goal, cash, 50_000, nov3, now)
        assertEquals("a deposit puts it right a little", -50_000L, saved())
        ledger.update(row(withdrawal).copy(amountCentavos = 290_000))
        assertEquals("so does lowering what was taken out", -40_000L, saved())

        refused { ledger.update(row(withdrawal).copy(amountCentavos = 300_000)) }
        refused { ledger.delete(row(topUp)) }
        refused { ledger.withdrawFromGoal(goal, cash, 1, nov3, now) }
        assertEquals(SAVINGS_DEPOSIT, row(topUp).purpose)
    }
}
