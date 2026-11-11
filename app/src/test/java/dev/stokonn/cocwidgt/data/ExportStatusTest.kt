package dev.stokonn.cocwidgt.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ExportStatusTest {
    @Test fun importedSnapshotAgeIsRelativeAndBlankWhenUnknown() {
        val now = 48 * 60 * 60_000L
        assertEquals("", ExportStatus.age(now, 0L))
        assertEquals("just now", ExportStatus.age(now, now))
        assertEquals("5m ago", ExportStatus.age(now, now - 5 * 60_000L))
        assertEquals("2h ago", ExportStatus.age(now, now - 2 * 60 * 60_000L))
        assertEquals("1d ago", ExportStatus.age(now, now - 24 * 60 * 60_000L))
    }
}
