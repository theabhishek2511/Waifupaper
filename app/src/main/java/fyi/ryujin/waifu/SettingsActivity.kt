package fyi.ryujin.waifu

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import fyi.ryujin.waifu.work.RefreshWorker
import java.io.File

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }

        // Fetch images on first launch if none are cached
        val wallpaperDir = File(filesDir, "wallpapers")
        if (!wallpaperDir.exists() || (wallpaperDir.listFiles()?.isEmpty() != false)) {
            RefreshWorker.ensureImages(this)
        }

        findViewById<Button>(R.id.btn_set_wallpaper).setOnClickListener {
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

            findPreference<Preference>("view_cache")?.setOnPreferenceClickListener {
                startActivity(Intent(requireContext(), CacheViewerActivity::class.java))
                true
            }

            findPreference<Preference>("clear_cache")?.setOnPreferenceClickListener {
                val dir = File(requireContext().filesDir, "wallpapers")
                dir.listFiles()?.forEach { it.delete() }
                Toast.makeText(requireContext(), R.string.cache_cleared, Toast.LENGTH_SHORT).show()
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
                RefreshWorker.triggerSettingsChanged(requireContext())
            }
        }
    }
}
