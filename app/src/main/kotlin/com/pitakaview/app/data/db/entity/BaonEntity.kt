package com.pitakaview.app.data.db.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import com.pitakaview.app.domain.BaonDirection
import com.pitakaview.app.domain.BaonPeriod
import java.time.Instant
import java.time.LocalDate

/** Baon: an allowance you receive or give, a set amount per day, week or month. */
@Entity(
    tableName = "baon",
    foreignKeys = [
        ForeignKey(entity = WalletEntity::class, parentColumns = ["id"], childColumns = ["walletId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("walletId")],
)
data class BaonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** "My baon", or the person you give it to. */
    val name: String,
    val direction: BaonDirection,
    /** Planned amount per period, in centavos. */
    val amountCentavos: Long,
    val period: BaonPeriod,
    /** Anchors the periods: weekly baon restarts on this weekday, monthly on this day of the month. */
    val startDate: LocalDate,
    /** Default wallet for quick entry. */
    val walletId: Long? = null,
    val isActive: Boolean = true,
    val createdAt: Instant,
)
