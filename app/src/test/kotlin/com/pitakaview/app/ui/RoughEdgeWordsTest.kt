package com.pitakaview.app.ui

import com.pitakaview.app.data.db.dao.CategoryTotal
import com.pitakaview.app.data.db.dao.MonthPurposeTotal
import com.pitakaview.app.data.db.dao.RecentTransaction
import com.pitakaview.app.data.db.entity.WalletEntity
import com.pitakaview.app.domain.ImportSource
import com.pitakaview.app.domain.SearchQuery
import com.pitakaview.app.domain.TxPurpose
import com.pitakaview.app.domain.WalletType
import com.pitakaview.app.domain.importing.StatementWallet
import com.pitakaview.app.domain.importing.WalletFit
import com.pitakaview.app.ui.importing.ImportText
import com.pitakaview.app.ui.ledger.MonthLedgerBuilder
import com.pitakaview.app.ui.ledger.homeSubtitle
import com.pitakaview.app.ui.summary.MonthBar
import com.pitakaview.app.ui.summary.MonthSummaryBuilder
import com.pitakaview.app.ui.summary.TAP_HINT
import com.pitakaview.app.ui.summary.readout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

/** The words and small rules behind the rough-edge fixes, checked without drawing a screen. */
class RoughEdgeWordsTest {
    private val today = LocalDate.of(2026, 11, 26)

    // ---- Home's rows ----

    @Test
    fun aRowOnHomeSaysTheNoteBetweenTheWalletAndTheDay() {
        val imported = RecentTransaction(1, TxPurpose.EXPENSE, 50_000, today.minusDays(40), "Shopee · Payment", "Other", "MariBank", null)
        assertEquals("MariBank · Shopee · Payment · Oct 17", imported.homeSubtitle(today))

        val plain = RecentTransaction(2, TxPurpose.EXPENSE, 12_500, today, null, "Food", "Cash", null)
        assertEquals("with no note there is nothing extra, and no stray dot", "Cash · Today", plain.homeSubtitle(today))

        val move = RecentTransaction(3, TxPurpose.TRANSFER, 300_000, today.minusDays(1), "for rent", null, "MariBank", "GCash")
        assertEquals("MariBank → GCash · for rent · Yesterday", move.homeSubtitle(today))
    }

    // ---- category icons reach the lists ----

    @Test
    fun aMonthsCategoriesCarryTheirIcons() {
        val rows = listOf(
            RecentTransaction(1, TxPurpose.EXPENSE, 10_000, today, null, "Food", "Cash", null, categoryIconKey = "food"),
            RecentTransaction(2, TxPurpose.EXPENSE, 5_000, today, null, "Food", "Cash", null, categoryIconKey = "food"),
            RecentTransaction(3, TxPurpose.EXPENSE, 2_000, today, null, null, "Cash", null),
        )
        val shares = MonthLedgerBuilder.build(YearMonth.of(2026, 11), true, rows).categories
        assertEquals(listOf("Food" to "food", MonthLedgerBuilder.NO_CATEGORY to null), shares.map { it.name to it.iconKey })

        val top = MonthSummaryBuilder.build(
            YearMonth.of(2026, 11), true,
            listOf(MonthPurposeTotal("2026-11", TxPurpose.EXPENSE, 17_000)),
            listOf(CategoryTotal(1, "Food", "food", 15_000), CategoryTotal(2, "Bills", "bills", 2_000)),
        ).topCategories
        assertEquals(listOf("food", "bills"), top.map { it.iconKey })
    }

    // ---- the chart's line of text ----

    @Test
    fun theLineUnderTheChartGivesTheTappedMonthInFull() {
        val bars = listOf(MonthBar(YearMonth.of(2026, 10), 3_000_000, 2_460_050), MonthBar(YearMonth.of(2026, 11), 3_500_000, 2_638_500))
        assertEquals(TAP_HINT, readout(bars, null))
        // In full, to the centavo: the axis beside the bars only says "₱30K".
        assertEquals("October 2026: ₱30,000.00 in, ₱24,600.50 out", readout(bars, 0))
        assertEquals("November 2026: ₱35,000.00 in, ₱26,385.00 out", readout(bars, 1))
        assertEquals("a position with no month says nothing wrong", TAP_HINT, readout(bars, 7))
    }

