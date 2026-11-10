package dev.stokonn.cocwidgt.widget

import dev.stokonn.cocwidgt.data.UpgradeCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Geometry and text fitting of the Glance widget. The critical regression: the progress bar
 * must fit inside its visible column content (column minus trailing gap minus card padding),
 * otherwise it paints over the next column — the overlap reported on the device.
 */
class WidgetFitTest {

    @Test
    fun barGeometryAlwaysFitsTheVisibleColumnContent() {
        val widths = listOf(96, 120, 142, 175, 198, 264, 300)
        for (columnWidth in widths) {
            for (endGap in listOf(0, WidgetContentPlanner.COLUMN_GAP_DP)) {
                for (cardPadding in listOf(8, 10)) {
                    val inner = WidgetFit.cardInnerWidth(columnWidth, endGap, cardPadding)
                    // Regression invariant: inner + gap + padding == full column width, so the
                    // bar plus the gap plus both paddings can never exceed the column.
                    assertEquals(columnWidth, inner + endGap + cardPadding * 2)
                    assertTrue("inner must not be negative", inner >= 0)
                }
            }
        }
    }

    @Test
    fun nonLastColumnBarIsOneGapNarrowerThanTheOldFormula() {
        // The old formula (columnWidth - 2*padding) ignored the trailing gap that lives
        // inside the column width, so every bar in a non-last column overflowed by exactly
        // the gap into the neighbouring column — the reported overlap.
        val columnWidth = 175
        val gap = WidgetContentPlanner.COLUMN_GAP_DP
        val padding = 8
        val fixed = WidgetFit.cardInnerWidth(columnWidth, gap, padding)
        assertEquals(columnWidth - gap - padding * 2, fixed)
        assertTrue(fixed < columnWidth - padding * 2)
    }

    @Test
    fun ellipsizeTrimsLongNamesToTheBudget() {
        val longName = "X-Bow · 14"
        val trimmed = WidgetFit.ellipsize(longName, 5)
        assertEquals(5, trimmed.length)
        assertTrue(trimmed.endsWith("…"))
        // Short text is never touched — a valid name must not be cut for no reason.
        assertEquals("Archer", WidgetFit.ellipsize("Archer", 10))
        // Degenerate budgets stay safe.
        assertEquals("", WidgetFit.ellipsize("Anything", 0))
        assertEquals("…", WidgetFit.ellipsize("Anything", 1))
    }

    @Test
    fun maxCharsScalesWithWidthAndShrinksWithFontSize() {
        assertTrue(WidgetFit.maxChars(200, 12) > WidgetFit.maxChars(100, 12))
        assertTrue(WidgetFit.maxChars(100, 12) > WidgetFit.maxChars(100, 16))
        assertTrue(WidgetFit.maxChars(40, 12) >= 4)
        assertEquals(0, WidgetFit.maxChars(0, 12))
        assertEquals(0, WidgetFit.maxChars(100, 0))
        // An ellipsized long name always ends up no wider than the budget it was given.
        val budget = WidgetFit.maxChars(100, 12)
        assertTrue(WidgetFit.ellipsize("A".repeat(500), budget).length <= budget)
    }

    @Test
    fun categoryLabelsShortenOnlyInNarrowColumns() {
        assertEquals("Buildings & Traps", WidgetFit.categoryLabel(UpgradeCategory.BUILDINGS, 200))
        assertEquals("Buildings & Traps", WidgetFit.categoryLabel(UpgradeCategory.BUILDINGS, 170))
        assertEquals("Build", WidgetFit.categoryLabel(UpgradeCategory.BUILDINGS, 169))
        assertEquals("Build", WidgetFit.categoryLabel(UpgradeCategory.BUILDINGS, 120))
        assertEquals("Heroes", WidgetFit.categoryLabel(UpgradeCategory.HEROES, 100))
        assertEquals("Goblin", WidgetFit.categoryLabel(UpgradeCategory.GOBLIN, 150))
    }

    @Test
    fun columnWidthMatchesThePlannerBudgetAndNeverOverflowsTheContent() {
        // Single column uses the full content width.
        assertEquals(254, WidgetFit.columnWidth(254, 1))
        // Multi-column distribution is the planner's arithmetic: gaps come out first.
        val two = WidgetFit.columnWidth(374, 2)
        assertEquals((374 - WidgetContentPlanner.COLUMN_GAP_DP) / 2, two)
        assertTrue(two * 2 + WidgetContentPlanner.COLUMN_GAP_DP <= 374)
        val three = WidgetFit.columnWidth(614, 3)
        assertEquals((614 - WidgetContentPlanner.COLUMN_GAP_DP * 2) / 3, three)
        assertTrue(three * 3 + WidgetContentPlanner.COLUMN_GAP_DP * 2 <= 614)
    }
}
