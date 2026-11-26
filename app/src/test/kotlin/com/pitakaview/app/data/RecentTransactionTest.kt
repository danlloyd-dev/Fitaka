package com.pitakaview.app.data

import com.pitakaview.app.data.db.dao.RecentTransaction
import com.pitakaview.app.data.db.dao.isMove
import com.pitakaview.app.data.db.dao.signedCentavos
import com.pitakaview.app.domain.TxPurpose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Regression: a transfer between your own wallets was shown as "+₱3,000.00", like income. */
class RecentTransactionTest {
    private fun row(purpose: TxPurpose, from: String?, to: String?) =
        RecentTransaction(1, purpose, 300_000, LocalDate.of(2026, 11, 24), null, null, from, to)

    @Test
    fun transfersAreMovesNotIncome() {
        val transfer = row(TxPurpose.TRANSFER, "MariBank", "GCash")
        assertTrue(transfer.isMove)
        assertEquals(300_000L, transfer.signedCentavos)
    }

    @Test
    fun moneyInIsPositiveAndMoneyOutIsNegative() {
        assertFalse(row(TxPurpose.INCOME, null, "MariBank").isMove)
        assertEquals(300_000L, row(TxPurpose.INCOME, null, "MariBank").signedCentavos)
        assertFalse(row(TxPurpose.EXPENSE, "GCash", null).isMove)
        assertEquals(-300_000L, row(TxPurpose.EXPENSE, "GCash", null).signedCentavos)
    }
}
