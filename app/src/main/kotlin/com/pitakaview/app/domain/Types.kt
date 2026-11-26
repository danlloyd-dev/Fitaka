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
}

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
