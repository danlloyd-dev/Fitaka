package com.pitakaview.app.ui.theme

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.pitakaview.app.domain.AppearanceSettings
import com.pitakaview.app.domain.DarkModePref
import com.pitakaview.app.domain.ThemeId
import com.pitakaview.app.domain.UiSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The size setting: everything inside the theme shrinks with it, and what steps out of it (the bottom
 * bar, through [FullSize]) stays the size the phone gives it. Large is the size the app always had.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h800dp-xhdpi")
class UiScaleTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun largeIsTheAppsOwnSizeAndTheDefault() {
        assertEquals(1f, UiSize.LARGE.scale)
        assertEquals(UiSize.LARGE, AppearanceSettings().size)
        assertTrue(UiSize.SMALL.scale < UiSize.MEDIUM.scale && UiSize.MEDIUM.scale < UiSize.LARGE.scale)
        // Small still leaves a 56-high button about 48 high: no smaller than a thumb needs.
        assertTrue(56 * UiSize.SMALL.scale >= 47f)
    }

    @Test
    fun everythingShrinksWithTheSizeExceptWhatStepsOutOfIt() {
        var size by mutableStateOf(UiSize.LARGE)
        rule.setContent {
            FitakaTheme(ThemeId.MANGO, DarkModePref.LIGHT, size = size) {
                Column {
                    Box(Modifier.size(100.dp).testTag("a part"))
                    Text("Sahod", Modifier.testTag("words"), style = MaterialTheme.typography.titleMedium)
                    FullSize { Box(Modifier.size(100.dp).testTag("the bar")) }
                }
            }
        }
        fun width(tag: String) = rule.onNodeWithTag(tag).fetchSemanticsNode().size.width
        fun height(tag: String) = rule.onNodeWithTag(tag).fetchSemanticsNode().size.height
        val full = width("a part")
        val words = height("words")
        assertEquals("at Large a part is the size the phone gives it", full, width("the bar"))
        for (chosen in UiSize.entries) {
            size = chosen
            rule.waitForIdle()
            assertEquals("a part at ${chosen.label}", full * chosen.scale, width("a part").toFloat(), 1f)
            assertEquals("the bar at ${chosen.label} keeps its size", full, width("the bar"))
        }
        size = UiSize.SMALL
        rule.waitForIdle()
        assertTrue("the words shrink too: ${height("words")} against $words", height("words") < words)
    }
}
