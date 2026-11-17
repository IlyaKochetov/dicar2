package com.dicar.vehicle.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dicar.vehicle.data.model.VehicleState
import com.dicar.vehicle.util.Format

/** 电池与能量卡片：F09-F17。 */
@Composable
fun BatteryCard(state: VehicleState, modifier: Modifier = Modifier) {
    DataCard(title = "Battery & energy", modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Bottom) {
            BigMetric(Format.num(state.soc, 1), "%", "SOC")
            BigMetric(Format.num(state.remainRangeElec), "km", "EV range", size = 32.sp)
            BigMetric(Format.num(state.remainRangeFuel), "km", "Fuel range", size = 32.sp)
        }
        PercentBar("State of charge", state.soc, MaterialTheme.colorScheme.secondary)

        MetricPair(
            "Max battery temperature", Format.num(state.batteryTempMax, 1, "℃"),
            "Min battery temperature", Format.num(state.batteryTempMin, 1, "℃"),
        )
        MetricPair(
            "Average battery temperature", Format.num(state.batteryTempAvg, 1, "℃"),
            "Pack voltage", Format.num(state.batteryVoltage, 1, "V"),
        )
        MetricPair(
            "Max cell voltage", Format.num(state.cellVoltageMax, 3, "V"),
            "Min cell voltage", Format.num(state.cellVoltageMin, 3, "V"),
        )
        MetricPair(
            "Cell voltage difference", Format.num(state.cellVoltageDiffMv, unit = "mV"),
            "12V voltage", Format.num(state.voltage12V, 1, "V"),
        )
        MetricPair(
            "Battery current", Format.num(state.batteryCurrent, 1, "A"),
            "Battery power", Format.num(state.batteryPower, 1, "kW"),
        )
        HorizontalDivider()
        MetricPair(
            "Charge connector", when (state.chargeGunConnected) {
                null -> Format.NA
                true -> "Connected"
                false -> "Not connected"
            },
            "Charging status", Format.text(state.chargeStatus),
        )
        MetricPair(
            "Instant EV consumption", Format.num(state.instantElecConsumption, 1, "kWh/100 km"),
            "Instant fuel consumption", Format.num(state.instantFuelConsumption, 1, "L/100 km"),
        )
        MetricPair(
            "Trip EV consumption", Format.num(state.tripElecConsumption, 1, "kWh/100 km"),
            "Trip fuel consumption", Format.num(state.tripFuelConsumption, 1, "L/100 km"),
        )
        MetricRow("Fuel level", Format.num(state.fuelPercent, unit = "%"))
    }
}
