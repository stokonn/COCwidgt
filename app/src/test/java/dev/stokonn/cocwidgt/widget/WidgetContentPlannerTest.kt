package dev.stokonn.cocwidgt.widget

import dev.stokonn.cocwidgt.data.UpgradeCategory
import dev.stokonn.cocwidgt.data.VillageJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetContentPlannerTest {
    private val now = 1_000_000L

    private fun job(index: Int, dataId: Long, goblin: Boolean? = null, startedAt: Long = now, remainingMs: Long = 3_600_000L) =
        VillageJob("job:$index", "Item $index", "Builders", 2, startedAt, remainingMs, goblin, dataId = dataId)

    // One real stable ID per category: building, hero, troop, spell (= Other).
    private val categoryIds = longArrayOf(1000008L, 28000000L, 4000000L, 26000000L)

    private fun sampleJobs(count: Int): List<VillageJob> =
        (0 until count).map { i -> job(i, categoryIds[i % categoryIds.size]) }

    @Test fun columnCountAdaptsToTheRealWidgetWidth() {
        assertEquals(1, WidgetContentPlanner.columnCount(180))
        assertEquals(1, WidgetContentPlanner.columnCount(250))
        assertEquals(1, WidgetContentPlanner.columnCount(399))
        assertEquals(2, WidgetContentPlanner.columnCount(400))
        assertEquals(2, WidgetContentPlanner.columnCount(480))
        assertEquals(2, WidgetContentPlanner.columnCount(639))
        assertEquals(3, WidgetContentPlanner.columnCount(640))
        assertEquals(3, WidgetContentPlanner.columnCount(900))
    }

    @Test fun upgradesAreGroupedIntoVisuallyDistinctCategories() {
        val selection = WidgetContentPlanner.select(sampleJobs(8), 480, 600, now)
        assertEquals(0, selection.hiddenCount)
        assertEquals(8, selection.items.size)
        val activeColumns = selection.columns.filter { it.isNotEmpty() }
        assertTrue("A wide widget must use its columns", activeColumns.size >= 2)
        val seen = activeColumns.flatten()
        assertEquals(setOf(UpgradeCategory.BUILDINGS, UpgradeCategory.HEROES, UpgradeCategory.TROOPS, UpgradeCategory.OTHER),
            seen.map { it.category }.toSet())
        // Every column lists its categories in the canonical display order.
        val orderIndex = UpgradeCategory.displayOrder.withIndex().associate { (i, c) -> c to i }
        activeColumns.forEach { groups ->
            val indices = groups.map { orderIndex.getValue(it.category) }
            assertEquals(indices.sorted(), indices)
        }
        // Every item sits in the group matching its own category.
        seen.forEach { group -> group.items.forEach { assertEquals(group.category, it.category) } }
        assertEquals(8, seen.sumOf { it.items.size })
    }

    @Test fun goblinWorkerUpgradesStayInTheirCategoryAndAreVisible() {
        val jobs = listOf(
            job(0, 1000008L, goblin = true),
            job(1, 28000000L),
            job(2, 4000000L)
        )
        val selection = WidgetContentPlanner.select(jobs, 480, 600, now)
        assertEquals(0, selection.hiddenCount)
        val buildings = selection.columns.flatten().single { it.category == UpgradeCategory.BUILDINGS }
        assertEquals(true, buildings.items.single().goblin)
        assertEquals(UpgradeCategory.HEROES, selection.columns.flatten().single { it.category == UpgradeCategory.HEROES }.category)
    }

    @Test fun widgetSelectionScalesWithAvailableSpaceAndReportsHiddenRows() {
        val jobs = (0 until 30).map { index -> job(index, index.toLong()) }
        val compact = WidgetContentPlanner.select(jobs, 250, 180, now)
        val medium = WidgetContentPlanner.select(jobs, 360, 280, now)
        val wide = WidgetContentPlanner.select(jobs, 480, 360, now)
        val maximum = WidgetContentPlanner.select(jobs, 480, 600, now)

        assertTrue(compact.compact)
        assertEquals(3, compact.items.size)
        assertEquals(jobs.size - compact.items.size, compact.hiddenCount)
        // Larger sizes switch to the full layout and must never show less than the compact list's item count…
        // (except that regular rows are 48–51dp tall vs compact's 26dp, so only monotonic growth across
        // regular sizes is guaranteed; here medium is regular single-column and shows fewer, wider rows.)
        assertTrue(!medium.compact)
        assertTrue(medium.items.size >= 1)
        assertTrue(medium.items.size + medium.hiddenCount == jobs.size)
        assertTrue(wide.items.size > medium.items.size)
        assertTrue(maximum.items.size > wide.items.size)
        assertEquals(jobs.size, maximum.items.size + maximum.hiddenCount)

        val tallExactSize = WidgetContentPlanner.select(jobs, 480, 2_500, now)
        assertEquals(jobs.size, tallExactSize.items.size)
        assertEquals(0, tallExactSize.hiddenCount)
    }

    @Test fun smallestSupportedSizeStillShowsAnUpgrade() {
        val jobs = sampleJobs(10)
        val minimum = WidgetContentPlanner.select(jobs, 180, 110, now)
        assertTrue(minimum.compact)
        assertEquals(1, minimum.items.size)
        assertEquals(jobs.size - 1, minimum.hiddenCount)
    }

    @Test fun largeWidgetsNeverDropItemsSilently() {
        val jobs = sampleJobs(60)
        val maximum = WidgetContentPlanner.select(jobs, 480, 600, now)
        assertEquals(jobs.size, maximum.items.size + maximum.hiddenCount)

        val huge = WidgetContentPlanner.select(jobs, 480, 4_000, now)
        assertEquals(jobs.size, huge.items.size)
        assertEquals(0, huge.hiddenCount)
    }

    @Test fun completedUpgradesAreExcludedInsteadOfCountedAsHidden() {
        val active = job(0, 1000008L, startedAt = now - 1_800_000L, remainingMs = 3_600_000L)
        val finished = job(1, 1000009L, startedAt = now - 10_000L, remainingMs = -1L)
        val selection = WidgetContentPlanner.select(listOf(active, finished), 480, 600, now)
        assertEquals(listOf(active.id), selection.items.map { it.id })
        assertEquals(0, selection.hiddenCount)
    }

    @Test fun compactLayoutKeepsCategoryOrder() {
        val jobs = listOf(
            job(0, 26000000L),  // Other first in data order
            job(1, 1000008L),   // Buildings must still lead the compact list
            job(2, 28000000L)
        )
        val selection = WidgetContentPlanner.select(jobs, 250, 180, now)
        assertTrue(selection.compact)
        assertEquals(listOf(1000008L, 28000000L, 26000000L), selection.items.map { it.dataId })
    }
}
