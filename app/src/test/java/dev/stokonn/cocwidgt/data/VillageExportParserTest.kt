package dev.stokonn.cocwidgt.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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
        // Progress is derived from the absolute start and completion timestamps, never invented.
        assertEquals(0f, job.progress(timestamp * 1000L)!!, 0.001f)
        assertEquals(1f, job.progress((timestamp + 3600L) * 1000L)!!, 0.001f)
        assertEquals(0.5f, job.progress((timestamp + 1800L) * 1000L)!!, 0.001f)
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
        assertEquals(0f, job.progress(1_000L)!!, 0.001f)
        assertEquals(0.5f, job.progress(31_000L)!!, 0.001f)
        assertEquals(1f, job.progress(61_000L)!!, 0.001f)
        assertFalse(job.remainingMs(61_000L) > 0)
        assertTrue(job.isComplete(61_000L))
    }

    @Test fun playerTagsAreCanonicalAndNamesNeverCrossPlayerBoundaries() {
        assertEquals("#Y2099VGPJ", PlayerIdentity.normalizeTag("  y2099vgpj  "))
        assertEquals("#Y2099VGPJ", PlayerIdentity.normalizeTag(" #y2099vgpj "))
        assertNull(PlayerIdentity.normalizeTag("##Y2099VGPJ"))
        assertNull(PlayerIdentity.normalizeTag("bad tag!"))
        assertNull(PlayerIdentity.normalizeTag("#YA 123"))
        assertEquals("#Y2099VGPJ", PlayerIdentity.firstValidTag("##invalid", "  y2099vgpj "))
        assertEquals("#OTHER123", PlayerIdentity.firstValidTag("##bad", "#other123"))
        assertEquals("API Name", PlayerIdentity.matchingName("#Y2099VGPJ", " y2099vgpj ", " API Name ", "#Y2099VGPJ", "Export Name"))
        assertEquals("Export Name", PlayerIdentity.matchingName("#Y2099VGPJ", "#OTHER123", "Wrong Player", "#Y2099VGPJ", "Export Name"))
        assertNull(PlayerIdentity.matchingName("#Y2099VGPJ", "#OTHER123", "Wrong Player", "#OTHER123", "Other Player Export"))
        assertNull(PlayerIdentity.matchingName("#Y2099VGPJ", "#OTHER123", "Wrong Player", "#OTHER123", ""))
        assertEquals("Export Name", PlayerIdentity.matchingName("#Y2099VGPJ", "#Y2099VGPJ", "   ", "#Y2099VGPJ", " Export Name "))
        assertNull(PlayerIdentity.matchingName("#Y2099VGPJ", "#Y2099VGPJ", " ", "#Y2099VGPJ", " "))
        // Persistence flow: stored API profile name is preferred once the profile tag matches.
        assertEquals("Stored Name", PlayerIdentity.matchingName("#Y2099VGPJ", "#Y2099VGPJ", " Stored Name ", null, null))
        // No API profile yet → the import name is used instead of a placeholder.
        assertEquals("Export Name", PlayerIdentity.matchingName("#Y2099VGPJ", null, null, "#Y2099VGPJ", "Export Name"))
        // No source at all → neutral fallback, never the Player Tag posing as a name.
        assertNull(PlayerIdentity.matchingName("#Y2099VGPJ", null, null, "#Y2099VGPJ", "   "))
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

    @Test fun goblinWorkerUpgradesAreParsedStoredCategorizedAndKept() {
        val timestamp = System.currentTimeMillis() / 1000L
        val raw = """{"tag":"#Y2099VGPJ","timestamp":$timestamp,
            "buildings":[{"data":1000008,"lvl":5,"timer":600,"extra":true},{"data":1000009,"lvl":5,"timer":900,"extra":1}],
            "traps":[{"data":12000000,"lvl":3,"timer":300,"extra":false}],
            "units":[{"data":4000000,"lvl":9,"timer":1200,"extra":"true"}],
            "heroes":[{"data":28000000,"lvl":95,"timer":2400}],
            "pets":[{"data":73000000,"lvl":1,"timer":1500,"extra":true}],
            "buildings2":[{"data":1000044,"lvl":8,"timer":1800,"extra":true}]}"""
        val village = VillageExportParser.parse(raw)
        // No valid upgrade is silently discarded, whatever its flag or section.
        assertEquals(7, village.jobs.size)
        fun job(dataId: Long) = village.jobs.single { it.dataId == dataId }
        assertEquals(true, job(1000008L).goblin)
        assertEquals(true, job(1000009L).goblin)
        assertEquals(false, job(12000000L).goblin)
        assertEquals(true, job(4000000L).goblin)
        assertNull("Flag absent means unknown, never guessed", job(28000000L).goblin)
        assertNull("Pets never carry the flag", job(73000000L).goblin)
        assertNull("Builder Base never carries the flag", job(1000044L).goblin)
        // The goblin flag routes the row into the Goblin / Builder column; the real entity,
        // level and timer are untouched. Rows without the flag stay in their game category.
        assertEquals(UpgradeCategory.GOBLIN, job(1000008L).category)
        assertEquals(UpgradeCategory.GOBLIN, job(4000000L).category)
        assertEquals(UpgradeCategory.BUILDINGS, job(12000000L).category)
        assertEquals(UpgradeCategory.HEROES, job(28000000L).category)
        assertEquals(UpgradeCategory.PETS, job(73000000L).category)
        assertEquals(UpgradeCategory.BUILDINGS, job(1000044L).category)
    }

    @Test fun unknownSectionsAreKeptInsteadOfDiscarded() {
        val timestamp = System.currentTimeMillis() / 1000L
        val raw = """{"tag":"#Y2099VGPJ","timestamp":$timestamp,
            "buildings":[],"guardians":[{"data":987654999,"lvl":2,"timer":600}]}"""
        val village = VillageExportParser.parse(raw)
        val guardian = village.jobs.single()
        assertEquals(987654999L, guardian.dataId)
        assertEquals("Unknown game item (ID 987654999)", guardian.name)
        assertEquals(UpgradeCategory.OTHER, guardian.category)
    }

    @Test fun everyTopLevelUpgradeArrayIsParsedNotOnlyAWhitelist() {
        val timestamp = System.currentTimeMillis() / 1000L
        val raw = """{"tag":"#Y2099VGPJ","timestamp":$timestamp,"buildings":[],
            "house_parts":[{"data":73000001,"lvl":3,"timer":600}],
            "goblin_research":[{"data":93000001,"lvl":2,"timer":300}],
            "skins":[{"data":1000008,"lvl":1,"timer":0}]}"""
        val village = VillageExportParser.parse(raw)
        // The non-upgrade row (no timer) is skipped; both real upgrades survive with their IDs.
        assertEquals(2, village.jobs.size)
        val pet = village.jobs.single { it.dataId == 73000001L }
        assertEquals("Mighty Yak", pet.name)
        assertEquals("House Parts", pet.section) // section rendered from the unknown key
        assertEquals(UpgradeCategory.PETS, pet.category)
        assertEquals(4, pet.level) // lvl + 1 preserved
        assertEquals((timestamp + 600L) * 1000L, pet.finishAt())
        val assistant = village.jobs.single { it.dataId == 93000001L }
        assertEquals("Lab Assistant", assistant.name)
        assertEquals("Laboratory", assistant.section) // recognised by the "research" keyword
        assertEquals(UpgradeCategory.GOBLIN, assistant.category)
        assertEquals((timestamp + 300L) * 1000L, assistant.finishAt())
    }

    @Test fun rowsWithoutAnUpgradeShapeAreSkippedInsteadOfFailingTheImport() {
        val timestamp = System.currentTimeMillis() / 1000L
        val raw = """{"tag":"#Y2099VGPJ","timestamp":$timestamp,"buildings":[],
            "boosts":[{"timer":600}],"obstacles":[{"data":1000005,"lvl":1}]}"""
        val village = VillageExportParser.parse(raw)
        assertTrue(village.jobs.isEmpty())
    }

    @Test fun petHouseAndHelperUpgradesLandInTheGoblinAndPetColumns() {
        val timestamp = System.currentTimeMillis() / 1000L
        val raw = """{"tag":"#Y2099VGPJ","timestamp":$timestamp,
            "buildings":[{"data":1000068,"lvl":6,"timer":900}],
            "helpers":[{"data":93000000,"lvl":3,"timer":600}]}"""
        val village = VillageExportParser.parse(raw)
        val petHouse = village.jobs.single { it.dataId == 1000068L }
        assertEquals("Pet House", petHouse.name)
        assertEquals(UpgradeCategory.PETS, petHouse.category)
        assertNull("Helpers never carry the goblin flag", village.jobs.single { it.dataId == 93000000L }.goblin)
        assertEquals(UpgradeCategory.GOBLIN, village.jobs.single { it.dataId == 93000000L }.category)
    }

    @Test fun exportWithoutANameStaysNamelessForTheHeaderToResolve() {
        // Real exports carry no name; the widget must fall back instead of inventing one.
        val raw = export(upgrades = """[{"data":1000008,"lvl":1,"timer":60}]""")
            .replace("\"name\":\"Test Village\",", "")
        val village = VillageExportParser.parse(raw)
        assertEquals("", village.name)
        val header = PlayerIdentity.headerState(
            exportTag = village.tag, exportName = village.name, exportTownHall = village.townHall,
            settingsTag = null, profile = StoredProfile(null, null, -1, 0L, null),
        )
        assertEquals(PlayerHeaderState.FALLBACK_NAME, header.displayName)
        assertEquals("#Y2099VGPJ", header.expectedTag)
    }

    @Test fun reimportKeepsFirstObservedStartForTheSameRunningUpgrade() {
        val first = VillageSnapshot("#Y2099VGPJ", "", 16, 5, 1_000_000L, listOf(
            VillageJob("buildings:0", "Cannon", "Builders", 5, 1_000_000L, 600_000L, dataId = 1000008L)))
        val second = VillageSnapshot("#Y2099VGPJ", "", 16, 5, 1_300_000L, listOf(
            VillageJob("buildings:0", "Cannon", "Builders", 5, 1_300_000L, 300_000L, dataId = 1000008L)))
        assertEquals(first.jobs.single().finishAt(), second.jobs.single().finishAt())
        val merged = second.withMergedTimers(first)
        assertEquals(1_000_000L, merged.jobs.single().startedAt)
        assertEquals(0.5f, merged.jobs.single().progress(1_300_000L)!!, 0.001f)
    }

    @Test fun changedTimerOrTagStartsAFreshProgressWindow() {
        val first = VillageSnapshot("#Y2099VGPJ", "", 16, 5, 1_000_000L, listOf(
            VillageJob("buildings:0", "Cannon", "Builders", 5, 1_000_000L, 600_000L, dataId = 1000008L)))
        // Deadline moved by hours: a genuinely different timer, not export jitter.
        val drifted = VillageSnapshot("#Y2099VGPJ", "", 16, 5, 1_300_000L, listOf(
            VillageJob("buildings:0", "Cannon", "Builders", 5, 1_300_000L, 3_600_000L, dataId = 1000008L)))
        assertEquals(1_300_000L, drifted.withMergedTimers(first).jobs.single().startedAt)
        val otherPlayer = first.copy(tag = "#OTHER123")
        assertSame(otherPlayer, otherPlayer.withMergedTimers(first))
        assertSame(first, first.withMergedTimers(null))
    }

    @Test fun goblinFlagSurvivesReimportOnlyWhileTheExportKeepsQuiet() {
        val first = VillageSnapshot("#Y2099VGPJ", "", 16, 5, 1_000_000L, listOf(
            VillageJob("buildings:0", "Cannon", "Builders", 5, 1_000_000L, 600_000L, goblin = true, dataId = 1000008L)))
        val silent = VillageSnapshot("#Y2099VGPJ", "", 16, 5, 1_300_000L, listOf(
            VillageJob("buildings:0", "Cannon", "Builders", 5, 1_300_000L, 300_000L, goblin = null, dataId = 1000008L)))
        val explicit = VillageSnapshot("#Y2099VGPJ", "", 16, 5, 1_300_000L, listOf(
            VillageJob("buildings:0", "Cannon", "Builders", 5, 1_300_000L, 300_000L, goblin = false, dataId = 1000008L)))
        assertEquals(true, silent.withMergedTimers(first).jobs.single().goblin)
        assertEquals(false, explicit.withMergedTimers(first).jobs.single().goblin)
    }
}
