package com.pitakaview.app.domain

import com.pitakaview.app.data.db.entity.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * One half of a move between the owner's own wallets (TxPurpose.OWN_MOVE_OUT and OWN_MOVE_IN) as the
 * ledger's rules, the quick-entry form and the spreadsheet export see it.
 */
class OwnMoveEntryTest {
    private val now = Instant.parse("2026-11-25T08:00:00Z")
    private val nov3 = LocalDate.of(2026, 11, 3)

    private val halfOut = TransactionEntity(
        id = 9, purpose = TxPurpose.OWN_MOVE_OUT, amountCentavos = 500_000, occurredOn = nov3, fromWalletId = 1, categoryId = 4,
        note = "Transfer to MariBank", importBatchId = 2, externalRef = "GCASH:1:G1:out:500000", createdAt = now,
    )
    private val halfIn = halfOut.copy(id = 10, purpose = TxPurpose.OWN_MOVE_IN, fromWalletId = null, toWalletId = 2, externalRef = "MARIBANK:2:M1:in:500000")

    @Test
    fun aHalfTouchesOneWalletTheWaySpendingOrIncomeDoes() {
        assertEquals(WalletShape.OUT_OF, LedgerRules.walletShape(TxPurpose.OWN_MOVE_OUT))
        assertEquals(WalletShape.INTO, LedgerRules.walletShape(TxPurpose.OWN_MOVE_IN))
        assertTrue(LedgerRules.validate(halfOut, null).isEmpty())
        assertTrue(LedgerRules.validate(halfIn, null).isEmpty())
        assertEquals(
            "money leaving cannot be written as arriving",
            setOf(LedgerProblem.WALLETS_DONT_MATCH_PURPOSE), LedgerRules.validate(halfOut.copy(fromWalletId = null, toWalletId = 2), null),
        )
        assertTrue("a category is not asked of it", LedgerRules.validate(halfOut.copy(categoryId = null), null).isEmpty())
        assertTrue(TxPurpose.OWN_MOVE_OUT.isOwnMove && TxPurpose.OWN_MOVE_IN.isOwnMove)
        assertFalse(TxPurpose.TRANSFER.isOwnMove || TxPurpose.EXPENSE.isOwnMove || TxPurpose.INCOME.isOwnMove)
    }

    @Test
    fun theFormOpensAHalfAsMoneyOutOrInAndSavesItAsTheHalfItIs() {
        val form = EntryFormLogic.fromTransaction(halfOut)!!
        assertEquals(EntryKind.EXPENSE, form.kind)
        assertTrue(form.ownMove)
        assertEquals(EntryKind.INCOME, EntryFormLogic.fromTransaction(halfIn)!!.kind)

        // A change of note: everything that ties it to its file and its other half is kept.
        val saved = EntryFormLogic.toTransaction(form.copy(note = "to MariBank"), halfOut, now)
        assertEquals(halfOut.copy(note = "to MariBank"), saved)
        assertEquals(halfIn, EntryFormLogic.toTransaction(EntryFormLogic.fromTransaction(halfIn)!!, halfIn, now))
    }

    @Test
    fun saidToBeSpendingAfterAllItIsSavedAsSpending() {
        val form = EntryFormLogic.fromTransaction(halfOut)!!.copy(ownMove = false)
        assertEquals(halfOut.copy(purpose = TxPurpose.EXPENSE), EntryFormLogic.toTransaction(form, halfOut, now))
        val income = EntryFormLogic.fromTransaction(halfIn)!!.copy(ownMove = false)
        assertEquals(TxPurpose.INCOME, EntryFormLogic.toTransaction(income, halfIn, now).purpose)
    }

    @Test
    fun ordinarySpendingIsNotOpenedAsAHalf() {
        assertFalse(EntryFormLogic.fromTransaction(halfOut.copy(purpose = TxPurpose.EXPENSE))!!.ownMove)
    }

    /** In the spreadsheet a half is a move, like a transfer, so reading the export back in leaves it out. */
    @Test
    fun theExportCallsAHalfAMove() {
        fun row(purpose: TxPurpose, from: String?, to: String?) = ExportRow(1, nov3, purpose, 500_000, null, "Other", from, to, null, null, null, null)
        assertEquals("move", CsvExport.direction(row(TxPurpose.OWN_MOVE_OUT, "GCash", null)))
        assertEquals("move", CsvExport.direction(row(TxPurpose.OWN_MOVE_IN, null, "MariBank")))
        assertEquals("out", CsvExport.direction(row(TxPurpose.EXPENSE, "GCash", null)))
        assertEquals("in", CsvExport.direction(row(TxPurpose.INCOME, null, "MariBank")))
        assertEquals("move", CsvExport.direction(row(TxPurpose.TRANSFER, "GCash", "MariBank")))
    }
}
