package com.dicar.vehicle.data.hardware

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.opengl.GLES30
import android.os.Build

/** 处理器。核心拓扑、指令集、各簇频率范围。 */
fun collectSoc(context: Context): HwSection {
    val cpuinfo = SysFs.text("/proc/cpuinfo")
    val id = cpuinfo?.let { ProcParse.cpuIdentity(it) }
    val cores = cpuinfo?.let { ProcParse.cpuCores(it) }.orEmpty()
    val dirs = SysFs.cpuDirs()
    val platform = SysFs.firstProp("ro.board.platform", "ro.hardware", "ro.soc.model")

    return section(HwCategory.SOC) {
        block {
            // 安卓 12 起系统直接给型号；之前只能拿平台代号去查表，查不到就原样显示
            val socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Build.SOC_MODEL.takeIf { it != Build.UNKNOWN }
            } else null
            // 系统自己报的型号优先，但仍过一遍查表：有些平台报的是代号而不是市售名
            item("Model", socModel?.let { SiliconNames.soc(it) ?: it } ?: SiliconNames.soc(platform) ?: platform)
            item("Platform codename", platform)
            item(
                "Manufacturer",
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Build.SOC_MANUFACTURER.takeIf { it != Build.UNKNOWN }
                } else null
                    ?: SiliconNames.implementer(id?.implementer),
            )
            itemIfPresent("CPUinfo identifier", id?.hardware)
            itemIfPresent("x86 model string", id?.modelName)
            item("CPU cores", "${dirs.size.takeIf { it > 0 } ?: Runtime.getRuntime().availableProcessors()} cores")
            item("Cores available to process", "${Runtime.getRuntime().availableProcessors()} cores")
        }

        block("Instruction sets") {
            item("Primary ABI", Build.SUPPORTED_ABIS.firstOrNull())
            // 列表为空是「一个都不支持」，不是「读不到」，不能显示成未知
            item("64-bit ABI", Build.SUPPORTED_64_BIT_ABIS.joinToString(", ").ifEmpty { "None" })
            item("32-bit ABI", Build.SUPPORTED_32_BIT_ABIS.joinToString(", ").ifEmpty { "None" })
            item("Bitness", if (Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()) "64-bit" else "32-bit")
            itemIfPresent("Architecture version", id?.variant?.let { "variant 0x%x / revision %d".format(it, id.revision ?: 0) })
            val features = id?.features.orEmpty()
            if (features.isNotEmpty()) {
                item("Extension count", "${features.size} items")
                item("Extensions", features.joinToString(" "))
                // 挑出几个对实际性能影响明显的单独点名，省得在一长串里找
                val notable = features.mapNotNull { f ->
                    SiliconNames.featureNote(f)?.let { "$f（$it）" }
                }
                if (notable.isNotEmpty()) item("Notable extensions", notable.joinToString(", "))
            }
        }

        // 按「核心型号 + 最高频率」分簇，这就是 big.LITTLE 的实际分组
        val clusters = dirs.mapNotNull { dir ->
            val index = dir.name.removePrefix("cpu").toIntOrNull() ?: return@mapNotNull null
            val freq = "${dir.absolutePath}/cpufreq"
            val model = cores.firstOrNull { it.index == index }
                ?.let { SiliconNames.core(it.implementer, it.part) }
            Triple(index, model, SysFs.khz("$freq/cpuinfo_max_freq") ?: SysFs.khz("$freq/scaling_max_freq"))
        }.groupBy { it.second to it.third }

        if (clusters.isNotEmpty()) {
            block("CPU clusters") {
                clusters.entries
                    .sortedByDescending { it.key.second ?: 0 }
                    .forEachIndexed { i, (key, members) ->
                        val (model, maxKhz) = key
                        val indices = members.map { it.first }.sorted()
                        val minKhz = SysFs.khz("/sys/devices/system/cpu/cpu${indices.first()}/cpufreq/cpuinfo_min_freq")
                        item(
                            "Cluster ${i + 1}" + (model?.let { "（$it）" } ?: ""),
                            listOfNotNull(
                                "${indices.size} cores",
                                "CPU${indices.first()}-${indices.last()}",
                                // 频率读不到就别写「未知 ~ 未知」，那一串比空着还难看
                                if (minKhz == null && maxKhz == null) null
                                else "${HwFormat.kHz(minKhz)} ~ ${HwFormat.kHz(maxKhz)}",
                            ).joinToString(" · "),
                        )
                    }
            }
        }

        block("Frequency scaling") {
            val gov0 = "/sys/devices/system/cpu/cpu0/cpufreq"
            item("Current governor", SysFs.text("$gov0/scaling_governor"))
            item("Available governors", SysFs.text("$gov0/scaling_available_governors")?.replace(' ', '、'))
            item("Driver", SysFs.text("$gov0/scaling_driver"))
            itemIfPresent(
                "Available frequencies",
                SysFs.text("$gov0/scaling_available_frequencies")
                    ?.split(' ')
                    // 和 SysFs.khz 同样的理由：这里也可能是频率表序号而不是 kHz
                    ?.mapNotNull { it.toLongOrNull()?.takeIf { v -> v >= SysFs.MIN_PLAUSIBLE_KHZ } }
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { "${it.size} levels: ${HwFormat.kHz(it.min())}–${HwFormat.kHz(it.max())}" },
            )
        }

        block("Board") {
            item("Board name", Build.BOARD)
            item("Hardware codename", Build.HARDWARE)
            item("Bootloader", Build.BOOTLOADER)
            itemIfPresent("DDR type", SysFs.firstProp("ro.boot.ddr_type", "ro.boot.ddr_size"))
            item("Can run 32-bit apps", Build.SUPPORTED_32_BIT_ABIS.isNotEmpty())
        }
    }
}

