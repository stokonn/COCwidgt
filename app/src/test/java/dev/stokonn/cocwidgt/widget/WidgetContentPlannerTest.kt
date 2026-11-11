package dev.stokonn.cocwidgt.widget

import dev.stokonn.cocwidgt.data.UpgradeCategory
import dev.stokonn.cocwidgt.data.VillageJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The widget renders one ordered list and selects a size-dependent number of rows from the
 * actual viewport. All remaining active rows are counted for the "more in app" status.
 */
class WidgetContentPlannerTest {
    private val now = 1_000_000L

    private fun job(
        index: Int,
        dataId: Long,
        section: String = "Builders",
        goblin: Boolean? = null,
        startedAt: Long = now,
        remainingMs: Long = 3_600_000L,
    ) = VillageJob("job:$index", "Item $index", section, 2, startedAt, remainingMs, goblin, dataId = dataId)

    // One real stable ID per category: building, hero, troop, pet, laboratory (spell),
    // helper (Goblin), unknown (Other).
    private val categoryIds = longArrayOf(1000008L, 28000000L, 4000000L, 73000000L, 26000000L, 93000000L, 90000000L)

    private fun sampleJobs(count: Int): List<VillageJob> =
        (0 until count).map { i -> job(i, categoryIds[i % categoryIds.size]) }

    @Test fun exactViewportControlsVisibleRowsAndPreservesGroupOrder() {
        // Compact, medium, wide and maximum sizes on both resize dimensions.
        val sizes = listOf(
            180 to 110, 250 to 180, 280 to 220, 360 to 280, 360 to 900,
            480 to 360, 480 to 600, 640 to 400, 900 to 1_200, 480 to 2_500,
        )
        for ((width, height) in sizes) {
            val selection = WidgetContentPlanner.select(sampleJobs(8), width, height, now)
            assertTrue("width=$width height=$height has a visible active row", selection.items.isNotEmpty())
            assertEquals("width=$width height=$height accounts for active rows",
                8, selection.items.size + selection.hiddenCount)
            assertEquals("width=$width height=$height groups follow the design order",
                WidgetSection.entries.filter { section -> selection.groups.any { it.section == section } },
                selection.groups.map { it.section })
            selection.groups.forEach { group ->
                assertEquals(group.items.size, group.items.distinctBy { it.id }.size)
            }
            assertEquals(selection.items.toSet(), selection.groups.flatMap { it.items }.toSet())
        }
    }

    @Test fun maximumViewportUsesAvailableHeightAndTracksRemainingRows() {
        val selection = WidgetContentPlanner.select(sampleJobs(30), 900, 1_200, now)
        assertTrue(selection.items.size > WidgetContentPlanner.select(sampleJobs(30), 480, 360, now).items.size)
        assertEquals(WidgetSection.entries.toSet(), selection.groups.map { it.section }.toSet())
        assertEquals(selection.items.size, selection.groups.sumOf { it.items.size })
        assertEquals(30, selection.items.size + selection.hiddenCount)
    }

    @Test fun compactMediumLargeAndMaximumSizesShowIncreasingRows() {
        val jobs = sampleJobs(60)
        val minimum = WidgetContentPlanner.select(jobs, 180, 110, now)
        val medium = WidgetContentPlanner.select(jobs, 360, 344, now)
        val large = WidgetContentPlanner.select(jobs, 480, 600, now)
        val maximum = WidgetContentPlanner.select(jobs, 720, 900, now)
        assertEquals(1, minimum.items.size)
        assertTrue(medium.items.size > minimum.items.size)
        assertTrue(large.items.size > medium.items.size)
        assertTrue(maximum.items.size > large.items.size)
        listOf(minimum, medium, large, maximum).forEach {
            assertEquals(jobs.size, it.items.size + it.hiddenCount)
        }
    }

    @Test fun invalidOrTinyReportedSizesProduceAnEmptySafeSelection() {
        for ((width, height) in listOf(0 to 0, 1 to 1, 100 to 50)) {
            val selection = WidgetContentPlanner.select(sampleJobs(5), width, height, now)
            assertTrue(selection.items.isEmpty())
            assertEquals(5, selection.hiddenCount)
            assertTrue(selection.groups.isEmpty())
        }
    }

