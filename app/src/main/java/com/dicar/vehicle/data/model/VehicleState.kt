package com.dicar.vehicle.data.model

/**
 * Vehicle state snapshot. Nullable fields use null for unsupported, failed or invalid readings; the UI displays N/A.
 *
 * Data sources fill what they can; do not substitute defaults, since zero and unavailable have different meanings.
 */
data class VehicleState(
    // ---------------- Powertrain and driving (F01-F08) ----------------
    val speed: Float? = null,                 // km/h
    val engineRpm: Int? = null,               // rpm
    val motorRpmFront: Int? = null,           // rpm
    val motorRpmRear: Int? = null,            // rpm
    val power: Float? = null,                 // kW, total vehicle power (positive = drive, negative = regeneration)
    val motorPower: Float? = null,            // kW
    val enginePower: Float? = null,           // kW
    val accelerator: Float? = null,           // %，0-100
    val brake: Float? = null,                 // %，0-100
    val gear: String? = null,                 // P/R/N/D/S...
    val workMode: String? = null,             // EV / HEV / forced EV ...
    val driveMode: String? = null,            // ECO / Normal / Sport ...

    // ---------------- Battery and energy (F09-F17) ----------------
    val soc: Float? = null,                   // %
    val batteryTempMax: Float? = null,        // ℃
    val batteryTempMin: Float? = null,        // ℃
    val batteryTempAvg: Float? = null,        // ℃
    val batteryVoltage: Float? = null,        // V, traction battery pack voltage
    val cellVoltageMax: Float? = null,        // V
    val cellVoltageMin: Float? = null,        // V
    val batteryCurrent: Float? = null,        // A (positive = discharge, negative = charge)
    val batteryPower: Float? = null,          // kW (positive = discharge, negative = charging/regeneration)
    val chargeGunConnected: Boolean? = null,
    val chargeStatus: String? = null,         // Charging / complete / not charging ...
    val remainRangeElec: Int? = null,         // km
    val remainRangeFuel: Int? = null,         // km
    val fuelPercent: Float? = null,           // %
    val instantElecConsumption: Float? = null,  // kWh/100km
    val instantFuelConsumption: Float? = null,  // L/100km
    val tripElecConsumption: Float? = null,     // kWh/100km
    val tripFuelConsumption: Float? = null,     // L/100km
    val voltage12V: Float? = null,            // V

    // ---------------- Climate and comfort (F18-F25) ----------------
    val acOn: Boolean? = null,
    val acAuto: Boolean? = null,
    val acTempDriver: Float? = null,          // °C, 0.5 increments
    val acTempPassenger: Float? = null,       // ℃
    val acFanLevel: Int? = null,              // 0-7
    val acWindMode: AcWindMode? = null,
    val acCycle: AcCycleMode? = null,
    val insideTemp: Float? = null,            // ℃
    val outsideTemp: Float? = null,           // ℃
    val seatHeatDriver: Int? = null,          // 0 = off, levels 1–3
    val seatHeatPassenger: Int? = null,
    val seatVentDriver: Int? = null,
    val seatVentPassenger: Int? = null,

    // ---------------- Body and safety (F26-F32) ----------------
    val doors: Openings = Openings(),
    val windowPercent: Wheels<Int> = Wheels(),  // Four door window openings, %
    val sunroofPercent: Int? = null,
    val sunshadePercent: Int? = null,
    val tirePressure: Wheels<Float> = Wheels(), // kPa
    val tireTemp: Wheels<Float> = Wheels(),     // ℃
    val steeringAngle: Float? = null,           // degrees, negative left and positive right
    val seatbeltDriver: Boolean? = null,        // true = fastened
    val seatbeltPassenger: Boolean? = null,
    val passengerPresent: Boolean? = null,      // Passenger detection: the belt signal is unreliable when unoccupied
    val windowAntiPinch: Boolean? = null,       // Window anti-pinch
    val turnLeft: Boolean? = null,
    val turnRight: Boolean? = null,
    val radar: Radar = Radar(),

    // ---------------- Other (F33-F37) ----------------
    val totalMileage: Float? = null,          // km
    val tripMileage: Float? = null,           // km
    val altitude: Float? = null,              // m
    val slope: Float? = null,                 // °
    val pm25: Int? = null,                    // μg/m³

    // ---------------- Metadata ----------------
    val acTempStep: Float = 0.5f,             // Temperature step supported by the source (BYDAuto accepts integer °C only)
    val seatMaxLevel: Int = 3,                // Maximum seat heating/ventilation level supported by the source (DiPlus has two)
    val source: DataSourceType? = null,
    val timestamp: Long = 0L,
    val error: String? = null,                // Explanation when the entire source is unavailable
) {
    /** Dashboard RPM: prefer engine RPM, otherwise use the motor RPM with the larger absolute value. */
    val displayRpm: Int?
        get() = engineRpm ?: listOfNotNull(motorRpmFront, motorRpmRear).maxByOrNull { kotlin.math.abs(it) }

    val displayRpmLabel: String
        get() = if (engineRpm != null) "Engine RPM" else "Motor RPM"

    /** Cell voltage difference (mV), derived from maximum and minimum cell voltages. */
    val cellVoltageDiffMv: Float?
        get() = if (cellVoltageMax != null && cellVoltageMin != null) {
            (cellVoltageMax - cellVoltageMin) * 1000f
        } else null

    /** F37 energy flow, roughly inferred from power and RPM; null when data is insufficient. */
    val energyFlow: String?
        get() {
            val engineRunning = (engineRpm ?: 0) > 300
            val motorRpm = maxOf(motorRpmFront ?: 0, motorRpmRear ?: 0)
            val batPower = batteryPower ?: power ?: return when {
                chargeStatus != null && chargeGunConnected == true -> "External power → Battery"
                else -> null
            }
            return when {
                chargeGunConnected == true && batPower < -0.5f -> "External power → Battery"
                batPower < -0.5f && engineRunning -> "Engine → Battery (charging)"
                batPower < -0.5f -> "Wheels → Motor → Battery (regen)"
                engineRunning && motorRpm > 0 -> "Engine + Battery → Wheels"
                engineRunning -> "Engine → Wheels"
                batPower > 0.5f -> "Battery → Motor → Wheels"
                else -> "Stationary / no significant energy flow"
            }
        }
}

