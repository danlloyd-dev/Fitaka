package com.pitakaview.app.lock

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.pitakaview.app.data.prefs.SettingsRepository
import com.pitakaview.app.ui.settings.LockSettingsText
import com.pitakaview.app.ui.settings.LockSettingsUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** When the app is locked, with a real settings file and a clock the test moves. */
class AppLockTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val storeJob = Job()
    private val settings by lazy {
        SettingsRepository(
            PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + storeJob), produceFile = { File(tmp.root, "settings.preferences_pb") }),
        )
    }
    private var nowMs = 1_000_000L

    private fun lock() = AppLock(settings, elapsedMs = { nowMs })

    @After
    fun close() = runBlocking { storeJob.cancelAndJoin() }

    @Test
    fun withTheLockOffTheAppIsNeverLocked() = runBlocking {
        val lock = lock()
        assertNull("not known until the saved choice is read", lock.locked.value)
        lock.load()
        assertEquals(false, lock.locked.value)

        lock.onLeft()
        nowMs += 10 * AppLockRules.AWAY_MS
        lock.onReturned()
        assertEquals(false, lock.locked.value)
    }

    @Test
    fun withTheLockOnTheAppStartsLockedAndOpensWhenThePhoneSaysYes() = runBlocking {
        settings.setLockOn(true)
        val lock = lock()
        lock.load()
        assertEquals(true, lock.locked.value)

        lock.onUnlocked()
        assertEquals(false, lock.locked.value)
        // Reading the choice again (a rotation makes a new screen, not a new process) does not lock it again.
        lock.load()
        assertEquals(false, lock.locked.value)
    }

    @Test
    fun itLocksAgainOnlyAfterAMinuteOutOfSight() = runBlocking {
        settings.setLockOn(true)
        val lock = lock()
        lock.load()
        lock.onUnlocked()

        // A short trip away (picking a file, answering a message).
        lock.onLeft()
        nowMs += AppLockRules.AWAY_MS - 1
        lock.onReturned()
        assertEquals(false, lock.locked.value)

        // A minute or more.
        lock.onLeft()
        nowMs += AppLockRules.AWAY_MS
        lock.onReturned()
        assertEquals(true, lock.locked.value)

        // Coming and going while locked changes nothing.
        lock.onLeft()
        nowMs += 5
        lock.onReturned()
        assertEquals(true, lock.locked.value)
    }

    @Test
    fun comingIntoSightWithoutHavingLeftDoesNotLock() = runBlocking {
        // A rotation: the new screen "starts" although the app never went away.
        settings.setLockOn(true)
        val lock = lock()
        lock.load()
        lock.onUnlocked()
        nowMs += 10 * AppLockRules.AWAY_MS
        lock.onReturned()
        assertEquals(false, lock.locked.value)
    }

    @Test
    fun switchingTheLockOnDoesNotLockAtOnceAndSwitchingItOffOpensTheApp() = runBlocking {
        val lock = lock()
        lock.load()

        lock.setOn(true)
        assertTrue(settings.lockOn.first())
        assertEquals("they have just proved who they are", false, lock.locked.value)
        // From now on it locks after a while away.
        lock.onLeft()
        nowMs += AppLockRules.AWAY_MS
        lock.onReturned()
        assertEquals(true, lock.locked.value)

        lock.setOn(false)
        assertFalse(settings.lockOn.first())
        assertEquals(false, lock.locked.value)
        lock.onLeft()
        nowMs += AppLockRules.AWAY_MS
        lock.onReturned()
        assertEquals(false, lock.locked.value)
    }

    @Test
    fun theRuleItself() {
        assertFalse(AppLockRules.locksOnReturn(lockOn = false, leftAtMs = 0, nowMs = AppLockRules.AWAY_MS * 2))
        assertFalse(AppLockRules.locksOnReturn(lockOn = true, leftAtMs = null, nowMs = AppLockRules.AWAY_MS * 2))
        assertFalse(AppLockRules.locksOnReturn(lockOn = true, leftAtMs = 0, nowMs = AppLockRules.AWAY_MS - 1))
        assertTrue(AppLockRules.locksOnReturn(lockOn = true, leftAtMs = 0, nowMs = AppLockRules.AWAY_MS))
    }

    @Test
    fun theSettingsSwitchCanAlwaysBeTurnedOffButOnlyOnWhenThePhoneCanLock() {
        assertTrue(LockSettingsText.canTap(LockSettingsUi(on = false, support = LockSupport.READY)))
        assertFalse(LockSettingsText.canTap(LockSettingsUi(on = false, support = LockSupport.NO_SCREEN_LOCK)))
        assertTrue("a lock left on is never stuck on", LockSettingsText.canTap(LockSettingsUi(on = true, support = LockSupport.NO_SCREEN_LOCK)))
    }
}
