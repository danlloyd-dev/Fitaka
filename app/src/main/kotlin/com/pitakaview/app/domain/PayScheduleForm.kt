package com.pitakaview.app.domain

import com.pitakaview.app.data.db.entity.PayScheduleEntity
import java.time.DayOfWeek
import java.time.Instant

/** What the payday screen holds while you type. Day 31 means the last day of the month. */
data class PayScheduleForm(
    val name: String = "",
    val amountText: String = "",
    val frequency: PayFrequency = PayFrequency.SEMI_MONTHLY,
    val firstDayText: String = "15",
    val secondDayText: String = "31",
    val dayOfWeek: DayOfWeek = DayOfWeek.FRIDAY,
    val weekendRule: WeekendRule = WeekendRule.KEEP,
    val walletId: Long? = null,
    val categoryId: Long? = null,
    /** Whether to notify on the payday (when reminders are on in Settings). */
    val remind: Boolean = true,
)

enum class PayScheduleProblem { NAME, AMOUNT, FIRST_DAY, SECOND_DAY, SAME_DAYS, WALLET, CATEGORY }

/** Turns the payday form into a pay schedule and back. Pure, so every rule is unit-tested. */
object PayScheduleFormLogic {

    /** A typed day of the month, or null unless it is 1 to 31. */
    fun day(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in 1..PayRule.LAST_DAY }

    fun amountCentavos(form: PayScheduleForm): Long? = Peso.parse(form.amountText)?.takeIf { it > 0 }

    fun problems(form: PayScheduleForm): Set<PayScheduleProblem> = buildSet {
        if (form.name.isBlank()) add(PayScheduleProblem.NAME)
        if (amountCentavos(form) == null) add(PayScheduleProblem.AMOUNT)
        val first = day(form.firstDayText)
        val second = day(form.secondDayText)
        when (form.frequency) {
            PayFrequency.SEMI_MONTHLY -> {
                if (first == null) add(PayScheduleProblem.FIRST_DAY)
                if (second == null) add(PayScheduleProblem.SECOND_DAY)
                if (first != null && first == second) add(PayScheduleProblem.SAME_DAYS)
            }
            PayFrequency.MONTHLY -> if (first == null) add(PayScheduleProblem.FIRST_DAY)
            PayFrequency.WEEKLY -> Unit
        }
        if (form.walletId == null) add(PayScheduleProblem.WALLET)
        if (form.categoryId == null) add(PayScheduleProblem.CATEGORY)
    }

    /** The rule the form describes, or null while its days are not valid yet. Used for the "next paydays" preview. */
    fun rule(form: PayScheduleForm): PayRule? {
        val first = day(form.firstDayText)
        val second = day(form.secondDayText)
        return when (form.frequency) {
            PayFrequency.SEMI_MONTHLY ->
                if (first == null || second == null || first == second) {
                    null
                } else {
                    PayRule(form.frequency, minOf(first, second), maxOf(first, second), form.dayOfWeek, form.weekendRule)
                }
            PayFrequency.MONTHLY -> first?.let { PayRule(form.frequency, firstDay = it, dayOfWeek = form.dayOfWeek, weekendRule = form.weekendRule) }
            // A weekly payday is a weekday the owner picked, so there is no weekend to move it off.
            PayFrequency.WEEKLY -> PayRule(form.frequency, dayOfWeek = form.dayOfWeek, weekendRule = WeekendRule.KEEP)
        }
    }

    /** Builds the schedule. Call only when [problems] is empty. Editing keeps the id and the day it was set up. */
    fun toEntity(form: PayScheduleForm, existing: PayScheduleEntity?, now: Instant): PayScheduleEntity {
        val rule = requireNotNull(rule(form)) { "Days must be checked with problems() first" }
        val amount = requireNotNull(amountCentavos(form)) { "Amount must be checked with problems() first" }
        val walletId = requireNotNull(form.walletId) { "Wallet must be checked with problems() first" }
        val categoryId = requireNotNull(form.categoryId) { "Category must be checked with problems() first" }
        val base = existing ?: PayScheduleEntity(
            name = "", expectedAmountCentavos = amount, walletId = walletId, categoryId = categoryId,
            frequency = form.frequency, createdAt = now,
        )
        // Weekly pay does not use the month days; keep whatever was there in case the owner switches back.
        val weekly = form.frequency == PayFrequency.WEEKLY
        return base.copy(
            name = form.name.trim(),
            expectedAmountCentavos = amount,
            walletId = walletId,
            categoryId = categoryId,
            frequency = form.frequency,
            firstDay = if (weekly) base.firstDay else rule.firstDay,
            secondDay = if (weekly) base.secondDay else rule.secondDay,
            dayOfWeek = rule.dayOfWeek,
            weekendRule = rule.weekendRule,
            remind = form.remind,
        )
    }

    fun fromEntity(schedule: PayScheduleEntity) = PayScheduleForm(
        name = schedule.name,
        amountText = EntryFormLogic.plainAmount(schedule.expectedAmountCentavos),
        frequency = schedule.frequency,
        firstDayText = schedule.firstDay.toString(),
        secondDayText = schedule.secondDay.toString(),
        dayOfWeek = schedule.dayOfWeek,
        weekendRule = schedule.weekendRule,
        walletId = schedule.walletId,
        categoryId = schedule.categoryId,
        remind = schedule.remind,
    )
}

/** Turns what was typed into the search box into what the database is asked for. */
object SearchQuery {
    /**
     * A LIKE pattern that finds [text] anywhere, with the characters LIKE treats specially (`%`, `_`
     * and the escape `\`) made literal. Null when there is nothing to search for.
     */
    fun pattern(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        val literal = buildString {
            for (ch in trimmed) {
                when {
                    ch == '\\' || ch == '%' || ch == '_' -> append('\\').append(ch)
                    // The database would compare this letter exactly; "any one character" lets both
                    // forms through, and [matches] then keeps the rows that really have it.
                    hasCaseTheDatabaseIgnores(ch) -> append('_')
                    else -> append(ch)
                }
            }
        }
        return "%$literal%"
    }

    /**
     * True for a letter outside plain English that has a capital and a small form (ñ, é). The database
     * treats "A" and "a" as the same letter but not "Ñ" and "ñ", so for these the app does the comparing.
     */
    private fun hasCaseTheDatabaseIgnores(ch: Char): Boolean = ch.code > 127 && ch.lowercaseChar() != ch.uppercaseChar()

    /** Whether rows found with [pattern] for this [text] still have to be checked with [matches]. */
    fun needsOwnMatching(text: String): Boolean = text.trim().any(::hasCaseTheDatabaseIgnores)

    /** Whether [text] is in any of [fields], capitals and small letters counting as the same (so "piña" finds "PIÑA"). */
    fun matches(text: String, vararg fields: String?): Boolean {
        val wanted = text.trim()
        return fields.any { it != null && it.contains(wanted, ignoreCase = true) }
    }

    /** The amount to match exactly when the text reads as one ("250", "1,234.50", "₱20"), else null. */
    fun amountCentavos(text: String): Long? = Peso.parse(text)?.takeIf { it > 0 }
}
