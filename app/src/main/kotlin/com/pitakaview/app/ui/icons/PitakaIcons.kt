package com.pitakaview.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The app's icons (24×24), all built in code, so the app needs no icon library. Black is recoloured by
 * Icon's tint.
 *
 * Most are hand-drawn ([HandDrawn], from Streamline Freehand). The small arrows and the tick stay plain
 * round strokes: they are controls, and read best as simple lines.
 */
object PitakaIcons {
    val Home: ImageVector get() = HandDrawn.Home

    /** Kita: a bag of money. */
    val Kita: ImageVector get() = HandDrawn.MoneyBag

    /** Baon: a coin purse. */
    val Baon: ImageVector get() = HandDrawn.CoinPurse

    /** Ipon: an alkansya. */
    val Ipon: ImageVector get() = HandDrawn.PiggyBank

    /** Gastos: a receipt. */
    val Gastos: ImageVector get() = HandDrawn.Receipt

    /** Utang: money changing hands. */
    val Utang: ImageVector get() = HandDrawn.Exchange

    val Wallet: ImageVector get() = HandDrawn.Wallet
    val Settings: ImageVector get() = HandDrawn.Cog
    val Add: ImageVector get() = HandDrawn.Plus
    val Search: ImageVector get() = HandDrawn.Magnifier

    val Back by lazy { strokeIcon("Back", "M19 12 H5", "M11 6 L5 12 L11 18") }
    val Check by lazy { strokeIcon("Check", "M5 12.5 L10 17.5 L19 7") }
    val ChevronRight by lazy { strokeIcon("ChevronRight", "M9 6 L15 12 L9 18") }
    val ChevronLeft by lazy { strokeIcon("ChevronLeft", "M15 6 L9 12 L15 18") }
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

/** The line of the app's own stroke icons. */
internal const val STROKE = 2.2f

/**
 * The line of the eight category icons the hand-drawn set has no match for (food, bus, health, family,
 * gift, coffee, heart, shirt): lighter, so they sit beside the hand-drawn ones.
 */
internal const val STROKE_BESIDE_HAND_DRAWN = 1.7f

internal fun strokeIcon(name: String, vararg paths: String, width: Float = STROKE): ImageVector =
    ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .apply {
            paths.forEach {
                addPath(
                    pathData = addPathNodes(it),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = width,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }
        .build()

/** One piece of a hand-drawn icon: a filled outline ([Shape]), or a line drawn with a round pen ([PenLine]). */
internal sealed interface Piece

internal class Shape(val path: String, val evenOdd: Boolean = false) : Piece

internal class PenLine(val path: String, val width: Float) : Piece

private const val HAND_DRAWN = "HandDrawn-"

/** Builds a hand-drawn icon from its pieces, in the order given. */
internal fun drawnIcon(name: String, vararg pieces: Piece): ImageVector =
    ImageVector.Builder(name = HAND_DRAWN + name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .apply {
            pieces.forEach { piece ->
                when (piece) {
                    is Shape -> addPath(
                        pathData = addPathNodes(piece.path),
                        fill = SolidColor(Color.Black),
                        pathFillType = if (piece.evenOdd) PathFillType.EvenOdd else PathFillType.NonZero,
                    )
                    is PenLine -> addPath(
                        pathData = addPathNodes(piece.path),
                        stroke = SolidColor(Color.Black),
                        strokeLineWidth = piece.width,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    )
                }
            }
        }
        .build()

/** True for the hand-drawn icons, whose lines are thinner than the app's own strokes. */
val ImageVector.isHandDrawn: Boolean get() = name.startsWith(HAND_DRAWN)

/**
 * The size to draw this icon at where a stroke icon would be [base]: hand-drawn icons are shown a little
 * larger ([more]) because their lines are thinner (26 in a 40 badge where a stroke icon is 22).
 */
fun ImageVector.sizeFor(base: Dp, more: Dp = 4.dp): Dp = if (isHandDrawn) base + more else base
