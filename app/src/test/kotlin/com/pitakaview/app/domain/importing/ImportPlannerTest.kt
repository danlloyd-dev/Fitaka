package com.pitakaview.app.domain.importing

import com.pitakaview.app.data.db.entity.CategoryEntity
import com.pitakaview.app.domain.CategoryKind
import com.pitakaview.app.domain.ImportSource
import com.pitakaview.app.domain.LedgerRules
import com.pitakaview.app.domain.TxPurpose
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What an imported file adds to the ledger, and what it leaves out as already there. */
class ImportPlannerTest {
    private val nov3 = LocalDate.of(2026, 11, 3)
    private val wallet = 7L

    private val expense = listOf("Food", "Transport", "Bills", "Load & Internet", "Groceries", "Rent & Home", "Health", "Fun", "Other")
        .mapIndexed { i, name -> CategoryEntity(id = 10L + i, name = name, kind = CategoryKind.EXPENSE, iconKey = "x") }
    private val income = listOf("Salary", "Sideline", "Gift", "Other")
        .mapIndexed { i, name -> CategoryEntity(id = 30L + i, name = name, kind = CategoryKind.INCOME, iconKey = "x") }

    private fun id(name: String, from: List<CategoryEntity> = expense) = from.first { it.name == name }.id

    private fun row(description: String = "Lunch", centavos: Long = 25_000, moneyIn: Boolean = false, reference: String? = null, date: LocalDate = nov3) =
        StatementRow(date, description, reference, centavos, moneyIn)

    private fun plan(rows: List<StatementRow>, importedRefs: Set<String> = emptySet(), existing: List<LedgerMark> = emptyList()) =
        ImportPlanner.plan(rows, ImportSource.CSV, wallet, importedRefs, existing, expense, income)

    @Test
    fun newLinesAreTickedAndGetACategoryGuess() {
        val plan = plan(listOf(row("Payment to Jollibee"), row("Payroll November", 1_500_000, moneyIn = true)))
        assertEquals(listOf(ImportStatus.NEW, ImportStatus.NEW), plan.map { it.status })
        assertTrue(plan.all { it.picked })
        assertEquals(id("Food"), plan[0].categoryId)
        assertEquals(id("Salary", income), plan[1].categoryId)
        assertNull("no reference in the file, so nothing to recognise it by later", plan[0].externalRef)
    }

    @Test
    fun aLineImportedBeforeIsRecognisedByItsReferenceAndCannotBeBroughtInAgain() {
        val line = row(reference = "1000000000001")
        val key = ImportPlanner.refFor(ImportSource.CSV, wallet, line)!!
        assertEquals("CSV:7:1000000000001:out:25000", key)

        val again = plan(listOf(line, row("Fare", 2_500, reference = "1000000000002")), importedRefs = setOf(key))
        assertEquals(listOf(ImportStatus.ALREADY_IMPORTED, ImportStatus.NEW), again.map { it.status })
        assertFalse(again[0].picked)
        assertFalse(again[0].canPick)
        // The same reference in another wallet is a different line.
        assertEquals("CSV:8:1000000000001:out:25000", ImportPlanner.refFor(ImportSource.CSV, 8, line))
        assertNull(ImportPlanner.refFor(ImportSource.CSV, wallet, row(reference = "  ")))
    }

    @Test
    fun aPaymentAndItsFeeCanShareAReferenceButTheSameLineTwiceCannot() {
        val plan = plan(
            listOf(
                row("Bank transfer", 100_000, reference = "R1"),
                row("Transfer fee", 1_500, reference = "R1"),
                row("Bank transfer", 100_000, reference = "R1"),
            ),
        )
        assertEquals(listOf(ImportStatus.NEW, ImportStatus.NEW, ImportStatus.TWICE_IN_FILE), plan.map { it.status })
        assertFalse(plan[2].canPick)
        assertEquals("the two kept lines have different keys", 2, plan.take(2).map { it.externalRef }.toSet().size)
    }

    @Test
    fun aLineThatMatchesAnEntryAlreadyThereIsUntickedButMayBeTicked() {
        // Lunch was typed in by hand on the day; the statement lists it too, and a second ₱250 the same day.
        val plan = plan(
            listOf(row("Lunch"), row("Dinner"), row("Fare", 2_500)),
            existing = listOf(LedgerMark(nov3, 25_000, moneyIn = false)),
        )
        assertEquals(listOf(ImportStatus.LOOKS_LIKE_EXISTING, ImportStatus.NEW, ImportStatus.NEW), plan.map { it.status })
        assertFalse(plan[0].picked)
        assertTrue("the owner decides", plan[0].canPick)
    }

