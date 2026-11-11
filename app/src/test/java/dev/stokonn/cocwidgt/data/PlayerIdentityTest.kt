package dev.stokonn.cocwidgt.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerIdentityTest {

    @Test fun importedAndManualNamesAreBoundToTheConfiguredPlayer() {
        assertEquals("Export Name", PlayerIdentity.importedDisplayName("#Y2099VGPJ", " y2099vgpj ", " Export Name ", "Manual Name"))
        assertEquals("Manual Name", PlayerIdentity.importedDisplayName("#Y2099VGPJ", "#OTHER123", "Stale Export Name", "Manual Name"))
        assertEquals(PlayerHeaderState.FALLBACK_NAME, PlayerIdentity.importedDisplayName("#Y2099VGPJ", "#OTHER123", "Stale Export Name", "null"))
        assertEquals("Manual Name", PlayerIdentity.importedDisplayName("#Y2099VGPJ", "#Y2099VGPJ", "null", "Manual Name"))
    }

    @Test fun importTagIsCanonicalizedAndMismatchIsRejected() {
        assertEquals("#Y2099VGPJ", PlayerIdentity.requireImportMatchesConfiguredTag(" y2099vgpj ", "#Y2099VGPJ"))
        assertEquals("#Y2099VGPJ", PlayerIdentity.requireImportMatchesConfiguredTag(null, "y2099vgpj"))
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            PlayerIdentity.requireImportMatchesConfiguredTag("#OTHER123", "#Y2099VGPJ")
        }
    }

    @Test fun playerDisplayUsesOnlyMatchingApiOrImportIdentityAndFallsBackToTag() {
        assertEquals("Alice", PlayerIdentity.displayName("#Y2099VGPJ", "#Y2099VGPJ", "Alice", "#Y2099VGPJ", null, null, null))
        assertEquals("#Y2099VGPJ", PlayerIdentity.displayName("#Y2099VGPJ", "#OTHER123", "Wrong", "#OTHER123", "Stale", null, null))
        assertEquals("Manual", PlayerIdentity.displayName("#Y2099VGPJ", null, null, "#Y2099VGPJ", null, "#Y2099VGPJ", "Manual"))
    }

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

    @Test
    fun headerStateResolvesTheSettingsTagFirstLikeTheSyncDoes() {
        // The settings tag is written on every import and every explicit save, so it always
        // reflects the latest user intent. SyncWorker uses the identical order (input tag →
        // settings → export → stored profile), so the stored profile lands under exactly the
        // tag this header asks for — otherwise the nickname would be filed under a tag the
        // header never displays and would silently fall back to the placeholder.
        val profile = StoredProfile("#2PP", "Alice", 16, 1_000L, null)
        val state = PlayerIdentity.headerState(
            exportTag = "#2RR", exportName = null, exportTownHall = 15,
            settingsTag = "#2PP", profile = profile,
        )
        assertEquals("#2PP", state.expectedTag)
        assertEquals("Alice", state.displayName)
        assertEquals(true, state.profileMatches)
        // The export still contributes its town hall for the displayed player.
        assertEquals(15, state.townHall)
    }

    @Test
    fun headerStateFallsBackToExportThenStoredProfileInThatOrder() {
        // No settings tag: the export defines the player (same order as the sync chain).
        val fromExport = PlayerIdentity.headerState(
            exportTag = "#2RR", exportName = null, exportTownHall = 0,
            settingsTag = null, profile = StoredProfile("#2RR", "Bob", 14, 2_000L, null),
        )
        assertEquals("#2RR", fromExport.expectedTag)
        assertEquals("Bob", fromExport.displayName)
        // No settings, no export: only the stored profile identifies the player.
        val fromProfile = PlayerIdentity.headerState(
            exportTag = null, exportName = null, exportTownHall = 0,
            settingsTag = "bad tag", profile = StoredProfile("#2PP", "Alice", 16, 1_000L, null),
        )
        assertEquals("#2PP", fromProfile.expectedTag)
        assertEquals("Alice", fromProfile.displayName)
    }

    @Test
    fun headerStateSurfacesErrorsAttributedToTheDisplayedTagOnly() {
        // Error recorded while syncing the displayed tag → visible, even alongside a name.
        val errored = StoredProfile("#2PP", "Alice", 16, 1_000L, "API rate limit reached. Try later.", "#2PP")
        val matched = PlayerIdentity.headerState("#2PP", "", 0, "#2PP", errored)
        assertEquals("API rate limit reached. Try later.", matched.apiError)

        // The header now displays another player → the old player's error stays hidden.
        val other = PlayerIdentity.headerState("#2RR", "", 0, "#2RR", errored)
        assertEquals("#2RR", other.expectedTag)
        assertEquals("", other.apiError)

        // Sync failed for the displayed tag before any profile existed → error still shown
        // instead of a misleading generic "not synced".
        val failedSync = StoredProfile(null, null, -1, 0L, "Player not found. Check the tag.", "#2RR")
        val shown = PlayerIdentity.headerState(null, null, 0, "#2RR", failedSync)
        assertEquals("Player not found. Check the tag.", shown.apiError)
        assertEquals(false, shown.profileMatches)

        // Unattributed error (no tag could be resolved, e.g. settings missing) is always shown.
        val unattributed = StoredProfile(null, null, -1, 0L, "Add your Player Tag and API token in app settings.", null)
        assertEquals(
            "Add your Player Tag and API token in app settings.",
            PlayerIdentity.headerState(null, null, 0, null, unattributed).apiError,
        )

        // No error stored → nothing is shown for any tag.
        assertEquals("", PlayerIdentity.headerState("#2PP", null, 0, "#2PP", errored.copy(syncError = null, syncErrorTag = null)).apiError)

        // Unattributed legacy error attached to another player's profile stays on that player.
        val legacy = StoredProfile("#2PP", "Alice", 16, 1_000L, "API down")
        assertEquals("", PlayerIdentity.headerState("#2RR", "", 0, "#2RR", legacy).apiError)
    }
}
