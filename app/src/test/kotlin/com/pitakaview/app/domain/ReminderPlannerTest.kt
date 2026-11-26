package com.pitakaview.app.domain

import com.pitakaview.app.data.db.dao.LoanWithPaid
import com.pitakaview.app.data.db.dao.PayReceipt
import com.pitakaview.app.data.db.entity.LoanEntity
import com.pitakaview.app.data.db.entity.PayScheduleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** What is worth a notification on a given day. Fixed dates: 2026-11-27 is a Friday. */
class ReminderPlannerTest {
    private val zone = ZoneId.of("Asia/Manila")
    private val friday = LocalDate.of(2026, 11, 27)
    private val setUpLongAgo = Instant.parse("2026-01-01T00:00:00Z")

    private fun loan(
        id: Long = 1,
        direction: LoanDirection = LoanDirection.I_OWE,
        total: Long = 150_000,
        dueDate: LocalDate? = null,
        installment: Long? = null,
        remind: Boolean = true,
        paid: Long = 0,
    ) = LoanWithPaid(
        LoanEntity(
            id = id, direction = direction, counterparty = "Tita Baby", principalCentavos = total, totalPayableCentavos = total,
            startDate = LocalDate.of(2026, 10, 1), dueDate = dueDate, installmentCentavos = installment,
            dueDayOfMonth = if (installment != null) dueDate?.dayOfMonth else null, remind = remind, createdAt = setUpLongAgo,
        ),
        paidCentavos = paid,
    )

    /** Paid every Friday. */
    private fun weeklyPay(id: Long = 7, remind: Boolean = true, isActive: Boolean = true) = PayScheduleEntity(
        id = id, name = "Sahod", expectedAmountCentavos = 1_500_000, walletId = 1, categoryId = 1,
        frequency = PayFrequency.WEEKLY, dayOfWeek = DayOfWeek.FRIDAY, remind = remind, isActive = isActive, createdAt = setUpLongAgo,
    )

    private fun plan(
        today: LocalDate,
        loans: List<LoanWithPaid> = emptyList(),
        schedules: List<PayScheduleEntity> = emptyList(),
        receipts: List<PayReceipt> = emptyList(),
    ) = ReminderPlanner.plan(today, loans, schedules, receipts, zone)

    @Test
    fun aPaydayIsRemindedOnTheDayItself() {
        val reminder = plan(friday, schedules = listOf(weeklyPay())).single()
        assertEquals("Payday today", reminder.title)
        assertEquals("Sahod · ₱15,000.00. Tap to mark it received.", reminder.text)
        assertEquals(ReminderKind.PAYDAY, reminder.kind)
        assertEquals(7L, reminder.rowId)

        assertEquals("not the day before", emptyList<Reminder>(), plan(friday.minusDays(1), schedules = listOf(weeklyPay())))
        assertEquals("not the day after", emptyList<Reminder>(), plan(friday.plusDays(1), schedules = listOf(weeklyPay())))
    }

    @Test
    fun aPaydayAlreadyMarkedReceivedIsNotReminded() {
        // The pay came in a day early and was logged against this payday.
        val receivedEarly = listOf(PayReceipt(payScheduleId = 7, occurredOn = friday.minusDays(1)))
        assertEquals(emptyList<Reminder>(), plan(friday, schedules = listOf(weeklyPay()), receipts = receivedEarly))
        // Someone else's receipt does not count for this payday.
        val otherPayday = listOf(PayReceipt(payScheduleId = 8, occurredOn = friday))
        assertEquals(1, plan(friday, schedules = listOf(weeklyPay()), receipts = otherPayday).size)
    }

    @Test
    fun aPaydayWithItsSwitchOffOrStoppedIsNotReminded() {
        assertEquals(emptyList<Reminder>(), plan(friday, schedules = listOf(weeklyPay(remind = false))))
        assertEquals(emptyList<Reminder>(), plan(friday, schedules = listOf(weeklyPay(isActive = false))))
    }

    @Test
    fun aLoanIOweIsRemindedTheDayBeforeOnTheDayAndOnceWhenLate() {
        val due = loan(dueDate = friday)

        val before = plan(friday.minusDays(1), loans = listOf(due)).single()
        assertEquals("Payment due tomorrow", before.title)
        assertEquals("₱1,500.00 to Tita Baby", before.text)

        val onTheDay = plan(friday, loans = listOf(due)).single()
        assertEquals("Payment due today", onTheDay.title)
        assertEquals("₱1,500.00 to Tita Baby", onTheDay.text)

        val late = plan(friday.plusDays(1), loans = listOf(due)).single()
        assertEquals("Payment overdue", late.title)
        assertEquals("₱1,500.00 to Tita Baby was due on Nov 27", late.text)

        assertEquals("two days ahead is too soon", emptyList<Reminder>(), plan(friday.minusDays(2), loans = listOf(due)))
        assertEquals("still said while this recent", 1, plan(friday.plusDays(ReminderPlanner.LATE_DAYS), loans = listOf(due)).size)
        assertEquals("an old miss does not nag", emptyList<Reminder>(), plan(friday.plusDays(ReminderPlanner.LATE_DAYS + 1), loans = listOf(due)))
    }

