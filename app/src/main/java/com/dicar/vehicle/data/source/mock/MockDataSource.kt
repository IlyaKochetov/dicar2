package com.dicar.vehicle.data.source.mock

import com.dicar.vehicle.data.model.AcCycleMode
import com.dicar.vehicle.data.model.AcWindMode
import com.dicar.vehicle.data.model.CommandResult
import com.dicar.vehicle.data.model.DataSourceType
import com.dicar.vehicle.data.model.Openings
import com.dicar.vehicle.data.model.QuickAction
import com.dicar.vehicle.data.model.Radar
import com.dicar.vehicle.data.model.VehicleCommand
import com.dicar.vehicle.data.model.VehicleState
import com.dicar.vehicle.data.model.Wheels
import com.dicar.vehicle.data.model.Zone
import com.dicar.vehicle.data.source.VehicleDataSource
import kotlin.math.abs
import kotlin.math.sin

/**
 * Mock data source for UI development on an emulator or phone without a head unit.
 * Driving data varies sinusoidally; commands can change climate state to verify optimistic updates and readback.
 * Tire temperature, PM2.5 and altitude are deliberately null to check N/A display.
 */
class MockDataSource : VehicleDataSource {

    override val type = DataSourceType.MOCK

    private val startedAt = System.currentTimeMillis()
    private var ac = VehicleState(
        acOn = true,
        acAuto = false,
        acTempDriver = 22.0f,
        acTempPassenger = 22.5f,
        acFanLevel = 3,
        acWindMode = AcWindMode.FACE,
        acCycle = AcCycleMode.OUTER,
        seatHeatDriver = 0,
        seatHeatPassenger = 0,
        seatVentDriver = 1,
        seatVentPassenger = 0,
    )

    override suspend fun isAvailable() = true

    override suspend fun read(): VehicleState {
        val t = (System.currentTimeMillis() - startedAt) / 1000.0
        // The lower bound must reach zero so parking, reversing, radar and stationary energy flow
        // can be exercised without a vehicle.
        val speed = (50 + 62 * sin(t / 9)).toFloat().coerceAtLeast(0f)
        val accel = (35 + 35 * sin(t / 3)).toFloat().coerceIn(0f, 100f)
        val brake = if (accel < 8f) 30f else 0f
        val motorPower = (accel * 1.6f - brake * 0.8f)
        val engineOn = speed > 85f
        val soc = (78.6 - t / 600).toFloat()
        return ac.copy(
            speed = speed,
            engineRpm = if (engineOn) (1400 + speed * 12).toInt() else 0,
            motorRpmFront = (speed * 95).toInt(),
            motorRpmRear = null,
            power = motorPower + if (engineOn) 25f else 0f,
            motorPower = motorPower,
            enginePower = if (engineOn) 25f else 0f,
            accelerator = accel,
            brake = brake,
            gear = "D",
            workMode = if (engineOn) "HEV" else "EV",
            driveMode = "ECO",
            soc = soc,
            batteryTempMax = 31.0f,
            batteryTempMin = 28.0f,
            batteryTempAvg = 29.5f,
            batteryVoltage = 535.2f,
            cellVoltageMax = 3.342f,
            cellVoltageMin = 3.329f,
            batteryCurrent = motorPower * 1000f / 535.2f,
            batteryPower = motorPower,
            chargeGunConnected = false,
            chargeStatus = "Not charging",
            remainRangeElec = (soc * 1.2f).toInt(),
            remainRangeFuel = 820,
            fuelPercent = 64f,
            instantElecConsumption = abs(motorPower) / speed.coerceAtLeast(1f) * 100f,
            instantFuelConsumption = if (engineOn) 5.2f else 0f,
            tripElecConsumption = 14.8f,
            tripFuelConsumption = 1.9f,
            voltage12V = 13.8f,
            insideTemp = 24.5f,
            outsideTemp = 31.0f,
            // Cycle body openings periodically to exercise door and window animations without a vehicle.
            doors = bodyCycle(t),
            windowPercent = windowCycle(t),
            radar = radarCycle(t, speed),
            sunroofPercent = 0,
            sunshadePercent = 40,
            tirePressure = Wheels(250f, 252f, 248f, 249f),
            tireTemp = Wheels(),
            steeringAngle = (15 * sin(t / 2)).toFloat(),
            seatbeltDriver = true,
            seatbeltPassenger = false,
            passengerPresent = false, // Unoccupied passenger seat: show Unoccupied rather than Unfastened.
            windowAntiPinch = true,
            turnLeft = (t.toInt() % 6) < 1,
            turnRight = false,
            totalMileage = 12_345.6f,
            tripMileage = (t / 60).toFloat(),
            slope = 1.5f,
        )
    }

    override suspend fun execute(command: VehicleCommand): CommandResult {
        // Simulate head-unit command latency.
        Thread.sleep(150)
        when (command) {
            is VehicleCommand.Quick -> if (command.action == QuickAction.PURIFY) {
                return CommandResult.Unsupported("Mock: air purification unavailable")
            }
            is VehicleCommand.SeatVent -> if (command.seat == Zone.PASSENGER) {
                return CommandResult.Unsupported("Mock: passenger ventilation unavailable")
            }
            else -> Unit
        }
        ac = command.applyTo(ac)
        return CommandResult.Sent
    }

    /**
     * Door and lid demo: every [BODY_PERIOD] seconds, open each door, hood and trunk in turn,
     * then close all of them to exercise the twin view without a vehicle.
     */
    private fun bodyCycle(t: Double): Openings {
        val phase = ((t % BODY_PERIOD) / BODY_PERIOD * 7).toInt()
        return Openings(
            fl = phase == 1,
            fr = phase == 2,
            rl = phase == 3,
            rr = phase == 3,
            hood = phase == 4,
            trunk = phase == 5,
        )
    }

    /**
     * Radar demo: treat low speed as reversing and move rear probe readings from clear to level 0 and back.
     */
    private fun radarCycle(t: Double, speed: Float): Radar {
        val parking = speed < 12f
        if (!parking) return Radar(reverseSwitchOn = false)
        // Sweep 0..6 and back; lower is closer.
        val sweep = ((1 - kotlin.math.cos(t / 4)) / 2 * Radar.OBSTACLE_MAX).toInt()
            .coerceIn(Radar.OBSTACLE_MIN, Radar.OBSTACLE_MAX)
        return Radar(
            frontLeft = Radar.SAFE,
            frontLeftMid = Radar.SAFE,
            frontRightMid = Radar.SAFE,
            frontRight = Radar.SAFE,
            rearLeft = (sweep + 1).coerceAtMost(Radar.OBSTACLE_MAX),
            rearMid = sweep,
            rearRight = (sweep + 2).coerceAtMost(Radar.OBSTACLE_MAX),
            left = Radar.SAFE,
            right = Radar.SAFE,
            reverseSwitchOn = true,
        )
    }

    /** Window demo: the front-left window moves slowly; the other three stay at fixed openings. */
    private fun windowCycle(t: Double): Wheels<Int> {
        val wave = ((1 - kotlin.math.cos(t / 7)) / 2 * 100).toInt().coerceIn(0, 100)
        return Wheels(fl = wave, fr = 0, rl = 35, rr = 0)
    }

    private companion object {
        const val BODY_PERIOD = 28.0
    }
}
