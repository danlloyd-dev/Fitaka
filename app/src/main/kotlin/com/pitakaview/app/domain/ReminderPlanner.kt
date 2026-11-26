package com.pitakaview.app.domain

import com.pitakaview.app.data.db.dao.LoanWithPaid
import com.pitakaview.app.data.db.dao.PayReceipt
import com.pitakaview.app.data.db.entity.PayScheduleEntity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

enum class ReminderKind { PAYDAY, LOAN }

/**
 * One notification worth showing. [key] names the thing and the moment ("this loan's due date, the day
 * before"), so each is shown once however often the check runs. [notificationId] is one per payday or
 * loan, so a newer reminder about the same loan replaces the older one in the phone's list.
 */
data class Reminder(
    val key: String,
    val notificationId: Int,
    val kind: ReminderKind,
    /** The pay schedule's id or the loan's id: what a tap opens. */
    val rowId: Long,
    val title: String,
    val text: String,
)

/**
 * Decides what is worth a notification on a given day. Pure: it reads the same rows and uses the same
 * rules ([PaydayTracker], [LoanMath]) as "Coming up" on Home, so a notification never disagrees with the app.
 *
 * - A payday: on the day itself, unless the pay is already marked received.
 * - A loan's due date or monthly installment, either way round: the day before, the day itself, and
 *   once more if it is still unpaid up to [LATE_DAYS] days later.
 */
object ReminderPlanner {
    /** Reminders are shown from this hour of the morning, never in the night. */
    const val HOUR = 8

    /** How long after a missed due date it is still worth saying so. */
    const val LATE_DAYS = 3L

    /** Keys of shown reminders are forgotten after this many days; nothing that old can come up again. */
    const val KEEP_KEYS_DAYS = 45L

    private val SHORT_DATE = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)

    fun plan(
        today: LocalDate,
        openLoans: List<LoanWithPaid>,
        paySchedules: List<PayScheduleEntity>,
        payReceipts: List<PayReceipt>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Reminder> {
        val receivedOn = payReceipts.groupBy({ it.payScheduleId }, { it.occurredOn })
        val paydays = paySchedules.filter { it.isActive && it.remind }.mapNotNull { schedule ->
            val setUpOn = schedule.createdAt.atZone(zone).toLocalDate()
            val waiting = PaydayTracker.waiting(schedule.rule(), receivedOn[schedule.id].orEmpty(), today, setUpOn, count = 1).firstOrNull()
            if (waiting?.date != today) return@mapNotNull null
            Reminder(
                key = "payday:${schedule.id}:$today",
                notificationId = notificationId(ReminderKind.PAYDAY, schedule.id),
                kind = ReminderKind.PAYDAY,
                rowId = schedule.id,
                title = "Payday today",
                text = "${schedule.name} · ${Peso.format(schedule.expectedAmountCentavos)}. Tap to mark it received.",
            )
        }
        val dues = openLoans.filter { it.loan.remind }.mapNotNull { row ->
            val due = LoanMath.nextDue(row.loan, row.paidCentavos) ?: return@mapNotNull null
            val iOwe = row.loan.direction == LoanDirection.I_OWE
            val amount = "${Peso.format(due.amountCentavos)} ${if (iOwe) "to" else "from"} ${row.loan.counterparty}"
            val (stage, title, text) = when {
                due.date == today.plusDays(1) ->
                    Triple("before", if (iOwe) "Payment due tomorrow" else "Money due to you tomorrow", amount)
                due.date == today ->
                    Triple("day", if (iOwe) "Payment due today" else "Money due to you today", amount)
                due.date < today && due.date >= today.minusDays(LATE_DAYS) ->
                    Triple("late", if (iOwe) "Payment overdue" else "Money owed to you is overdue", "$amount was due on ${SHORT_DATE.format(due.date)}")
                else -> return@mapNotNull null
            }
            Reminder(
                key = "loan:${row.loan.id}:${due.date}:$stage",
                notificationId = notificationId(ReminderKind.LOAN, row.loan.id),
                kind = ReminderKind.LOAN,
                rowId = row.loan.id,
                title = title,
                text = text,
            )
        }
        return paydays + dues
    }

    fun notificationId(kind: ReminderKind, rowId: Long): Int = ((kind.ordinal + 1) * 1_000_000 + rowId % 1_000_000).toInt()

    /** Whether a shown-reminder key is recent enough to keep. A key that cannot be read is dropped. */
    fun isKeyFresh(key: String, today: LocalDate): Boolean {
        val date = key.split(':').getOrNull(2) ?: return false
        return try {
            LocalDate.parse(date) >= today.minusDays(KEEP_KEYS_DAYS)
        } catch (e: DateTimeParseException) {
            false
        }
    }

    /** Whether it is late enough in the day to show reminders. */
    fun isTime(now: LocalDateTime): Boolean = now.hour >= HOUR

    /** The next time the daily check should run: [HOUR] o'clock today if that is still ahead, else tomorrow. */
    fun nextRun(now: LocalDateTime): LocalDateTime {
        val todayRun = now.toLocalDate().atTime(HOUR, 0)
        return if (now < todayRun) todayRun else todayRun.plusDays(1)
    }
}
