package com.pitakaview.app.ui.summary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
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
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.pitakaview.app.domain.Peso
import com.pitakaview.app.ui.components.sketchBox
import com.pitakaview.app.ui.ledger.monthName
import com.pitakaview.app.ui.theme.PitakaDimens
import com.pitakaview.app.ui.theme.PitakaTheme
import com.pitakaview.app.ui.theme.Section
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** The colours the chart uses for money in and money out: the Kita and Gastos section colours. */
private val IN_SECTION = Section.KITA
private val OUT_SECTION = Section.GASTOS

/**
 * Money in and money out for the last six months, as pairs of bars in the app's sketch style: section
 * colours, thick ink outlines, the handwritten font. Drawn with Vico.
 *
 * The bars are for the eye. What they say is also given in words: [describe] is what a screen reader
 * reads, and the legend names each colour, so nothing depends on telling two hues apart.
 */
@Composable
fun MonthBarsChart(bars: List<MonthBar>, modifier: Modifier = Modifier) {
    val c = PitakaTheme.colors
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
    val moneyIn = rememberLineComponent(fill = Fill(c.fill(IN_SECTION)), thickness = 14.dp, shape = barShape, strokeFill = ink, strokeThickness = 2.dp)
    val moneyOut = rememberLineComponent(fill = Fill(c.fill(OUT_SECTION)), thickness = 14.dp, shape = barShape, strokeFill = ink, strokeThickness = 2.dp)
    val label = rememberAxisLabelComponent(style = type.labelMedium.copy(color = c.ink))
    val axisLine = rememberAxisLineComponent(fill = ink, thickness = 2.dp)
    val guideline = rememberAxisGuidelineComponent(fill = Fill(c.ink.copy(alpha = 0.25f)))
    val pesoAxis = remember { CartesianValueFormatter { _, value, _ -> MonthSummaryBuilder.shortPeso(value) } }
    // A label must never be empty, so a position with no month gets a space.
    val monthAxis = remember(months) { CartesianValueFormatter { _, value, _ -> months.getOrNull(value.roundToInt()) ?: " " } }

    val chart = rememberCartesianChart(
        rememberColumnCartesianLayer(
            columnProvider = ColumnCartesianLayer.ColumnProvider.series(moneyIn, moneyOut),
            columnCollectionSpacing = 18.dp,
            rangeProvider = range,
        ),
        startAxis = VerticalAxis.rememberStart(
            line = axisLine, label = label, valueFormatter = pesoAxis, tick = null, guideline = guideline, itemPlacer = linePlacer,
        ),
        bottomAxis = HorizontalAxis.rememberBottom(line = axisLine, label = label, valueFormatter = monthAxis, tick = null, guideline = null),
    )
    CartesianChartHost(
        chart = chart,
        model = model,
        modifier = modifier.fillMaxWidth().semantics { contentDescription = describe(bars) },
        // Six months always fit the width, so there is nothing to scroll or zoom.
        scrollState = rememberVicoScrollState(scrollEnabled = false),
        zoomState = rememberVicoZoomState(zoomEnabled = false, initialZoom = Zoom.Content),
        chartAreaHeight = 180.dp,
    )
}

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
    val c = PitakaTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(14.dp).sketchBox(RoundedCornerShape(4.dp), fill = c.fill(section), border = c.ink, borderWidth = PitakaDimens.borderThin))
        Text(text, style = MaterialTheme.typography.labelMedium, color = c.ink)
    }
}
