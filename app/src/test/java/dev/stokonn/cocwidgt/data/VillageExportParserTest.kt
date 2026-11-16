package dev.stokonn.cocwidgt.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import dev.stokonn.cocwidgt.R
import dev.stokonn.cocwidgt.widget.WidgetContentPlanner

class VillageExportParserTest {
    private fun export(buildings: String = "[{\"data\":1000001,\"lvl\":16}]", upgrades: String = "[]", timestamp: Long = System.currentTimeMillis() / 1000L) =
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
        // The source section is preserved even for unknown IDs, so routing and diagnostics
        // still know where the row came from.
        assertEquals("Builders", village.jobs.single().section)
        assertEquals("unknown", village.jobs.single().iconKey)
        assertEquals("", village.name)
        assertEquals(listOf(987654321L), village.unsupportedDataIds)
    }

    @Test fun rejectsMalformedAndNonVillageJson() {
        assertThrows(IllegalArgumentException::class.java) { VillageExportParser.parse("{") }
        assertThrows(IllegalArgumentException::class.java) { VillageExportParser.parse("""{"tag":"#Y2099VGPJ","timestamp":1}""") }
        assertThrows(IllegalArgumentException::class.java) { VillageExportParser.parse(export(timestamp = 0)) }
    }

    @Test fun acceptsUtf8BomBeforeExportJson() {
        val village = VillageExportParser.parse("\uFEFF  ${export()}")
        assertEquals("#Y2099VGPJ", village.tag)
        assertEquals(16, village.townHall)
    }

    @Test fun acceptsTownHallNineteenWithoutClampingTheExportedLevel() {
        val village = VillageExportParser.parse(export(buildings = """[{"data":1000001,"lvl":19}]"""))
        assertEquals(19, village.townHall)
        assertEquals("Town Hall", GameCatalogue.resolve(1000001L).name)
        assertTrue(GameArtwork.drawableResId(1000001L, 19) != null)
    }

    @Test fun verifiedTownHallNineteenCatalogueMatchesUpstreamAndResolvesArtwork() {
        val expected = mapOf(
            1000001L to Triple("Town Hall", GameCategory.BUILDING, 19),
            1000014L to Triple("Clan Castle", GameCategory.BUILDING, 15),
            1000007L to Triple("Laboratory", GameCategory.BUILDING, 17),
            1000071L to Triple("Hero Hall", GameCategory.BUILDING, 13),
            1000005L to Triple("Gold Storage", GameCategory.BUILDING, 20),
            1000003L to Triple("Elixir Storage", GameCategory.BUILDING, 20),
            1000024L to Triple("Dark Elixir Storage", GameCategory.BUILDING, 14),
            1000010L to Triple("Wall", GameCategory.BUILDING, 20),
            1000004L to Triple("Gold Mine", GameCategory.BUILDING, 18),
            1000002L to Triple("Elixir Collector", GameCategory.BUILDING, 18),
            1000023L to Triple("Dark Elixir Drill", GameCategory.BUILDING, 12),
            1000012L to Triple("Air Defense", GameCategory.DEFENCE, 17),
            1000019L to Triple("Hidden Tesla", GameCategory.DEFENCE, 18),
            1000021L to Triple("X-Bow", GameCategory.DEFENCE, 14),
            1000084L to Triple("Multi-Archer Tower", GameCategory.DEFENCE, 5),
            1000079L to Triple("Multi-Gear Tower", GameCategory.DEFENCE, 4),
            1000085L to Triple("Ricochet Cannon", GameCategory.DEFENCE, 5),
            1000015L to Triple("Builder's Hut", GameCategory.DEFENCE, 9),
            1000020L to Triple("Spell Factory", GameCategory.BUILDING, 10),
            1000070L to Triple("Blacksmith", GameCategory.BUILDING, 10),
            1000106L to Triple("Squad Barracks", GameCategory.BUILDING, 12),
            1000107L to Triple("Spiky Tower", GameCategory.DEFENCE, 2),
            1000108L to Triple("Firecracker Tower", GameCategory.DEFENCE, 2),
            12000005L to Triple("Air Bomb", GameCategory.TRAP, 14),
            12000008L to Triple("Skeleton Trap", GameCategory.TRAP, 6),
            12000001L to Triple("Spring Trap", GameCategory.TRAP, 14),
            26000000L to Triple("Lightning Spell", GameCategory.SPELL, 14),
            26000001L to Triple("Healing Spell", GameCategory.SPELL, 13),
            26000009L to Triple("Poison Spell", GameCategory.SPELL, 13),
            26000011L to Triple("Haste Spell", GameCategory.SPELL, 8),
            26000028L to Triple("Bat Spell", GameCategory.SPELL, 9),
            26000120L to Triple("Totem Spell", GameCategory.SPELL, 5),
            26000153L to Triple("Heroic Spell", GameCategory.SPELL, 4),
            4000000L to Triple("Barbarian", GameCategory.TROOP, 14),
            4000001L to Triple("Archer", GameCategory.TROOP, 15),
            4000002L to Triple("Goblin", GameCategory.TROOP, 11),
            4000003L to Triple("Giant", GameCategory.TROOP, 15),
            4000004L to Triple("Wall Breaker", GameCategory.TROOP, 15),
            4000005L to Triple("Balloon", GameCategory.TROOP, 14),
            4000006L to Triple("Wizard", GameCategory.TROOP, 15),
            4000007L to Triple("Healer", GameCategory.TROOP, 12),
            4000008L to Triple("Dragon", GameCategory.TROOP, 14),
            4000009L to Triple("P.E.K.K.A", GameCategory.TROOP, 14),
            4000010L to Triple("Minion", GameCategory.TROOP, 15),
            4000011L to Triple("Hog Rider", GameCategory.TROOP, 16),
            4000012L to Triple("Valkyrie", GameCategory.TROOP, 13),
            4000013L to Triple("Golem", GameCategory.TROOP, 16),
            4000015L to Triple("Witch", GameCategory.TROOP, 9),
            4000017L to Triple("Lava Hound", GameCategory.TROOP, 9),
            4000022L to Triple("Bowler", GameCategory.TROOP, 11),
            4000023L to Triple("Baby Dragon", GameCategory.TROOP, 13),
            4000024L to Triple("Miner", GameCategory.TROOP, 13),
            4000053L to Triple("Yeti", GameCategory.TROOP, 9),
            4000056L to Triple("Super Miner", GameCategory.TROOP, 13),
            4000059L to Triple("Electro Dragon", GameCategory.TROOP, 10),
            4000063L to Triple("Inferno Dragon", GameCategory.TROOP, 13),
            4000064L to Triple("Super Valkyrie", GameCategory.TROOP, 13),
            4000065L to Triple("Dragon Rider", GameCategory.TROOP, 7),
            4000066L to Triple("Super Witch", GameCategory.TROOP, 9),
            4000076L to Triple("Ice Hound", GameCategory.TROOP, 9),
            4000080L to Triple("Super Bowler", GameCategory.TROOP, 11),
            4000082L to Triple("Headhunter", GameCategory.TROOP, 5),
            4000091L to Triple("Flame Flinger", GameCategory.SIEGE, 6),
            4000095L to Triple("Electro Titan", GameCategory.TROOP, 6),
            4000109L to Triple("Ruin Witch", GameCategory.TROOP, 5),
            4000110L to Triple("Root Rider", GameCategory.TROOP, 5),
            4000123L to Triple("Druid", GameCategory.TROOP, 7),
            4000132L to Triple("Thrower", GameCategory.TROOP, 5),
            4000147L to Triple("Super Yeti", GameCategory.TROOP, 9),
            4000177L to Triple("Meteor Golem", GameCategory.TROOP, 4),
            4000195L to Triple("Barch Squad", GameCategory.TROOP, 3),
            4000196L to Triple("Giant Wizard Squad", GameCategory.TROOP, 3),
            4000197L to Triple("Wall Breaker Squad", GameCategory.TROOP, 3),
            4000198L to Triple("Minion Squad", GameCategory.TROOP, 3),
            4000199L to Triple("Balloon Squad", GameCategory.TROOP, 3),
            4000200L to Triple("Golem Witch Squad", GameCategory.TROOP, 3),
            4000201L to Triple("Hog Rider Squad", GameCategory.TROOP, 3),
            4000202L to Triple("Miner Squad", GameCategory.TROOP, 3),
            4000203L to Triple("Healer Squad", GameCategory.TROOP, 3),
            4000204L to Triple("P.E.K.K.A Titan Squad", GameCategory.TROOP, 3),
            4000205L to Triple("Dragons Squad", GameCategory.TROOP, 3),
            4000206L to Triple("Baby Dragon Squad", GameCategory.TROOP, 3),
            28000000L to Triple("Barbarian King", GameCategory.HERO, 115),
            28000001L to Triple("Archer Queen", GameCategory.HERO, 115),
            28000002L to Triple("Grand Warden", GameCategory.HERO, 90),
            28000004L to Triple("Royal Champion", GameCategory.HERO, 60),
            28000006L to Triple("Minion Prince", GameCategory.HERO, 100),
            28000007L to Triple("Dragon Duke", GameCategory.HERO, 30),
            73000011L to Triple("Angry Jelly", GameCategory.PET, 15),
            107000000L to Triple("Longshot", GameCategory.GUARDIAN, 5),
            107000001L to Triple("Smasher", GameCategory.GUARDIAN, 10),
            107000008L to Triple("Logger", GameCategory.GUARDIAN, 5),
        )
        expected.forEach { (id, metadata) ->
            val entity = GameCatalogue.resolve(id)
            assertEquals("name for $id", metadata.first, entity.name)
            assertEquals("category for $id", metadata.second, entity.category)
            assertEquals("max level for $id", metadata.third, GameCatalogue.maxLevel(id))
            assertTrue("artwork for $id", GameArtwork.drawableResId(id, metadata.third) != null)
            if (metadata.second in setOf(GameCategory.BUILDING, GameCategory.DEFENCE, GameCategory.TRAP)) {
                assertTrue("exact TH19 level artwork for $id", GameArtworkLevels.levelArtwork[id]?.containsKey(metadata.third) == true)
            }
        }
        assertEquals(expected.mapValues { it.value.third }, GameCatalogue.verifiedTownHall19MaxLevels)
        assertEquals("Guardian Shrine", GameCatalogue.resolve(1000105L).name)
        assertEquals(GameCategory.BUILDING, GameCatalogue.resolve(1000105L).category)
        assertFalse(GameCatalogue.resolve(1000105L).upgradeable)
        assertNull(GameCatalogue.maxLevel(1000105L))
        assertNull(GameArtwork.drawableResId(1000105L))
        assertEquals("Heroic Spell", GameCatalogue.resolve(26000153L).name)
        val townHallVariants = mapOf(
            119000000L to ("Wizard Hall" to R.drawable.coc_buildings_home_village_town_hall_level_19_wizard),
            119000001L to ("Archer Hall" to R.drawable.coc_buildings_home_village_town_hall_level_19_archer),
            119000002L to ("Barbarian Hall" to R.drawable.coc_buildings_home_village_town_hall_level_19_barbarian),
        )
        townHallVariants.forEach { (id, variant) ->
            val entity = GameCatalogue.resolve(id)
            assertEquals(variant.first, entity.name)
            assertEquals(GameCategory.BUILDING_VARIANT, entity.category)
            assertFalse(entity.upgradeable)
            assertEquals(variant.second, GameArtwork.drawableResId(id))
        }
        (4000195L..4000206L).forEach { id -> assertTrue("artwork for $id", GameArtwork.drawableResId(id) != null) }
    }

    @Test fun townHallNineteenImportKeepsNewEntitiesAndRoutesGuardiansAndLabCorrectly() {
        val village = VillageExportParser.parse("""{"tag":"#Y2099VGPJ","timestamp":${System.currentTimeMillis() / 1000L},
            "buildings":[{"data":1000001,"lvl":19},{"data":1000105},{"data":1000105,"lvl":0,"timer":30},{"data":1000107,"lvl":0,"timer":60}],
            "units":[{"data":4000195,"lvl":2,"timer":120}],
            "spells":[{"data":26000153,"lvl":3,"timer":180}],
            "heroes":[{"data":28000007,"lvl":29,"timer":240}],
            "guardians":[{"data":107000001,"lvl":9,"timer":300}],
            "unknown_th19_section":[{"data":998877,"lvl":1,"timer":60}]}""")
        assertEquals(19, village.townHall)
        assertEquals(7, village.jobs.size)
        assertEquals("Spiky Tower", village.jobs.single { it.dataId == 1000107L }.name)
        assertEquals(UpgradeCategory.LABORATORY, village.jobs.single { it.dataId == 4000195L }.category)
        assertEquals(UpgradeCategory.LABORATORY, village.jobs.single { it.dataId == 26000153L }.category)
        assertEquals(UpgradeCategory.HEROES, village.jobs.single { it.dataId == 28000007L }.category)
        assertEquals(UpgradeCategory.GUARDIANS, village.jobs.single { it.dataId == 107000001L }.category)
        assertEquals(listOf(998877L), village.unsupportedDataIds)
        assertTrue(village.jobs.any { it.name == "Unknown game item (ID 998877)" })
        assertTrue(village.jobs.any { it.dataId == 1000105L })
        assertEquals(listOf(1000105L), village.jobsForNonUpgradeableEntities.map { it.dataId })
    }

    @Test fun unsupportedIdsAndOutOfRangeLevelsAreReportedWithoutDroppingRows() {
        val village = VillageExportParser.parse(export(upgrades = """
            [{"data":987654321,"lvl":1,"timer":60},{"data":1000012,"lvl":17,"timer":120}]
        """))
        assertEquals(listOf(987654321L), village.unsupportedDataIds)
        assertEquals(listOf(1000012L), village.jobsAboveCatalogueMax.map { it.dataId })
        assertEquals(2, village.jobs.size)
        assertTrue(village.jobs.any { it.name == "Unknown game item (ID 987654321)" })
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
            "buildings":[{"data":1000001,"lvl":16},{"data":1000008,"lvl":1,"timer":600},{"data":1000009,"lvl":1,"timer":900}],
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
        assertEquals(1, village.jobs.count { it.section == "Equipment" })
        assertEquals(2, village.jobs.count { it.section == "Builder Base" })
        assertEquals(1, village.jobs.count { it.section == "Star Laboratory" })
        assertEquals(1, village.jobs.count { it.section == "Pets" })
        // Category routing: home laboratory research is its own Laboratory group, Builder Base
        // Star Laboratory research stays in Troops, equipment rows in Heroes, and unknown-but-
        // valid rows are kept instead of dropped.
        fun job(dataId: Long) = village.jobs.single { it.dataId == dataId }
        assertEquals(UpgradeCategory.LABORATORY, job(4000000L).category)
        assertEquals(UpgradeCategory.LABORATORY, job(4000001L).category)
        assertEquals(UpgradeCategory.LABORATORY, job(26000000L).category)
        assertEquals(UpgradeCategory.TROOPS, job(4000031L).category)
        assertEquals(UpgradeCategory.HEROES, job(99900001L).category)
        assertEquals(UpgradeCategory.PETS, job(73000000L).category)
        assertEquals(UpgradeCategory.BUILDINGS, job(1000008L).category)
        assertEquals(UpgradeCategory.BUILDINGS, job(1000044L).category)
    }

    @Test fun emptyUpgradeSectionsProduceNoSyntheticJobsForAValidVillage() {
        val village = VillageExportParser.parse(export(upgrades = "[]"))
        assertTrue(village.jobs.isEmpty())
        assertTrue(WidgetContentPlanner.select(village.jobs, 480, 600, System.currentTimeMillis()).items.isEmpty())
    }

    @Test fun incompleteImportWithoutTownHallIsRejectedBeforePersistence() {
        assertThrows(IllegalArgumentException::class.java) {
            VillageExportParser.parse(export(buildings = "[]", upgrades = "[]"))
        }
    }

    @Test fun repeatedNamesAndIdsKeepDistinctExportInstances() {
        val village = VillageExportParser.parse(export(upgrades = """[
            {"data":1000010,"lvl":1,"timer":60},
            {"data":1000033,"lvl":1,"timer":120},
            {"data":1000008,"lvl":2,"timer":90,"cnt":4},
            {"data":1000008,"lvl":3,"timer":180}]"""))
        assertEquals(4, village.jobs.size)
        assertEquals(2, village.jobs.count { it.name == "Wall" })
        assertEquals(2, village.jobs.count { it.dataId == 1000008L })
        assertEquals(listOf(3, 4), village.jobs.filter { it.dataId == 1000008L }.map { it.level })
        assertEquals(listOf(2, 3), village.jobs.filter { it.dataId == 1000008L }.map { it.currentLevel })
        assertEquals(listOf(4, 1), village.jobs.filter { it.dataId == 1000008L }.map { it.count })
        assertEquals(4, village.jobs.map { it.id }.distinct().size)
    }

    @Test fun malformedActiveUpgradeFieldsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            VillageExportParser.parse(export(upgrades = """[{"data":1000008,"timer":60}]"""))
        }
        assertThrows(IllegalArgumentException::class.java) {
            VillageExportParser.parse(export(upgrades = """[{"data":1000008,"lvl":1,"timer":60,"cnt":0}]"""))
        }
        assertThrows(IllegalArgumentException::class.java) {
            VillageExportParser.parse(export(upgrades = """[{"data":1000008,"lvl":1,"timer":-60}]"""))
        }
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
            "buildings":[{"data":1000001,"lvl":16},{"data":1000008,"lvl":5,"timer":600,"extra":true},{"data":1000009,"lvl":5,"timer":900,"extra":1}],
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

    @Test fun laboratoryResearchGoblinLabAndHelpersRouteToTheirOwnGroups() {
        val timestamp = System.currentTimeMillis() / 1000L
        val raw = """{"tag":"#Y2099VGPJ","timestamp":$timestamp,"buildings":[{"data":1000001,"lvl":16}],
            "units":[{"data":4000000,"lvl":10,"timer":600}],
            "spells":[{"data":26000002,"lvl":6,"timer":900}],
            "siege_machines":[{"data":4000051,"lvl":3,"timer":1200}],
            "goblin_lab":[{"data":4000008,"lvl":7,"timer":300,"extra":true}],
            "goblin_research":[{"data":93000001,"lvl":2,"timer":300}]}"""
        val village = VillageExportParser.parse(raw)
        assertEquals(5, village.jobs.size)
        fun job(dataId: Long) = village.jobs.single { it.dataId == dataId }
        // Home laboratory research (troops, spells, siege machines) is its own Laboratory group.
        assertEquals(UpgradeCategory.LABORATORY, job(4000000L).category)
        assertEquals(UpgradeCategory.LABORATORY, job(26000002L).category)
        assertEquals(UpgradeCategory.LABORATORY, job(4000051L).category)
        // A goblin-flagged lab row goes to Goblin Workers even inside a laboratory section…
        assertEquals(true, job(4000008L).goblin)
        assertEquals(UpgradeCategory.GOBLIN, job(4000008L).category)
        // …and helper entities are Goblin Workers wherever they appear.
        assertEquals(UpgradeCategory.GOBLIN, job(93000001L).category)
    }

    @Test fun unknownSectionsAreKeptInsteadOfDiscarded() {
        val timestamp = System.currentTimeMillis() / 1000L
        val raw = """{"tag":"#Y2099VGPJ","timestamp":$timestamp,
            "buildings":[{"data":1000001,"lvl":16}],"guardians":[{"data":987654999,"lvl":2,"timer":600}]}"""
        val village = VillageExportParser.parse(raw)
        val guardian = village.jobs.single()
        assertEquals(987654999L, guardian.dataId)
        assertEquals("Unknown game item (ID 987654999)", guardian.name)
        assertEquals("Guardians", guardian.section)
        assertEquals(UpgradeCategory.OTHER, guardian.category)
    }

    @Test fun everyTopLevelUpgradeArrayIsParsedNotOnlyAWhitelist() {
        val timestamp = System.currentTimeMillis() / 1000L
        val raw = """{"tag":"#Y2099VGPJ","timestamp":$timestamp,"buildings":[{"data":1000001,"lvl":16}],
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
        val raw = """{"tag":"#Y2099VGPJ","timestamp":$timestamp,"buildings":[{"data":1000001,"lvl":16}],
            "boosts":[{"timer":600}],"obstacles":[{"data":1000005,"lvl":1}]}"""
        val village = VillageExportParser.parse(raw)
        assertTrue(village.jobs.isEmpty())
    }

    @Test fun petHouseAndHelperUpgradesLandInTheGoblinAndPetColumns() {
        val timestamp = System.currentTimeMillis() / 1000L
        val raw = """{"tag":"#Y2099VGPJ","timestamp":$timestamp,
            "buildings":[{"data":1000001,"lvl":16},{"data":1000068,"lvl":6,"timer":900}],
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
