package dev.stokonn.cocwidgt.data

import android.content.Context
import android.content.SharedPreferences

data class PlayerAccount(val tag: String, val label: String)

/** Account metadata and per-widget account choices. Player tags are the stable storage key. */
class AccountStore(private val prefs: SharedPreferences) {
    fun accounts(): List<PlayerAccount> = AccountDirectory.decode(prefs.getString("tags", null), prefs.all)
    fun activeTag(): String? = PlayerIdentity.normalizeTag(prefs.getString("active_tag", null))
        ?.takeIf { tag -> accounts().any { it.tag == tag } }

    fun manualName(tag: String?): String? = PlayerIdentity.normalizeTag(tag)?.let { prefs.getString("manual:$it", null) }

    fun setManualName(tag: String, name: String) {
        val normalized = PlayerIdentity.normalizeTag(tag) ?: throw IllegalArgumentException("Enter a valid Player Tag.")
        require(accounts().any { it.tag == normalized }) { "Account not found." }
        prefs.edit().apply {
            val value = PlayerIdentity.usableName(name)
            if (value == null) remove("manual:$normalized") else putString("manual:$normalized", value)
        }.apply()
    }

    fun add(tag: String, label: String = ""): Boolean {
        val normalized = PlayerIdentity.normalizeTag(tag) ?: throw IllegalArgumentException("Enter a valid Player Tag.")
        val next = AccountDirectory.add(AccountDirectoryState(accounts(), activeTag()), normalized, label)
        if (next.accounts.size == accounts().size) return false
        write(next)
        return true
    }

    fun rename(tag: String, label: String) {
        val next = AccountDirectory.rename(AccountDirectoryState(accounts(), activeTag()), tag, label)
        write(next)
    }

    fun select(tag: String) {
        write(AccountDirectory.select(AccountDirectoryState(accounts(), activeTag()), tag))
    }

    fun remove(tag: String): String? {
        val next = AccountDirectory.remove(AccountDirectoryState(accounts(), activeTag()), tag)
        write(next)
        return next.activeTag
    }

    private fun write(state: AccountDirectoryState) {
        val edit = prefs.edit().putString("tags", state.accounts.joinToString("|") { it.tag })
            .putString("active_tag", state.activeTag)
        val tags = state.accounts.mapTo(mutableSetOf()) { it.tag }
        prefs.all.keys.filter {
            (it.startsWith("label:") && it.removePrefix("label:") !in tags) ||
                (it.startsWith("manual:") && it.removePrefix("manual:") !in tags)
        }.forEach(edit::remove)
        state.accounts.forEach { edit.putString("label:${it.tag}", it.label) }
        edit.apply()
    }

    companion object {
        const val PREFS = "cocwidgt_accounts"
        fun from(context: Context) = AccountStore(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE))

        fun removeCachedData(context: Context, tag: String) {
            val normalized = PlayerIdentity.normalizeTag(tag) ?: return
            val legacyVillage = context.getSharedPreferences(VillageSnapshot.PREFS, Context.MODE_PRIVATE)
            if (PlayerIdentity.normalizeTag(legacyVillage.getString("tag", null)) == normalized) legacyVillage.edit().clear().apply()
            val legacyName = context.getSharedPreferences("cocwidgt_player_name", Context.MODE_PRIVATE)
            if (PlayerNameCache.decode(legacyName.all)?.tag == normalized) legacyName.edit().clear().apply()
            val legacyProfile = context.getSharedPreferences(PlayerProfileCodec.PREFS, Context.MODE_PRIVATE)
            if (PlayerProfileCodec.decode(legacyProfile.all).tag == normalized) legacyProfile.edit().clear().apply()
            val settings = context.getSharedPreferences("cocwidgt", Context.MODE_PRIVATE)
            val edit = settings.edit()
            if (PlayerIdentity.normalizeTag(settings.getString("player_tag", null)) == normalized) edit.remove("player_tag")
            if (PlayerIdentity.normalizeTag(settings.getString("player_name_tag", null)) == normalized) {
                edit.remove("player_name_tag").remove("player_name")
            }
            edit.apply()
        }

        /** Copies the existing single account identity into the directory without deleting legacy data. */
        fun migrate(context: Context): AccountStore {
            val store = from(context)
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (prefs.getBoolean("migration_v1", false)) return store
            val settings = context.getSharedPreferences("cocwidgt", Context.MODE_PRIVATE)
            val village = context.getSharedPreferences(VillageSnapshot.PREFS, Context.MODE_PRIVATE)
            val profile = context.getSharedPreferences(PlayerProfileCodec.PREFS, Context.MODE_PRIVATE)
            val nameCache = context.getSharedPreferences("cocwidgt_player_name", Context.MODE_PRIVATE)
            val oldTag = AccountMigration.legacyTag(
                settings.getString("player_tag", null),
                village.getString("tag", null),
                PlayerProfileCodec.decode(profile.all).tag,
            )
            if (oldTag == null) {
                prefs.edit().putBoolean("migration_v1", true).apply()
                return store
            }
            val oldLabel = settings.getString("player_name", null)
                .takeIf { PlayerIdentity.normalizeTag(settings.getString("player_name_tag", null)) == oldTag }
                ?: village.let { old ->
                    old.getString("name", null).takeIf { PlayerIdentity.normalizeTag(old.getString("tag", null)) == oldTag }
                }
                ?: PlayerProfileCodec.decode(profile.all).name
                ?: PlayerNameCache.decode(nameCache.all)?.name
            if (store.accounts().none { it.tag == oldTag }) store.add(oldTag, oldLabel.orEmpty())
            if (oldLabel != null && store.manualName(oldTag) == null) store.setManualName(oldTag, oldLabel)
            // Copy the old single-account caches to tag-keyed stores; leave originals as rollback-safe data.
            copyLegacy(village, context.getSharedPreferences(VillageSnapshot.prefsName(oldTag), Context.MODE_PRIVATE),
                PlayerIdentity.normalizeTag(village.getString("tag", null)) == oldTag)
            copyLegacy(nameCache, context.getSharedPreferences(PlayerNameCache.prefsName(oldTag), Context.MODE_PRIVATE),
                PlayerNameCache.decode(nameCache.all)?.tag == oldTag)
            copyLegacy(profile, context.getSharedPreferences(PlayerProfileCodec.prefsName(oldTag), Context.MODE_PRIVATE),
                PlayerProfileCodec.decode(profile.all).tag == oldTag)
            prefs.edit().putBoolean("migration_v1", true).apply()
            return store
        }

