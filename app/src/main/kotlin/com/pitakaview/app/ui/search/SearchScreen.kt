package com.pitakaview.app.ui.search

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pitakaview.app.data.db.FitakaDatabase
import com.pitakaview.app.data.db.dao.RecentTransaction
import com.pitakaview.app.di.LocalAppContainer
import com.pitakaview.app.domain.SearchQuery
import com.pitakaview.app.ui.components.EmptyState
import com.pitakaview.app.ui.components.FitakaCard
import com.pitakaview.app.ui.components.FitakaScreen
import com.pitakaview.app.ui.components.FitakaTextField
import com.pitakaview.app.ui.ledger.DayGroup
import com.pitakaview.app.ui.ledger.MonthLedgerBuilder
import com.pitakaview.app.ui.ledger.dayGroups
import com.pitakaview.app.ui.ledger.entriesText
import com.pitakaview.app.ui.theme.FitakaTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/** What the search screen shows. [asked] is false while the box is empty. */
data class SearchUi(
    val asked: Boolean = false,
    val count: Int = 0,
    val days: List<DayGroup> = emptyList(),
    /** True when there were more matches than are shown. */
    val more: Boolean = false,
)

/**
 * Search across every entry. Each change to the text asks the database again and drops the answer to
 * the previous text; one search over 10,000 entries measured about 8 ms, so no typing delay is added.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModel(db: FitakaDatabase) : ViewModel() {
    val query = TextFieldState()

    val results: StateFlow<SearchUi> = snapshotFlow { query.text.toString().trim() }
        .distinctUntilChanged()
        .flatMapLatest { text ->
            val pattern = SearchQuery.pattern(text)
            val amount = SearchQuery.amountCentavos(text)
            if (pattern == null) {
                flowOf(SearchUi())
            } else if (!SearchQuery.needsOwnMatching(text)) {
                // One row more than is shown tells us whether there were more.
                db.transactionDao().search(pattern, amount, LIMIT + 1).map(::show)
            } else {
                // A letter such as ñ: the database finds every row with any character in its place (a
                // few more than wanted), and the rows that really have the letter are kept here. The
                // cut to what is shown comes after that, so no real match is pushed out by a near one.
                db.transactionDao().search(pattern, amount, Int.MAX_VALUE).map { rows ->
                    show(
                        rows.filter { row ->
                            row.amountCentavos == amount || SearchQuery.matches(text, row.note, row.categoryName, row.fromWalletName, row.toWalletName)
                        },
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUi())

    private fun show(rows: List<RecentTransaction>): SearchUi {
        val shown = rows.take(LIMIT)
        return SearchUi(asked = true, count = shown.size, days = MonthLedgerBuilder.groupByDay(shown), more = rows.size > LIMIT)
    }

    companion object {
        const val LIMIT = 100
    }
}

@Composable
fun SearchRoute(onBack: () -> Unit, onOpenEntry: (Long) -> Unit) {
    val container = LocalAppContainer.current
    val vm = viewModel { SearchViewModel(container.database) }
    val results by vm.results.collectAsStateWithLifecycle()
    val today by container.dayClock.today.collectAsStateWithLifecycle()
    SearchContent(vm.query, results, today, onBack, onOpenEntry)
}

@Composable
fun SearchContent(query: TextFieldState, results: SearchUi, today: LocalDate, onBack: () -> Unit, onOpenEntry: (Long) -> Unit) {
    val c = FitakaTheme.colors
    val type = MaterialTheme.typography
    val focus = remember { FocusRequester() }
    FitakaScreen(title = "Search", subtitle = "Across all your entries", onBack = onBack) {
        item(key = "query") {
            FitakaTextField(
                state = query, label = "Find", placeholder = "A note, category, wallet or amount",
                modifier = Modifier.focusRequester(focus),
            )
            // The screen exists to be typed into, so the keyboard opens with it.
            LaunchedEffect(Unit) { focus.requestFocus() }
        }
        when {
            !results.asked -> item(key = "hint") {
                FitakaCard(Modifier.fillMaxWidth()) {
                    EmptyState("Type to search", "For example: lunch, Food, GCash, or 250.")
                }
            }
            results.count == 0 -> item(key = "nothing") {
                FitakaCard(Modifier.fillMaxWidth()) {
                    EmptyState("Nothing found", "Try another word, or an amount like 250.")
                }
            }
            else -> item(key = "count") {
                Text(
                    if (results.more) "Showing the newest ${results.count}. Add a word to narrow it down." else "Found ${entriesText(results.count)}",
                    style = type.labelLarge, color = c.ink, modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
        dayGroups(results.days, today, onOpenEntry)
    }
}