/** 图形。EGL/GLES/Vulkan 能力。 */
fun collectGpu(context: Context): HwSection {
    val gl = GlProbe.query()
    val pm = context.packageManager
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager

    return section(HwCategory.GPU) {
        block {
            item("Model", gl?.renderer)
            item("Vendor", gl?.vendor)
            item("OpenGL ES", gl?.version)
            item("Shader language", gl?.glslVersion)
            item(
                "Reported API version",
                am?.deviceConfigurationInfo?.glEsVersion?.let { "OpenGL ES $it" },
            )
        }

        if (gl != null) {
            block("Capability limits") {
                item("Maximum texture size", gl.maxTextureSize?.let { "$it × $it" })
                item("Maximum cube-map size", gl.maxCubeMapSize?.let { "$it × $it" })
                item("Maximum viewport", gl.maxViewport?.let { "${it.first} × ${it.second}" })
                item("Maximum renderbuffer", gl.maxRenderbufferSize?.let { "$it × $it" })
                item("Vertex attributes", gl.maxVertexAttribs?.toString())
                item("Texture units (fragment)", gl.maxTextureUnits?.toString())
                item("Texture units (total)", gl.maxCombinedTextureUnits?.toString())
                item("Anisotropic filtering", gl.maxAnisotropy?.let { "${it.toInt()}×" })
                item("Maximum multisampling", gl.maxSamples?.let { "${it}× MSAA" })
                item("Compressed texture formats", gl.compressedFormats?.let { "$it types" })
            }
            block("Extensions") {
                item("Count", "${gl.extensions.size} items")
                // 这几个最常被用来判断能不能跑某类效果，单独列
                item("ASTC textures", gl.extensions.any { "texture_compression_astc" in it })
                item("ETC2 textures", gl.extensions.any { "ES3_compatibility" in it || "texture_compression_etc2" in it })
                item("Floating-point textures", gl.extensions.any { it.endsWith("texture_float") })
                item("Full list", gl.extensions.sorted().joinToString(" "))
            }
        }

        block("Vulkan") {
            val level = pm.featureVersion("android.hardware.vulkan.level")
            val hwVersion = pm.featureVersion("android.hardware.vulkan.version")
            item("Hardware supported", hwVersion != null)
            // version 是打包成整数的 Vulkan 版本号，按 (22,12,0) 位宽拆
            item("Version", hwVersion?.let { "%d.%d.%d".format(it shr 22, (it shr 12) and 0x3ff, it and 0xfff) })
            item("Hardware level", level?.toString())
            item("Compute capability", pm.featureVersion("android.hardware.vulkan.compute")?.let { "Level $it" })
        }

        block("Other") {
            item("AEP extension pack", pm.hasSystemFeature(PackageManager.FEATURE_OPENGLES_EXTENSION_PACK))
            // 频率是动态值，在顶部实时面板里看；这里给出节点路径，方便 adb 直接读
            itemIfPresent("Frequency node", SysFs.gpuFreqNode())
        }
    }
}

