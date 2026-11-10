package dev.stokonn.cocwidgt.data

import java.util.Locale

/**
 * Short, human labels for the widget/API status line, derived from the last sync error.
 *
 * The full error stays stored for the app screen; the widget only has a single narrow line,
 * so offline and failure states must stay short and explicit instead of a raw exception.
 * Pure and unit-tested — every classification is deterministic.
 */
object SyncStatus {
    /** Offline/network transport problems (WorkManager retries these automatically). */
    private val OFFLINE_MARKERS = listOf(
        "unable to resolve host",
        "failed to connect",
        "connection reset",
        "connection refused",
        "connection aborted",
        "connection timed out",
        "timed out",
        "timeout",
        "network is unreachable",
        "unexpected end of stream",
        "software caused connection abort",
        "ssl",
        "no route to host",
        "broken pipe",
    )

    /** Maps a stored sync error to a compact widget label; "" for a blank message. */
    fun shortLabel(message: String?): String {
        val raw = message?.trim().orEmpty()
        if (raw.isEmpty()) return ""
        val lower = raw.lowercase(Locale.ROOT)
        return when {
            OFFLINE_MARKERS.any { lower.contains(it) } -> "offline — will retry"
            lower.contains("rate limit") || lower.contains("http 429") -> "rate limited"
            lower.contains("token rejected") || lower.contains("allowlist") || lower.contains("http 403") -> "token/IP rejected"
            lower.contains("player not found") || lower.contains("http 404") -> "player not found"
            lower.contains("token") && lower.contains("unreadable") -> "token unreadable"
            lower.contains("add your player tag") || lower.contains("add your api token") -> "tag or token missing"
            lower.contains("invalid player tag or request") -> "invalid tag or request"
            lower.contains("does not match") || lower.contains("invalid player tag") || lower.contains("invalid player") -> "tag mismatch"
            lower.contains("unreadable") -> "unreadable API response"
            lower.contains("http 401") -> "API token rejected"
            else -> if (raw.length <= 40) raw else raw.take(39).trimEnd() + "…"
        }
    }

    /**
     * Relative age of an absolute timestamp ("just now", "6m ago", "2h ago", "3d ago"),
     * recomputed from [now] on every widget render — never a stored relative string, so it
     * cannot go stale between refreshes. Blank for unknown (≤ 0) timestamps.
     */
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
