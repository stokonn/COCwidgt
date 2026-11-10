package dev.stokonn.cocwidgt.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Offline and failure classification for the one-line widget status. Every label must stay
 * short enough for a narrow widget line while still being explicit about what happened.
 */
class SyncStatusTest {

    @Test
    fun transportFailuresAreClassifiedAsOffline() {
        assertEquals(
            "offline — will retry",
            SyncStatus.shortLabel("unable to resolve host \"api.clashofclans.com\" (No address associated with hostname)"),
        )
        assertEquals(
            "offline — will retry",
            SyncStatus.shortLabel("failed to connect to /13.107.42.14 (port 443) after 15000ms"),
        )
        assertEquals("offline — will retry", SyncStatus.shortLabel("Network is unreachable"))
        assertEquals("offline — will retry", SyncStatus.shortLabel("connection timed out"))
    }

    @Test
    fun knownApiFailuresGetShortExplicitLabels() {
        assertEquals("rate limited", SyncStatus.shortLabel("API rate limit reached. Try later."))
        assertEquals("rate limited", SyncStatus.shortLabel("HTTP 429"))
        assertEquals("token/IP rejected", SyncStatus.shortLabel("Token rejected or source IP is not allowlisted."))
        assertEquals("token/IP rejected", SyncStatus.shortLabel("API token rejected."))
        assertEquals("player not found", SyncStatus.shortLabel("Player not found. Check the tag."))
        assertEquals(
            "tag or token missing",
            SyncStatus.shortLabel("Add your Player Tag and API token in app settings."),
        )
        assertEquals(
            "token unreadable",
            SyncStatus.shortLabel("Stored API token is unreadable. Re-enter it in app settings."),
        )
        assertEquals("invalid tag or request", SyncStatus.shortLabel("Invalid player tag or request."))
    }

    @Test
    fun relativeAgeIsDerivedFromTheAbsoluteTimestampOnEveryRender() {
        val now = 1_700_000_000_000L
        assertEquals("", SyncStatus.age(now, 0L))
        assertEquals("just now", SyncStatus.age(now, now))
        assertEquals("just now", SyncStatus.age(now, now - 30_000L))
        assertEquals("6m ago", SyncStatus.age(now, now - 6 * 60_000L))
        assertEquals("59m ago", SyncStatus.age(now, now - 59 * 60_000L))
        assertEquals("1h ago", SyncStatus.age(now, now - 60 * 60_000L))
        assertEquals("1d ago", SyncStatus.age(now, now - 25 * 60 * 60_000L))
        assertEquals("3d ago", SyncStatus.age(now, now - 3 * 24 * 60 * 60_000L))
        // A timestamp in the future (clock skew) never renders a negative age.
        assertEquals("just now", SyncStatus.age(now, now + 5_000L))
    }

    @Test
    fun unknownErrorsAreKeptButNeverGrowPastTheWidgetLine() {
        // Blank means "no failure" and must stay empty.
        assertEquals("", SyncStatus.shortLabel(null))
        assertEquals("", SyncStatus.shortLabel("   "))
        // A short raw message passes through unchanged so context is not lost.
        assertEquals("Network error.", SyncStatus.shortLabel("Network error."))
        // A long raw message is truncated to fit the single widget line, with an ellipsis.
        val long = "Clash API error 500: something went very wrong with the response body"
        val label = SyncStatus.shortLabel(long)
        assertTrue(label.length <= 40)
        assertTrue(label.endsWith("…"))
    }
}
