package dev.stokonn.cocwidgt.data

import java.util.Locale

/** Compatibility labels for stored status text from older installs. New API requests use ApiFailureMessage. */
object SyncStatus {
    private val OFFLINE_MARKERS = listOf(
        "unable to resolve host", "failed to connect", "connection reset", "connection refused",
        "connection aborted", "connection timed out", "timed out", "timeout", "network is unreachable",
        "unexpected end of stream", "software caused connection abort", "ssl", "no route to host", "broken pipe",
    )

    fun shortLabel(message: String?): String {
        val raw = message?.trim().orEmpty()
        if (raw.isEmpty()) return ""
        val lower = raw.lowercase(Locale.ROOT)
        return when {
            OFFLINE_MARKERS.any(lower::contains) -> "offline"
            lower.contains("rate limit") || lower.contains("http 429") -> "rate limited"
            lower.contains("allowlist") || lower.contains("http 403") -> "IP not allowlisted"
            lower.contains("http 401") || lower.contains("developer api key rejected") || lower.contains("api key rejected") -> "API key rejected"
            lower.contains("player not found") || lower.contains("http 404") -> "player not found"
            lower.contains("invalid player tag or request") -> "invalid player tag"
            lower.contains("does not match") || lower.contains("invalid player tag") -> "tag mismatch"
            else -> if (raw.length <= 40) raw else raw.take(39).trimEnd() + "…"
        }
    }

    fun age(now: Long, since: Long): String = ExportStatus.age(now, since)
}
