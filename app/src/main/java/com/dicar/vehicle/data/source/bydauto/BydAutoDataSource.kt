package com.dicar.vehicle.data.source.bydauto

import android.content.Context
import android.util.Log
import com.dicar.vehicle.R
import com.dicar.vehicle.data.commandLabel
import com.dicar.vehicle.data.model.AcCycleMode
import com.dicar.vehicle.data.model.CommandResult
import com.dicar.vehicle.data.model.DataSourceType
import com.dicar.vehicle.data.model.Openings
import com.dicar.vehicle.data.model.Radar
import com.dicar.vehicle.data.model.QuickAction
import com.dicar.vehicle.data.model.VehicleCommand
import com.dicar.vehicle.data.model.VehicleState
import com.dicar.vehicle.data.model.Wheels
import com.dicar.vehicle.data.model.Zone
import com.dicar.vehicle.data.source.VehicleDataSource
import com.dicar.vehicle.data.source.bydauto.BydApiMap.Dev
import com.dicar.vehicle.data.source.bydauto.BydApiMap.Src
import com.dicar.vehicle.data.source.bydauto.BydDevice.CallResult
import com.dicar.vehicle.data.source.bydauto.adb.AdbTransport
import com.dicar.vehicle.util.ValueSanitizer
import kotlin.math.roundToInt

/**
 * Primary source: reflectively calls android.hardware.bydauto.* in the head-unit framework.
 *
 * Automatically select one of two channels (see [BydAccess]):
 * - [AdbBydAccess]: helper process under the debugging identity when the debug port is enabled;
 * - [InProcessBydAccess]: direct app-process access when permissions allow.
 *
 * [BydApiMap] configures interfaces and encodings; this class reads, converts and validates ranges.
 * For unavailable readings on a vehicle, run Settings → Probe BYDAuto interfaces and compare the report with BydApiMap.
 */
class BydAutoDataSource(private val context: Context, transport: AdbTransport) : VehicleDataSource {

    override val type = DataSourceType.BYD_AUTO

    private val featureIds = BydFeatureIds()
    private val adb = AdbBydAccess(transport)
    private val inProcess = InProcessBydAccess(context)

    private var active: BydAccess? = null
    private lateinit var current: BydAccess // Channel used for this read/execute

    private fun pickAccess(): BydAccess? = when {
        adb.probeAvailable() -> adb
        inProcess.probeAvailable() -> inProcess
        else -> null
    }

    override suspend fun isAvailable(): Boolean {
        active = pickAccess()
        return active != null
    }

    /**
     * Available channel for the BYDAuto probe; it must use the same channel to avoid
     * permission-denied in-process calls hiding real readings.
     */
    fun acquireAccess(): BydAccess? = runCatching { acquire() }.getOrNull()

    /** Establish or validate and select the channel; throw with a reason if unavailable. */
    private fun acquire(): BydAccess {
        val a = active ?: pickAccess()?.also { active = it }
            ?: throw IllegalStateException(context.getString(R.string.byd_debug_missing))
        a.ensureReady()
        current = a
        return a
    }

    // ------------------------------------------------------------------
    // Read
    // ------------------------------------------------------------------

    // Build in two passes: collect mapped sources and batch-fetch them, then populate from the cache.
    // This uses one cross-process round trip per poll instead of one per field.
    private var collecting = false
    private val collected = LinkedHashSet<Src>()
    private var cache: Map<Src, CallResult> = emptyMap()

    override suspend fun read(): VehicleState {
        acquire()

        collecting = true
        collected.clear()
        cache = emptyMap()
        buildState() // Discard the result; this pass only collects sources.
        collecting = false

        cache = fetchAll(collected.toList())
        return buildState()
    }

