package dev.stokonn.cocwidgt.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class VillageExportParserTest {
    private fun export(buildings: String = "[]", upgrades: String = "[]", timestamp: Long = System.currentTimeMillis() / 1000L) =
        """{"tag":"#Y2099VGPJ","name":"Test Village","timestamp":$timestamp,"buildings":$buildings,"heroes":$upgrades}"""

    @Test fun mapsKnownIdsToRealNamesAndStableIds() {
        assertEquals("Cannon", GameCatalogue.resolve(1000008).name)
        assertEquals("Gold Storage", GameCatalogue.resolve(1000005).name)
        assertEquals("Barbarian King", GameCatalogue.resolve(28000000).name)
        assertEquals("L.A.S.S.I", GameCatalogue.resolve(73000000).name)
        val village = VillageExportParser.parse(export("""[{"data":1000001,"lvl":16},{"data":1000015,"lvl":1,"cnt":5}]"""))
        assertEquals(16, village.townHall)
        assertEquals(5, village.builders)
    }

    @Test fun timerIsRemainingTimeFromExportTimestamp() {
        val timestamp = System.currentTimeMillis() / 1000L
        val village = VillageExportParser.parse(export(upgrades = """[{"data":28000000,"lvl":90,"timer":3600}]""", timestamp = timestamp))
        val job = village.jobs.single()
        assertEquals("Barbarian King", job.name)
        assertEquals(28_000_000L, job.dataId)
        assertEquals((timestamp + 3600L) * 1000L, job.finishAt())
        assertTrue(job.remainingMs(timestamp * 1000L) in 3_599_000L..3_600_000L)
        assertNull("Unknown total upgrade duration must not produce a fake percentage", job.progress(timestamp * 1000L))
    }

    @Test fun unknownIdsAreExplicitAndNeverFabricated() {
        val village = VillageExportParser.parse(export(upgrades = """[{"data":987654321,"lvl":1,"timer":60}]"""))
        assertEquals("Unknown game item (ID 987654321)", village.jobs.single().name)
        assertEquals("Unknown item", village.jobs.single().section)
        assertEquals("unknown", village.jobs.single().iconKey)
    }

    @Test fun rejectsMalformedAndNonVillageJson() {
        assertThrows(IllegalArgumentException::class.java) { VillageExportParser.parse("{") }
        assertThrows(IllegalArgumentException::class.java) { VillageExportParser.parse("""{"tag":"#Y2099VGPJ","timestamp":1}""") }
        assertThrows(IllegalArgumentException::class.java) { VillageExportParser.parse(export(timestamp = 0)) }
    }

    @Test fun countdownReachesReadyAndClampsProgress() {
        val job = VillageJob("b:0", "Cannon", "Builders", 2, 1_000L, 60_000L, totalDurationMs = 120_000L)
        assertEquals("1m", job.countdown(1_000L))
        assertEquals("Ready", job.countdown(61_000L))
        assertEquals(0.5f, job.progress(1_000L)!!, 0.001f)
        assertEquals(1f, job.progress(61_000L)!!, 0.001f)
        assertFalse(job.remainingMs(61_000L) > 0)
    }
}
