package com.dicar.vehicle.data.hardware

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Display
import android.view.Surface
import kotlin.math.hypot

/**
 * 屏幕。分辨率、刷新率、HDR、触摸能力。
 *
 * 车机常常挂着不止一块屏（中控 + 仪表 + HUD），所以这里把 [DisplayManager]
 * 报出来的每一块都列出来，而不是只看主屏。
 */
@Suppress("DEPRECATION") // getRealMetrics 在 API 31 起被 WindowMetrics 取代，但 minSdk 28 还得靠它
fun collectDisplay(context: Context): HwSection {
    val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
    val displays = dm?.displays.orEmpty()
    val main = displays.firstOrNull { it.displayId == Display.DEFAULT_DISPLAY } ?: displays.firstOrNull()
    val metrics = DisplayMetrics().also { main?.getRealMetrics(it) }
    val config = context.resources.configuration
    val pm = context.packageManager

    return section(HwCategory.DISPLAY) {
        block {
            item("Resolution", if (metrics.widthPixels > 0) "${metrics.widthPixels} × ${metrics.heightPixels}" else null)
            item("Pixel density", metrics.densityDpi.takeIf { it > 0 }?.let { "$it dpi（${densityBucket(it)}）" })
            item("Measured density", if (metrics.xdpi > 0) "x ${metrics.xdpi.toInt()} / y ${metrics.ydpi.toInt()} dpi" else null)
            item("Diagonal", physicalInches(metrics))
            item("Scale factor", metrics.density.takeIf { it > 0 }?.let { "${it}×" })
            item("Logical size", "${config.screenWidthDp} × ${config.screenHeightDp} dp")
            item("Smallest width", "${config.smallestScreenWidthDp} dp")
            item("Screen category", layoutSize(config))
        }

        block("Refresh and color") {
            item("Current refresh rate", main?.refreshRate?.let { "%.1f Hz".format(it) })
            val modes = main?.supportedModes.orEmpty()
            item("Display modes", modes.size.takeIf { it > 0 }?.let { "$it modes" })
            modes.sortedByDescending { it.refreshRate }.take(MAX_MODES).forEach { m ->
                item("  Mode ${m.modeId}", "${m.physicalWidth} × ${m.physicalHeight} @ %.1f Hz".format(m.refreshRate))
            }
            item("Wide color gamut", config.isScreenWideColorGamut)
            item("HDR", config.isScreenHdr)
            val hdrTypes = main?.hdrCapabilities?.supportedHdrTypes?.toList().orEmpty()
            itemIfPresent("HDR formats", hdrTypes.takeIf { it.isNotEmpty() }?.joinToString(", ") { hdrName(it) })
            flag(
                "Night mode",
                (config.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES,
                yes = "Enabled", no = "Disabled",
            )
        }

        block("Status") {
            item("Orientation", orientation(main?.rotation))
            item("Display state", displayState(main?.state))
            item(
                "Brightness",
                runCatching {
                    Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
                }.getOrNull()?.let { "$it / 255" },
            )
            item(
                "Brightness mode",
                runCatching {
                    Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE)
                }.getOrNull()?.let { if (it == 1) "Automatic" else "Manual" },
            )
            // DisplayCutout 本身是 API 28 加的，但从 Display 上直接取要到 API 29
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val cutout = main?.cutout
                item("Notch / cutout", if (cutout == null) "None" else "Present (${cutout.boundingRects.size} regions)")
            }
        }

        if (displays.size > 1) {
            block("Other displays") {
                displays.filter { it.displayId != main?.displayId }.forEach { d ->
                    val m = DisplayMetrics().also { d.getRealMetrics(it) }
                    item("#${d.displayId} ${d.name}", "${m.widthPixels} × ${m.heightPixels} · ${displayState(d.state)}")
                }
            }
        }

        block("Touch") {
            item("Touchscreen", pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN))
            item(
                "Multitouch",
                when {
                    pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_JAZZHAND) -> "Supported (5+ points)"
                    pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_DISTINCT) -> "Supported (2 independent points)"
                    pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH) -> "Supported (basic)"
                    else -> "Unsupported"
                },
            )
            item("Pressure sensitivity", pm.hasSystemFeature("android.hardware.sensor.stylus"))
            item("Input devices", inputDevices())
        }
    }
}

private fun inputDevices(): String? {
    val ids = runCatching { android.view.InputDevice.getDeviceIds() }.getOrNull() ?: return null
    val names = ids.toList().mapNotNull { android.view.InputDevice.getDevice(it)?.name }
    return names.takeIf { it.isNotEmpty() }?.joinToString(", ")
}

/** 对角线英寸。xdpi/ydpi 在部分车机上是厂商乱填的，明显离谱就不显示。 */
private fun physicalInches(m: DisplayMetrics): String? {
    if (m.xdpi <= 1f || m.ydpi <= 1f || m.widthPixels <= 0) return null
    val inches = hypot(m.widthPixels / m.xdpi, m.heightPixels / m.ydpi)
    return if (inches in 1f..60f) HwFormat.inches(inches) else null
}

private fun densityBucket(dpi: Int): String = when {
    dpi <= 120 -> "ldpi"
    dpi <= 160 -> "mdpi"
    dpi <= 213 -> "tvdpi"
    dpi <= 240 -> "hdpi"
    dpi <= 320 -> "xhdpi"
    dpi <= 480 -> "xxhdpi"
    dpi <= 640 -> "xxxhdpi"
    else -> "Extra-high density"
}

private fun layoutSize(config: Configuration): String =
    when (config.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) {
        Configuration.SCREENLAYOUT_SIZE_SMALL -> "Small"
        Configuration.SCREENLAYOUT_SIZE_NORMAL -> "Normal"
        Configuration.SCREENLAYOUT_SIZE_LARGE -> "Large"
        Configuration.SCREENLAYOUT_SIZE_XLARGE -> "Extra large"
        else -> HwItem.UNKNOWN
    }

private fun orientation(rotation: Int?): String = when (rotation) {
    Surface.ROTATION_0 -> "0°"
    Surface.ROTATION_90 -> "90°"
    Surface.ROTATION_180 -> "180°"
    Surface.ROTATION_270 -> "270°"
    else -> HwItem.UNKNOWN
}

private fun displayState(state: Int?): String = when (state) {
    Display.STATE_ON -> "On"
    Display.STATE_OFF -> "Off"
    Display.STATE_DOZE, Display.STATE_DOZE_SUSPEND -> "Doze mode"
    Display.STATE_VR -> "VR"
    Display.STATE_ON_SUSPEND -> "On (suspended)"
    else -> HwItem.UNKNOWN
}

private fun hdrName(type: Int): String = when (type) {
    Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> "Dolby Vision"
    Display.HdrCapabilities.HDR_TYPE_HDR10 -> "HDR10"
    Display.HdrCapabilities.HDR_TYPE_HLG -> "HLG"
    Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS -> "HDR10+"
    else -> "Type $type"
}

/** 模式太多时只列最快的几个，完整列表意义不大。 */
private const val MAX_MODES = 8
