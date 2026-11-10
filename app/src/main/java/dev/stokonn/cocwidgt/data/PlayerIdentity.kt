package dev.stokonn.cocwidgt.data

import java.util.Locale

/** Canonical player identity shared by imports, API sync, preferences and widget rendering. */
object PlayerIdentity {
    fun firstValidTag(vararg values: String?): String? = values.firstNotNullOfOrNull(::normalizeTag)

    fun normalizeTag(value: String?): String? {
        val compact = value.orEmpty().trim().uppercase(Locale.ROOT)
        val body = compact.removePrefix("#")
        return body.takeIf { it.matches(Regex("[A-Z0-9]{3,20}")) }?.let { "#$it" }
    }

    /**
     * A display name is usable only when it is a real value: non-blank and not the JSON
     * `null` literal some sources produce instead of an absent field.
     */
    fun usableName(value: String?): String? = value?.trim()?.takeIf {
        it.isNotEmpty() && !it.equals("null", ignoreCase = true)
    }

    fun matchingName(
        expectedTag: String?, apiTag: String?, apiName: String?, exportTag: String?, exportName: String?
    ): String? {
        val expected = normalizeTag(expectedTag) ?: return null
        val api = if (normalizeTag(apiTag) == expected) usableName(apiName) else null
        if (api != null) return api
        return if (normalizeTag(exportTag) == expected) usableName(exportName) else null
    }

    /**
     * Everything the player header (activity card and Glance widget) needs, resolved once from
     * the same identity rules the sync uses: the saved settings tag first (it is written on
     * every import and on every explicit save, so it always reflects the latest user intent),
     * then the imported export, then the stored API profile. Using one identical chain for the
     * header and for [dev.stokonn.cocwidgt.sync.SyncWorker] is what guarantees the stored
     * profile is filed under exactly the tag the header expects — otherwise the nickname can
     * silently belong to a tag the header never asks for and falls back to the placeholder.
     *
     * [PlayerHeaderState.displayName] is the stored API nickname for the matching Player Tag,
     * the export name when it carries one, and [PlayerHeaderState.FALLBACK_NAME] otherwise —
     * a nickname from another player's profile is never displayed, and an incomplete stored
     * profile (no valid tag) never contributes a name at all.
     *
     * [PlayerHeaderState.apiError] surfaces the last sync failure whenever it belongs to the
     * tag on display — attributed by [StoredProfile.syncErrorTag], or unattributed while no
     * stored profile claims a different player — so a failed sync is visible instead of being
     * hidden behind a stale "not synced" state.
     */
    fun headerState(
        exportTag: String?,
        exportName: String?,
        exportTownHall: Int,
        settingsTag: String?,
        profile: StoredProfile,
    ): PlayerHeaderState {
        val expectedTag = firstValidTag(settingsTag, exportTag, profile.tag)
        val profileMatches = expectedTag != null && profile.tag == expectedTag
        val displayName = matchingName(
            expectedTag, profile.tag, profile.name, exportTag, exportName
        ) ?: PlayerHeaderState.FALLBACK_NAME
        val storedError = profile.syncError?.trim().orEmpty()
        val errorVisible = storedError.isNotEmpty() && when {
            profileMatches -> true
            // Attributed: only the header for that exact Player Tag shows it.
            profile.syncErrorTag != null -> profile.syncErrorTag == expectedTag
            // Unattributed (legacy record or no tag resolved at all): shown only while no
            // stored profile claims a different player — so a global "add your settings"
            // error is never swallowed, but another player's legacy failure stays hidden.
            else -> profile.tag == null
        }
        return PlayerHeaderState(
            displayName = displayName,
            expectedTag = expectedTag,
            townHall = when {
                exportTownHall > 0 -> exportTownHall
                profileMatches && profile.townHall > 0 -> profile.townHall
                else -> -1
            },
            profileMatches = profileMatches,
            apiSyncedAt = if (profileMatches) profile.syncedAt else 0L,
            apiError = if (errorVisible) storedError else "",
        )
    }
}

