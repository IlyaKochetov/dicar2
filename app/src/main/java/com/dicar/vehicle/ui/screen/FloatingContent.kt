package com.dicar.vehicle.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.dicar.vehicle.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dicar.vehicle.data.FloatingBlock
import com.dicar.vehicle.data.model.VehicleState
import com.dicar.vehicle.ui.MainViewModel
import com.dicar.vehicle.ui.components.ArcGauge
import com.dicar.vehicle.ui.components.CarDiagram
import com.dicar.vehicle.ui.components.ChartSeries
import com.dicar.vehicle.ui.components.HistoryChart
import com.dicar.vehicle.ui.components.MetricRow
import com.dicar.vehicle.util.Format

/**
 * Floating window content, rendered according to the configured sections and opacity.
 * Drag the top handle to move the window, tap ✕ to close, or tap the title to return to the main screen.
 */
@Composable
fun FloatingContent(
    state: VehicleState,
    history: List<MainViewModel.HistorySample>,
    blocks: Set<FloatingBlock>,
    alpha: Float,
    onDrag: (dx: Float, dy: Float) -> Unit,
    onOpenApp: () -> Unit,
    onClose: () -> Unit,
) {
    val width = if (FloatingBlock.CHART in blocks || FloatingBlock.CAR in blocks) 320.dp else 240.dp
    Surface(
        modifier = Modifier.width(width),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = alpha),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = alpha)),
        tonalElevation = 0.dp,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount.x, dragAmount.y)
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "DiCar",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.pointerInput(Unit) { detectTapGestures { onOpenApp() } },
                )
                Spacer(Modifier.weight(1f))
                Text("⋮⋮", color = MaterialTheme.colorScheme.outline, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                Text(
                    "✕",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .pointerInput(Unit) { detectTapGestures { onClose() } },
                )
            }

            if (FloatingBlock.READOUT in blocks) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        Format.num(state.speed),
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (state.speed == null) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        " " + stringResource(R.string.unit_speed),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        Format.text(state.gear),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (FloatingBlock.GAUGES in blocks) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ArcGauge(
                        value = state.displayRpm?.toFloat(), min = 0f, max = 12000f,
                        label = stringResource(R.string.unit_label_rpm), unit = stringResource(R.string.unit_rpm), modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                    ArcGauge(
                        value = state.batteryPower ?: state.power, min = -100f, max = 200f, zeroAt = 0f,
                        label = stringResource(R.string.text_power), unit = stringResource(R.string.unit_power), decimals = 1, modifier = Modifier.weight(1f),
                    )
                    ArcGauge(
                        value = state.soc, min = 0f, max = 100f,
                        label = stringResource(R.string.text_battery), unit = "%", decimals = 0, modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }

            if (FloatingBlock.CHART in blocks) {
                HistoryChart(
                    series = buildList {
                        add(ChartSeries(stringResource(R.string.text_speed), stringResource(R.string.unit_speed), MaterialTheme.colorScheme.primary, history.map { it.speed }))
                        add(ChartSeries(stringResource(R.string.text_power), stringResource(R.string.unit_power), MaterialTheme.colorScheme.tertiary, history.map { it.power }, baseline = 0f))
                        if (history.any { it.engineRpm != null }) {
                            add(ChartSeries(stringResource(R.string.text_engine), stringResource(R.string.unit_rpm), MaterialTheme.colorScheme.error, history.map { it.engineRpm }, baseline = 0f))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    height = 70.dp,
                )
            }

            if (FloatingBlock.CAR in blocks) {
                CarDiagram(
                    state,
                    Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                )
            }

            if (FloatingBlock.DETAILS in blocks) {
                MetricRow(stringResource(R.string.text_battery), Format.num(state.soc, 1, "%"))
                MetricRow(stringResource(R.string.text_range), Format.num(state.remainRangeElec, unit = stringResource(R.string.unit_km)))
                MetricRow(stringResource(R.string.text_inside_outside), "${Format.num(state.insideTemp, 1)} / ${Format.num(state.outsideTemp, 1, stringResource(R.string.unit_celsius))}")
                MetricRow(stringResource(R.string.text_battery_temperature), Format.num(state.batteryTempMax, 1, stringResource(R.string.unit_celsius)))
            }
        }
    }
}
