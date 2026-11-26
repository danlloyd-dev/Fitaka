package com.pitakaview.app.domain

import com.pitakaview.app.data.db.entity.PayScheduleEntity
import com.pitakaview.app.data.db.entity.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

class PayScheduleFormLogicTest {
    private val now = Instant.parse("2026-11-26T03:00:00Z")
    private val good = PayScheduleForm(name = " Sahod ", amountText = "15000", walletId = 1, categoryId = 2)

    @Test
    fun aCompleteFormHasNoProblemsAndBecomesASchedule() {
        assertTrue(PayScheduleFormLogic.problems(good).isEmpty())
        val schedule = PayScheduleFormLogic.toEntity(good, existing = null, now = now)
        assertEquals("Sahod", schedule.name)
        assertEquals(1_500_000L, schedule.expectedAmountCentavos)
        assertEquals(PayFrequency.SEMI_MONTHLY, schedule.frequency)
        assertEquals(15 to PayRule.LAST_DAY, schedule.firstDay to schedule.secondDay)
        assertEquals("the owner chose: a weekend payday stays on its date", WeekendRule.KEEP, schedule.weekendRule)
        assertEquals(now, schedule.createdAt)
    }

    @Test
    fun eachMissingOrWrongPartIsNamed() {
        fun problems(form: PayScheduleForm) = PayScheduleFormLogic.problems(form)
        assertEquals(setOf(PayScheduleProblem.NAME), problems(good.copy(name = "  ")))
        assertEquals(setOf(PayScheduleProblem.AMOUNT), problems(good.copy(amountText = "0")))
        assertEquals(setOf(PayScheduleProblem.AMOUNT), problems(good.copy(amountText = "")))
        assertEquals(setOf(PayScheduleProblem.FIRST_DAY), problems(good.copy(firstDayText = "0")))
        assertEquals(setOf(PayScheduleProblem.SECOND_DAY), problems(good.copy(secondDayText = "32")))
        assertEquals(setOf(PayScheduleProblem.SECOND_DAY), problems(good.copy(secondDayText = "")))
        assertEquals(setOf(PayScheduleProblem.SAME_DAYS), problems(good.copy(firstDayText = "15", secondDayText = "15")))
        assertEquals(setOf(PayScheduleProblem.WALLET), problems(good.copy(walletId = null)))
        assertEquals(setOf(PayScheduleProblem.CATEGORY), problems(good.copy(categoryId = null)))
    }

    @Test
    fun onlyTheDaysThatMatterAreChecked() {
        val monthly = good.copy(frequency = PayFrequency.MONTHLY, firstDayText = "30", secondDayText = "nonsense")
        assertTrue("monthly pay has one day", PayScheduleFormLogic.problems(monthly).isEmpty())
        assertEquals(setOf(PayScheduleProblem.FIRST_DAY), PayScheduleFormLogic.problems(monthly.copy(firstDayText = "")))

        val weekly = good.copy(frequency = PayFrequency.WEEKLY, firstDayText = "", secondDayText = "")
        assertTrue("weekly pay has no month days", PayScheduleFormLogic.problems(weekly).isEmpty())
    }

    @Test
    fun twoDaysTypedInEitherOrderAreStoredEarlierFirst() {
        val schedule = PayScheduleFormLogic.toEntity(good.copy(firstDayText = "25", secondDayText = "10"), null, now)
        assertEquals(10 to 25, schedule.firstDay to schedule.secondDay)
    }

    @Test
    fun editingKeepsTheIdAndTheDayItWasSetUp() {
        val existing = PayScheduleEntity(
            id = 7, name = "Old", expectedAmountCentavos = 100, walletId = 9, categoryId = 9,
            frequency = PayFrequency.MONTHLY, firstDay = 5, createdAt = Instant.EPOCH,
        )
        val edited = PayScheduleFormLogic.toEntity(good, existing, now)
        assertEquals(7L, edited.id)
        assertEquals("paydays before the set-up day are never asked about, so that day must not move", Instant.EPOCH, edited.createdAt)
        assertEquals("Sahod", edited.name)
        assertEquals(1L to 2L, edited.walletId to edited.categoryId)
    }

