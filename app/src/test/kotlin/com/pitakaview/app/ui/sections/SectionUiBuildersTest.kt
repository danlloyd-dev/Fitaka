package com.pitakaview.app.ui.sections

import com.pitakaview.app.data.db.dao.BaonActivityRow
import com.pitakaview.app.data.db.dao.GoalProgress
import com.pitakaview.app.data.db.dao.LoanWithPaid
import com.pitakaview.app.data.db.dao.RecentTransaction
import com.pitakaview.app.data.db.entity.BaonEntity
import com.pitakaview.app.data.db.entity.LoanEntity
import com.pitakaview.app.domain.BaonDirection
import com.pitakaview.app.domain.BaonPeriod
import com.pitakaview.app.domain.LoanDirection
import com.pitakaview.app.domain.TxPurpose
import com.pitakaview.app.ui.baon.BaonUiBuilder
import com.pitakaview.app.ui.baon.restartText
import com.pitakaview.app.ui.ipon.IponUiBuilder
import com.pitakaview.app.ui.utang.UtangUiBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** What the Utang, Ipon and Baon tabs show is worked out by pure functions; these pin the working out. */
class SectionUiBuildersTest {
    private val today = LocalDate.of(2026, 11, 25) // a Wednesday
    private val t0 = Instant.EPOCH

    // ---------- Utang ----------

    private fun loan(
        id: Long, direction: LoanDirection, who: String, total: Long, paid: Long,
        due: LocalDate? = null, installment: Long? = null, closed: Boolean = false,
    ) = LoanWithPaid(
        LoanEntity(
            id = id, direction = direction, counterparty = who, principalCentavos = total, totalPayableCentavos = total,
            startDate = LocalDate.of(2026, 10, 1), dueDate = due, installmentCentavos = installment,
            dueDayOfMonth = if (installment != null) due?.dayOfMonth else null,
            closedAt = if (closed) t0 else null, createdAt = t0,
        ),
        paidCentavos = paid,
    )

    @Test
    fun loansAreSplitByDirectionSoonestDueFirstWithSettledOnesApart() {
        val ui = UtangUiBuilder.build(
            listOf(
                loan(1, LoanDirection.I_OWE, "No date", 100_000, 0),
                loan(2, LoanDirection.I_OWE, "Due later", 200_000, 50_000, due = LocalDate.of(2026, 12, 20)),
                loan(3, LoanDirection.I_OWE, "Due soon", 300_000, 0, due = LocalDate.of(2026, 12, 1)),
                loan(4, LoanDirection.OWED_TO_ME, "Kuya Jun", 300_000, 100_000, due = LocalDate.of(2026, 11, 20)),
                loan(5, LoanDirection.I_OWE, "Paid off", 500_000, 500_000),
                loan(6, LoanDirection.OWED_TO_ME, "Forgiven", 400_000, 100_000, closed = true),
            ),
            today,
        )
        assertTrue(ui.loaded)
        assertEquals(listOf("Due soon", "Due later", "No date"), ui.iOwe.map { it.who })
        assertEquals(listOf("Kuya Jun"), ui.owedToMe.map { it.who })
        assertEquals("newest settled first", listOf("Forgiven", "Paid off"), ui.settled.map { it.who })
        assertEquals("only open loans count", 100_000L + 150_000L + 300_000L, ui.iOweCentavos)
        assertEquals("a forgiven loan is no longer owed", 200_000L, ui.owedToMeCentavos)
        assertEquals(150_000L to 0.25f, ui.iOwe[1].leftCentavos to ui.iOwe[1].fraction)
    }

    @Test
    fun aLoansStatusSaysWhatIsDueAndWhen() {
        fun status(row: LoanWithPaid) = UtangUiBuilder.status(row.loan, row.paidCentavos, today)
        assertEquals("No due date", status(loan(1, LoanDirection.I_OWE, "a", 100_000, 0)))
        assertEquals("Due Dec 1", status(loan(1, LoanDirection.I_OWE, "a", 100_000, 0, due = LocalDate.of(2026, 12, 1))))
        assertEquals("Due tomorrow", status(loan(1, LoanDirection.I_OWE, "a", 100_000, 0, due = today.plusDays(1))))
        assertEquals("Overdue since Nov 20", status(loan(1, LoanDirection.I_OWE, "a", 100_000, 0, due = LocalDate.of(2026, 11, 20))))
        val monthly = loan(1, LoanDirection.I_OWE, "a", 1_200_000, 200_000, due = LocalDate.of(2026, 11, 5), installment = 200_000)
        assertEquals("₱2,000.00 due Dec 5", status(monthly))
        assertEquals("₱2,000.00 overdue since Nov 5", status(monthly.copy(paidCentavos = 0)))
        assertEquals("Paid in full", status(loan(1, LoanDirection.I_OWE, "a", 100_000, 100_000)))
        assertEquals("Marked settled", status(loan(1, LoanDirection.I_OWE, "a", 100_000, 40_000, closed = true)))
    }

