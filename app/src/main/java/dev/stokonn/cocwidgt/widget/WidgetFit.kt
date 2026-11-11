package dev.stokonn.cocwidgt.widget

import dev.stokonn.cocwidgt.data.UpgradeCategory

/**
 * Pure fitting of text geometry to the real widget dimensions (Glance reports the actual
 * [androidx.glance.unit.DpSize] of the widget through LocalSize).
 *
 * Glance has no text ellipsis support and RemoteViews children can draw past an unmeasured
 * parent, so every single-line text is constrained here from the same arithmetic the rows
 * use. That keeps long names, category headings and status labels inside the single
 * scrolling column instead of clipping or overlapping the widget frame. All of it is
 * unit-tested.
 */
object WidgetFit {
    /**
     * Average glyph width of the platform default typeface as a fraction of the font size.
     * Deliberately slightly conservative: it rather ellipsizes early than lets text overflow.
     */
    private const val CHAR_WIDTH_FACTOR = 0.56

    /** Category headings switch to their short form below this width. */
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

    /** Category heading: short form in very narrow widgets so heading + count always fit. */
    fun categoryLabel(category: UpgradeCategory, widthDp: Int): String =
        if (widthDp < NARROW_COLUMN_DP) category.shortLabel else category.label
}
