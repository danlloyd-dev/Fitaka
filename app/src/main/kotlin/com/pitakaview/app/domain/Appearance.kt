package com.pitakaview.app.domain

/** The six colour themes. Stored by name in DataStore. */
enum class ThemeId(val label: String) {
    MANGO("Mango"),
    UBE("Ube"),
    CALAMANSI("Calamansi"),
    DAGAT("Dagat"),
    KAPE("Kape"),

    /** Pink ("rosas" is the word for both the rose and its colour). */
    ROSAS("Rosas"),
}

enum class DarkModePref { SYSTEM, LIGHT, DARK }

/**
 * How large everything but the bottom bar is drawn: text, icons, buttons and the room between them all
 * shrink together by [scale]. Large is the size the app always had, and stays the default.
 */
enum class UiSize(val label: String, val scale: Float) {
    SMALL("Small", 0.85f),
    MEDIUM("Medium", 0.925f),
    LARGE("Large", 1f),
}

/**
 * The handwriting the app is set in. Each one draws the peso sign and ñ itself: of 39 handwritten fonts
 * checked, only seven do. Shantell Sans is the default.
 */
enum class FontChoice(val label: String) {
    SHANTELL("Shantell Sans"),
    MALI("Mali"),
    PANGOLIN("Pangolin"),
    FUZZY_BUBBLES("Fuzzy Bubbles"),
    ITIM("Itim"),
}

/** How the parts are finished. Glossy is the default. */
enum class UiStyle(val label: String) {
    /** Flat colour inside the outlines. */
    PLAIN("Plain"),

    /** A paler band and a white glint on every solid-coloured part, like a jelly sweet. */
    GLOSSY("Glossy"),

    /** Cards and buttons you can see through, over soft colour on the page. */
    GLASSY("Glassy"),

    /** Outlines drawn by an unsteady hand, twice over, like a pencil sketch. */
    SKETCH("Sketch"),
}

data class AppearanceSettings(
    val theme: ThemeId = ThemeId.MANGO,
    val darkMode: DarkModePref = DarkModePref.SYSTEM,
    val size: UiSize = UiSize.LARGE,
    val font: FontChoice = FontChoice.SHANTELL,
    val style: UiStyle = UiStyle.GLOSSY,
)