    private fun fetchAll(sources: List<Src>): Map<Src, CallResult> {
        val result = HashMap<Src, CallResult>(sources.size)
        val keys = ArrayList<Src>(sources.size)
        val requests = ArrayList<RawReq>(sources.size)
        for (src in sources) when (src) {
            is Src.Getter -> {
                keys += src
                requests += RawReq.Getter(src.dev.className, src.method, src.args.toIntArray())
            }
            is Src.Fid -> {
                val fid = featureIds.resolve(src.symbol)
                if (fid == null) result[src] = CallResult.Missing // The firmware lacks this symbol; no need to query the helper.
                else {
                    keys += src
                    requests += RawReq.Fid(src.dev.id, fid, src.isFloat)
                }
            }
        }
        val replies = current.readBatch(requests)
        keys.forEachIndexed { i, key -> result[key] = replies.getOrElse(i) { CallResult.Missing } }
        return result
    }

    private fun buildState(): VehicleState {
        val m = BydApiMap
        val tempOffset = m.BATTERY_TEMP_OFFSET.toDouble()
        val turnSignal = int(m.TURN_SIGNAL_MASK, 0, m.TURN_SIGNAL_MAX)

        return VehicleState(
            // Powertrain
            speed = float(m.SPEED, 0.0, 300.0),
            // Use the head unit's ENGINE_SPEED_MAX to reject the invalid 8191 (0x1FFF) marker during EV driving.
            engineRpm = int(m.ENGINE_RPM, 0, m.ENGINE_RPM_MAX),
            motorRpmFront = int(m.MOTOR_RPM_FRONT, -25_000, 25_000),
            motorRpmRear = int(m.MOTOR_RPM_REAR, -25_000, 25_000),
            power = float(m.POWER, -500.0, 1_000.0),
            accelerator = float(m.ACCELERATOR, 0.0, 100.0),
            brake = float(m.BRAKE, 0.0, 100.0),
            gear = int(m.GEAR, 0, 15)?.let { m.GEAR_LABELS[it] },
            workMode = int(m.WORK_MODE, 0, 15)?.let { m.WORK_MODE_LABELS[it] ?: "Mode $it" },
            driveMode = int(m.DRIVE_MODE, 0, 15)?.let { m.DRIVE_MODE_LABELS[it] ?: "Mode $it" },

            // Battery
            soc = float(m.SOC, 0.0, 100.0),
            batteryTempMax = float(m.BATTERY_TEMP_MAX, -40.0, 100.0, offset = tempOffset),
            batteryTempMin = float(m.BATTERY_TEMP_MIN, -40.0, 100.0, offset = tempOffset),
            batteryVoltage = float(m.BATTERY_VOLTAGE, 50.0, 1_000.0),
            cellVoltageMax = float(m.CELL_VOLTAGE_MAX, 1.0, 5.0, scale = 0.001),
            cellVoltageMin = float(m.CELL_VOLTAGE_MIN, 1.0, 5.0, scale = 0.001),
            batteryCurrent = float(m.BATTERY_CURRENT, -1_000.0, 1_000.0),
            chargeGunConnected = int(m.CHARGE_GUN, 1, 5)?.let { it >= 2 },
            chargeStatus = int(m.CHARGE_STATE, 0, 255)?.let { m.CHARGE_STATE_LABELS[it] ?: "Status $it" }
                ?: int(m.CHARGE_GUN, 1, 5)?.let { m.CHARGE_GUN_LABELS[it] },
            remainRangeElec = int(m.RANGE_ELEC, 0, 2_000),
            remainRangeFuel = int(m.RANGE_FUEL, 0, 2_000),
            fuelPercent = float(m.FUEL_PERCENT, 0.0, 100.0),
            instantElecConsumption = float(m.INSTANT_ELEC, 0.0, 100.0),
            instantFuelConsumption = float(m.INSTANT_FUEL, 0.0, 100.0),
            tripElecConsumption = float(m.TRIP_ELEC, 0.0, 100.0),
            tripFuelConsumption = float(m.TRIP_FUEL, 0.0, 100.0),
            voltage12V = float(m.VOLTAGE_12V, 6.0, 20.0),

            // Climate
            acOn = int(m.AC_ON, 0, 1)?.let { it == 1 },
            acAuto = int(m.AC_CONTROL_MODE, 0, 1)?.let { it == 0 },
            acTempDriver = float(m.AC_TEMP_DRIVER, 10.0, 40.0),
            acTempPassenger = float(m.AC_TEMP_PASSENGER, 10.0, 40.0),
            acFanLevel = int(m.AC_FAN_LEVEL, 0, 7),
            acWindMode = int(m.AC_WIND_MODE, 0, 15)?.let { code -> m.AC_WIND_MODE_CODES.entries.firstOrNull { it.value == code }?.key },
            acCycle = when (int(m.AC_CYCLE, 0, 1)) {
                m.AC_CYCLE_INNER -> AcCycleMode.INNER
                m.AC_CYCLE_OUTER -> AcCycleMode.OUTER
                else -> null
            },
            insideTemp = float(m.INSIDE_TEMP, -50.0, 80.0),
            outsideTemp = float(m.OUTSIDE_TEMP, -50.0, 80.0),
            seatHeatDriver = seatLevel(m.SEAT_HEAT_DRIVER),
            seatHeatPassenger = seatLevel(m.SEAT_HEAT_PASSENGER),
            seatVentDriver = seatLevel(m.SEAT_VENT_DRIVER),
            seatVentPassenger = seatLevel(m.SEAT_VENT_PASSENGER),

            // Body
            doors = Openings(
                fl = bool(m.door(m.DOOR_FL)), fr = bool(m.door(m.DOOR_FR)),
                rl = bool(m.door(m.DOOR_RL)), rr = bool(m.door(m.DOOR_RR)),
                hood = bool(m.door(m.DOOR_HOOD)), trunk = bool(m.door(m.DOOR_TRUNK)),
            ),
            windowPercent = Wheels(
                int(m.WINDOW_FL, 0, 100), int(m.WINDOW_FR, 0, 100),
                int(m.WINDOW_RL, 0, 100), int(m.WINDOW_RR, 0, 100),
            ),
            sunroofPercent = int(m.SUNROOF, 0, 100),
            sunshadePercent = int(m.SUNSHADE, 0, 100),
            tirePressure = Wheels(
                float(m.TIRE_PRESSURE_FL, 0.0, 500.0), float(m.TIRE_PRESSURE_FR, 0.0, 500.0),
                float(m.TIRE_PRESSURE_RL, 0.0, 500.0), float(m.TIRE_PRESSURE_RR, 0.0, 500.0),
            ),
            tireTemp = Wheels(
                float(m.TIRE_TEMP_FL, -40.0, 150.0), float(m.TIRE_TEMP_FR, -40.0, 150.0),
                float(m.TIRE_TEMP_RL, -40.0, 150.0), float(m.TIRE_TEMP_RR, -40.0, 150.0),
            ),
            steeringAngle = float(m.STEERING_ANGLE, -900.0, 900.0),
            seatbeltDriver = bool(m.SEATBELT_DRIVER),
            seatbeltPassenger = bool(m.SEATBELT_PASSENGER),
            passengerPresent = bool(m.PASSENGER_PRESENT),
            windowAntiPinch = int(m.WINDOW_ANTI_PINCH, 0, 10)?.let { it != m.WINDOW_NO_ANTI_PINCH },
            turnLeft = turnSignal?.let { it and m.TURN_LEFT_BIT != 0 },
            turnRight = turnSignal?.let { it and m.TURN_RIGHT_BIT != 0 },
            radar = Radar(
                frontLeft = radarLevel(m.radar(m.RADAR_LEFT_FRONT)),
                frontLeftMid = radarLevel(m.radar(m.RADAR_FRONT_LEFT_MID)),
                frontRightMid = radarLevel(m.radar(m.RADAR_FRONT_RIGHT_MID)),
                frontRight = radarLevel(m.radar(m.RADAR_RIGHT_FRONT)),
                rearLeft = radarLevel(m.radar(m.RADAR_LEFT_REAR)),
                rearMid = radarLevel(m.radar(m.RADAR_MIDDLE_REAR)),
                rearRight = radarLevel(m.radar(m.RADAR_RIGHT_REAR)),
                left = radarLevel(m.radar(m.RADAR_LEFT)),
                right = radarLevel(m.radar(m.RADAR_RIGHT)),
                reverseSwitchOn = bool(m.RADAR_REVERSE_SWITCH),
            ),

            // Other
            totalMileage = float(m.TOTAL_MILEAGE_KM, 0.0, 2_000_000.0)
                ?: float(m.TOTAL_MILEAGE_DECI_KM, 0.0, 2_000_000.0, scale = 0.1),
            pm25 = int(m.PM25, 0, 3_000),
            slope = float(m.SLOPE, -60.0, 60.0),

            acTempStep = m.AC_TEMP_STEP,
            seatMaxLevel = m.SEAT_MAX_LEVEL,
        )
    }

