package fyi.ryujin.waifu

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import fyi.ryujin.waifu.util.AppLogger
import fyi.ryujin.waifu.work.RefreshWorker
import java.io.File

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false)
        AppLogger.init(this)
        AppLogger.i("SettingsActivity", "Settings opened")

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }

        // Fetch images on first launch if none are cached
        val wallpaperDir = File(filesDir, "wallpapers")
        if (!wallpaperDir.exists() || (wallpaperDir.listFiles()?.isEmpty() != false)) {
            AppLogger.i("SettingsActivity", "No cached images found, triggering initial fetch")
            RefreshWorker.ensureImages(this)
        }

        findViewById<Button>(R.id.btn_set_wallpaper).setOnClickListener {
            AppLogger.i("SettingsActivity", "User tapped 'Set as Live Wallpaper'")
            val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                putExtra(
                    WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    ComponentName(this@SettingsActivity, WaifuWallpaperService::class.java)
                )
            }
            startActivity(intent)
        }
    }

    class SettingsFragment : PreferenceFragmentCompat(),
        SharedPreferences.OnSharedPreferenceChangeListener {

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)

            // Ensure defaults are written and summaries resolve
            val defaults = mapOf("nsfw" to "False", "orientation" to "Portrait", "time_interval" to "1800")
            for ((key, defVal) in defaults) {
                val lp = findPreference<ListPreference>(key) ?: continue
                if (lp.entry == null) {
                    lp.value = defVal
                }
            }

            findPreference<Preference>("view_cache")?.setOnPreferenceClickListener {
                AppLogger.d("Settings", "Opening cache viewer")
                startActivity(Intent(requireContext(), CacheViewerActivity::class.java))
                true
            }

            findPreference<Preference>("clear_cache")?.setOnPreferenceClickListener {
                val dir = File(requireContext().filesDir, "wallpapers")
                val count = dir.listFiles()?.size ?: 0
                dir.listFiles()?.forEach { it.delete() }
                AppLogger.i("Settings", "Cache cleared: $count images deleted, triggering re-fetch")
                Toast.makeText(requireContext(), R.string.cache_cleared, Toast.LENGTH_SHORT).show()
                RefreshWorker.ensureImages(requireContext())
                true
            }

            findPreference<Preference>("export_logs")?.setOnPreferenceClickListener {
                val logFile = AppLogger.getLogFile()
                if (logFile == null || !logFile.exists()) {
                    Toast.makeText(requireContext(), R.string.no_logs, Toast.LENGTH_SHORT).show()
                } else {
                    val uri = FileProvider.getUriForFile(
                        requireContext(),
                        "${requireContext().packageName}.fileprovider",
                        logFile
                    )
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(intent, getString(R.string.export_logs_chooser)))
                    AppLogger.i("Settings", "Logs exported via share")
                }
                true
            }

            findPreference<Preference>("clear_logs")?.setOnPreferenceClickListener {
                AppLogger.clearLogs()
                Toast.makeText(requireContext(), R.string.logs_cleared, Toast.LENGTH_SHORT).show()
                true
            }
        }

        override fun onResume() {
            super.onResume()
            preferenceScreen.sharedPreferences
                ?.registerOnSharedPreferenceChangeListener(this)
        }

        override fun onPause() {
            super.onPause()
            preferenceScreen.sharedPreferences
                ?.unregisterOnSharedPreferenceChangeListener(this)
        }

        override fun onSharedPreferenceChanged(
            sharedPreferences: SharedPreferences?,
            key: String?
        ) {
            if (key in listOf("nsfw", "orientation", "time_interval")) {
                val value = sharedPreferences?.all?.get(key)
                AppLogger.i("Settings", "Setting changed: $key=$value")
                RefreshWorker.triggerSettingsChanged(requireContext())
            }
        }
    }
}
