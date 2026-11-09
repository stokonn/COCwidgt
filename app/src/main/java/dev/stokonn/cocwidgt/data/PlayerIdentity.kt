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
     * the same identity rules: the export, the saved settings tag and the stored API profile.
     *
     * [PlayerHeaderState.displayName] is the stored API nickname for the matching Player Tag,
     * the export name when it carries one, and [PlayerHeaderState.FALLBACK_NAME] otherwise —
     * a nickname from another player's profile is never displayed, and an incomplete stored
     * profile (no valid tag) never contributes a name at all.
     */
    fun headerState(
        exportTag: String?,
        exportName: String?,
        exportTownHall: Int,
        settingsTag: String?,
        profile: StoredProfile,
    ): PlayerHeaderState {
        val expectedTag = firstValidTag(exportTag, settingsTag, profile.tag)
        val profileMatches = expectedTag != null && profile.tag == expectedTag
        val displayName = matchingName(
            expectedTag, profile.tag, profile.name, exportTag, exportName
        ) ?: PlayerHeaderState.FALLBACK_NAME
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
            apiError = if (profileMatches) profile.syncError.orEmpty() else "",
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

/** Stored API profile as restored from preferences. */
data class StoredProfile(
    val tag: String?,
    val name: String?,
    val townHall: Int,
    val syncedAt: Long,
    val syncError: String?,
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
            SYNC_ERROR_KEY to "",
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
        return StoredProfile(
            tag = tag,
            name = name,
            townHall = (values[TOWN_HALL_KEY] as? Number)?.toInt() ?: -1,
            syncedAt = (values[SYNCED_AT_KEY] as? Number)?.toLong() ?: 0L,
            syncError = (values[SYNC_ERROR_KEY] as? String)?.takeIf { it.isNotBlank() },
        )
    }
}
