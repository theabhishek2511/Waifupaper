package fyi.ryujin.waifu

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.preference.PreferenceManager
import fyi.ryujin.waifu.util.AppLogger

class WallpaperNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_NEXT_WALLPAPER) return

        AppLogger.init(context)
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val requests = prefs.getInt(KEY_NEXT_WALLPAPER_REQUESTS, 0) + 1
        prefs.edit().putInt(KEY_NEXT_WALLPAPER_REQUESTS, requests).apply()
        AppLogger.d("WallpaperNotification", "Queued next wallpaper request #$requests")
    }

    companion object {
        const val ACTION_NEXT_WALLPAPER = "fyi.ryujin.waifu.action.NEXT_WALLPAPER"
    }
}
