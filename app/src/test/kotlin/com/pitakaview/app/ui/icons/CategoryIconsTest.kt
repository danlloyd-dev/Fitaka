package com.pitakaview.app.ui.icons

import com.pitakaview.app.data.DefaultData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryIconsTest {
    @Test
    fun everyStarterCategoryHasItsOwnDrawnIcon() {
        val starters = DefaultData.expenseCategories + DefaultData.incomeCategories
        for ((name, key) in starters) {
            assertTrue("$name asks for the icon \"$key\", which is not drawn", CategoryIcons.has(key))
        }
        // Only the two called Other share one (the three dots); every other starter looks different.
        val named = starters.filter { it.first != "Other" }
        assertEquals(named.size, named.map { CategoryIcons.forKey(it.second) }.distinct().size)
        assertNotSame(CategoryIcons.forKey("food"), CategoryIcons.forKey(CategoryIcons.DEFAULT))
    }

    @Test
    fun everyIconCanBeDrawnAndIsNamedForAScreenReader() {
        assertEquals("each key is used once", CategoryIcons.all.size, CategoryIcons.all.map { it.key }.distinct().size)
        assertEquals("no two icons are read out the same", CategoryIcons.all.size, CategoryIcons.all.map { it.label }.distinct().size)
        for (icon in CategoryIcons.all) {
            assertTrue(icon.label.isNotBlank())
            // Building the image reads the icon's path data: a slip in it fails here, not on a screen.
            assertEquals("${icon.key} is drawn on the 24 by 24 grid", 24f, icon.image.viewportWidth)
            assertTrue("${icon.key} has something to draw", icon.image.root.size > 0)
        }
    }

    @Test
    fun aKeyThisVersionDoesNotKnowGetsTheThreeDots() {
        val dots = CategoryIcons.forKey(CategoryIcons.DEFAULT)
        assertSame(dots, CategoryIcons.forKey("an-icon-from-a-later-version"))
        assertSame(dots, CategoryIcons.forKey(null))
        assertFalse(CategoryIcons.has("an-icon-from-a-later-version"))
        assertFalse(CategoryIcons.has(null))
    }
}
