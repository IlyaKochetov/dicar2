package com.dicar.vehicle.data.hardware

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 硬件信息总装。
 *
 * 每个采集器单独 try：某一类读失败（权限、厂商魔改、节点不存在）只应该让那一张卡片
 * 显示错误，不能拖垮整页。这在车机上不是假设——不同固件能读到的东西差别很大。
 */
class HardwareInspector(private val context: Context) {

    data class Export(val file: File?, val text: String)

    /** 静态信息。耗时主要在编解码器枚举和 getprop 上，必须放后台线程。 */
    fun collectStatic(): List<HwSection> = listOf(
        guard(HwCategory.SOC) { collectSoc(context) },
        guard(HwCategory.GPU) { collectGpu(context) },
        guard(HwCategory.MEMORY) { collectMemory(context) },
        guard(HwCategory.STORAGE) { collectStorage(context) },
        guard(HwCategory.DISPLAY) { collectDisplay(context) },
        guard(HwCategory.SENSOR) { collectSensors(context) },
        guard(HwCategory.CAMERA) { collectCameras(context) },
        guard(HwCategory.NETWORK) { collectNetwork(context) },
        guard(HwCategory.AUDIO) { collectAudio(context) },
        guard(HwCategory.CODEC) { collectCodecs() },
        guard(HwCategory.SYSTEM) { collectSystem(context) },
        guard(HwCategory.FEATURE) { collectFeatures(context) },
        guard(HwCategory.PROPS) { collectProps() },
    )

    private inline fun guard(category: HwCategory, body: () -> HwSection): HwSection = try {
        body()
    } catch (e: Throwable) {
        Log.w(TAG, "collect ${category.name} failed", e)
        section(category) {
            block { item("Scan failed", "${e.javaClass.simpleName}: ${e.message ?: "No details"}") }
        }
    }

    /**
     * 导出成文本。[redact] 为 true 时抹掉能定位到这台设备的标识（序列号、MAC、IP、
     * Android ID 等）——导出文件是用来发给别人看的，默认不该把这些带出去。
     */
    fun export(sections: List<HwSection>, redact: Boolean): Export {
        val text = render(sections, redact)
        val file = runCatching {
            val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "hardware").apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            File(dir, "hardware_$stamp.txt").apply { writeText(text) }
        }.getOrElse {
            Log.w(TAG, "write hardware report failed", it)
            null
        }
        return Export(file, text)
    }

    private fun render(sections: List<HwSection>, redact: Boolean) = buildString {
        appendLine("# Head Unit Hardware Report")
        appendLine("Time    : ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
        appendLine("Device  : ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
        appendLine("System  : Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
        appendLine("Tool    : DiCar International")
        appendLine(
            if (redact) "Identifiers: redacted (serial / MAC / IP / Android ID)"
            else "Identifiers: retained; check before sharing"
        )
        appendLine()
        for (s in sections) {
            appendLine("## ${s.category.label}")
            for (b in s.blocks) {
                b.subtitle?.let { appendLine("  [$it]") }
                for (i in b.items) {
                    val value = if (redact) Redactor.scrub(i.label, i.value) else i.value
                    appendLine("    ${i.label.padEnd(LABEL_WIDTH)} ${value.replace('\n', ' ')}")
                }
            }
            appendLine()
        }
    }

    companion object {
        private const val TAG = "HardwareInspector"
        private const val LABEL_WIDTH = 20
    }
}

/** 电池。车机常常没有独立电池，这时如实说明而不是报一堆 0。 */
fun batterySection(live: BatteryLive?): HwSection = section(HwCategory.BATTERY) {
    if (live == null) {
        block { item("Battery", "System did not provide battery information") }
        return@section
    }
    block {
        flag("Battery present", live.present, yes = "Yes", no = "No")
        item("Battery level", HwFormat.percent(live.levelPercent))
        item("Status", live.status)
        item("Health", live.health)
        item("Power source", live.plugged)
        item("Type", live.technology)
    }
    block("Readings") {
        item("Voltage", live.voltageMv?.let { "$it mV" })
        item("Temperature", HwFormat.celsius(live.temperature))
        item("Instantaneous current", HwFormat.microAmp(live.currentNowUa))
        item("Average current", HwFormat.microAmp(live.currentAvgUa))
        // 这个值除以电量百分比就能估出设计容量，是同类工具算「电池健康度」的依据
        item("Remaining charge", live.chargeCounterUah?.let { "${it / 1000} mAh" })
        item("Remaining energy", live.energyCounterNwh?.let { "${it / 1_000_000} mWh" })
        item("Estimated full capacity", estimateCapacity(live))
        item("Cycle count", live.cycleCount?.toString())
    }
}

/**
 * 由「剩余电荷 ÷ 当前电量百分比」反推总容量。
 * 只是估算：电量百分比本身就是电量计拟合出来的，低电量时误差很大。
 */
private fun estimateCapacity(live: BatteryLive): String? {
    val charge = live.chargeCounterUah ?: return null
    val percent = live.levelPercent?.takeIf { it > 5f } ?: return null
    return "approx. ${(charge / 1000 / percent * 100).toInt()} mAh"
}

/** 温度。各路 thermal zone 的当前读数。 */
fun thermalSection(live: LiveStats): HwSection = section(HwCategory.THERMAL) {
    block {
        item("System thermal status", live.thermalStatus)
        item("Thermal zones", "${live.thermal.size} zones")
        item("Maximum temperature", live.thermal.mapNotNull { it.celsius }.maxOrNull()?.let { HwFormat.celsius(it) })
    }
    if (live.thermal.isEmpty()) {
        block {
            item("Note", "System restricts access to /sys/class/thermal on newer Android versions")
        }
        return@section
    }
    block("Thermal zones (${live.thermal.size})") {
        // 按温度从高到低，最值得关注的排最上面
        live.thermal.sortedByDescending { it.celsius ?: -999f }.forEach { t ->
            item("${t.type} (${t.zone})", HwFormat.celsius(t.celsius))
        }
    }
}

/**
 * 导出时抹掉设备标识。
 *
 * 两道：按字段名命中敏感词的整条打码；剩下的再用正则扫一遍 IP 和 MAC——
 * 同一个 MAC 可能藏在某条不起眼的系统属性里，光看字段名会漏。
 */
object Redactor {

    const val MASK = "[REDACTED]"

    fun scrub(label: String, value: String): String {
        if (value == HwItem.UNKNOWN || value.isBlank()) return value
        val key = label.lowercase()
        if (SENSITIVE_KEYS.any { it in key }) return MASK
        return value
            .replace(MAC, MASK)
            // 回环和全零地址不是标识，留着反而有助于判断网络状态
            .replace(IPV4) { m -> if (m.value in KEEP_IPS) m.value else MASK }
    }

    private val SENSITIVE_KEYS = listOf(
        "serial", "序列号", "imei", "meid", "iccid", "imsi",
        "mac", "bssid", "ssid", "android_id", "androidid",
        "ip 地址", "ip地址", "uuid", "系统 id", "systemid", "设备 id", "deviceid",
    )

    private val MAC = Regex("""\b([0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}\b""")
    private val IPV4 = Regex("""\b((25[0-5]|2[0-4]\d|1?\d?\d)\.){3}(25[0-5]|2[0-4]\d|1?\d?\d)\b""")
    private val KEEP_IPS = setOf("127.0.0.1", "0.0.0.0", "255.255.255.255")
}
