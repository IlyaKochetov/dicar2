package com.dicar.vehicle.data.hardware

/**
 * Pure parsers for several `/proc` text files; no filesystem access, for easy unit testing.
 *
 * Formats are stable, but access varies substantially by Android version and SELinux policy;
 * each parser must tolerate incomplete input.
 */
object ProcParse {

    // ---------------- /proc/cpuinfo ----------------

    data class CpuIdentity(
        /** `Hardware` line, usually a platform identifier such as `Qualcomm Technologies, Inc SM6125`. */
        val hardware: String?,
        /** `CPU implementer`: 0x41 = ARM, 0x51 = Qualcomm, etc. */
        val implementer: Int?,
        /** `CPU part`, used with implementer to identify a core. */
        val part: Int?,
        val variant: Int?,
        val revision: Int?,
        /** Instruction set extensions parsed from `Features` / `flags`. */
        val features: List<String>,
        /** Number of `processor` sections in the file. */
        val processorCount: Int,
        /** `model name`, present on x86 emulators. */
        val modelName: String?,
        val serialPresent: Boolean,
    )

    fun cpuIdentity(text: String): CpuIdentity {
        val fields = mutableMapOf<String, String>()
        var processors = 0
        for (line in text.lineSequence()) {
            val idx = line.indexOf(':')
            if (idx < 0) continue
            val key = line.substring(0, idx).trim().lowercase()
            val value = line.substring(idx + 1).trim()
            if (key == "processor") {
                processors++
                continue
            }
            // Keep the first value for fields repeated per core.
            fields.putIfAbsent(key, value)
        }
        val featureLine = fields["features"] ?: fields["flags"]
        return CpuIdentity(
            hardware = fields["hardware"],
            implementer = hex(fields["cpu implementer"]),
            part = hex(fields["cpu part"]),
            variant = hex(fields["cpu variant"]),
            revision = fields["cpu revision"]?.toIntOrNull(),
            features = featureLine?.split(' ')?.filter { it.isNotBlank() }.orEmpty(),
            processorCount = processors,
            modelName = fields["model name"],
            serialPresent = fields.containsKey("serial"),
        )
    }

    /** Core identity from one `processor` section; part differs across big.LITTLE cores. */
    data class CoreId(val index: Int, val implementer: Int?, val part: Int?)

    /**
     * Parse each core's implementer and part to distinguish big and little cores;
     * grouping by maximum frequency is unreliable when cores share a nominal frequency.
     */
    fun cpuCores(text: String): List<CoreId> {
        val result = mutableListOf<CoreId>()
        var index: Int? = null
        var implementer: Int? = null
        var part: Int? = null
        fun flush() {
            index?.let { result += CoreId(it, implementer, part) }
            index = null; implementer = null; part = null
        }
        for (line in text.lineSequence()) {
            val idx = line.indexOf(':')
            if (idx < 0) continue
            when (line.substring(0, idx).trim().lowercase()) {
                // A processor line starts a new section and ends the previous one.
                "processor" -> {
                    flush()
                    index = line.substring(idx + 1).trim().toIntOrNull()
                }
                "cpu implementer" -> implementer = hex(line.substring(idx + 1))
                "cpu part" -> part = hex(line.substring(idx + 1))
            }
        }
        flush()
        return result
    }

    private fun hex(raw: String?): Int? {
        val s = raw?.trim()?.removePrefix("0x")?.removePrefix("0X") ?: return null
        return s.toIntOrNull(16)
    }

    // ---------------- /proc/meminfo ----------------

    /** Return bytes, converting meminfo's kB units centrally. */
    fun memInfo(text: String): Map<String, Long> = buildMap {
        for (line in text.lineSequence()) {
            val idx = line.indexOf(':')
            if (idx < 0) continue
            val key = line.substring(0, idx).trim()
            val rest = line.substring(idx + 1).trim()
            val number = rest.substringBefore(' ').toLongOrNull() ?: continue
            put(key, if (rest.endsWith("kB")) number * 1024 else number)
        }
    }

    // ---------------- /proc/stat ----------------

    /** Cumulative time counters for one CPU or all CPUs. */
    data class CpuTimes(val total: Long, val idle: Long)

    /**
     * Parse aggregate `cpu` and each `cpuN` from /proc/stat.
     * Offline cores are absent, so return a name-to-counters map rather than using list indices as core IDs.
     */
    fun cpuTimes(text: String): Map<String, CpuTimes> = buildMap {
        for (line in text.lineSequence()) {
            if (!line.startsWith("cpu")) continue
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size < 5) continue
            val values = parts.drop(1).mapNotNull { it.toLongOrNull() }
            if (values.size < 4) continue
            // user nice system idle iowait irq softirq steal …
            val idle = values[3] + (values.getOrNull(4) ?: 0)
            put(parts[0], CpuTimes(values.sum(), idle))
        }
    }

    /**
     * Utilization between samples. Return null when the denominator is zero (short interval or newly online core),
     * since unavailable is different from idle.
     */
    fun load(before: CpuTimes?, after: CpuTimes?): Float? {
        if (before == null || after == null) return null
        val totalDelta = after.total - before.total
        val idleDelta = after.idle - before.idle
        if (totalDelta <= 0) return null
        return ((totalDelta - idleDelta).toFloat() / totalDelta * 100f).coerceIn(0f, 100f)
    }

    // ---------------- /proc/self/stat ----------------

    /**
     * Process CPU time (utime + stime, in clock ticks).
     * The parenthesized process name can contain spaces and parentheses, so count fields after the last closing parenthesis.
     */
    fun selfCpuTicks(text: String): Long? {
        val tail = text.substringAfterLast(") ", missingDelimiterValue = "")
        if (tail.isEmpty()) return null
        val fields = tail.trim().split(Regex("\\s+"))
        // The first tail item is state (field 3), so utime (14) and stime (15) are indices 11 and 12.
        val utime = fields.getOrNull(11)?.toLongOrNull() ?: return null
        val stime = fields.getOrNull(12)?.toLongOrNull() ?: return null
        return utime + stime
    }

    // ---------------- /proc/mounts ----------------

    data class Mount(val device: String, val point: String, val fsType: String, val options: List<String>)

    fun mounts(text: String): List<Mount> = text.lineSequence().mapNotNull { line ->
        val p = line.trim().split(' ')
        if (p.size < 4) null else Mount(p[0], p[1], p[2], p[3].split(','))
    }.toList()

    // ---------------- /proc/version ----------------

    /** Show the leading version on the dashboard and the full kernel version string separately. */
    fun kernelVersion(text: String): String? =
        Regex("""Linux version (\S+)""").find(text)?.groupValues?.get(1)
}
