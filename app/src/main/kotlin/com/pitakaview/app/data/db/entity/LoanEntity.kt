package com.pitakaview.app.data.db.entity

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import com.pitakaview.app.domain.LoanDirection
import java.time.Instant
import java.time.LocalDate

/**
 * Utang: money you owe or money owed to you. Remaining = [totalPayableCentavos] − Σ LOAN_PAYMENT rows.
 * The money changing hands at the start is an optional LOAN_START ledger row (skip it for a loan
 * that started before you used the app).
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
    /** Final (or next) due date, for reminders. */
    val dueDate: LocalDate? = null,
    /** For installment loans: amount due each month and the day it is due. */
    val installmentCentavos: Long? = null,
    val dueDayOfMonth: Int? = null,
    val note: String? = null,
    val remind: Boolean = true,
    /** Set when the loan is fully paid or written off. */
    val closedAt: Instant? = null,
    val createdAt: Instant,
)
