package com.pitakaview.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pitakaview.app.ui.home.friendlyDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Today, yesterday or another date, as chips. Used wherever an entry needs its date. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DateChoice(date: LocalDate, today: LocalDate, onDate: (LocalDate) -> Unit, title: String = "Date") {
    var pick by rememberSaveable { mutableStateOf(false) }
    val gap = Arrangement.spacedBy(8.dp)
    Column {
        SectionHeader(title)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = gap, verticalArrangement = gap) {
            val yesterday = today.minusDays(1)
            FitakaChip("Today", selected = date == today, onClick = { onDate(today) })
            FitakaChip("Yesterday", selected = date == yesterday, onClick = { onDate(yesterday) })
            val other = date != today && date != yesterday
            FitakaChip(if (other) friendlyDate(date, today) else "Other date", selected = other, onClick = { pick = true })
        }
    }
    if (pick) PickDateDialog(date, onPick = onDate, onDismiss = { pick = false })
}

/** A date that may be left out: [noneLabel], or one picked from the calendar. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OptionalDateChoice(
    title: String,
    date: LocalDate?,
    today: LocalDate,
    onDate: (LocalDate?) -> Unit,
    noneLabel: String = "No date",
    pickLabel: String = "Pick a date",
) {
    var pick by rememberSaveable { mutableStateOf(false) }
    val gap = Arrangement.spacedBy(8.dp)
    Column {
        SectionHeader(title)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = gap, verticalArrangement = gap) {
            FitakaChip(noneLabel, selected = date == null, onClick = { onDate(null) })
            FitakaChip(date?.let { friendlyDate(it, today) } ?: pickLabel, selected = date != null, onClick = { pick = true })
        }
    }
    if (pick) PickDateDialog(date ?: today, onPick = onDate, onDismiss = { pick = false })
}

/** The stock Material date picker, themed by FitakaTheme's colours and font. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickDateDialog(initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = state)
    }
}
