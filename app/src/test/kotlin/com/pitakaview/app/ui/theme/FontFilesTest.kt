package com.pitakaview.app.ui.theme

import com.pitakaview.app.domain.FontChoice
import com.pitakaview.app.ui.settings.AboutText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Font
import java.io.File

/**
 * The font files are cut down to the characters the app shows. A cut that lost the peso sign or ñ would
 * not fail a build: those letters would quietly be drawn in the phone's own font. This reads the files
 * themselves.
 */
class FontFilesTest {
    private val folder = File("src/main/res/font")

    /** File name to the family its name table gives. */
    private val files = mapOf(
        "shantell_sans_regular.ttf" to "Shantell Sans", "shantell_sans_semibold.ttf" to "Shantell Sans", "shantell_sans_bold.ttf" to "Shantell Sans",
        "mali_regular.ttf" to "Mali", "mali_semibold.ttf" to "Mali", "mali_bold.ttf" to "Mali",
        "pangolin_regular.ttf" to "Pangolin",
        "fuzzy_bubbles_regular.ttf" to "Fuzzy Bubbles", "fuzzy_bubbles_bold.ttf" to "Fuzzy Bubbles",
        "itim_regular.ttf" to "Itim",
    )

    @Test
    fun onlyTheFilesOfTheFiveFontsAreBundledAndEachIsCutDown() {
        assertEquals(files.keys.sorted(), folder.list()!!.sorted())
        // The cut is what keeps the app small: as downloaded they are 100 to 570 KB each, 2.6 MB together.
        for (name in files.keys) assertTrue("$name is ${File(folder, name).length()} bytes", File(folder, name).length() in 30_000..200_000)
        val together = files.keys.sumOf { File(folder, it).length() }
        assertTrue("the ten files are $together bytes together", together < 1_050_000)
    }

    @Test
    fun everyFileDrawsTheLettersFiguresAndSignsTheAppShows() {
        val needed = ('a'..'z') + ('A'..'Z') + ('0'..'9') + "₱ñÑ−·—…’“”&%+-.,:;!?()/'\"<>=".toList()
        for ((name, family) in files) {
            val font = Font.createFont(Font.TRUETYPE_FONT, File(folder, name))
            assertTrue("$name is ${font.family}, not $family", font.family.startsWith(family))
            val missing = needed.filterNot { font.canDisplay(it) }
            assertTrue("$name cannot draw ${missing.joinToString(" ")}", missing.isEmpty())
        }
    }

    @Test
    fun everyFontOfferedInSettingsHasItsOwnFiles() {
        assertEquals(5, FontChoice.entries.size)
        assertEquals("no two choices are the same font", 5, FontChoice.entries.map { it.family() }.distinct().size)
        assertEquals("the default is the font the app had", FontChoice.SHANTELL, FontChoice.entries.first())
        assertEquals(ShantellSans, FontChoice.SHANTELL.family())
        for (choice in FontChoice.entries) assertTrue("${choice.label} has files", files.values.any { it == choice.label })
        assertEquals(files.values.toSet(), FontChoice.entries.map { it.label }.toSet())
    }

    /** Every licence asks for its notice to travel with the app, and the icons' asks to be told they were changed. */
    @Test
    fun theLicencesTravelWithTheApp() {
        val licences = File("src/main/assets/licenses")
        assertEquals(
            listOf("Freehand-icons.txt", "FuzzyBubbles-OFL.txt", "Itim-OFL.txt", "Mali-OFL.txt", "Pangolin-OFL.txt", "ShantellSans-OFL.txt"),
            licences.list()!!.sorted(),
        )
        for (name in licences.list()!!.filter { it.endsWith("-OFL.txt") }) {
            assertTrue(name, File(licences, name).readText().contains("SIL Open Font License, Version 1.1"))
        }
        val icons = File(licences, "Freehand-icons.txt").readText()
        for (part in listOf("streamlinehq.com", "CC BY 4.0", "creativecommons.org/licenses/by/4.0", "Changes made")) assertTrue(part, icons.contains(part))
        for (choice in FontChoice.entries) assertTrue("About names ${choice.label}", AboutText.FONT.contains(choice.label))
        assertTrue(AboutText.FONT.contains("SIL Open Font License 1.1"))
        for (part in listOf("Streamline Freehand", "streamlinehq.com", "CC BY 4.0", "redrawn")) assertTrue(part, AboutText.ICONS.contains(part))
    }
}
