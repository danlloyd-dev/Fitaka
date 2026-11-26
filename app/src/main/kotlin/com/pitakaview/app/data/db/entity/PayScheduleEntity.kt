package com.pitakaview.app.data.db.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import com.pitakaview.app.domain.PayFrequency
import com.pitakaview.app.domain.PayRule
import com.pitakaview.app.domain.WeekendRule
import java.time.DayOfWeek
import java.time.Instant

/** Kita: a regular income such as salary on kinsenas/katapusan. Drives payday reminders. */
@Entity(
    tableName = "pay_schedules",
    foreignKeys = [
        ForeignKey(entity = WalletEntity::class, parentColumns = ["id"], childColumns = ["walletId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("walletId"), Index("categoryId")],
)
data class PayScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val expectedAmountCentavos: Long,
    /** Wallet the pay lands in. */
    val walletId: Long,
    val categoryId: Long,
    val frequency: PayFrequency,
    val firstDay: Int = 15,
    val secondDay: Int = PayRule.LAST_DAY,
    val dayOfWeek: DayOfWeek = DayOfWeek.FRIDAY,
    val weekendRule: WeekendRule = WeekendRule.PREVIOUS_WEEKDAY,
    val remind: Boolean = true,
    val isActive: Boolean = true,
    val createdAt: Instant,
) {
    fun rule() = PayRule(frequency, firstDay, secondDay, dayOfWeek, weekendRule)
}
