package com.pitakaview.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Simple round-stroke icons drawn in code (24×24), so the app needs no icon library.
 * Black strokes are recoloured by Icon's tint.
 */
object PitakaIcons {
    val Home by lazy { strokeIcon("Home", "M4 10.5 L12 4 L20 10.5 V20 H14.5 V14.5 H9.5 V20 H4 Z") }

    /** Kita: money coming in. */
    val Kita by lazy { strokeIcon("Kita", "M4 14 V19 H20 V14", "M12 4 V14", "M8 10 L12 14 L16 10") }

    /** Baon: a lunch box. */
    val Baon by lazy { strokeIcon("Baon", "M4 9 H20 V19 H4 Z", "M9 9 V6 H15 V9", "M4 13.5 H20") }

    /** Ipon: an alkansya jar with a coin slot. */
    val Ipon by lazy {
        strokeIcon("Ipon", "M7 8 H17 V18.5 Q17 20 15.5 20 H8.5 Q7 20 7 18.5 Z", "M8.5 4.5 H15.5 V8 H8.5 Z", "M10 12.5 H14")
    }

    /** Gastos: a receipt. */
    val Gastos by lazy {
        strokeIcon(
            "Gastos",
            "M6 3 H18 V21 L16 19.5 L14 21 L12 19.5 L10 21 L8 19.5 L6 21 Z",
            "M9 8 H15",
            "M9 12 H15",
            "M9 16 H12",
        )
    }

    /** Utang: money changing hands. */
    val Utang by lazy { strokeIcon("Utang", "M4 8 H17", "M14 5 L17 8 L14 11", "M20 16 H7", "M10 13 L7 16 L10 19") }

    val Wallet by lazy {
        strokeIcon("Wallet", "M4 7 H18 Q20 7 20 9 V18 Q20 20 18 20 H6 Q4 20 4 18 Z", "M4 7 Q4 4 7 4 H16 V7", "M15 13.5 H17")
    }

    val Settings by lazy {
        strokeIcon(
            "Settings",
            "M4 7 H6.5", "M11.5 7 H20", "M6.5 7 A2.5 2.5 0 1 0 11.5 7 A2.5 2.5 0 1 0 6.5 7",
            "M4 17 H12.5", "M17.5 17 H20", "M12.5 17 A2.5 2.5 0 1 0 17.5 17 A2.5 2.5 0 1 0 12.5 17",
        )
    }

    val Add by lazy { strokeIcon("Add", "M12 5 V19", "M5 12 H19") }
    val Back by lazy { strokeIcon("Back", "M19 12 H5", "M11 6 L5 12 L11 18") }
    val Check by lazy { strokeIcon("Check", "M5 12.5 L10 17.5 L19 7") }
    val ChevronRight by lazy { strokeIcon("ChevronRight", "M9 6 L15 12 L9 18") }
}

/** The icon that stands for each section. */
val com.pitakaview.app.ui.theme.Section.icon: ImageVector
    get() = when (this) {
        com.pitakaview.app.ui.theme.Section.KITA -> PitakaIcons.Kita
        com.pitakaview.app.ui.theme.Section.BAON -> PitakaIcons.Baon
        com.pitakaview.app.ui.theme.Section.IPON -> PitakaIcons.Ipon
        com.pitakaview.app.ui.theme.Section.GASTOS -> PitakaIcons.Gastos
        com.pitakaview.app.ui.theme.Section.UTANG -> PitakaIcons.Utang
    }

private fun strokeIcon(name: String, vararg paths: String): ImageVector =
    ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .apply {
            paths.forEach {
                addPath(
                    pathData = addPathNodes(it),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2.2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }
        .build()