    // Try sources in order; use the first non-error value within range after conversion.
    private fun value(sources: List<Src>, min: Double, max: Double, scale: Double = 1.0, offset: Double = 0.0): Double? {
        for (src in sources) {
            val converted = ValueSanitizer.toDouble(rawValue(src))?.let { it * scale + offset } ?: continue
            if (converted in min..max) return converted
        }
        return null
    }

    private fun float(sources: List<Src>, min: Double, max: Double, scale: Double = 1.0, offset: Double = 0.0) =
        value(sources, min, max, scale, offset)?.toFloat()

    private fun int(sources: List<Src>, min: Int, max: Int) =
        value(sources, min.toDouble(), max.toDouble())?.toInt()

    private fun bool(sources: List<Src>) = int(sources, 0, 1)?.let { it == 1 }

    /** Seat states 1 off / 2 low / 3 high map to app levels 0/1/2. */
    private fun seatLevel(sources: List<Src>) =
        int(sources, 0, 3)?.let { (it - 1).coerceIn(0, BydApiMap.SEAT_MAX_LEVEL) }

    /** Radar obstacle levels 0..6 indicate distance; 14 means clear. Preserve raw values for [Radar]. */
    private fun radarLevel(sources: List<Src>) = int(sources, Radar.OBSTACLE_MIN, Radar.SAFE)

