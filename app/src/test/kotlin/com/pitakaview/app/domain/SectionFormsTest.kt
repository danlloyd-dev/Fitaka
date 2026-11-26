package com.pitakaview.app.domain

import com.pitakaview.app.data.db.entity.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** The forms behind Utang, Ipon and Baon, and the entries that belong to them. */
class SectionFormsTest {
    private val now = Instant.parse("2026-11-26T04:00:00Z")
    private val nov26 = LocalDate.of(2026, 11, 26)
    private val dec5 = LocalDate.of(2026, 12, 5)

    // ---------- loans ----------

    private val loan = LoanForm(who = " Tita Baby ", amountText = "10000", startDate = nov26)

    @Test
    fun aSimpleLoanNeedsOnlyWhoAndHowMuch() {
        assertTrue(LoanFormLogic.problems(loan).isEmpty())
        val entity = LoanFormLogic.toEntity(loan, existing = null, now = now)
        assertEquals("Tita Baby", entity.counterparty)
        assertEquals(LoanDirection.I_OWE, entity.direction)
        assertEquals("no interest typed: pay back what was borrowed", 1_000_000L to 1_000_000L, entity.principalCentavos to entity.totalPayableCentavos)
        assertNull(entity.dueDate)
        assertNull(entity.installmentCentavos)
        assertNull(entity.closedAt)
    }

    @Test
    fun eachMissingOrWrongPartOfALoanIsNamed() {
        fun problems(form: LoanForm) = LoanFormLogic.problems(form)
        assertEquals(setOf(LoanProblem.WHO), problems(loan.copy(who = " ")))
        assertEquals(setOf(LoanProblem.AMOUNT), problems(loan.copy(amountText = "0")))
        assertEquals("paying back less than was lent is a slip", setOf(LoanProblem.TOTAL), problems(loan.copy(totalText = "9000")))
        assertEquals(setOf(LoanProblem.TOTAL), problems(loan.copy(totalText = "abc")))
        assertEquals(setOf(LoanProblem.DUE_DATE), problems(loan.copy(plan = LoanPlan.ONE_DATE)))
        assertEquals(setOf(LoanProblem.DUE_DATE, LoanProblem.INSTALLMENT), problems(loan.copy(plan = LoanPlan.MONTHLY)))
        assertEquals(setOf(LoanProblem.INSTALLMENT), problems(loan.copy(plan = LoanPlan.MONTHLY, dueDate = dec5, installmentText = "0")))
    }

    @Test
    fun interestAndTheWayOfPayingAreStored() {
        val oneDate = LoanFormLogic.toEntity(loan.copy(totalText = "11000", plan = LoanPlan.ONE_DATE, dueDate = dec5), null, now)
        assertEquals(1_100_000L, oneDate.totalPayableCentavos)
        assertEquals(dec5, oneDate.dueDate)
        assertNull(oneDate.installmentCentavos)

        val monthly = LoanFormLogic.toEntity(loan.copy(plan = LoanPlan.MONTHLY, dueDate = dec5, installmentText = "2000"), null, now)
        assertEquals("the first payment date", dec5, monthly.dueDate)
        assertEquals(200_000L, monthly.installmentCentavos)
        assertEquals(5, monthly.dueDayOfMonth)
        assertEquals(LoanDue(dec5, 200_000, true), LoanMath.nextDue(monthly, 0))

        // Switching back to "no date" clears what the other choices stored.
        val cleared = LoanFormLogic.toEntity(LoanFormLogic.fromEntity(monthly).copy(plan = LoanPlan.NO_DATE), monthly, now)
        assertNull(cleared.dueDate)
        assertNull(cleared.installmentCentavos)
        assertNull(cleared.dueDayOfMonth)
    }

    @Test
    fun editingALoanKeepsWhatItsEntriesDependOn() {
        val lent = LoanFormLogic.toEntity(loan.copy(direction = LoanDirection.OWED_TO_ME), null, now).copy(id = 4, closedAt = now)
        val edited = LoanFormLogic.toEntity(LoanFormLogic.fromEntity(lent).copy(who = "Kuya Jun", direction = LoanDirection.I_OWE), lent, Instant.EPOCH)
        assertEquals(4L, edited.id)
        assertEquals("Kuya Jun", edited.counterparty)
        assertEquals("the direction cannot change: payments already logged go one way", LoanDirection.OWED_TO_ME, edited.direction)
        assertEquals(now, edited.closedAt)
        assertEquals(now, edited.createdAt)
    }

    @Test
    fun aLoanReadsBackIntoTheSameForm() {
        val form = loan.copy(who = "Tita Baby", totalText = "11000", plan = LoanPlan.MONTHLY, dueDate = dec5, installmentText = "2000", note = "for tuition")
        assertEquals(form, LoanFormLogic.fromEntity(LoanFormLogic.toEntity(form, null, now)))
        assertEquals("no interest reads back as an empty total", "", LoanFormLogic.fromEntity(LoanFormLogic.toEntity(loan, null, now)).totalText)
    }

