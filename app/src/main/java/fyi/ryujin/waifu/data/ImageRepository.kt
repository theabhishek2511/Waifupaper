package fyi.ryujin.waifu.data

import android.content.Context
import fyi.ryujin.waifu.api.WaifuApi
import fyi.ryujin.waifu.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import androidx.preference.PreferenceManager

private const val TAG = "ImageRepository"

class ImageRepository(context: Context) {
    private val api = WaifuApi.create()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private val baseDir = context.filesDir
    private val cacheDir = File(baseDir, "wallpapers")
    private val appPrefs = PreferenceManager.getDefaultSharedPreferences(context)
    @Volatile private var lastRequestTime = 0L

    init {
        cacheDir.mkdirs()
    }

    private fun rateLimit() {
        val now = System.currentTimeMillis()
        val elapsed = now - lastRequestTime
        if (elapsed < 50) {
            Thread.sleep(50 - elapsed)
        }
        lastRequestTime = System.currentTimeMillis()
    }

    suspend fun refreshImages(nsfw: String, orientation: String, totalNeeded: Int) =
        withContext(Dispatchers.IO) {
            AppLogger.i(TAG, "=== Refresh started: nsfw=$nsfw orientation=$orientation totalNeeded=$totalNeeded ===")
            val tempDir = File(baseDir, "wallpapers_tmp")
            val created = tempDir.mkdirs()
            AppLogger.d(TAG, "Temp dir: path=$tempDir exists=${tempDir.exists()} created=$created")
            val cleaned = tempDir.listFiles()?.size ?: 0
            tempDir.listFiles()?.forEach { it.delete() }
            if (cleaned > 0) AppLogger.d(TAG, "Cleaned $cleaned stale temp files")

            var remaining = totalNeeded
            var index = 0
            var batchNum = 0
            var downloadErrors = 0

            while (remaining > 0) {
                batchNum++
                val batchSize = minOf(remaining, 30)
                AppLogger.d(TAG, "Batch #$batchNum: requesting $batchSize images ($remaining remaining)")
                val response = try {
                    rateLimit()
                    api.getImages(
                        isNsfw = nsfw,
                        orderBy = "Random",
                        orientation = orientation,
                        pageSize = batchSize
                    )
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Batch #$batchNum: API call failed, aborting refresh", e)
                    break
                }

                AppLogger.d(TAG, "Batch #$batchNum: API returned ${response.items.size} images")
                if (response.items.isEmpty()) {
                    AppLogger.w(TAG, "Batch #$batchNum: API returned empty list, stopping")
                    break
                }

                for (image in response.items) {
                    try {
                        rateLimit()
                        downloadImage(image.url, index, tempDir)
                        index++
                    } catch (e: Exception) {
                        downloadErrors++
                        AppLogger.e(TAG, "Download failed (#$downloadErrors): ${image.url}", e)
                    }
                }

                remaining -= response.items.size
            }

            val oldCount = cacheDir.listFiles()?.size ?: 0
            AppLogger.i(TAG, "Download complete: $index images downloaded, $downloadErrors errors, $batchNum batches")
            // Swap: clear old cache, move temp files in
            if (index > 0) {
                cacheDir.listFiles()?.forEach { it.delete() }
                tempDir.listFiles()?.forEach { it.renameTo(File(cacheDir, it.name)) }
                AppLogger.i(TAG, "Cache swapped: $oldCount old -> $index new images")
                val newVersion = appPrefs.getInt("cache_version", 0) + 1
                appPrefs.edit().putInt("cache_version", newVersion).apply()
                AppLogger.d(TAG, "Cache version bumped to $newVersion")
            } else {
                AppLogger.w(TAG, "No images downloaded, keeping existing cache ($oldCount images)")
            }
            tempDir.delete()
            AppLogger.i(TAG, "=== Refresh finished ===")
        }

    private fun downloadImage(url: String, index: Int, targetDir: File) {
        if (!url.startsWith("https://")) {
            AppLogger.w(TAG, "Skipping non-HTTPS URL: $url")
            return
        }
        targetDir.mkdirs()

        val ext = url.substringAfterLast('.', "jpg")
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                AppLogger.w(TAG, "Download HTTP ${response.code}: $url")
                return
            }
            val file = File(targetDir, "$index.$ext")
            response.body?.byteStream()?.use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            AppLogger.d(TAG, "Downloaded #$index: ${file.name} (${file.length() / 1024}KB)")
        }
    }
}
