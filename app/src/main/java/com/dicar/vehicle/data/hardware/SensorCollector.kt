package com.dicar.vehicle.data.hardware

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.params.StreamConfigurationMap
import android.os.Build
import android.util.Size

/** 传感器。逐个列出完整参数。 */
fun collectSensors(context: Context): HwSection {
    val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    val sensors = sm?.getSensorList(Sensor.TYPE_ALL).orEmpty()

    return section(HwCategory.SENSOR) {
        block {
            item("Count", "${sensors.size} sensors")
            item("Wake-up sensor", "${sensors.count { it.isWakeUpSensor }} sensors")
            item("Vendor", HwFormat.list(sensors.map { it.vendor }.distinct()))
        }

        sensors.sortedBy { it.type }.forEach { s ->
            block("${sensorTypeName(s.type)}（${s.name}）") {
                item("Vendor", s.vendor)
                item("Type ID", "${s.type}" + (s.stringType?.let { " · $it" } ?: ""))
                item("Version", s.version.toString())
                item("Range", "${s.maximumRange}")
                item("Resolution", "${s.resolution}")
                item("Power draw", "${s.power} mA")
                // minDelay 是最小采样周期（微秒），倒过来才是最高频率，后者更好理解
                item(
                    "Maximum frequency",
                    when {
                        s.minDelay > 0 -> "%.0f Hz (min interval %d µs)".format(1_000_000f / s.minDelay, s.minDelay)
                        s.minDelay == 0 -> "On change only"
                        else -> "One-shot"
                    },
                )
                if (s.maxDelay > 0) item("Maximum interval", "${s.maxDelay} µs")
                item("Reporting mode", reportingMode(s.reportingMode))
                flag("Wake-up sensor", s.isWakeUpSensor)
                if (s.fifoMaxEventCount > 0) {
                    item("Batch buffer", "${s.fifoReservedEventCount} / ${s.fifoMaxEventCount} events")
                }
                item("Sensor ID", s.id.toString())
                if (s.highestDirectReportRateLevel > 0) {
                    item("Direct report level", s.highestDirectReportRateLevel.toString())
                }
            }
        }
    }
}

/** 摄像头。车机上通常是倒车影像和 360 环视。 */
fun collectCameras(context: Context): HwSection {
    val cm = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    // 只读参数，不开相机，所以不需要 CAMERA 权限
    val ids = runCatching { cm?.cameraIdList }.getOrNull().orEmpty()

    return section(HwCategory.CAMERA) {
        block {
            item("Count", "${ids.size} cameras")
            itemIfPresent("ID list", ids.joinToString(", "))
        }

        ids.forEach { id ->
            val c = runCatching { cm?.getCameraCharacteristics(id) }.getOrNull() ?: return@forEach
            val map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            block("Camera $id (${facing(c.get(CameraCharacteristics.LENS_FACING))})") {
                item("Maximum resolution", map?.maxJpegSize()?.let { "${it.width} × ${it.height}（${megapixels(it)}）" })
                item("Pixel array", c.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
                    ?.let { "${it.width} × ${it.height}" })
                item("Active area", c.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
                    ?.let { "${it.width()} × ${it.height()}" })
                item("Sensor size", c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                    ?.let { "%.2f × %.2f mm".format(it.width, it.height) })
                item("Focal lengths", c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    ?.joinToString(", ") { "%.2f mm".format(it) })
                item("Apertures", c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)
                    ?.joinToString(", ") { "f/%.1f".format(it) })
                item("Maximum digital zoom", c.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)
                    ?.let { "%.1f×".format(it) })
                item("ISO range", c.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
                    ?.let { "${it.lower} ~ ${it.upper}" })
                item("Exposure time", c.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
                    ?.let { "${it.lower / 1000} µs ~ ${it.upper / 1_000_000} ms" })
                flag("Flash", c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE), yes = "Yes", no = "No")
                item("Mounting angle", c.get(CameraCharacteristics.SENSOR_ORIENTATION)?.let { "$it°" })
                item("Hardware level", hardwareLevel(c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)))
                item("Focus modes", c.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)?.size?.toString())
                item("Supported formats", map?.outputFormats?.joinToString(", ") { imageFormat(it) })
                item("Video capabilities", map?.highSpeedVideoSizes()?.takeIf { it.isNotEmpty() }
                    ?.joinToString(", ") { "${it.width}×${it.height}" })
                itemIfPresent("Logical multicamera", runCatching { c.physicalCameraIds }.getOrNull()
                    ?.takeIf { it.isNotEmpty() }?.joinToString(", "))
            }
        }
    }
}

