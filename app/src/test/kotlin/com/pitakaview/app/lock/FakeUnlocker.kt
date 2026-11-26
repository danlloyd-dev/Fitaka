package com.pitakaview.app.lock

import android.app.Activity

/** Stands in for the phone's own unlock question: it answers at once with whatever the test last set. */
class FakeUnlocker(
    var answer: UnlockResult = UnlockResult.NOT_UNLOCKED,
    var support: LockSupport = LockSupport.READY,
) : Unlocker {
    /** The title of every question that was put to the phone, in order. */
    val asked = mutableListOf<String>()

    override fun support() = support

    override fun ask(activity: Activity, title: String, onResult: (UnlockResult) -> Unit) {
        asked += title
        onResult(answer)
    }
}
