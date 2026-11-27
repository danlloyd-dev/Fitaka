package com.pitakaview.app

/**
 * The app's name wherever people read it: Home's title, the lock screen, the fingerprint question, the
 * messages that name the app. Fitaka is short for "Financial + Pitaka".
 *
 * Android wants the launcher's label as a resource, so the same word is also in res/values/strings.xml
 * (`app_name`); AppNameTest keeps the two the same.
 *
 * Until 2026-11-27 the app was called PitakaView. The names that were already on phones then are kept
 * as they were, because changing any of them would lose or double what is stored under it: the
 * application id and this package (`com.pitakaview.app`), the database file, the settings file, the
 * reminder job's name, the notification channel's id. StoredNamesTest holds each of them.
 */
const val APP_NAME = "Fitaka"
