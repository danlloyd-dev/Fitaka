package com.pitakaview.app.ui.theme

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.pitakaview.app.domain.DarkModePref
import com.pitakaview.app.domain.FontChoice
import com.pitakaview.app.domain.ThemeId
import com.pitakaview.app.domain.UiSize
import com.pitakaview.app.domain.UiStyle
import com.pitakaview.app.ui.components.FitakaButton
import com.pitakaview.app.ui.components.SizePicker
import com.pitakaview.app.ui.components.StylePicker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What the theme hands down for the choices in Settings: the chosen font reaches every text style and
 * the buttons' labels (which have a style of their own), and only the Glassy style darkens secondary text.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h300dp-xhdpi")
class ThemeChoicesTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun theChosenFontReachesEveryTextStyleAndTheButtons() {
        var font by mutableStateOf(FontChoice.SHANTELL)
        var seen: Typography? = null
        rule.setContent {
            FitakaTheme(ThemeId.MANGO, DarkModePref.LIGHT, font = font) {
                seen = MaterialTheme.typography
                FitakaButton("Save", onClick = {})
            }
        }
        for (choice in FontChoice.entries) {
            font = choice
            rule.waitForIdle()
            val t = seen!!
            val styles = listOf(
                t.displayLarge, t.displayMedium, t.displaySmall, t.headlineLarge, t.headlineMedium, t.headlineSmall,
                t.titleLarge, t.titleMedium, t.titleSmall, t.bodyLarge, t.bodyMedium, t.bodySmall, t.labelLarge, t.labelMedium, t.labelSmall,
            )
            for (style in styles) assertEquals("a text style in ${choice.label}", choice.family(), style.fontFamily)

            val drawn = mutableListOf<TextLayoutResult>()
            rule.onNode(hasText("Save"), useUnmergedTree = true).fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)!!.action!!.invoke(drawn)
            assertEquals("the button's label in ${choice.label}", choice.family(), drawn.single().layoutInput.style.fontFamily)
        }
    }

    /**
     * The Size and Style strips hold three and four words across a phone. On a narrow phone with the
     * phone's own text size turned up, in the widest of the five fonts, each word is still whole and on one line.
     */
    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun theChoiceStripsKeepTheirWordsWholeAtLargeText() {
        var font by mutableStateOf(FontChoice.SHANTELL)
        rule.setContent {
            val phone = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(phone.density, fontScale = 1.3f)) {
                FitakaTheme(ThemeId.MANGO, DarkModePref.LIGHT, font = font) {
                    Column(Modifier.padding(16.dp)) {
                        SizePicker(UiSize.LARGE, {})
                        StylePicker(UiStyle.GLOSSY, {})
                    }
                }
            }
        }
        val words = UiSize.entries.map { it.label } + UiStyle.entries.map { it.label }
        for (choice in FontChoice.entries) {
            font = choice
            rule.waitForIdle()
            for (word in words) {
                val drawn = mutableListOf<TextLayoutResult>()
                rule.onNode(hasText(word), useUnmergedTree = true).fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)!!.action!!.invoke(drawn)
                val layout = drawn.single()
                assertEquals("\"$word\" in ${choice.label} at large text: lines", 1, layout.lineCount)
                assertTrue("\"$word\" in ${choice.label} at large text is cut short", !layout.isLineEllipsized(0))
            }
        }
    }

    @Test
    fun onlyTheGlassyStyleDarkensSecondaryText() {
        var style by mutableStateOf(UiStyle.GLOSSY)
        var seen: FitakaColors? = null
        rule.setContent { FitakaTheme(ThemeId.UBE, DarkModePref.LIGHT, style = style) { seen = FitakaTheme.colors } }
        val usual = fitakaColors(ThemeId.UBE, dark = false)
        assertNotEquals("the glass colours are not the usual ones", usual.inkMuted, usual.forGlass().inkMuted)
        for (chosen in UiStyle.entries) {
            style = chosen
            rule.waitForIdle()
            assertEquals(chosen.label, if (chosen == UiStyle.GLASSY) usual.forGlass() else usual, seen)
        }
    }
}
