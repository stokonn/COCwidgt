package dev.stokonn.cocwidgt.data

import org.json.JSONObject
import java.io.IOException
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
        val canonicalRequestedTag = PlayerIdentity.normalizeTag(requestedTag)
            ?: throw IllegalArgumentException("Enter a valid Player Tag.")
        val json = runCatching { JSONObject(body) }
            .getOrElse { throw IllegalStateException("Clash API returned an unreadable response.") }
        if (!json.has("tag")) throw IllegalStateException("Clash API response is missing the Player Tag.")
        val responseTag = PlayerIdentity.normalizeTag(json.optString("tag", canonicalRequestedTag))
            ?: throw IllegalStateException("Clash API returned an invalid Player Tag.")
        if (responseTag != canonicalRequestedTag) throw IllegalStateException("Clash API response does not match the requested Player Tag.")
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

class ApiException(val status: Int, message: String) : IOException(message)

/** Official player-profile lookup. Callers must run this operation off the main thread. */
class ClashApi {
    fun fetchPlayer(tag: String, developerApiKey: String): PlayerSnapshot {
        val normalized = PlayerIdentity.normalizeTag(tag) ?: throw IllegalArgumentException("Enter a valid Player Tag.")
        require(developerApiKey.isNotBlank()) { "Developer API key is not configured." }
        val encoded = URLEncoder.encode(normalized.removePrefix("#"), Charsets.UTF_8.name())
        val connection = (URL("https://api.clashofclans.com/v1/players/%23$encoded").openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $developerApiKey")
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val detail = runCatching { JSONObject(body).optString("message") }.getOrNull()
                throw ApiException(code, detail?.takeIf { it.isNotBlank() } ?: "HTTP $code")
            }
            return PlayerProfileDto.parse(body, normalized)
        } finally {
            connection.disconnect()
        }
    }
}

/** Safe user-facing classification; exception text and credentials are never exposed. */
object ApiFailureMessage {
    fun forError(error: Throwable): String = when (error) {
        is ApiException -> when (error.status) {
            400 -> "Invalid player tag or request."
            401 -> "Developer API key rejected. Check the key in settings."
            403 -> "API key access denied. Check its allowed public IP address."
            404 -> "Player not found for this tag."
            429 -> "API rate limit reached. Try again later."
            in 500..599 -> "Clash API server error. Village data remains available offline."
            else -> "Clash API request failed (${error.status})."
        }
        is java.net.UnknownHostException, is java.net.SocketTimeoutException, is IOException ->
            "Network unavailable or timed out. Village data remains available offline."
        else -> "Player name lookup failed. Village data remains available offline."
    }
}
