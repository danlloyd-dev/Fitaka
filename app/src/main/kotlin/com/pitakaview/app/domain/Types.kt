package com.pitakaview.app.domain

// Stored in Room as their names (TEXT). Renaming a constant needs a database migration.

/** What kind of place money sits in. */
enum class WalletType { CASH, E_WALLET, BANK, OTHER }

/** Which provider a wallet belongs to, so file imports can match it. */
enum class WalletProvider { NONE, GCASH, MAYA, MARIBANK }

enum class CategoryKind { INCOME, EXPENSE }

/**
 * What a ledger row means. Which wallet it moves money out of / into is fixed per purpose
 * (see [LedgerRules.walletShape]).
 */
enum class TxPurpose {
    /** Kita: salary or other income into a wallet. */
    INCOME,

    /** Gastos: spending out of a wallet. May also be charged to a received baon. */
    EXPENSE,

    /** Moving money between two of your own wallets (e.g. a GCash cash-in from MariBank). */
    TRANSFER,

    /** Baon you received into a wallet. */
    BAON_RECEIVED,

    /** Baon you gave out of a wallet. */
    BAON_GIVEN,

    /** Ipon: putting money toward a goal. Wallets may be the same (money stays put, only earmarked). */
    SAVINGS_DEPOSIT,

    /** Ipon: taking money back out of a goal. */
    SAVINGS_WITHDRAWAL,

    /** Utang: the loan money changing hands (borrowed into a wallet, or lent out of one). */
    LOAN_START,

    /** Utang: a repayment (paid out of a wallet, or received into one). */
    LOAN_PAYMENT,

    /**
     * One half of a move between two of the owner's own wallets, as the two wallets' bank statements show
     * it: this half is the money leaving a wallet. Its other half is an [OWN_MOVE_IN] in the other wallet.
     * Neither spending nor income: no total counts it. Unlike [TRANSFER], which is one entry for both
     * wallets, each half is one statement's own line with that statement's reference, so each file can be
     * opened again or taken back out by itself. A half keeps the category it had as spending or income,
     * and goes back to being that when its other half goes (see LedgerRepository).
     */
    OWN_MOVE_OUT,

    /** The other half: the money arriving in a wallet. See [OWN_MOVE_OUT]. */
    OWN_MOVE_IN,
}

/** Whether this is one half of a move between the owner's own wallets. */
val TxPurpose.isOwnMove: Boolean get() = this == TxPurpose.OWN_MOVE_OUT || this == TxPurpose.OWN_MOVE_IN

enum class BaonDirection { RECEIVE, GIVE }

enum class BaonPeriod { DAILY, WEEKLY, MONTHLY }

enum class LoanDirection { I_OWE, OWED_TO_ME }

enum class PayFrequency {
    /** Twice a month on two set days, e.g. kinsenas (15th) and katapusan (last day). */
    SEMI_MONTHLY,
    MONTHLY,
    WEEKLY,
}

/** What happens when a payday lands on a Saturday or Sunday. Public holidays are not handled. */
enum class WeekendRule { KEEP, PREVIOUS_WEEKDAY, NEXT_WEEKDAY }

enum class ImportSource { GCASH, MAYA, MARIBANK, CSV }
