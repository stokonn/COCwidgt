package dev.stokonn.cocwidgt.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs

/**
 * One running upgrade.
 *
 * [startedAt] is the earliest moment this finish window is known for: the export timestamp of the
 * first import that observed the upgrade running. Re-imports keep that first observation (see
 * [VillageSnapshot.withMergedTimers]) so progress never jumps backwards. [remainingAtExportMs] is
 * the remaining time measured at [startedAt], therefore the absolute expected completion is
 * [finishAt] = [startedAt] + [remainingAtExportMs] and stays valid across widget refreshes.
 *
 * [goblin] is tri-state because the export marks it explicitly: true/false when the export row
 * carries the flag, null when the export does not say (section not applicable or flag absent).
 */
data class VillageJob(
    val id: String,
    val name: String,
    val section: String,
    val level: Int,
    val startedAt: Long,
    val remainingAtExportMs: Long,
    val goblin: Boolean? = null,
    val dataId: Long = -1,
    val iconKey: String = "unknown",
    val totalDurationMs: Long? = null
) {
    fun finishAt(): Long = startedAt + remainingAtExportMs

    fun remainingMs(now: Long = System.currentTimeMillis()): Long = (finishAt() - now).coerceAtLeast(0L)

    fun isComplete(now: Long = System.currentTimeMillis()): Boolean = remainingMs(now) <= 0L

    /**
     * Progress from the first known start to the expected completion, clamped to 0..1.
     * Returns null when the timestamps are missing or invalid so no fake percentage is shown.
     */
    fun progress(now: Long = System.currentTimeMillis()): Float? {
        val start = startedAt
        val finish = finishAt()
        if (start <= 0L || finish <= start) return null
        return ((now - start).toFloat() / (finish - start).toFloat()).coerceIn(0f, 1f)
    }

    fun countdown(now: Long = System.currentTimeMillis()): String {
        val m = (remainingMs(now) + 59_999L) / 60_000L
        if (m <= 0) return "Ready"
        val d = m / 1440; val h = (m % 1440) / 60; val min = m % 60
        return buildString { if (d > 0) append(d).append("d "); if (h > 0) append(h).append("h "); append(min).append("m") }
    }

    val category: UpgradeCategory get() = UpgradeCategory.of(this)
}

data class VillageSnapshot(val tag: String, val name: String, val townHall: Int, val builders: Int, val exportedAt: Long, val jobs: List<VillageJob>) {
    fun save(context: Context) {
        val array = JSONArray()
        jobs.forEach { job -> array.put(JSONObject().apply {
            put("id", job.id); put("name", job.name); put("section", job.section); put("level", job.level)
            put("startedAt", job.startedAt); put("durationMs", job.remainingAtExportMs)
            if (job.goblin != null) put("goblin", job.goblin)
            put("dataId", job.dataId); put("iconKey", job.iconKey); put("totalDurationMs", job.totalDurationMs)
        }) }
        val normalizedTag = requireNotNull(PlayerIdentity.normalizeTag(tag)) { "Cannot save a village without a valid Player Tag." }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("tag", normalizedTag).putString("name", name.trim())
            .putInt("townHall", townHall).putInt("builders", builders).putLong("exportedAt", exportedAt)
            .putString("jobs", array.toString()).apply()
    }

    /**
     * Keeps progress bars honest across re-imports: when a new export shows the same still-running
     * upgrade (same stable data ID, expected completion within [TIMER_MATCH_TOLERANCE_MS]), the
     * first observed start is carried over instead of resetting progress to zero. A changed timer
     * or a finished upgrade starts a fresh window. A different player tag never merges.
     */
    fun withMergedTimers(previous: VillageSnapshot?): VillageSnapshot {
        if (previous == null || previous.tag != tag) return this
        val prior = previous.jobs.filter { it.startedAt > 0L }
        if (prior.isEmpty()) return this
        val merged = jobs.map { job ->
            val match = job.takeIf { it.startedAt > 0L }?.let { fresh ->
                prior.firstOrNull { old ->
                    old.dataId >= 0 && old.dataId == fresh.dataId &&
                        abs(old.finishAt() - fresh.finishAt()) <= TIMER_MATCH_TOLERANCE_MS &&
                        old.startedAt < fresh.startedAt
                }
            }
            if (match == null) job else job.copy(
                startedAt = match.startedAt,
                // Keep the freshly observed absolute deadline; only the window start moves.
                remainingAtExportMs = job.finishAt() - match.startedAt,
                goblin = job.goblin ?: match.goblin
            )
        }
        return copy(jobs = merged)
    }