private fun StreamConfigurationMap.maxJpegSize(): Size? =
    runCatching { getOutputSizes(ImageFormat.JPEG)?.maxByOrNull { it.width.toLong() * it.height } }.getOrNull()

private fun StreamConfigurationMap.highSpeedVideoSizes(): List<Size> =
    runCatching { highSpeedVideoSizes?.toList() }.getOrNull().orEmpty()

private fun megapixels(size: Size): String =
    "%.1f MP".format(size.width.toLong() * size.height / 1_000_000f)

private fun facing(v: Int?): String = when (v) {
    CameraCharacteristics.LENS_FACING_FRONT -> "Front"
    CameraCharacteristics.LENS_FACING_BACK -> "Rear"
    CameraCharacteristics.LENS_FACING_EXTERNAL -> "External"
    else -> HwItem.UNKNOWN
}

private fun hardwareLevel(v: Int?): String = when (v) {
    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY (legacy API only)"
    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
    else -> HwItem.UNKNOWN
}

private fun imageFormat(v: Int): String = when (v) {
    ImageFormat.JPEG -> "JPEG"
    ImageFormat.YUV_420_888 -> "YUV420"
    ImageFormat.YUV_422_888 -> "YUV422"
    ImageFormat.RAW_SENSOR -> "RAW"
    ImageFormat.RAW10 -> "RAW10"
    ImageFormat.RAW12 -> "RAW12"
    ImageFormat.PRIVATE -> "PRIVATE"
    ImageFormat.NV21 -> "NV21"
    ImageFormat.DEPTH16 -> "DEPTH16"
    ImageFormat.HEIC -> "HEIC"
    else -> "0x%x".format(v)
}

private fun reportingMode(mode: Int): String = when (mode) {
    Sensor.REPORTING_MODE_CONTINUOUS -> "Continuous"
    Sensor.REPORTING_MODE_ON_CHANGE -> "On change"
    Sensor.REPORTING_MODE_ONE_SHOT -> "One-shot"
    Sensor.REPORTING_MODE_SPECIAL_TRIGGER -> "Special trigger"
    else -> HwItem.UNKNOWN
}

/** 常量名比数字好认，但安卓没有现成的反查，只能自己列。 */
private fun sensorTypeName(type: Int): String = when (type) {
    Sensor.TYPE_ACCELEROMETER -> "Accelerometer"
    Sensor.TYPE_MAGNETIC_FIELD -> "Magnetometer"
    Sensor.TYPE_GYROSCOPE -> "Gyroscope"
    Sensor.TYPE_LIGHT -> "Light sensor"
    Sensor.TYPE_PRESSURE -> "Barometer"
    Sensor.TYPE_PROXIMITY -> "Proximity sensor"
    Sensor.TYPE_GRAVITY -> "Gravity"
    Sensor.TYPE_LINEAR_ACCELERATION -> "Linear acceleration"
    Sensor.TYPE_ROTATION_VECTOR -> "Rotation vector"
    Sensor.TYPE_RELATIVE_HUMIDITY -> "Relative humidity"
    Sensor.TYPE_AMBIENT_TEMPERATURE -> "Ambient temperature"
    Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED -> "Magnetometer (uncalibrated)"
    Sensor.TYPE_GAME_ROTATION_VECTOR -> "Game rotation vector"
    Sensor.TYPE_GYROSCOPE_UNCALIBRATED -> "Gyroscope (uncalibrated)"
    Sensor.TYPE_SIGNIFICANT_MOTION -> "Significant motion"
    Sensor.TYPE_STEP_DETECTOR -> "Step detector"
    Sensor.TYPE_STEP_COUNTER -> "Step counter"
    Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> "Geomagnetic rotation vector"
    Sensor.TYPE_HEART_RATE -> "Heart rate"
    Sensor.TYPE_POSE_6DOF -> "6-DoF pose"
    Sensor.TYPE_STATIONARY_DETECT -> "Stationary detection"
    Sensor.TYPE_MOTION_DETECT -> "Motion detection"
    Sensor.TYPE_HEART_BEAT -> "Heart beat"
    Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT -> "Off-body detection"
    Sensor.TYPE_ACCELEROMETER_UNCALIBRATED -> "Accelerometer (uncalibrated)"
    Sensor.TYPE_HINGE_ANGLE -> "Hinge angle"
    else -> "Sensor"
}
