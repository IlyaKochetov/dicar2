package com.dicar.vehicle.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    DataCard(title = "Body & safety", modifier = modifier) {
        SectionLabel("Tyre pressure / temperature")
        WheelGrid(state.tirePressure, state.tireTemp)

        HorizontalDivider()
        SectionLabel("Doors, hood & trunk")
        val doors = state.doors
        MetricPair("Front left door", Format.openClose(doors.fl), "Front right door", Format.openClose(doors.fr))
        MetricPair("Rear left door", Format.openClose(doors.rl), "Rear right door", Format.openClose(doors.rr))
        MetricPair("Hood", Format.openClose(doors.hood), "Trunk", Format.openClose(doors.trunk))

        HorizontalDivider()
        SectionLabel("Windows / sunroof")
        val w = state.windowPercent
        MetricPair("Front left window", Format.num(w.fl, unit = "%"), "Front right window", Format.num(w.fr, unit = "%"))
        MetricPair("Rear left window", Format.num(w.rl, unit = "%"), "Rear right window", Format.num(w.rr, unit = "%"))
        MetricPair(
            "Sunroof", Format.num(state.sunroofPercent, unit = "%"),
            "Sunshade", Format.num(state.sunshadePercent, unit = "%"),
        )
        MetricRow("Window anti-pinch", Format.onOff(state.windowAntiPinch))

        HorizontalDivider()
        MetricRow("Steering angle", Format.num(state.steeringAngle, 1, "°"))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            MetricRow(
                "Driver seat belt", seatbelt(state.seatbeltDriver), Modifier.weight(1f),
                valueColor = if (state.seatbeltDriver == false) warn else Color.Unspecified,
            )
            // When the passenger seat is empty, the belt signal is unreliable; show "Unoccupied".
            MetricRow(
                "Passenger seat belt",
                if (state.passengerPresent == false) "Unoccupied" else seatbelt(state.seatbeltPassenger),
                Modifier.weight(1f),
                valueColor = if (state.passengerPresent != false && state.seatbeltPassenger == false) warn else Color.Unspecified,
            )
        }
        MetricPair(
            "Left indicator", Format.onOff(state.turnLeft),
            "Right indicator", Format.onOff(state.turnRight),
        )

        HorizontalDivider()
        SectionLabel("Parking radar")
        val radar = state.radar
        MetricRow("Reverse gear", Format.onOff(radar.reverseSwitchOn))
        // List individual probes: the mapping from levels to centimetres has not been verified on a car.
        // Show raw levels to help identify changing probes (marked [?] in BydApiMap).
        MetricPair("Front left", probe(radar.frontLeft), "Front left-centre", probe(radar.frontLeftMid))
        MetricPair("Front right-centre", probe(radar.frontRightMid), "Front right", probe(radar.frontRight))
        MetricPair("Rear left", probe(radar.rearLeft), "Rear centre", probe(radar.rearMid))
        MetricPair("Rear right", probe(radar.rearRight), "Left side", probe(radar.left))
        MetricRow("Right side", probe(radar.right))
    }
}

/** Other information: F33-F35. */
@Composable
fun MiscCard(state: VehicleState, modifier: Modifier = Modifier) {
    DataCard(title = "Other information", modifier = modifier) {
        MetricPair(
            "Odometer", Format.num(state.totalMileage, unit = "km"),
            "Trip distance", Format.num(state.tripMileage, 1, "km"),
        )
        MetricPair(
            "Altitude", Format.num(state.altitude, unit = "m"),
            "Slope", Format.num(state.slope, 1, "°"),
        )
        MetricRow("PM2.5", Format.num(state.pm25, unit = "μg/m³"))
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
            MetricRow(label, Format.num(p, unit = "kPa"))
            MetricRow("", Format.num(t, unit = "℃"))
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        cell("Front left", pressure.fl, temp.fl, Modifier.weight(1f))
        cell("Front right", pressure.fr, temp.fr, Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        cell("Rear left", pressure.rl, temp.rl, Modifier.weight(1f))
        cell("Rear right", pressure.rr, temp.rr, Modifier.weight(1f))
    }
}

/**
 * Reading from one radar probe. Head-unit constants 0..6 represent distance levels (lower is closer);
 * 14 means clear. Other values are unavailable. The mapping to centimetres has not been verified on a car.
 */
private fun probe(level: Int?): String = when {
    level == null -> Format.NA
    level == Radar.SAFE -> "Clear"
    level in Radar.OBSTACLE_MIN..Radar.OBSTACLE_MAX -> "Level $level"
    else -> Format.NA
}

private fun seatbelt(fastened: Boolean?) = when (fastened) {
    null -> Format.NA
    true -> "Fastened"
    false -> "Unfastened"
}
