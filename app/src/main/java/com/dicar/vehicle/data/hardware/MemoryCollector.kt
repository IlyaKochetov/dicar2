package com.dicar.vehicle.data.hardware

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import java.io.File

/** Memory capacity, partitions and VM heap limit. */
fun collectMemory(context: Context): HwSection {
    val mem = SysFs.text("/proc/meminfo")?.let { ProcParse.memInfo(it) } ?: emptyMap()
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    val info = am?.let { ActivityManager.MemoryInfo().also { m -> it.getMemoryInfo(m) } }

    return section(HwCategory.MEMORY) {
        block {
            item("Total capacity", HwFormat.bytes(info?.totalMem ?: mem["MemTotal"]))
            // Kernel MemTotal excludes kernel and reserved memory, so estimate the advertised capacity.
            item("Nominal capacity", HwFormat.nominalRam(info?.totalMem ?: mem["MemTotal"]))
            itemIfPresent("Memory type", SysFs.firstProp("ro.boot.ddr_type", "ro.boot.hardware.ddr"))
            flag("Low-memory device", am?.isLowRamDevice)
            item("Low-memory threshold", HwFormat.bytes(info?.threshold))
        }

        block("Details (/proc/meminfo)") {
            // These fields explain the difference between available and free memory: cache can be reclaimed.
            MEMINFO_KEYS.forEach { (key, label) ->
                itemIfPresent(label, mem[key]?.let { HwFormat.bytes(it) })
            }
        }

        block("Swap and ZRAM") {
            item("Total swap", HwFormat.bytes(mem["SwapTotal"]))
            item("Free swap", HwFormat.bytes(mem["SwapFree"]))
            val zramDisks = SysFs.children("/sys/block") { it.name.startsWith("zram") }
            item("ZRAM devices", if (zramDisks.isEmpty()) "None" else "${zramDisks.size} devices")
            zramDisks.forEach { d ->
                itemIfPresent(
                    "${d.name} compression algorithm",
                    SysFs.text("${d.absolutePath}/comp_algorithm")
                        // The active algorithm is marked in brackets; the rest are alternatives.
                        ?.let { Regex("\\[(\\w+)]").find(it)?.groupValues?.get(1) ?: it },
                )
                itemIfPresent("${d.name} capacity", SysFs.long("${d.absolutePath}/disksize")?.let { HwFormat.bytes(it) })
            }
        }

        block("App heap limits") {
            // These values indicate the app's memory limits and are useful when investigating OOM.
            item("Standard heap limit", am?.memoryClass?.let { "$it MB" })
            item("Large heap limit", am?.largeMemoryClass?.let { "$it MB" })
            item("Process heap limit", HwFormat.bytes(Runtime.getRuntime().maxMemory()))
            item("Dalvik heap size", SysFs.prop("dalvik.vm.heapsize"))
            item("Dalvik growth limit", SysFs.prop("dalvik.vm.heapgrowthlimit"))
            item("Dalvik initial heap", SysFs.prop("dalvik.vm.heapstartsize"))
        }
    }
}

/** Storage partition capacities and media model. */
fun collectStorage(context: Context): HwSection {
    val mounts = SysFs.text("/proc/mounts")?.let { ProcParse.mounts(it) }.orEmpty()

    return section(HwCategory.STORAGE) {
        block("Capacity") {
            // This corresponds to the value shown in Settings → Storage.
            volume("Internal storage", Environment.getDataDirectory())
            volume("System partition", Environment.getRootDirectory())
            context.getExternalFilesDir(null)?.let { volume("App external directory", it) }
            volume("Cache partition", context.cacheDir)
        }

        val sm = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
        val volumes = runCatching { sm?.storageVolumes }.getOrNull().orEmpty()
        if (volumes.isNotEmpty()) {
            block("Storage volumes") {
                volumes.forEachIndexed { i, v ->
                    val name = runCatching { v.getDescription(context) }.getOrNull() ?: "Volume ${i + 1}"
                    item(
                        name,
                        buildList {
                            add(if (v.isRemovable) "Removable" else "Built-in")
                            if (v.isPrimary) add("Primary")
                            if (v.isEmulated) add("Emulated")
                            add(runCatching { v.state }.getOrNull() ?: HwItem.UNKNOWN)
                        }.joinToString(" · "),
                    )
                }
            }
        }

        // Physical media: UFS uses /sys/block/sd*, eMMC uses mmcblk*.
        val blocks = SysFs.children("/sys/block") { BLOCK_DEVICE.matches(it.name) }
        if (blocks.isNotEmpty()) {
            block("Physical storage media") {
                blocks.forEach { dev ->
                    val base = "${dev.absolutePath}/device"
                    val model = SysFs.text("$base/model") ?: SysFs.text("$base/name")
                    val vendor = SysFs.text("$base/vendor") ?: SysFs.text("$base/manfid")
                    // size is measured in 512-byte sectors, not bytes.
                    val sizeBytes = SysFs.long("${dev.absolutePath}/size")?.times(512)
                    item(
                        dev.name,
                        listOfNotNull(
                            model?.trim(),
                            vendor?.trim()?.takeIf { it != model?.trim() },
                            SysFs.text("$base/rev")?.trim()?.let { "Firmware $it" },
                            sizeBytes?.let { HwFormat.bytes(it) },
                        ).joinToString(" · ").ifEmpty { HwItem.UNKNOWN },
                    )
                }
            }
        }

        // /data_mirror, /data/user and app-private directories are bind mounts of /data;
    // list meaningful partitions such as /, /vendor and /data instead.
    val interesting = mounts.filter { m ->
        m.fsType in INTERESTING_FS && NOISY_MOUNTS.none { m.point.startsWith(it) }
    }
        if (interesting.isNotEmpty()) {
            block("Mounted file systems") {
                item("Mount count", "${mounts.size} mounts")
                interesting.distinctBy { it.point }.forEach { m ->
                    item(m.point, "${m.fsType}（${if ("ro" in m.options) "read-only" else "writable"}）")
                }
            }
        }
    }
}

private fun BlockScope.volume(label: String, dir: File) {
    val stat = runCatching { StatFs(dir.absolutePath) }.getOrNull()
    if (stat == null) {
        item(label, null as String?)
        return
    }
    val total = stat.blockCountLong * stat.blockSizeLong
    val free = stat.availableBlocksLong * stat.blockSizeLong
    item(label, HwFormat.usage(total - free, total))
}

private val BLOCK_DEVICE = Regex("(sd[a-z]|mmcblk\\d+|nvme\\d+n\\d+)")

private val INTERESTING_FS = setOf("ext4", "f2fs", "erofs", "vfat", "exfat", "ntfs", "squashfs", "ubifs")

private val NOISY_MOUNTS = listOf(
    "/apex", "/data_mirror", "/data/user", "/data/data", "/data/misc", "/mnt/pass_through",
)

private val MEMINFO_KEYS = listOf(
    "MemTotal" to "MemTotal (kernel-visible)",
    "MemFree" to "MemFree (free)",
    "MemAvailable" to "MemAvailable (available)",
    "Buffers" to "Buffers (block cache)",
    "Cached" to "Cached (page cache)",
    "Active" to "Active (active)",
    "Inactive" to "Inactive (reclaimable)",
    "Dirty" to "Dirty (pending write)",
    "Shmem" to "Shmem (shared memory)",
    "Slab" to "Slab (kernel objects)",
    "KernelStack" to "KernelStack (kernel stack)",
    "PageTables" to "PageTables (page tables)",
    "VmallocTotal" to "VmallocTotal (kernel virtual space)",
    "CmaTotal" to "CmaTotal (contiguous memory)",
)
