package dev.stokonn.cocwidgt.sync

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.stokonn.cocwidgt.widget.CocWidget
import java.util.concurrent.TimeUnit

/**
 * Re-renders the widget so absolute-timestamp countdowns and progress bars stay current.
 *
 * Android does not allow reliable per-second widget updates: WorkManager periodic work has a
 * 15-minute minimum and the system app-widget update period is capped at 30 minutes, so this
 * worker refreshes on the coarsest schedule the platform supports instead.
 */
class WidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        CocWidget().updateAll(applicationContext)
        Result.success()
    } catch (error: Exception) {
        Result.retry()
    }

    companion object {
        const val UNIQUE_NAME = "cocwidgt-widget-refresh"
        private const val REFRESH_MINUTES = 15L

        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<WidgetRefreshWorker>(REFRESH_MINUTES, TimeUnit.MINUTES).build()
            )
        }
    }
}
