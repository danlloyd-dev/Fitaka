package com.pitakaview.app.data.db.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import com.pitakaview.app.domain.LoanDirection
import java.time.Instant
import java.time.LocalDate

/**
 * Utang: money you owe or money owed to you. Remaining = [totalPayableCentavos] − [paidBeforeCentavos]
 * − Σ LOAN_PAYMENT rows. The money changing hands at the start is an optional LOAN_START ledger row (skip it
 * for a loan that started before you used the app).
 */
@Entity(tableName = "loans")
data class LoanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val direction: LoanDirection,
    /** Who you owe, or who owes you. */
    val counterparty: String,
    val principalCentavos: Long,
    /** Principal plus any interest or fees agreed; equals principal for an interest-free loan. */
    val totalPayableCentavos: Long,
    val startDate: LocalDate,
    /** Final (or next) due date, for reminders; the first payment date of a loan paid in installments. */
    val dueDate: LocalDate? = null,
    /** For installment loans: the amount due each time, and the day of the month it is due. */
    val installmentCentavos: Long? = null,
    val dueDayOfMonth: Int? = null,
    val note: String? = null,
    val remind: Boolean = true,
    /** Set when the loan is fully paid or written off. */
    val closedAt: Instant? = null,
    val createdAt: Instant,
    /**
     * For a loan paid twice a month: the second day of the month a payment is due ([dueDayOfMonth] is the
     * first; 31 means the last day of the month). Null for every other loan. Since layout version 2.
     */
    val secondDueDay: Int? = null,
    /**
     * What had been paid before the owner used the app: counted as paid, and never taken from a wallet
     * (that money left long before Fitaka knew of it). Since layout version 2; 0 for loans from before it.
     */
    @ColumnInfo(defaultValue = "0") val paidBeforeCentavos: Long = 0,
)
