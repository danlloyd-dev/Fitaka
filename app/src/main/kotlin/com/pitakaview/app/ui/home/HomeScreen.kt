package com.pitakaview.app.ui.home

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pitakaview.app.data.db.dao.RecentTransaction
import com.pitakaview.app.data.db.dao.isMove
import com.pitakaview.app.data.db.dao.signedCentavos
import com.pitakaview.app.di.LocalAppContainer
import com.pitakaview.app.domain.Peso
import com.pitakaview.app.ui.components.AmountText
import com.pitakaview.app.ui.components.PitakaButton
import com.pitakaview.app.ui.components.PitakaButtonStyle
import com.pitakaview.app.ui.components.PitakaCard
import com.pitakaview.app.ui.components.PitakaDivider
import com.pitakaview.app.ui.components.PitakaIconButton
import com.pitakaview.app.ui.components.PitakaListItem
import com.pitakaview.app.ui.components.PitakaScreen
import com.pitakaview.app.ui.components.SectionBadge
import com.pitakaview.app.ui.components.SectionHeader
import com.pitakaview.app.ui.components.sketchBox
import com.pitakaview.app.ui.icons.PitakaIcons
import com.pitakaview.app.ui.icons.icon
import com.pitakaview.app.ui.motion.AnimatedAmountText
import com.pitakaview.app.ui.motion.PitakaMotion
import com.pitakaview.app.ui.theme.PitakaDimens
import com.pitakaview.app.ui.theme.PitakaTheme
import com.pitakaview.app.ui.theme.Section
import com.pitakaview.app.ui.theme.label
import com.pitakaview.app.ui.theme.section
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun HomeRoute(
    onOpenSection: (Section) -> Unit,
    onAddTransaction: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val container = LocalAppContainer.current
    val vm = viewModel { HomeViewModel(container.database, container.dayClock.today) }
    val state by vm.state.collectAsStateWithLifecycle()
    // The same "today" the ViewModel uses, so the dates on screen and the totals always agree.
    val today by container.dayClock.today.collectAsStateWithLifecycle()
    HomeContent(state, today, greetingFor(LocalTime.now()), onOpenSection, onAddTransaction, onOpenSettings)
}

fun greetingFor(time: LocalTime): String = when (time.hour) {
    in 5..11 -> "Magandang umaga!"
    in 12..17 -> "Magandang hapon!"
    else -> "Magandang gabi!"
}

private val shortDate = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
private val longDate = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)

fun friendlyDate(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    today.plusDays(1) -> "Tomorrow"
    else -> (if (date.year == today.year) shortDate else longDate).format(date)
}

/** Stateless Home, so screenshot tests can render it with any [state]. */
@Composable
fun HomeContent(
    state: HomeState,
    today: LocalDate,
    greeting: String,
    onOpenSection: (Section) -> Unit,
    onAddTransaction: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val month = today.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
    PitakaScreen(
        title = "PitakaView",
        subtitle = greeting,
        actions = { PitakaIconButton(PitakaIcons.Settings, "Settings", onOpenSettings) },
    ) {
        item(key = "hero") { WholeWalletCard(state) }
        item(key = "add") {
            PitakaButton(
                "Add transaction",
                onClick = onAddTransaction,
                modifier = Modifier.fillMaxWidth(),
                style = PitakaButtonStyle.Primary,
                icon = PitakaIcons.Add,
            )
        }
        item(key = "month") { SectionHeader("This month · $month") }
        item(key = "tiles-1") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTile(Section.KITA, "Income", state.incomeCentavos, onOpenSection, Modifier.weight(1f))
                SectionTile(Section.GASTOS, "Spent", state.spendingCentavos, onOpenSection, Modifier.weight(1f))
            }
        }
        item(key = "tiles-2") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTile(Section.IPON, "Saved", state.savedCentavos, onOpenSection, Modifier.weight(1f))
                SectionTile(Section.BAON, if (state.baonLeftCentavos == null) "Not set up" else "Left", state.baonLeftCentavos, onOpenSection, Modifier.weight(1f))
            }
        }
        item(key = "utang") { UtangTile(state, onOpenSection) }
        item(key = "upcoming-title") { SectionHeader("Coming up") }
        item(key = "upcoming") { UpcomingCard(state.upcoming, today) }
        item(key = "recent-title") { SectionHeader("Recent") }
        item(key = "recent") { RecentCard(state.recent, today) }
    }
}

