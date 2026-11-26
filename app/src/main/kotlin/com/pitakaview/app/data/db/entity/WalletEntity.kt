package com.pitakaview.app.data.db.entity

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import com.pitakaview.app.domain.WalletProvider
import com.pitakaview.app.domain.WalletType
import java.time.Instant

/** A place money sits: Cash, GCash, Maya, MariBank… Its balance is computed from the ledger, never stored. */
@Entity(tableName = "wallets")
data class WalletEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: WalletType,
    val provider: WalletProvider = WalletProvider.NONE,
    /** Balance before the first ledger row, in centavos. */
    val openingBalanceCentavos: Long = 0,
    val sortOrder: Int = 0,
    /** Wallets with history are archived, not deleted, so past rows keep their wallet. */
    val isArchived: Boolean = false,
    val createdAt: Instant,
)