    @Test
    fun aNewLoanWithAWalletMovesTheMoneyTheRightWay() {
        val borrowed = requireNotNull(LoanFormLogic.startRow(loan.copy(walletId = 7), loanId = 3, now = now))
        assertEquals(TxPurpose.LOAN_START, borrowed.purpose)
        assertEquals("borrowed money comes into the wallet", null to 7L, borrowed.fromWalletId to borrowed.toWalletId)
        assertEquals(1_000_000L to 3L, borrowed.amountCentavos to borrowed.loanId)
        assertTrue(LedgerRules.validate(borrowed, LoanDirection.I_OWE).isEmpty())

        val lent = requireNotNull(LoanFormLogic.startRow(loan.copy(direction = LoanDirection.OWED_TO_ME, walletId = 7), 3, now))
        assertEquals("lent money leaves the wallet", 7L to null, lent.fromWalletId to lent.toWalletId)
        assertTrue(LedgerRules.validate(lent, LoanDirection.OWED_TO_ME).isEmpty())

        assertNull("a loan from before the app moves nothing", LoanFormLogic.startRow(loan, 3, now))
    }

    // ---------- savings goals ----------

    @Test
    fun aGoalNeedsOnlyAName() {
        val open = GoalForm(name = " Emergency fund ")
        assertTrue(GoalFormLogic.problems(open).isEmpty())
        val entity = GoalFormLogic.toEntity(open, null, now)
        assertEquals("Emergency fund", entity.name)
        assertNull("no target: open-ended savings", entity.targetCentavos)
        assertNull("no wallet: money is only set aside", entity.walletId)

        assertEquals(setOf(GoalProblem.NAME), GoalFormLogic.problems(GoalForm()))
        assertEquals(setOf(GoalProblem.TARGET), GoalFormLogic.problems(open.copy(targetText = "0")))
    }

    @Test
    fun aGoalWithATargetDateAndWalletReadsBackTheSame() {
        val form = GoalForm(name = "Laptop", targetText = "45000.50", targetDate = LocalDate.of(2027, 6, 30), walletId = 3)
        val entity = GoalFormLogic.toEntity(form, null, now)
        assertEquals(4_500_050L, entity.targetCentavos)
        assertEquals(form, GoalFormLogic.fromEntity(entity))
        val edited = GoalFormLogic.toEntity(form.copy(targetText = ""), entity.copy(id = 9, isArchived = true), Instant.EPOCH)
        assertEquals(9L to true, edited.id to edited.isArchived)
        assertNull("clearing the target makes it open-ended again", edited.targetCentavos)
    }

    // ---------- baon ----------

    @Test
    fun aBaonNeedsANameAndAnAmount() {
        val form = BaonForm(name = " My baon ", amountText = "500", startDate = nov26)
        assertTrue(BaonFormLogic.problems(form).isEmpty())
        val entity = BaonFormLogic.toEntity(form, null, now)
        assertEquals("My baon", entity.name)
        assertEquals(50_000L, entity.amountCentavos)
        assertEquals(BaonDirection.RECEIVE to BaonPeriod.WEEKLY, entity.direction to entity.period)
        assertEquals(form.copy(name = "My baon"), BaonFormLogic.fromEntity(entity))

        assertEquals(setOf(BaonProblem.NAME, BaonProblem.AMOUNT), BaonFormLogic.problems(BaonForm(startDate = nov26)))
    }

    @Test
    fun editingABaonKeepsItsDirectionAndWhetherItIsStopped() {
        val given = BaonFormLogic.toEntity(BaonForm(direction = BaonDirection.GIVE, name = "Bunso", amountText = "200", period = BaonPeriod.DAILY, startDate = nov26), null, now)
            .copy(id = 2, isActive = false)
        val edited = BaonFormLogic.toEntity(BaonFormLogic.fromEntity(given).copy(direction = BaonDirection.RECEIVE, amountText = "250"), given, Instant.EPOCH)
        assertEquals(BaonDirection.GIVE, edited.direction)
        assertEquals(2L to false, edited.id to edited.isActive)
        assertEquals(25_000L, edited.amountCentavos)
    }

    // ---------- entries that belong to a loan, a goal or a baon ----------

    private val move = MoveForm(amountText = "2000", walletId = 7, date = nov26, note = " December ")

    private fun row(target: MoveTarget, form: MoveForm = move, existing: TransactionEntity? = null) =
        MoveFormLogic.toTransaction(form, target, existing, now).also {
            assertTrue("the ledger accepts it: $it", LedgerRules.validate(it, target.loanDirection).isEmpty())
        }

