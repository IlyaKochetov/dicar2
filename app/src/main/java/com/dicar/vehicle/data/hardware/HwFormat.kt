package com.dicar.vehicle.data.hardware

import java.util.Locale
import kotlin.math.abs

/**
 * Hardware reading formatting, implemented as pure functions for unit testing.
 *
 * Capacities use base 1024 but are labeled KB/MB/GB to match common hardware tools.
 */
object HwFormat {

    private val BYTE_UNITS = listOf("B", "KB", "MB", "GB", "TB", "PB")

    fun bytes(value: Long?): String {
        if (value == null || value < 0) return HwItem.UNKNOWN
        if (value < 1024) return "$value B"
        var v = value.toDouble()
        var unit = 0
        while (v >= 1024 && unit < BYTE_UNITS.lastIndex) {
            v /= 1024
            unit++
        }
        // Omit decimal places above 100 for a more readable value.
        val decimals = if (v >= 100) 0 else 2
        return "${fixed(v, decimals)} ${BYTE_UNITS[unit]}"
    }

    /** sysfs cpufreq values are in kHz. */
    fun kHz(value: Long?): String = when {
        value == null || value <= 0 -> HwItem.UNKNOWN
        value >= 1_000_000 -> "${fixed(value / 1_000_000.0, 2)} GHz"
        value >= 1_000 -> "${fixed(value / 1_000.0, 0)} MHz"
        else -> "$value kHz"
    }

    fun hz(value: Long?): String = if (value == null || value <= 0) HwItem.UNKNOWN else kHz(value / 1000)

    /** Network rates are reported by Android in kbps. */
    fun kbps(value: Int?): String = when {
        value == null || value <= 0 -> HwItem.UNKNOWN
        value >= 1_000_000 -> "${fixed(value / 1_000_000.0, 1)} Gbps"
        value >= 1_000 -> "${fixed(value / 1_000.0, 0)} Mbps"
        else -> "$value kbps"
    }

    fun percent(value: Float?, decimals: Int = 0): String =
        if (value == null) HwItem.UNKNOWN else "${fixed(value.toDouble(), decimals)}%"

    /** Celsius. Divide millidegrees (common in sysfs thermal) by 1000 first. */
    fun celsius(value: Float?, decimals: Int = 1): String =
        if (value == null) HwItem.UNKNOWN else "${fixed(value.toDouble(), decimals)} ℃"

    /** Long intervals such as uptime use minute precision; show seconds below one minute. */
    fun duration(millis: Long?): String {
        if (millis == null || millis < 0) return HwItem.UNKNOWN
        val totalSec = millis / 1000
        val days = totalSec / 86_400
        val hours = (totalSec % 86_400) / 3_600
        val minutes = (totalSec % 3_600) / 60
        return buildString {
            if (days > 0) append("$days d ")
            if (days > 0 || hours > 0) append("$hours h ")
            if (days > 0 || hours > 0 || minutes > 0) append("$minutes min") else append("${totalSec} sec")
        }.trim()
    }

    /** Use one decimal place for values such as physical display size. */
    fun inches(value: Float?): String =
        if (value == null || value <= 0) HwItem.UNKNOWN else "${fixed(value.toDouble(), 2)} inches"

    /**
     * Kernel MemTotal excludes reserved memory and is smaller than advertised capacity.
     * Choose the first fitting common capacity rather than rounding to a power of two;
     * do not guess above the largest known capacity.
     */
    fun nominalRam(total: Long?): String? {
        if (total == null || total <= 0) return null
        val gb = COMMON_RAM_GB.firstOrNull { it.toLong() shl 30 >= total } ?: return null
        return bytes(gb.toLong() shl 30)
    }

    private val COMMON_RAM_GB = listOf(1, 2, 3, 4, 6, 8, 12, 16, 24, 32, 48, 64)

    /** Format a used / total row such as 3.8 / 8.0 GB (47%). */
    fun usage(used: Long?, total: Long?): String {
        if (used == null || total == null || total <= 0) return HwItem.UNKNOWN
        val pct = (used.toDouble() / total * 100).toFloat()
        return "${bytes(used)} / ${bytes(total)}(${percent(pct)})"
    }

    fun list(values: Collection<String>): String =
        values.filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: HwItem.UNKNOWN

    /** Current is reported in microamps; Android normally uses positive for charging, but vendors may reverse it, so preserve the sign. */
    fun microAmp(value: Int?): String = when {
        value == null || value == Int.MIN_VALUE -> HwItem.UNKNOWN
        abs(value) >= 1000 -> "${fixed(value / 1000.0, 0)} mA"
        else -> "$value µA"
    }

    private fun fixed(value: Double, decimals: Int): String =
        String.format(Locale.US, "%.${decimals}f", value)
}