    @Test
    fun aLoansOwnScreenReadsFromTheSameNumbers() {
        val row = loan(1, LoanDirection.OWED_TO_ME, "Kuya Jun", 300_000, 100_000, due = LocalDate.of(2026, 12, 1))
        val entries = listOf(RecentTransaction(9, TxPurpose.LOAN_PAYMENT, 100_000, today, null, null, null, "Cash"))
        val detail = UtangUiBuilder.detail(row, entries, today)
        assertEquals("Kuya Jun" to "Owes you", detail.title to detail.subtitle)
        assertEquals("Left to collect" to 200_000L, detail.amountCaption to detail.amountCentavos)
        assertEquals(listOf("Collected ₱1,000.00 of ₱3,000.00", "Due Dec 1"), detail.lines)
        assertEquals(1, detail.days.single().rows.size)
        assertEquals("Paid ₱0.00 of ₱1,000.00", UtangUiBuilder.detail(loan(2, LoanDirection.I_OWE, "a", 100_000, 0), emptyList(), today).lines.first())
    }

    // ---------- Ipon ----------

    @Test
    fun goalsShowProgressAndHiddenOnesStayOutOfTheTotal() {
        val ui = IponUiBuilder.build(
            listOf(
                GoalProgress(1, "Emergency fund", 5_000_000, null, null, 1_250_000),
                GoalProgress(2, "Laptop", 4_500_000, LocalDate.of(2027, 6, 30), 3, 0),
                GoalProgress(3, "Travel", null, null, null, 300_000),
                GoalProgress(4, "Old", 100_000, null, null, 70_000, isArchived = true),
            ),
            today,
        )
        assertEquals(1_550_000L, ui.savedCentavos)
        assertEquals(listOf("Emergency fund", "Laptop", "Travel"), ui.goals.map { it.name })
        assertEquals(listOf("Old"), ui.hidden.map { it.name })
        assertEquals(listOf("of ₱50,000.00 · 25%"), ui.goals[0].lines)
        assertEquals(0.25f, ui.goals[0].fraction)
        assertEquals("nothing saved yet: no percent to show", listOf("of ₱45,000.00", "by Jun 30, 2027"), ui.goals[1].lines)
        assertEquals(listOf("No target"), ui.goals[2].lines)
        assertNull("no target, so no bar", ui.goals[2].fraction)
    }

    @Test
    fun aGoalsOwnScreenSaysHowFarThereIsToGo() {
        val going = IponUiBuilder.detail(GoalProgress(2, "Laptop", 4_500_000, LocalDate.of(2027, 6, 30), 3, 500_000), "MariBank", emptyList(), today)
        assertEquals("Kept in MariBank", going.subtitle)
        assertEquals(listOf("₱40,000.00 to go of ₱45,000.00", "Target date: Jun 30, 2027"), going.lines)

        val reached = IponUiBuilder.detail(GoalProgress(2, "Laptop", 4_500_000, null, null, 4_600_000), null, emptyList(), today)
        assertEquals("Set aside in your wallets", reached.subtitle)
        assertEquals(listOf("Target of ₱45,000.00 reached"), reached.lines)
        assertEquals("the bar is full, not past full", 1f, reached.fraction)

        val open = IponUiBuilder.detail(GoalProgress(3, "Travel", null, null, null, 300_000, isArchived = true), null, emptyList(), today)
        assertEquals(listOf("No target: save as much as you like", "Hidden from Home and from new entries"), open.lines)
    }

    // ---------- Baon ----------

