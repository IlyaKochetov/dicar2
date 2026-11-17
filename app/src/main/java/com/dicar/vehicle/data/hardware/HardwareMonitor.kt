package com.dicar.vehicle.data.hardware

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants

/** Current state of one core. */
data class CoreLive(
    val index: Int,
    val online: Boolean,
    val curKhz: Long?,
    val minKhz: Long?,
    val maxKhz: Long?,
    val governor: String?,
    /** Utilization between samples; null when counters do not change. */
    val load: Float?,
    /** Core model, such as `Cortex-A55`. */
    val model: String?,
)

data class ThermalReading(val zone: String, val type: String, val celsius: Float?)

data class BatteryLive(
    val present: Boolean,
    val levelPercent: Float?,
    val status: String,
    val health: String,
    val plugged: String,
    val technology: String?,
    val voltageMv: Int?,
    val temperature: Float?,
    val currentNowUa: Int?,
    val currentAvgUa: Int?,
    val chargeCounterUah: Int?,
    val energyCounterNwh: Long?,
    val capacityPercent: Int?,
    val cycleCount: Int?,
)

/** Live hardware data; [HardwareInspector] collects static information separately. */
data class LiveStats(
    val totalLoad: Float?,
    /** Whether /proc/stat is readable; the UI explains system restrictions when it is not. */
    val systemLoadAvailable: Boolean,
    /** This app's CPU utilization from /proc/self/stat, a fallback when global utilization is unavailable. */
    val appLoad: Float?,
    val cores: List<CoreLive>,
    val gpuHz: Long?,
    val memTotal: Long?,
    val memAvailable: Long?,
    val memFree: Long?,
    val memCached: Long?,
    val swapTotal: Long?,
    val swapFree: Long?,
    val lowMemory: Boolean,
    val javaHeapUsed: Long?,
    val javaHeapMax: Long?,
    val nativeHeapUsed: Long?,
    val thermal: List<ThermalReading>,
    val thermalStatus: String?,
    val battery: BatteryLive?,
    val uptimeMs: Long,
    val awakeMs: Long,
    /** Open file descriptors for this app, available even when hidepid restricts other processes. */
    val openFds: Int?,
) {
    val memUsed: Long? get() = if (memTotal != null && memAvailable != null) memTotal - memAvailable else null
}

/**
 * Live metrics sampler.
 *
 * CPU utilization uses differences between `/proc/stat` samples, so this class is stateful.
 * Call [sample] periodically; the first sample has null utilization because no previous sample exists.
 */
class HardwareMonitor(private val context: Context) {

    private var previous: Map<String, ProcParse.CpuTimes> = emptyMap()
    /** Cache core models after the first read rather than rereading /proc/cpuinfo every second. */
    private val coreModels: Map<Int, String?> by lazy {
        val text = SysFs.text("/proc/cpuinfo") ?: return@lazy emptyMap()
        ProcParse.cpuCores(text).associate { it.index to SiliconNames.core(it.implementer, it.part) }
    }

    fun sample(): LiveStats {
        val stat = SysFs.text("/proc/stat")
        val now = stat?.let { ProcParse.cpuTimes(it) } ?: emptyMap()
        val before = previous
        previous = now

        val mem = SysFs.text("/proc/meminfo")?.let { ProcParse.memInfo(it) } ?: emptyMap()
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val amInfo = am?.let { ActivityManager.MemoryInfo().also { info -> it.getMemoryInfo(info) } }
        val runtime = Runtime.getRuntime()

        return LiveStats(
            totalLoad = ProcParse.load(before["cpu"], now["cpu"]),
            systemLoadAvailable = stat != null,
            appLoad = appLoad(),
            cores = cores(before, now),
            gpuHz = SysFs.gpuHz(),
            // Show both ActivityManager memory (excluding kernel reservations) and meminfo;
            // prefer the system figure to match Settings.
            memTotal = amInfo?.totalMem ?: mem["MemTotal"],
            memAvailable = amInfo?.availMem ?: mem["MemAvailable"],
            memFree = mem["MemFree"],
            memCached = mem["Cached"],
            swapTotal = mem["SwapTotal"],
            swapFree = mem["SwapFree"],
            lowMemory = amInfo?.lowMemory == true,
            javaHeapUsed = runtime.totalMemory() - runtime.freeMemory(),
            javaHeapMax = runtime.maxMemory(),
            nativeHeapUsed = Debug.getNativeHeapAllocatedSize(),
            thermal = thermal(),
            thermalStatus = thermalStatus(),
            battery = battery(),
            uptimeMs = SystemClock.elapsedRealtime(),
            awakeMs = SystemClock.uptimeMillis(),
            openFds = SysFs.children("/proc/self/fd").size.takeIf { it > 0 },
        )
    }

