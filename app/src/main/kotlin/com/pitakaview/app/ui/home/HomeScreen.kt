package com.pitakaview.app.ui.home

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pitakaview.app.data.db.dao.RecentTransaction
import com.pitakaview.app.di.LocalAppContainer
import com.pitakaview.app.domain.Peso
import com.pitakaview.app.ui.components.AmountText
import com.pitakaview.app.ui.components.EmptyState
import com.pitakaview.app.ui.components.PitakaButton
import com.pitakaview.app.ui.components.PitakaButtonStyle
import com.pitakaview.app.ui.components.PitakaCard
import com.pitakaview.app.ui.components.PitakaDivider
import com.pitakaview.app.ui.components.PitakaIconButton
import com.pitakaview.app.ui.components.PitakaListItem
import com.pitakaview.app.ui.components.PitakaScreen
import com.pitakaview.app.ui.components.SectionBadge
import com.pitakaview.app.ui.components.SectionHeader
import com.pitakaview.app.ui.icons.PitakaIcons
import com.pitakaview.app.ui.icons.icon
import com.pitakaview.app.ui.ledger.EntryListItem
import com.pitakaview.app.ui.ledger.homeSubtitle
import com.pitakaview.app.ui.mascot.SleepyAlkansya
import com.pitakaview.app.ui.motion.AnimatedAmountText
import com.pitakaview.app.ui.motion.PitakaMotion
import com.pitakaview.app.ui.theme.PitakaTheme
import com.pitakaview.app.ui.theme.Section
import kotlinx.coroutines.launch
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
    onOpenEntry: (Long) -> Unit,
    onOpenWallets: () -> Unit,
    onOpenBackup: () -> Unit,
    onSearch: () -> Unit,
    onOpenSummary: () -> Unit,
) {
    val container = LocalAppContainer.current
    val vm = viewModel { HomeViewModel(container.database, container.dayClock.today, container.settings.lastBackupAt) }
    val state by vm.state.collectAsStateWithLifecycle()
    // The same "today" the ViewModel uses, so the dates on screen and the totals always agree.
    val today by container.dayClock.today.collectAsStateWithLifecycle()
    // A restore that broke off restarts the app, which lands here: this is where the owner is told.
    val restoreFailed by container.settings.restoreFailed.collectAsStateWithLifecycle(initialValue = false)
    val scope = rememberCoroutineScope()
    HomeContent(
        state, today, greetingFor(LocalTime.now()), onOpenSection, onAddTransaction, onOpenSettings, onOpenEntry, onOpenWallets, onOpenBackup, onSearch, onOpenSummary,
        restoreFailed = restoreFailed,
        onRestoreNoticeSeen = { scope.launch { container.settings.setRestoreFailed(false) } },
    )
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
    onOpenEntry: (Long) -> Unit = {},
    onOpenWallets: () -> Unit = {},
    onOpenBackup: () -> Unit = {},
    onSearch: () -> Unit = {},
    onOpenSummary: () -> Unit = {},
    /** The last restore broke off and the app restarted on the data it had before. */
    restoreFailed: Boolean = false,
    onRestoreNoticeSeen: () -> Unit = {},
) {
    val month = today.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
    PitakaScreen(
        title = "PitakaView",
        subtitle = greeting,
        actions = {
            PitakaIconButton(PitakaIcons.Search, "Search", onSearch)
            PitakaIconButton(PitakaIcons.Settings, "Settings", onOpenSettings)
        },
    ) {
        // First on the page: the app has just restarted, and the owner needs to know why and what is true now.
        if (restoreFailed) {
            item(key = "restore-failed") { RestoreFailedCard(onOpenBackup, onRestoreNoticeSeen) }
        }
        item(key = "hero") { WholeWalletCard(state, onOpenWallets) }
        item(key = "add") {
            PitakaButton(
                "Add transaction",
                onClick = onAddTransaction,
                modifier = Modifier.fillMaxWidth(),
                style = PitakaButtonStyle.Primary,
                icon = PitakaIcons.Add,
            )
        }
        // High on the page: a reminder below the fold is not seen.
        state.backupNudge?.let { nudge ->
            item(key = "backup-nudge") { BackupNudgeCard(nudge, onOpenBackup) }
        }
        item(key = "month") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionHeader("This month · $month", Modifier.weight(1f))
                SummaryLink(onOpenSummary)
            }
        }
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
        item(key = "recent") { RecentCard(state.recent, today, onOpenEntry) }
    }
}

