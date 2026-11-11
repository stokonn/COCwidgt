package dev.stokonn.cocwidgt.data

import dev.stokonn.cocwidgt.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for level- and identity-aware artwork resolution.
 *
 * The v0.5.0 widget showed level-1 artwork for every building and for the Town Hall
 * header regardless of the real level. These tests pin down:
 *  - exact resolution for bundled levels (Town Hall 17 in particular),
 *  - graceful fallback only when the requested level is genuinely not bundled,
 *  - entity identity (a home-village X-Bow must never resolve to Builder Base art),
 *  - portraits for level-independent entities.
 */
class GameArtworkTest {

    private val townHall = 1000001L
    private val homeVillageXBow = 1000021L
    private val builderBaseXBow = 1000081L

    // ---- exact level resolution -----------------------------------------------------

    @Test
    fun `town hall level 17 resolves to the level 17 artwork not level 1`() {
        assertEquals(
            R.drawable.coc_buildings_home_village_town_hall_level_17,
            GameArtwork.drawableResId(townHall, level = 17),
        )
        assertNotEquals(
            R.drawable.coc_buildings_home_village_town_hall_level_1,
            GameArtwork.drawableResId(townHall, level = 17),
        )
    }

    @Test
    fun `town hall level 18 (the highest bundled) resolves exactly`() {
        assertEquals(
            R.drawable.coc_buildings_home_village_town_hall_level_18,
            GameArtwork.drawableResId(townHall, level = 18),
        )
    }

    @Test
    fun `every bundled level of every entity resolves to that exact level`() {
        for ((dataId, levels) in GameArtworkLevels.levelArtwork) {
            for ((level, expected) in levels) {
                assertEquals(
                    "dataId=$dataId level=$level",
                    expected,
                    GameArtwork.drawableResId(dataId, level),
                )
            }
        }
    }

    // ---- fallback only when genuinely absent ----------------------------------------

    @Test
    fun `a level above the highest bundled artwork falls back to the highest available`() {
        // Requested level 25 does not exist for any entity: highest bundled wins.
        for ((dataId, levels) in GameArtworkLevels.levelArtwork) {
            val highest = levels.keys.max()
            assertEquals(
                "dataId=$dataId",
                levels[highest],
                GameArtwork.drawableResId(dataId, highest + 8),
            )
        }
    }

    @Test
    fun `a level below the lowest bundled artwork falls back to the lowest available`() {
        for ((dataId, levels) in GameArtworkLevels.levelArtwork) {
            val lowest = levels.keys.min()
            assertEquals("dataId=$dataId", levels[lowest], GameArtwork.drawableResId(dataId, 0))
            assertEquals("dataId=$dataId", levels[lowest], GameArtwork.drawableResId(dataId, -3))
        }
    }

    @Test
    fun `no entity silently reverts to level 1 when higher levels exist`() {
        for ((dataId, levels) in GameArtworkLevels.levelArtwork) {
            val highest = levels.keys.max()
            if (highest <= 1) continue
            val levelOne = levels[1] ?: continue
            for (level in 2..highest) {
                assertNotEquals(
                    "dataId=$dataId level=$level must not fall back to level-1 art",
                    levelOne,
                    GameArtwork.drawableResId(dataId, level),
                )
            }
        }
    }

    @Test
    fun `bundled level ranges are contiguous with no holes`() {
        for ((dataId, levels) in GameArtworkLevels.levelArtwork) {
            val sorted = levels.keys.sorted()
            val expected = (sorted.first()..sorted.last()).toList()
            assertEquals("dataId=$dataId has a gap in its level artwork", expected, sorted)
        }
    }

    // ---- entity identity -------------------------------------------------------------

    @Test
    fun `home village x bow never resolves to builder base artwork`() {
        assertEquals(
            R.drawable.coc_buildings_home_village_x_bow_level_5,
            GameArtwork.drawableResId(homeVillageXBow, level = 5),
        )
        assertNotEquals(
            R.drawable.coc_buildings_builder_base_x_bow_level_5,
            GameArtwork.drawableResId(homeVillageXBow, level = 5),
        )
        // And the Builder Base entity keeps its own identity.
        assertEquals(
            R.drawable.coc_buildings_builder_base_x_bow_level_3,
            GameArtwork.drawableResId(builderBaseXBow, level = 3),
        )
    }

    @Test
    fun `the single argument overload behaves like level 1 for levelled entities`() {
        for ((dataId, levels) in GameArtworkLevels.levelArtwork) {
            assertEquals(
                "dataId=$dataId",
                GameArtwork.drawableResId(dataId, 1),
                GameArtwork.drawableResId(dataId),
            )
        }
    }

    // ---- portraits -------------------------------------------------------------------

    @Test
    fun `portrait entities resolve to the same artwork for every level`() {
        for ((dataId, portrait) in GameArtworkLevels.portraitArtwork) {
            // Entities that also carry per-level artwork resolve level-aware first.
            if (GameArtworkLevels.levelArtwork.containsKey(dataId)) continue
            for (level in listOf(1, 2, 7, 20, 99)) {
                assertEquals(
                    "dataId=$dataId level=$level",
                    portrait,
                    GameArtwork.drawableResId(dataId, level),
                )
            }
        }
    }

    @Test
    fun `portraits cover the hero troop spell and pet dataIds used by the parser`() {
        // Barbarian King, Archer Queen, Grand Warden, Royal Champion, Minion Prince.
        for (dataId in listOf(28000000L, 28000001L, 28000002L, 28000004L, 28000006L)) {
            assertEquals(dataId.toString(), GameArtwork.drawableResId(dataId, 50), GameArtwork.drawableResId(dataId, 1))
            assertTrue(
                "dataId=$dataId must have artwork",
                GameArtwork.drawableResId(dataId, 1) != null,
            )
        }
    }

    // ---- unknown entities ------------------------------------------------------------

    @Test
    fun `an entity with no bundled artwork returns null instead of a wrong image`() {
        assertNull(GameArtwork.drawableResId(999_999_999L, level = 1))
        assertNull(GameArtwork.drawableResId(999_999_999L))
    }

    @Test
    fun `the artwork tables are non empty and consistent`() {
        assertTrue(GameArtworkLevels.levelArtwork.isNotEmpty())
        assertTrue(GameArtworkLevels.portraitArtwork.isNotEmpty())
        assertFalse(GameArtworkLevels.levelArtwork.keys.any { it in GameArtworkLevels.portraitArtwork })
        for ((dataId, levels) in GameArtworkLevels.levelArtwork) {
            assertTrue("dataId=$dataId", levels.isNotEmpty())
            for (res in levels.values) {
                assertTrue(res != 0)
            }
        }
    }
}
