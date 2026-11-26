package com.pitakaview.app.domain.importing

import com.pitakaview.app.data.db.entity.WalletEntity
import com.pitakaview.app.domain.ImportSource

/** How a bank's statement and the wallet its lines are about to go into fit together. */
enum class WalletFit {
    /** The wallet is named after the bank. */
    NAMED_FOR_BANK,

    /** Another wallet is named after the bank, but it is not the one picked. */
    ANOTHER_IS_NAMED_FOR_BANK,

    /** No wallet is named after the bank. */
    NONE_NAMED_FOR_BANK,
}

/**
 * Which wallet a bank's statement belongs in. A statement says which bank it is from; a wallet only
 * has the name its owner gave it, so the two are matched by name ("MariBank", "My GCash savings").
 * Pure, so every rule is unit-tested.
 */
object StatementWallet {
    /** The bank's name as people write it; null for a file that is not a bank's own statement. */
    fun bankName(source: ImportSource): String? = when (source) {
        ImportSource.GCASH -> "GCash"
        ImportSource.MAYA -> "Maya"
        ImportSource.MARIBANK -> "MariBank"
        ImportSource.CSV -> null
    }

    fun isNamedFor(wallet: WalletEntity, bank: String): Boolean = wallet.name.contains(bank, ignoreCase = true)

    /** The one wallet named after [bank]; null when none is, or when several are and it cannot be told which. */
    fun only(bank: String, wallets: List<WalletEntity>): WalletEntity? = wallets.filter { isNamedFor(it, bank) }.singleOrNull()

    fun fit(bank: String, picked: WalletEntity, wallets: List<WalletEntity>): WalletFit = when {
        isNamedFor(picked, bank) -> WalletFit.NAMED_FOR_BANK
        wallets.any { isNamedFor(it, bank) } -> WalletFit.ANOTHER_IS_NAMED_FOR_BANK
        else -> WalletFit.NONE_NAMED_FOR_BANK
    }
}
