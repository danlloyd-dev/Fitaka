package com.pitakaview.app

import android.content.ComponentName
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The app's screen asks to be resized for the on-screen keyboard. On Android 10 and older the app is told
 * the keyboard's size only then, and the Save footer rides above the keyboard by that size. Found missing
 * in the review before 1.0 (2026-11-27); the owner's phone (Android 16) did not need it, older ones do.
 */
@RunWith(RobolectricTestRunner::class)
class KeyboardSettingTest {
    @Test
    fun theScreenIsResizedForTheKeyboard() {
        val app = ApplicationProvider.getApplicationContext<FitakaApp>()
        val activity = app.packageManager.getActivityInfo(ComponentName(app, MainActivity::class.java), 0)
        assertEquals(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE, activity.softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST)
    }
}
