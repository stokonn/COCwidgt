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
}
