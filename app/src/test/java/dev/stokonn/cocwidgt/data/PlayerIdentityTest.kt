package dev.stokonn.cocwidgt.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerIdentityTest {

    @Test
    fun firstValidTagPrefersTheFirstValidCandidate() {
        assertEquals("#2PP", PlayerIdentity.firstValidTag("#2PP", "#2RR"))
        assertEquals("#2RR", PlayerIdentity.firstValidTag("bad!", "#2RR"))
        assertNull(PlayerIdentity.firstValidTag("bad!", null))
        assertNull(PlayerIdentity.firstValidTag(null, "no pe"))
        assertNull(PlayerIdentity.firstValidTag())
    }

    @Test
    fun matchingNameUsesApiNicknameOnlyWhenTagsAgree() {
        // The API profile is the only source of a nickname (exports carry none).
        assertEquals(
            "Alice",
            PlayerIdentity.matchingName(
                "#2PP", "#2PP", "Alice", "#2PP", null,
            ),
        )
        // API synced a different player: its nickname must not leak onto the import.
        assertNull(
            PlayerIdentity.matchingName(
                "#2PP", "#2RR", "Bob", "#2PP", null,
            ),
        )
    }

    @Test
    fun matchingNameFallsBackToExportNameAndRequiresExpectedTag() {
        assertEquals(
            "Clan Member",
            PlayerIdentity.matchingName("#2PP", null, null, "#2PP", "Clan Member"),
        )
        assertNull(PlayerIdentity.matchingName("#2PP", null, null, "#2PP", null))
        // Without a resolved expected tag there is nothing safe to display.
        assertNull(PlayerIdentity.matchingName(null, "#2RR", "Bob", "#2RR", "Export"))
    }

    @Test
    fun normalizeTagStaysStrict() {
        assertEquals("#2PP", PlayerIdentity.normalizeTag(" #2pp "))
        assertEquals("#2PP", PlayerIdentity.normalizeTag("#2PP"))
        assertNull(PlayerIdentity.normalizeTag(null))
        assertNull(PlayerIdentity.normalizeTag(""))
        assertNull(PlayerIdentity.normalizeTag("not a tag"))
        assertNull(PlayerIdentity.normalizeTag("#2P"))
        assertNull(PlayerIdentity.normalizeTag("#2PP!"))
    }

    @Test
    fun headerStateKeepsTheNeutralFallbackWithoutAnyRealName() {
        val state = PlayerIdentity.headerState(
            exportTag = "#2PP", exportName = "", exportTownHall = 0,
            settingsTag = null, profile = StoredProfile(null, null, -1, 0L, null),
        )
        assertEquals(PlayerHeaderState.FALLBACK_NAME, state.displayName)
        assertEquals("#2PP", state.expectedTag)
        assertEquals(false, state.profileMatches)
        assertEquals(-1, state.townHall)
        assertEquals(0L, state.apiSyncedAt)
        // An incomplete stored profile never contributes its name.
        val incomplete = PlayerIdentity.headerState(
            exportTag = null, exportName = null, exportTownHall = 0,
            settingsTag = "#2PP", profile = StoredProfile(null, "Alice", 16, 1_000L, null),
        )
        assertEquals(PlayerHeaderState.FALLBACK_NAME, incomplete.displayName)
        assertEquals("#2PP", incomplete.expectedTag)
    }

    @Test
    fun headerStateUsesStoredNicknameOnlyWhenTheProfileMatches() {
        val profile = StoredProfile("#2PP", "Alice", 16, 1_000L, "API down")
        val matched = PlayerIdentity.headerState("#2PP", "", 0, "#2PP", profile)
        assertEquals("Alice", matched.displayName)
        assertEquals(true, matched.profileMatches)
        assertEquals(16, matched.townHall)
        assertEquals(1_000L, matched.apiSyncedAt)
        assertEquals("API down", matched.apiError)

        // Profile belongs to another player: no nickname, no town hall, no error surfaced.
        val other = PlayerIdentity.headerState("#2RR", "", 0, "#2RR", profile)
        assertEquals(PlayerHeaderState.FALLBACK_NAME, other.displayName)
        assertEquals(false, other.profileMatches)
        assertEquals(-1, other.townHall)
        assertEquals(0L, other.apiSyncedAt)
        assertEquals("", other.apiError)
    }

    @Test
    fun headerStatePrefersStoredNicknameThenExportNameAndKeepsExportTownHall() {
        val profile = StoredProfile("#2PP", "Alice", 16, 1_000L, null)
        assertEquals(
            "Alice",
            PlayerIdentity.headerState("#2PP", " Export Nick ", 15, "#2PP", profile).displayName,
        )
        // No stored profile for this tag yet: the export name is used, trimmed, when tags agree.
        val noProfile = StoredProfile(null, "Alice", 16, 1_000L, null)
        val fromExport = PlayerIdentity.headerState("#2PP", " Export Nick ", 15, "#2PP", noProfile)
        assertEquals("Export Nick", fromExport.displayName)
        assertEquals(15, fromExport.townHall)
        // An export name that cannot be attributed to a valid Player Tag is never displayed.
        assertEquals(
            PlayerHeaderState.FALLBACK_NAME,
            PlayerIdentity.headerState(
                exportTag = "bad tag", exportName = "Other Player", exportTownHall = 15,
                settingsTag = "#2PP", profile = noProfile,
            ).displayName,
        )
    }
}
