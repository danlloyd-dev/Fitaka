package com.pitakaview.app.ui.ledger

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pitakaview.app.data.db.dao.RecentTransaction
import com.pitakaview.app.data.db.dao.isMove
import com.pitakaview.app.data.db.dao.signedCentavos
import com.pitakaview.app.domain.Peso
import com.pitakaview.app.ui.components.AmountText
import com.pitakaview.app.ui.components.Gloss
import com.pitakaview.app.ui.components.PitakaCard
import com.pitakaview.app.ui.components.PitakaDivider
import com.pitakaview.app.ui.components.PitakaIconButton
import com.pitakaview.app.ui.components.PitakaListItem
import com.pitakaview.app.ui.components.SectionBadge
import com.pitakaview.app.ui.components.sketchBox
import com.pitakaview.app.ui.home.friendlyDate
import com.pitakaview.app.ui.icons.CategoryIcons
import com.pitakaview.app.ui.icons.PitakaIcons
import com.pitakaview.app.ui.icons.icon
import com.pitakaview.app.ui.icons.sizeFor
import com.pitakaview.app.ui.motion.AnimatedAmountText
import com.pitakaview.app.ui.motion.PitakaMotion
import com.pitakaview.app.ui.theme.PitakaDimens
import com.pitakaview.app.ui.theme.PitakaTheme
import com.pitakaview.app.ui.theme.Section
import com.pitakaview.app.ui.theme.label
import com.pitakaview.app.ui.theme.section
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale

// The pieces the Gastos and Kita tabs (and Search) share.

/** "November". */
fun monthName(month: YearMonth): String = month.month.getDisplayName(DateTextStyle.FULL, Locale.ENGLISH)

/** "November 2026". */
fun monthTitle(month: YearMonth): String = "${monthName(month)} ${month.year}"

fun entriesText(count: Int): String = if (count == 1) "1 entry" else "$count entries"

/** "Cash", or "MariBank → GCash" for a move between wallets. */
fun RecentTransaction.walletsText(): String = listOfNotNull(fromWalletName, toWalletName).joinToString(" → ")

/** Under a row in a day group: where, and the note if there is one. The day is in the group's heading. */
fun RecentTransaction.ledgerSubtitle(): String =
    listOf(walletsText(), note.orEmpty()).filter { it.isNotEmpty() }.joinToString(" · ")

/**
 * Under a row on Home, which has no day headings: where, the note if there is one, and the day. The
 * note is what tells two entries of the same category apart (every imported line is "Other" at first).
 */
fun RecentTransaction.homeSubtitle(today: LocalDate): String =
    listOf(walletsText(), note.orEmpty(), friendlyDate(occurredOn, today)).filter { it.isNotEmpty() }.joinToString(" · ")

/** Previous month, the month shown, next month. Tapping the name of another month returns to this month. */
@Composable
fun MonthSwitcher(
    month: YearMonth,
    isThisMonth: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onThisMonth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = PitakaTheme.colors
    val type = MaterialTheme.typography
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        PitakaIconButton(PitakaIcons.ChevronLeft, "Previous month", onPrevious)
        Column(
            Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .then(if (isThisMonth) Modifier else Modifier.clickable(role = Role.Button, onClick = onThisMonth)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(monthTitle(month), style = type.titleMedium, color = c.ink)
            // The second line is always there, so the row does not jump when the month changes.
            Text(
                if (isThisMonth) "This month" else "Back to this month",
                style = type.labelMedium,
                color = if (isThisMonth) c.inkMuted else c.ink,
                textDecoration = if (isThisMonth) null else TextDecoration.Underline,
            )
        }
        PitakaIconButton(PitakaIcons.ChevronRight, "Next month", onNext)
    }
}

/** The month's total on the section's tint. */
@Composable
fun MonthTotalCard(section: Section, caption: String, ledger: MonthLedger, modifier: Modifier = Modifier) {
    val c = PitakaTheme.colors
    val type = MaterialTheme.typography
    PitakaCard(modifier.fillMaxWidth(), section = section) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionBadge(section, section.icon, size = 36.dp)
            Text(caption, style = type.titleSmall, color = c.ink)
        }
        Spacer(Modifier.height(8.dp))
        AnimatedAmountText(ledger.totalCentavos, style = type.displaySmall)
        Text(if (ledger.loaded) entriesText(ledger.entryCount) else " ", style = type.bodySmall, color = c.captionOnTint)
    }
}

