package com.dicar.vehicle.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.dicar.vehicle.ui.localizedVehicleValue
import com.dicar.vehicle.ui.localizedEnergyFlow
import androidx.compose.ui.res.stringResource
import com.dicar.vehicle.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dicar.vehicle.data.model.VehicleState
import com.dicar.vehicle.ui.MainViewModel
import com.dicar.vehicle.ui.components.Car3dView
import com.dicar.vehicle.ui.components.CarDiagram
import com.dicar.vehicle.ui.components.ChartCanvas
import com.dicar.vehicle.ui.components.ChartLegend
import com.dicar.vehicle.ui.components.ChartSeries
import com.dicar.vehicle.ui.components.DataCard
import com.dicar.vehicle.ui.components.DialGauge
import com.dicar.vehicle.ui.components.HeroNumber
import com.dicar.vehicle.ui.components.LabeledBar
import com.dicar.vehicle.ui.components.MetricRow
import com.dicar.vehicle.ui.components.SignedBar
import com.dicar.vehicle.ui.components.SignedBarScale
import com.dicar.vehicle.ui.components.StatusPill
import com.dicar.vehicle.util.Format

/**
 * Twin dashboard: RPM on the left, speed and vehicle view in the center, power and levels on the right.
 * A trend chart and status pills sit below. Portrait stacks the same content vertically.
 *
 * The central speed and vehicle view sit directly on the background, framed by the side cards.
 */
@Composable
fun TwinPane(
    state: VehicleState,
    history: List<MainViewModel.HistorySample>,
    modifier: Modifier = Modifier,
    use3d: Boolean = false,
    evOnly: Boolean = false,
) {
    BoxWithConstraints(modifier) {
        val wide = maxWidth > 720.dp
        if (wide) {
            Column(
                Modifier.fillMaxSize().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (evOnly) EvDrivingCard(state, Modifier.weight(1f).fillMaxHeight())
                    else RpmCard(state, Modifier.weight(1f).fillMaxHeight())
                    SpeedAndCar(state, use3d, Modifier.weight(1.25f).fillMaxHeight())
                    PowerCardPane(state, history, Modifier.weight(1f).fillMaxHeight(), evOnly)
                }
                TrendStrip(state, history, evOnly)
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Use a fixed height on narrow screens: aspect-ratio sizing would make the car
                // about 700dp tall on a 600dp-wide screen.
                SpeedAndCar(state, use3d, Modifier.fillMaxWidth().height(380.dp))
                if (evOnly) EvDrivingCard(state, Modifier.fillMaxWidth())
                else RpmCard(state, Modifier.fillMaxWidth().height(260.dp))
                PowerCardPane(state, history, Modifier.fillMaxWidth(), evOnly)
                TrendStrip(state, history, evOnly)
            }
        }
    }
}

