package com.pitakaview.app.domain

import com.pitakaview.app.data.prefs.ReminderCheck
import com.pitakaview.app.ui.settings.ReminderSettingsText
import com.pitakaview.app.ui.settings.ReminderSwitch
import com.pitakaview.app.ui.settings.ReminderSwitchStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The "Remind me" switch on a payday and on a loan, and the words Settings uses about reminders. */
class RemindSwitchTest {
    private val now = Instant.parse("2026-11-26T04:00:00Z")
    private val manila = ZoneId.of("Asia/Manila")

    @Test
    fun aPaydaysRemindSwitchIsSavedAndReadBack() {
        val form = PayScheduleForm(name = "Sahod", amountText = "15000", walletId = 1, categoryId = 2)
        assertTrue("on unless the owner says otherwise", PayScheduleFormLogic.toEntity(form, null, now).remind)

        val off = PayScheduleFormLogic.toEntity(form.copy(remind = false), null, now)
        assertFalse(off.remind)
        assertFalse(PayScheduleFormLogic.fromEntity(off).remind)

        // Editing something else leaves the switch as it was; switching it back on is saved too.
        val renamed = PayScheduleFormLogic.toEntity(PayScheduleFormLogic.fromEntity(off).copy(name = "Sweldo"), off, now)
        assertFalse(renamed.remind)
        assertTrue(PayScheduleFormLogic.toEntity(PayScheduleFormLogic.fromEntity(off).copy(remind = true), off, now).remind)
    }

    @Test
    fun aLoansRemindSwitchIsSavedAndReadBack() {
        val form = LoanForm(who = "Tita Baby", amountText = "10000", startDate = LocalDate.of(2026, 11, 26), plan = LoanPlan.ONE_DATE, dueDate = LocalDate.of(2026, 12, 5))
        assertTrue("on unless the owner says otherwise", LoanFormLogic.toEntity(form, null, now).remind)

        val off = LoanFormLogic.toEntity(form.copy(remind = false), null, now)
        assertFalse(off.remind)
        assertFalse(LoanFormLogic.fromEntity(off).remind)

        val renamed = LoanFormLogic.toEntity(LoanFormLogic.fromEntity(off).copy(who = "Tita B"), off, now)
        assertFalse(renamed.remind)
        assertTrue(LoanFormLogic.toEntity(LoanFormLogic.fromEntity(off).copy(remind = true), off, now).remind)
    }

    @Test
    fun settingsSaysWhenAndHowRemindersWereLastLookedFor() {
        val today = LocalDate.of(2026, 11, 27)
        // 00:02 UTC is 8:02 in the morning in Manila.
        val thisMorning = Instant.parse("2026-11-27T00:02:00Z")
        assertEquals(
            "Last checked today at 8:02 AM, in the background.",
            ReminderSettingsText.lastCheck(ReminderCheck(thisMorning, inBackground = true), today, manila),
        )
        assertEquals(
            "Last checked yesterday at 3:30 PM, with the app open.",
            ReminderSettingsText.lastCheck(ReminderCheck(Instant.parse("2026-11-26T07:30:00Z"), inBackground = false), today, manila),
        )
        assertEquals(
            "Last checked on Nov 20 at 8:00 AM, in the background.",
            ReminderSettingsText.lastCheck(ReminderCheck(Instant.parse("2026-11-20T00:00:00Z"), inBackground = true), today, manila),
        )
    }

    @Test
    fun theRemindersSwitchAsksThePhoneOnlyWhenItMustAndObeysTheAnswer() {
        // Switching off never asks anything.
        assertEquals(ReminderSwitchStep.TURN_OFF, ReminderSwitch.onTap(wanted = false, mustAsk = true, phoneAllows = false))
        // Switching on: ask first if the phone has not been asked; otherwise go by what the phone allows now.
        assertEquals(ReminderSwitchStep.ASK_THE_PHONE, ReminderSwitch.onTap(wanted = true, mustAsk = true, phoneAllows = false))
        assertEquals(ReminderSwitchStep.TURN_ON, ReminderSwitch.onTap(wanted = true, mustAsk = false, phoneAllows = true))
        assertEquals(ReminderSwitchStep.SAY_BLOCKED, ReminderSwitch.onTap(wanted = true, mustAsk = false, phoneAllows = false))
        // The phone's answer decides: reminders that could never be shown are not switched on.
        assertEquals(ReminderSwitchStep.TURN_ON, ReminderSwitch.onAnswer(granted = true))
        assertEquals(ReminderSwitchStep.SAY_BLOCKED, ReminderSwitch.onAnswer(granted = false))
    }

    @Test
    fun theBackgroundTipIsOnlyForPhonesThatNeedIt() {
        assertTrue(ReminderSettingsText.needsPhoneTip("Xiaomi"))
        assertTrue(ReminderSettingsText.needsPhoneTip("xiaomi"))
        assertFalse(ReminderSettingsText.needsPhoneTip("Google"))
        assertFalse(ReminderSettingsText.needsPhoneTip("samsung"))
    }
}