    @Test
    fun matchingByDayAmountAndDirectionIsExact() {
        val existing = listOf(LedgerMark(nov3, 25_000, moneyIn = false))
        assertEquals(ImportStatus.NEW, plan(listOf(row(date = nov3.plusDays(1))), existing = existing).single().status)
        assertEquals(ImportStatus.NEW, plan(listOf(row(centavos = 25_001)), existing = existing).single().status)
        assertEquals(ImportStatus.NEW, plan(listOf(row(moneyIn = true)), existing = existing).single().status)
        assertEquals(ImportStatus.LOOKS_LIKE_EXISTING, plan(listOf(row(reference = "R9")), existing = existing).single().status)
    }

    @Test
    fun categoryHintsMatchWholeWordsAndFallBackToOther() {
        fun guess(text: String) = ImportPlanner.guessCategory(text, expense)
        assertEquals(id("Food"), guess("GRABFOOD order 1234"))
        assertEquals("grabfood is food, grab is a ride", id("Transport"), guess("Grab ride to Makati"))
        assertEquals(id("Bills"), guess("MERALCO bills payment"))
        assertEquals(id("Load & Internet"), guess("Buy Load 50"))
        assertEquals(id("Groceries"), guess("Puregold Cubao"))
        assertEquals(id("Health"), guess("Mercury Drug - Katipunan"))
        assertEquals(id("Food"), guess("McDonald's Taft"))
        // Not fooled by a word inside another word.
        assertEquals(id("Other"), guess("Download fee for current account"))
        assertEquals(id("Other"), guess("Parent transfer"))
        assertEquals(id("Other"), guess(""))
        // An owner who renamed or removed categories still gets one of their own.
        val renamed = listOf(CategoryEntity(id = 99, name = "Pagkain", kind = CategoryKind.EXPENSE, iconKey = "x"))
        assertEquals(99L, ImportPlanner.guessCategory("Jollibee", renamed))
    }

    @Test
    fun onlyTickedLinesBecomeLedgerRowsAndEachRowIsValid() {
        val plan = plan(
            listOf(row("Payment to Jollibee", reference = "R1"), row("Payroll", 1_500_000, moneyIn = true), row("Skipped", 100)),
        ).mapIndexed { i, candidate -> if (i == 2) candidate.copy(picked = false) else candidate }

        val now = Instant.parse("2026-11-26T04:00:00Z")
        val rows = ImportPlanner.toTransactions(plan, wallet, batchId = 5, now = now)
        assertEquals(2, rows.size)
        val (spent, earned) = rows
        assertEquals(TxPurpose.EXPENSE, spent.purpose)
        assertEquals(wallet, spent.fromWalletId)
        assertNull(spent.toWalletId)
        assertEquals("Payment to Jollibee", spent.note)
        assertEquals("CSV:7:R1:out:25000", spent.externalRef)
        assertEquals(5L, spent.importBatchId)
        assertEquals(TxPurpose.INCOME, earned.purpose)
        assertEquals(wallet, earned.toWalletId)
        assertNull(earned.fromWalletId)
        rows.forEach { assertEquals("the ledger's own rules accept it", emptySet<Any>(), LedgerRules.validate(it, null)) }

        // A line that cannot be picked is never written, even if something ticked it.
        val forced = plan(listOf(row(reference = "R1")), importedRefs = setOf("CSV:7:R1:out:25000")).map { it.copy(picked = true) }
        assertEquals(0, ImportPlanner.toTransactions(forced, wallet, 5, now).size)
    }

    // ---------- a line brought in before, the other way round (review before 1.0, 2026-11-27) ----------

    private fun settled(rows: List<StatementRow>, importedRefs: Set<String> = emptySet(), existing: List<LedgerMark> = emptyList(), open: Boolean = true) =
        ImportPlanner.settledByLedger(rows, { open }, ImportSource.CSV, wallet, importedRefs, existing)

    @Test
    fun aLineBroughtInTheOtherWayRoundIsRecognisedByItsReference() {
        val file = listOf(row("Sideline pay", 50_000, moneyIn = false, reference = "R1"))
        val broughtInAsMoneyIn = setOf(ImportPlanner.refFor(ImportSource.CSV, wallet, file[0].copy(moneyIn = true))!!)

        val rows = settled(file, importedRefs = broughtInAsMoneyIn)
        assertTrue("it takes the direction the ledger has", rows.single().moneyIn)
        assertTrue(rows.single().sure)
        assertEquals(ImportStatus.ALREADY_IMPORTED, plan(rows, importedRefs = broughtInAsMoneyIn).single().status)
        assertEquals("without this it was offered again, as new", ImportStatus.NEW, plan(file, importedRefs = broughtInAsMoneyIn).single().status)
    }

