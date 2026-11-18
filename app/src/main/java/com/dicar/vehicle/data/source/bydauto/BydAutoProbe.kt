package com.dicar.vehicle.data.source.bydauto

import android.content.Context
import android.os.Build
import android.util.Log
import com.dicar.vehicle.R
import com.dicar.vehicle.data.source.bydauto.BydDevice.Companion.unwrap
import java.io.File
import java.lang.reflect.Modifier
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipFile

/**
 * 开发探测工具（需求 2.5：「先用反射枚举所有 BYDAuto*Device，确认实际可用方法」）。
 *
 * 1. 扫描 BOOTCLASSPATH 中各 jar 的 dex，找出所有 android.hardware.bydauto.* 类；
 *    扫描失败时退回到 [BydApiMap.KNOWN_DEVICE_CLASSES] 已知列表；
 * 2. 对每个类输出 public static 常量（区域编号、档位编码等）与 public 方法签名；
 * 3. 对设备类（有 getInstance）实际调用所有无参 get / is / has 方法，记录当前返回值。
 *
 * 结果写入 /sdcard/Android/data/com.dicar.vehicle/files/probe/，用
 *   adb pull /sdcard/Android/data/com.dicar.vehicle/files/probe/
 * 拉回电脑后对照修改 [BydApiMap]。
 */
