package fyi.ryujin.waifu.data

import android.content.Context
import androidx.preference.PreferenceManager

class WallpaperPreferences(context: Context) {
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)

    val nsfw: String get() = prefs.getString("nsfw", "False") ?: "False"
    val orientation: String get() = prefs.getString("orientation", "Portrait") ?: "Portrait"
    val timeInterval: Int get() = (prefs.getString("time_interval", "1800") ?: "1800").toInt()
    val overlayDarkness: Int get() = prefs.getInt("overlay_darkness", 20)
    val cacheVersion: Int get() = prefs.getInt("cache_version", 0)
}
