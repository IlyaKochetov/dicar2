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
import com.dicar.vehicle.ui.localizedVehicleValue
import com.dicar.vehicle.ui.localizedEnergyFlow
import androidx.compose.ui.res.stringResource
import com.dicar.vehicle.R
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
private fun PowerCardPane(state: VehicleState, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val power = state.batteryPower ?: state.power
    DataCard(title = stringResource(R.string.text_drive_power), modifier = modifier) {
        HeroNumber(power, stringResource(R.string.unit_power), size = 40.sp, signed = true)
        SignedBar(power, maxPositive = MAX_DRIVE_KW, maxNegative = MAX_REGEN_KW)
        SignedBarScale(stringResource(R.string.text_regen), "0", stringResource(R.string.text_drive))
        Spacer(Modifier.height(2.dp))
        LabeledBar(stringResource(R.string.text_battery), state.soc, scheme.secondary)
        LabeledBar(stringResource(R.string.text_fuel), state.fuelPercent, scheme.tertiary)
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth()) {
            val style = MaterialTheme.typography.labelLarge
            Text(
                stringResource(R.string.fmt_electric, Format.num(state.remainRangeElec, unit = stringResource(R.string.unit_km))),
                style = style,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.fmt_combined, Format.num(combinedRange(state), unit = stringResource(R.string.unit_km))),
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
        add(ChartSeries(stringResource(R.string.text_power), stringResource(R.string.unit_power), scheme.secondary, history.map { it.power }, baseline = 0f))
        if (history.any { it.engineRpm != null }) {
            add(ChartSeries(stringResource(R.string.text_engine), stringResource(R.string.unit_rpm), scheme.error, history.map { it.engineRpm }, baseline = 0f))
        }
        if (history.any { it.motorRpm != null }) {
            add(ChartSeries(stringResource(R.string.text_motor), stringResource(R.string.unit_rpm), scheme.primary, history.map { it.motorRpm }, baseline = 0f))
        }
        // Fall back to primary RPM if neither separate RPM is available.
        if (size == 1 && history.any { it.rpm != null }) {
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
