package dev.stokonn.cocwidgt.widget

import dev.stokonn.cocwidgt.data.GameCatalogue
import dev.stokonn.cocwidgt.data.GameCategory
import dev.stokonn.cocwidgt.data.UpgradeCategory
import dev.stokonn.cocwidgt.data.VillageJob

/** Reference design's two top-level sections. */
enum class WidgetSection(val label: String) {
    BUILDERS("Builders"),
    LAB_PETS("Lab & Pets"),
}

/** One reference-design section inside the widget's single vertical list. */
data class UpgradeGroup(val section: WidgetSection, val items: List<VillageJob>)

/**
 * Size-aware selection for one widget render.
 *
 * [groups] is the single ordered list of design sections the widget scrolls through —
 * there is exactly one column at every size, nothing is ever hidden to fit the height
 * (the Glance `LazyColumn` scrolls instead), and [items] holds every active upgrade in
 * display order. [compact] only switches typography/padding for very small widgets.
 */
data class WidgetSelection(
    val items: List<VillageJob>,
    val groups: List<UpgradeGroup>,
    val compact: Boolean,
    val hiddenCount: Int,
)

/**
 * Stable, size-aware selection for the Glance widget: active upgrades ordered by their
 * canonical data category (then by finish time), grouped into the two reference sections.
 * The number of rows is derived from the actual viewport, so larger widgets show more rows.
 */
object WidgetContentPlanner {
    private const val COMPACT_WIDTH_DP = 280
    private const val COMPACT_HEIGHT_DP = 220
    private const val COMPACT_ROW_DP = 26
    private const val STANDARD_ROW_DP = 36
    private const val SECTION_HEADER_DP = 18

    fun select(jobs: List<VillageJob>, widthDp: Int, heightDp: Int, now: Long): WidgetSelection {
        val active = jobs.filter { it.remainingMs(now) > 0 }
        val ordered = orderByCategory(active)
        val compact = widthDp < COMPACT_WIDTH_DP || heightDp < COMPACT_HEIGHT_DP
        // Match CocWidget's measured fixed regions: outer padding, player header, status line,
        // gaps and the two section headings. LocalSize comes from SizeMode.Exact.
        val padding = if (compact) 8 else 13
        val fixed = if (compact) 32 + 4 + 14 else 50 + 7 + 24
        val listHeight = (heightDp - 2 * padding - fixed).coerceAtLeast(0)
        val rowHeight = if (compact) COMPACT_ROW_DP else STANDARD_ROW_DP
        var capacity = ordered.size
        while (capacity > 0) {
            val candidate = ordered.take(capacity)
            val requiredHeight = candidate.size * rowHeight + groupBySection(candidate).size * SECTION_HEADER_DP
            if (requiredHeight <= listHeight) break
            capacity--
        }
        val visible = ordered.take(capacity)
        return WidgetSelection(
            items = visible,
            groups = groupBySection(visible),
            compact = compact,
            hiddenCount = (ordered.size - visible.size).coerceAtLeast(0),
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
        UpgradeCategory.TROOPS, UpgradeCategory.PETS, UpgradeCategory.LABORATORY -> WidgetSection.LAB_PETS
        UpgradeCategory.GOBLIN -> when (GameCatalogue.resolve(job.dataId).category) {
            GameCategory.TROOP, GameCategory.SPELL, GameCategory.PET, GameCategory.SIEGE -> WidgetSection.LAB_PETS
            GameCategory.HELPER -> if (job.dataId == LAB_ASSISTANT_ID) WidgetSection.LAB_PETS else WidgetSection.BUILDERS
            else -> WidgetSection.BUILDERS
        }
        UpgradeCategory.BUILDINGS, UpgradeCategory.HEROES, UpgradeCategory.OTHER -> WidgetSection.BUILDERS
    }

    private const val LAB_ASSISTANT_ID = 93000001L
}
