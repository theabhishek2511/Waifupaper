package fyi.ryujin.waifu.data

import android.content.Context
import android.util.Log
import fyi.ryujin.waifu.api.WaifuApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

private const val TAG = "ImageRepository"

class ImageRepository(context: Context) {
    private val api = WaifuApi.create()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private val baseDir = context.filesDir
    private val cacheDir = File(baseDir, "wallpapers")
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
            Log.i(TAG, "refreshImages: nsfw=$nsfw orientation=$orientation totalNeeded=$totalNeeded")
            val tempDir = File(baseDir, "wallpapers_tmp")
            val created = tempDir.mkdirs()
            Log.d(TAG, "tempDir=$tempDir exists=${tempDir.exists()} created=$created")
            tempDir.listFiles()?.forEach { it.delete() }

            var remaining = totalNeeded
            var index = 0

            while (remaining > 0) {
                val batchSize = minOf(remaining, 30)
                Log.d(TAG, "Fetching batch: size=$batchSize remaining=$remaining")
                val response = try {
                    rateLimit()
                    api.getImages(
                        isNsfw = nsfw,
                        orderBy = "Random",
                        orientation = orientation,
                        pageSize = batchSize
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "API call failed", e)
                    break
                }

                Log.d(TAG, "API returned ${response.items.size} images")
                if (response.items.isEmpty()) break

                for (image in response.items) {
                    try {
                        rateLimit()
                        downloadImage(image.url, index, tempDir)
                        index++
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to download ${image.url}", e)
                    }
                }

                remaining -= response.items.size
            }

            Log.i(TAG, "Downloaded $index images total")
            // Swap: clear old cache, move temp files in
            if (index > 0) {
                cacheDir.listFiles()?.forEach { it.delete() }
                tempDir.listFiles()?.forEach { it.renameTo(File(cacheDir, it.name)) }
            }
            tempDir.delete()
        }

    private fun downloadImage(url: String, index: Int, targetDir: File) {
        if (!url.startsWith("https://")) return
        targetDir.mkdirs()

        val ext = url.substringAfterLast('.', "jpg")
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return
            response.body?.byteStream()?.use { input ->
                File(targetDir, "$index.$ext").outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }
}
