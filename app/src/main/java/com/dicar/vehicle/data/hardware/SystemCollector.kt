package com.dicar.vehicle.data.hardware

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.Settings
import android.webkit.WebView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** System version, build, runtime and head-unit identification. */
fun collectSystem(context: Context): HwSection {
    val pm = context.packageManager

    return section(HwCategory.SYSTEM) {
        block("Device") {
            item("Manufacturer", Build.MANUFACTURER)
            item("Brand", Build.BRAND)
            item("Model", Build.MODEL)
            item("Device codename", Build.DEVICE)
            item("Product name", Build.PRODUCT)
            itemIfPresent("Marketing name", SysFs.firstProp("ro.product.marketname", "ro.config.marketing_name"))
            item("Serial number", serial())
        }

        block("Android") {
            item("Version", "Android ${Build.VERSION.RELEASE}")
            item("API level", Build.VERSION.SDK_INT.toString())
            item("Security patch", Build.VERSION.SECURITY_PATCH)
            item("Codename", Build.VERSION.CODENAME)
            item("Build ID", Build.ID)
            item("Build display", Build.DISPLAY)
            item("Incremental build", Build.VERSION.INCREMENTAL)
            item("Build type", "${Build.TYPE} / ${Build.TAGS}")
            item("Build date", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(Build.TIME)))
            item("Build fingerprint", Build.FINGERPRINT)
        }

        block("Kernel and runtime") {
            // Newer Android versions restrict /proc/version; fall back to the JVM os.version property.
            val version = SysFs.text("/proc/version")
            item("Kernel version", version?.let { ProcParse.kernelVersion(it) } ?: System.getProperty("os.version"))
            itemIfPresent("Full kernel string", version?.replace('\n', ' '))
            item("Java VM", sysProps("java.vm.name", "java.vm.version"))
            item("ART/Dalvik", SysFs.prop("persist.sys.dalvik.vm.lib.2"))
            flag("64-bit Zygote", SysFs.prop("ro.zygote")?.contains("64"))
            item("Runtime platform", sysProps("os.name", "os.arch", "os.version"))
            item("Clock frequency", "${HardwareMonitor.clockTicks} Hz")
        }

        block("Security and integrity") {
            // Together these three values help identify modifications; any anomaly merits attention.
            item("SELinux", selinux())
            flag("Debuggable build", SysFs.prop("ro.debuggable") == "1")
            flag("Release signed", Build.TAGS?.contains("release-keys"))
            flag("Root (su) detected", suPath() != null)
            itemIfPresent("su path", suPath())
            flag(
                "ADB enabled",
                Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1,
                yes = "Enabled", no = "Disabled",
            )
            item("Storage encryption", encryption())
            item("Verified boot", SysFs.firstProp("ro.boot.verifiedbootstate", "ro.boot.veritymode"))
            flag("Treble", SysFs.prop("ro.treble.enabled") == "true")
            item("VNDK version", SysFs.firstProp("ro.vndk.version", "persist.sys.vndk"))
        }

        block("Runtime state") {
            val uptime = SystemClock.elapsedRealtime()
            item("Uptime", HwFormat.duration(uptime))
            item("Awake time", HwFormat.duration(SystemClock.uptimeMillis()))
            item("Deep sleep", HwFormat.duration(uptime - SystemClock.uptimeMillis()))
            item(
                "Last boot",
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                    .format(Date(System.currentTimeMillis() - uptime)),
            )
            item("Time zone", TimeZone.getDefault().let { "${it.id}（${it.displayName}）" })
            item("Language", Locale.getDefault().toString())
            item("External storage state", Environment.getExternalStorageState())
        }

        block("Software environment") {
            // Android 11 package visibility restricts the count without QUERY_ALL_PACKAGES.
            // Indicate that the count is incomplete rather than requesting that permission.
            val partial = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) " (visible to this app only)" else ""
            item("Installed apps", runCatching {
                "${pm.getInstalledPackages(0).size} apps$partial"
            }.getOrNull())
            item("System apps", runCatching {
                val count = pm.getInstalledApplications(0)
                    .count { (it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0 }
                "$count apps$partial"
            }.getOrNull())
            item("WebView", webViewVersion(context))
            item("Google Play services", packageVersion(pm, "com.google.android.gms"))
            item("Shared libraries", runCatching { pm.systemSharedLibraryNames?.size }.getOrNull()?.let { "$it libraries" })
        }

        // Head-unit identification: whether it is BYD and which DiLink generation.
        val bydProps = SysFs.allProps().filterKeys { it.contains("byd", ignoreCase = true) }
        block("Head unit") {
            flag(
                "BYDAuto framework",
                runCatching { Class.forName("android.hardware.bydauto.BYDAutoDeviceManager"); true }
                    .getOrDefault(false),
                yes = "Installed", no = "Not detected",
            )
            itemIfPresent("DiLink version", SysFs.firstProp("ro.byd.dilink.version", "ro.build.dilink.version"))
            itemIfPresent("Vehicle model code", SysFs.firstProp("ro.byd.car.type", "ro.byd.vehicle.type", "ro.byd.model"))
            item("BYD properties", "${bydProps.size} entries")
            bydProps.toSortedMap().forEach { (k, v) -> item("  $k", v) }
        }
    }
}

