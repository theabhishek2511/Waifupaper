package fyi.ryujin.waifu.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import fyi.ryujin.waifu.data.ImageRepository
import fyi.ryujin.waifu.data.WallpaperPreferences
import android.util.Log
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.math.floor

private const val TAG = "RefreshWorker"

class RefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = WallpaperPreferences(applicationContext)
        val repo = ImageRepository(applicationContext)

        val pageSize = inputData.getInt(KEY_PAGE_SIZE, -1).let {
            if (it > 0) it
            else floor(86400.0 / prefs.timeInterval).toInt()
        }

        Log.i(TAG, "doWork: pageSize=$pageSize nsfw=${prefs.nsfw} orientation=${prefs.orientation} interval=${prefs.timeInterval}")
        if (pageSize <= 0) return Result.success()

        return try {
            repo.refreshImages(prefs.nsfw, prefs.orientation, pageSize)
            scheduleMidnightRefresh(applicationContext)
            Log.i(TAG, "doWork: completed successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "doWork: failed (attempt $runAttemptCount)", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val MIDNIGHT_WORK = "midnight_refresh"
        private const val SETTINGS_WORK = "settings_refresh"
        private const val KEY_PAGE_SIZE = "page_size"

        fun scheduleMidnightRefresh(context: Context) {
            val now = System.currentTimeMillis()
            val midnight = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val delayMillis = midnight.timeInMillis - now

            val request = OneTimeWorkRequestBuilder<RefreshWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(MIDNIGHT_WORK, ExistingWorkPolicy.REPLACE, request)
        }

        fun triggerSettingsChanged(context: Context) {
            val prefs = WallpaperPreferences(context)
            val secondsToMidnight = run {
                val now = Calendar.getInstance()
                val midnight = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                (midnight.timeInMillis - now.timeInMillis) / 1000
            }
            val pageSize = floor(secondsToMidnight.toDouble() / prefs.timeInterval).toInt()

            if (pageSize <= 0) return

            val data = workDataOf(KEY_PAGE_SIZE to pageSize)
            val request = OneTimeWorkRequestBuilder<RefreshWorker>()
                .setInputData(data)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(SETTINGS_WORK, ExistingWorkPolicy.REPLACE, request)

            scheduleMidnightRefresh(context)
        }

        fun ensureImages(context: Context) {
            val prefs = WallpaperPreferences(context)
            val secondsToMidnight = run {
                val now = Calendar.getInstance()
                val midnight = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                (midnight.timeInMillis - now.timeInMillis) / 1000
            }
            val pageSize = floor(secondsToMidnight.toDouble() / prefs.timeInterval).toInt()

            if (pageSize <= 0) return

            val data = workDataOf(KEY_PAGE_SIZE to pageSize)
            val request = OneTimeWorkRequestBuilder<RefreshWorker>()
                .setInputData(data)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(SETTINGS_WORK, ExistingWorkPolicy.KEEP, request)

            scheduleMidnightRefresh(context)
        }
    }
}