    private var lastAppCpuTicks: Long? = null
    private var lastAppWallMs: Long = 0

    /**
     * App CPU utilization normalized so all cores together equal 100%.
     *
     * /proc/self/stat reads the current process and provides a fallback when /proc/stat is inaccessible.
     */
    private fun appLoad(): Float? {
        val ticks = SysFs.text("/proc/self/stat")?.let { ProcParse.selfCpuTicks(it) }
        val wall = SystemClock.elapsedRealtime()
        val prevTicks = lastAppCpuTicks
        val prevWall = lastAppWallMs
        lastAppCpuTicks = ticks
        lastAppWallMs = wall
        if (ticks == null || prevTicks == null) return null

        val wallSec = (wall - prevWall) / 1000.0
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        if (wallSec <= 0) return null
        val cpuSec = (ticks - prevTicks).toDouble() / clockTicks
        return (cpuSec / wallSec / cores * 100).toFloat().coerceIn(0f, 100f)
    }

    private fun cores(
        before: Map<String, ProcParse.CpuTimes>,
        now: Map<String, ProcParse.CpuTimes>,
    ): List<CoreLive> = SysFs.cpuDirs().map { dir ->
        val index = dir.name.removePrefix("cpu").toIntOrNull() ?: -1
        val freq = "${dir.absolutePath}/cpufreq"
        // cpu0 is always online and may have no online node.
        val online = SysFs.int("${dir.absolutePath}/online")?.let { it == 1 } ?: (index == 0)
        CoreLive(
            index = index,
            online = online,
            // Treat offline-core scaling_cur_freq as unavailable; it may be missing or stale.
            curKhz = if (online) {
                SysFs.khz("$freq/scaling_cur_freq") ?: SysFs.khz("$freq/cpuinfo_cur_freq")
            } else null,
            minKhz = SysFs.khz("$freq/cpuinfo_min_freq") ?: SysFs.khz("$freq/scaling_min_freq"),
            maxKhz = SysFs.khz("$freq/cpuinfo_max_freq") ?: SysFs.khz("$freq/scaling_max_freq"),
            governor = SysFs.text("$freq/scaling_governor"),
            load = ProcParse.load(before["cpu$index"], now["cpu$index"]),
            model = coreModels[index],
        )
    }

    private fun thermal(): List<ThermalReading> =
        SysFs.children("/sys/class/thermal") { it.name.startsWith("thermal_zone") }
            .mapNotNull { dir ->
                val raw = SysFs.long("${dir.absolutePath}/temp") ?: return@mapNotNull null
                val type = SysFs.text("${dir.absolutePath}/type") ?: dir.name
                ThermalReading(dir.name, type, normalizeTemp(raw))
            }
            // Discard implausible readings such as -40 or 0 from disconnected sensors.
            .filter { it.celsius != null && it.celsius > -30f && it.celsius < 200f }