    @Test
    fun weeklyPayKeepsItsWeekdayAndNeverMovesOffAWeekend() {
        val existing = PayScheduleEntity(
            id = 7, name = "Old", expectedAmountCentavos = 100, walletId = 1, categoryId = 2,
            frequency = PayFrequency.SEMI_MONTHLY, firstDay = 10, secondDay = 25, createdAt = Instant.EPOCH,
        )
        val weekly = good.copy(frequency = PayFrequency.WEEKLY, dayOfWeek = DayOfWeek.SATURDAY, weekendRule = WeekendRule.PREVIOUS_WEEKDAY)
        val schedule = PayScheduleFormLogic.toEntity(weekly, existing, now)
        assertEquals(DayOfWeek.SATURDAY, schedule.dayOfWeek)
        assertEquals("the owner picked Saturday, so it stays Saturday", WeekendRule.KEEP, schedule.weekendRule)
        assertEquals("the month days are kept for a switch back", 10 to 25, schedule.firstDay to schedule.secondDay)
    }

    @Test
    fun aScheduleReadsBackIntoTheSameForm() {
        val form = good.copy(name = "Sahod", frequency = PayFrequency.MONTHLY, firstDayText = "30", weekendRule = WeekendRule.PREVIOUS_WEEKDAY)
        val back = PayScheduleFormLogic.fromEntity(PayScheduleFormLogic.toEntity(form, null, now))
        assertEquals(form.copy(secondDayText = "31"), back)
    }

    @Test
    fun thePreviewRuleExistsOnlyWhileTheDaysMakeSense() {
        assertEquals(PayRule(PayFrequency.SEMI_MONTHLY, 15, 31), PayScheduleFormLogic.rule(good))
        assertNull(PayScheduleFormLogic.rule(good.copy(firstDayText = "")))
        assertNull(PayScheduleFormLogic.rule(good.copy(firstDayText = "31")))
        assertEquals(PayRule(PayFrequency.MONTHLY, firstDay = 20), PayScheduleFormLogic.rule(good.copy(frequency = PayFrequency.MONTHLY, firstDayText = "20")))
    }

    @Test
    fun searchTextIsTakenLiterally() {
        assertNull(SearchQuery.pattern("   "))
        assertEquals("%lunch%", SearchQuery.pattern(" lunch "))
        assertEquals("%50\\% off%", SearchQuery.pattern("50% off"))
        assertEquals("%a\\_b%", SearchQuery.pattern("a_b"))
        assertEquals("%back\\\\slash%", SearchQuery.pattern("back\\slash"))
    }

    @Test
    fun searchTextThatReadsAsAnAmountAlsoMatchesAmounts() {
        assertEquals(25_000L, SearchQuery.amountCentavos("250"))
        assertEquals(123_450L, SearchQuery.amountCentavos("₱1,234.50"))
        assertNull(SearchQuery.amountCentavos("lunch"))
        assertNull(SearchQuery.amountCentavos("0"))
    }

    // ---- the quick-entry form carries the payday link ----

    private val day = LocalDate.of(2026, 11, 30)
    private val pay = EntryForm(kind = EntryKind.INCOME, amountText = "15000", categoryId = 2, toWalletId = 1, date = day, payScheduleId = 7)

    @Test
    fun payReceivedIsSavedWithItsPayday() {
        val row = EntryFormLogic.toTransaction(pay, existing = null, now = now)
        assertEquals(7L, row.payScheduleId)
        assertEquals(TxPurpose.INCOME, row.purpose)
    }

    @Test
    fun editingPayReceivedKeepsItsPayday() {
        val saved = EntryFormLogic.toTransaction(pay, null, now).copy(id = 3)
        val form = requireNotNull(EntryFormLogic.fromTransaction(saved))
        assertEquals(7L, form.payScheduleId)
        val edited = EntryFormLogic.toTransaction(form.copy(amountText = "15500"), saved, now)
        assertEquals(7L, edited.payScheduleId)
        assertEquals(1_550_000L, edited.amountCentavos)
    }

    @Test
    fun turningPayIntoSpendingDropsThePayday() {
        val saved: TransactionEntity = EntryFormLogic.toTransaction(pay, null, now).copy(id = 3)
        val asExpense = EntryFormLogic.toTransaction(pay.copy(kind = EntryKind.EXPENSE, fromWalletId = 1), saved, now)
        assertNull(asExpense.payScheduleId)
    }
}
