package com.pitakaview.app.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.pitakaview.app.domain.DarkModePref
import com.pitakaview.app.domain.FontChoice
import com.pitakaview.app.domain.ThemeId
import com.pitakaview.app.domain.UiStyle
import com.pitakaview.app.ui.theme.PitakaTheme
import com.pitakaview.app.ui.theme.pitakaColors
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A question about something that cannot be undone is a dialog over the whole screen (the owner asked
 * for it on 2026-11-26; it was a strip in the footer before). Both answers work, the question is inside
 * the dialog and not on the screen behind, and while the app is busy neither answer can be tapped.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h800dp-xhdpi")
class ConfirmDialogTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val inDialog = hasAnyAncestor(isDialog())

    @Test
    fun theQuestionIsADialogAndBothAnswersWork() {
        var open by mutableStateOf(true)
        var busy by mutableStateOf(false)
        val said = mutableListOf<String>()
        rule.setContent {
            PitakaTheme(ThemeId.MANGO, DarkModePref.LIGHT) {
                Box(Modifier.fillMaxSize()) {
                    Text("The screen behind")
                    if (open) {
                        PitakaConfirmDialog(
                            title = "Delete this entry?",
                            lines = listOf("It is removed from your totals. This cannot be undone."),
                            confirmLabel = "Yes, delete",
                            dismissLabel = "Keep",
                            onConfirm = { said += "yes"; open = false },
                            onDismiss = { said += "no"; open = false },
                            enabled = !busy,
                        )
                    }
                }
            }
        }
        rule.onNode(hasText("Delete this entry?") and inDialog).assertIsDisplayed()
        rule.onNode(hasText("It is removed from your totals. This cannot be undone.") and inDialog).assertIsDisplayed()
        assertTrue("the screen behind is not part of the dialog", rule.onAllNodes(hasText("The screen behind") and inDialog).fetchSemanticsNodes().isEmpty())

        rule.onNode(hasText("Keep") and hasClickAction()).performClick()
        rule.waitForIdle()
        assertEquals(listOf("no"), said)
        assertTrue("the question goes away", rule.onAllNodes(hasText("Delete this entry?")).fetchSemanticsNodes().isEmpty())

        open = true
        rule.waitForIdle()
        rule.onNode(hasText("Yes, delete") and hasClickAction()).performClick()
        rule.waitForIdle()
        assertEquals(listOf("no", "yes"), said)

        busy = true
        open = true
        rule.waitForIdle()
        rule.onNode(hasText("Yes, delete") and hasClickAction()).assertIsNotEnabled()
        rule.onNode(hasText("Keep") and hasClickAction()).assertIsNotEnabled()
    }

    /**
     * The first version had the two answers side by side, and "Yes, delete" came out as "Yes, dele…" on a
     * phone-sized screen. Every pair of answers the app asks with, in every font, on a small phone.
     */
    @Test
    @Config(qualifiers = "w320dp-h640dp-xhdpi")
    fun theAnswersAreNeverCutShort() {
        val answers = listOf("Yes, delete" to "Keep", "Yes, remove" to "Keep", "Yes, restore" to "Cancel")
        var font by mutableStateOf(FontChoice.SHANTELL)
        rule.setContent {
            PitakaTheme(ThemeId.MANGO, DarkModePref.LIGHT, font = font) {
                Column(Modifier.padding(horizontal = 24.dp)) {
                    for ((yes, no) in answers) ConfirmCard("Replace everything on this phone?", listOf("This cannot be undone."), yes, no, onConfirm = {}, onDismiss = {})
                }
            }
        }
        for (choice in FontChoice.entries) {
            font = choice
            rule.waitForIdle()
            for (label in answers.flatMap { listOf(it.first, it.second) }.distinct()) {
                for (node in rule.onAllNodes(hasText(label), useUnmergedTree = true).fetchSemanticsNodes()) {
                    val drawn = mutableListOf<TextLayoutResult>()
                    node.config.getOrNull(SemanticsActions.GetTextLayoutResult)!!.action!!.invoke(drawn)
                    // Whether the one line was shortened to fit. (hasVisualOverflow is no use here: it is true
                    // for any text narrower than the room it was offered.)
                    val layout = drawn.single()
                    assertEquals("\"$label\" in ${choice.label} is one line", 1, layout.lineCount)
                    assertTrue("\"$label\" is cut short in ${choice.label}: drawn ${layout.size} in ${layout.layoutInput.constraints}", !layout.isLineEllipsized(0))
                }
            }
        }
    }

    /** In the Glassy style a card shows what is behind it; the question's card does not. */
    @Test
    fun theQuestionIsNeverSeeThrough() {
        val behind = Color(0xFF2D9CDB)
        var style by mutableStateOf(UiStyle.GLASSY)
        rule.setContent {
            PitakaTheme(ThemeId.MANGO, DarkModePref.LIGHT, style = style) {
                Box(Modifier.fillMaxSize().background(behind).padding(24.dp)) {
                    Box(Modifier.testTag("question")) { ConfirmCard("Delete this entry?", listOf("This cannot be undone."), "Yes, delete", "Keep", onConfirm = {}, onDismiss = {}) }
                }
            }
        }
        val card = pitakaColors(ThemeId.MANGO, dark = false).card
        for (chosen in UiStyle.entries) {
            style = chosen
            rule.waitForIdle()
            val pixels = rule.onRoot().captureToImage().toPixelMap()
            val box = rule.onNodeWithTag("question").fetchSemanticsNode().boundsInRoot
            // 8 in from the card's left and top edges: past the outline, before any words.
            val at = with(rule.density) { pixels[(box.left + 8.dp.toPx()).roundToInt(), (box.top + 8.dp.toPx()).roundToInt()] }
            assertEquals("${chosen.label}: the card's own colour, not the blue behind it (red)", card.red, at.red, 0.03f)
            assertEquals("${chosen.label}: the card's own colour, not the blue behind it (blue)", card.blue, at.blue, 0.03f)
        }
    }
}