    @Test
    fun withoutAReferenceItIsRecognisedByTheEntryOfThatDayAndAmount() {
        val file = listOf(row("Sideline pay", 50_000, moneyIn = false))
        val existing = listOf(LedgerMark(nov3, 50_000, moneyIn = true))

        val rows = settled(file, existing = existing)
        assertTrue(rows.single().moneyIn)
        assertEquals(ImportStatus.LOOKS_LIKE_EXISTING, plan(rows, existing = existing).single().status)
        assertFalse("and it is not ticked", plan(rows, existing = existing).single().picked)
    }

    @Test
    fun anEntryGoingTheSameWayComesFirstAndEachEntryIsUsedForOneLine() {
        // Two 500.00 lines read as money out; the ledger has one 500.00 out and one 500.00 in on that day.
        val file = listOf(row("First", 50_000), row("Second", 50_000), row("Third", 50_000))
        val existing = listOf(LedgerMark(nov3, 50_000, moneyIn = false), LedgerMark(nov3, 50_000, moneyIn = true))
        assertEquals("the third has no entry left and stays as it was read", listOf(false, true, false), settled(file, existing = existing).map { it.moneyIn })
    }

    @Test
    fun aLineTheFileSettlesIsNeverTurnedRound() {
        val file = listOf(row("Sideline pay", 50_000, moneyIn = false, reference = "R1"), row("Lunch", 25_000))
        val otherWay = setOf(ImportPlanner.refFor(ImportSource.CSV, wallet, file[0].copy(moneyIn = true))!!)
        val existing = listOf(LedgerMark(nov3, 25_000, moneyIn = true))
        assertEquals(file, settled(file, importedRefs = otherWay, existing = existing, open = false))
    }

    // ---------- the app's own export names a wallet for each line ----------

    @Test
    fun aLineNamedForAnotherWalletIsNotBroughtIn() {
        val rows = listOf(row("Lunch").copy(walletName = "Cash"), row("Grab", 18_000).copy(walletName = "GCash"), row("From a bank file", 9_900))
        val plan = ImportPlanner.plan(rows, ImportSource.CSV, wallet, emptySet(), emptyList(), expense, income, walletName = " cash ")
        assertEquals(listOf(ImportStatus.NEW, ImportStatus.OTHER_WALLET, ImportStatus.NEW), plan.map { it.status })
        assertEquals(listOf(true, false, true), plan.map { it.picked })
        assertFalse("and it cannot be ticked", plan[1].canPick)
        assertEquals("only the two are turned into entries", 2, ImportPlanner.toTransactions(plan, wallet, 5, Instant.parse("2026-11-25T08:00:00Z")).size)
    }

    @Test
    fun aLineForAnotherWalletUsesUpNoExistingEntry() {
        // The GCash line and the Cash line are the same day and amount; Cash has one such entry already.
        val rows = listOf(row("Lunch").copy(walletName = "GCash"), row("Lunch").copy(walletName = "Cash"))
        val plan = ImportPlanner.plan(rows, ImportSource.CSV, wallet, emptySet(), listOf(LedgerMark(nov3, 25_000, moneyIn = false)), expense, income, walletName = "Cash")
        assertEquals(listOf(ImportStatus.OTHER_WALLET, ImportStatus.LOOKS_LIKE_EXISTING), plan.map { it.status })
    }

    // ---------- the other half of a move between the owner's own wallets (owner's choice, 2026-11-27) ----------

    private val at = Instant.parse("2026-11-25T08:00:00Z")

    /** 5,000.00 of plain spending in GCash on the 3rd, unless said otherwise. */
    private fun inGcash(id: Long, centavos: Long = 500_000, moneyIn: Boolean = false, date: LocalDate = nov3) = OwnMoveCandidate(id, "GCash", date, centavos, moneyIn)

    private fun planWith(rows: List<StatementRow>, ownMoves: List<OwnMoveCandidate>, existing: List<LedgerMark> = emptyList()) =
        ImportPlanner.plan(rows, ImportSource.CSV, wallet, emptySet(), existing, expense, income, ownMoves = ownMoves)

