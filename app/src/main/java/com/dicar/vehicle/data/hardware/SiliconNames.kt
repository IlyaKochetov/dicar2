package com.dicar.vehicle.data.hardware

/**
 * Translate numeric `/proc/cpuinfo` identifiers and platform codes into readable names.
 *
 * These are lookups only: return unknown hexadecimal values or platform codes unchanged rather than guessing.
 */
object SiliconNames {

    /** `CPU implementer`: vendor identifier assigned by ARM. */
    fun implementer(code: Int?): String? = when (code) {
        null -> null
        0x41 -> "ARM"
        0x42 -> "Broadcom"
        0x43 -> "Cavium"
        0x44 -> "DEC"
        0x46 -> "Fujitsu"
        0x48 -> "HiSilicon"
        0x49 -> "Infineon"
        0x4d -> "Motorola / Freescale"
        0x4e -> "NVIDIA"
        0x50 -> "Applied Micro"
        0x51 -> "Qualcomm"
        0x53 -> "Samsung"
        0x56 -> "Marvell"
        0x61 -> "Apple"
        0x66 -> "Faraday"
        0x69 -> "Intel"
        0x6d -> "Microsoft"
        0xc0 -> "Ampere"
        else -> "0x%02x".format(code)
    }

    /**
     * `CPU implementer` + `CPU part` → core model.
     *
     * Part IDs distinguish big and little cores in the same SoC more reliably than frequency,
     * since some cores share a maximum frequency.
     */
    fun core(implementer: Int?, part: Int?): String? {
        if (part == null) return null
        val name = when (implementer) {
            0x41 -> ARM_PARTS[part]
            0x51 -> QUALCOMM_PARTS[part]
            0x48 -> HISILICON_PARTS[part]
            0x4e -> NVIDIA_PARTS[part]
            0x53 -> SAMSUNG_PARTS[part]
            else -> null
        }
        return name ?: "0x%03x".format(part)
    }

    private val ARM_PARTS = mapOf(
        0xc05 to "Cortex-A5", 0xc07 to "Cortex-A7", 0xc08 to "Cortex-A8",
        0xc09 to "Cortex-A9", 0xc0d to "Cortex-A12", 0xc0e to "Cortex-A17",
        0xc0f to "Cortex-A15", 0xc14 to "Cortex-R4", 0xc15 to "Cortex-R5",
        0xd01 to "Cortex-A32", 0xd02 to "Cortex-A34", 0xd03 to "Cortex-A53",
        0xd04 to "Cortex-A35", 0xd05 to "Cortex-A55", 0xd06 to "Cortex-A65",
        0xd07 to "Cortex-A57", 0xd08 to "Cortex-A72", 0xd09 to "Cortex-A73",
        0xd0a to "Cortex-A75", 0xd0b to "Cortex-A76", 0xd0c to "Neoverse-N1",
        0xd0d to "Cortex-A77", 0xd0e to "Cortex-A76AE",
        0xd40 to "Neoverse-V1", 0xd41 to "Cortex-A78", 0xd42 to "Cortex-A78AE",
        0xd44 to "Cortex-X1", 0xd46 to "Cortex-A510", 0xd47 to "Cortex-A710",
        0xd48 to "Cortex-X2", 0xd49 to "Neoverse-N2", 0xd4a to "Neoverse-E1",
        0xd4b to "Cortex-A78C", 0xd4c to "Cortex-X1C", 0xd4d to "Cortex-A715",
        0xd4e to "Cortex-X3", 0xd4f to "Neoverse-V2",
        0xd80 to "Cortex-A520", 0xd81 to "Cortex-A720", 0xd82 to "Cortex-X4",
        0xd84 to "Neoverse-V3", 0xd85 to "Cortex-X925", 0xd87 to "Cortex-A725",
        0xd8e to "Neoverse-N3",
    )

    private val QUALCOMM_PARTS = mapOf(
        0x00f to "Scorpion", 0x02d to "Scorpion", 0x04d to "Krait", 0x06f to "Krait",
        0x201 to "Kryo (Gold)", 0x205 to "Kryo (Gold)", 0x211 to "Kryo (Silver)",
        0x800 to "Kryo 2xx Gold", 0x801 to "Kryo 2xx Silver",
        0x802 to "Kryo 3xx Gold", 0x803 to "Kryo 3xx Silver",
        0x804 to "Kryo 4xx Gold", 0x805 to "Kryo 4xx/5xx Silver",
        0xc00 to "Falkor", 0xc01 to "Saphira",
    )