@Composable
private fun WholeWalletCard(state: HomeState) {
    val c = PitakaTheme.colors
    val type = MaterialTheme.typography
    PitakaCard(Modifier.fillMaxWidth()) {
        Text("Your whole wallet", style = type.labelLarge, color = c.inkMuted)
        AnimatedAmountText(state.totalCentavos, style = type.displaySmall)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.wallets.forEach { w ->
                Column(
                    Modifier
                        .sketchBox(RoundedCornerShape(PitakaDimens.cornerSmall), c.background, c.ink, PitakaDimens.borderThin)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(w.wallet.name, style = type.labelMedium, color = c.inkMuted)
                    Text(Peso.format(w.balanceCentavos), style = type.labelLarge, color = c.ink)
                }
            }
        }
    }
}

@Composable
private fun SectionTile(
    section: Section,
    caption: String,
    centavos: Long?,
    onOpen: (Section) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = PitakaTheme.colors
    val type = MaterialTheme.typography
    PitakaCard(modifier, section = section, onClick = { onOpen(section) }, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionBadge(section, section.icon, size = 32.dp)
            Text(section.label, style = type.titleSmall, color = c.ink)
        }
        Spacer(Modifier.height(8.dp))
        Text(caption, style = type.bodySmall, color = c.inkMuted)
        if (centavos != null) AnimatedAmountText(centavos) else Text("—", style = type.titleMedium, color = c.ink)
    }
}

@Composable
private fun UtangTile(state: HomeState, onOpen: (Section) -> Unit) {
    val c = PitakaTheme.colors
    val type = MaterialTheme.typography
    PitakaCard(Modifier.fillMaxWidth(), section = Section.UTANG, onClick = { onOpen(Section.UTANG) }, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionBadge(Section.UTANG, Section.UTANG.icon, size = 32.dp)
            Text(Section.UTANG.label, style = type.titleSmall, color = c.ink)
        }
        Spacer(Modifier.height(8.dp))
        Row {
            Column(Modifier.weight(1f)) {
                Text("You owe", style = type.bodySmall, color = c.inkMuted)
                AnimatedAmountText(state.iOweCentavos)
            }
            Column(Modifier.weight(1f)) {
                Text("Owed to you", style = type.bodySmall, color = c.inkMuted)
                AnimatedAmountText(state.owedToMeCentavos)
            }
        }
    }
}

@Composable
private fun UpcomingCard(items: List<UpcomingItem>, today: LocalDate) {
    val c = PitakaTheme.colors
    PitakaCard(
        Modifier.fillMaxWidth().animateContentSize(tween(PitakaMotion.MEDIUM, easing = PitakaMotion.Emphasized)),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = if (items.isEmpty()) 16.dp else 4.dp),
    ) {
        if (items.isEmpty()) {
            Text("Nothing due in the next 2 weeks.", style = MaterialTheme.typography.bodyMedium, color = c.inkMuted)
        }
        items.forEachIndexed { i, item ->
            if (i > 0) PitakaDivider()
            PitakaListItem(
                title = item.title,
                subtitle = "${item.detail} · ${friendlyDate(item.date, today)}",
                leading = { SectionBadge(item.section, item.section.icon) },
                trailing = { item.amountCentavos?.let { AmountText(it) } },
            )
        }
    }
}

@Composable
private fun RecentCard(rows: List<RecentTransaction>, today: LocalDate) {
    val c = PitakaTheme.colors
    PitakaCard(
        Modifier.fillMaxWidth().animateContentSize(tween(PitakaMotion.MEDIUM, easing = PitakaMotion.Emphasized)),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = if (rows.isEmpty()) 16.dp else 4.dp),
    ) {
        if (rows.isEmpty()) {
            Text(
                "No entries yet. Tap Add transaction to log your first one.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.inkMuted,
            )
        }
        rows.forEachIndexed { i, row ->
            key(row.id) {
                if (i > 0) PitakaDivider()
                val section = row.purpose.section
                // The badge already shows the section, so the subtitle is just where and when.
                val wallets = listOfNotNull(row.fromWalletName, row.toWalletName).joinToString(" → ")
                PitakaListItem(
                    title = row.categoryName ?: row.purpose.label,
                    subtitle = listOf(wallets, friendlyDate(row.occurredOn, today)).filter { it.isNotEmpty() }.joinToString(" · "),
                    leading = { if (section != null) SectionBadge(section, section.icon) else TransferBadge() },
                    // Moves between your own wallets are neither in nor out, so they get no sign.
                    trailing = { AmountText(row.signedCentavos, signed = !row.isMove) },
                )
            }
        }
    }
}

@Composable
private fun TransferBadge() {
    val c = PitakaTheme.colors
    Box(
        Modifier.size(40.dp).sketchBox(RoundedCornerShape(12.dp), c.background, c.ink, PitakaDimens.borderThin),
        contentAlignment = Alignment.Center,
    ) {
        Icon(PitakaIcons.Wallet, contentDescription = null, tint = c.ink, modifier = Modifier.size(22.dp))
    }
}
