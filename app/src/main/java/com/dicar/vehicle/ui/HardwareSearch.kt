package com.dicar.vehicle.ui

import android.content.Context
import com.dicar.vehicle.data.hardware.HwBlock
import com.dicar.vehicle.data.hardware.HwSection

/** Search both the displayed translation and the original collector text. */
fun filterLocalizedSections(sections: List<HwSection>, query: String, context: Context): List<HwSection> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return sections
    fun matches(value: String) = value.lowercase().contains(q) || context.hardwareText(value).lowercase().contains(q)
    return sections.mapNotNull { section ->
        if (matches(section.category.label)) return@mapNotNull section
        val blocks = section.blocks.mapNotNull { block ->
            if (block.subtitle?.let(::matches) == true) return@mapNotNull block
            val items = block.items.filter { matches(it.label) || matches(it.value) }
            if (items.isEmpty()) null else HwBlock(block.subtitle, items)
        }
        if (blocks.isEmpty()) null else HwSection(section.category, blocks)
    }
}
