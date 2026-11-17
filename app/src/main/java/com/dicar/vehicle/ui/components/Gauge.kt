package com.dicar.vehicle.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dicar.vehicle.util.Format

/**
 * Arc gauge: a 240° dial with the gap at the bottom.
 *
 * A null [value] draws an empty dial and N/A. [zeroAt] supports signed values such as power:
 * zero lies within the range, with negative values drawn left and positive values right.
 */
@Composable
fun ArcGauge(
    value: Float?,
    min: Float,
    max: Float,
    label: String,
    unit: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    negativeColor: Color = MaterialTheme.colorScheme.secondary,
    decimals: Int = 0,
    zeroAt: Float = min,
) {
    val span = (max - min).takeIf { it > 0f } ?: 1f
    val target = value?.coerceIn(min, max) ?: min
    val fraction by animateFloatAsState(
        targetValue = (target - min) / span,
        label = "gauge",
    )
    val zeroFraction = ((zeroAt - min) / span).coerceIn(0f, 1f)
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val negative = value != null && value < zeroAt

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.6f),
            contentAlignment = Alignment.Center,
        ) {
            // Scale text with the dial: small floating-window gauges can be only tens of dp wide,
            // so a fixed 26sp would push readings such as -20.4 outside.
            val valueSize = (maxWidth.value * 0.17f).coerceIn(13f, 26f).sp
            Canvas(Modifier.fillMaxSize()) {
                val stroke = size.minDimension * 0.095f
                val inset = stroke / 2 + size.minDimension * 0.04f
                val side = size.minDimension - inset * 2
                val topLeft = Offset((size.width - side) / 2, inset)
                val arcSize = Size(side, side)

                // Track
                drawArc(
                    color = track, startAngle = START, sweepAngle = SWEEP, useCenter = false,
                    topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round),
                )
                if (value == null) return@Canvas

                // Draw from zero to the current value (signed values may extend left).
                val from = minOf(zeroFraction, fraction)
                val to = maxOf(zeroFraction, fraction)
                val sweep = (to - from) * SWEEP
                if (sweep > 0.5f) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            0f to (if (negative) negativeColor else color).copy(alpha = 0.55f),
                            1f to (if (negative) negativeColor else color),
                        ),
                        startAngle = START + from * SWEEP, sweepAngle = sweep, useCenter = false,
                        topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    Format.num(value, decimals),
                    style = MaterialTheme.typography.displaySmall,
                    fontSize = valueSize,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    color = if (value == null) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                )
                if (value != null) {
                    Text(unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Gap down: sweep clockwise 240° from 150° at the lower left. */
private const val START = 150f
private const val SWEEP = 240f

/**
 * Main gauge: a 270° C-shaped ring, central value and range labels.
 *
 * Unlike [ArcGauge], this prominent RPM gauge has large text, a thick ring and range labels;
 * [ArcGauge] is a smaller gauge with just a value and title.
 *
 * When [redlineFrom] is set, the ring turns red above that value.
 */
@Composable
fun DialGauge(
    value: Float?,
    max: Float,
    unit: String,
    modifier: Modifier = Modifier,
    min: Float = 0f,
    decimals: Int = 0,
    color: Color = MaterialTheme.colorScheme.primary,
    redlineFrom: Float? = null,
    showRange: Boolean = true,
) {
    val span = (max - min).takeIf { it > 0f } ?: 1f
    val fraction by animateFloatAsState(
        targetValue = ((value?.coerceIn(min, max) ?: min) - min) / span,
        label = "dial",
    )
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    val active = if (redlineFrom != null && value != null && value >= redlineFrom) {
        MaterialTheme.colorScheme.error
    } else color

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight)
        Box(Modifier.size(side), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = size.minDimension * 0.085f
                val inset = stroke / 2 + size.minDimension * 0.03f
                val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                val topLeft = Offset(inset, inset)
                drawArc(
                    color = track, startAngle = DIAL_START, sweepAngle = DIAL_SWEEP, useCenter = false,
                    topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round),
                )
                if (value == null || fraction <= 0.001f) return@Canvas
                drawArc(
                    color = active, startAngle = DIAL_START, sweepAngle = DIAL_SWEEP * fraction, useCenter = false,
                    topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }

            // Scale the central reading with the dial so it fits small gauges.
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    Format.num(value, decimals),
                    fontSize = (side.value * 0.26f).sp,
                    lineHeight = (side.value * 0.28f).sp,
                    style = MaterialTheme.typography.displayMedium,
                    color = if (value == null) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    unit,
                    fontSize = (side.value * 0.075f).sp,
                    color = dim,
                )
            }

            if (showRange) {
                // Place range labels on either side of the gap, below the ring ends.
                Text(
                    Format.num(min),
                    style = MaterialTheme.typography.labelMedium,
                    color = dim,
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = side * 0.10f),
                )
                Text(
                    Format.num(max),
                    style = MaterialTheme.typography.labelMedium,
                    color = dim,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = side * 0.10f),
                )
            }
        }
    }
}

/** Main dial: sweep clockwise 270° from 135°, leaving a 90° bottom gap for range labels. */
private const val DIAL_START = 135f
private const val DIAL_SWEEP = 270f
