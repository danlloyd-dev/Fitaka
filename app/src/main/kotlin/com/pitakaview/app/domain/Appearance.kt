package com.pitakaview.app.domain

/** The five colour themes. Stored by name in DataStore. */
enum class ThemeId(val label: String) {
    MANGO("Mango"),
    UBE("Ube"),
    CALAMANSI("Calamansi"),
    DAGAT("Dagat"),
    KAPE("Kape"),
}

enum class DarkModePref { SYSTEM, LIGHT, DARK }

data class AppearanceSettings(
    val theme: ThemeId = ThemeId.MANGO,
    val darkMode: DarkModePref = DarkModePref.SYSTEM,
)