        private fun copyLegacy(source: SharedPreferences, destination: SharedPreferences, matchesTag: Boolean) {
            if (!matchesTag || destination.all.isNotEmpty()) return
            val edit = destination.edit()
            AccountMigration.copyableValues(source.all).forEach { (key, value) -> when (value) {
                is String -> edit.putString(key, value)
                is Int -> edit.putInt(key, value)
                is Long -> edit.putLong(key, value)
                is Float -> edit.putFloat(key, value)
                is Boolean -> edit.putBoolean(key, value)
                is Set<*> -> edit.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
            }
            edit.apply()
        }
    }
}

/** Pure directory codec; malformed and duplicate tags are ignored safely. */
object AccountDirectory {
    fun decode(encoded: String?, values: Map<String, *>): List<PlayerAccount> = encoded.orEmpty().split('|')
        .mapNotNull(PlayerIdentity::normalizeTag).distinct().map { tag ->
            PlayerAccount(tag, PlayerIdentity.usableName(values["label:$tag"] as? String) ?: tag)
        }

    fun add(state: AccountDirectoryState, tag: String, label: String): AccountDirectoryState {
        val normalized = PlayerIdentity.normalizeTag(tag) ?: throw IllegalArgumentException("Enter a valid Player Tag.")
        if (state.accounts.any { it.tag == normalized }) return state
        val name = PlayerIdentity.usableName(label) ?: normalized
        return AccountDirectoryState(state.accounts + PlayerAccount(normalized, name), state.activeTag ?: normalized)
    }

    fun rename(state: AccountDirectoryState, tag: String, label: String): AccountDirectoryState {
        val normalized = requireKnown(state, tag)
        val name = PlayerIdentity.usableName(label) ?: throw IllegalArgumentException("Enter an account name.")
        return state.copy(accounts = state.accounts.map { if (it.tag == normalized) it.copy(label = name) else it })
    }

    fun select(state: AccountDirectoryState, tag: String): AccountDirectoryState =
        state.copy(activeTag = requireKnown(state, tag))

    fun remove(state: AccountDirectoryState, tag: String): AccountDirectoryState {
        val normalized = requireKnown(state, tag)
        val remaining = state.accounts.filterNot { it.tag == normalized }
        return AccountDirectoryState(remaining, if (state.activeTag == normalized) remaining.firstOrNull()?.tag else state.activeTag)
    }

    private fun requireKnown(state: AccountDirectoryState, tag: String): String {
        val normalized = PlayerIdentity.normalizeTag(tag) ?: throw IllegalArgumentException("Enter a valid Player Tag.")
        require(state.accounts.any { it.tag == normalized }) { "Account not found." }
        return normalized
    }
}

data class AccountDirectoryState(val accounts: List<PlayerAccount>, val activeTag: String?)

object AccountMigration {
    fun legacyTag(settingsTag: String?, villageTag: String?, profileTag: String?): String? =
        PlayerIdentity.firstValidTag(settingsTag, villageTag, profileTag)

    fun copyableValues(values: Map<String, *>): Map<String, Any> = values.mapNotNull { (key, value) ->
        if (value is String || value is Int || value is Long || value is Float || value is Boolean || value is Set<*>) {
            key to (if (value is Set<*>) value.filterIsInstance<String>().toSet() else value!!)
        } else null
    }.toMap()
}

object WidgetAccountStore {
    fun key(widgetId: Int) = "widget:$widgetId"
    fun resolve(assignedTag: String?, fallback: String?, validTags: Set<String>): String? =
        (PlayerIdentity.normalizeTag(assignedTag)?.takeIf { it in validTags }
            ?: PlayerIdentity.normalizeTag(fallback)?.takeIf { it in validTags })
    fun accountTag(prefs: SharedPreferences, widgetId: Int, fallback: String?, validTags: Set<String>): String? =
        resolve(prefs.getString(key(widgetId), null), fallback, validTags)
    fun assign(prefs: SharedPreferences, widgetId: Int, tag: String) {
        val normalized = PlayerIdentity.normalizeTag(tag) ?: throw IllegalArgumentException("Enter a valid Player Tag.")
        prefs.edit().putString(key(widgetId), normalized).apply()
    }
    fun removeAccount(prefs: SharedPreferences, tag: String) {
        val normalized = PlayerIdentity.normalizeTag(tag) ?: return
        prefs.all.filter { it.key.startsWith("widget:") && PlayerIdentity.normalizeTag(it.value as? String) == normalized }
            .keys.forEach { prefs.edit().remove(it).apply() }
    }
}
