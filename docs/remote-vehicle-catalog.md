# DiCar Remote — vehicle data and command catalog

This catalog defines the vehicle data and commands for the [server specification](https://github.com/IlyaKochetov/dicar2/blob/spec/python-remote-backend/docs/server-specification.md). The remote connector is not yet implemented. Pairing, authorization, retention and safety requirements in the server specification apply.

## State available for an allowlisted snapshot

All observations are nullable. Null means unavailable, invalid or unsupported, not zero or false. Support varies by source, model and firmware; a field in the Android model is not evidence that a particular car supplies it. Use stable language-neutral wire names and enums rather than the model's display strings. The names below define the JSON fields.

| Group | JSON fields (unit where applicable) | Android model |
| --- | --- | --- |
| Driving | speed_kmh, engine_rpm, motor_rpm_front, motor_rpm_rear, power_kw, motor_power_kw, engine_power_kw, accelerator_percent, brake_percent, gear, work_mode, drive_mode | speed, engineRpm, motorRpmFront, motorRpmRear, power, motorPower, enginePower, accelerator, brake, gear, workMode, driveMode |
| Battery | soc_percent, battery_temp_max_c, battery_temp_min_c, battery_temp_avg_c, battery_voltage_v, cell_voltage_max_v, cell_voltage_min_v, battery_current_a, battery_power_kw | soc, batteryTempMax, batteryTempMin, batteryTempAvg, batteryVoltage, cellVoltageMax, cellVoltageMin, batteryCurrent, batteryPower |
| Charging and range | charge_connector_connected, charging_status, electric_range_km, fuel_range_km, fuel_percent, voltage_12v | chargeGunConnected, chargeStatus, remainRangeElec, remainRangeFuel, fuelPercent, voltage12V |
| Consumption | instant_electric_kwh_per_100km, instant_fuel_l_per_100km, trip_electric_kwh_per_100km, trip_fuel_l_per_100km | instantElecConsumption, instantFuelConsumption, tripElecConsumption, tripFuelConsumption |
| Climate | climate_on, climate_auto, driver_target_c, passenger_target_c, fan_level, wind_mode, cycle_mode, inside_temp_c, outside_temp_c | acOn, acAuto, acTempDriver, acTempPassenger, acFanLevel, acWindMode, acCycle, insideTemp, outsideTemp |
| Seats | driver_heat_level, passenger_heat_level, driver_vent_level, passenger_vent_level, driver_belt_fastened, passenger_belt_fastened, passenger_present | seatHeatDriver, seatHeatPassenger, seatVentDriver, seatVentPassenger, seatbeltDriver, seatbeltPassenger, passengerPresent |
| Body | doors, windows_percent, sunroof_percent, sunshade_percent, window_anti_pinch | doors, windowPercent, sunroofPercent, sunshadePercent, windowAntiPinch |
| Tires and steering | tire_pressure_kpa, tire_temp_c, steering_angle_deg | tirePressure, tireTemp, steeringAngle |
| Indicators and proximity | turn_left, turn_right, radar | turnLeft, turnRight, radar |
| Other | odometer_km, trip_distance_km, altitude_m, slope_deg, pm25_ug_m3 | totalMileage, tripMileage, altitude, slope, pm25 |

Wheel and window objects use nullable fl, fr, rl and rr members; doors also include hood and trunk, where true means open. Radar contains front_left, front_left_mid, front_right_mid, front_right, rear_left, rear_mid, rear_right, left, right and reverse_switch_on. Its raw values 0–6 represent obstacle levels and 14 means clear; distance conversion is unverified. Battery current and battery power are positive for discharge and negative for charge; total power is positive for drive and negative for regeneration. Steering angle is negative left, positive right. Seat level 0 is off.

Battery power and instantaneous electric consumption can be derived by the app when source values are missing. Expose provenance where known; do not represent an inferred value as a directly measured one. The app's energy-flow description is a heuristic, not an authoritative vehicle state. Normalize gear to P, R, N, D or S; map other values to null. Normalize charging_status to charging, complete or not_charging; map unrecognized values to null. Normalize work_mode to ev, hev or forced_ev and drive_mode to eco, normal or sport; map unrecognized values to null. Never transmit localized labels as protocol values. Wind modes are face, face_feet, feet, feet_defrost and defrost; cycle modes are inner and outer.

The existing snapshot timestamp is not proof that every individual reading is fresh. The connector must track observation freshness sufficiently to enforce the local safety checks; it must not refresh a stale speed or gear just because another field changed. Include source (byd_auto, di_plus or mock), observed_at (UTC RFC 3339), and quality (an object mapping field names to measured, derived or unknown). Use null for unavailable values. Mock data must be clearly identified. Do not forward raw source error strings without sanitizing them.

Send the fields in this catalog when remote sharing is enabled, except altitude_m, trip_distance_km, trip_electric_kwh_per_100km and trip_fuel_l_per_100km, which require a separate informed opt-in. When that opt-in is disabled, omit those four fields. No VIN, coordinates, device identifiers or hardware exports by default. Remote sharing itself requires explicit opt-in and discloses that the selected backend can see uploaded data.

## Commands the Android model can represent

The server accepts the first three actions below. The remaining actions are explicitly excluded from v1. Advertise and validate each enabled capability per source and vehicle; model and firmware compatibility is unverified.

| Action | Parameters | Android command | Remote v1 |
| --- | --- | --- | --- |
| climate.power | enabled: boolean | AcPower | Allowed |
| climate.temperature | zone: driver/passenger; celsius: number | AcTemperature | Allowed within advertised range and step |
| climate.auto | enabled: boolean | AcAuto | Allowed |
| climate.fan | level: integer 0–7 | AcFanLevel | Excluded |
| climate.wind | mode: face, face_feet, feet, feet_defrost, defrost | AcWind | Excluded |
| climate.cycle | mode: inner/outer | AcCycle | Excluded |
| seat.heat | seat: driver/passenger; level: integer | SeatHeat | Excluded initially |
| seat.ventilation | seat: driver/passenger; level: integer | SeatVent | Excluded initially |
| glass.open / glass.close | zone: window_fl, window_fr, window_rl, window_rr, sunroof, sunshade | Glass | Excluded initially |
| quick.purify / quick.cool / quick.front_defrost | none | Quick | Excluded initially |

Fan state is modeled as 0–7 and seat state as 0–3; the source's advertised limits govern accepted values. The model reports a temperature step and seat maximum (DiPlus may use two levels); do not assume these metadata prove vehicle support. Glass and Quick have no confirming readback. Never expose arbitrary FIDs, protocol strings, reflection calls or shell access. Any expansion of the remote allowlist requires documented safety review and explicit opt-in; retain the original exclusion of defrost, seats and glass in v1.

## Execution contract

The head unit must independently reject commands when remote access is disabled, capability is unknown or unsupported, readings are stale or unavailable, gear is not P, or speed is at least 1 km/h. Server-side freshness checks do not replace this local check. No offline queue; retain TTL, idempotency, rate limiting and per-vehicle serialization from the server specification.

Repository state can include optimistic updates. A matching optimistic value is not readback confirmation. Correlate command IDs to actual dispatch and subsequent source readback; report sent_unconfirmed when confirmation is unavailable. Glass and Quick are send-only. Rejection, failure, expiration and unsupported results must remain distinct. Validate this behavior using mock data and test physical controls only while safely parked.

Implementation references: [VehicleState.kt](../app/src/main/java/com/dicar/vehicle/data/model/VehicleState.kt), [VehicleCommand.kt](../app/src/main/java/com/dicar/vehicle/data/model/VehicleCommand.kt). Implement the connector and verify this contract with tests.
