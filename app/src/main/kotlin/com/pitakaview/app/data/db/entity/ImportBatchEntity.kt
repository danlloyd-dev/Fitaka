package com.pitakaview.app.data.db.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import com.pitakaview.app.domain.ImportSource
import java.time.Instant

/** One imported file (GCash/Maya/MariBank/CSV). Deleting it removes every ledger row it added. */
@Entity(
    tableName = "import_batches",
    foreignKeys = [
        ForeignKey(entity = WalletEntity::class, parentColumns = ["id"], childColumns = ["walletId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("walletId")],
)
data class ImportBatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val source: ImportSource,
    val walletId: Long,
    val fileName: String,
    val rowCount: Int,
    val importedAt: Instant,
)
