package dev.stokonn.cocwidgt.data

data class CachedPlayerName(val tag: String, val name: String, val fetchedAt: Long)

/** Pure tag-bound cache codec: stale identity can never resolve for a different player. */
object PlayerNameCache {
    fun encode(snapshot: PlayerSnapshot): Map<String, Any> {
        val tag = PlayerIdentity.normalizeTag(snapshot.tag)
            ?: throw IllegalArgumentException("Cannot cache a name without a valid Player Tag.")
        val name = PlayerIdentity.usableName(snapshot.name)
            ?: throw IllegalArgumentException("The official player response did not contain a name.")
        return mapOf("tag" to tag, "name" to name, "fetched_at" to snapshot.syncedAt)
    }

    fun decode(values: Map<String, Any?>): CachedPlayerName? {
        val tag = PlayerIdentity.normalizeTag(values["tag"] as? String) ?: return null
        val name = PlayerIdentity.usableName(values["name"] as? String) ?: return null
        return CachedPlayerName(tag, name, (values["fetched_at"] as? Number)?.toLong() ?: 0L)
    }

    fun nameFor(values: Map<String, Any?>, requestedTag: String?): String? {
        val expectedTag = PlayerIdentity.normalizeTag(requestedTag) ?: return null
        return decode(values)?.takeIf { it.tag == expectedTag }?.name
    }
}
