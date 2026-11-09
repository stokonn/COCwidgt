package dev.stokonn.cocwidgt.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Clash API profile responses: field names are taken from the real `/players/{tag}` schema and
 * the requested tag must match the response tag, so a nickname can never bind to the wrong player.
 */
class ClashApiDtoTest {

    private val body = """{"tag":"#Y2099VGPJ","name":"Alice","townHallLevel":16,"expLevel":200,
        "trophies":4300,"bestTrophies":4500,"warStars":120,
        "heroes":[{"name":"Barbarian King","level":95},{"name":"Archer Queen","level":93}]}"""

    @Test fun parsesTheRealApiResponseFields() {
        val snapshot = PlayerProfileDto.parse(body, "#Y2099VGPJ", syncedAt = 1_000L)
        assertEquals("Alice", snapshot.name)
        assertEquals("#Y2099VGPJ", snapshot.tag)
        assertEquals(16, snapshot.townHall)
        assertEquals(200, snapshot.expLevel)
        assertEquals(4300, snapshot.trophies)
        assertEquals(4500, snapshot.bestTrophies)
        assertEquals(120, snapshot.warStars)
        assertEquals(listOf("Barbarian King · 95", "Archer Queen · 93"), snapshot.heroes)
        assertEquals(1_000L, snapshot.syncedAt)
    }

    @Test fun responseTagMustMatchTheRequestedPlayer() {
        assertThrows(IllegalStateException::class.java) {
            PlayerProfileDto.parse("""{"tag":"#OTHER123","name":"Bob"}""", "#Y2099VGPJ")
        }
        // Tag case and spacing are canonicalized before the comparison, never loosened.
        val normalized = PlayerProfileDto.parse("""{"tag":" y2099vgpj ","name":"Alice"}""", "#Y2099VGPJ")
        assertEquals("#Y2099VGPJ", normalized.tag)
    }

    @Test fun missingOrJsonNullNameBecomesAnEmptyNickname() {
        assertEquals("", PlayerProfileDto.parse("""{"tag":"#Y2099VGPJ"}""", "#Y2099VGPJ").name)
        assertEquals("", PlayerProfileDto.parse("""{"tag":"#Y2099VGPJ","name":null}""", "#Y2099VGPJ").name)
        assertEquals("", PlayerProfileDto.parse("""{"tag":"#Y2099VGPJ","name":"   "}""", "#Y2099VGPJ").name)
    }

    @Test fun unreadableOrInvalidTagResponsesFailInsteadOfGuessingAProfile() {
        assertThrows(IllegalStateException::class.java) {
            PlayerProfileDto.parse("<html>proxy error</html>", "#Y2099VGPJ")
        }
        assertThrows(IllegalStateException::class.java) {
            PlayerProfileDto.parse("""{"tag":"bad tag!","name":"Alice"}""", "#Y2099VGPJ")
        }
    }

    @Test fun absentOptionalFieldsStayNullInsteadOfZero() {
        val snapshot = PlayerProfileDto.parse("""{"tag":"#Y2099VGPJ","name":"Alice"}""", "#Y2099VGPJ")
        assertEquals(null, snapshot.townHall)
        assertEquals(null, snapshot.expLevel)
        assertEquals(null, snapshot.trophies)
        assertEquals(emptyList<String>(), snapshot.heroes)
    }
}
