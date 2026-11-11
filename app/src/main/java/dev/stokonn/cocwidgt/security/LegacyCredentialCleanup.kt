package dev.stokonn.cocwidgt.security

import android.content.Context
import java.security.KeyStore

/** Removes the unused Developer API bearer secret from installations upgraded from older builds. */
object LegacyCredentialCleanup {
    fun clear(context: Context) {
        context.getSharedPreferences("cocwidgt_secrets", Context.MODE_PRIVATE)
            .edit().remove("encrypted_api_token").apply()
        runCatching {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                .deleteEntry("cocwidgt_supercell_api_token")
        }
    }
}
