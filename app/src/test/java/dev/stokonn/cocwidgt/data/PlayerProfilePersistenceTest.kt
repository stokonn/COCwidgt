package dev.stokonn.cocwidgt.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Persistence of the API profile: the nickname must survive a restart only together with a
 * valid Player Tag — an incomplete or legacy record can never surface a wrong player's name.
 */
class PlayerProfilePersistenceTest {

    private fun snapshot(
        tag: String = "#Y2099VGPJ",
        name: String = "Alice",
        syncedAt: Long = 1_000L,
    ) = PlayerSnapshot(
        name = name, tag = tag, townHall = 16, expLevel = 200, trophies = 4300,
        bestTrophies = 4500, warStars = 120,
        heroes = listOf("Barbarian King · 95", "Archer Queen · 93"),
        syncedAt = syncedAt,
    )

    @Test fun profileRoundTripsThroughThePreferenceValues() {
        val decoded = PlayerProfileCodec.decode(PlayerProfileCodec.encode(snapshot()))
        assertEquals("#Y2099VGPJ", decoded.tag)
        assertEquals("Alice", decoded.name)
        assertEquals(16, decoded.townHall)
        assertEquals(1_000L, decoded.syncedAt)
        assertNull("A fresh profile has no error", decoded.syncError)
    }

    @Test fun invalidTagNeverPersistsAProfile() {
        assertThrows(IllegalArgumentException::class.java) {
            PlayerProfileCodec.encode(snapshot(tag = "not a tag"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PlayerProfileCodec.encode(snapshot(tag = ""))
        }
    }

    @Test fun legacyStoredTagIsHonouredButStillNeedsToBeValid() {
        val legacy = PlayerProfileCodec.decode(
            mapOf("player_tag" to " y2099vgpj ", "name" to "Legacy Name", "town_hall" to 14, "synced_at" to 500L)
        )
        assertEquals("#Y2099VGPJ", legacy.tag)
        assertEquals("Legacy Name", legacy.name)
        assertEquals(14, legacy.townHall)
        assertEquals(500L, legacy.syncedAt)

        val brokenLegacy = PlayerProfileCodec.decode(mapOf("player_tag" to "bad tag!", "name" to "Legacy Name"))
        assertNull(brokenLegacy.tag)
        assertNull("No valid tag means no name", brokenLegacy.name)
    }

    @Test fun namelessAndJsonNullRecordsDoNotCreateANickname() {
        val nameless = PlayerProfileCodec.decode(mapOf("name" to "Alice"))
        assertNull(nameless.tag)
        assertNull(nameless.name)

        val jsonNull = PlayerProfileCodec.decode(mapOf("tag" to "#Y2099VGPJ", "name" to "null"))
        assertEquals("#Y2099VGPJ", jsonNull.tag)
        assertNull(jsonNull.name)

        val blankError = PlayerProfileCodec.decode(
            mapOf("tag" to "#Y2099VGPJ", "name" to "Alice", "sync_error" to "")
        )
        assertNull(blankError.syncError)
    }

    @Test fun encodeKeepsEveryFieldTheWidgetReads() {
        val values = PlayerProfileCodec.encode(snapshot())
        assertEquals("Alice", values["name"])
        assertEquals("#Y2099VGPJ", values["tag"])
        assertEquals(16, values["town_hall"])
        assertEquals(200, values["exp_level"])
        assertEquals(4300, values["trophies"])
        assertEquals(4500, values["best_trophies"])
        assertEquals(120, values["war_stars"])
        assertEquals("Barbarian King · 95|Archer Queen · 93", values["heroes"])
        assertEquals(1_000L, values["synced_at"])
        assertEquals("", values[PlayerProfileCodec.SYNC_ERROR_KEY])
        assertEquals("", values[PlayerProfileCodec.SYNC_ERROR_TAG_KEY])
    }

    @Test fun syncErrorRoundTripsTogetherWithItsAttributionTag() {
        val stored = PlayerProfileCodec.decode(
            PlayerProfileCodec.encode(snapshot()) + mapOf(
                PlayerProfileCodec.SYNC_ERROR_KEY to "API rate limit reached. Try later.",
                PlayerProfileCodec.SYNC_ERROR_TAG_KEY to " y2099vgpj ",
            )
        )
        assertEquals("API rate limit reached. Try later.", stored.syncError)
        // Attribution is canonicalized exactly like every other stored tag.
        assertEquals("#Y2099VGPJ", stored.syncErrorTag)

        // An error without an attribution tag degrades to "unattributed", which the header
        // shows instead of hiding the failure forever.
        val unattributed = PlayerProfileCodec.decode(
            PlayerProfileCodec.encode(snapshot()) +
                mapOf(PlayerProfileCodec.SYNC_ERROR_KEY to "Add your Player Tag and API token in app settings.")
        )
        assertEquals("Add your Player Tag and API token in app settings.", unattributed.syncError)
        assertNull(unattributed.syncErrorTag)

        // An unusable attribution tag is treated the same way — never trusted blindly.
        val brokenTag = PlayerProfileCodec.decode(
            PlayerProfileCodec.encode(snapshot()) +
                mapOf(PlayerProfileCodec.SYNC_ERROR_KEY to "boom", PlayerProfileCodec.SYNC_ERROR_TAG_KEY to "bad tag!")
        )
        assertNull(brokenTag.syncErrorTag)
    }

    @Test fun successfulSyncClearsTheStaleErrorAndItsTag() {
        // encode() is what ClashApi.saveSnapshot writes on success: both the failure message
        // and its attribution must be wiped, or a resolved error would keep surfacing.
        val values = PlayerProfileCodec.encode(snapshot())
        assertEquals("", values[PlayerProfileCodec.SYNC_ERROR_KEY])
        assertEquals("", values[PlayerProfileCodec.SYNC_ERROR_TAG_KEY])
        val decoded = PlayerProfileCodec.decode(values)
        assertNull(decoded.syncError)
        assertNull(decoded.syncErrorTag)
    }
}