    /**
     * Thermal zone units vary: most use millidegrees, some tenths of a degree or degrees.
     * Infer the scale from magnitude.
     */
    private fun normalizeTemp(raw: Long): Float = when {
        raw > 10_000 || raw < -10_000 -> raw / 1000f
        raw > 1_000 || raw < -1_000 -> raw / 100f
        raw > 200 || raw < -200 -> raw / 10f
        else -> raw.toFloat()
    }

    private fun thermalStatus(): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return null
        return when (runCatching { pm.currentThermalStatus }.getOrNull()) {
            PowerManager.THERMAL_STATUS_NONE -> "Normal"
            PowerManager.THERMAL_STATUS_LIGHT -> "Light heating"
            PowerManager.THERMAL_STATUS_MODERATE -> "Moderate heating"
            PowerManager.THERMAL_STATUS_SEVERE -> "Severe heating"
            PowerManager.THERMAL_STATUS_CRITICAL -> "Critical"
            PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergency throttling"
            PowerManager.THERMAL_STATUS_SHUTDOWN -> "Shutdown imminent"
            else -> null
        }
    }

    // ---------------- Battery ----------------

    private fun battery(): BatteryLive? {
        val sticky = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        if (sticky == null && bm == null) return null

        val level = sticky?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = sticky?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return BatteryLive(
            // 车机大多没有独立Battery，这里经常是 false，界面要如实说明而不是显示 0%
            present = sticky?.getBooleanExtra(BatteryManager.EXTRA_PRESENT, false) ?: false,
            levelPercent = if (level >= 0 && scale > 0) level * 100f / scale else null,
            status = statusText(sticky?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1),
            health = healthText(sticky?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1),
            plugged = pluggedText(sticky?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1),
            technology = sticky?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY),
            voltageMv = sticky?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)?.takeIf { it > 0 },
            temperature = sticky?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
                ?.takeIf { it != Int.MIN_VALUE && it != -1 }?.div(10f),
            currentNowUa = bm?.prop(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW),
            currentAvgUa = bm?.prop(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE),
            chargeCounterUah = bm?.prop(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER),
            energyCounterNwh = bm?.let {
                runCatching { it.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER) }
                    .getOrNull()?.takeIf { v -> v != Long.MIN_VALUE }
            },
            capacityPercent = bm?.prop(BatteryManager.BATTERY_PROPERTY_CAPACITY),
            cycleCount = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                sticky?.getIntExtra("android.os.extra.CYCLE_COUNT", -1)?.takeIf { it > 0 }
            } else null,
        )
    }

    /** Treat unsupported properties reported as `Integer.MIN_VALUE` (or 0 on some devices) as unavailable. */
    private fun BatteryManager.prop(id: Int): Int? =
        runCatching { getIntProperty(id) }.getOrNull()?.takeIf { it != Int.MIN_VALUE && it != 0 }

    private fun statusText(v: Int) = when (v) {
        BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
        BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
        BatteryManager.BATTERY_STATUS_FULL -> "Full"
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
        else -> HwItem.UNKNOWN
    }

    private fun healthText(v: Int) = when (v) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheated"
        BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Overvoltage"
        BatteryManager.BATTERY_HEALTH_COLD -> "Too cold"
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
        else -> HwItem.UNKNOWN
    }

    private fun pluggedText(v: Int) = when (v) {
        0 -> "Not connected"
        BatteryManager.BATTERY_PLUGGED_AC -> "AC power"
        BatteryManager.BATTERY_PLUGGED_USB -> "USB"
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless charging"
        else -> HwItem.UNKNOWN
    }

    companion object {
        /** Visible-screen sampling interval, matching the one-second vehicle poll. */
        const val SAMPLE_INTERVAL_MS = 1_000L

        /** Clock ticks per second for CPU time; fall back to the Linux convention of 100. */
        val clockTicks: Long by lazy {
            runCatching { Os.sysconf(OsConstants._SC_CLK_TCK) }.getOrNull()?.takeIf { it > 0 } ?: 100L
        }
    }
}
