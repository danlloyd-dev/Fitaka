package com.pitakaview.app.domain

import com.pitakaview.app.data.db.entity.TransactionEntity
import com.pitakaview.app.domain.LedgerProblem.AMOUNT_NOT_POSITIVE
import com.pitakaview.app.domain.LedgerProblem.BAON_REQUIRED
import com.pitakaview.app.domain.LedgerProblem.CATEGORY_REQUIRED
import com.pitakaview.app.domain.LedgerProblem.GOAL_REQUIRED
import com.pitakaview.app.domain.LedgerProblem.LOAN_REQUIRED
import com.pitakaview.app.domain.LedgerProblem.TRANSFER_TO_SAME_WALLET
import com.pitakaview.app.domain.LedgerProblem.WALLETS_DONT_MATCH_PURPOSE
import com.pitakaview.app.domain.LoanDirection.I_OWE
import com.pitakaview.app.domain.LoanDirection.OWED_TO_ME
import com.pitakaview.app.domain.TxPurpose.BAON_GIVEN
import com.pitakaview.app.domain.TxPurpose.BAON_RECEIVED
import com.pitakaview.app.domain.TxPurpose.EXPENSE
import com.pitakaview.app.domain.TxPurpose.INCOME
import com.pitakaview.app.domain.TxPurpose.LOAN_PAYMENT
import com.pitakaview.app.domain.TxPurpose.LOAN_START
import com.pitakaview.app.domain.TxPurpose.SAVINGS_DEPOSIT
import com.pitakaview.app.domain.TxPurpose.SAVINGS_WITHDRAWAL
import com.pitakaview.app.domain.TxPurpose.TRANSFER
import com.pitakaview.app.domain.WalletShape.BETWEEN
import com.pitakaview.app.domain.WalletShape.INTO
import com.pitakaview.app.domain.WalletShape.OUT_OF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class LedgerRulesTest {
    private fun tx(
        purpose: TxPurpose,
        amount: Long = 100,
        from: Long? = null,
        to: Long? = null,
        category: Long? = null,
        baon: Long? = null,
        goal: Long? = null,
        loan: Long? = null,
    ) = TransactionEntity(
        purpose = purpose, amountCentavos = amount, occurredOn = LocalDate.of(2026, 11, 25),
        fromWalletId = from, toWalletId = to, categoryId = category, baonId = baon, goalId = goal, loanId = loan,
        createdAt = Instant.EPOCH,
    )

    @Test
    fun walletShapeFollowsPurposeAndLoanDirection() {
        assertEquals(INTO, LedgerRules.walletShape(INCOME))
        assertEquals(OUT_OF, LedgerRules.walletShape(EXPENSE))
        assertEquals(BETWEEN, LedgerRules.walletShape(TRANSFER))
        assertEquals(INTO, LedgerRules.walletShape(BAON_RECEIVED))
        assertEquals(OUT_OF, LedgerRules.walletShape(BAON_GIVEN))
        assertEquals(BETWEEN, LedgerRules.walletShape(SAVINGS_DEPOSIT))
        assertEquals(BETWEEN, LedgerRules.walletShape(SAVINGS_WITHDRAWAL))
        // Borrowing puts money into my wallet; lending takes it out. Repaying reverses each.
        assertEquals(INTO, LedgerRules.walletShape(LOAN_START, I_OWE))
        assertEquals(OUT_OF, LedgerRules.walletShape(LOAN_START, OWED_TO_ME))
        assertEquals(OUT_OF, LedgerRules.walletShape(LOAN_PAYMENT, I_OWE))
        assertEquals(INTO, LedgerRules.walletShape(LOAN_PAYMENT, OWED_TO_ME))
    }

    @Test
    fun validRowsHaveNoProblems() {
        assertTrue(LedgerRules.validate(tx(INCOME, to = 1, category = 1), null).isEmpty())
        assertTrue(LedgerRules.validate(tx(EXPENSE, from = 1, category = 1, baon = 4), null).isEmpty())
        assertTrue(LedgerRules.validate(tx(TRANSFER, from = 1, to = 2), null).isEmpty())
        // Earmarked savings: same wallet on both sides is allowed (moves nothing).
        assertTrue(LedgerRules.validate(tx(SAVINGS_DEPOSIT, from = 1, to = 1, goal = 9), null).isEmpty())
        assertTrue(LedgerRules.validate(tx(LOAN_PAYMENT, from = 1, loan = 7), I_OWE).isEmpty())
    }

    @Test
    fun brokenRowsAreNamed() {
        assertEquals(setOf(AMOUNT_NOT_POSITIVE), LedgerRules.validate(tx(EXPENSE, amount = 0, from = 1, category = 1), null))
        assertEquals(setOf(CATEGORY_REQUIRED), LedgerRules.validate(tx(EXPENSE, from = 1), null))
        assertEquals(setOf(WALLETS_DONT_MATCH_PURPOSE), LedgerRules.validate(tx(EXPENSE, to = 1, category = 1), null))
        assertEquals(setOf(TRANSFER_TO_SAME_WALLET), LedgerRules.validate(tx(TRANSFER, from = 1, to = 1), null))
        assertEquals(setOf(BAON_REQUIRED), LedgerRules.validate(tx(BAON_GIVEN, from = 1), null))
        assertEquals(setOf(GOAL_REQUIRED), LedgerRules.validate(tx(SAVINGS_DEPOSIT, from = 1, to = 2), null))
        assertEquals(setOf(LOAN_REQUIRED), LedgerRules.validate(tx(LOAN_PAYMENT, from = 1), null))
        // Paying a loan I owe INTO my wallet is the wrong way round.
        assertEquals(setOf(WALLETS_DONT_MATCH_PURPOSE), LedgerRules.validate(tx(LOAN_PAYMENT, to = 1, loan = 7), I_OWE))
    }
}
