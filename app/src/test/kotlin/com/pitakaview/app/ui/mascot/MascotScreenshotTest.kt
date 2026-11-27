package com.pitakaview.app.ui.mascot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.pitakaview.app.domain.DarkModePref
import com.pitakaview.app.domain.ThemeId
import com.pitakaview.app.ui.theme.FitakaTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The mascot in each mood, in every theme, light and dark, and small: one picture to look at. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w460dp-h1700dp-hdpi")
class MascotScreenshotTest {
    @Test
    fun everyMoodInEveryTheme() = captureRoboImage("build/outputs/roborazzi/mascot_moods.png") {
        Column {
            for (theme in ThemeId.entries) {
                for (mode in listOf(DarkModePref.LIGHT, DarkModePref.DARK)) {
                    FitakaTheme(theme, mode) {
                        Row(
                            Modifier.background(FitakaTheme.colors.background).padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            AlkansyaMood.entries.forEach { Alkansya(mood = it, width = 120.dp) }
                            Column {
                                Alkansya(mood = AlkansyaMood.HELLO, width = 56.dp)
                                Text(theme.name.lowercase(), style = MaterialTheme.typography.bodySmall, color = FitakaTheme.colors.ink)
                            }
                        }
                    }
                }
            }
        }
    }
}