    @Test
    fun aLoanPaymentLeavesMyWalletWhenIOweAndArrivesWhenIAmOwed() {
        val paying = row(MoveTarget(TxPurpose.LOAN_PAYMENT, loanId = 3, loanDirection = LoanDirection.I_OWE))
        assertEquals(7L to null, paying.fromWalletId to paying.toWalletId)
        assertEquals(200_000L to 3L, paying.amountCentavos to paying.loanId)
        assertEquals("December", paying.note)

        val collecting = row(MoveTarget(TxPurpose.LOAN_PAYMENT, loanId = 3, loanDirection = LoanDirection.OWED_TO_ME))
        assertEquals(null to 7L, collecting.fromWalletId to collecting.toWalletId)
    }

    @Test
    fun savingMovesIntoTheGoalsWalletOrOnlySetsAside() {
        val intoGoalWallet = row(MoveTarget(TxPurpose.SAVINGS_DEPOSIT, goalId = 5, goalWalletId = 9))
        assertEquals(7L to 9L, intoGoalWallet.fromWalletId to intoGoalWallet.toWalletId)

        val setAside = row(MoveTarget(TxPurpose.SAVINGS_DEPOSIT, goalId = 5))
        assertEquals("same wallet on both sides: the balance does not move", 7L to 7L, setAside.fromWalletId to setAside.toWalletId)

        val takenOut = row(MoveTarget(TxPurpose.SAVINGS_WITHDRAWAL, goalId = 5, goalWalletId = 9))
        assertEquals(9L to 7L, takenOut.fromWalletId to takenOut.toWalletId)
        assertEquals(5L, takenOut.goalId)
    }

    @Test
    fun baonReceivedArrivesAndBaonGivenLeaves() {
        val received = row(MoveTarget(TxPurpose.BAON_RECEIVED, baonId = 2))
        assertEquals(null to 7L, received.fromWalletId to received.toWalletId)
        val given = row(MoveTarget(TxPurpose.BAON_GIVEN, baonId = 2))
        assertEquals(7L to null, given.fromWalletId to given.toWalletId)
        assertEquals(2L, given.baonId)
    }

    @Test
    fun editingAnOldDepositDoesNotReRouteItWhenTheGoalChangedWallet() {
        val target = MoveTarget(TxPurpose.SAVINGS_DEPOSIT, goalId = 5, goalWalletId = 9)
        val saved = row(target).copy(id = 11)
        // The goal has since moved to wallet 12; the old deposit still went into wallet 9.
        val edited = row(target.copy(goalWalletId = 12), move.copy(amountText = "2500"), existing = saved)
        assertEquals(11L, edited.id)
        assertEquals(250_000L, edited.amountCentavos)
        assertEquals(7L to 9L, edited.fromWalletId to edited.toWalletId)

        // A set-aside deposit follows the wallet picked for it.
        val aside = row(MoveTarget(TxPurpose.SAVINGS_DEPOSIT, goalId = 5)).copy(id = 12)
        val moved = row(MoveTarget(TxPurpose.SAVINGS_DEPOSIT, goalId = 5), move.copy(walletId = 8), existing = aside)
        assertEquals(8L to 8L, moved.fromWalletId to moved.toWalletId)
    }

    @Test
    fun anEntryReadsBackWithTheWalletThatWasPicked() {
        val deposit = row(MoveTarget(TxPurpose.SAVINGS_DEPOSIT, goalId = 5, goalWalletId = 9))
        assertEquals(MoveForm("2000", 7, nov26, "December"), MoveFormLogic.fromTransaction(deposit))
        val withdrawal = row(MoveTarget(TxPurpose.SAVINGS_WITHDRAWAL, goalId = 5, goalWalletId = 9))
        assertEquals(7L, MoveFormLogic.chosenWallet(withdrawal))
        val payment = row(MoveTarget(TxPurpose.LOAN_PAYMENT, loanId = 3, loanDirection = LoanDirection.I_OWE))
        assertEquals(7L, MoveFormLogic.chosenWallet(payment))
    }

    @Test
    fun anEntryNeedsAnAmountAndAWalletAndCannotTakeOutMoreThanIsSaved() {
        assertTrue(MoveFormLogic.problems(move, TxPurpose.LOAN_PAYMENT).isEmpty())
        assertEquals(setOf(MoveProblem.AMOUNT, MoveProblem.WALLET), MoveFormLogic.problems(MoveForm(date = nov26), TxPurpose.BAON_GIVEN))
        assertEquals(setOf(MoveProblem.MORE_THAN_SAVED), MoveFormLogic.problems(move, TxPurpose.SAVINGS_WITHDRAWAL, mostCentavos = 150_000))
        assertTrue("taking out exactly what is saved is fine", MoveFormLogic.problems(move, TxPurpose.SAVINGS_WITHDRAWAL, mostCentavos = 200_000).isEmpty())
        assertTrue("the limit is only for taking out", MoveFormLogic.problems(move, TxPurpose.SAVINGS_DEPOSIT, mostCentavos = 1).isEmpty())
    }
}
