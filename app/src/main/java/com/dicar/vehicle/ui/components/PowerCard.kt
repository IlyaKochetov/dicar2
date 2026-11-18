package com.dicar.vehicle.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.dicar.vehicle.ui.localizedVehicleValue
import com.dicar.vehicle.ui.localizedEnergyFlow
import androidx.compose.ui.res.stringResource
import com.dicar.vehicle.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dicar.vehicle.data.model.VehicleState
import com.dicar.vehicle.util.Format

/** Driving and powertrain card: F01-F08 and F37 energy flow. */
@Composable
fun PowerCard(state: VehicleState, modifier: Modifier = Modifier) {
    DataCard(title = stringResource(R.string.label_driving_powertrain), modifier = modifier, trailing = { GearBadge(state.gear) }) {
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            BigMetric(Format.num(state.speed), stringResource(R.string.unit_speed), stringResource(R.string.label_speed))
            BigMetric(Format.num(state.power, 1), stringResource(R.string.unit_power), stringResource(R.string.label_total_power))
        }
        MetricPair(
            stringResource(R.string.label_engine_rpm), Format.num(state.engineRpm, unit = stringResource(R.string.unit_rpm)),
            stringResource(R.string.label_engine_power), Format.num(state.enginePower, 1, stringResource(R.string.unit_power)),
        )
        MetricPair(
            stringResource(R.string.label_front_motor_rpm), Format.num(state.motorRpmFront, unit = stringResource(R.string.unit_rpm)),
            stringResource(R.string.label_rear_motor_rpm), Format.num(state.motorRpmRear, unit = stringResource(R.string.unit_rpm)),
        )
        MetricRow(stringResource(R.string.label_motor_power), Format.num(state.motorPower, 1, stringResource(R.string.unit_power)))
        PercentBar(stringResource(R.string.label_accelerator_position), state.accelerator, MaterialTheme.colorScheme.secondary)
        PercentBar(stringResource(R.string.label_brake_position), state.brake, MaterialTheme.colorScheme.error)
        HorizontalDivider()
        MetricPair(
            stringResource(R.string.label_powertrain_mode), Format.text(localizedVehicleValue(state.workMode)),
            stringResource(R.string.label_drive_mode), Format.text(localizedVehicleValue(state.driveMode)),
        )
        MetricRow(stringResource(R.string.label_energy_flow), localizedEnergyFlow(state.energyFlow) ?: Format.NA)
    }
}

@Composable
private fun GearBadge(gear: String?) {
    Box(
        Modifier
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            gear ?: "-",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}
