package com.pitakaview.app.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Test

/** The rule every Save button goes through: one write at a time, and none after the one that finished the screen. */
class OneAtATimeTest {
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @Test
    fun aSecondCallWhileOneIsGoingDoesNothing() {
        val once = OneAtATime()
        val gate = CompletableDeferred<Unit>()
        var started = 0
        repeat(3) {
            once.launch(scope) {
                started++
                gate.await()
                true
            }
        }
        assertEquals("three taps, one write", 1, started)
        gate.complete(Unit)
    }

    @Test
    fun afterAWriteThatFinishedTheScreenNothingMoreRuns() {
        val once = OneAtATime()
        var runs = 0
        once.launch(scope) { runs++; true }
        once.launch(scope) { runs++; true }
        assertEquals(1, runs)
    }

    @Test
    fun afterAWriteThatWasRefusedTheOwnerCanSaveAgain() {
        val once = OneAtATime()
        var runs = 0
        once.launch(scope) { runs++; false }
        once.launch(scope) { runs++; true }
        assertEquals("refused, corrected, saved", 2, runs)
    }

    @Test
    fun aWriteThatThrowsDoesNotLeaveTheButtonDead() {
        val once = OneAtATime()
        var runs = 0
        // As a view model's scope is: one write that breaks does not end the scope for the next.
        val quiet = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined + CoroutineExceptionHandler { _, _ -> })
        once.launch(quiet) { runs++; error("the disk is full") }
        once.launch(quiet) { runs++; true }
        assertEquals(2, runs)
    }
}
