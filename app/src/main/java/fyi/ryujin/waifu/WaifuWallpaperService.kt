package fyi.ryujin.waifu

import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.SurfaceHolder
import fyi.ryujin.waifu.util.AppLogger
import fyi.ryujin.waifu.data.WallpaperPreferences
import fyi.ryujin.waifu.work.RefreshWorker
import java.io.File
import java.util.Calendar

private const val TAG = "WaifuWallpaper"

class WaifuWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = WaifuEngine()

    private inner class WaifuEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private var visible = false
        private var currentBitmap: Bitmap? = null
        private var oldBitmap: Bitmap? = null
        private var lastIndex = -1
        private var surfaceWidth = 0
        private var surfaceHeight = 0
        private var manualOffset = 0
        private var knownCacheVersion = -1

        private var fadeAlpha = 255
        private var fadeStartTime = 0L
        private val fadeDuration = 500L
        private val paint = Paint()

        private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "cache_version") {
                AppLogger.i(TAG, "Cache refreshed, resetting wallpaper cycle")
                manualOffset = 0
                lastIndex = -1
                currentBitmap?.recycle()
                currentBitmap = null
                knownCacheVersion = WallpaperPreferences(this@WaifuWallpaperService).cacheVersion
                if (visible) {
                    updateAndDraw()
                    scheduleNextChange()
                }
            }
        }

        private val gestureDetector = GestureDetector(
            this@WaifuWallpaperService,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    val count = getCachedImageCount()
                    if (count > 0) {
                        manualOffset = (manualOffset + 1) % count
                        lastIndex = -1
                        AppLogger.d(TAG, "Double-tap: cycling to offset=$manualOffset (of $count)")
                        updateAndDraw()
                    }
                    return true
                }
            }
        )

        private fun animateFade() {
            val elapsed = System.currentTimeMillis() - fadeStartTime
            fadeAlpha = ((elapsed.toFloat() / fadeDuration) * 255).toInt().coerceIn(0, 255)
            drawFrame()
            if (fadeAlpha < 255) {
                handler.postDelayed(::animateFade, 16L)
            } else {
                oldBitmap?.recycle()
                oldBitmap = null
            }
        }

        private val retryRunnable = Runnable {
            updateAndDraw()
        }

        private val changeRunnable = Runnable {
            updateAndDraw()
            scheduleNextChange()
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(true)
            AppLogger.init(this@WaifuWallpaperService)
            AppLogger.i(TAG, "WallpaperEngine created")
            knownCacheVersion = WallpaperPreferences(this@WaifuWallpaperService).cacheVersion
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(this@WaifuWallpaperService)
                .registerOnSharedPreferenceChangeListener(prefsListener)
            RefreshWorker.scheduleMidnightRefresh(this@WaifuWallpaperService)
        }

        override fun onTouchEvent(event: MotionEvent) {
            gestureDetector.onTouchEvent(event)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            AppLogger.d(TAG, "Visibility changed: visible=$visible")
            if (visible) {
                updateAndDraw()
                scheduleNextChange()
            } else {
                handler.removeCallbacks(changeRunnable)
            }
        }

        override fun onSurfaceChanged(
            holder: SurfaceHolder,
            format: Int,
            width: Int,
            height: Int
        ) {
            super.onSurfaceChanged(holder, format, width, height)
            surfaceWidth = width
            surfaceHeight = height
            AppLogger.d(TAG, "Surface changed: ${width}x${height}")
            // Force reload at new size
            lastIndex = -1
            currentBitmap?.recycle()
            currentBitmap = null
            updateAndDraw()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            AppLogger.d(TAG, "Surface destroyed")
            visible = false
            handler.removeCallbacks(changeRunnable)
            handler.removeCallbacks(retryRunnable)
            handler.removeCallbacksAndMessages(null)
        }

        override fun onDestroy() {
            super.onDestroy()
            AppLogger.i(TAG, "WallpaperEngine destroyed")
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(this@WaifuWallpaperService)
                .unregisterOnSharedPreferenceChangeListener(prefsListener)
            handler.removeCallbacksAndMessages(null)
            currentBitmap?.recycle()
            currentBitmap = null
            oldBitmap?.recycle()
            oldBitmap = null
        }

        private fun updateAndDraw() {
            val count = getCachedImageCount()
            val index = getCurrentImageIndex()
            AppLogger.d(TAG, "updateAndDraw: index=$index lastIndex=$lastIndex count=$count surface=${surfaceWidth}x${surfaceHeight}")
            if (count == 0) {
                AppLogger.d(TAG, "No cached images, drawing blank and retrying in 5s")
                drawFrame()
                // Retry in 5 seconds — images may still be downloading
                if (visible) {
                    handler.removeCallbacks(retryRunnable)
                    handler.postDelayed(retryRunnable, 5000L)
                }
                return
            }
            handler.removeCallbacks(retryRunnable)
            if (index != lastIndex || currentBitmap == null) {
                val file = getCachedImageFile(index)
                AppLogger.d(TAG, "Loading image: ${file?.name} exists=${file?.exists()}")
                if (file != null) {
                    val newBitmap = decodeSampledBitmap(file, surfaceWidth, surfaceHeight)
                    if (newBitmap != null) {
                        oldBitmap?.recycle()
                        oldBitmap = currentBitmap
                        currentBitmap = newBitmap
                        lastIndex = index
                        fadeAlpha = 0
                        fadeStartTime = System.currentTimeMillis()
                        animateFade()
                        AppLogger.d(TAG, "Bitmap loaded: ${newBitmap.width}x${newBitmap.height} from ${file.name}")
                        return
                    } else {
                        AppLogger.e(TAG, "Failed to decode bitmap from ${file.name}")
                    }
                }
            }
            drawFrame()
        }

        private fun drawFrame() {
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) {
                    canvas.drawColor(Color.BLACK)
                    oldBitmap?.let {
                        paint.alpha = 255 - fadeAlpha
                        drawCenterCrop(canvas, it, paint)
                    }
                    currentBitmap?.let {
                        paint.alpha = fadeAlpha
                        drawCenterCrop(canvas, it, paint)
                    }
                    // Dark overlay
                    val overlayPct = WallpaperPreferences(this@WaifuWallpaperService).overlayDarkness
                    if (overlayPct > 0) {
                        val overlayAlpha = (overlayPct * 255 / 100).coerceIn(0, 255)
                        canvas.drawColor(Color.argb(overlayAlpha, 0, 0, 0))
                    }
                }
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas)
                    } catch (_: IllegalArgumentException) {
                        // Surface already released
                    }
                }
            }
        }

        private fun drawCenterCrop(canvas: Canvas, bitmap: Bitmap, p: Paint) {
            val scaleX = canvas.width.toFloat() / bitmap.width
            val scaleY = canvas.height.toFloat() / bitmap.height
            val scale = maxOf(scaleX, scaleY)
            val dx = (canvas.width - bitmap.width * scale) / 2f
            val dy = (canvas.height - bitmap.height * scale) / 2f
            val matrix = Matrix().apply {
                setScale(scale, scale)
                postTranslate(dx, dy)
            }
            canvas.drawBitmap(bitmap, matrix, p)
        }

        private fun getCurrentImageIndex(): Int {
            val count = getCachedImageCount()
            if (count == 0) return 0
            val prefs = WallpaperPreferences(this@WaifuWallpaperService)
            val now = Calendar.getInstance()
            val secondsSinceMidnight = now.get(Calendar.HOUR_OF_DAY) * 3600 +
                    now.get(Calendar.MINUTE) * 60 +
                    now.get(Calendar.SECOND)
            val timeIndex = secondsSinceMidnight / prefs.timeInterval
            return (timeIndex + manualOffset) % count
        }

        private fun scheduleNextChange() {
            handler.removeCallbacks(changeRunnable)
            if (visible) {
                val prefs = WallpaperPreferences(this@WaifuWallpaperService)
                val now = Calendar.getInstance()
                val secondsSinceMidnight = now.get(Calendar.HOUR_OF_DAY) * 3600 +
                        now.get(Calendar.MINUTE) * 60 +
                        now.get(Calendar.SECOND)
                val nextChangeIn = prefs.timeInterval - (secondsSinceMidnight % prefs.timeInterval)
                AppLogger.d(TAG, "Next wallpaper change in ${nextChangeIn}s")
                handler.postDelayed(changeRunnable, nextChangeIn * 1000L)
            }
        }

        private fun getCachedImageCount(): Int {
            val dir = File(filesDir, "wallpapers")
            return dir.listFiles()?.size ?: 0
        }

        private fun getCachedImageFile(index: Int): File? {
            val dir = File(filesDir, "wallpapers")
            return dir.listFiles()?.firstOrNull { it.nameWithoutExtension == index.toString() }
        }

        private fun decodeSampledBitmap(file: File, reqWidth: Int, reqHeight: Int): Bitmap? {
            if (reqWidth == 0 || reqHeight == 0) {
                return BitmapFactory.decodeFile(file.absolutePath)
            }
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false
            return BitmapFactory.decodeFile(file.absolutePath, options)
        }

        private fun calculateInSampleSize(
            options: BitmapFactory.Options,
            reqWidth: Int,
            reqHeight: Int
        ): Int {
            val (height, width) = options.outHeight to options.outWidth
            var inSampleSize = 1
            if (height > reqHeight || width > reqWidth) {
                val halfHeight = height / 2
                val halfWidth = width / 2
                while (halfHeight / inSampleSize >= reqHeight &&
                    halfWidth / inSampleSize >= reqWidth
                ) {
                    inSampleSize *= 2
                }
            }
            return inSampleSize
        }
    }
}
