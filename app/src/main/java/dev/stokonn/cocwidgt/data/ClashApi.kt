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

/**
 * Pure mapping of the Clash API `/players/{tag}` response body to a [PlayerSnapshot].
 * Free of networking so nickname resolution and the strict tag checks are unit-testable.
 *
 * The response tag must match the requested one — a nickname from a different player's
 * profile can never be captured here.
 */
object PlayerProfileDto {
    fun parse(body: String, requestedTag: String, syncedAt: Long = System.currentTimeMillis()): PlayerSnapshot {
        val json = runCatching { JSONObject(body) }
            .getOrElse { throw IllegalStateException("Clash API returned an unreadable response.") }
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
            name = PlayerIdentity.usableName(json.optString("name")).orEmpty(),
            tag = responseTag,
            townHall = json.takeIf { it.has("townHallLevel") }?.optInt("townHallLevel"),
            expLevel = json.takeIf { it.has("expLevel") }?.optInt("expLevel"),
            trophies = json.takeIf { it.has("trophies") }?.optInt("trophies"),
            bestTrophies = json.takeIf { it.has("bestTrophies") }?.optInt("bestTrophies"),
            warStars = json.takeIf { it.has("warStars") }?.optInt("warStars"),
            heroes = heroes,
            syncedAt = syncedAt,
        )
    }
}

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
            return PlayerProfileDto.parse(body, requestedTag)
        } finally {
            connection.disconnect()
        }
    }

    fun saveSnapshot(snapshot: PlayerSnapshot) {
        // Pure codec: throws for an invalid tag instead of persisting a mislabelled profile.
        val values = PlayerProfileCodec.encode(snapshot)
        val editor = context.getSharedPreferences(PlayerProfileCodec.PREFS, Context.MODE_PRIVATE).edit()
        values.forEach { (key, value) ->
            when (value) {
                is String -> editor.putString(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                else -> throw IllegalArgumentException("Unsupported profile value for '$key'.")
            }
        }
        editor.apply()
    }

    fun saveError(message: String) {
        context.getSharedPreferences(PlayerProfileCodec.PREFS, Context.MODE_PRIVATE).edit()
            .putString(PlayerProfileCodec.SYNC_ERROR_KEY, message.take(160)).apply()
    }
}
class ApiException(val status: Int, message: String) : Exception(message)