/** The words of the card shown after a restore that broke off. */
object RestoreFailedText {
    const val TITLE = "The restore did not finish"
    const val BODY = "Nothing was replaced: your entries are as they were before the restore. You can try it again from Backup."
}

@Composable
private fun RestoreFailedCard(onOpenBackup: () -> Unit, onSeen: () -> Unit) {
    val c = PitakaTheme.colors
    val type = MaterialTheme.typography
    PitakaCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
        Text("! ${RestoreFailedText.TITLE}", style = type.titleSmall, color = c.ink)
        Text(RestoreFailedText.BODY, style = type.bodyMedium, color = c.ink)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PitakaButton("OK", onClick = onSeen, modifier = Modifier.weight(1f))
            // Twice the width: at equal halves its two words were cut short on a phone.
            PitakaButton("Open Backup", onClick = { onSeen(); onOpenBackup() }, modifier = Modifier.weight(2f))
        }
    }
}

/** Opens the month's summary and chart. Underlined, with a chevron, so it reads as something to tap. */
@Composable
private fun SummaryLink(onClick: () -> Unit) {
    val c = PitakaTheme.colors
    Row(
        Modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onClick).padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Summary", style = MaterialTheme.typography.labelLarge, color = c.ink, textDecoration = TextDecoration.Underline)
        Icon(PitakaIcons.ChevronRight, contentDescription = null, tint = c.ink, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun WholeWalletCard(state: HomeState, onOpenWallets: () -> Unit) {
    val c = PitakaTheme.colors
    val type = MaterialTheme.typography
    PitakaCard(Modifier.fillMaxWidth(), onClick = onOpenWallets) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Your whole wallet", style = type.labelLarge, color = c.inkMuted, modifier = Modifier.weight(1f))
            Icon(PitakaIcons.ChevronRight, contentDescription = "Manage wallets", tint = c.inkMuted, modifier = Modifier.size(20.dp))
        }
        AnimatedAmountText(state.totalCentavos, style = type.displaySmall)
        Spacer(Modifier.height(10.dp))
        // One row per wallet, name on the left and amount on the right: three wallets or six read the
        // same, and nothing is squeezed into a row of boxes that scrolls sideways.
        state.wallets.forEach { w ->
            key(w.wallet.id) {
                PitakaDivider()
                Row(Modifier.fillMaxWidth().heightIn(min = 38.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(w.wallet.name, style = type.labelLarge, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(12.dp))
                    Text(Peso.format(w.balanceCentavos), style = type.labelLarge, color = c.ink, maxLines = 1)
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
        Text(caption, style = type.bodySmall, color = c.captionOnTint)
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
                Text("You owe", style = type.bodySmall, color = c.captionOnTint)
                AnimatedAmountText(state.iOweCentavos)
            }
            Column(Modifier.weight(1f)) {
                Text("Owed to you", style = type.bodySmall, color = c.captionOnTint)
                AnimatedAmountText(state.owedToMeCentavos)
            }
        }
    }
}

@Composable
private fun BackupNudgeCard(nudge: BackupNudge, onOpenBackup: () -> Unit) {
    val c = PitakaTheme.colors
    val type = MaterialTheme.typography
    PitakaCard(Modifier.fillMaxWidth(), onClick = onOpenBackup, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Back up your data", style = type.titleSmall, color = c.ink)
                Text(
                    if (nudge == BackupNudge.NEVER_BACKED_UP) {
                        "Your entries are only on this phone. Save a copy."
                    } else {
                        "Your last backup is more than 30 days old."
                    },
                    style = type.bodySmall, color = c.inkMuted,
                )
            }
            Icon(PitakaIcons.ChevronRight, contentDescription = null, tint = c.inkMuted, modifier = Modifier.size(20.dp))
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
private fun RecentCard(rows: List<RecentTransaction>, today: LocalDate, onOpenEntry: (Long) -> Unit) {
    val c = PitakaTheme.colors
    PitakaCard(
        Modifier.fillMaxWidth().animateContentSize(tween(PitakaMotion.MEDIUM, easing = PitakaMotion.Emphasized)),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = if (rows.isEmpty()) 16.dp else 4.dp),
    ) {
        if (rows.isEmpty()) {
            EmptyState("No entries yet", "Tap Add transaction to log your first one.", mascot = SleepyAlkansya)
        }
        rows.forEachIndexed { i, row ->
            key(row.id) {
                if (i > 0) PitakaDivider()
                EntryListItem(row, subtitle = row.homeSubtitle(today), onClick = { onOpenEntry(row.id) })
            }
        }
    }
}