class BydAutoProbe(
    private val context: Context,
    /** 读真实值用的通道（通常是无线调试辅助进程）；为 null 时退回进程内反射。 */
    private val accessProvider: () -> BydAccess? = { null },
) {

    data class Result(val file: File?, val report: String)

    fun run(): Result {
        val sb = StringBuilder()
        sb.appendLine(context.getString(R.string.probe_title))
        sb.appendLine("time      : ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
        sb.appendLine("model     : ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
        sb.appendLine("android   : ${Build.VERSION.RELEASE} / SDK ${Build.VERSION.SDK_INT}")
        sb.appendLine("display   : ${Build.DISPLAY}")
        sb.appendLine("incremental: ${Build.VERSION.INCREMENTAL}")
        sb.appendLine()

        val scanned = scanBootClasspath(sb)
        val classNames = (scanned + BydApiMap.KNOWN_DEVICE_CLASSES)
            .filter { it.startsWith(PACKAGE_PREFIX) && '$' !in it }
            .toSortedSet()
        sb.appendLine(context.getString(R.string.probe_classes, classNames.size, scanned.size))
        sb.appendLine()

        for (name in classNames) {
            try {
                dumpClass(name, sb)
            } catch (e: Throwable) {
                sb.appendLine(context.getString(R.string.probe_dump_failed, name, unwrap(e)))
            }
        }

        val report = sb.toString()
        val file = try {
            val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "probe").apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            File(dir, "bydauto_probe_$stamp.txt").apply { writeText(report) }
        } catch (e: Throwable) {
            Log.w(TAG, "write report failed", e)
            null
        }
        Log.i(TAG, "probe done: ${classNames.size} classes -> ${file?.absolutePath}")
        return Result(file, report)
    }

    private fun dumpClass(name: String, sb: StringBuilder) {
        val cls = try {
            Class.forName(name)
        } catch (e: Throwable) {
            sb.appendLine(context.getString(R.string.probe_class_missing, name, e.javaClass.simpleName))
            return
        }
        sb.appendLine("## $name")
        cls.superclass?.takeIf { it != Any::class.java }?.let { sb.appendLine("  extends ${it.name}") }

        // 常量
        val constants = cls.declaredFields.filter {
            Modifier.isStatic(it.modifiers) && Modifier.isPublic(it.modifiers) &&
                (it.type.isPrimitive || it.type == String::class.java)
        }
        if (constants.isNotEmpty()) {
            sb.appendLine(context.getString(R.string.probe_constants))
            constants.sortedBy { it.name }.forEach { f ->
                val value = runCatching { f.get(null) }.getOrElse { "?" }
                sb.appendLine("    ${f.name} = $value")
            }
        }

        // 方法签名
        val methods = cls.declaredMethods.filter { Modifier.isPublic(it.modifiers) }.sortedBy { it.name }
        if (methods.isNotEmpty()) {
            sb.appendLine(context.getString(R.string.probe_methods))
            methods.forEach { m ->
                val static = if (Modifier.isStatic(m.modifiers)) "static " else ""
                val params = m.parameterTypes.joinToString(", ") { it.simpleName }
                sb.appendLine("    $static${m.returnType.simpleName} ${m.name}($params)")
            }
        }

        // 设备类：实例化后调用无参 getter
        val hasGetInstance = cls.methods.any { it.name == "getInstance" && Modifier.isStatic(it.modifiers) }
        if (hasGetInstance) {
            val access = accessProvider()
            val device = if (access == null) BydDevice(context, name) else null
            val instance = device?.obtain()
            if (access == null && instance == null) {
                sb.appendLine(context.getString(R.string.probe_instance_failed))
            } else {
                sb.appendLine(context.getString(R.string.probe_values) + if (access != null) context.getString(R.string.probe_access, context.getString(if (access is AdbBydAccess) R.string.probe_access_adb else R.string.probe_access_process)) else "")
                cls.methods
                    .filter {
                        !Modifier.isStatic(it.modifiers) && it.parameterTypes.isEmpty() &&
                            it.returnType != Void.TYPE && GETTER_PREFIXES.any { p -> it.name.startsWith(p) } &&
                            it.name !in SKIP_GETTERS
                    }
                    .sortedBy { it.name }
                    .forEach { m ->
                        val value = if (access != null) {
                            when (val r = access.readGetter(name, m.name, IntArray(0))) {
                                is BydDevice.CallResult.Value -> render(r.value)
                                BydDevice.CallResult.Missing -> context.getString(R.string.probe_missing)
                                is BydDevice.CallResult.Error -> "!! ${r.error.javaClass.simpleName}: ${r.error.message}"
                            }
                        } else {
                            try {
                                render(m.invoke(instance))
                            } catch (e: Throwable) {
                                "!! ${unwrap(e).javaClass.simpleName}: ${unwrap(e).message}"
                            }
                        }
                        sb.appendLine("    ${m.name}() = $value")
                    }
            }
        }
        sb.appendLine()
    }

    private fun render(value: Any?): String = when (value) {
        null -> "null"
        is IntArray -> value.contentToString()
        is DoubleArray -> value.contentToString()
        is FloatArray -> value.contentToString()
        is LongArray -> value.contentToString()
        is BooleanArray -> value.contentToString()
        is ByteArray -> value.contentToString()
        is Array<*> -> value.contentDeepToString()
        else -> value.toString()
    }.let { if (it.length > 300) it.take(300) + "…" else it }

    // ------------------------------------------------------------------
    // dex 扫描：直接解析 jar 里 classes*.dex 的 type_ids，不依赖已废弃的 DexFile API
    // ------------------------------------------------------------------

    private fun scanBootClasspath(sb: StringBuilder): Set<String> {
        val jars = linkedSetOf<String>()
        System.getenv("BOOTCLASSPATH")?.split(':')?.filter { it.isNotBlank() }?.let(jars::addAll)
        File("/system/framework").listFiles { f -> f.name.endsWith(".jar") }?.forEach { jars += it.absolutePath }

        val found = sortedSetOf<String>()
        var dexTotal = 0
        sb.appendLine(context.getString(R.string.probe_jars, jars.size))
        for (path in jars) {
            val scan = try {
                scanJar(File(path))
            } catch (e: Throwable) {
                sb.appendLine(context.getString(R.string.probe_scan_failed, path, e.javaClass.simpleName))
                continue
            }
            dexTotal += scan.dexCount
            // 只列出含 dex 的 jar；boot image 预编译后很多 jar 里没有 dex，这是正常的
            if (scan.dexCount > 0) {
                sb.appendLine(context.getString(R.string.probe_scan_result, path, scan.dexCount, scan.typeCount, scan.hits.size))
            }
            found += scan.hits
        }
        if (dexTotal == 0) sb.appendLine(context.getString(R.string.probe_no_dex))
        sb.appendLine()
        return found
    }

    private class JarScan(val dexCount: Int, val typeCount: Int, val hits: Set<String>)

    private fun scanJar(file: File): JarScan {
        if (!file.canRead()) return JarScan(0, 0, emptySet())
        val hits = HashSet<String>()
        var dexCount = 0
        var typeCount = 0
        ZipFile(file).use { zip ->
            zip.entries().asSequence()
                .filter { it.name.matches(Regex("classes\\d*\\.dex")) }
                .forEach { entry ->
                    val bytes = zip.getInputStream(entry).use { it.readBytes() }
                    val scan = DexTypeScanner.scan(bytes, DESCRIPTOR_PREFIX)
                    dexCount++
                    typeCount += scan.typeCount
                    hits += scan.matches.map { it.removePrefix("L").removeSuffix(";").replace('/', '.') }
                }
        }
        return JarScan(dexCount, typeCount, hits)
    }

    companion object {
        private const val TAG = "BydAutoProbe"
        private const val PACKAGE_PREFIX = "android.hardware.bydauto."
        private const val DESCRIPTOR_PREFIX = "Landroid/hardware/bydauto/"
        private val GETTER_PREFIXES = listOf("get", "is", "has")
        private val SKIP_GETTERS = setOf("getClass", "getInstance", "hashCode")
    }
}
