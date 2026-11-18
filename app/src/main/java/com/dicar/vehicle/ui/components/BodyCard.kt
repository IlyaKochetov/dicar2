package com.dicar.vehicle.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.dicar.vehicle.ui.displayOnOff
import com.dicar.vehicle.ui.displayOpenClose
import androidx.compose.ui.res.stringResource
import com.dicar.vehicle.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dicar.vehicle.data.model.Radar
import com.dicar.vehicle.data.model.VehicleState
import com.dicar.vehicle.data.model.Wheels
import com.dicar.vehicle.util.Format

/** Body and safety card: F26-F32. */
@Composable
fun BodyCard(state: VehicleState, modifier: Modifier = Modifier) {
    val warn = MaterialTheme.colorScheme.tertiary
    DataCard(title = stringResource(R.string.ui_body_safety), modifier = modifier) {
        SectionLabel(stringResource(R.string.ui_tyre_pressure_temperature))
        WheelGrid(state.tirePressure, state.tireTemp)

        HorizontalDivider()
        SectionLabel(stringResource(R.string.ui_doors_hood_trunk))
        val doors = state.doors
        MetricPair(stringResource(R.string.ui_front_left_door), displayOpenClose(doors.fl), stringResource(R.string.ui_front_right_door), displayOpenClose(doors.fr))
        MetricPair(stringResource(R.string.ui_rear_left_door), displayOpenClose(doors.rl), stringResource(R.string.ui_rear_right_door), displayOpenClose(doors.rr))
        MetricPair(stringResource(R.string.ui_hood), displayOpenClose(doors.hood), stringResource(R.string.ui_trunk), displayOpenClose(doors.trunk))

        HorizontalDivider()
        SectionLabel(stringResource(R.string.ui_windows_sunroof))
        val w = state.windowPercent
        MetricPair(stringResource(R.string.ui_front_left_window), Format.num(w.fl, unit = "%"), stringResource(R.string.ui_front_right_window), Format.num(w.fr, unit = "%"))
        MetricPair(stringResource(R.string.ui_rear_left_window), Format.num(w.rl, unit = "%"), stringResource(R.string.ui_rear_right_window), Format.num(w.rr, unit = "%"))
        MetricPair(
            stringResource(R.string.ui_sunroof), Format.num(state.sunroofPercent, unit = "%"),
            stringResource(R.string.ui_sunshade), Format.num(state.sunshadePercent, unit = "%"),
        )
        MetricRow(stringResource(R.string.ui_window_anti_pinch), displayOnOff(state.windowAntiPinch))

        HorizontalDivider()
        MetricRow(stringResource(R.string.ui_steering_angle), Format.num(state.steeringAngle, 1, "°"))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            MetricRow(
                stringResource(R.string.ui_driver_seat_belt), seatbelt(state.seatbeltDriver), Modifier.weight(1f),
                valueColor = if (state.seatbeltDriver == false) warn else Color.Unspecified,
            )
            // When the passenger seat is empty, the belt signal is unreliable; show "Unoccupied".
            MetricRow(
                stringResource(R.string.ui_passenger_seat_belt),
                if (state.passengerPresent == false) stringResource(R.string.msg_unoccupied) else seatbelt(state.seatbeltPassenger),
                Modifier.weight(1f),
                valueColor = if (state.passengerPresent != false && state.seatbeltPassenger == false) warn else Color.Unspecified,
            )
        }
        MetricPair(
            stringResource(R.string.ui_left_indicator), displayOnOff(state.turnLeft),
            stringResource(R.string.ui_right_indicator), displayOnOff(state.turnRight),
        )

        HorizontalDivider()
        SectionLabel(stringResource(R.string.ui_parking_radar))
        val radar = state.radar
        MetricRow(stringResource(R.string.ui_reverse_gear), displayOnOff(radar.reverseSwitchOn))
        // List individual probes: the mapping from levels to centimetres has not been verified on a car.
        // Show raw levels to help identify changing probes (marked [?] in BydApiMap).
        MetricPair(stringResource(R.string.ui_front_left), probe(radar.frontLeft), stringResource(R.string.ui_front_left_centre), probe(radar.frontLeftMid))
        MetricPair(stringResource(R.string.ui_front_right_centre), probe(radar.frontRightMid), stringResource(R.string.ui_front_right), probe(radar.frontRight))
        MetricPair(stringResource(R.string.ui_rear_left), probe(radar.rearLeft), stringResource(R.string.ui_rear_centre), probe(radar.rearMid))
        MetricPair(stringResource(R.string.ui_rear_right), probe(radar.rearRight), stringResource(R.string.ui_left_side), probe(radar.left))
        MetricRow(stringResource(R.string.ui_right_side), probe(radar.right))
    }
}

/** Other information: F33-F35. */
@Composable
fun MiscCard(state: VehicleState, modifier: Modifier = Modifier) {
    DataCard(title = stringResource(R.string.ui_other_information), modifier = modifier) {
        MetricPair(
            stringResource(R.string.ui_odometer), Format.num(state.totalMileage, unit = stringResource(R.string.unit_km)),
            stringResource(R.string.ui_trip_distance), Format.num(state.tripMileage, 1, stringResource(R.string.unit_km)),
        )
        MetricPair(
            stringResource(R.string.ui_altitude), Format.num(state.altitude, unit = stringResource(R.string.unit_meter)),
            stringResource(R.string.ui_slope), Format.num(state.slope, 1, "°"),
        )
        MetricRow("PM2.5", Format.num(state.pm25, unit = stringResource(R.string.unit_pm25)))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun WheelGrid(pressure: Wheels<Float>, temp: Wheels<Float>) {
    @Composable
    fun cell(label: String, p: Float?, t: Float?, modifier: Modifier) {
        Column(modifier) {
            MetricRow(label, Format.num(p?.div(100f), 1, stringResource(R.string.unit_bar)))
            MetricRow("", Format.num(t, unit = stringResource(R.string.unit_celsius)))
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        cell(stringResource(R.string.ui_front_left), pressure.fl, temp.fl, Modifier.weight(1f))
        cell(stringResource(R.string.ui_front_right), pressure.fr, temp.fr, Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        cell(stringResource(R.string.ui_rear_left), pressure.rl, temp.rl, Modifier.weight(1f))
        cell(stringResource(R.string.ui_rear_right), pressure.rr, temp.rr, Modifier.weight(1f))
    }
}

/**
 * Reading from one radar probe. Head-unit constants 0..6 represent distance levels (lower is closer);
 * 14 means clear. Other values are unavailable. The mapping to centimetres has not been verified on a car.
 */
@Composable
private fun probe(level: Int?): String = when {
    level == null -> Format.NA
    level == Radar.SAFE -> stringResource(R.string.msg_clear)
    level in Radar.OBSTACLE_MIN..Radar.OBSTACLE_MAX -> stringResource(R.string.fmt_level, level)
    else -> Format.NA
}

@Composable
private fun seatbelt(fastened: Boolean?) = when (fastened) {
    null -> Format.NA
    true -> stringResource(R.string.msg_fastened)
    false -> stringResource(R.string.msg_unfastened)
}
