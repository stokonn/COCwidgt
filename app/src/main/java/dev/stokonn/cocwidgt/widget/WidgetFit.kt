package dev.stokonn.cocwidgt.widget

import dev.stokonn.cocwidgt.data.UpgradeCategory

/**
 * Pure fitting of text and bar geometry to the real widget dimensions (Glance reports the
 * actual [androidx.glance.unit.DpSize] of the widget through LocalSize).
 *
 * Glance has no text ellipsis support and RemoteViews children can draw past an unmeasured
 * parent, so every single-line text and every fixed-width bar is constrained here from the
 * same arithmetic the columns use. That keeps long names, category headings and progress bars
 * inside their column instead of overlapping the neighbouring one. All of it is unit-tested.
 */
object WidgetFit {
    /**
     * Average glyph width of the platform default typeface as a fraction of the font size.
     * Deliberately slightly conservative: it rather ellipsizes early than lets text overflow.
     */
    private const val CHAR_WIDTH_FACTOR = 0.56

    /** Category headings switch to their short form below this column width. */
    private const val NARROW_COLUMN_DP = 170

    private const val ELLIPSIS = "…"

    /** How many characters of [fontSp] text fit into [widthDp]; 0 when nothing fits. */
    fun maxChars(widthDp: Int, fontSp: Int): Int {
        val charWidth = fontSp * CHAR_WIDTH_FACTOR
        if (widthDp <= 0 || charWidth <= 0) return 0
        return maxOf(1, (widthDp / charWidth).toInt())
    }

    /** Trims [text] to [maxChars] characters with a trailing ellipsis; never longer than asked. */
    fun ellipsize(text: String, maxChars: Int): String = when {
        maxChars <= 0 -> ""
        text.length <= maxChars -> text
        maxChars == 1 -> ELLIPSIS
        else -> text.take(maxChars - 1).trimEnd() + ELLIPSIS
    }

    /**
     * Width of one category column: the content width minus the gaps between columns,
     * divided evenly — identical arithmetic to the planner's column budget.
     */
    fun columnWidth(contentWidth: Int, columnCount: Int): Int = when {
        columnCount <= 1 -> contentWidth
        else -> (contentWidth - WidgetContentPlanner.COLUMN_GAP_DP * (columnCount - 1)) / columnCount
    }

    /**
     * Inner width of a card: the space left for text and the progress bar once the column's
     * trailing gap and the card's own horizontal padding are subtracted. The progress bar
     * uses exactly this width, so it can never stick out over the next column.
     */
    fun cardInnerWidth(columnWidth: Int, endGapDp: Int, cardPaddingDp: Int): Int =
        (columnWidth - endGapDp - cardPaddingDp * 2).coerceAtLeast(0)

    /** Category heading: short form in narrow columns so heading + count always fit. */
    fun categoryLabel(category: UpgradeCategory, columnWidthDp: Int): String =
        if (columnWidthDp < NARROW_COLUMN_DP) category.shortLabel else category.label
}