    @Test
    fun moneyInThatLeftAnotherWalletIsBroughtInAsTheOtherHalfOfAMove() {
        val plan = planWith(listOf(row("Via Other Banks Channel", 500_000, moneyIn = true), row("Lunch")), listOf(inGcash(41)))
        assertEquals(41L, plan[0].ownMove?.entryId)
        assertTrue(plan[0].asOwnMove)
        assertTrue("it is still a new line, ticked", plan[0].picked && plan[0].status == ImportStatus.NEW)
        assertNull("the lunch is nobody's other half", plan[1].ownMove)
        assertFalse(plan[1].asOwnMove)

        val entries = ImportPlanner.toTransactions(plan, wallet, 5, at)
        assertEquals(listOf(TxPurpose.OWN_MOVE_IN, TxPurpose.EXPENSE), entries.map { it.purpose })
        assertEquals("it arrives in this wallet, like income", wallet, entries[0].toWalletId)
        assertNull(entries[0].fromWalletId)
        assertTrue("both are rows the ledger accepts", entries.all { LedgerRules.validate(it, null).isEmpty() })
        assertEquals("and the GCash entry is the one to pair it with", listOf(41L), ImportPlanner.pairedEntryIds(plan))
    }

    @Test
    fun moneyOutThatArrivedInAnotherWalletIsTheOtherHalfToo() {
        val plan = planWith(listOf(row("Transfer to GCash", 500_000, moneyIn = false)), listOf(inGcash(42, moneyIn = true)))
        assertEquals(42L, plan.single().ownMove?.entryId)
        assertEquals(TxPurpose.OWN_MOVE_OUT, ImportPlanner.toTransactions(plan, wallet, 5, at).single().purpose)
    }

    @Test
    fun itMustBeTheSameAmountGoingTheOtherWayADayApartAtMost() {
        val line = listOf(row("Via Other Banks Channel", 500_000, moneyIn = true))
        assertNull("money in here and money in there is not one move", planWith(line, listOf(inGcash(1, moneyIn = true))).single().ownMove)
        assertNull("another amount", planWith(line, listOf(inGcash(1, centavos = 500_001))).single().ownMove)
        assertNull("two days apart", planWith(line, listOf(inGcash(1, date = nov3.minusDays(2)))).single().ownMove)
        assertEquals("the day before", 1L, planWith(line, listOf(inGcash(1, date = nov3.minusDays(1)))).single().ownMove?.entryId)
        assertEquals("the day after", 1L, planWith(line, listOf(inGcash(1, date = nov3.plusDays(1)))).single().ownMove?.entryId)
    }

    @Test
    fun theNearestDayWinsAndOneEntryIsOneLinesOtherHalf() {
        val lines = listOf(row("First", 500_000, moneyIn = true, date = nov3), row("Second", 500_000, moneyIn = true, date = nov3.plusDays(1)))
        val two = planWith(lines, listOf(inGcash(41, date = nov3.plusDays(1)), inGcash(42, date = nov3)))
        assertEquals("each line takes the entry of its own day", listOf(42L, 41L), two.map { it.ownMove?.entryId })

        val one = planWith(lines, listOf(inGcash(41, date = nov3)))
        assertEquals("one entry cannot be the other half of two lines", listOf(41L, null), one.map { it.ownMove?.entryId })
        assertEquals(listOf(TxPurpose.OWN_MOVE_IN, TxPurpose.INCOME), ImportPlanner.toTransactions(one, wallet, 5, at).map { it.purpose })
    }

    @Test
    fun aLineThatIsNotNewIsNotOfferedAsOne() {
        val lines = listOf(row("Via Other Banks Channel", 500_000, moneyIn = true), row("Again", 500_000, moneyIn = true))
        // This wallet already has an entry for the first line's day and amount.
        val plan = planWith(lines, listOf(inGcash(41)), existing = listOf(LedgerMark(nov3, 500_000, moneyIn = true)))
        assertEquals(listOf(ImportStatus.LOOKS_LIKE_EXISTING, ImportStatus.NEW), plan.map { it.status })
        assertEquals("the entry is left for the line that is new", listOf(null, 41L), plan.map { it.ownMove?.entryId })
    }

    @Test
    fun whenTheOwnerSaysItIsNotTheirTransferOrUnticksItNothingIsPaired() {
        val plan = planWith(listOf(row("Via Other Banks Channel", 500_000, moneyIn = true)), listOf(inGcash(41)))

        val saidNo = plan.map { it.copy(asOwnMove = false) }
        assertEquals("brought in as income, as before", TxPurpose.INCOME, ImportPlanner.toTransactions(saidNo, wallet, 5, at).single().purpose)
        assertEquals(emptyList<Long>(), ImportPlanner.pairedEntryIds(saidNo))

        val unticked = plan.map { it.copy(picked = false) }
        assertEquals(0, ImportPlanner.toTransactions(unticked, wallet, 5, at).size)
        assertEquals("a line that is not brought in changes no other entry", emptyList<Long>(), ImportPlanner.pairedEntryIds(unticked))
    }
}