/** EV driving information and current safety statuses. */
@Composable
private fun EvDrivingCard(state: VehicleState, modifier: Modifier = Modifier) {
    val power = state.batteryPower ?: state.power
    DataCard(title = stringResource(R.string.text_drive_power), modifier = modifier) {
        HeroNumber(power, stringResource(R.string.unit_power), size = 36.sp, signed = true)
        SignedBar(power, maxPositive = MAX_DRIVE_KW, maxNegative = MAX_REGEN_KW)
        SignedBarScale(stringResource(R.string.text_regen), "0", stringResource(R.string.text_drive))
        MetricRow(stringResource(R.string.label_speed), Format.num(state.speed, unit = stringResource(R.string.unit_speed)))
        MetricRow(stringResource(R.string.ui_cabin_temperature), Format.num(state.insideTemp, 1, stringResource(R.string.unit_celsius)))
        MetricRow(stringResource(R.string.ui_outside_temperature), Format.num(state.outsideTemp, 1, stringResource(R.string.unit_celsius)))
        val tyre = tyreSummary(state)
        val door = doorSummary(state)
        val radar = radarSummary(state)
        listOfNotNull(radar, tyre, door).forEach { (message, alert) ->
            Text(message, style = MaterialTheme.typography.labelMedium,
                color = if (alert) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MotorRpmBar(state: VehicleState) {
    LabeledBar(
        stringResource(R.string.rpm_motor),
        state.displayRpm?.let { kotlin.math.abs(it.toFloat()) },
        MaterialTheme.colorScheme.primary,
        unit = stringResource(R.string.unit_rpm),
        maxValue = MOTOR_MAX_RPM,
    )
}

/** Left column: RPM gauge; switches to motor RPM, range and redline when the engine is inactive. */
@Composable
private fun RpmCard(state: VehicleState, modifier: Modifier = Modifier) {
    val isEngine = state.engineRpm != null
    DataCard(title = stringResource(if (state.engineRpm != null) R.string.rpm_engine else R.string.rpm_motor), modifier = modifier) {
        DialGauge(
            value = state.displayRpm?.toFloat(),
            max = if (isEngine) ENGINE_MAX_RPM else MOTOR_MAX_RPM,
            unit = stringResource(R.string.unit_rpm),
            redlineFrom = if (isEngine) ENGINE_REDLINE_RPM else null,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
        Text(
            text = localizedEnergyFlow(state.energyFlow) ?: Format.text(localizedVehicleValue(state.workMode)),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

/** Center column: speed and vehicle view. [use3d] selects the 3D model or 2D top view. */
@Composable
private fun SpeedAndCar(state: VehicleState, use3d: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        HeroNumber(state.speed, stringResource(R.string.unit_speed), size = 64.sp)
        Spacer(Modifier.height(4.dp))
        val carModifier = Modifier.fillMaxWidth().weight(1f)
        if (use3d) Car3dView(state, carModifier) else CarDiagram(state, carModifier)
    }
}

/** Right column: drive/regeneration power, battery, fuel and range. */
@Composable
private fun PowerCardPane(state: VehicleState, history: List<MainViewModel.HistorySample>, modifier: Modifier = Modifier, evOnly: Boolean = false) {
    if (evOnly && (state.speed ?: 0f) > 0f) {
        ElectricRangeCard(state, history, modifier)
        return
    }
    val scheme = MaterialTheme.colorScheme
    val power = state.batteryPower ?: state.power
    DataCard(title = stringResource(R.string.text_drive_power), modifier = modifier) {
        HeroNumber(power, stringResource(R.string.unit_power), size = 40.sp, signed = true)
        SignedBar(power, maxPositive = MAX_DRIVE_KW, maxNegative = MAX_REGEN_KW)
        SignedBarScale(stringResource(R.string.text_regen), "0", stringResource(R.string.text_drive))
        Spacer(Modifier.height(2.dp))
        LabeledBar(stringResource(R.string.text_battery), state.soc, scheme.secondary)
        if (!evOnly) LabeledBar(stringResource(R.string.text_fuel), state.fuelPercent, scheme.tertiary)
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth()) {
            val style = MaterialTheme.typography.labelLarge
            Text(
                stringResource(R.string.fmt_electric, Format.num(state.remainRangeElec, unit = stringResource(R.string.unit_km))),
                style = style,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (!evOnly) Text(
                stringResource(R.string.fmt_combined, Format.num(combinedRange(state), unit = stringResource(R.string.unit_km))),
                style = style,
                color = scheme.onSurfaceVariant,
            )
        }
        if (evOnly) MotorRpmBar(state)
    }
}

/** Vehicle-reported range and recent readings. */
@Composable
private fun ElectricRangeCard(state: VehicleState, history: List<MainViewModel.HistorySample>, modifier: Modifier) {
    val range = state.remainRangeElec
    val color = when {
        range == null -> MaterialTheme.colorScheme.onSurfaceVariant
        range <= 50 -> MaterialTheme.colorScheme.error
        range <= 100 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.secondary
    }
    DataCard(title = stringResource(R.string.label_ev_range), modifier = modifier) {
        Text(
            Format.num(range, unit = stringResource(R.string.unit_km)),
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = color,
        )
        RangeAreaChart(history.map { it.electricRange }, color, Modifier.fillMaxWidth().height(90.dp))
        LabeledBar(stringResource(R.string.text_battery), state.soc, MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.weight(1f))
        MotorRpmBar(state)
    }
}

/** Zero-based range history; missing samples break the area into separate segments. */
@Composable
private fun RangeAreaChart(points: List<Float?>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val max = points.filterNotNull().maxOrNull()?.coerceAtLeast(1f) ?: return@Canvas
        if (points.size < 2) return@Canvas
        val dx = size.width / (points.size - 1)
        var start = -1
        fun drawSegment(end: Int) {
            if (start < 0 || end <= start) return
            val area = Path()
            val line = Path()
            area.moveTo(start * dx, size.height)
            for (i in start..end) {
                val x = i * dx
                val y = size.height * (1f - (points[i]!! / max).coerceIn(0f, 1f))
                area.lineTo(x, y)
                if (i == start) line.moveTo(x, y) else line.lineTo(x, y)
            }
            area.lineTo(end * dx, size.height)
            area.close()
            drawPath(area, color.copy(alpha = 0.20f))
            drawPath(line, color, style = Stroke(width = 2.dp.toPx()))
        }
        points.forEachIndexed { index, value ->
            if (value != null) {
                if (start < 0) start = index
            } else {
                drawSegment(index - 1)
                start = -1
            }
        }
        drawSegment(points.lastIndex)
    }
}

/** Combined range is electric plus fuel; unavailable inputs produce null and N/A. */
private fun combinedRange(state: VehicleState): Int? {
    val e = state.remainRangeElec
    val f = state.remainRangeFuel
    return when {
        e != null && f != null -> e + f
        e != null && state.fuelPercent == null -> e   // An EV has no fuel tank, so combined range equals electric range.
        else -> null
    }
}

/** Bottom bar: vertical legend left, chart center, status pills right. */
@Composable
private fun TrendStrip(state: VehicleState, history: List<MainViewModel.HistorySample>, evOnly: Boolean) {
    val scheme = MaterialTheme.colorScheme
    // Plot available engine and motor RPM separately. A separate primary RPM line
    // would duplicate engine RPM when the engine is present.
    val series = buildList {
        add(ChartSeries(stringResource(R.string.text_power), stringResource(R.string.unit_power), scheme.secondary, history.map { it.power }, baseline = 0f))
        if (!evOnly && history.any { it.engineRpm != null }) {
            add(ChartSeries(stringResource(R.string.text_engine), stringResource(R.string.unit_rpm), scheme.error, history.map { it.engineRpm }, baseline = 0f))
        }
        if (history.any { it.motorRpm != null }) {
            add(ChartSeries(stringResource(R.string.text_motor), stringResource(R.string.unit_rpm), scheme.primary, history.map { it.motorRpm }, baseline = 0f))
        }
        // Fall back to primary RPM if neither separate RPM is available.
        if (!evOnly && size == 1 && history.any { it.rpm != null }) {
            add(ChartSeries(stringResource(R.string.unit_label_rpm), stringResource(R.string.unit_rpm), scheme.primary, history.map { it.rpm }, baseline = 0f))
        }
    }
    DataCard(title = null) {
        Row(
            Modifier.fillMaxWidth().height(62.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChartLegend(series, vertical = true)
            ChartCanvas(series, Modifier.weight(1f).fillMaxHeight())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                // Show radar only when active (reverse gear or probe readings), avoiding a persistent unhelpful N/A.
                radarSummary(state)?.let { (text, alert) -> StatusPill(text, alert = alert) }
                StatusPill(acSummary(state))
                val tyre = tyreSummary(state)
                StatusPill(tyre.first, alert = tyre.second)
                val door = doorSummary(state)
                StatusPill(door.first, alert = door.second)
            }
        }
    }
}

/**
 * Parking radar summary, or null when inactive.
 * Returns text to alert status; levels within [RADAR_ALERT_LEVEL] trigger an alert.
 */
@Composable
private fun radarSummary(state: VehicleState): Pair<String, Boolean>? {
    val radar = state.radar
    if (!radar.hasAnyReading && radar.reverseSwitchOn != true) return null
    val nearest = radar.nearest ?: return stringResource(R.string.msg_radar_clear) to false
    return stringResource(R.string.fmt_radar_level, nearest) to (nearest <= RADAR_ALERT_LEVEL)
}

@Composable
private fun acSummary(state: VehicleState): String = when (state.acOn) {
    null -> stringResource(R.string.fmt_climate, Format.NA)
    false -> stringResource(R.string.msg_climate_off)
    true -> stringResource(if (state.acAuto == true) R.string.fmt_climate_auto else R.string.fmt_climate, Format.num(state.acTempDriver, 1, stringResource(R.string.unit_celsius)))
}

/** Returns text to alert status; show N/A when all four wheels are unavailable. */
@Composable
private fun tyreSummary(state: VehicleState): Pair<String, Boolean> {
    val values = state.tirePressure.toList().filterNotNull()
    if (values.isEmpty()) return stringResource(R.string.fmt_tyres, Format.NA) to false
    val bad = values.any { it < TYRE_MIN_KPA || it > TYRE_MAX_KPA }
    return if (bad) stringResource(R.string.msg_tyre_alert) to true else stringResource(R.string.msg_tyre_ok) to false
}

@Composable
private fun doorSummary(state: VehicleState): Pair<String, Boolean> {
    val d = state.doors
    val all = listOf(d.fl, d.fr, d.rl, d.rr, d.hood, d.trunk)
    if (all.all { it == null }) return stringResource(R.string.fmt_doors, Format.NA) to false
    return if (all.any { it == true }) stringResource(R.string.msg_door_open) to true else stringResource(R.string.msg_doors_closed) to false
}

private const val ENGINE_MAX_RPM = 6000f
private const val ENGINE_REDLINE_RPM = 5000f
private const val MOTOR_MAX_RPM = 12000f
private const val MAX_DRIVE_KW = 200f
private const val MAX_REGEN_KW = 100f
private const val TYRE_MIN_KPA = 190f
private const val TYRE_MAX_KPA = 300f

/** The pill turns red at or below this obstacle level. Zero is closest, six farthest. */
private const val RADAR_ALERT_LEVEL = 2
