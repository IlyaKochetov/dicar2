package com.dicar.vehicle.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.dicar.vehicle.ui.displayOnOff
import com.dicar.vehicle.ui.displayLevel
import com.dicar.vehicle.ui.localizedLabel
import androidx.compose.ui.res.stringResource
import com.dicar.vehicle.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dicar.vehicle.data.model.AcCycleMode
import com.dicar.vehicle.data.model.AcWindMode
import com.dicar.vehicle.data.model.GlassZone
import com.dicar.vehicle.data.model.QuickAction
import com.dicar.vehicle.data.model.VehicleState
import com.dicar.vehicle.data.model.Zone
import com.dicar.vehicle.util.Format

/** Operations exposed by the climate panel and implemented by the ViewModel. */
interface AcActions {
    fun toggleAc()
    fun toggleAcAuto()
    fun adjustTemp(zone: Zone, delta: Float)
    fun adjustFan(delta: Int)
    fun setWindMode(mode: AcWindMode)
    fun toggleCycle()
    fun cycleSeatHeat(zone: Zone)
    fun cycleSeatVent(zone: Zone)
    fun quick(action: QuickAction)
    fun glass(zone: GlassZone, open: Boolean)
}

/** Climate and comfort controls: F18-F25 + F38. [pending] contains command keys awaiting readback confirmation. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AcPanel(
    state: VehicleState,
    pending: Set<String>,
    actions: AcActions,
    tempStep: Float,
    modifier: Modifier = Modifier,
) {
    DataCard(
        title = stringResource(R.string.ui_climate_controls),
        modifier = modifier,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if ("ac_power" in pending) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(displayOnOff(state.acOn), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(8.dp))
                Switch(checked = state.acOn == true, onCheckedChange = { actions.toggleAc() })
            }
        },
    ) {
        MetricPair(
            stringResource(R.string.ui_cabin_temperature), Format.num(state.insideTemp, 1, stringResource(R.string.unit_celsius)),
            stringResource(R.string.ui_outside_temperature), Format.num(state.outsideTemp, 1, stringResource(R.string.unit_celsius)),
        )
        HorizontalDivider()

        Stepper(
            label = stringResource(R.string.ui_driver_temperature),
            value = Format.num(state.acTempDriver, 1, stringResource(R.string.unit_celsius)),
            pending = "ac_temp_${Zone.DRIVER}" in pending,
            onMinus = { actions.adjustTemp(Zone.DRIVER, -tempStep) },
            onPlus = { actions.adjustTemp(Zone.DRIVER, tempStep) },
        )
        Stepper(
            label = stringResource(R.string.ui_passenger_temperature),
            value = Format.num(state.acTempPassenger, 1, stringResource(R.string.unit_celsius)),
            pending = "ac_temp_${Zone.PASSENGER}" in pending,
            onMinus = { actions.adjustTemp(Zone.PASSENGER, -tempStep) },
            onPlus = { actions.adjustTemp(Zone.PASSENGER, tempStep) },
        )
        Stepper(
            label = stringResource(R.string.ui_fan_speed),
            value = displayLevel(state.acFanLevel),
            pending = "ac_fan" in pending,
            onMinus = { actions.adjustFan(-1) },
            onPlus = { actions.adjustFan(1) },
        )

        Label(stringResource(R.string.ui_airflow_direction))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AcWindMode.entries.forEach { mode ->
                FilterChip(
                    selected = state.acWindMode == mode,
                    onClick = { actions.setWindMode(mode) },
                    label = { Text(mode.localizedLabel()) },
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.acAuto == true,
                onClick = { actions.toggleAcAuto() },
                label = { Text("AUTO") },
            )
            FilterChip(
                selected = state.acCycle == AcCycleMode.INNER,
                onClick = { actions.toggleCycle() },
                label = { Text(state.acCycle?.localizedLabel() ?: stringResource(R.string.fmt_circulation, Format.NA)) },
            )
        }

        HorizontalDivider()
        Label(stringResource(R.string.fmt_seats, state.seatMaxLevel))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SeatButton(stringResource(R.string.ui_driver_heating), state.seatHeatDriver, "seat_heat_${Zone.DRIVER}" in pending, Modifier.weight(1f)) {
                actions.cycleSeatHeat(Zone.DRIVER)
            }
            SeatButton(stringResource(R.string.ui_passenger_heating), state.seatHeatPassenger, "seat_heat_${Zone.PASSENGER}" in pending, Modifier.weight(1f)) {
                actions.cycleSeatHeat(Zone.PASSENGER)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SeatButton(stringResource(R.string.ui_driver_ventilation), state.seatVentDriver, "seat_vent_${Zone.DRIVER}" in pending, Modifier.weight(1f)) {
                actions.cycleSeatVent(Zone.DRIVER)
            }
            SeatButton(stringResource(R.string.ui_passenger_ventilation), state.seatVentPassenger, "seat_vent_${Zone.PASSENGER}" in pending, Modifier.weight(1f)) {
                actions.cycleSeatVent(Zone.PASSENGER)
            }
        }

        HorizontalDivider()
        Label(stringResource(R.string.ui_windows_sunroof_vehicle_parts_move_check_surroundings))
        GlassRow(GlassZone.WINDOW_FL, state.windowPercent.fl, pending, actions, Modifier.weight(1f)) {
            GlassRowItem(GlassZone.WINDOW_FR, state.windowPercent.fr, pending, actions)
        }
        GlassRow(GlassZone.WINDOW_RL, state.windowPercent.rl, pending, actions, Modifier.weight(1f)) {
            GlassRowItem(GlassZone.WINDOW_RR, state.windowPercent.rr, pending, actions)
        }
        GlassRow(GlassZone.SUNROOF, state.sunroofPercent, pending, actions, Modifier.weight(1f)) {
            GlassRowItem(GlassZone.SUNSHADE, state.sunshadePercent, pending, actions)
        }

        HorizontalDivider()
        Label(stringResource(R.string.ui_quick_actions))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickAction.entries.forEach { action ->
                OutlinedButton(onClick = { actions.quick(action) }) {
                    if ("quick_$action" in pending) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(action.localizedLabel())
                }
            }
        }
    }
}

@Composable
private fun SeatButton(label: String, level: Int?, pending: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val active = (level ?: 0) > 0
    FilterChip(
        selected = active,
        onClick = onClick,
        modifier = modifier,
        label = {
            Text("$label  ${displayLevel(level)}")
            if (pending) {
                Spacer(Modifier.width(6.dp))
                CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp)
            }
        },
    )
}

/** Controls for two glass components in a row. */
@Composable
private fun GlassRow(
    zone: GlassZone,
    percent: Int?,
    pending: Set<String>,
    actions: AcActions,
    modifier: Modifier,
    second: @Composable RowScope.() -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(modifier) { GlassControl(zone, percent, pending, actions) }
        second()
    }
}

@Composable
private fun RowScope.GlassRowItem(zone: GlassZone, percent: Int?, pending: Set<String>, actions: AcActions) {
    Box(Modifier.weight(1f)) { GlassControl(zone, percent, pending, actions) }
}

@Composable
private fun GlassControl(zone: GlassZone, percent: Int?, pending: Set<String>, actions: AcActions) {
    val busy = "glass_$zone" in pending
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(zone.localizedLabel(), style = MaterialTheme.typography.bodyMedium)
            Text(
                Format.num(percent, unit = "%"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (busy) {
            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(6.dp))
        }
        OutlinedButton(onClick = { actions.glass(zone, true) }, contentPadding = PaddingValues(horizontal = 10.dp)) {
            Text(stringResource(R.string.ui_on))
        }
        Spacer(Modifier.width(6.dp))
        OutlinedButton(onClick = { actions.glass(zone, false) }, contentPadding = PaddingValues(horizontal = 10.dp)) {
            Text(stringResource(R.string.ui_off))
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
