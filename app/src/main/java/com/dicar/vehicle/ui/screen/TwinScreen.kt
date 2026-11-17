package com.dicar.vehicle.ui.screen

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
import androidx.compose.ui.Alignment
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
) {
    BoxWithConstraints(modifier) {
        val wide = maxWidth > 720.dp
        if (wide) {
            Column(
                Modifier.fillMaxSize().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RpmCard(state, Modifier.weight(1f).fillMaxHeight())
                    SpeedAndCar(state, use3d, Modifier.weight(1.25f).fillMaxHeight())
                    PowerCardPane(state, Modifier.weight(1f).fillMaxHeight())
                }
                TrendStrip(state, history)
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
                RpmCard(state, Modifier.fillMaxWidth().height(260.dp))
                PowerCardPane(state, Modifier.fillMaxWidth())
                TrendStrip(state, history)
            }
        }
    }
}

/** Left column: RPM gauge; switches to motor RPM, range and redline when the engine is inactive. */
@Composable
private fun RpmCard(state: VehicleState, modifier: Modifier = Modifier) {
    val isEngine = state.engineRpm != null
    DataCard(title = state.displayRpmLabel, modifier = modifier) {
        DialGauge(
            value = state.displayRpm?.toFloat(),
            max = if (isEngine) ENGINE_MAX_RPM else MOTOR_MAX_RPM,
            unit = "rpm",
            redlineFrom = if (isEngine) ENGINE_REDLINE_RPM else null,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
        Text(
            text = state.energyFlow ?: Format.text(state.workMode),
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
        HeroNumber(state.speed, "km/h", size = 64.sp)
        Spacer(Modifier.height(4.dp))
        val carModifier = Modifier.fillMaxWidth().weight(1f)
        if (use3d) Car3dView(state, carModifier) else CarDiagram(state, carModifier)
    }
}

/** Right column: drive/regeneration power, battery, fuel and range. */
@Composable
private fun PowerCardPane(state: VehicleState, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val power = state.batteryPower ?: state.power
    DataCard(title = "Drive power", modifier = modifier) {
        HeroNumber(power, "kW", size = 40.sp, signed = true)
        SignedBar(power, maxPositive = MAX_DRIVE_KW, maxNegative = MAX_REGEN_KW)
        SignedBarScale("Regen", "0", "Drive")
        Spacer(Modifier.height(2.dp))
        LabeledBar("Battery", state.soc, scheme.secondary)
        LabeledBar("Fuel", state.fuelPercent, scheme.tertiary)
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth()) {
            val style = MaterialTheme.typography.labelLarge
            Text(
                "Electric ${Format.num(state.remainRangeElec, unit = "km")}",
                style = style,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                "Combined ${Format.num(combinedRange(state), unit = "km")}",
                style = style,
                color = scheme.onSurfaceVariant,
            )
        }
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
private fun TrendStrip(state: VehicleState, history: List<MainViewModel.HistorySample>) {
    val scheme = MaterialTheme.colorScheme
    // Plot available engine and motor RPM separately. A separate primary RPM line
    // would duplicate engine RPM when the engine is present.
    val series = buildList {
        add(ChartSeries("Power", "kW", scheme.secondary, history.map { it.power }, baseline = 0f))
        if (history.any { it.engineRpm != null }) {
            add(ChartSeries("Engine", "rpm", scheme.error, history.map { it.engineRpm }, baseline = 0f))
        }
        if (history.any { it.motorRpm != null }) {
            add(ChartSeries("Motor", "rpm", scheme.primary, history.map { it.motorRpm }, baseline = 0f))
        }
        // Fall back to primary RPM if neither separate RPM is available.
        if (size == 1 && history.any { it.rpm != null }) {
            add(ChartSeries("RPM", "rpm", scheme.primary, history.map { it.rpm }, baseline = 0f))
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
private fun radarSummary(state: VehicleState): Pair<String, Boolean>? {
    val radar = state.radar
    if (!radar.hasAnyReading && radar.reverseSwitchOn != true) return null
    val nearest = radar.nearest ?: return "Radar clear" to false
    return "Radar level $nearest" to (nearest <= RADAR_ALERT_LEVEL)
}

private fun acSummary(state: VehicleState): String = when (state.acOn) {
    null -> "Climate ${Format.NA}"
    false -> "Climate off"
    true -> buildString {
        append("Climate ")
        append(Format.num(state.acTempDriver, 1, "℃"))
        if (state.acAuto == true) append(" Auto")
    }
}

/** Returns text to alert status; show N/A when all four wheels are unavailable. */
private fun tyreSummary(state: VehicleState): Pair<String, Boolean> {
    val values = state.tirePressure.toList().filterNotNull()
    if (values.isEmpty()) return "Tyres ${Format.NA}" to false
    val bad = values.any { it < TYRE_MIN_KPA || it > TYRE_MAX_KPA }
    return if (bad) "Tyre pressure alert" to true else "Tyre pressure OK" to false
}

private fun doorSummary(state: VehicleState): Pair<String, Boolean> {
    val d = state.doors
    val all = listOf(d.fl, d.fr, d.rl, d.rr, d.hood, d.trunk)
    if (all.all { it == null }) return "Doors ${Format.NA}" to false
    return if (all.any { it == true }) "Door open" to true else "Doors closed" to false
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