    private val monday = LocalDate.of(2026, 11, 23)
    private val mine = BaonEntity(id = 1, name = "My baon", direction = BaonDirection.RECEIVE, amountCentavos = 50_000, period = BaonPeriod.WEEKLY, startDate = monday, createdAt = t0)
    private val bunso = BaonEntity(id = 2, name = "Bunso", direction = BaonDirection.GIVE, amountCentavos = 20_000, period = BaonPeriod.DAILY, startDate = monday, createdAt = t0)
    private val activity = listOf(
        BaonActivityRow(1, TxPurpose.BAON_RECEIVED, monday, 50_000),
        BaonActivityRow(1, TxPurpose.EXPENSE, today, 12_000),
        BaonActivityRow(1, TxPurpose.EXPENSE, monday.minusDays(1), 99_900), // last week: not counted
        BaonActivityRow(2, TxPurpose.BAON_GIVEN, today, 10_000),
        BaonActivityRow(2, TxPurpose.BAON_GIVEN, today.minusDays(1), 20_000), // yesterday: not counted for a daily baon
    )

    @Test
    fun baonLeftIsReceivedMinusSpentThisPeriod() {
        val ui = BaonUiBuilder.build(listOf(mine, bunso, mine.copy(id = 3, name = "Old baon", isActive = false)), activity, today)
        assertEquals(38_000L, ui.leftCentavos)

        val received = ui.receiving.single()
        assertEquals("My baon" to 38_000L, received.name to received.amountCentavos)
        assertEquals("left", received.amountCaption)
        assertEquals(listOf("Received ₱500.00, spent ₱120.00", "Nov 23 – Nov 29"), received.lines)
        assertEquals(0.24f, received.fraction)

        val given = ui.giving.single()
        assertEquals("Bunso" to 10_000L, given.name to given.amountCentavos)
        assertEquals(listOf("of ₱200.00 planned", "Today"), given.lines)
        assertEquals(0.5f, given.fraction)

        assertEquals(listOf("Old baon"), ui.stopped.map { it.name })
        assertNull("no baon received: nothing to show as left", BaonUiBuilder.build(listOf(bunso), activity, today).leftCentavos)
    }

    @Test
    fun aBaonsOwnScreenUsesTheEntriesItLists() {
        val entries = listOf(
            RecentTransaction(5, TxPurpose.EXPENSE, 12_000, today, "lugaw", "Food", "Cash", null),
            RecentTransaction(4, TxPurpose.BAON_RECEIVED, 50_000, monday, null, null, null, "Cash"),
        )
        val detail = BaonUiBuilder.detail(mine, entries, today)
        assertEquals("My baon" to "You receive ₱500.00 every week", detail.title to detail.subtitle)
        assertEquals("Left this period" to 38_000L, detail.amountCaption to detail.amountCentavos)
        assertEquals(2, detail.days.size)
        assertEquals("You give ₱200.00 every day", BaonUiBuilder.rhythm(bunso))
        assertTrue("Stopped" in BaonUiBuilder.detail(mine.copy(isActive = false), entries, today).lines)
    }

    @Test
    fun theFormSaysWhenABaonStartsAgain() {
        assertEquals("It starts again every Monday.", restartText(BaonPeriod.WEEKLY, monday))
        assertEquals("It starts again on day 23 of each month.", restartText(BaonPeriod.MONTHLY, monday))
        assertEquals("It starts again every day.", restartText(BaonPeriod.DAILY, monday))
    }

    // ---------- the shared entry form's words ----------

    @Test
    fun theEntryFormIsTitledForWhatItDoes() {
        assertEquals("Add a payment", MoveText.title(TxPurpose.LOAN_PAYMENT, LoanDirection.I_OWE, isEdit = false))
        assertEquals("Payment received", MoveText.title(TxPurpose.LOAN_PAYMENT, LoanDirection.OWED_TO_ME, isEdit = false))
        assertEquals("Edit payment", MoveText.title(TxPurpose.LOAN_PAYMENT, LoanDirection.OWED_TO_ME, isEdit = true))
        assertEquals("Take out of savings", MoveText.title(TxPurpose.SAVINGS_WITHDRAWAL, null, isEdit = false))
        assertEquals("Paid from", MoveText.walletLabel(TxPurpose.LOAN_PAYMENT, LoanDirection.I_OWE))
        assertEquals("Received in", MoveText.walletLabel(TxPurpose.LOAN_PAYMENT, LoanDirection.OWED_TO_ME))
        assertEquals("Received in", MoveText.walletLabel(TxPurpose.LOAN_START, LoanDirection.I_OWE))
        assertEquals("Taken from", MoveText.walletLabel(TxPurpose.SAVINGS_DEPOSIT, null))
        assertEquals("Put into", MoveText.walletLabel(TxPurpose.SAVINGS_WITHDRAWAL, null))
        assertEquals("Paid from", MoveText.walletLabel(TxPurpose.BAON_GIVEN, null))
    }
}
