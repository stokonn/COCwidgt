package dev.stokonn.cocwidgt.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class VillageJob(
    val id: String,
    val name: String,
    val section: String,
    val level: Int,
    val startedAt: Long,
    val remainingAtExportMs: Long,
    val goblin: Boolean = false,
    val dataId: Long = -1,
    val iconKey: String = "unknown",
    val totalDurationMs: Long? = null
) {
    // Export timer is remaining seconds at exportedAt; the deadline remains valid after import.
    fun finishAt() = startedAt + remainingAtExportMs
    fun remainingMs(now: Long = System.currentTimeMillis()) = (finishAt() - now).coerceAtLeast(0L)
    fun progress(now: Long = System.currentTimeMillis()): Float? = totalDurationMs?.takeIf { it > 0 }
        ?.let { (1f - remainingMs(now).toFloat() / it).coerceIn(0f, 1f) }
    fun countdown(now: Long = System.currentTimeMillis()): String {
        val m = (remainingMs(now) + 59_999L) / 60_000L
        if (m <= 0) return "Ready"
        val d = m / 1440; val h = (m % 1440) / 60; val min = m % 60
        return buildString { if (d > 0) append(d).append("d "); if (h > 0) append(h).append("h "); append(min).append("m") }
    }
}

data class VillageSnapshot(val tag: String, val name: String, val townHall: Int, val builders: Int, val exportedAt: Long, val jobs: List<VillageJob>) {
    fun save(context: Context) {
        val array = JSONArray()
        jobs.forEach { job -> array.put(JSONObject().apply {
            put("id", job.id); put("name", job.name); put("section", job.section); put("level", job.level)
            put("startedAt", job.startedAt); put("durationMs", job.remainingAtExportMs); put("goblin", job.goblin)
            put("dataId", job.dataId); put("iconKey", job.iconKey); put("totalDurationMs", job.totalDurationMs)
        }) }
        val normalizedTag = requireNotNull(PlayerIdentity.normalizeTag(tag)) { "Cannot save a village without a valid Player Tag." }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("tag", normalizedTag).putString("name", name.trim())
            .putInt("townHall", townHall).putInt("builders", builders).putLong("exportedAt", exportedAt)
            .putString("jobs", array.toString()).apply()
    }

    companion object {
        const val PREFS = "cocwidgt_village"
        fun load(context: Context): VillageSnapshot? {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val tag = PlayerIdentity.normalizeTag(prefs.getString("tag", null)) ?: return null
            val array = runCatching { JSONArray(prefs.getString("jobs", "[]")) }.getOrNull() ?: JSONArray()
            val jobs = (0 until array.length()).mapNotNull { i -> runCatching {
                val j = array.getJSONObject(i)
                VillageJob(j.getString("id"), j.getString("name"), j.getString("section"), j.optInt("level"),
                    j.getLong("startedAt"), j.getLong("durationMs"), j.optBoolean("goblin"), j.optLong("dataId", -1),
                    j.optString("iconKey", "unknown"), j.takeIf { it.has("totalDurationMs") && !it.isNull("totalDurationMs") }?.optLong("totalDurationMs"))
            }.getOrNull() }
            return VillageSnapshot(tag, prefs.getString("name", "").orEmpty(), prefs.getInt("townHall", 0),
                prefs.getInt("builders", 0), prefs.getLong("exportedAt", 0), jobs)
        }
    }
}

object VillageExportParser {
    private val sections = listOf("buildings" to "Builders", "traps" to "Builders", "heroes" to "Builders",
        "units" to "Laboratory", "siege_machines" to "Laboratory", "spells" to "Laboratory", "equipment" to "Equipment", "hero_equipment" to "Equipment", "pets" to "Pets",
        "buildings2" to "Builder Base", "traps2" to "Builder Base", "heroes2" to "Builder Base", "units2" to "Star Laboratory")

    fun parse(raw: String): VillageSnapshot {
        require(raw.length <= 1_000_000) { "Export is too large (maximum 1 MB)." }
        val root = runCatching { JSONObject(raw.trim()) }.getOrElse { throw IllegalArgumentException("This is not valid village export JSON.") }
        val tag = PlayerIdentity.normalizeTag(root.optString("tag"))
        require(tag != null) { "Export is missing a valid Player Tag." }
        require(root.has("timestamp")) { "Export is missing its creation timestamp." }
        val timestampSeconds = root.optLong("timestamp", -1L)
        require(timestampSeconds > 0L && timestampSeconds <= Long.MAX_VALUE / 1000L) { "Export contains an invalid timestamp." }
        val stamp = timestampSeconds * 1000L
        require(stamp <= System.currentTimeMillis() + 300_000L) { "Export timestamp is in the future." }
        val buildings = root.optJSONArray("buildings")
        require(buildings != null) { "This JSON has no buildings section and is not a village export." }
        val hall = findLevel(buildings, 1_000_001L)
        val builders = findCount(buildings, 1_000_015L).coerceIn(0, 6)
        val jobs = mutableListOf<VillageJob>()
        sections.forEach { (key, section) ->
            val array = root.optJSONArray(key) ?: return@forEach
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val secondsRemaining = item.optLong("timer", 0L)
                if (secondsRemaining <= 0) continue
                require(secondsRemaining <= 366L * 86_400L) { "Upgrade timer is outside the supported range." }
                val dataId = item.optLong("data", -1L)
                require(dataId >= 0) { "An upgrade entry is missing its stable game data ID." }
                val entity = GameCatalogue.resolve(dataId)
                val actualSection = if (entity.category == GameCategory.UNKNOWN) "Unknown item" else section
                // The ID is authoritative. Export-provided display text may be absent or stale.
                jobs += VillageJob("$key:$i", entity.name, actualSection, item.optInt("lvl") + 1,
                    stamp, secondsRemaining * 1000L, item.optBoolean("extra"), dataId, entity.iconKey)
            }
        }
        require(jobs.size <= 300) { "Export contains too many active upgrades." }
        return VillageSnapshot(tag, root.optString("name").trim().takeUnless { it.equals("null", ignoreCase = true) }.orEmpty(), hall, builders, stamp, jobs)
    }

    private fun findLevel(array: JSONArray, id: Long): Int = (0 until array.length()).firstNotNullOfOrNull { i ->
        array.optJSONObject(i)?.takeIf { it.optLong("data") == id }?.optInt("lvl")
    } ?: 0
    private fun findCount(array: JSONArray, id: Long): Int = (0 until array.length()).sumOf { i ->
        array.optJSONObject(i)?.takeIf { it.optLong("data") == id }?.optInt("cnt", 1) ?: 0
    }
}
