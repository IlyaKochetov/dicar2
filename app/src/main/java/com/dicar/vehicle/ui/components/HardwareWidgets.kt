package com.dicar.vehicle.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dicar.vehicle.data.hardware.CoreLive
import com.dicar.vehicle.data.hardware.HwFormat
import com.dicar.vehicle.data.hardware.HwItem

/**
 * Live metric tile: a large number, a thin progress bar, and a caption.
 * A null [fraction] draws an empty bar: unavailable is not 0%.
 */
@Composable
fun UsageTile(
    label: String,
    headline: String,
    caption: String,
    fraction: Float?,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            headline,
            style = MaterialTheme.typography.headlineMedium,
            color = if (headline == HwItem.UNKNOWN) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        ThinBar(fraction, color)
        Text(
            caption,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Thin progress bar. LinearProgressIndicator draws a dot even at zero. */
@Composable
fun ThinBar(fraction: Float?, color: Color, height: Int = 6) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(RoundedCornerShape(height.dp / 2))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        if (fraction != null && fraction > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(height.dp)
                    .background(color)
            )
        }
    }
}

/**
 * One row per CPU core: number, model, current frequency and utilization.
 * Offline cores are dimmed and labeled so they are not mistaken for faulty readings.
 */
@Composable
fun CoreRow(core: CoreLive, modifier: Modifier = Modifier) {
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "CPU${core.index}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = if (core.online) MaterialTheme.colorScheme.onSurface else dim,
            modifier = Modifier.width(46.dp),
        )
        core.model?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = dim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(76.dp),
            )
        }
        Box(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            ThinBar(core.load?.div(100f), loadColor(core.load), height = 8)
        }
        Text(
            if (core.online) HwFormat.kHz(core.curKhz) else "Offline",
            style = MaterialTheme.typography.labelMedium,
            color = if (core.online) MaterialTheme.colorScheme.onSurface else dim,
            maxLines = 1,
            modifier = Modifier.width(70.dp),
        )
        Text(
            HwFormat.percent(core.load),
            style = MaterialTheme.typography.labelMedium,
            color = dim,
            maxLines = 1,
            modifier = Modifier.width(46.dp),
        )
    }
}

/** Higher utilization is more prominent: neutral blue at low levels, orange and red at high levels. */
@Composable
fun loadColor(load: Float?): Color = when {
    load == null -> MaterialTheme.colorScheme.outline
    load >= 90f -> MaterialTheme.colorScheme.error
    load >= 70f -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.primary
}

/**
 * Temperature colors use typical SoC junction temperature thresholds: attention above 70°C, often throttling above 85°C.
 */
@Composable
fun tempColor(celsius: Float?): Color = when {
    celsius == null -> MaterialTheme.colorScheme.outline
    celsius >= 85f -> MaterialTheme.colorScheme.error
    celsius >= 70f -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.secondary
}

/** Compact key-value row for dense lists such as the hardware page. */
@Composable
fun HwRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.42f),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = if (value == HwItem.UNKNOWN) MaterialTheme.colorScheme.outline
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.58f),
        )
    }
}

/** Subheading below the DataCard title level. */
@Composable
fun HwSubtitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 2.dp),
    )
}

/** Item count at the top right of a card. */
@Composable
fun CountBadge(count: Int) {
    Text(
        "$count",
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