/**
 * Fill derivable fields from existing readings only when null, without overwriting source values:
 * - Battery power = pack voltage × current;
 * - Instant consumption = power / speed × 100 (kW / km/h = kWh/km); omitted at very low speed.
 */
fun VehicleState.withDerivedValues(): VehicleState {
    val batPower = batteryPower
        ?: if (batteryVoltage != null && batteryCurrent != null) batteryVoltage * batteryCurrent / 1000f else null
    val instant = instantElecConsumption ?: run {
        val p = batPower ?: power
        val v = speed
        if (p != null && v != null && v >= 5f) p / v * 100f else null
    }
    return if (batPower == batteryPower && instant == instantElecConsumption) this
    else copy(batteryPower = batPower, instantElecConsumption = instant)
}

/**
 * Parking radar probe obstacle levels (BYDAutoRadarDevice.getRadarProbeState(zone)).
 *
 * Head-unit constants: RADAR_OBSTACLE_DISTANCE_MIN=0 / MAX=6 (lower is closer), SAFE=14 (clear).
 * The mapping from levels to centimeters has not been verified on a vehicle; display raw levels. [?]
 */
data class Radar(
    val frontLeft: Int? = null,
    val frontLeftMid: Int? = null,
    val frontRightMid: Int? = null,
    val frontRight: Int? = null,
    val rearLeft: Int? = null,
    val rearMid: Int? = null,
    val rearRight: Int? = null,
    val left: Int? = null,
    val right: Int? = null,
    val reverseSwitchOn: Boolean? = null,
) {
    /** The head unit uses 14 for clear; only 0..6 indicate an obstacle. */
    private fun level(v: Int?): Int? = v?.takeIf { it in OBSTACLE_MIN..OBSTACLE_MAX }

    val front: List<Int?> get() = listOf(level(frontLeft), level(frontLeftMid), level(frontRightMid), level(frontRight))
    val rear: List<Int?> get() = listOf(level(rearLeft), level(rearMid), level(rearRight))
    val sides: List<Int?> get() = listOf(level(left), level(right))

    /** Nearest obstacle level (lower is closer), or null when none is detected. */
    val nearest: Int? get() = (front + rear + sides).filterNotNull().minOrNull()

    val hasAnyReading: Boolean
        get() = listOf(frontLeft, frontLeftMid, frontRightMid, frontRight, rearLeft, rearMid, rearRight, left, right)
            .any { it != null }

    companion object {
        const val OBSTACLE_MIN = 0
        const val OBSTACLE_MAX = 6
        const val SAFE = 14
    }
}

/** Four-wheel/door container: FL front left, FR front right, RL rear left, RR rear right. */
data class Wheels<T>(
    val fl: T? = null,
    val fr: T? = null,
    val rl: T? = null,
    val rr: T? = null,
) {
    fun toList(): List<T?> = listOf(fl, fr, rl, rr)
}

/** Four doors and two lids: true means open. */
data class Openings(
    val fl: Boolean? = null,
    val fr: Boolean? = null,
    val rl: Boolean? = null,
    val rr: Boolean? = null,
    val hood: Boolean? = null,
    val trunk: Boolean? = null,
)

enum class AcWindMode(val label: String) {
    FACE("Face"),
    FACE_FEET("Face + feet"),
    FEET("Feet"),
    FEET_DEFROST("Feet + defrost"),
    DEFROST("Defrost"),
}

enum class AcCycleMode(val label: String) {
    INNER("Recirculate"),
    OUTER("Fresh air"),
}

enum class DataSourceType(val label: String) {
    BYD_AUTO("BYDAuto"),
    DI_PLUS("DiPlus"),
    MOCK("Mock"),
}
