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
 * Stable, size-aware selection for Glance widgets: groups upgrades into visual categories,
 * distributes them over as many columns as the real widget width allows, and shows exactly as
 * many rows as the real height fits. There is no arbitrary item limit — overflow is reported
 * through [WidgetSelection.hiddenCount].
 */
object WidgetContentPlanner {
    private const val COMPACT_WIDTH_DP = 280
    private const val COMPACT_HEIGHT_DP = 220
    private const val COMPACT_FIXED_HEIGHT_DP = 84
    private const val COMPACT_ROW_HEIGHT_DP = 26

    private const val TWO_COLUMN_MIN_WIDTH_DP = 400
    private const val THREE_COLUMN_MIN_WIDTH_DP = 640

    private const val SINGLE_ROW_HEIGHT_DP = 51
    private const val MULTI_ROW_HEIGHT_DP = 48
    private const val GROUP_HEADER_HEIGHT_DP = 17
    const val COLUMN_GAP_DP = 10

    // Player header (incl. padding), header spacing and the export age line.
    private const val RESERVED_HEIGHT_DP = 26 + 58 + 7 + 16
    private const val API_STATUS_HEIGHT_DP = 16
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
        val apiReserved = if (widthDp >= 420 && heightDp >= 360) API_STATUS_HEIGHT_DP else 0
        val baseAvailable = heightDp - RESERVED_HEIGHT_DP - apiReserved
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

        val estimatedTotal = groups.sumOf { GROUP_HEADER_HEIGHT_DP + it.items.size * rowHeight }
        val fitsHorizontally = estimatedTotal <= availableHeight

        if (fitsHorizontally) {
            // Everything fits vertically: spread items over the columns so no column is left
            // empty and the available width is actually used.
            for (group in groups) {
                for (item in group.items) {
                    val target = (0 until columnCount).minByOrNull { candidate ->
                        val slices = columns[candidate]
                        val header = if (slices.isNotEmpty() && slices.last().first == group.category) 0 else GROUP_HEADER_HEIGHT_DP
                        heights[candidate] + header
                    } ?: break
                    val slices = columns[target]
                    if (slices.isEmpty() || slices.last().first != group.category) {
                        slices.add(group.category to mutableListOf())
                        heights[target] += GROUP_HEADER_HEIGHT_DP
                    }
                    val projected = heights[target] + rowHeight
                    if (projected > availableHeight) { hidden += 1; continue }
                    slices.last().second.add(item)
                    heights[target] = projected
                }
            }
        } else {
            // Content exceeds one column: fill columns in order so no later column has room
            // while an earlier one is cut short. A category split across columns repeats its header.
            var column = 0
            for (group in groups) {
                var index = 0
                while (index < group.items.size && column < columnCount) {
                    val slices = columns[column]
                    val continued = slices.isNotEmpty() && slices.last().first == group.category
                    val header = if (continued) 0 else GROUP_HEADER_HEIGHT_DP
                    if (heights[column] + header + rowHeight > availableHeight) {
                        column++
                        continue
                    }
                    if (!continued) slices.add(group.category to mutableListOf())
                    slices.last().second.add(group.items[index])
                    heights[column] += header + rowHeight
                    index++
                }
                if (index < group.items.size) hidden += group.items.size - index
            }
        }

        val renderedColumns = columns.map { slices ->
            slices.filter { it.second.isNotEmpty() }.map { UpgradeGroup(it.first, it.second.toList()) }
        }
        val orderIndex = UpgradeCategory.displayOrder.withIndex().associate { (i, cat) -> cat to i }
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
