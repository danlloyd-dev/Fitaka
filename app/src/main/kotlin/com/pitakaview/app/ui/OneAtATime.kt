package com.pitakaview.app.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * One write at a time for a screen's Save (or Import) button.
 *
 * Found in the review before 1.0 (2026-11-27) and proven by a test: a second tap on Save, either at once
 * or while the screen was on its way out, started a second write, and one ₱250.00 expense became two.
 * Every editor saved the same way, so every editor now saves through this.
 *
 * A second call while a write is going does nothing. A write that finishes the screen (it returns true)
 * shuts the door for good: the screen is leaving and must take no more. One that does not (the ledger
 * refused the row) opens it again, so the owner can correct and save.
 *
 * Called from the main thread only, as view models are.
 */
class OneAtATime {
    private var busy = false

    /** Starts [work] unless a write from this screen is still going or has already finished the screen. */
    fun launch(scope: CoroutineScope, work: suspend () -> Boolean) {
        if (busy) return
        busy = true
        scope.launch {
            var finished = false
            try {
                finished = work()
            } finally {
                if (!finished) busy = false
            }
        }
    }
}
