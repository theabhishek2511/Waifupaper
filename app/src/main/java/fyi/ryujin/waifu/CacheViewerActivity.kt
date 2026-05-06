package fyi.ryujin.waifu

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class CacheViewerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cache_viewer)

        title = "Cached Images"

        val recyclerView = findViewById<RecyclerView>(R.id.recycler_view)
        val emptyText = findViewById<TextView>(R.id.empty_text)

        val dir = File(filesDir, "wallpapers")
        val files = dir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in IMAGE_EXTENSIONS }
            ?.sortedBy { it.nameWithoutExtension.toIntOrNull() ?: 0 }
            ?: emptyList()

        if (files.isEmpty()) {
            emptyText.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyText.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            recyclerView.layoutManager = GridLayoutManager(this, 3)
            recyclerView.adapter = CacheAdapter(files) { file ->
                startActivity(
                    Intent(this, CachedImageDetailActivity::class.java)
                        .putExtra(CachedImageDetailActivity.EXTRA_FILE_NAME, file.name)
                )
            }
        }
    }

    private class CacheAdapter(
        private val files: List<File>,
        private val onClick: (File) -> Unit
    ) :
        RecyclerView.Adapter<CacheAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val imageView: ImageView = view.findViewById(R.id.cache_image)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_cache_image, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val file = files[position]
            val options = BitmapFactory.Options().apply { inSampleSize = 4 }
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, options)
            holder.imageView.setImageBitmap(bitmap)
            holder.imageView.setOnClickListener { onClick(file) }
        }

        override fun getItemCount() = files.size
    }

    companion object {
        val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif")
    }
}
