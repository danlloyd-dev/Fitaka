package com.pitakaview.app.ui.icons

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * One drawn icon a category can wear. [key] is what the database stores for the category; [label] is
 * the icon's name for a screen reader in the picker.
 */
class CategoryIcon(val key: String, val label: String, vararg paths: String) {
    /** Built the first time it is drawn: a screen that shows three categories builds three icons. */
    val image: ImageVector by lazy { strokeIcon("Category-$key", *paths) }
}

/** The icons a Gastos or Kita category can have, in the same round-stroke style as the section icons. */
object CategoryIcons {
    /** What a category gets until one is picked, and what an unknown key falls back to: three dots. */
    const val DEFAULT = "other"

    private fun dot(x: Float, y: Float, r: Float) = "M${x - r} $y A$r $r 0 1 0 ${x + r} $y A$r $r 0 1 0 ${x - r} $y"

    /** In the order the picker shows them. The keys of the starter categories (DefaultData) are all here. */
    val all: List<CategoryIcon> = listOf(
        CategoryIcon("food", "Fork and knife", "M6 3 V8 Q6 11 8.5 11 Q11 11 11 8 V3", "M8.5 3 V21", "M17 21 V3 Q13.5 5.5 14 13 H17"),
        CategoryIcon("coffee", "Cup", "M5 9 H16 V15 Q16 19 12 19 H9 Q5 19 5 15 Z", "M16 10.5 H17.5 Q20 10.5 20 13 Q20 15.5 17.5 15.5 H16", "M8.5 3.5 V6", "M12.5 3.5 V6"),
        CategoryIcon("cart", "Shopping cart", "M3 4 H5.5 L8 15 H18 L20 7.5 H6.5", dot(9.5f, 19f, 1.3f), dot(16.5f, 19f, 1.3f)),
        CategoryIcon("bag", "Shopping bag", "M5.5 8 H18.5 L19.5 20 H4.5 Z", "M9 8 V7 Q9 4 12 4 Q15 4 15 7 V8"),
        CategoryIcon("transport", "Bus", "M4 7 Q4 5 6 5 H18 Q20 5 20 7 V16 H4 Z", "M4 10.5 H20", dot(8f, 18f, 2f), dot(16f, 18f, 2f)),
        CategoryIcon("plane", "Paper plane", "M3.5 11 L20.5 4 L15 20 L11.5 13 Z", "M11.5 13 L20.5 4"),
        CategoryIcon("bills", "Lightning", "M13 3 L6 13.5 H12 L11 21 L18 10.5 H12 Z"),
        CategoryIcon("water", "Water drop", "M12 3.5 Q5.5 11 5.5 15 Q5.5 20.5 12 20.5 Q18.5 20.5 18.5 15 Q18.5 11 12 3.5 Z"),
        CategoryIcon("phone", "Phone", "M8 3 H16 Q17.5 3 17.5 4.5 V19.5 Q17.5 21 16 21 H8 Q6.5 21 6.5 19.5 V4.5 Q6.5 3 8 3 Z", "M10.5 17.5 H13.5"),
        CategoryIcon("home", "House", "M4 10.5 L12 4 L20 10.5 V20 H14.5 V14.5 H9.5 V20 H4 Z"),
        CategoryIcon("health", "Medical cross", "M5 7 Q5 5 7 5 H17 Q19 5 19 7 V17 Q19 19 17 19 H7 Q5 19 5 17 Z", "M12 8.5 V15.5", "M8.5 12 H15.5"),
        CategoryIcon("family", "People", dot(8.5f, 8f, 2.5f), "M3.5 19 Q3.5 13.5 8.5 13.5 Q13.5 13.5 13.5 19", dot(16.5f, 9f, 2f), "M16 14 Q20.5 14 20.5 19"),
        CategoryIcon("heart", "Heart", "M12 20 Q3.5 14 3.5 8.5 Q3.5 5 7 5 Q10 5 12 8 Q14 5 17 5 Q20.5 5 20.5 8.5 Q20.5 14 12 20 Z"),
        CategoryIcon("pet", "Paw", "M8 17.5 Q8 13.5 12 13.5 Q16 13.5 16 17.5 Q16 20 12 19 Q8 20 8 17.5 Z", dot(6.5f, 11.5f, 1.3f), dot(10f, 7.5f, 1.3f), dot(14f, 7.5f, 1.3f), dot(17.5f, 11.5f, 1.3f)),
        CategoryIcon("fun", "Star", "M12 3.5 L14.5 9 L20.5 9.7 L16 13.8 L17.3 19.8 L12 16.7 L6.7 19.8 L8 13.8 L3.5 9.7 L9.5 9 Z"),
        CategoryIcon("school", "Book", "M4 5.5 Q8 4 12 6.5 Q16 4 20 5.5 V18.5 Q16 17 12 19.5 Q8 17 4 18.5 Z", "M12 6.5 V19.5"),
        CategoryIcon("shirt", "Shirt", "M8.5 4 L4 7.5 L6.5 11 L8 10 V20 H16 V10 L17.5 11 L20 7.5 L15.5 4 Q12 7 8.5 4 Z"),
        CategoryIcon("gift", "Gift", "M3.5 8 H20.5 V11.5 H3.5 Z", "M5 11.5 V20 H19 V11.5", "M12 8 V20", "M12 8 Q8 2.5 6.5 5.5 Q5.5 8 12 8 Q18.5 8 17.5 5.5 Q16 2.5 12 8"),
        CategoryIcon("salary", "Banknote", "M3 7 H21 V17 H3 Z", dot(12f, 12f, 2.5f), "M6.5 12 H6.6", "M17.4 12 H17.5"),
        CategoryIcon("sideline", "Laptop", "M5 6 H19 V15 H5 Z", "M3 18.5 H21"),
        CategoryIcon("coin", "Peso coin", dot(12f, 12f, 8.5f), "M10 8 V16", "M10 8 H12.5 Q15 8 15 10.5 Q15 13 12.5 13 H10", "M8.5 10.5 H16"),
        CategoryIcon(DEFAULT, "Three dots", dot(6f, 12f, 1.2f), dot(12f, 12f, 1.2f), dot(18f, 12f, 1.2f)),
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
