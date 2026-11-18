package com.dicar.vehicle.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.dicar.vehicle.ui.localizedVehicleValue
import androidx.compose.ui.res.stringResource
import com.dicar.vehicle.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dicar.vehicle.data.model.VehicleState
import com.dicar.vehicle.util.Format

/** Battery and energy card: F09-F17. */
@Composable
fun BatteryCard(state: VehicleState, modifier: Modifier = Modifier) {
    DataCard(title = stringResource(R.string.label_battery_energy), modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Bottom) {
            BigMetric(Format.num(state.soc, 1), "%", "SOC")
            BigMetric(Format.num(state.remainRangeElec), stringResource(R.string.unit_km), stringResource(R.string.label_ev_range), size = 32.sp)
            BigMetric(Format.num(state.remainRangeFuel), stringResource(R.string.unit_km), stringResource(R.string.label_fuel_range), size = 32.sp)
        }
        PercentBar(stringResource(R.string.label_state_of_charge), state.soc, MaterialTheme.colorScheme.secondary)

        MetricPair(
            stringResource(R.string.label_max_battery_temperature), Format.num(state.batteryTempMax, 1, stringResource(R.string.unit_celsius)),
            stringResource(R.string.label_min_battery_temperature), Format.num(state.batteryTempMin, 1, stringResource(R.string.unit_celsius)),
        )
        MetricPair(
            stringResource(R.string.label_average_battery_temperature), Format.num(state.batteryTempAvg, 1, stringResource(R.string.unit_celsius)),
            stringResource(R.string.label_pack_voltage), Format.num(state.batteryVoltage, 1, stringResource(R.string.unit_volt)),
        )
        MetricPair(
            stringResource(R.string.label_max_cell_voltage), Format.num(state.cellVoltageMax, 3, stringResource(R.string.unit_volt)),
            stringResource(R.string.label_min_cell_voltage), Format.num(state.cellVoltageMin, 3, stringResource(R.string.unit_volt)),
        )
        MetricPair(
            stringResource(R.string.label_cell_voltage_difference), Format.num(state.cellVoltageDiffMv, unit = stringResource(R.string.unit_millivolt)),
            stringResource(R.string.label_12v_voltage), Format.num(state.voltage12V, 1, stringResource(R.string.unit_volt)),
        )
        MetricPair(
            stringResource(R.string.label_battery_current), Format.num(state.batteryCurrent, 1, stringResource(R.string.unit_ampere)),
            stringResource(R.string.label_battery_power), Format.num(state.batteryPower, 1, stringResource(R.string.unit_power)),
        )
        HorizontalDivider()
        MetricPair(
            stringResource(R.string.label_charge_connector), when (state.chargeGunConnected) {
                null -> Format.NA
                true -> stringResource(R.string.label_connected)
                false -> stringResource(R.string.label_not_connected)
            },
            stringResource(R.string.label_charging_status), Format.text(localizedVehicleValue(state.chargeStatus)),
        )
        MetricPair(
            stringResource(R.string.label_instant_ev_consumption), Format.num(state.instantElecConsumption, 1, stringResource(R.string.unit_electric_consumption)),
            stringResource(R.string.label_instant_fuel_consumption), Format.num(state.instantFuelConsumption, 1, stringResource(R.string.unit_fuel_consumption)),
        )
        MetricPair(
            stringResource(R.string.label_trip_ev_consumption), Format.num(state.tripElecConsumption, 1, stringResource(R.string.unit_electric_consumption)),
            stringResource(R.string.label_trip_fuel_consumption), Format.num(state.tripFuelConsumption, 1, stringResource(R.string.unit_fuel_consumption)),
        )
        MetricRow(stringResource(R.string.label_fuel_level), Format.num(state.fuelPercent, unit = "%"))
    }
}