/** Each category's share of the month: name, percent and amount in words, plus a bar for the eye. */
@Composable
fun CategoryBreakdownCard(categories: List<CategoryShare>, totalCentavos: Long, section: Section, modifier: Modifier = Modifier) {
    val c = PitakaTheme.colors
    val type = MaterialTheme.typography
    PitakaCard(modifier.fillMaxWidth()) {
        categories.forEachIndexed { i, category ->
            key(category.name) {
                if (i > 0) Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // The name beside it says what it is; a row without a category of its own gets the three dots.
                    val icon = CategoryIcons.forKey(category.iconKey)
                    Icon(icon, contentDescription = null, tint = c.ink, modifier = Modifier.size(icon.sizeFor(20.dp)))
                    Spacer(Modifier.width(8.dp))
                    Text(category.name, style = type.titleSmall, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(
                        MonthLedgerBuilder.percentText(category.totalCentavos, totalCentavos),
                        style = type.labelMedium, color = c.inkMuted, modifier = Modifier.padding(horizontal = 10.dp),
                    )
                    AmountText(category.totalCentavos, style = type.titleSmall)
                }
                Spacer(Modifier.height(6.dp))
                ShareBar(if (totalCentavos > 0) category.totalCentavos.toFloat() / totalCentavos else 0f, section)
            }
        }
    }
}

/** A bar filled to [fraction] in the section's colour. The words beside it carry the meaning; the bar is for the eye. */
@Composable
fun ShareBar(fraction: Float, section: Section) {
    val c = PitakaTheme.colors
    val shape = RoundedCornerShape(50)
    val shown by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(PitakaMotion.MEDIUM, easing = PitakaMotion.Emphasized), label = "share")
    Box(
        Modifier
            .fillMaxWidth()
            .height(12.dp)
            .sketchBox(shape, fill = c.background, border = c.ink, borderWidth = PitakaDimens.borderThin)
            .clip(shape),
    ) {
        // The fill, with a paler band along its top half: the same gloss the buttons have.
        Box(
            Modifier.fillMaxWidth(shown).fillMaxHeight().background(c.fill(section)).drawBehind {
                drawRect(Color.White.copy(alpha = 0.55f), size = Size(size.width, size.height * 0.45f))
            },
        )
    }
}

/** One card per day, newest day first; each day is its own lazy item, so a long month stays cheap to scroll. */
fun LazyListScope.dayGroups(days: List<DayGroup>, today: LocalDate, onOpenEntry: (Long) -> Unit) {
    items(days, key = { "day-${it.date.toEpochDay()}" }) { day -> DayGroupCard(day, today, onOpenEntry) }
}

@Composable
private fun DayGroupCard(day: DayGroup, today: LocalDate, onOpenEntry: (Long) -> Unit) {
    val c = PitakaTheme.colors
    val type = MaterialTheme.typography
    Column {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(friendlyDate(day.date, today), style = type.labelLarge, color = c.ink, modifier = Modifier.weight(1f))
            if (day.rows.size > 1) Text(Peso.format(day.totalCentavos), style = type.labelLarge, color = c.inkMuted)
        }
        PitakaCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            day.rows.forEachIndexed { i, row ->
                key(row.id) {
                    if (i > 0) PitakaDivider()
                    EntryListItem(row, subtitle = row.ledgerSubtitle(), onClick = { onOpenEntry(row.id) })
                }
            }
        }
    }
}

/**
 * One ledger row: a badge in the section's colour, category, [subtitle], signed amount. The badge
 * wears the category's own icon when the entry has a category, the section's otherwise.
 */
@Composable
fun EntryListItem(row: RecentTransaction, subtitle: String, onClick: () -> Unit) {
    val section = row.purpose.section
    PitakaListItem(
        title = row.categoryName ?: row.purpose.label,
        subtitle = subtitle.ifEmpty { null },
        leading = {
            if (section != null) {
                SectionBadge(section, row.categoryIconKey?.let(CategoryIcons::forKey) ?: section.icon)
            } else {
                TransferBadge()
            }
        },
        // Moves between your own wallets are neither in nor out, so they get no sign.
        trailing = { AmountText(row.signedCentavos, signed = !row.isMove) },
        onClick = onClick,
    )
}

@Composable
fun TransferBadge() {
    val c = PitakaTheme.colors
    Box(
        Modifier.size(40.dp).sketchBox(RoundedCornerShape(12.dp), c.background, c.ink, PitakaDimens.borderThin, gloss = Gloss.Badge),
        contentAlignment = Alignment.Center,
    ) {
        Icon(PitakaIcons.Wallet, contentDescription = null, tint = c.ink, modifier = Modifier.size(PitakaIcons.Wallet.sizeFor(22.dp)))
    }
}
