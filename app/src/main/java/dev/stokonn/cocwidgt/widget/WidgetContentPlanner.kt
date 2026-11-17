package dev.stokonn.cocwidgt.widget

import dev.stokonn.cocwidgt.data.GameCatalogue
import dev.stokonn.cocwidgt.data.GameCategory
import dev.stokonn.cocwidgt.data.UpgradeCategory
import dev.stokonn.cocwidgt.data.VillageJob

/** Top-level sections in the widget design. */
enum class WidgetSection(val label: String) {
    BUILDERS("Builders"),
    LAB("Lab"),
    PETS("Pets"),
    GUARDIANS("Guardians"),
}

/** One reference-design section inside the widget's single vertical list. */
data class UpgradeGroup(val section: WidgetSection, val items: List<VillageJob>)

/**
 * Size-aware selection for one widget render.
 *
 * [groups] and [items] contain the rows selected for the available viewport. Excess active
 * rows are reported separately so the footer can point to the app instead of clipping.
 */
data class WidgetSelection(
    val items: List<VillageJob>,
    val groups: List<UpgradeGroup>,
    val compact: Boolean,
    val hiddenCount: Int,
    val rowHeightDp: Int,
    val showSectionHeadings: Boolean,
)

/**
 * Stable, size-aware selection for the Glance widget: active upgrades ordered by their
 * canonical data category (then by finish time), grouped into the design sections.
 * The number of rows is derived from the actual viewport, so larger widgets show more rows.
 */
object WidgetContentPlanner {
    private const val COMPACT_WIDTH_DP = 280
    private const val COMPACT_HEIGHT_DP = 220
    private const val COMPACT_ROW_DP = 26
    private const val STANDARD_ROW_DP = 36
    private const val SECTION_HEADER_DP = 18
    private const val COMPACT_HEADER_DP = 28
    private const val COMPACT_HEADER_GAP_DP = 3
    private const val COMPACT_FOOTER_DP = 20
    private const val COMPACT_FOOTER_GAP_DP = 3
    private const val COMPACT_OUTER_PADDING_DP = 8
    private const val STANDARD_HEADER_DP = 32
    private const val STANDARD_GAP_DP = 5
    private const val STANDARD_OUTER_PADDING_DP = 10
    private const val STANDARD_FOOTER_DP = 24

    /** List viewport between the player header and always-visible bottom status. */
    fun listHeight(heightDp: Int, compact: Boolean): Int {
        val padding = if (compact) COMPACT_OUTER_PADDING_DP else STANDARD_OUTER_PADDING_DP
        val fixed = if (compact) {
            COMPACT_HEADER_DP + COMPACT_HEADER_GAP_DP + COMPACT_FOOTER_GAP_DP + COMPACT_FOOTER_DP
        } else {
            STANDARD_HEADER_DP + STANDARD_GAP_DP + STANDARD_FOOTER_DP
        }
        return (heightDp - 2 * padding - fixed).coerceAtLeast(0)
    }

    fun select(jobs: List<VillageJob>, widthDp: Int, heightDp: Int, now: Long): WidgetSelection {
        val active = jobs.filter { it.remainingMs(now) > 0 }
        val ordered = orderByCategory(active)
        val compact = widthDp < COMPACT_WIDTH_DP || heightDp < COMPACT_HEIGHT_DP
        // Match CocWidget's fixed header, footer, outer padding, and gaps. LocalSize comes from
        // SizeMode.Exact so the remaining list viewport follows launcher resizing.
        val listHeight = listHeight(heightDp, compact)
        val rowHeight = if (compact) COMPACT_ROW_DP else STANDARD_ROW_DP
        var showHeadings = true
        var capacity = ordered.size
        while (capacity > 0) {
            val candidate = ordered.take(capacity)
            val requiredHeight = candidate.size * rowHeight + groupBySection(candidate).size * SECTION_HEADER_DP
            if (requiredHeight <= listHeight) break
            capacity--
        }
        if (capacity == 0 && ordered.isNotEmpty() && listHeight >= rowHeight) {
            showHeadings = false
            capacity = minOf(ordered.size, listHeight / rowHeight)
        }
        val visible = ordered.take(capacity)
        val groups = groupBySection(visible)
        val headingHeight = if (showHeadings) groups.size * SECTION_HEADER_DP else 0
        val adaptiveRowHeight = if (visible.isEmpty()) rowHeight else
            ((listHeight - headingHeight) / visible.size).coerceAtLeast(rowHeight)
        return WidgetSelection(
            items = visible,
            groups = groups,
            compact = compact,
            hiddenCount = (ordered.size - visible.size).coerceAtLeast(0),
            rowHeightDp = adaptiveRowHeight,
            showSectionHeadings = showHeadings,
        )
    }

    private fun orderByCategory(active: List<VillageJob>): List<VillageJob> {
        val orderIndex = UpgradeCategory.displayOrder.withIndex().associate { (i, cat) -> cat to i }
        return active.sortedWith(
            compareBy<VillageJob> { orderIndex.getValue(it.category) }
                .thenBy { it.finishAt() }
                .thenBy { it.dataId }
                .thenBy { it.id }
        )
    }

    private fun groupBySection(ordered: List<VillageJob>): List<UpgradeGroup> {
        val itemsBySection = ordered.groupBy(::sectionOf)
        return WidgetSection.entries.mapNotNull { section ->
            itemsBySection[section]?.let { UpgradeGroup(section, it) }
        }
    }

    private fun sectionOf(job: VillageJob): WidgetSection = when (job.category) {
        UpgradeCategory.TROOPS, UpgradeCategory.LABORATORY -> WidgetSection.LAB
        UpgradeCategory.PETS -> WidgetSection.PETS
        UpgradeCategory.GUARDIANS -> WidgetSection.GUARDIANS
        UpgradeCategory.GOBLIN -> when (GameCatalogue.resolve(job.dataId).category) {
            GameCategory.TROOP, GameCategory.SPELL, GameCategory.SIEGE -> WidgetSection.LAB
            GameCategory.PET -> WidgetSection.PETS
            GameCategory.HELPER -> if (job.dataId == LAB_ASSISTANT_ID) WidgetSection.LAB else WidgetSection.BUILDERS
            else -> WidgetSection.BUILDERS
        }
        UpgradeCategory.BUILDINGS, UpgradeCategory.HEROES, UpgradeCategory.OTHER -> WidgetSection.BUILDERS
    }

    private const val LAB_ASSISTANT_ID = 93000001L
}