    private val HISILICON_PARTS = mapOf(0xd01 to "TaiShan-v110", 0xd40 to "TaiShan-v120")

    private val NVIDIA_PARTS = mapOf(0x000 to "Denver", 0x003 to "Denver 2", 0x004 to "Carmel")

    private val SAMSUNG_PARTS = mapOf(
        0x001 to "Exynos-M1", 0x002 to "Exynos-M3", 0x003 to "Exynos-M4", 0x004 to "Exynos-M5",
    )

    /**
     * Platform code (`ro.board.platform` / `ro.hardware`) → commercial name.
     *
     * Android 12 exposes `Build.SOC_MODEL`; older head units may require this table.
     * Automotive chips (SA series) are listed separately for head units.
     */
    fun soc(platform: String?): String? {
        val key = platform?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        return SOC_NAMES[key]
    }

    private val SOC_NAMES = mapOf(
        // automotive
        "msm8996au" to "Snapdragon 820A (automotive)",
        "sdm660_au" to "Snapdragon 660A (automotive)",
        "sa6155" to "Snapdragon SA6155 automotive platform",
        "sa6155p" to "Snapdragon SA6155P automotive platform",
        "sa8155" to "Snapdragon SA8155 automotive platform",
        "sa8155p" to "Snapdragon 8155 automotive platform",
        "sa8195p" to "Snapdragon 8195 automotive platform",
        "sa8295p" to "Snapdragon 8295 automotive platform",
        "sa8255p" to "Snapdragon 8255 automotive platform",
        "mt2712" to "MediaTek MT2712 automotive platform",
        "mt8666" to "MediaTek MT8666 automotive platform",
        "mt8675" to "MediaTek MT8675 automotive platform",
        // Mobile SoCs commonly found in head units
        "msm8953" to "Snapdragon 625 / 626",
        "msm8937" to "Snapdragon 430",
        "msm8940" to "Snapdragon 435",
        "msm8998" to "Snapdragon 835",
        "sdm630" to "Snapdragon 630",
        "sdm636" to "Snapdragon 636",
        "sdm660" to "Snapdragon 660",
        "sdm670" to "Snapdragon 670",
        "sdm710" to "Snapdragon 710",
        "sdm845" to "Snapdragon 845",
        "sm6115" to "Snapdragon 662",
        "sm6125" to "Snapdragon 665",
        "sm6150" to "Snapdragon 675",
        "sm6225" to "Snapdragon 680",
        "sm7125" to "Snapdragon 720G",
        "sm7150" to "Snapdragon 730",
        "sm8150" to "Snapdragon 855",
        "sm8250" to "Snapdragon 865",
        "sm8350" to "Snapdragon 888",
        "sm8450" to "Snapdragon 8 Gen 1",
        "sm8550" to "Snapdragon 8 Gen 2",
        "sm8650" to "Snapdragon 8 Gen 3",
        "kirin710" to "Kirin 710",
        "kirin980" to "Kirin 980",
        "kirin990" to "Kirin 990",
        "exynos9820" to "Exynos 9820",
        "mt6765" to "MediaTek Helio P35",
        "mt6785" to "MediaTek Helio G95",
        "mt6889" to "Dimensity 1000",
        "mt6893" to "Dimensity 1200",
        "ranchu" to "Android emulator (Goldfish/Ranchu)",
        "goldfish" to "Android emulator (Goldfish)",
    )

    /** Descriptions of selected instruction-set extensions; list others unchanged. */
    fun featureNote(feature: String): String? = when (feature.lowercase()) {
        "neon", "asimd" -> "SIMD vectors"
        "aes" -> "AES acceleration"
        "sha1", "sha2", "sha512", "sha3" -> "Hash acceleration"
        "crc32" -> "CRC32 acceleration"
        "fphp", "asimdhp" -> "Half-precision floating point"
        "atomics" -> "Atomic instructions (LSE)"
        "sve", "sve2" -> "Scalable vectors"
        "dotprod", "asimddp" -> "Dot product (AI acceleration)"
        "i8mm" -> "Int8 matrix multiplication"
        "bf16", "bti", "mte" -> "ARMv8.5+ extensions"
        else -> null
    }
}
