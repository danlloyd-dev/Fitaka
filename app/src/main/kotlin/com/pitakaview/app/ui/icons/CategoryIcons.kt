package com.pitakaview.app.ui.icons

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * One drawn icon a category can wear. [key] is what the database stores for the category; [label] is
 * the icon's name for a screen reader in the picker.
 */
class CategoryIcon(val key: String, val label: String, build: () -> ImageVector) {
    /** Built the first time it is drawn: a screen that shows three categories builds three icons. */
    val image: ImageVector by lazy(build)
}

/**
 * The icons a Gastos or Kita category can have. Fourteen are hand-drawn ([HandDrawn]); the set has no
 * match for the other eight, which stay the app's own strokes with a lighter line.
 */
object CategoryIcons {
    /** What a category gets until one is picked, and what an unknown key falls back to: three dots. */
    const val DEFAULT = "other"

    private fun dot(x: Float, y: Float, r: Float) = "M${x - r} $y A$r $r 0 1 0 ${x + r} $y A$r $r 0 1 0 ${x - r} $y"

    private fun own(key: String, label: String, vararg paths: String) =
        CategoryIcon(key, label) { strokeIcon("Category-$key", *paths, width = STROKE_BESIDE_HAND_DRAWN) }

    /** In the order the picker shows them. The keys of the starter categories (DefaultData) are all here. */
    val all: List<CategoryIcon> = listOf(
        own("food", "Fork and knife", "M6 3 V8 Q6 11 8.5 11 Q11 11 11 8 V3", "M8.5 3 V21", "M17 21 V3 Q13.5 5.5 14 13 H17"),
        own("coffee", "Cup", "M5 9 H16 V15 Q16 19 12 19 H9 Q5 19 5 15 Z", "M16 10.5 H17.5 Q20 10.5 20 13 Q20 15.5 17.5 15.5 H16", "M8.5 3.5 V6", "M12.5 3.5 V6"),
        CategoryIcon("cart", "Shopping cart") { HandDrawn.Cart },
        CategoryIcon("bag", "Shopping bag") { HandDrawn.ShoppingBags },
        own("transport", "Bus", "M4 7 Q4 5 6 5 H18 Q20 5 20 7 V16 H4 Z", "M4 10.5 H20", dot(8f, 18f, 2f), dot(16f, 18f, 2f)),
        CategoryIcon("plane", "Paper plane") { HandDrawn.PaperPlane },
        CategoryIcon("bills", "Lightning") { HandDrawn.Lightning },
        CategoryIcon("water", "Tap and basin") { HandDrawn.Tap },
        CategoryIcon("phone", "Phone") { HandDrawn.Phone },
        CategoryIcon("home", "House") { HandDrawn.Home },
        own("health", "Medical cross", "M5 7 Q5 5 7 5 H17 Q19 5 19 7 V17 Q19 19 17 19 H7 Q5 19 5 17 Z", "M12 8.5 V15.5", "M8.5 12 H15.5"),
        own("family", "People", dot(8.5f, 8f, 2.5f), "M3.5 19 Q3.5 13.5 8.5 13.5 Q13.5 13.5 13.5 19", dot(16.5f, 9f, 2f), "M16 14 Q20.5 14 20.5 19"),
        own("heart", "Heart", "M12 20 Q3.5 14 3.5 8.5 Q3.5 5 7 5 Q10 5 12 8 Q14 5 17 5 Q20.5 5 20.5 8.5 Q20.5 14 12 20 Z"),
        CategoryIcon("pet", "Cat") { HandDrawn.Cat },
        CategoryIcon("fun", "Game controller") { HandDrawn.GameController },
        CategoryIcon("school", "Book") { HandDrawn.Book },
        own("shirt", "Shirt", "M8.5 4 L4 7.5 L6.5 11 L8 10 V20 H16 V10 L17.5 11 L20 7.5 L15.5 4 Q12 7 8.5 4 Z"),
        own("gift", "Gift", "M3.5 8 H20.5 V11.5 H3.5 Z", "M5 11.5 V20 H19 V11.5", "M12 8 V20", "M12 8 Q8 2.5 6.5 5.5 Q5.5 8 12 8 Q18.5 8 17.5 5.5 Q16 2.5 12 8"),
        CategoryIcon("salary", "Banknote") { HandDrawn.Banknote },
        CategoryIcon("sideline", "Laptop") { HandDrawn.Laptop },
        CategoryIcon("coin", "Peso coin") { HandDrawn.PesoCoin },
        CategoryIcon(DEFAULT, "Three dots") { HandDrawn.Dots },
    )

    private val byKey = all.associateBy { it.key }

    /** True when [key] names one of the icons. */
    fun has(key: String?): Boolean = key in byKey

    /**
     * The icon for a stored key. A key this version does not know (a category restored from a backup
     * made by a later version) gets the three dots, never a blank.
     */
    fun forKey(key: String?): ImageVector = (byKey[key] ?: byKey.getValue(DEFAULT)).image
}
