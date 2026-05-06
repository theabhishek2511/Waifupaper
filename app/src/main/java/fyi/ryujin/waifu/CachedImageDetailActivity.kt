package fyi.ryujin.waifu

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.gson.Gson
import fyi.ryujin.waifu.api.WaifuImage
import fyi.ryujin.waifu.util.AppLogger
import java.io.File

class CachedImageDetailActivity : AppCompatActivity() {
    private lateinit var imageFile: File
    private var metadata: WaifuImage? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_cached_image_detail)
        supportActionBar?.hide()
        applySystemBarInsets()

        val fileName = intent.getStringExtra(EXTRA_FILE_NAME)
        if (fileName == null || fileName.contains(File.separatorChar)) {
            finish()
            return
        }

        imageFile = File(File(filesDir, "wallpapers"), fileName)
        if (!imageFile.exists()) {
            finish()
            return
        }
        metadata = readMetadata(fileName)

        findViewById<ImageView>(R.id.fullscreen_image).setImageURI(getInternalImageUri(imageFile))
        findViewById<ImageButton>(R.id.close_button).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.export_button).setOnClickListener { exportImage() }
        findViewById<ImageButton>(R.id.open_source_button).apply {
            val source = metadata?.source?.takeIf { it.isNotBlank() }
            configureLinkButton(source?.takeIf { it.isHttpUrl() } ?: metadata?.url?.takeIf { it.isHttpUrl() })
        }
        findViewById<ImageButton>(R.id.open_waifu_button).apply {
            configureLinkButton(metadata?.id?.let { waifuPreviewUrl(it) })
        }
        findViewById<TextView>(R.id.metadata_text).text = metadataText(metadata)
    }

    private fun applySystemBarInsets() {
        val topControls = findViewById<View>(R.id.top_controls)
        val metadataText = findViewById<TextView>(R.id.metadata_text)
        val topInitialPadding = Padding(
            topControls.paddingLeft,
            topControls.paddingTop,
            topControls.paddingRight,
            topControls.paddingBottom
        )
        val metadataInitialPadding = Padding(
            metadataText.paddingLeft,
            metadataText.paddingTop,
            metadataText.paddingRight,
            metadataText.paddingBottom
        )

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.detail_root)) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            topControls.updatePadding(
                left = topInitialPadding.left + bars.left,
                top = topInitialPadding.top + bars.top,
                right = topInitialPadding.right + bars.right
            )
            metadataText.updatePadding(
                left = metadataInitialPadding.left + bars.left,
                right = metadataInitialPadding.right + bars.right,
                bottom = metadataInitialPadding.bottom + bars.bottom
            )
            insets
        }
    }

    private fun getInternalImageUri(file: File): Uri {
        return FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
    }

    private fun readMetadata(fileName: String): WaifuImage? {
        val metadataFile = File(File(filesDir, "wallpaper_metadata"), "${imageFile.nameWithoutExtension}.json")
        if (!metadataFile.exists()) return null

        return runCatching {
            Gson().fromJson(metadataFile.readText(), WaifuImage::class.java)
        }.getOrElse {
            AppLogger.e("CachedImageDetail", "Failed to read metadata for $fileName", it)
            null
        }
    }

    private fun metadataText(image: WaifuImage?): String {
        if (image == null) return getString(R.string.metadata_unavailable)
        return listOfNotNull(
            image.id?.let { getString(R.string.detail_metadata_id, it) },
            image.artists.orEmpty().firstNotNullOfOrNull { it.name }?.let { getString(R.string.detail_metadata_artist, it) },
            image.tags.orEmpty().mapNotNull { it.name }.take(5).joinToString(", ")
                .takeIf { it.isNotBlank() }?.let { getString(R.string.detail_metadata_tags, it) },
            image.source?.takeIf { it.isNotBlank() }?.let { getString(R.string.detail_metadata_source, it) },
            if (image.width != null && image.height != null) {
                getString(R.string.detail_metadata_size, image.width, image.height)
            } else null
        ).joinToString("\n").ifBlank { getString(R.string.metadata_unavailable) }
    }

    private fun ImageButton.configureLinkButton(url: String?) {
        val validUrl = url?.takeIf { it.isHttpUrl() }
        isEnabled = validUrl != null
        alpha = if (validUrl == null) 0.4f else 1f
        setOnClickListener {
            validUrl?.let { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
        }
    }

    private fun String.isHttpUrl(): Boolean {
        return startsWith("https://") || startsWith("http://")
    }

    private fun waifuPreviewUrl(id: Long): String {
        return "https://www.waifu.im/images/$id"
    }

    private fun exportImage() {
        val displayName = "waifupaper_${imageFile.name}"
        val resolver = contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeTypeFor(imageFile))
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Waifupaper")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri == null) {
            Toast.makeText(this, R.string.export_image_failed, Toast.LENGTH_SHORT).show()
            return
        }

        try {
            resolver.openOutputStream(uri)?.use { output ->
                imageFile.inputStream().use { input -> input.copyTo(output) }
            } ?: error("Unable to open MediaStore output stream")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            Toast.makeText(this, R.string.export_image_success, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            AppLogger.e("CachedImageDetail", "Failed to export ${imageFile.name}", e)
            Toast.makeText(this, R.string.export_image_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun mimeTypeFor(file: File): String {
        return when (file.extension.lowercase()) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            else -> "image/jpeg"
        }
    }

    companion object {
        const val EXTRA_FILE_NAME = "file_name"
    }

    private data class Padding(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    )
}
