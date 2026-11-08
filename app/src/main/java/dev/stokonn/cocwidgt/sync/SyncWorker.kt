package dev.stokonn.cocwidgt.sync

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.stokonn.cocwidgt.data.ApiException
import dev.stokonn.cocwidgt.data.ClashApi
import dev.stokonn.cocwidgt.data.PlayerIdentity
import dev.stokonn.cocwidgt.security.TokenStore
import dev.stokonn.cocwidgt.widget.CocWidget

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val settings = applicationContext.getSharedPreferences("cocwidgt", Context.MODE_PRIVATE)
        val tag = PlayerIdentity.normalizeTag(settings.getString("player_tag", null))
        val token = TokenStore(applicationContext).read()
        val api = ClashApi(applicationContext)

        if (tag == null || token.isNullOrBlank()) {
            api.saveError("Add your Player Tag and API token in app settings.")
            CocWidget().updateAll(applicationContext)
            return Result.failure()
        }
        return try {
            api.saveSnapshot(api.fetchPlayer(tag, token))
            CocWidget().updateAll(applicationContext)
            Result.success()
        } catch (error: ApiException) {
            val message = when (error.status) {
                400 -> "Invalid player tag or request."
                403 -> "Token rejected or source IP is not allowlisted."
                404 -> "Player not found. Check the tag."
                429 -> "API rate limit reached. Try later."
                else -> "Clash API error ${error.status}: ${error.message}"
            }
            api.saveError(message)
            CocWidget().updateAll(applicationContext)
            if (error.status == 429 || error.status >= 500) Result.retry() else Result.failure()
        } catch (error: Exception) {
            api.saveError(error.message ?: "Network error.")
            CocWidget().updateAll(applicationContext)
            Result.retry()
        }
    }
}
