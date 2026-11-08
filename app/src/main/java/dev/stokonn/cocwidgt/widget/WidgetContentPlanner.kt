package dev.stokonn.cocwidgt.widget

import dev.stokonn.cocwidgt.data.VillageJob

data class WidgetSelection(val items: List<VillageJob>, val hiddenCount: Int, val compact: Boolean)

/** Stable, size-aware selection for non-scrollable launcher widgets. */
object WidgetContentPlanner {
    private val sectionOrder = listOf("Builders", "Laboratory", "Equipment", "Pets", "Builder Base", "Star Laboratory", "Unknown item")

    fun select(jobs: List<VillageJob>, widthDp: Int, heightDp: Int, now: Long): WidgetSelection {
        val active = jobs.filter { it.remainingMs(now) > 0 }
        val compact = widthDp < 250 || heightDp < 150
        val ordered = active.sortedWith(compareBy<VillageJob> { sectionOrder.indexOf(it.section).let { i -> if (i < 0) sectionOrder.size else i } }
            .thenBy { it.remainingMs(now) }.thenBy { it.dataId }.thenBy { it.id })
        val capacity = if (compact) 1 else ((heightDp - 115).coerceAtLeast(0) / if (widthDp >= 420) 48 else 60).coerceAtLeast(1)
        val selected = ordered.take(capacity)
        return WidgetSelection(selected, (ordered.size - selected.size).coerceAtLeast(0), compact)
    }
}
