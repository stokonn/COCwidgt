package dev.stokonn.cocwidgt.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountDirectoryTest {
    private val empty = AccountDirectoryState(emptyList(), null)

    @Test fun createsAccountsAndRejectsDuplicateNormalizedTags() {
        val first = AccountDirectory.add(empty, "y2099vgpj", "Main")
        assertEquals("#Y2099VGPJ", first.accounts.single().tag)
        assertEquals("#Y2099VGPJ", first.activeTag)
        assertEquals(first, AccountDirectory.add(first, "#y2099vgpj", "Alt"))
        assertThrows(IllegalArgumentException::class.java) { AccountDirectory.add(first, "bad tag", "Bad") }
    }

    @Test fun renameAndSwitchKeepTagsStable() {
        val state = AccountDirectory.add(AccountDirectory.add(empty, "#Y2099VGPJ", "Main"), "#P0LY9Q2V", "Alt")
        val renamed = AccountDirectory.rename(state, "#P0LY9Q2V", "Farm")
        assertEquals("Farm", renamed.accounts.last().label)
        assertEquals("#P0LY9Q2V", AccountDirectory.select(renamed, "#P0LY9Q2V").activeTag)
    }

    @Test fun addingAnotherAccountDoesNotChangeTheActiveAccount() {
        val main = AccountDirectory.add(empty, "#Y2099VGPJ", "Main")
        val withAlt = AccountDirectory.add(main, "#P0LY9Q2V", "Alt")
        assertEquals("#Y2099VGPJ", withAlt.activeTag)
        assertEquals(listOf("#Y2099VGPJ", "#P0LY9Q2V"), withAlt.accounts.map { it.tag })
    }

    @Test fun deletingActiveAccountSelectsAnotherAndDeletingLastLeavesNoActiveAccount() {
        val state = AccountDirectory.add(AccountDirectory.add(empty, "#Y2099VGPJ", "Main"), "#P0LY9Q2V", "Alt")
        val afterDelete = AccountDirectory.remove(state, "#P0LY9Q2V")
        assertEquals("#Y2099VGPJ", afterDelete.activeTag)
        assertEquals(1, afterDelete.accounts.size)
        val none = AccountDirectory.remove(afterDelete, "#Y2099VGPJ")
        assertTrue(none.accounts.isEmpty())
        assertEquals(null, none.activeTag)
    }

    @Test fun directoryCodecIgnoresBadAndDuplicateTagsAndWidgetKeysArePerInstance() {
        val decoded = AccountDirectory.decode("#Y2099VGPJ|bad tag|#y2099vgpj|#P0LY9Q2V", mapOf("label:#P0LY9Q2V" to "Alt"))
        assertEquals(listOf("#Y2099VGPJ", "#P0LY9Q2V"), decoded.map { it.tag })
        assertEquals("Alt", decoded.last().label)
        assertEquals("widget:10", WidgetAccountStore.key(10))
        assertEquals("widget:11", WidgetAccountStore.key(11))
        assertFalse(WidgetAccountStore.key(10) == WidgetAccountStore.key(11))
        assertFalse(VillageSnapshot.prefsName("#Y2099VGPJ") == VillageSnapshot.prefsName("#P0LY9Q2V"))
        assertFalse(PlayerProfileCodec.prefsName("#Y2099VGPJ") == PlayerProfileCodec.prefsName("#P0LY9Q2V"))
    }

    @Test fun legacyMigrationUsesTheFirstValidIdentity() {
        assertEquals("#P0LY9Q2V", AccountMigration.legacyTag("invalid!", "p0ly9q2v", "#Y2099VGPJ"))
        assertEquals("#Y2099VGPJ", AccountMigration.legacyTag(null, null, " y2099vgpj "))
        assertEquals(null, AccountMigration.legacyTag(null, "bad tag", null))
        val legacy = mapOf<String, Any>("tag" to "#Y2099VGPJ", "name" to "Main", "townHall" to 19, "jobs" to "[]")
        assertEquals(legacy, AccountMigration.copyableValues(legacy))
    }

    @Test fun removedWidgetAccountFallsBackToActiveAccountAndNeverToRemovedData() {
        assertEquals("#Y2099VGPJ", WidgetAccountStore.resolve("#Y2099VGPJ", "#P0LY9Q2V", setOf("#Y2099VGPJ", "#P0LY9Q2V")))
        assertEquals("#P0LY9Q2V", WidgetAccountStore.resolve("#Y2099VGPJ", "#P0LY9Q2V", setOf("#P0LY9Q2V")))
        assertEquals(null, WidgetAccountStore.resolve("#Y2099VGPJ", null, emptySet()))
    }
}
