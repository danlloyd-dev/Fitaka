package com.pitakaview.app.ui.placeholder

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pitakaview.app.ui.components.PitakaButton
import com.pitakaview.app.ui.components.PitakaButtonStyle
import com.pitakaview.app.ui.components.PitakaCard
import com.pitakaview.app.ui.components.PitakaScreen
import com.pitakaview.app.ui.components.SectionBadge
import com.pitakaview.app.ui.icons.PitakaIcons
import com.pitakaview.app.ui.icons.icon
import com.pitakaview.app.ui.theme.PitakaTheme
import com.pitakaview.app.ui.theme.Section
import com.pitakaview.app.ui.theme.tagline

// Temporary, clearly labelled screens so every tab and button already works. Each is replaced as its
// real screen is built.

@Composable
fun SectionComingSoon(section: Section, onAddTransaction: () -> Unit) {
    val c = PitakaTheme.colors
    PitakaScreen(title = section.label, subtitle = section.tagline) {
        item(key = "soon") {
            PitakaCard(Modifier.fillMaxWidth(), section = section) {
                SectionBadge(section, section.icon, size = 48.dp)
                Spacer(Modifier.height(12.dp))
                Text("Being built next", style = MaterialTheme.typography.titleMedium, color = c.ink)
                Text(
                    "This tab will show your ${section.tagline.lowercase()} in detail.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.inkMuted,
                )
            }
        }
        item(key = "add") {
            PitakaButton(
                "Add transaction",
                onClick = onAddTransaction,
                modifier = Modifier.fillMaxWidth(),
                style = PitakaButtonStyle.Primary,
                icon = PitakaIcons.Add,
            )
        }
    }
}

@Composable
fun AddTransactionComingSoon(onBack: () -> Unit) {
    val c = PitakaTheme.colors
    PitakaScreen(title = "Add transaction", onBack = onBack) {
        item(key = "soon") {
            PitakaCard(Modifier.fillMaxWidth()) {
                Text("Quick entry is next", style = MaterialTheme.typography.titleMedium, color = c.ink)
                Text(
                    "Amount, category and wallet in a few taps — this is the next screen being built.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.inkMuted,
                )
            }
        }
    }
}
