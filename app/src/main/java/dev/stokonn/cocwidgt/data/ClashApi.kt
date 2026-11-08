package dev.stokonn.cocwidgt.data

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class PlayerSnapshot(
    val name: String,
    val tag: String,
    val townHall: Int?,
    val expLevel: Int?,
    val trophies: Int?,
    val bestTrophies: Int?,
    val warStars: Int?,
    val heroes: List<String>,
    val syncedAt: Long
)

class ClashApi(private val context: Context) {
    fun fetchPlayer(tag: String, token: String): PlayerSnapshot {
        val requestedTag = PlayerIdentity.normalizeTag(tag) ?: throw IllegalArgumentException("Enter a valid Player Tag.")
        val encodedTag = URLEncoder.encode(requestedTag.removePrefix("#"), "UTF-8")
        val connection = (URL("https://api.clashofclans.com/v1/players/%23$encodedTag").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
        }
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val detail = runCatching { JSONObject(body).optString("message") }.getOrNull()
                throw ApiException(status, detail?.takeIf { it.isNotBlank() } ?: "HTTP $status")
            }
            val json = JSONObject(body)
            val responseTag = PlayerIdentity.normalizeTag(json.optString("tag", requestedTag))
                ?: throw IllegalStateException("Clash API returned an invalid Player Tag.")
            if (responseTag != requestedTag) throw IllegalStateException("Clash API response does not match the requested Player Tag.")
            val heroes = buildList {
                val array = json.optJSONArray("heroes") ?: return@buildList
                for (i in 0 until array.length()) {
                    val hero = array.optJSONObject(i) ?: continue
                    val heroName = hero.optString("name")
                    val level = hero.optInt("level", -1)
                    if (heroName.isNotBlank() && level >= 0) add("$heroName · $level")
                }
            }
            return PlayerSnapshot(
                name = json.optString("name").trim().takeUnless { it.equals("null", ignoreCase = true) }.orEmpty(),
                tag = responseTag,
                townHall = json.takeIf { it.has("townHallLevel") }?.optInt("townHallLevel"),
                expLevel = json.takeIf { it.has("expLevel") }?.optInt("expLevel"),
                trophies = json.takeIf { it.has("trophies") }?.optInt("trophies"),
                bestTrophies = json.takeIf { it.has("bestTrophies") }?.optInt("bestTrophies"),
                warStars = json.takeIf { it.has("warStars") }?.optInt("warStars"),
                heroes = heroes,
                syncedAt = System.currentTimeMillis()
            )
        } finally {
            connection.disconnect()
        }
    }

    fun saveSnapshot(snapshot: PlayerSnapshot) {
        val normalizedTag = PlayerIdentity.normalizeTag(snapshot.tag)
            ?: throw IllegalArgumentException("Cannot save a profile without a valid Player Tag.")
        context.getSharedPreferences("cocwidgt_player", Context.MODE_PRIVATE).edit()
            .putString("name", snapshot.name.trim())
            .putString("tag", normalizedTag)
            .putInt("town_hall", snapshot.townHall ?: -1)
            .putInt("exp_level", snapshot.expLevel ?: -1)
            .putInt("trophies", snapshot.trophies ?: -1)
            .putInt("best_trophies", snapshot.bestTrophies ?: -1)
            .putInt("war_stars", snapshot.warStars ?: -1)
            .putString("heroes", snapshot.heroes.joinToString("|"))
            .putLong("synced_at", snapshot.syncedAt)
            .putString("sync_error", "")
            .apply()
    }

    fun saveError(message: String) {
        context.getSharedPreferences("cocwidgt_player", Context.MODE_PRIVATE).edit()
            .putString("sync_error", message.take(160)).apply()
    }
}
class ApiException(val status: Int, message: String) : Exception(message)