private fun PackageManager.featureVersion(name: String): Int? =
    runCatching { systemAvailableFeatures.firstOrNull { it.name == name }?.version }.getOrNull()

/**
 * 开一个 1×1 的离屏 EGL 上下文来问 GPU 的型号和能力。
 *
 * 没有别的办法：`GL_RENDERER` 这类字符串只有在**当前线程有 GL 上下文**时才能读。
 * 整个过程不碰屏幕、几十毫秒就结束，用完立刻拆掉，不会留下常驻的 GL 资源。
 */
private object GlProbe {

    data class Info(
        val renderer: String?,
        val vendor: String?,
        val version: String?,
        val glslVersion: String?,
        val extensions: List<String>,
        val maxTextureSize: Int?,
        val maxCubeMapSize: Int?,
        val maxRenderbufferSize: Int?,
        val maxViewport: Pair<Int, Int>?,
        val maxVertexAttribs: Int?,
        val maxTextureUnits: Int?,
        val maxCombinedTextureUnits: Int?,
        val maxAnisotropy: Float?,
        val maxSamples: Int?,
        val compressedFormats: Int?,
    )

    fun query(): Info? {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) return null
        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) return null

        var context = EGL14.EGL_NO_CONTEXT
        var surface = EGL14.EGL_NO_SURFACE
        try {
            val configs = arrayOfNulls<EGLConfig>(1)
            val count = IntArray(1)
            val attribs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_NONE,
            )
            if (!EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, count, 0) || count[0] == 0) return null

            context = EGL14.eglCreateContext(
                display, configs[0], EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0,
            )
            if (context == EGL14.EGL_NO_CONTEXT) return null

            surface = EGL14.eglCreatePbufferSurface(
                display, configs[0],
                intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0,
            )
            if (!EGL14.eglMakeCurrent(display, surface, surface, context)) return null

            val glVersion = GLES20.glGetString(GLES20.GL_VERSION)
            // GLES 3 才有的查询项，在 2.0 上调会报 GL_INVALID_ENUM，按版本串判断
            val es3 = glVersion?.contains("ES 3") == true
            return Info(
                renderer = GLES20.glGetString(GLES20.GL_RENDERER),
                vendor = GLES20.glGetString(GLES20.GL_VENDOR),
                version = glVersion,
                glslVersion = GLES20.glGetString(GLES20.GL_SHADING_LANGUAGE_VERSION),
                extensions = GLES20.glGetString(GLES20.GL_EXTENSIONS)
                    ?.split(' ')?.filter { it.isNotBlank() }.orEmpty(),
                maxTextureSize = geti(GLES20.GL_MAX_TEXTURE_SIZE),
                maxCubeMapSize = geti(GLES20.GL_MAX_CUBE_MAP_TEXTURE_SIZE),
                maxRenderbufferSize = geti(GLES20.GL_MAX_RENDERBUFFER_SIZE),
                maxViewport = IntArray(2).let {
                    GLES20.glGetIntegerv(GLES20.GL_MAX_VIEWPORT_DIMS, it, 0)
                    if (it[0] > 0) it[0] to it[1] else null
                },
                maxVertexAttribs = geti(GLES20.GL_MAX_VERTEX_ATTRIBS),
                maxTextureUnits = geti(GLES20.GL_MAX_TEXTURE_IMAGE_UNITS),
                maxCombinedTextureUnits = geti(GLES20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS),
                maxAnisotropy = FloatArray(1).let {
                    GLES20.glGetFloatv(GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT, it, 0)
                    if (GLES20.glGetError() == GLES20.GL_NO_ERROR && it[0] > 1f) it[0] else null
                },
                maxSamples = if (es3) geti(GLES30.GL_MAX_SAMPLES) else null,
                compressedFormats = geti(GLES20.GL_NUM_COMPRESSED_TEXTURE_FORMATS),
            )
        } catch (e: Throwable) {
            return null
        } finally {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
        }
    }

    private fun geti(name: Int): Int? {
        val out = IntArray(1)
        GLES20.glGetIntegerv(name, out, 0)
        return if (GLES20.glGetError() == GLES20.GL_NO_ERROR && out[0] > 0) out[0] else null
    }

    /** GL_EXT_texture_filter_anisotropic 的常量，安卓 SDK 里没有定义。 */
    private const val GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT = 0x84FF
}
