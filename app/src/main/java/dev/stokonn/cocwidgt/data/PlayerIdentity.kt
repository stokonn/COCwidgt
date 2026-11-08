package dev.stokonn.cocwidgt.data

import java.util.Locale

/** Canonical player identity shared by imports, API sync, preferences and widget rendering. */
object PlayerIdentity {
    fun normalizeTag(value: String?): String? {
        val compact = value.orEmpty().trim().uppercase(Locale.ROOT)
        val body = compact.removePrefix("#")
        return body.takeIf { it.matches(Regex("[A-Z0-9]{3,20}")) }?.let { "#$it" }
    }

    fun matchingName(
        expectedTag: String?, apiTag: String?, apiName: String?, exportTag: String?, exportName: String?
    ): String? {
        val expected = normalizeTag(expectedTag) ?: return null
        val api = if (normalizeTag(apiTag) == expected) apiName.cleanName() else null
        if (api != null) return api
        return if (normalizeTag(exportTag) == expected) exportName.cleanName() else null
    }

    private fun String?.cleanName(): String? = this?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
}