/** System features declared by `PackageManager`. */
fun collectFeatures(context: Context): HwSection {
    val features = runCatching {
        context.packageManager.systemAvailableFeatures.toList()
    }.getOrNull().orEmpty()
    val named = features.mapNotNull { f -> f.name?.let { it to f.version } }.sortedBy { it.first }
    val unnamed = features.count { it.name == null }

    return section(HwCategory.FEATURE) {
        block {
            item("Count", "${features.size} items")
            // Unnamed entries represent OpenGL ES versions; calculate those separately.
            if (unnamed > 0) item("Unnamed features", "$unnamed items (OpenGL ES versions)")
        }
        block("All features") {
            named.forEach { (name, version) ->
                item(name.removePrefix("android.hardware.").removePrefix("android.software."),
                    if (version > 0) "Version $version" else "Supported")
            }
        }
    }
}

/**
 * All system properties.
 *
 * `getprop` can return thousands of properties useful for comparing devices.
 * The UI collapses them by default; export includes the full list.
 */
fun collectProps(): HwSection {
    val props = SysFs.allProps()
    return section(HwCategory.PROPS) {
        block {
            item("Count", if (props.isEmpty()) "Read failed (getprop unavailable)" else "${props.size} entries")
        }
        if (props.isEmpty()) return@section
        // Group by prefix to make large property lists navigable.
        PROP_GROUPS.forEach { (prefix, label) ->
            val group = props.filterKeys { it.startsWith(prefix) }.toSortedMap()
            if (group.isNotEmpty()) {
                block("$label (${group.size} entries)") {
                    group.forEach { (k, v) -> item(k, v) }
                }
            }
        }
        val rest = props.filterKeys { key -> PROP_GROUPS.none { key.startsWith(it.first) } }.toSortedMap()
        if (rest.isNotEmpty()) {
            block("Other (${rest.size} entries)") {
                rest.forEach { (k, v) -> item(k, v) }
            }
        }
    }
}

private val PROP_GROUPS = listOf(
    "ro.product." to "Product",
    "ro.build." to "Build",
    "ro.boot." to "Boot",
    "ro.hardware" to "Hardware",
    "ro.board." to "Board",
    "ro.vendor." to "Vendor partition",
    "ro.config." to "Configuration",
    "dalvik.vm." to "Virtual machine",
    "persist.sys." to "System persistent",
    "persist." to "Other persistent",
    "sys." to "Runtime",
    "init.svc." to "Service status",
    "net." to "Network",
    "gsm." to "Mobile network",
    "audio." to "Audio",
    "media." to "Multimedia",
    "debug." to "Debug",
)

/** Combine available JVM properties, skipping those absent on a given ART version. */
private fun sysProps(vararg keys: String): String? =
    keys.mapNotNull { System.getProperty(it) }.joinToString(" ").takeIf { it.isNotBlank() }

@Suppress("DEPRECATION") // On older Android versions Build.SERIAL can provide the serial without permission.
private fun serial(): String? = when {
    // Android 10 restricts Build.SERIAL and getSerial() requires READ_PHONE_STATE.
    // Explain unavailability rather than requesting permission for one field.
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> "Restricted by Android"
    else -> Build.SERIAL.takeIf { it != Build.UNKNOWN }
}

private fun selinux(): String {
    // Newer Android may restrict the sysfs node; the system getenforce command can be used.
    val raw = SysFs.text("/sys/fs/selinux/enforce")
        ?: SysFs.exec("getenforce")?.trim()
        ?: SysFs.prop("ro.boot.selinux")
    // Accept only known values so command errors are not displayed as status.
    return when (raw?.lowercase()) {
        "1", "enforcing" -> "Enforcing"
        "0", "permissive" -> "Permissive"
        "disabled" -> "Disabled"
        else -> HwItem.UNKNOWN
    }
}

private fun encryption(): String = when (SysFs.firstProp("ro.crypto.state", "ro.crypto.type")) {
    "encrypted" -> "Encrypted"
    "unencrypted" -> "Unencrypted"
    "file" -> "File-based encryption"
    "block" -> "Block-based encryption"
    else -> SysFs.prop("ro.crypto.type") ?: HwItem.UNKNOWN
}

private fun suPath(): String? = SU_PATHS.firstOrNull { runCatching { File(it).exists() }.getOrDefault(false) }

private val SU_PATHS = listOf(
    "/system/bin/su", "/system/xbin/su", "/sbin/su",
    "/su/bin/su", "/system/sd/xbin/su", "/vendor/bin/su",
)

private fun webViewVersion(context: Context): String? {
    runCatching { WebView.getCurrentWebViewPackage() }.getOrNull()?.let {
        return "${it.packageName} ${it.versionName}"
    }
    return packageVersion(context.packageManager, "com.google.android.webview")
}

private fun packageVersion(pm: PackageManager, pkg: String): String? =
    runCatching { pm.getPackageInfo(pkg, 0).versionName }.getOrNull()
