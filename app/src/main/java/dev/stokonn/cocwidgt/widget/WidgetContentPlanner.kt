package dev.stokonn.cocwidgt.widget

import dev.stokonn.cocwidgt.data.UpgradeCategory
import dev.stokonn.cocwidgt.data.VillageJob

/** One category block of upgrades as rendered inside a column. */
data class UpgradeGroup(val category: UpgradeCategory, val items: List<VillageJob>)

/**
 * Size-aware layout decision for the non-scrollable launcher widget.
 *
 * [columns] holds one list of category blocks per rendered column (empty for compact widgets).
 * [items] is every visible upgrade in category order, [hiddenCount] the ones that do not fit.
 */
data class WidgetSelection(
    val items: List<VillageJob>,
    val hiddenCount: Int,
    val compact: Boolean,
    val columns: List<List<UpgradeGroup>> = emptyList(),
    val columnCount: Int = 1
)

/**
 * Stable, size-aware selection for Glance widgets: one visual category per column block,
 * packed into as many columns as the real widget width allows, showing exactly as many rows
 * as the real height fits. Categories are placed as whole blocks whenever they fit (they are
 * only split across columns when a single column cannot hold them), every column lists its
 * blocks in the canonical display order, and there is no arbitrary item limit — overflow is
 * reported through [WidgetSelection.hiddenCount].
 */
object WidgetContentPlanner {
    private const val COMPACT_WIDTH_DP = 280
    private const val COMPACT_HEIGHT_DP = 220
    private const val COMPACT_FIXED_HEIGHT_DP = 84
    private const val COMPACT_ROW_HEIGHT_DP = 26

    private const val TWO_COLUMN_MIN_WIDTH_DP = 400
    private const val THREE_COLUMN_MIN_WIDTH_DP = 640

    // Measured heights of the reference-design rows: green progress row (34) + its 2dp margin.
    private const val SINGLE_ROW_HEIGHT_DP = 36
    private const val MULTI_ROW_HEIGHT_DP = 36
    private const val GROUP_HEADER_HEIGHT_DP = 19
    const val COLUMN_GAP_DP = 10

    // Content padding (13 * 2), player header incl. padding, header spacing and the bottom
    // status row (chips + last-sync line). The API/export status lives inside that row.
    private const val RESERVED_HEIGHT_DP = 26 + 52 + 6 + 24
    private const val OVERFLOW_LINE_HEIGHT_DP = 16

    fun columnCount(widthDp: Int): Int = when {
        widthDp < TWO_COLUMN_MIN_WIDTH_DP -> 1
        widthDp < THREE_COLUMN_MIN_WIDTH_DP -> 2
        else -> 3
    }

    fun select(jobs: List<VillageJob>, widthDp: Int, heightDp: Int, now: Long): WidgetSelection {
        val active = jobs.filter { it.remainingMs(now) > 0 }
        val ordered = orderByCategory(active, now)

        if (widthDp < COMPACT_WIDTH_DP || heightDp < COMPACT_HEIGHT_DP) {
            val capacity = (heightDp - COMPACT_FIXED_HEIGHT_DP).coerceAtLeast(0) / COMPACT_ROW_HEIGHT_DP
            val selected = ordered.take(capacity)
            return WidgetSelection(selected, ordered.size - selected.size, compact = true, columns = emptyList(), columnCount = 1)
        }

        val columnCount = columnCount(widthDp)
        val rowHeight = if (columnCount == 1) SINGLE_ROW_HEIGHT_DP else MULTI_ROW_HEIGHT_DP
        val baseAvailable = heightDp - RESERVED_HEIGHT_DP
        val groups = groupByCategory(ordered)
        if (groups.isEmpty()) {
            return WidgetSelection(emptyList(), 0, compact = false, columns = List(columnCount) { emptyList() }, columnCount = columnCount)
        }

        // The "+N more" line only consumes height when something is actually cut off, so pack
        // once without it and retry with the extra reservation when overflow appears.
        var selection = pack(groups, columnCount, rowHeight, baseAvailable)
        if (selection.hiddenCount > 0) {
            selection = pack(groups, columnCount, rowHeight, baseAvailable - OVERFLOW_LINE_HEIGHT_DP)
        }
        return selection
    }

    private fun orderByCategory(active: List<VillageJob>, now: Long): List<VillageJob> {
        val orderIndex = UpgradeCategory.displayOrder.withIndex().associate { (i, cat) -> cat to i }
        return active.sortedWith(
            compareBy<VillageJob> { orderIndex.getValue(it.category) }
                .thenBy { it.finishAt() }
                .thenBy { it.dataId }
                .thenBy { it.id }
        )
    }

    private fun groupByCategory(ordered: List<VillageJob>): List<UpgradeGroup> =
        UpgradeCategory.displayOrder.mapNotNull { category ->
            ordered.filter { it.category == category }.takeIf { it.isNotEmpty() }?.let { UpgradeGroup(category, it) }
        }

    private fun pack(
        groups: List<UpgradeGroup>,
        columnCount: Int,
        rowHeight: Int,
        availableHeight: Int
    ): WidgetSelection {
        val columns = List(columnCount) { mutableListOf<Pair<UpgradeCategory, MutableList<VillageJob>>>() }
        val heights = IntArray(columnCount)
        var hidden = 0

        fun blockHeight(itemCount: Int): Int = GROUP_HEADER_HEIGHT_DP + itemCount * rowHeight

        // Pass 1: each category block goes whole into the least-loaded column that fits it,
        // so a category is visually distinct (one block) whenever the height allows it.
        val pending = mutableListOf<UpgradeGroup>()
        for (group in groups) {
            val cost = blockHeight(group.items.size)
            val target = (0 until columnCount)
                .filter { heights[it] + cost <= availableHeight }
                .minByOrNull { heights[it] }
            if (target == null) {
                pending += group
            } else {
                columns[target].add(group.category to group.items.toMutableList())
                heights[target] += cost
            }
        }

        // Pass 2: a block taller than the remaining space fills the columns left to right —
        // no later column keeps room while an earlier one is cut short. A split category
        // repeats its header in each column it continues in; anything still not fitting is
        // reported as hidden instead of being dropped silently.
        for (group in pending) {
            var index = 0
            var column = 0
            while (index < group.items.size && column < columnCount) {
                val capacity = ((availableHeight - heights[column] - GROUP_HEADER_HEIGHT_DP) / rowHeight).coerceAtLeast(0)
                if (capacity > 0) {
                    val take = minOf(capacity, group.items.size - index)
                    columns[column].add(group.category to group.items.subList(index, index + take).toMutableList())
                    heights[column] += GROUP_HEADER_HEIGHT_DP + take * rowHeight
                    index += take
                }
                column++
            }
            hidden += group.items.size - index
        }

        val orderIndex = UpgradeCategory.displayOrder.withIndex().associate { (i, cat) -> cat to i }
        val renderedColumns = columns.map { slices ->
            slices.filter { it.second.isNotEmpty() }
                .sortedBy { orderIndex.getValue(it.first) }
                .map { UpgradeGroup(it.first, it.second.toList()) }
        }
        val visible = renderedColumns.flatten().flatMap { it.items }
            .sortedBy { orderIndex.getValue(it.category) }
        return WidgetSelection(
            items = visible,
            hiddenCount = hidden,
            compact = false,
            columns = renderedColumns,
            columnCount = columnCount
        )
    }
}
