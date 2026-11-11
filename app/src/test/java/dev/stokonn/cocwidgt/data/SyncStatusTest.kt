package dev.stokonn.cocwidgt.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncStatusTest {
    @Test fun transportFailuresAreClassifiedAsOffline() {
        assertEquals("offline", SyncStatus.shortLabel("unable to resolve host api.clashofclans.com"))
        assertEquals("offline", SyncStatus.shortLabel("failed to connect"))
        assertEquals("offline", SyncStatus.shortLabel("Network is unreachable"))
        assertEquals("offline", SyncStatus.shortLabel("connection timed out"))
    }

    @Test fun apiErrorsUseDeveloperKeyLanguageAndUsefulLabels() {
        assertEquals("rate limited", SyncStatus.shortLabel("API rate limit reached"))
        assertEquals("IP not allowlisted", SyncStatus.shortLabel("Source IP is not allowlisted"))
        assertEquals("API key rejected", SyncStatus.shortLabel("Developer API key rejected"))
        assertEquals("player not found", SyncStatus.shortLabel("Player not found"))
        assertEquals("invalid player tag", SyncStatus.shortLabel("Invalid player tag or request"))
    }

    @Test fun relativeAgeUsesAbsoluteTimestampAndHandlesClockSkew() {
        val now = 1_700_000_000_000L
        assertEquals("", SyncStatus.age(now, 0L))
        assertEquals("just now", SyncStatus.age(now, now))
        assertEquals("just now", SyncStatus.age(now, now - 30_000L))
        assertEquals("6m ago", SyncStatus.age(now, now - 6 * 60_000L))
        assertEquals("59m ago", SyncStatus.age(now, now - 59 * 60_000L))
        assertEquals("1h ago", SyncStatus.age(now, now - 60 * 60_000L))
        assertEquals("1d ago", SyncStatus.age(now, now - 25 * 60 * 60_000L))
        assertEquals("3d ago", SyncStatus.age(now, now - 3 * 24 * 60 * 60_000L))
        assertEquals("just now", SyncStatus.age(now, now + 5_000L))
    }

    @Test fun unknownMessagesRemainCompact() {
        assertEquals("", SyncStatus.shortLabel(null))
        assertEquals("", SyncStatus.shortLabel("  "))
        assertEquals("Network error.", SyncStatus.shortLabel("Network error."))
        val label = SyncStatus.shortLabel("Clash API error 500: something went very wrong with the response body")
        assertTrue(label.length <= 40)
        assertTrue(label.endsWith("…"))
    }
}
