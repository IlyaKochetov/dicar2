package com.dicar.vehicle.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.dicar.vehicle.util.Format

/** Data and styling for one series. */
data class ChartSeries(
    val name: String,
    val unit: String,
    val color: Color,
    val points: List<Float?>,
    /** Fixed lower bound; null adapts to the data. A zero baseline improves readability for signed values such as power. */
    val baseline: Float? = null,
)

/**
 * Multiple series share a chart, each normalized to its own range.
 * Speed (km/h) and power (kW) have different units, so the chart compares trends only.
 */
@Composable
fun HistoryChart(
    series: List<ChartSeries>,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 120.dp,
) {
    Column(modifier) {
        ChartLegend(series)
        ChartCanvas(series, Modifier.fillMaxWidth().height(height))
    }
}

/**
 * Legend. With [vertical] true, each series gets a row for the bottom bar layout.
 * Each entry shows the current value; the curves show trends.
 */
@Composable
fun ChartLegend(
    series: List<ChartSeries>,
    modifier: Modifier = Modifier,
    vertical: Boolean = false,
    showValue: Boolean = true,
) {
    @Composable
    fun item(s: ChartSeries) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(width = 12.dp, height = 3.dp).background(s.color, CircleShape))
            val last = s.points.lastOrNull { it != null }
            Text(
                if (showValue) "${s.name} ${Format.num(last, if (s.unit == "kW") 1 else 0, s.unit)}" else s.name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
    if (vertical) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) { series.forEach { item(it) } }
    } else {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            series.forEach { item(it) }
        }
    }
}

/** Canvas containing only the lines, for layouts that position the legend separately. */
@Composable
fun ChartCanvas(series: List<ChartSeries>, modifier: Modifier = Modifier) {
    val grid = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    Canvas(modifier) {
        // Horizontal grid
        val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 10f))
        for (i in 0..2) {
            val y = size.height * i / 2f
            drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f, pathEffect = dash)
        }

        series.forEach { s ->
            val pts = s.points
            if (pts.count { it != null } < 2) return@forEach
            val values = pts.filterNotNull()
            var lo = s.baseline ?: values.min()
            var hi = values.max()
            if (s.baseline != null) {
                lo = minOf(s.baseline, values.min())
                hi = maxOf(s.baseline, values.max())
            }
            if (hi - lo < 1e-3f) hi = lo + 1f

            val dx = size.width / (pts.size - 1).coerceAtLeast(1)
            val path = Path()
            var started = false
            pts.forEachIndexed { i, v ->
                if (v == null) {
                    started = false // Break the line at missing data rather than implying continuity
                    return@forEachIndexed
                }
                val x = dx * i
                val y = size.height * (1f - (v - lo) / (hi - lo))
                if (started) path.lineTo(x, y) else { path.moveTo(x, y); started = true }
            }
            drawPath(path, s.color, style = Stroke(width = 2.5f))
        }
    }
}
