package com.pitakaview.app.domain

import com.pitakaview.app.data.db.entity.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class EntryFormLogicTest {
    private val day = LocalDate.of(2026, 11, 26)
    private val now = Instant.parse("2026-11-26T02:00:00Z")

    private fun expense(amount: String = "250") =
        EntryForm(EntryKind.EXPENSE, amountText = amount, categoryId = 3, fromWalletId = 1, date = day)

    @Test
    fun anEmptyFormNamesEverythingThatIsMissing() {
        assertEquals(
            setOf(EntryProblem.AMOUNT, EntryProblem.CATEGORY, EntryProblem.WALLET),
            EntryFormLogic.problems(EntryForm(date = day)),
        )
    }

    @Test
    fun onlyPlainAmountsAboveZeroAreAccepted() {
        for (bad in listOf("", "0", "0.00", "12.345", "abc", "250.")) {
            assertEquals("amount '$bad'", setOf(EntryProblem.AMOUNT), EntryFormLogic.problems(expense(bad)))
        }
        for ((typed, centavos) in listOf("250" to 25_000L, "0.05" to 5L, "1234.5" to 123_450L, "1,234.50" to 123_450L)) {
            assertEquals("amount '$typed'", centavos, EntryFormLogic.amountCentavos(expense(typed)))
        }
    }

    @Test
    fun eachKindFillsTheRightWallets() {
        val spent = EntryFormLogic.toTransaction(expense().copy(note = "  lunch  "), null, now)
        assertEquals(TxPurpose.EXPENSE, spent.purpose)
        assertEquals(25_000L, spent.amountCentavos)
        assertEquals(1L, spent.fromWalletId)
        assertNull(spent.toWalletId)
        assertEquals(3L, spent.categoryId)
        assertEquals("lunch", spent.note)
        assertEquals(day, spent.occurredOn)
        assertEquals(now, spent.createdAt)

        val earned = EntryFormLogic.toTransaction(
            EntryForm(EntryKind.INCOME, "15000", categoryId = 11, toWalletId = 2, date = day), null, now,
        )
        assertEquals(TxPurpose.INCOME, earned.purpose)
        assertNull(earned.fromWalletId)
        assertEquals(2L, earned.toWalletId)
        assertNull("an empty note is stored as nothing", earned.note)

        val moved = EntryFormLogic.toTransaction(
            EntryForm(EntryKind.TRANSFER, "3000", categoryId = 3, fromWalletId = 2, toWalletId = 1, date = day), null, now,
        )
        assertEquals(TxPurpose.TRANSFER, moved.purpose)
        assertEquals(2L to 1L, moved.fromWalletId to moved.toWalletId)
        assertNull("transfers have no category", moved.categoryId)

        // What the form builds must also satisfy the ledger's own rules.
        for (tx in listOf(spent, earned, moved)) assertTrue("$tx", LedgerRules.validate(tx, null).isEmpty())
    }

    @Test
    fun transfersNeedTwoDifferentWallets() {
        val form = EntryForm(EntryKind.TRANSFER, "100", date = day, fromWalletId = 1)
        assertEquals(setOf(EntryProblem.WALLET), EntryFormLogic.problems(form))
        assertEquals(setOf(EntryProblem.SAME_WALLET), EntryFormLogic.problems(form.copy(toWalletId = 1)))
        assertTrue(EntryFormLogic.problems(form.copy(toWalletId = 2)).isEmpty())
    }

    @Test
    fun editingKeepsTheRowsIdentityAndClearsLinksThatNoLongerFit() {
        val original = TransactionEntity(
            id = 42, purpose = TxPurpose.EXPENSE, amountCentavos = 9_900, occurredOn = day.minusDays(3),
            fromWalletId = 1, categoryId = 3, baonId = 7, importBatchId = 5, externalRef = "GCASH:1001",
            createdAt = Instant.EPOCH,
        )
        // Since M4 quick entry shows the baon as a choice, so the form carries it: an edit that starts
        // from the saved row keeps it, and unpicking it takes the spending off the baon.
        val loaded = requireNotNull(EntryFormLogic.fromTransaction(original))
        assertEquals(7L, loaded.baonId)
        val stillSpending = EntryFormLogic.toTransaction(loaded.copy(amountText = "120"), original, now)
        assertEquals(42L, stillSpending.id)
        assertEquals(Instant.EPOCH, stillSpending.createdAt)
        assertEquals("GCASH:1001", stillSpending.externalRef)
        assertEquals(5L, stillSpending.importBatchId)
        assertEquals("spending stays charged to its baon", 7L, stillSpending.baonId)
        assertEquals(12_000L, stillSpending.amountCentavos)

        val unpicked = EntryFormLogic.toTransaction(loaded.copy(baonId = null), original, now)
        assertEquals(42L, unpicked.id)
        assertNull("the owner chose 'not from baon'", unpicked.baonId)

        val nowIncome = EntryFormLogic.toTransaction(
            EntryForm(EntryKind.INCOME, "120", categoryId = 11, toWalletId = 1, date = day), original, now,
        )
        assertEquals(42L, nowIncome.id)
        assertNull("income cannot be charged to a baon", nowIncome.baonId)
        assertNull(nowIncome.fromWalletId)
    }

    @Test
    fun aSavedRowLoadsBackIntoTheSameForm() {
        val form = expense("1234.56").copy(note = "groceries")
        val tx = EntryFormLogic.toTransaction(form, null, now)
        assertEquals(form, EntryFormLogic.fromTransaction(tx))
        assertEquals("250", EntryFormLogic.plainAmount(25_000))
        assertEquals("0.05", EntryFormLogic.plainAmount(5))

        val loanPayment = tx.copy(purpose = TxPurpose.LOAN_PAYMENT, loanId = 1)
        assertNull("loan rows are edited in the Utang tab", EntryFormLogic.fromTransaction(loanPayment))
    }

    @Test
    fun correctingABalanceMovesTheStartingBalanceByTheDifference() {
        // Started at ₱0, spent ₱250, so the app shows −₱250. The wallet really holds ₱1,000.
        val opening = WalletRules.openingForActualBalance(opening = 0, current = -25_000, actual = 100_000)
        assertEquals(125_000L, opening)
        assertEquals("new opening − the same ₱250 spent = the real balance", 100_000L, opening - 25_000)
        assertEquals("no difference, no change", 5_000L, WalletRules.openingForActualBalance(5_000, 80_000, 80_000))
    }
}
