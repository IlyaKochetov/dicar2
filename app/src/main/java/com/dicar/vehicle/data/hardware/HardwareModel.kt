package com.dicar.vehicle.data.hardware

/**
 * Common hardware information structure: category → block → item.
 *
 * Collectors populate this structure; [com.dicar.vehicle.ui.screen.HardwareScreen] and
 * [HardwareInspector.export] traverse it without category-specific rendering.
 */
data class HwItem(val label: String, val value: String) {
    val unknown: Boolean get() = value == UNKNOWN

    companion object {
        /** Placeholder for unavailable hardware readings, distinct from vehicle data's `N/A`. */
        const val UNKNOWN = "Unknown"
    }
}

/** Card block; null [subtitle] omits the subheading. */
data class HwBlock(val subtitle: String?, val items: List<HwItem>)

data class HwSection(val category: HwCategory, val blocks: List<HwBlock>) {
    val itemCount: Int get() = blocks.sumOf { it.items.size }
}

/** Categories in the filter bar, ordered as cards appear by default. */
enum class HwCategory(val label: String, val glyph: String) {
    SOC("Processor", "▤"),
    GPU("Graphics", "◈"),
    MEMORY("Memory", "▥"),
    STORAGE("Storage", "▦"),
    DISPLAY("Display", "▭"),
    BATTERY("Power", "▮"),
    THERMAL("Thermal", "▲"),
    SENSOR("Sensors", "◉"),
    CAMERA("Cameras", "◎"),
    NETWORK("Network", "◇"),
    AUDIO("Audio", "♪"),
    CODEC("Codecs", "▷"),
    SYSTEM("System", "⬡"),
    FEATURE("Features", "✦"),
    PROPS("Properties", "≡"),
}

// ---------------------------------------------------------------------------
// Small collector DSL
// ---------------------------------------------------------------------------

@DslMarker
private annotation class HwDsl

@HwDsl
class BlockScope internal constructor() {
    private val items = mutableListOf<HwItem>()

    /** Show unknown for null or blank values instead of hiding the row. */
    fun item(label: String, value: String?) {
        items += HwItem(label, value?.takeIf { it.isNotBlank() } ?: HwItem.UNKNOWN)
    }

    /** Omit unavailable rows for items meaningful only when present, such as a particular sysfs node. */
    fun itemIfPresent(label: String, value: String?) {
        if (!value.isNullOrBlank()) items += HwItem(label, value)
    }

    /** Capability Boolean indicating whether the device supports something. */
    fun item(label: String, value: Boolean?) = item(label, value?.let { if (it) "Supported" else "Unsupported" })

    /**
     * State Boolean, separate from capability flags so presence and detection labels read naturally.
     */
    fun flag(label: String, value: Boolean?, yes: String = "Yes", no: String = "No") =
        item(label, value?.let { if (it) yes else no })

    internal fun build(subtitle: String?) = HwBlock(subtitle, items.toList())

    internal fun isEmpty() = items.isEmpty()
}

@HwDsl
class SectionScope internal constructor() {
    private val blocks = mutableListOf<HwBlock>()

    fun block(subtitle: String? = null, body: BlockScope.() -> Unit) {
        val scope = BlockScope().apply(body)
        if (!scope.isEmpty()) blocks += scope.build(subtitle)
    }

    internal fun build(category: HwCategory) = HwSection(category, blocks.toList())
}

/** Collector entry point: `section(HwCategory.SOC) { block("Cores") { item("Model", …) } }`. */
fun section(category: HwCategory, body: SectionScope.() -> Unit): HwSection =
    SectionScope().apply(body).build(category)

/**
 * Filter by label, value, subtitle or category; a subtitle match retains the whole block.
 * An empty query returns the input, and categories with no matches are omitted.
 */
fun filterSections(sections: List<HwSection>, query: String): List<HwSection> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return sections
    return sections.mapNotNull { s ->
        if (s.category.label.lowercase().contains(q)) return@mapNotNull s
        val blocks = s.blocks.mapNotNull { b ->
            if (b.subtitle?.lowercase()?.contains(q) == true) return@mapNotNull b
            b.items
                .filter { it.label.lowercase().contains(q) || it.value.lowercase().contains(q) }
                .takeIf { it.isNotEmpty() }
                ?.let { HwBlock(b.subtitle, it) }
        }
        if (blocks.isEmpty()) null else HwSection(s.category, blocks)
    }
}
