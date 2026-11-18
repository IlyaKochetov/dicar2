package com.dicar.vehicle.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.dicar.vehicle.R
import com.dicar.vehicle.data.FloatingBlock
import com.dicar.vehicle.data.SourceMode
import com.dicar.vehicle.data.model.*
import com.dicar.vehicle.ui.screen.MainTab


@Composable
fun displayOnOff(on: Boolean?): String = when (on) {
    null -> com.dicar.vehicle.util.Format.NA
    true -> stringResource(R.string.display_open)
    false -> stringResource(R.string.display_closed)
}

@Composable
fun displayOpenClose(open: Boolean?): String = when (open) {
    null -> com.dicar.vehicle.util.Format.NA
    true -> stringResource(R.string.display_on)
    false -> stringResource(R.string.display_off)
}

@Composable
fun displayLevel(level: Int?): String = when (level) {
    null -> com.dicar.vehicle.util.Format.NA
    0 -> stringResource(R.string.display_off)
    else -> stringResource(R.string.fmt_level, level)
}

@Composable
fun localizedEnergyFlow(value: String?): String? = when (value) {
    "External power → Battery" -> stringResource(R.string.flow_external)
    "Engine → Battery (charging)" -> stringResource(R.string.flow_engine_charging)
    "Wheels → Motor → Battery (regen)" -> stringResource(R.string.flow_regen)
    "Engine + Battery → Wheels" -> stringResource(R.string.flow_engine_battery)
    "Engine → Wheels" -> stringResource(R.string.flow_engine)
    "Battery → Motor → Wheels" -> stringResource(R.string.flow_battery)
    "Stationary / no significant energy flow" -> stringResource(R.string.flow_stationary)
    else -> value
}

@Composable
fun localizedVehicleValue(value: String?): String? = when (value) {
    "Stopped" -> stringResource(R.string.vehicle_value_stopped)
    "Forced EV" -> stringResource(R.string.vehicle_value_forced_ev)
    "Battery hold" -> stringResource(R.string.vehicle_value_battery_hold)
    "Eco" -> stringResource(R.string.vehicle_value_eco)
    "Sport" -> stringResource(R.string.vehicle_value_sport)
    "Normal" -> stringResource(R.string.vehicle_value_normal)
    "Snow" -> stringResource(R.string.vehicle_value_snow)
    "Mud" -> stringResource(R.string.vehicle_value_mud)
    "Sand" -> stringResource(R.string.vehicle_value_sand)
    "Unplugged" -> stringResource(R.string.vehicle_value_unplugged)
    "AC connector" -> stringResource(R.string.vehicle_value_ac_connector)
    "DC connector" -> stringResource(R.string.vehicle_value_dc_connector)
    "Vehicle-to-load" -> stringResource(R.string.vehicle_value_vehicle_to_load)
    "Not charging" -> stringResource(R.string.vehicle_value_not_charging)
    "Charging" -> stringResource(R.string.vehicle_value_charging)
    "Charge complete" -> stringResource(R.string.vehicle_value_charge_complete)
    "Discharging" -> stringResource(R.string.vehicle_value_discharging)
    "Charging stopped" -> stringResource(R.string.vehicle_value_charging_stopped)
    "Scheduled charging" -> stringResource(R.string.vehicle_value_scheduled_charging)
    "Charging timed out" -> stringResource(R.string.vehicle_value_charging_timed_out)
    "Discharging complete" -> stringResource(R.string.vehicle_value_discharging_complete)
    "Charging paused" -> stringResource(R.string.vehicle_value_charging_paused)
    else -> when {
        value?.startsWith("Mode ") == true -> stringResource(R.string.vehicle_value_mode, value.removePrefix("Mode "))
        value?.startsWith("Status ") == true -> stringResource(R.string.vehicle_value_status, value.removePrefix("Status "))
        else -> value
    }
}

@Composable
fun DataSourceType.localizedLabel(): String = stringResource(when (this) {
    DataSourceType.BYD_AUTO -> R.string.source_byd_auto
    DataSourceType.DI_PLUS -> R.string.source_di_plus
    DataSourceType.MOCK -> R.string.source_mock
})

@Composable
fun AcWindMode.localizedLabel(): String = stringResource(when (this) {
    AcWindMode.FACE -> R.string.enum_acwindmode_face
    AcWindMode.FACE_FEET -> R.string.enum_acwindmode_face_feet
    AcWindMode.FEET -> R.string.enum_acwindmode_feet
    AcWindMode.FEET_DEFROST -> R.string.enum_acwindmode_feet_defrost
    AcWindMode.DEFROST -> R.string.enum_acwindmode_defrost
})

@Composable
fun AcCycleMode.localizedLabel(): String = stringResource(when (this) {
    AcCycleMode.INNER -> R.string.enum_accyclemode_inner
    AcCycleMode.OUTER -> R.string.enum_accyclemode_outer
})

@Composable
fun GlassZone.localizedLabel(): String = stringResource(when (this) {
    GlassZone.WINDOW_FL -> R.string.enum_glasszone_window_fl
    GlassZone.WINDOW_FR -> R.string.enum_glasszone_window_fr
    GlassZone.WINDOW_RL -> R.string.enum_glasszone_window_rl
    GlassZone.WINDOW_RR -> R.string.enum_glasszone_window_rr
    GlassZone.SUNROOF -> R.string.enum_glasszone_sunroof
    GlassZone.SUNSHADE -> R.string.enum_glasszone_sunshade
})

@Composable
fun QuickAction.localizedLabel(): String = stringResource(when (this) {
    QuickAction.PURIFY -> R.string.enum_quickaction_purify
    QuickAction.QUICK_COOL -> R.string.enum_quickaction_quick_cool
    QuickAction.FRONT_DEFROST -> R.string.enum_quickaction_front_defrost
})

@Composable
fun FloatingBlock.localizedLabel(): String = stringResource(when (this) {
    FloatingBlock.READOUT -> R.string.enum_floatingblock_readout
    FloatingBlock.GAUGES -> R.string.enum_floatingblock_gauges
    FloatingBlock.CHART -> R.string.enum_floatingblock_chart
    FloatingBlock.CAR -> R.string.enum_floatingblock_car
    FloatingBlock.DETAILS -> R.string.enum_floatingblock_details
})

@Composable
fun SourceMode.localizedLabel(): String = stringResource(when (this) {
    SourceMode.AUTO -> R.string.enum_sourcemode_auto
    SourceMode.BYD_AUTO -> R.string.enum_sourcemode_byd_auto
    SourceMode.DI_PLUS -> R.string.enum_sourcemode_di_plus
    SourceMode.MOCK -> R.string.enum_sourcemode_mock
})

@Composable
fun MainTab.localizedLabel(): String = stringResource(when (this) {
    MainTab.TWIN -> R.string.enum_maintab_twin
    MainTab.CARDS -> R.string.enum_maintab_cards
    MainTab.HARDWARE -> R.string.enum_maintab_hardware
})

