package dev.stokonn.cocwidgt.widget

import dev.stokonn.cocwidgt.data.UpgradeCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Text fitting of the Glance widget. Every single-line text is budgeted from the measured
 * widget width, so long names, category headings and status labels can never clip the frame
 * or overlap their neighbours in the single scrolling column.
 */
class WidgetFitTest {

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
    fun categoryLabelsShortenOnlyInVeryNarrowWidgets() {
        assertEquals("Buildings", WidgetFit.categoryLabel(UpgradeCategory.BUILDINGS, 200))
        assertEquals("Buildings", WidgetFit.categoryLabel(UpgradeCategory.BUILDINGS, 170))
        assertEquals("Build", WidgetFit.categoryLabel(UpgradeCategory.BUILDINGS, 169))
        assertEquals("Build", WidgetFit.categoryLabel(UpgradeCategory.BUILDINGS, 120))
        assertEquals("Heroes", WidgetFit.categoryLabel(UpgradeCategory.HEROES, 100))
        assertEquals("Goblin", WidgetFit.categoryLabel(UpgradeCategory.GOBLIN, 150))
        assertEquals("Laboratory", WidgetFit.categoryLabel(UpgradeCategory.LABORATORY, 240))
        assertEquals("Lab", WidgetFit.categoryLabel(UpgradeCategory.LABORATORY, 140))
    }

    @Test
    fun everyCategoryLabelFitsAComfortableWidgetLine() {
        // Even the full labels must fit a modest 260dp content width at heading size.
        UpgradeCategory.entries.forEach { category ->
            val budget = WidgetFit.maxChars(260, 11)
            assertEquals(category.label, WidgetFit.ellipsize(category.label, budget))
        }
    }
}
