package com.pitakaview.app.ui.theme

import com.pitakaview.app.domain.TxPurpose

/** A short English line under each Filipino section name. */
val Section.tagline: String
    get() = when (this) {
        Section.KITA -> "Income & paydays"
        Section.BAON -> "Allowance"
        Section.IPON -> "Savings & goals"
        Section.GASTOS -> "Spending"
        Section.UTANG -> "Loans"
    }

/** The section a ledger row belongs to; null for moves between your own wallets. */
val TxPurpose.section: Section?
    get() = when (this) {
        TxPurpose.INCOME -> Section.KITA
        TxPurpose.EXPENSE -> Section.GASTOS
        TxPurpose.TRANSFER, TxPurpose.OWN_MOVE_OUT, TxPurpose.OWN_MOVE_IN -> null
        TxPurpose.BAON_RECEIVED, TxPurpose.BAON_GIVEN -> Section.BAON
        TxPurpose.SAVINGS_DEPOSIT, TxPurpose.SAVINGS_WITHDRAWAL -> Section.IPON
        TxPurpose.LOAN_START, TxPurpose.LOAN_PAYMENT -> Section.UTANG
    }

/** Fallback title for rows without a category. */
val TxPurpose.label: String
    get() = when (this) {
        TxPurpose.INCOME -> "Income"
        TxPurpose.EXPENSE -> "Spending"
        TxPurpose.TRANSFER -> "Transfer"
        TxPurpose.BAON_RECEIVED -> "Baon received"
        TxPurpose.BAON_GIVEN -> "Baon given"
        TxPurpose.SAVINGS_DEPOSIT -> "Added to savings"
        TxPurpose.SAVINGS_WITHDRAWAL -> "Took from savings"
        TxPurpose.LOAN_START -> "Loan"
        TxPurpose.LOAN_PAYMENT -> "Loan payment"
        TxPurpose.OWN_MOVE_OUT -> "Moved to my other wallet"
        TxPurpose.OWN_MOVE_IN -> "Moved from my other wallet"
    }
