package com.pitakaview.app.ui.summary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLineComponent
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.ColumnCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerController
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerVisibilityListener
import com.patrykandpatrick.vico.compose.cartesian.marker.ColumnCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.LineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import com.pitakaview.app.domain.Peso
import com.pitakaview.app.domain.UiStyle
import com.pitakaview.app.ui.components.sketchBox
import com.pitakaview.app.ui.ledger.monthName
import com.pitakaview.app.ui.ledger.monthTitle
import com.pitakaview.app.ui.theme.LocalUiStyle
import com.pitakaview.app.ui.theme.FitakaDimens
import com.pitakaview.app.ui.theme.FitakaTheme
import com.pitakaview.app.ui.theme.Section
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** The colours the chart uses for money in and money out: the Kita and Gastos section colours. */
private val IN_SECTION = Section.KITA
private val OUT_SECTION = Section.GASTOS

/**
 * A column's colour with a paler stripe down its left side: the gloss the buttons have, turned upright.
 * The stripe is placed by the column's own width, so every column has it in the same place.
 */
private fun glossyColumn(color: Color): Brush {
    val light = lerp(color, Color.White, 0.55f)
    return Brush.horizontalGradient(0f to color, 0.28f to color, 0.28f to light, 0.5f to light, 0.5f to color, 1f to color)
}

/**
 * Money in and money out for the last six months, as pairs of bars in the app's sketch style: section
 * colours, thick ink outlines, the handwritten font. Drawn with Vico.
 *
 * The bars are for the eye. What they say is also given in words: [describe] is what a screen reader
 * reads, and the legend names each colour, so nothing depends on telling two hues apart.
 */
@Composable
fun MonthBarsChart(bars: List<MonthBar>, modifier: Modifier = Modifier) {
    val c = FitakaTheme.colors
    val type = MaterialTheme.typography
    // Pesos, not centavos, so the axis reads in the units people think in.
    val model = remember(bars) {
        CartesianChartModel(
            ColumnCartesianLayerModel.build {
                series(bars.map { it.inCentavos / 100.0 })
                series(bars.map { it.outCentavos / 100.0 })
            },
        )
    }
    val months = remember(bars) { bars.map { it.month.month.getDisplayName(DateTextStyle.SHORT, Locale.ENGLISH) } }
    // The amount lines sit on round amounts we choose, so each label is exactly where its line is. Left to
    // itself the chart put lines on fractions (₱1.28, ₱2.55) whose rounded labels read ₱1, ₱3.
    val tallest = remember(bars) { bars.maxOf { maxOf(it.inCentavos, it.outCentavos) } / 100.0 }
    val lineGap = remember(tallest) { MonthSummaryBuilder.axisStep(tallest) }
    val range = remember(tallest) { CartesianLayerRangeProvider.fixed(minY = 0.0, maxY = MonthSummaryBuilder.axisTop(tallest)) }
    val linePlacer = remember(lineGap) { VerticalAxis.ItemPlacer.step({ lineGap }) }
    val barShape = RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp)
    val ink = Fill(c.ink)
    val glossy = LocalUiStyle.current == UiStyle.GLOSSY
    val moneyIn = rememberLineComponent(fill = if (glossy) Fill(glossyColumn(c.fill(IN_SECTION))) else Fill(c.fill(IN_SECTION)), thickness = BAR_WIDTH, shape = barShape, strokeFill = ink, strokeThickness = 2.dp)
    val moneyOut = rememberLineComponent(fill = if (glossy) Fill(glossyColumn(c.fill(OUT_SECTION))) else Fill(c.fill(OUT_SECTION)), thickness = BAR_WIDTH, shape = barShape, strokeFill = ink, strokeThickness = 2.dp)
    val label = rememberAxisLabelComponent(style = type.labelMedium.copy(color = c.ink))
    val axisLine = rememberAxisLineComponent(fill = ink, thickness = 2.dp)
    val guideline = rememberAxisGuidelineComponent(fill = Fill(c.ink.copy(alpha = 0.25f)))
    val pesoAxis = remember { CartesianValueFormatter { _, value, _ -> MonthSummaryBuilder.shortPeso(value) } }
    // A label must never be empty, so a position with no month gets a space.
    val monthAxis = remember(months) { CartesianValueFormatter { _, value, _ -> months.getOrNull(value.roundToInt()) ?: " " } }

    // Tapping a month marks its pair of bars with a soft band and writes the two amounts under the
    // chart. They are written as ordinary text, not drawn inside the chart, so they are large enough to
    // read and a screen reader can read them. Tapping the same bar again clears it.
    var picked by rememberSaveable { mutableStateOf<Int?>(null) }
    val band = rememberLineComponent(fill = Fill(c.ink.copy(alpha = 0.13f)), thickness = BAR_WIDTH, shape = RoundedCornerShape(8.dp))
    val marker = remember(band) { MonthBand(band) }
    val onMarker = remember {
        object : CartesianMarkerVisibilityListener {
            override fun onShown(marker: CartesianMarker, targets: List<CartesianMarker.Target>) {
                picked = targets.firstOrNull()?.x?.roundToInt()
            }

            override fun onUpdated(marker: CartesianMarker, targets: List<CartesianMarker.Target>) {
                picked = targets.firstOrNull()?.x?.roundToInt()
            }

            override fun onHidden(marker: CartesianMarker) {
                picked = null
            }
        }
    }

    val chart = rememberCartesianChart(
        rememberColumnCartesianLayer(
            columnProvider = ColumnCartesianLayer.ColumnProvider.series(moneyIn, moneyOut),
            columnCollectionSpacing = 18.dp,
            mergeMode = sideBySide,
            rangeProvider = range,
        ),
        startAxis = VerticalAxis.rememberStart(
            line = axisLine, label = label, valueFormatter = pesoAxis, tick = null, guideline = guideline, itemPlacer = linePlacer,
        ),
        bottomAxis = HorizontalAxis.rememberBottom(line = axisLine, label = label, valueFormatter = monthAxis, tick = null, guideline = null),
        marker = marker,
        markerVisibilityListener = onMarker,
        markerController = CartesianMarkerController.rememberToggleOnTap(),
    )
    Column(modifier.fillMaxWidth()) {
        CartesianChartHost(
            chart = chart,
            model = model,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = describe(bars) },
            // Six months always fit the width, so there is nothing to scroll or zoom.
            scrollState = rememberVicoScrollState(scrollEnabled = false),
            zoomState = rememberVicoZoomState(zoomEnabled = false, initialZoom = Zoom.Content),
            chartAreaHeight = 180.dp,
        )
        // Always there, at a fixed height, so the card does not jump when a month is tapped.
        Text(
            readout(bars, picked),
            style = type.labelLarge,
            color = if (picked == null) c.inkMuted else c.ink,
            modifier = Modifier.padding(top = 6.dp).heightIn(min = 40.dp),
        )
    }
}

