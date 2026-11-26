package com.pitakaview.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.pitakaview.app.domain.ThemeId

/** The five app sections, each with a fixed colour in every theme. */
enum class Section(val label: String) {
    KITA("Kita"),
    BAON("Baon"),
    IPON("Ipon"),
    GASTOS("Gastos"),
    UTANG("Utang"),
}

/** One theme in one mode, exactly as in the owner's brief, plus the text colour used on primary. */
data class BaseColors(
    val background: Color,
    val primary: Color,
    val card: Color,
    val ink: Color,
    /**
     * Text on the primary colour. Chosen by WCAG contrast (ThemeContrastTest): the dark text colour
     * everywhere except Kape light, where white reads better. Ube and Kape light reach only 4.1–4.3:1,
     * so text on primary must stay large and bold (ButtonLabelStyle), where 3:1 is the AA bar.
     */
    val onPrimary: Color,
)

internal object Palette {

    fun base(theme: ThemeId, dark: Boolean): BaseColors = if (dark) darkOf(theme) else lightOf(theme)

    private fun lightOf(theme: ThemeId): BaseColors = when (theme) {
        ThemeId.MANGO -> BaseColors(Color(0xFFFFF8EC), Color(0xFFF5A623), Color(0xFFFFFFFF), Color(0xFF1E1E1E), Color(0xFF1E1E1E))
        ThemeId.UBE -> BaseColors(Color(0xFFF4EEFB), Color(0xFF9B6FD6), Color(0xFFFFFFFF), Color(0xFF2A1F3D), Color(0xFF2A1F3D))
        ThemeId.CALAMANSI -> BaseColors(Color(0xFFF6FBE9), Color(0xFF9CCC3D), Color(0xFFFFFFFF), Color(0xFF1F2A14), Color(0xFF1F2A14))
        ThemeId.DAGAT -> BaseColors(Color(0xFFEAF6F8), Color(0xFF2BA8B8), Color(0xFFFFFFFF), Color(0xFF0F2A33), Color(0xFF0F2A33))
        ThemeId.KAPE -> BaseColors(Color(0xFFF7F1EA), Color(0xFFA0703F), Color(0xFFFFFFFF), Color(0xFF2B1D12), Color(0xFFFFFFFF))
    }

    /** Dark mode: text on primary is the theme's dark background colour. */
    private fun darkOf(theme: ThemeId): BaseColors = when (theme) {
        ThemeId.MANGO -> BaseColors(Color(0xFF1E1A14), Color(0xFFFFB547), Color(0xFF2B251C), Color(0xFFFFF3E0), Color(0xFF1E1A14))
        ThemeId.UBE -> BaseColors(Color(0xFF1C1626), Color(0xFFB48CEB), Color(0xFF2A2238), Color(0xFFF1EAFB), Color(0xFF1C1626))
        ThemeId.CALAMANSI -> BaseColors(Color(0xFF161C10), Color(0xFFB5E05A), Color(0xFF222B19), Color(0xFFEEF6DC), Color(0xFF161C10))
        ThemeId.DAGAT -> BaseColors(Color(0xFF0F1E23), Color(0xFF3CC4D4), Color(0xFF183039), Color(0xFFE3F4F7), Color(0xFF0F1E23))
        ThemeId.KAPE -> BaseColors(Color(0xFF1C1611), Color(0xFFC99566), Color(0xFF2A211A), Color(0xFFF3E9DE), Color(0xFF1C1611))
    }

    fun section(section: Section, dark: Boolean): Color = if (dark) {
        when (section) {
            Section.KITA -> Color(0xFF5CC46A)
            Section.BAON -> Color(0xFFF5CF6B)
            Section.IPON -> Color(0xFF56B4EA)
            Section.GASTOS -> Color(0xFFF07560)
            Section.UTANG -> Color(0xFFA97DE8)
        }
    } else {
        when (section) {
            Section.KITA -> Color(0xFF3FA34D)
            Section.BAON -> Color(0xFFF2C14E)
            Section.IPON -> Color(0xFF2D9CDB)
            Section.GASTOS -> Color(0xFFE5533D)
            Section.UTANG -> Color(0xFF8E5BD9)
        }
    }
}
