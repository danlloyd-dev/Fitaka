package com.pitakaview.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.pitakaview.app.data.db.entity.TransactionEntity
import com.pitakaview.app.domain.CategoryKind
import com.pitakaview.app.domain.TxPurpose
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

/**
 * The same app on a phone-sized screen (the owner's phone is about 390 x 850 dp), where lists must
 * scroll. These tests are about reachability: can every step actually be seen and tapped?
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h850dp-xhdpi")
class PhoneSizeFlowTest : FlowTestBase() {

    private fun scrollTo(text: String) = rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text))

    /**
     * Regression (found on the owner's phone, 2026-11-26), two causes:
     * 1. the delete confirmation opened inside the list, below the Save bar, where it could not be reached;
     * 2. the Save bar floated over the list, so a button scrolled into view could sit hidden underneath it
     *    and a tap on it landed on Save instead.
     */
    @Test
    fun deletingAnEntryWorksEndToEnd() {
        start()
        runBlocking {
            val cash = wallets().single().wallet.id
            val food = container.database.categoryDao().observeActive(CategoryKind.EXPENSE).first().first { it.name == "Food" }.id
            container.ledger.add(
                TransactionEntity(
                    purpose = TxPurpose.EXPENSE, amountCentavos = 25_000, occurredOn = container.dayClock.today.value,
                    fromWalletId = cash, categoryId = food, createdAt = Instant.now(),
                ),
            )
        }
        waitFor("−₱250.00")
        scrollTo("Food")
        tapButton("Food")
        waitFor("Edit entry")

        scrollTo("Delete entry")
        val delete = rule.onNode(hasText("Delete entry") and hasClickAction()).fetchSemanticsNode().boundsInRoot
        val save = rule.onNode(hasText("Save") and hasClickAction()).fetchSemanticsNode().boundsInRoot
        assertTrue("Delete entry ($delete) must not sit under the Save bar ($save)", delete.bottom <= save.top)

        tapButton("Delete entry")
        rule.onNode(hasText("Yes, delete") and hasClickAction()).assertIsDisplayed().performClick()
        rule.waitUntil(10_000) { entries().isEmpty() }
        assertEquals(0L, wallets().single().balanceCentavos)
    }

    @Test
    fun quickEntryNeedsNoScrolling() {
        start()
        tapButton("Add transaction")
        waitFor("Food")
        field(0).performTextInput("250")
        rule.onNode(hasText("Food") and isSelectable()).assertIsDisplayed().performClick()
        rule.onNode(hasText("Save") and hasClickAction()).assertIsDisplayed().performClick()
        rule.waitUntil(10_000) { entries().size == 1 }
        assertEquals(25_000L, entries().single().amountCentavos)
    }
}