    @Test fun compactOnlySwitchesTypographyNeverContent() {
        val jobs = sampleJobs(12)
        val compact = WidgetContentPlanner.select(jobs, 250, 180, now)
        val regular = WidgetContentPlanner.select(jobs, 480, 600, now)
        assertTrue(compact.compact)
        assertFalse(regular.compact)
        assertTrue(compact.items.size < regular.items.size)
        // Both dimensions select compact row geometry.
        assertTrue(WidgetContentPlanner.select(jobs, 480, 219, now).compact)
        assertTrue(WidgetContentPlanner.select(jobs, 279, 600, now).compact)
    }

    @Test fun referenceSectionsGroupAllCanonicalCategoriesWithoutDroppingRows() {
        val selection = WidgetContentPlanner.select(sampleJobs(7), 480, 600, now)
        assertEquals(listOf(WidgetSection.BUILDERS, WidgetSection.LAB_PETS), selection.groups.map { it.section })
        assertEquals(7, selection.items.size + selection.hiddenCount)
        assertTrue(selection.groups.first().items.any { it.category == UpgradeCategory.BUILDINGS })
        assertTrue(selection.groups.first().items.any { it.category == UpgradeCategory.HEROES })
        assertTrue(selection.groups.first().items.any { it.category == UpgradeCategory.GOBLIN })
        assertTrue(selection.groups.first().items.any { it.category == UpgradeCategory.OTHER })
        assertTrue(selection.groups.last().items.any { it.category == UpgradeCategory.TROOPS })
        assertTrue(selection.groups.last().items.any { it.category == UpgradeCategory.PETS })
        assertTrue(selection.groups.last().items.any { it.category == UpgradeCategory.LABORATORY })
    }

    @Test fun goblinRowsStayInTheirDesignSectionAndRetainGoblinFlags() {
        val jobs = listOf(
            job(0, 1000008L, goblin = true),
            job(1, 28000000L),
            job(2, 93000000L),
        )
        val selection = WidgetContentPlanner.select(jobs, 480, 600, now)
        val builders = selection.groups.single { it.section == WidgetSection.BUILDERS }
        assertTrue(builders.items.any { it.dataId == 1000008L && it.goblin == true })
        assertTrue(builders.items.any { it.dataId == 93000000L })
        assertTrue(builders.items.any { it.dataId == 28000000L })
        assertEquals(listOf(WidgetSection.BUILDERS), selection.groups.map { it.section })
    }

    @Test fun labAndPetResearchShareTheSecondDesignSection() {
        val jobs = listOf(
            job(0, 4000000L, section = "Laboratory"),
            job(1, 26000002L, section = "Laboratory"),
            job(2, 4000031L, section = "Star Laboratory"),
        )
        val selection = WidgetContentPlanner.select(jobs, 480, 600, now)
        val labAndPets = selection.groups.single { it.section == WidgetSection.LAB_PETS }
        assertEquals(listOf(4000031L, 4000000L, 26000002L), labAndPets.items.map { it.dataId })
    }

    @Test fun completedUpgradesAreExcludedInsteadOfRendered() {
        val active = job(0, 1000008L, startedAt = now - 1_800_000L, remainingMs = 3_600_000L)
        val finished = job(1, 1000009L, startedAt = now - 10_000L, remainingMs = -1L)
        val selection = WidgetContentPlanner.select(listOf(active, finished), 480, 600, now)
        assertEquals(listOf(active.id), selection.items.map { it.id })
        assertEquals(listOf(active), selection.groups.single().items)
    }

    @Test fun compactLayoutKeepsCategoryOrder() {
        val jobs = listOf(
            job(0, 90000000L),  // Unknown entity (Other) first in data order
            job(1, 1000008L),   // Buildings must still lead the list
            job(2, 28000000L),
        )
        val selection = WidgetContentPlanner.select(jobs, 250, 180, now)
        assertTrue(selection.compact)
        assertEquals(listOf(1000008L, 28000000L, 90000000L), selection.items.map { it.dataId })
        assertEquals(listOf(WidgetSection.BUILDERS), selection.groups.map { it.section })
        assertEquals(listOf(1000008L, 28000000L, 90000000L), selection.groups.single().items.map { it.dataId })
    }
}