    private fun rawValue(src: Src): Any? {
        if (collecting) {
            collected += src // Return null during collection so every candidate source is registered.
            return null
        }
        val raw = (cache[src] as? CallResult.Value)?.value ?: return null
        return if (src is Src.Getter && src.index != null && raw is IntArray) raw.getOrNull(src.index) else raw
    }

    // ------------------------------------------------------------------
    // Control
    // ------------------------------------------------------------------

    override suspend fun execute(command: VehicleCommand): CommandResult {
        acquire()
        val m = BydApiMap
        return when (command) {
            is VehicleCommand.AcPower -> namedThenFid(
                Dev.AC, if (command.on) m.AC_START else m.AC_STOP, intArrayOf(m.AC_POWER_ARG),
                m.AC_POWER_SET_FID, null, if (command.on) 1 else 0,
            )

            is VehicleCommand.AcAuto -> {
                val mode = if (command.on) 0 else 1 // 0 automatic / 1 manual
                namedThenFid(Dev.AC, m.AC_SET_CONTROL_MODE, intArrayOf(mode, m.AC_SOURCE), m.AC_CONTROL_MODE_SET_FID, null, mode)
            }

            is VehicleCommand.AcTemperature -> {
                val zone = if (command.zone == Zone.DRIVER) m.AC_ZONE_DRIVER else m.AC_ZONE_PASSENGER
                val temp = command.celsius.roundToInt()
                val fidSym = if (command.zone == Zone.DRIVER) m.AC_TEMP_DRIVER_SET_FID else m.AC_TEMP_PASSENGER_SET_FID
                namedThenFid(Dev.AC, m.AC_SET_TEMP, intArrayOf(zone, temp, m.AC_SOURCE, m.AC_TEMP_UNIT_CELSIUS), fidSym, null, temp)
            }

            is VehicleCommand.AcFanLevel ->
                toCommandResult(writeFidSym(Dev.AC, m.AC_WIND_LEVEL_SET_FID, m.AC_WIND_LEVEL_SET_FALLBACK, command.level), "Fan")

            is VehicleCommand.AcWind -> {
                val code = m.AC_WIND_MODE_CODES[command.mode]
                    ?: return CommandResult.Unsupported(context.getString(R.string.byd_encoding_missing, context.commandLabel(command)))
                namedThenFid(Dev.AC, m.AC_SET_WIND_MODE, intArrayOf(code, m.AC_SOURCE), m.AC_WIND_MODE_SET_FID, null, code)
            }

            is VehicleCommand.AcCycle -> {
                val code = if (command.mode == AcCycleMode.INNER) m.AC_CYCLE_INNER else m.AC_CYCLE_OUTER
                namedThenFid(Dev.AC, m.AC_SET_CYCLE, intArrayOf(code, m.AC_SOURCE), m.AC_CYCLE_SET_FID, null, code)
            }

            is VehicleCommand.SeatHeat -> seat(command.seat, command.level, m.SEAT_SET_HEAT,
                if (command.seat == Zone.DRIVER) m.SEAT_HEAT_DRIVER_SET_FID else m.SEAT_HEAT_PASSENGER_SET_FID)

            is VehicleCommand.SeatVent -> seat(command.seat, command.level, m.SEAT_SET_VENT,
                if (command.seat == Zone.DRIVER) m.SEAT_VENT_DRIVER_SET_FID else m.SEAT_VENT_PASSENGER_SET_FID)

            is VehicleCommand.Glass -> {
                val symbol = m.GLASS_SET_FIDS[command.zone]
                    ?: return CommandResult.Unsupported(context.getString(R.string.byd_write_missing, context.commandLabel(command)))
                val percent = if (command.open) m.GLASS_OPEN_PERCENT else m.GLASS_CLOSE_PERCENT
                toCommandResult(writeFidSym(Dev.BODYWORK, symbol, null, percent), command.zone.label)
            }

            is VehicleCommand.Quick -> when (command.action) {
                QuickAction.FRONT_DEFROST -> namedThenFid(
                    Dev.AC, m.AC_SET_DEFROST, intArrayOf(1, 1, m.AC_SOURCE), m.AC_DEFROST_FRONT_SET_FID, null, 1)
                QuickAction.QUICK_COOL -> toCommandResult(writeFidSym(Dev.AC, m.AC_MAX_COOLING_SET_FID, null, 1), "Quick cool")
                QuickAction.PURIFY -> toCommandResult(writeFidSym(Dev.AC, m.AC_QUICK_CLEAN_SET_FID, null, 1), "Air purification")
            }
        }
    }

