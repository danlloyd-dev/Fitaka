package com.pitakaview.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.pitakaview.app.domain.AppearanceSettings
import com.pitakaview.app.domain.DarkModePref
import com.pitakaview.app.domain.ThemeId
import com.pitakaview.app.ui.components.AmountText
import com.pitakaview.app.ui.components.FitakaButton
import com.pitakaview.app.ui.components.FitakaButtonStyle
import com.pitakaview.app.ui.components.FitakaCard
import com.pitakaview.app.ui.components.FitakaListItem
import com.pitakaview.app.ui.components.SectionBadge
import com.pitakaview.app.ui.components.SectionTag
import com.pitakaview.app.ui.icons.FitakaIcons
import com.pitakaview.app.ui.icons.icon
import com.pitakaview.app.ui.theme.FitakaTheme
import com.pitakaview.app.ui.theme.Section
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the components to PNG on the JVM (Robolectric native graphics + Roborazzi).
 * Record with `./gradlew :app:recordRoborazziDebug`; images land in app/build/outputs/roborazzi/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ThemeScreenshotTest {

    private fun mode(dark: Boolean) = if (dark) DarkModePref.DARK else DarkModePref.LIGHT

    /** Every component, full height, for each theme in light and dark. */
    @Test
    @Config(qualifiers = "w400dp-h1820dp-hdpi")
    fun galleryInEveryTheme() {
        for (theme in ThemeId.entries) {
            for (dark in listOf(false, true)) {
                val name = "${theme.name.lowercase()}_${if (dark) "dark" else "light"}"
                captureRoboImage("build/outputs/roborazzi/gallery_$name.png") {
                    FitakaTheme(theme, mode(dark)) {
                        ComponentGallery(AppearanceSettings(theme, mode(dark)), onThemeChange = {}, onDarkModeChange = {}, scrollable = false)
                    }
                }
            }
        }
    }

    /** Every theme in light and dark side by side, for comparing at a glance. */
    @Test
    @Config(qualifiers = "w1920dp-h560dp-mdpi")
    fun allThemesSideBySide() {
        captureRoboImage("build/outputs/roborazzi/themes_overview.png") {
            Row {
                for (theme in ThemeId.entries) {
                    for (dark in listOf(false, true)) {
                        FitakaTheme(theme, mode(dark)) { MiniPreview("${theme.label} ${if (dark) "dark" else "light"}") }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniPreview(title: String) {
    val c = FitakaTheme.colors
    Column(
        Modifier.width(160.dp).fillMaxHeight().background(c.background).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = c.ink)
        FitakaButton("Add", onClick = {}, modifier = Modifier.fillMaxWidth(), style = FitakaButtonStyle.Primary, icon = FitakaIcons.Add)
        FitakaButton("View", onClick = {}, modifier = Modifier.fillMaxWidth())
        FitakaCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)) {
            FitakaListItem("Food", subtitle = "Gastos", leading = { SectionBadge(Section.GASTOS, Section.GASTOS.icon, size = 32.dp) })
            AmountText(-12_500, signed = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(Section.KITA, Section.BAON).forEach { SectionTag(it.label, it) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(Section.IPON, Section.UTANG).forEach { SectionTag(it.label, it) }
        }
    }
}
