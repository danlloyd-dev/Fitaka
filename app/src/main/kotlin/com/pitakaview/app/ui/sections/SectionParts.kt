package com.pitakaview.app.ui.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pitakaview.app.data.db.entity.WalletEntity
import com.pitakaview.app.ui.components.EmptyState
import com.pitakaview.app.ui.components.PitakaButton
import com.pitakaview.app.ui.components.PitakaButtonStyle
import com.pitakaview.app.ui.components.PitakaCard
import com.pitakaview.app.ui.components.PitakaChip
import com.pitakaview.app.ui.components.PitakaScreen
import com.pitakaview.app.ui.components.ProblemText
import com.pitakaview.app.ui.components.SectionBadge
import com.pitakaview.app.ui.components.SectionHeader
import com.pitakaview.app.ui.home.friendlyDate
import com.pitakaview.app.ui.icons.icon
import com.pitakaview.app.ui.ledger.DayGroup
import com.pitakaview.app.ui.ledger.ShareBar
import com.pitakaview.app.ui.ledger.dayGroups
import com.pitakaview.app.ui.motion.AnimatedAmountText
import com.pitakaview.app.ui.theme.PitakaTheme
import com.pitakaview.app.ui.theme.Section
import java.time.LocalDate

// The pieces the Utang, Ipon and Baon screens share.

private val DAY_WORDS = setOf("Today", "Yesterday", "Tomorrow")

/** [friendlyDate] for the middle of a sentence: "today", "yesterday", "Nov 30". */
fun dateInText(date: LocalDate, today: LocalDate): String = friendlyDate(date, today).let { if (it in DAY_WORDS) it.lowercase() else it }

/** One or two captioned amounts on the section's tint, at the top of a section tab. */
@Composable
fun SectionTotalsCard(section: Section, totals: List<Pair<String, Long>>, modifier: Modifier = Modifier) {
    val c = PitakaTheme.colors
    val type = MaterialTheme.typography
    PitakaCard(modifier.fillMaxWidth(), section = section) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionBadge(section, section.icon, size = 36.dp)
            Text(section.label, style = type.titleSmall, color = c.ink)
        }
        Spacer(Modifier.height(8.dp))
        Row {
            totals.forEach { (caption, centavos) ->
                Column(Modifier.weight(1f)) {
                    Text(caption, style = type.bodySmall, color = c.inkMuted)
                    AnimatedAmountText(centavos, style = if (totals.size == 1) type.displaySmall else type.titleLarge)
                }
            }
        }
    }
}

/**
 * One loan, goal or baon in its tab's list. A card of its own, so it has room for a progress bar and
 * for the one or two things done with it most often.
 */
@Composable
fun SectionItemCard(
    section: Section,
    title: String,
    amount: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    amountCaption: String? = null,
    lines: List<String> = emptyList(),
    fraction: Float? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val c = PitakaTheme.colors
    val type = MaterialTheme.typography
    PitakaCard(modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionBadge(section, section.icon)
            Text(title, style = type.titleSmall, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(amount, style = type.titleMedium, color = c.ink, maxLines = 1)
                if (amountCaption != null) Text(amountCaption, style = type.labelMedium, color = c.inkMuted)
            }
        }
        if (lines.isNotEmpty()) {
            // Under the row and in line with the title, not squeezed beside the amount: a line such as
            // "₱2,000.00 overdue since Nov 5" needs the width.
            Column(Modifier.padding(start = 54.dp, top = 2.dp)) {
                lines.forEach { Text(it, style = type.bodySmall, color = c.inkMuted) }
            }
        }
        if (fraction != null) {
            Spacer(Modifier.height(10.dp))
            ShareBar(fraction, section)
        }
        if (actions != null) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
        }
    }
}

/** What a loan's, goal's or baon's own screen shows. Each section fills this in; the screen is shared. */
data class ItemDetailUi(
    val title: String,
    val subtitle: String?,
    val section: Section,
    val amountCaption: String,
    val amountCentavos: Long,
    val lines: List<String> = emptyList(),
    val fraction: Float? = null,
    val days: List<DayGroup> = emptyList(),
)

/** A button on a detail screen. */
data class DetailAction(val label: String, val onClick: () -> Unit, val primary: Boolean = false, val enabled: Boolean = true)

@Composable
fun ItemDetailContent(
    ui: ItemDetailUi,
    today: LocalDate,
    actions: List<DetailAction>,
    editLabel: String,
    onEdit: () -> Unit,
    onOpenEntry: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val c = PitakaTheme.colors
    val type = MaterialTheme.typography
    PitakaScreen(title = ui.title, subtitle = ui.subtitle, onBack = onBack) {
        item(key = "summary") {
            PitakaCard(Modifier.fillMaxWidth(), section = ui.section) {
                Text(ui.amountCaption, style = type.titleSmall, color = c.ink)
                AnimatedAmountText(ui.amountCentavos, style = type.displaySmall)
                ui.lines.forEach { Text(it, style = type.bodyMedium, color = c.ink) }
                if (ui.fraction != null) {
                    Spacer(Modifier.height(10.dp))
                    ShareBar(ui.fraction, ui.section)
                }
            }
        }
        actions.forEach { action ->
            item(key = "action-${action.label}") {
                PitakaButton(
                    action.label, onClick = action.onClick, modifier = Modifier.fillMaxWidth(), enabled = action.enabled,
                    style = if (action.primary) PitakaButtonStyle.Primary else PitakaButtonStyle.Secondary,
                )
            }
        }
        item(key = "edit") { PitakaButton(editLabel, onClick = onEdit, modifier = Modifier.fillMaxWidth()) }
        item(key = "entries-title") { SectionHeader("Entries") }
        if (ui.days.isEmpty()) {
            item(key = "no-entries") {
                PitakaCard(Modifier.fillMaxWidth()) { EmptyState("Nothing logged yet", "Entries for this show up here, newest first.") }
            }
        }
        dayGroups(ui.days, today, onOpenEntry)
    }
}

/**
 * Pick a wallet, with one more choice in front for "none" when [noneLabel] is given.
 * [selectedId] null with [noneSelected] true means that "none" choice is picked.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WalletChoice(
    title: String,
    wallets: List<WalletEntity>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    noneLabel: String? = null,
    noneSelected: Boolean = false,
    problem: String? = null,
    hint: String? = null,
) {
    val gap = Arrangement.spacedBy(8.dp)
    Column {
        SectionHeader(title)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = gap, verticalArrangement = gap) {
            if (noneLabel != null) PitakaChip(noneLabel, selected = noneSelected && selectedId == null, onClick = { onSelect(null) })
            wallets.forEach { PitakaChip(it.name, selected = it.id == selectedId, onClick = { onSelect(it.id) }) }
        }
        if (problem != null) ProblemText(problem)
        if (hint != null) {
            Text(
                hint, style = MaterialTheme.typography.bodySmall, color = PitakaTheme.colors.inkMuted,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp),
            )
        }
    }
}
