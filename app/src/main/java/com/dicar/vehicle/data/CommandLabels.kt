package com.dicar.vehicle.data

import android.content.Context
import com.dicar.vehicle.R
import com.dicar.vehicle.data.model.*

internal fun Context.commandLabel(command: VehicleCommand): String {
    fun zone(zone: Zone) = getString(if (zone == Zone.DRIVER) R.string.cmd_driver else R.string.cmd_passenger)
    fun wind(mode: AcWindMode) = getString(when (mode) {
        AcWindMode.FACE -> R.string.enum_acwindmode_face
        AcWindMode.FACE_FEET -> R.string.enum_acwindmode_face_feet
        AcWindMode.FEET -> R.string.enum_acwindmode_feet
        AcWindMode.FEET_DEFROST -> R.string.enum_acwindmode_feet_defrost
        AcWindMode.DEFROST -> R.string.enum_acwindmode_defrost
    })
    fun cycle(mode: AcCycleMode) = getString(if (mode == AcCycleMode.INNER) R.string.enum_accyclemode_inner else R.string.enum_accyclemode_outer)
    fun glass(zone: GlassZone) = getString(when (zone) {
        GlassZone.WINDOW_FL -> R.string.enum_glasszone_window_fl
        GlassZone.WINDOW_FR -> R.string.enum_glasszone_window_fr
        GlassZone.WINDOW_RL -> R.string.enum_glasszone_window_rl
        GlassZone.WINDOW_RR -> R.string.enum_glasszone_window_rr
        GlassZone.SUNROOF -> R.string.enum_glasszone_sunroof
        GlassZone.SUNSHADE -> R.string.enum_glasszone_sunshade
    })
    return when (command) {
        is VehicleCommand.AcPower -> getString(if (command.on) R.string.cmd_climate_on else R.string.cmd_climate_off)
        is VehicleCommand.AcAuto -> getString(if (command.on) R.string.cmd_auto_on else R.string.cmd_auto_off)
        is VehicleCommand.AcTemperature -> getString(R.string.cmd_temperature, zone(command.zone), command.celsius.toString())
        is VehicleCommand.AcFanLevel -> getString(R.string.cmd_fan, command.level)
        is VehicleCommand.AcWind -> getString(R.string.cmd_airflow, wind(command.mode))
        is VehicleCommand.AcCycle -> cycle(command.mode)
        is VehicleCommand.SeatHeat -> getString(R.string.cmd_heat, zone(command.seat), command.level)
        is VehicleCommand.SeatVent -> getString(R.string.cmd_vent, zone(command.seat), command.level)
        is VehicleCommand.Glass -> getString(if (command.open) R.string.cmd_glass_open else R.string.cmd_glass_close, glass(command.zone))
        is VehicleCommand.Quick -> getString(when (command.action) {
            QuickAction.PURIFY -> R.string.enum_quickaction_purify
            QuickAction.QUICK_COOL -> R.string.enum_quickaction_quick_cool
            QuickAction.FRONT_DEFROST -> R.string.enum_quickaction_front_defrost
        })
    }
}
