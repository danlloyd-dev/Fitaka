package com.pitakaview.app.ui.theme

import com.pitakaview.app.ui.settings.AboutText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Font
import java.io.File

/**
 * The three font files are cut down to the characters the app shows. A cut that lost the peso sign or
 * ñ would not fail a build: those letters would quietly be drawn in the phone's own font. This reads
 * the files themselves.
 */
class FontFilesTest {
    private val folder = File("src/main/res/font")
    private val files = listOf("shantell_sans_regular.ttf", "shantell_sans_semibold.ttf", "shantell_sans_bold.ttf")

    @Test
    fun onlyTheThreeShantellSansFilesAreBundled() {
        assertEquals(files.sorted(), folder.list()!!.sorted())
        // The cut is what keeps the app small: the full files are 393 KB each.
        for (name in files) assertTrue("$name is ${File(folder, name).length()} bytes", File(folder, name).length() in 100_000..200_000)
    }

    @Test
    fun everyFileDrawsTheLettersFiguresAndSignsTheAppShows() {
        val needed = ('a'..'z') + ('A'..'Z') + ('0'..'9') + "₱ñÑ−→·—…’“”&%+-.,:;!?()/'\"<>=•×".toList()
        for (name in files) {
            val font = Font.createFont(Font.TRUETYPE_FONT, File(folder, name))
            assertTrue("$name is ${font.family}", font.family.startsWith("Shantell Sans"))
            val missing = needed.filterNot { font.canDisplay(it) }
            assertTrue("$name cannot draw ${missing.joinToString(" ")}", missing.isEmpty())
        }
    }

    /** Both licences ask for their notice to travel with the app, and the icons' asks to be told they were changed. */
    @Test
    fun theLicencesTravelWithTheApp() {
        val licences = File("src/main/assets/licenses")
        assertEquals(listOf("Freehand-icons.txt", "ShantellSans-OFL.txt"), licences.list()!!.sorted())
        assertTrue(File(licences, "ShantellSans-OFL.txt").readText().contains("SIL Open Font License, Version 1.1"))
        val icons = File(licences, "Freehand-icons.txt").readText()
        for (part in listOf("streamlinehq.com", "CC BY 4.0", "creativecommons.org/licenses/by/4.0", "Changes made")) assertTrue(part, icons.contains(part))
        for (part in listOf("Shantell Sans", "SIL Open Font License 1.1")) assertTrue(part, AboutText.FONT.contains(part))
        for (part in listOf("Streamline Freehand", "streamlinehq.com", "CC BY 4.0", "redrawn")) assertTrue(part, AboutText.ICONS.contains(part))
    }
}
