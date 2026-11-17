package com.dicar.vehicle.data.hardware

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Read `/proc`, `/sys` and system properties.
 *
 * This layer does not throw: sysfs access varies by Android version and device.
 * Return null for inaccessible nodes so the UI can show unknown without failing the whole page.
 */
object SysFs {

    fun text(path: String): String? = runCatching {
        val f = File(path)
        if (f.canRead()) f.readText().trim().takeIf { it.isNotEmpty() } else null
    }.getOrNull()

    fun long(path: String): Long? = text(path)?.substringBefore('\n')?.trim()?.toLongOrNull()

    fun int(path: String): Int? = long(path)?.toInt()

    /**
     * cpufreq node in kHz.
     *
     * Treat values below 10 MHz as unavailable: some kernels expose frequency-table indices
     * instead of actual frequencies, producing implausible values such as 1 kHz.
     */
    fun khz(path: String): Long? = long(path)?.takeIf { it >= MIN_PLAUSIBLE_KHZ }

    /** 10 MHz minimum plausible Android CPU frequency. */
    const val MIN_PLAUSIBLE_KHZ = 10_000L

    /** Directory entries sorted by name, or an empty list if inaccessible. */
    fun children(path: String, filter: (File) -> Boolean = { true }): List<File> = runCatching {
        File(path).listFiles()?.filter(filter)?.sortedBy { it.name } ?: emptyList()
    }.getOrDefault(emptyList())

    /** CPU sysfs directories sorted numerically, so `cpu10` follows `cpu9`. */
    fun cpuDirs(): List<File> =
        children(CPU_ROOT) { it.isDirectory && CPU_DIR.matches(it.name) }
            .sortedBy { it.name.removePrefix("cpu").toIntOrNull() ?: Int.MAX_VALUE }

    /**
     * Run a read-only system command such as `getprop` and collect stdout.
     * Kill it on timeout because property services can hang.
     */
    fun exec(vararg command: String, timeoutMs: Long = 2_000): String? = runCatching {
        // Keep stderr separate from stdout so errors such as Permission denied are not displayed as values.
        val process = ProcessBuilder(*command).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        process.errorStream.close()
        if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) process.destroy()
        output.takeIf { it.isNotBlank() }
    }.getOrNull()

    /**
     * Reflectively call hidden API `android.os.SystemProperties.get`; return null if reflection fails.
     */
    fun prop(key: String): String? = runCatching {
        val cls = Class.forName("android.os.SystemProperties")
        val get = cls.getMethod("get", String::class.java)
        (get.invoke(null, key) as? String)?.takeIf { it.isNotBlank() }
    }.getOrNull()

    /** Try property names in order and return the first value; vendor keys vary. */
    fun firstProp(vararg keys: String): String? = keys.firstNotNullOfOrNull { prop(it) }

    /** Parse all `getprop` output into key-value pairs; return an empty map to trigger the whitelist fallback. */
    fun allProps(): Map<String, String> {
        val raw = exec("getprop") ?: return emptyMap()
        return raw.lineSequence().mapNotNull { line ->
            // Format: [key]: [value]
            val m = PROP_LINE.matchEntire(line.trim()) ?: return@mapNotNull null
            val value = m.groupValues[2]
            if (value.isEmpty()) null else m.groupValues[1] to value
        }.toMap()
    }

    /**
     * GPU frequency nodes vary by vendor; the second value converts to Hz.
     * Use the first readable node.
     */
    val GPU_FREQ_NODES = listOf(
        "/sys/class/kgsl/kgsl-3d0/gpuclk" to 1L,
        "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq" to 1L,
        "/sys/class/devfreq/gpufreq/cur_freq" to 1L,
        "/sys/kernel/gpu/gpu_clock" to 1_000_000L,
        "/sys/class/misc/mali0/device/clock" to 1_000_000L,
    )

    /** Return Hz, or null if unavailable. */
    fun gpuHz(): Long? = GPU_FREQ_NODES.firstNotNullOfOrNull { (path, scale) ->
        long(path)?.takeIf { it > 0 }?.times(scale)
    }

    /** Existing frequency node path, displayed to identify the source. */
    fun gpuFreqNode(): String? = GPU_FREQ_NODES.firstOrNull { long(it.first) != null }?.first

    private const val CPU_ROOT = "/sys/devices/system/cpu"
    private val CPU_DIR = Regex("cpu\\d+")
    private val PROP_LINE = Regex("""\[([^]]+)]: \[(.*)]""")
}
