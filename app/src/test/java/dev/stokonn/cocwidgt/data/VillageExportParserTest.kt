package dev.stokonn.cocwidgt.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import dev.stokonn.cocwidgt.widget.WidgetContentPlanner

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

    @Test fun catalogueNameWinsOverUntrustedExportLabel() {
        val village = VillageExportParser.parse(export(upgrades = """[{"data":1000008,"name":"Item 123","lvl":2,"timer":60}]"""))
        assertEquals("Cannon", village.jobs.single().name)
        assertTrue(GameArtwork.drawableResId(1000008) != null)
        assertNull(GameArtwork.drawableResId(987654321))
    }

    @Test fun unknownIdsAreExplicitAndNeverFabricated() {
        val raw = export(upgrades = """[{"data":987654321,"lvl":1,"timer":60}]""").replace("\"name\":\"Test Village\",", "")
        val village = VillageExportParser.parse(raw)
        assertEquals("Unknown game item (ID 987654321)", village.jobs.single().name)
        assertEquals("Unknown item", village.jobs.single().section)
        assertEquals("unknown", village.jobs.single().iconKey)
        assertEquals("", village.name)
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

    @Test fun playerTagsAreCanonicalAndNamesNeverCrossPlayerBoundaries() {
        assertEquals("#Y2099VGPJ", PlayerIdentity.normalizeTag("  y2099vgpj  "))
        assertEquals("#Y2099VGPJ", PlayerIdentity.normalizeTag(" #y2099vgpj "))
        assertNull(PlayerIdentity.normalizeTag("##Y2099VGPJ"))
        assertNull(PlayerIdentity.normalizeTag("bad tag!"))
        assertNull(PlayerIdentity.normalizeTag("#YA 123"))
        assertEquals("API Name", PlayerIdentity.matchingName("#Y2099VGPJ", " y2099vgpj ", " API Name ", "#Y2099VGPJ", "Export Name"))
        assertEquals("Export Name", PlayerIdentity.matchingName("#Y2099VGPJ", "#OTHER123", "Wrong Player", "#Y2099VGPJ", "Export Name"))
        assertNull(PlayerIdentity.matchingName("#Y2099VGPJ", "#OTHER123", "Wrong Player", "#OTHER123", "Other Player Export"))
        assertEquals("Export Name", PlayerIdentity.matchingName("#Y2099VGPJ", "#Y2099VGPJ", "   ", "#Y2099VGPJ", " Export Name "))
        assertNull(PlayerIdentity.matchingName("#Y2099VGPJ", "#Y2099VGPJ", " ", "#Y2099VGPJ", " "))
    }

    @Test fun mapsMultipleConcurrentUpgradesAcrossVillageSections() {
        val timestamp = System.currentTimeMillis() / 1000L
        val raw = """{"tag":" y2099vgpj ","name":"  Export Name  ","timestamp":$timestamp,
            "buildings":[{"data":1000008,"lvl":1,"timer":600},{"data":1000009,"lvl":1,"timer":900}],
            "units":[{"data":4000000,"lvl":10,"timer":1200},{"data":4000001,"lvl":10,"timer":1800}],
            "spells":[{"data":26000000,"lvl":5,"timer":2400}],
            "equipment":[{"data":99900001,"lvl":1,"timer":2700}],
            "pets":[{"data":73000000,"lvl":1,"timer":3000}],
            "buildings2":[{"data":1000044,"lvl":8,"timer":3600},{"data":1000055,"lvl":8,"timer":4200}],
            "units2":[{"data":4000031,"lvl":10,"timer":4800}]}"""
        val village = VillageExportParser.parse(raw)
        assertEquals("#Y2099VGPJ", village.tag)
        assertEquals("Export Name", village.name)
        assertEquals(10, village.jobs.size)
        assertEquals(2, village.jobs.count { it.section == "Builders" })
        assertEquals(3, village.jobs.count { it.section == "Laboratory" })
        assertEquals(1, village.jobs.count { it.section == "Unknown item" })
        assertEquals(2, village.jobs.count { it.section == "Builder Base" })
        assertEquals(1, village.jobs.count { it.section == "Star Laboratory" })
        assertEquals(1, village.jobs.count { it.section == "Pets" })
    }

    @Test fun emptyUpgradeSectionsProduceNoSyntheticJobs() {
        val village = VillageExportParser.parse(export(buildings = "[]", upgrades = "[]"))
        assertTrue(village.jobs.isEmpty())
        assertTrue(WidgetContentPlanner.select(village.jobs, 480, 600, System.currentTimeMillis()).items.isEmpty())
    }

    @Test fun repeatedNamesAndIdsKeepDistinctExportInstances() {
        val village = VillageExportParser.parse(export(upgrades = """[
            {"data":1000010,"lvl":1,"timer":60},
            {"data":1000033,"lvl":1,"timer":120},
            {"data":1000008,"lvl":2,"timer":90},
            {"data":1000008,"lvl":2,"timer":180}]"""))
        assertEquals(4, village.jobs.size)
        assertEquals(2, village.jobs.count { it.name == "Wall" })
        assertEquals(2, village.jobs.count { it.dataId == 1000008L })
        assertEquals(4, village.jobs.map { it.id }.distinct().size)
    }

    @Test fun oldExportTimersAreElapsedAgainstTheirTimestamp() {
        val oldTimestamp = System.currentTimeMillis() / 1000L - 86_400L
        val village = VillageExportParser.parse(export(upgrades = """[{"data":1000008,"lvl":1,"timer":60}]""", timestamp = oldTimestamp))
        assertEquals("Ready", village.jobs.single().countdown())
        assertTrue(System.currentTimeMillis() - village.exportedAt > 24 * 60 * 60 * 1000L)
    }

    @Test fun widgetSelectionScalesWithAvailableSpaceAndReportsHiddenRows() {
        val jobs = (0 until 16).map { index ->
            VillageJob("job:$index", "Upgrade $index", if (index % 4 == 0) "Builder Base" else "Builders", 2, 1_000L, 86_400_000L, dataId = index.toLong())
        }
        val now = 1_000L
        val compact = WidgetContentPlanner.select(jobs, 180, 110, now)
        val medium = WidgetContentPlanner.select(jobs, 360, 280, now)
        val wide = WidgetContentPlanner.select(jobs, 480, 600, now)
        assertEquals(1, compact.items.size)
        assertEquals(15, compact.hiddenCount)
        assertTrue(medium.items.size > compact.items.size)
        assertTrue(wide.items.size > medium.items.size)
        assertEquals(jobs.size, wide.items.size + wide.hiddenCount)
    }
}