private val BAR_WIDTH = 14.dp

/** The space between a month's two bars. */
private val BAR_GAP = 8.dp

/** How far the band reaches past the pair of bars on each side. */
private val BAND_AIR = 5.dp

private val sideBySide: (ExtraStore) -> ColumnCartesianLayer.MergeMode = { ColumnCartesianLayer.MergeMode.Grouped(BAR_GAP) }

/**
 * Marks the tapped month: a soft band behind its two bars. A tap lands on one bar of the pair, so
 * which bar it was (the first is money in) tells where the middle of the pair is.
 */
private data class MonthBand(val band: LineComponent) : CartesianMarker {
    override fun drawUnderLayers(context: CartesianDrawingContext, targets: List<CartesianMarker.Target>) {
        val target = targets.firstOrNull() as? ColumnCartesianLayerMarkerTarget ?: return
        val towardsPartner = when (target.columns.firstOrNull()?.entry?.seriesIndex) {
            0 -> 1f
            1 -> -1f
            else -> return
        }
        with(context) {
            val halfStep = (BAR_WIDTH.pixels + BAR_GAP.pixels) / 2 * zoom
            val middle = target.canvasX + towardsPartner * halfStep * layoutDirectionMultiplier
            val reach = halfStep + BAR_WIDTH.pixels / 2 * zoom + BAND_AIR.pixels
            band.draw(this, middle - reach, layerBounds.top, middle + reach, layerBounds.bottom)
        }
    }
}

const val TAP_HINT = "Tap a month to see its amounts."

/** The line under the chart: the tapped month's two amounts in full, or how to see them. */
fun readout(bars: List<MonthBar>, picked: Int?): String =
    picked?.let(bars::getOrNull)?.let { "${monthTitle(it.month)}: ${Peso.format(it.inCentavos)} in, ${Peso.format(it.outCentavos)} out" }
        ?: TAP_HINT

/** The chart in words, for a screen reader: "June: ₱20,000.00 in, ₱15,500.00 out. July: …". */
fun describe(bars: List<MonthBar>): String =
    bars.joinToString(". ") { "${monthName(it.month)}: ${Peso.format(it.inCentavos)} in, ${Peso.format(it.outCentavos)} out" }

/** Names the chart's two colours in words. */
@Composable
fun ChartLegend(modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendItem(IN_SECTION, "Money in")
        LegendItem(OUT_SECTION, "Money out")
    }
}

@Composable
private fun LegendItem(section: Section, text: String) {
    val c = FitakaTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(14.dp).sketchBox(RoundedCornerShape(4.dp), fill = c.fill(section), border = c.ink, borderWidth = FitakaDimens.borderThin))
        Text(text, style = MaterialTheme.typography.labelMedium, color = c.ink)
    }
}
