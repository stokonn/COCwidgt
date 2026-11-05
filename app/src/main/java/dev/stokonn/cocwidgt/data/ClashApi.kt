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
        val encodedTag = URLEncoder.encode(tag.removePrefix("#"), "UTF-8")
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
                name = json.optString("name", "Unknown"),
                tag = json.optString("tag", tag),
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
        context.getSharedPreferences("cocwidgt_player", Context.MODE_PRIVATE).edit()
            .putString("name", snapshot.name)
            .putString("tag", snapshot.tag)
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
