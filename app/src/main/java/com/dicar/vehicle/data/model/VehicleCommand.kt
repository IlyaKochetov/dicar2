package com.dicar.vehicle.data.model

/**
 * Vehicle control command.
 *
 * Each command provides:
 * - [key]: commands with the same key are debounced (only the last of three temperature increments is sent);
 * - [applyTo]: optimistic UI update before sending, avoiding a jump to the old value while awaiting readback;
 * - [expected] / [readBack]: confirmation when a poll returns the expected value;
 *   null readback cannot confirm application and is reported only as sent.
 */
sealed interface VehicleCommand {
    val key: String
    val label: String
    val expected: Any?
    fun readBack(state: VehicleState): Any?
    fun applyTo(state: VehicleState): VehicleState

    data class AcPower(val on: Boolean) : VehicleCommand {
        override val key = "ac_power"
        override val label = if (on) "Climate on" else "Climate off"
        override val expected = on
        override fun readBack(state: VehicleState) = state.acOn
        override fun applyTo(state: VehicleState) = state.copy(acOn = on)
    }

    data class AcAuto(val on: Boolean) : VehicleCommand {
        override val key = "ac_auto"
        override val label = if (on) "Auto climate" else "Auto climate off"
        override val expected = on
        override fun readBack(state: VehicleState) = state.acAuto
        override fun applyTo(state: VehicleState) = state.copy(acAuto = on)
    }

    data class AcTemperature(val zone: Zone, val celsius: Float) : VehicleCommand {
        override val key = "ac_temp_$zone"
        override val label = "${zone.label} temperature $celsius℃"
        override val expected = celsius
        override fun readBack(state: VehicleState) = when (zone) {
            Zone.DRIVER -> state.acTempDriver
            Zone.PASSENGER -> state.acTempPassenger
        }
        override fun applyTo(state: VehicleState) = when (zone) {
            Zone.DRIVER -> state.copy(acTempDriver = celsius)
            Zone.PASSENGER -> state.copy(acTempPassenger = celsius)
        }
    }

    data class AcFanLevel(val level: Int) : VehicleCommand {
        override val key = "ac_fan"
        override val label = "Fan level $level"
        override val expected = level
        override fun readBack(state: VehicleState) = state.acFanLevel
        override fun applyTo(state: VehicleState) = state.copy(acFanLevel = level)
    }

    data class AcWind(val mode: AcWindMode) : VehicleCommand {
        override val key = "ac_wind"
        override val label = "Airflow ${mode.label}"
        override val expected = mode
        override fun readBack(state: VehicleState) = state.acWindMode
        override fun applyTo(state: VehicleState) = state.copy(acWindMode = mode)
    }

    data class AcCycle(val mode: AcCycleMode) : VehicleCommand {
        override val key = "ac_cycle"
        override val label = mode.label
        override val expected = mode
        override fun readBack(state: VehicleState) = state.acCycle
        override fun applyTo(state: VehicleState) = state.copy(acCycle = mode)
    }

    data class SeatHeat(val seat: Zone, val level: Int) : VehicleCommand {
        override val key = "seat_heat_$seat"
        override val label = "${seat.label} seat heating level $level"
        override val expected = level
        override fun readBack(state: VehicleState) = when (seat) {
            Zone.DRIVER -> state.seatHeatDriver
            Zone.PASSENGER -> state.seatHeatPassenger
        }
        override fun applyTo(state: VehicleState) = when (seat) {
            Zone.DRIVER -> state.copy(seatHeatDriver = level)
            Zone.PASSENGER -> state.copy(seatHeatPassenger = level)
        }
    }

    data class SeatVent(val seat: Zone, val level: Int) : VehicleCommand {
        override val key = "seat_vent_$seat"
        override val label = "${seat.label} seat ventilation level $level"
        override val expected = level
        override fun readBack(state: VehicleState) = when (seat) {
            Zone.DRIVER -> state.seatVentDriver
            Zone.PASSENGER -> state.seatVentPassenger
        }
        override fun applyTo(state: VehicleState) = when (seat) {
            Zone.DRIVER -> state.copy(seatVentDriver = level)
            Zone.PASSENGER -> state.copy(seatVentPassenger = level)
        }
    }

    /**
     * Window, sunroof or shade control writes a target opening (0 closed, 100 fully open).
     * Travel takes several seconds, beyond the three-second readback window, so only sending is reported.
     */
    data class Glass(val zone: GlassZone, val open: Boolean) : VehicleCommand {
        override val key = "glass_$zone"
        override val label = "${zone.label} ${if (open) "open" else "close"}"
        override val expected: Any? = null
        override fun readBack(state: VehicleState): Any? = null
        override fun applyTo(state: VehicleState) = state // Polling reports actual opening; do not update optimistically.
    }

    /** Quick actions (purification, fast cooling, front defrost) have no readback; finish when sent. */
    data class Quick(val action: QuickAction) : VehicleCommand {
        override val key = "quick_$action"
        override val label = action.label
        override val expected: Any? = null
        override fun readBack(state: VehicleState): Any? = null
        override fun applyTo(state: VehicleState) = state
    }
}

enum class Zone(val label: String) { DRIVER("Driver"), PASSENGER("Passenger") }

/** Openable glass component. */
enum class GlassZone(val label: String) {
    WINDOW_FL("Front left window"), WINDOW_FR("Front right window"), WINDOW_RL("Rear left window"), WINDOW_RR("Rear right window"),
    SUNROOF("Sunroof"), SUNSHADE("Sunshade"),
}

enum class QuickAction(val label: String) {
    PURIFY("Purify air"),
    QUICK_COOL("Quick cool"),
    FRONT_DEFROST("Front defrost"),
}

/** Data source command result, indicating only dispatch; the repository confirms application through readback. */
sealed interface CommandResult {
    data object Sent : CommandResult
    data class Unsupported(val reason: String) : CommandResult
    data class Failed(val reason: String) : CommandResult
}
