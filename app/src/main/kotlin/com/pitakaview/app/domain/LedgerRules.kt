package com.pitakaview.app.domain

import com.pitakaview.app.data.db.entity.TransactionEntity

/** Which wallet fields a ledger row must fill. */
enum class WalletShape {
    /** Money arrives: only toWalletId. */
    INTO,

    /** Money leaves: only fromWalletId. */
    OUT_OF,

    /** Both fromWalletId and toWalletId. */
    BETWEEN,
}

enum class LedgerProblem {
    AMOUNT_NOT_POSITIVE,
    WALLETS_DONT_MATCH_PURPOSE,
    TRANSFER_TO_SAME_WALLET,
    CATEGORY_REQUIRED,
    BAON_REQUIRED,
    GOAL_REQUIRED,
    LOAN_REQUIRED,
}

/**
 * The single rule set for the ledger. Every wallet balance is
 * `opening + Σ amount where toWalletId = wallet − Σ amount where fromWalletId = wallet`,
 * so a row that names the same wallet on both sides (an earmarked savings deposit) moves nothing.
 */
object LedgerRules {

    fun walletShape(purpose: TxPurpose, loanDirection: LoanDirection? = null): WalletShape = when (purpose) {
        TxPurpose.INCOME, TxPurpose.BAON_RECEIVED, TxPurpose.OWN_MOVE_IN -> WalletShape.INTO
        TxPurpose.EXPENSE, TxPurpose.BAON_GIVEN, TxPurpose.OWN_MOVE_OUT -> WalletShape.OUT_OF
        TxPurpose.TRANSFER, TxPurpose.SAVINGS_DEPOSIT, TxPurpose.SAVINGS_WITHDRAWAL -> WalletShape.BETWEEN
        TxPurpose.LOAN_START ->
            if (requireNotNull(loanDirection) { "LOAN_START needs the loan's direction" } == LoanDirection.I_OWE) {
                WalletShape.INTO
            } else {
                WalletShape.OUT_OF
            }
        TxPurpose.LOAN_PAYMENT ->
            if (requireNotNull(loanDirection) { "LOAN_PAYMENT needs the loan's direction" } == LoanDirection.I_OWE) {
                WalletShape.OUT_OF
            } else {
                WalletShape.INTO
            }
    }

    /** Empty when [tx] is a valid row. [loanDirection] is the direction of the loan [tx] points to, if any. */
    fun validate(tx: TransactionEntity, loanDirection: LoanDirection?): Set<LedgerProblem> {
        val problems = mutableSetOf<LedgerProblem>()
        if (tx.amountCentavos <= 0) problems += LedgerProblem.AMOUNT_NOT_POSITIVE

        val isLoan = tx.purpose == TxPurpose.LOAN_START || tx.purpose == TxPurpose.LOAN_PAYMENT
        if (isLoan && (tx.loanId == null || loanDirection == null)) {
            problems += LedgerProblem.LOAN_REQUIRED
        } else {
            val shapeOk = when (walletShape(tx.purpose, loanDirection)) {
                WalletShape.INTO -> tx.toWalletId != null && tx.fromWalletId == null
                WalletShape.OUT_OF -> tx.fromWalletId != null && tx.toWalletId == null
                WalletShape.BETWEEN -> tx.fromWalletId != null && tx.toWalletId != null
            }
            if (!shapeOk) problems += LedgerProblem.WALLETS_DONT_MATCH_PURPOSE
        }

        when (tx.purpose) {
            TxPurpose.INCOME, TxPurpose.EXPENSE ->
                if (tx.categoryId == null) problems += LedgerProblem.CATEGORY_REQUIRED
            TxPurpose.TRANSFER ->
                if (tx.fromWalletId != null && tx.fromWalletId == tx.toWalletId) {
                    problems += LedgerProblem.TRANSFER_TO_SAME_WALLET
                }
            TxPurpose.BAON_RECEIVED, TxPurpose.BAON_GIVEN ->
                if (tx.baonId == null) problems += LedgerProblem.BAON_REQUIRED
            TxPurpose.SAVINGS_DEPOSIT, TxPurpose.SAVINGS_WITHDRAWAL ->
                if (tx.goalId == null) problems += LedgerProblem.GOAL_REQUIRED
            TxPurpose.LOAN_START, TxPurpose.LOAN_PAYMENT -> Unit
            // A half of an own move keeps the category it had, so it can go back to spending or income.
            TxPurpose.OWN_MOVE_OUT, TxPurpose.OWN_MOVE_IN -> Unit
        }
        return problems
    }
}