    @Test
    fun moneyOwedToMeIsWordedTheOtherWayRound() {
        val owed = loan(direction = LoanDirection.OWED_TO_ME, dueDate = friday)
        assertEquals("Money due to you tomorrow", plan(friday.minusDays(1), loans = listOf(owed)).single().title)
        val onTheDay = plan(friday, loans = listOf(owed)).single()
        assertEquals("Money due to you today", onTheDay.title)
        assertEquals("₱1,500.00 from Tita Baby", onTheDay.text)
        assertEquals("Money owed to you is overdue", plan(friday.plusDays(2), loans = listOf(owed)).single().title)
    }

    @Test
    fun aMonthlyLoanRemindsOfTheInstallmentThatIsNext() {
        // ₱12,000 at ₱2,000 a month from Nov 27. One installment paid: the next is Dec 27.
        val monthly = loan(total = 1_200_000, dueDate = friday, installment = 200_000, paid = 200_000)
        assertEquals("the paid one is not asked for", emptyList<Reminder>(), plan(friday, loans = listOf(monthly)))
        val next = plan(LocalDate.of(2026, 12, 27), loans = listOf(monthly)).single()
        assertEquals("Payment due today", next.title)
        assertEquals("₱2,000.00 to Tita Baby", next.text)

        // Half of an installment paid: the reminder asks for the rest of it.
        val partPaid = loan(total = 1_200_000, dueDate = friday, installment = 200_000, paid = 50_000)
        assertEquals("₱1,500.00 to Tita Baby", plan(friday, loans = listOf(partPaid)).single().text)
    }

    @Test
    fun aLoanWithNoDateOrPaidOffOrWithItsSwitchOffIsNotReminded() {
        assertEquals(emptyList<Reminder>(), plan(friday, loans = listOf(loan(dueDate = null))))
        assertEquals(emptyList<Reminder>(), plan(friday, loans = listOf(loan(dueDate = friday, paid = 150_000))))
        assertEquals(emptyList<Reminder>(), plan(friday, loans = listOf(loan(dueDate = friday, remind = false))))
    }

    @Test
    fun eachMomentHasItsOwnKeyAndEachThingItsOwnPlaceInThePhonesList() {
        val due = loan(id = 3, dueDate = friday)
        val before = plan(friday.minusDays(1), loans = listOf(due)).single()
        val onTheDay = plan(friday, loans = listOf(due)).single()
        val late = plan(friday.plusDays(1), loans = listOf(due)).single()
        // Different keys: each of the three is shown once.
        assertEquals(3, setOf(before.key, onTheDay.key, late.key).size)
        // Same key when asked again the same day: not shown twice.
        assertEquals(onTheDay.key, plan(friday, loans = listOf(due)).single().key)
        // One slot per loan: "due today" replaces "due tomorrow" instead of piling up.
        assertEquals(before.notificationId, onTheDay.notificationId)

        val otherLoan = plan(friday, loans = listOf(loan(id = 4, dueDate = friday))).single()
        assertNotEquals(onTheDay.notificationId, otherLoan.notificationId)
        // A payday and a loan that happen to share an id do not share a slot.
        val payday = plan(friday, schedules = listOf(weeklyPay(id = 3))).single()
        assertNotEquals(onTheDay.notificationId, payday.notificationId)
        // A payday's key changes with the date, so next week's is shown too.
        assertNotEquals(payday.key, plan(friday.plusDays(7), schedules = listOf(weeklyPay(id = 3))).single().key)
    }

    @Test
    fun oldKeysAreForgottenAndUnreadableOnesDropped() {
        val reminder = plan(friday, loans = listOf(loan(dueDate = friday)), schedules = listOf(weeklyPay()))
        reminder.forEach { assertTrue(it.key, ReminderPlanner.isKeyFresh(it.key, friday)) }
        reminder.forEach { assertTrue(it.key, ReminderPlanner.isKeyFresh(it.key, friday.plusDays(ReminderPlanner.KEEP_KEYS_DAYS))) }
        reminder.forEach { assertFalse(it.key, ReminderPlanner.isKeyFresh(it.key, friday.plusDays(ReminderPlanner.KEEP_KEYS_DAYS + 1))) }
        assertFalse(ReminderPlanner.isKeyFresh("nonsense", friday))
        assertFalse(ReminderPlanner.isKeyFresh("loan:1:not-a-date:day", friday))
    }

    @Test
    fun remindersWaitForTheMorningAndTheNextCheckIsTheComingEightOClock() {
        assertFalse(ReminderPlanner.isTime(LocalDateTime.of(2026, 11, 27, 7, 59)))
        assertTrue(ReminderPlanner.isTime(LocalDateTime.of(2026, 11, 27, 8, 0)))
        assertTrue(ReminderPlanner.isTime(LocalDateTime.of(2026, 11, 27, 23, 30)))

        val eightToday = LocalDateTime.of(2026, 11, 27, 8, 0)
        assertEquals("before eight: today", eightToday, ReminderPlanner.nextRun(LocalDateTime.of(2026, 11, 27, 6, 15)))
        assertEquals("at eight exactly: tomorrow, not now again", eightToday.plusDays(1), ReminderPlanner.nextRun(eightToday))
        assertEquals("after eight: tomorrow", eightToday.plusDays(1), ReminderPlanner.nextRun(LocalDateTime.of(2026, 11, 27, 15, 0)))
        assertEquals("over a month end", LocalDateTime.of(2026, 12, 1, 8, 0), ReminderPlanner.nextRun(LocalDateTime.of(2026, 11, 30, 21, 0)))
    }
}