    /** Seat level n maps to state n+1 (1 off / 2 low / 3 high). Try the named API before FID. */
    private fun seat(zone: Zone, level: Int, method: String, fidSymbol: String): CommandResult {
        val area = if (zone == Zone.DRIVER) BydApiMap.SEAT_DRIVER else BydApiMap.SEAT_PASSENGER
        val state = level + 1
        return namedThenFid(Dev.SETTING, method, intArrayOf(area, state), fidSymbol, null, state)
    }

    private fun namedThenFid(dev: Dev, method: String, args: IntArray, fidSymbol: String, fallbackFid: Int?, fidValue: Int): CommandResult {
        val r = current.callSetter(dev.className, method, args)
        if (r is CallResult.Missing) return toCommandResult(writeFidSym(dev, fidSymbol, fallbackFid, fidValue), fidSymbol)
        return toCommandResult(r, method)
    }

    private fun writeFidSym(dev: Dev, symbol: String, fallbackFid: Int?, value: Int): CallResult {
        val fid = featureIds.resolve(symbol) ?: fallbackFid ?: return CallResult.Missing
        return current.writeFid(dev.id, fid, value)
    }

    private fun toCommandResult(result: CallResult, name: String): CommandResult = when (result) {
        CallResult.Missing -> CommandResult.Unsupported(context.getString(R.string.byd_interface_missing, name))
        is CallResult.Error -> CommandResult.Failed(
            if (result.error is SecurityException) context.getString(R.string.byd_permission_denied) else (result.error.message ?: result.error.toString())
        )
        is CallResult.Value -> {
            val code = (result.value as? Number)?.toInt()
            if (code != null && ValueSanitizer.isErrorCode(code)) CommandResult.Failed(context.getString(R.string.byd_return_code, code)) else CommandResult.Sent
        }
    }

    override fun release() {
        adb.close()
        Log.i(TAG, "released")
    }

    private companion object {
        const val TAG = "BydAutoDataSource"
    }
}