/** Resolved identity shown by the player header; see [PlayerIdentity.headerState]. */
data class PlayerHeaderState(
    val displayName: String,
    val expectedTag: String?,
    val townHall: Int,
    val profileMatches: Boolean,
    val apiSyncedAt: Long,
    val apiError: String,
) {
    companion object {
        /** Only used when no valid name exists — never replaced by a Player Tag posing as a name. */
        const val FALLBACK_NAME = "Player profile"
        const val NO_TAG_LABEL = "ADD PLAYER TAG"
    }
}

/**
 * Stored API profile as restored from preferences. [syncErrorTag] attributes the last
 * [syncError] to the Player Tag the failing sync ran for, so an error recorded for one player
 * is never shown on another player's header.
 */
data class StoredProfile(
    val tag: String?,
    val name: String?,
    val townHall: Int,
    val syncedAt: Long,
    val syncError: String?,
    val syncErrorTag: String? = null,
)

/**
 * Pure mapping between [PlayerSnapshot] and the "cocwidgt_player" preference entries.
 * Kept free of Android types so persistence and restoration — including legacy keys written by
 * older releases — are covered by unit tests.
 */
object PlayerProfileCodec {
    const val PREFS = "cocwidgt_player"

    // Legacy key written by the first widget release; still honoured on read.
    private const val LEGACY_TAG_KEY = "player_tag"
    private const val TAG_KEY = "tag"
    private const val NAME_KEY = "name"
    private const val TOWN_HALL_KEY = "town_hall"
    private const val EXP_LEVEL_KEY = "exp_level"
    private const val TROPHIES_KEY = "trophies"
    private const val BEST_TROPHIES_KEY = "best_trophies"
    private const val WAR_STARS_KEY = "war_stars"
    private const val HEROES_KEY = "heroes"
    private const val SYNCED_AT_KEY = "synced_at"
    const val SYNC_ERROR_KEY = "sync_error"
    const val SYNC_ERROR_TAG_KEY = "sync_error_tag"

    fun encode(snapshot: PlayerSnapshot): Map<String, Any> {
        val tag = PlayerIdentity.normalizeTag(snapshot.tag)
            ?: throw IllegalArgumentException("Cannot save a profile without a valid Player Tag.")
        return mapOf(
            NAME_KEY to snapshot.name.trim(),
            TAG_KEY to tag,
            TOWN_HALL_KEY to (snapshot.townHall ?: -1),
            EXP_LEVEL_KEY to (snapshot.expLevel ?: -1),
            TROPHIES_KEY to (snapshot.trophies ?: -1),
            BEST_TROPHIES_KEY to (snapshot.bestTrophies ?: -1),
            WAR_STARS_KEY to (snapshot.warStars ?: -1),
            HEROES_KEY to snapshot.heroes.joinToString("|"),
            SYNCED_AT_KEY to snapshot.syncedAt,
            // A successful sync clears any previous failure and its attribution.
            SYNC_ERROR_KEY to "",
            SYNC_ERROR_TAG_KEY to "",
        )
    }

    /**
     * Restores the stored profile. Tags from any era (including the unnormalized legacy
     * [LEGACY_TAG_KEY]) are canonicalized here; a name is only restored together with a valid
     * tag, so an incomplete profile can never surface a nickname for the wrong player.
     */
    fun decode(values: Map<String, Any?>): StoredProfile {
        val tag = PlayerIdentity.firstValidTag(values[TAG_KEY] as? String, values[LEGACY_TAG_KEY] as? String)
        val name = if (tag == null) null else PlayerIdentity.usableName(values[NAME_KEY] as? String)
        val syncError = (values[SYNC_ERROR_KEY] as? String)?.takeIf { it.isNotBlank() }
        // A stored error without a parseable attribution tag degrades to "unattributed",
        // which [PlayerIdentity.headerState] shows instead of hiding forever.
        val syncErrorTag = if (syncError == null) {
            null
        } else {
            PlayerIdentity.normalizeTag(values[SYNC_ERROR_TAG_KEY] as? String)
        }
        return StoredProfile(
            tag = tag,
            name = name,
            townHall = (values[TOWN_HALL_KEY] as? Number)?.toInt() ?: -1,
            syncedAt = (values[SYNCED_AT_KEY] as? Number)?.toLong() ?: 0L,
            syncError = syncError,
            syncErrorTag = syncErrorTag,
        )
    }
}
