package com.pitakaview.app.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The app's single idea of "today". Screens and ViewModels read [today] instead of calling
 * LocalDate.now() themselves, so month totals and "Coming up" all move together when the date changes.
 */
class DayClock(private val now: () -> LocalDateTime = LocalDateTime::now) {
    private val _today = MutableStateFlow(now().toLocalDate())
    val today: StateFlow<LocalDate> = _today.asStateFlow()

    /** Re-reads the clock. Called when the app comes to the front and just after each midnight. */
    fun refresh() {
        _today.value = now().toLocalDate()
    }

    /** Time from now until one second past the next midnight. */
    fun millisUntilTomorrow(): Long {
        val current = now()
        return Duration.between(current, current.toLocalDate().plusDays(1).atStartOfDay()).toMillis() + 1_000
    }
}