    // ---- which wallet a statement goes to ----

    private val at = Instant.parse("2026-11-26T04:00:00Z")
    private fun wallet(id: Long, name: String) = WalletEntity(id = id, name = name, type = WalletType.BANK, createdAt = at)

    @Test
    fun aStatementIsMatchedToTheWalletNamedAfterItsBank() {
        val cash = wallet(1, "Cash")
        val mari = wallet(2, "My maribank savings")
        assertEquals("the name may be written any way, with other words around it", mari, StatementWallet.only("MariBank", listOf(cash, mari)))
        assertNull("no wallet is named for the bank", StatementWallet.only("GCash", listOf(cash, mari)))
        assertNull("two are, and the app cannot tell which", StatementWallet.only("MariBank", listOf(mari, wallet(3, "MariBank 2"))))
        assertNull("a CSV names no bank", StatementWallet.bankName(ImportSource.CSV))

        assertEquals(WalletFit.NAMED_FOR_BANK, StatementWallet.fit("MariBank", mari, listOf(cash, mari)))
        assertEquals(WalletFit.ANOTHER_IS_NAMED_FOR_BANK, StatementWallet.fit("MariBank", cash, listOf(cash, mari)))
        assertEquals(WalletFit.NONE_NAMED_FOR_BANK, StatementWallet.fit("GCash", cash, listOf(cash, mari)))
    }

    @Test
    fun theReviewSaysWhereABanksStatementIsHeaded() {
        val cash = wallet(1, "Cash")
        val mari = wallet(2, "MariBank")
        assertNull("nothing to compare a CSV's wallet with", ImportText.destination(ImportSource.CSV, listOf(cash, mari), 1))

        val (right, fits) = ImportText.destination(ImportSource.MARIBANK, listOf(cash, mari), 2)!!
        assertEquals(WalletFit.NAMED_FOR_BANK, fits)
        assertEquals("This is a MariBank statement. Its entries will go into MariBank.", right)

        // The owner's own case on 2026-11-26: a MariBank statement with Cash picked.
        val (wrong, another) = ImportText.destination(ImportSource.MARIBANK, listOf(cash, mari), 1)!!
        assertEquals(WalletFit.ANOTHER_IS_NAMED_FOR_BANK, another)
        assertTrue(wrong.startsWith("This is a MariBank statement, but the wallet picked above is Cash."))

        val (noneYet, none) = ImportText.destination(ImportSource.MARIBANK, listOf(cash), 1)!!
        assertEquals(WalletFit.NONE_NAMED_FOR_BANK, none)
        assertTrue(noneYet.contains("its entries will go into Cash") && noneYet.contains("Settings > Wallets"))
    }

    // ---- search and letters such as ñ ----

    @Test
    fun aLetterTheDatabaseComparesExactlyIsLeftToTheApp() {
        assertFalse("plain English letters need nothing extra", SearchQuery.needsOwnMatching("Lunch at Jollibee 250"))
        assertTrue(SearchQuery.needsOwnMatching(" piña "))
        assertTrue(SearchQuery.needsOwnMatching("CAFÉ"))
        assertFalse("a sign with no capital form is compared as it is", SearchQuery.needsOwnMatching("₱250 · 50%"))

        assertEquals("the letter stands for any one character in what the database is asked", "%Pi_a%", SearchQuery.pattern("Piña"))
        assertEquals("%50\\% _%", SearchQuery.pattern("50% ñ"))
    }

    @Test
    fun matchingTreatsCapitalsAndSmallLettersAlikeIncludingEnye() {
        assertTrue(SearchQuery.matches("PIÑA", "Piña colada", null))
        assertTrue(SearchQuery.matches("piña", null, "LAS PIÑAS toll"))
        assertTrue(SearchQuery.matches(" Niño ", "sto. niño fiesta"))
        assertFalse("another letter in its place is not a match", SearchQuery.matches("piña", "pixa", "pina"))
        assertFalse(SearchQuery.matches("piña", null, null))
    }
}
