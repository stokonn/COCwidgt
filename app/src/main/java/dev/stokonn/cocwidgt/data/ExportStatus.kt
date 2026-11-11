package dev.stokonn.cocwidgt.data

/** Relative age for an imported village timestamp, recalculated whenever the widget renders. */
object ExportStatus {
    fun age(now: Long, since: Long): String {
        if (since <= 0L) return ""
        val minutes = ((now - since).coerceAtLeast(0L)) / 60_000L
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "${minutes}m ago"
            minutes < 60 * 24 -> "${minutes / 60}h ago"
            else -> "${minutes / (60 * 24)}d ago"
        }
    }
}
