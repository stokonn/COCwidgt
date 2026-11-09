package dev.stokonn.cocwidgt.sync

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.stokonn.cocwidgt.data.ApiException
import dev.stokonn.cocwidgt.data.ClashApi
import dev.stokonn.cocwidgt.data.PlayerIdentity
import dev.stokonn.cocwidgt.data.PlayerProfileCodec
import dev.stokonn.cocwidgt.data.VillageSnapshot
import dev.stokonn.cocwidgt.security.TokenStore
import dev.stokonn.cocwidgt.widget.CocWidget
import java.util.concurrent.TimeUnit

/**
 * Fetches the Clash API profile for the player tag it was given ([KEY_TARGET_TAG]) with a
 * defensive fallback chain (input data → saved settings → stored profile → imported village),
 * so a sync started for one tag can never silently run against another player.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val settings = applicationContext.getSharedPreferences("cocwidgt", Context.MODE_PRIVATE)
        val storedProfile = PlayerProfileCodec.decode(
            applicationContext.getSharedPreferences(PlayerProfileCodec.PREFS, Context.MODE_PRIVATE).all
        )
        val tag = PlayerIdentity.firstValidTag(
            PlayerIdentity.normalizeTag(inputData.getString(KEY_TARGET_TAG)),
            PlayerIdentity.normalizeTag(settings.getString("player_tag", null)),
            storedProfile.tag,
            VillageSnapshot.load(applicationContext)?.tag,
        )
        val token = TokenStore(applicationContext).read()
        val api = ClashApi(applicationContext)

        if (tag == null || token.isNullOrBlank()) {
            api.saveError("Add your Player Tag and API token in app settings.")
            CocWidget().updateAll(applicationContext)
            return Result.failure()
        }
        return try {
            api.saveSnapshot(api.fetchPlayer(tag, token))
            // A successful sync also completes the settings, so the next automatic run (and the
            // widget) keeps syncing this same player without any further user action.
            if (PlayerIdentity.normalizeTag(settings.getString("player_tag", null)) == null) {
                settings.edit().putString("player_tag", tag).apply()
            }
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

    companion object {
        /** Player tag the sync runs for; carried with the work request (never re-derived silently). */
        const val KEY_TARGET_TAG = "target_tag"

        // Well within the WorkManager minimum interval of 15 minutes.
        private const val PERIODIC_WORK = "cocwidgt-periodic-sync"
        private const val PERIODIC_MINUTES = 15L

        /** One-time sync request for an explicit Player Tag. */
        fun request(tag: String): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setInputData(workDataOf(KEY_TARGET_TAG to tag))
                .build()

        /** Enqueues the automatic profile refresh; KEEP so repeated launches never stack it. */
        fun enqueuePeriodic(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SyncWorker>(PERIODIC_MINUTES, TimeUnit.MINUTES).build()
            )
        }
    }
}