    companion object {
        const val PREFS = "cocwidgt_village"
        private const val TIMER_MATCH_TOLERANCE_MS = 15 * 60 * 1000L

        fun load(context: Context): VillageSnapshot? {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val tag = PlayerIdentity.normalizeTag(prefs.getString("tag", null)) ?: return null
            val array = runCatching { JSONArray(prefs.getString("jobs", "[]")) }.getOrNull() ?: JSONArray()
            val jobs = (0 until array.length()).mapNotNull { i -> runCatching {
                val j = array.getJSONObject(i)
                VillageJob(j.getString("id"), j.getString("name"), j.getString("section"), j.optInt("level"),
                    j.getLong("startedAt"), j.getLong("durationMs"),
                    if (j.has("goblin") && !j.isNull("goblin")) j.optBoolean("goblin") else null,
                    j.optLong("dataId", -1), j.optString("iconKey", "unknown"),
                    j.takeIf { it.has("totalDurationMs") && !it.isNull("totalDurationMs") }?.optLong("totalDurationMs"))
            }.getOrNull() }
            return VillageSnapshot(tag, prefs.getString("name", "").orEmpty(), prefs.getInt("townHall", 0),
                prefs.getInt("builders", 0), prefs.getLong("exportedAt", 0), jobs)
        }
    }
}

object VillageExportParser {
    private val sections = listOf(
        "buildings" to "Builders", "traps" to "Builders", "heroes" to "Builders",
        "units" to "Laboratory", "siege_machines" to "Laboratory", "spells" to "Laboratory",
        "equipment" to "Equipment", "hero_equipment" to "Equipment", "pets" to "Pets",
        "guardians" to "Guardians",
        "buildings2" to "Builder Base", "traps2" to "Builder Base", "heroes2" to "Builder Base", "units2" to "Star Laboratory"
    )

    // The export only attaches the Goblin Worker flag to Home Village upgrades, and never to
    // pets or equipment rows (mirrors how the flag is defined in the export schema).
    private val goblinExemptSections = setOf("pets", "equipment", "hero_equipment")

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
                val goblin = if (key.endsWith("2") || key in goblinExemptSections) null else parseGoblinFlag(item.opt("extra"))
                // The ID is authoritative. Export-provided display text may be absent or stale.
                // Valid upgrades are kept even when their section or entity is unknown.
                jobs += VillageJob("$key:$i", entity.name, actualSection, item.optInt("lvl") + 1,
                    stamp, secondsRemaining * 1000L, goblin, dataId, entity.iconKey)
            }
        }
        require(jobs.size <= 300) { "Export contains too many active upgrades." }
        return VillageSnapshot(tag, root.optString("name").trim().takeUnless { it.equals("null", ignoreCase = true) }.orEmpty(), hall, builders, stamp, jobs)
    }

    /**
     * Goblin Worker marker: true/false for the boolean and 0/1 forms used by the export,
     * null when the flag is absent or unreadable (unknown, never guessed).
     */
    private fun parseGoblinFlag(value: Any?): Boolean? = when (value) {
        null, JSONObject.NULL -> null
        is Boolean -> value
        is Number -> when (value.toInt()) { 1 -> true; 0 -> false; else -> null }
        is String -> when (value.trim().lowercase(Locale.ROOT)) {
            "true" -> true
            "false" -> false
            else -> null
        }
        else -> null
    }

    private fun findLevel(array: JSONArray, id: Long): Int = (0 until array.length()).firstNotNullOfOrNull { i ->
        array.optJSONObject(i)?.takeIf { it.optLong("data") == id }?.optInt("lvl")
    } ?: 0
    private fun findCount(array: JSONArray, id: Long): Int = (0 until array.length()).sumOf { i ->
        array.optJSONObject(i)?.takeIf { it.optLong("data") == id }?.optInt("cnt", 1) ?: 0
    }
}
