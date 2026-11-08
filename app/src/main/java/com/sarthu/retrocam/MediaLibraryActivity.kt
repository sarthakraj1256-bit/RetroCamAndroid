package com.sarthu.retrocam

import android.app.Activity
import android.content.ContentUris
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

/**
 * In-app browser for RetroCam-owned media. Uses MediaStore scoped access and
 * intentionally limits the initial library to RetroCam's own Pictures/Movies folders.
 */
class MediaLibraryActivity : AppCompatActivity() {
    private lateinit var list: LinearLayout
    private lateinit var summary: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = 0xFF100F0E.toInt()
        window.navigationBarColor = 0xFF100F0E.toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 18, 18, 18)
            setBackgroundColor(0xFF100F0E.toInt())
        }
        root.addView(TextView(this).apply {
            text = "RETROCAM  /  LIBRARY"
            textSize = 20f
            setTextColor(0xFFF3E7D8.toInt())
        })
        summary = TextView(this).apply {
            textSize = 12f
            setTextColor(0xFFE9C49A.toInt())
            setPadding(0, 8, 0, 12)
        }
        root.addView(summary)
        val scroll = ScrollView(this)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(Button(this).apply {
            text = "REFRESH LIBRARY"
            setOnClickListener { loadMedia() }
        })
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        if (::list.isInitialized) loadMedia()
    }

    private data class MediaItem(val uri: Uri, val name: String, val mime: String, val date: Long, val isVideo: Boolean)

    private fun loadMedia() {
        list.removeAllViews()
        val items = mutableListOf<MediaItem>()
        query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false, items)
        query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, items)
        items.sortByDescending { it.date }
        summary.text = "${items.size} items saved by RetroCam"
        if (items.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "No RetroCam media yet.\nCapture a photo or record a video to see it here."
                setTextColor(0xFFB7AAA0.toInt())
                gravity = Gravity.CENTER
                setPadding(20, 60, 20, 60)
            })
            return
        }
        items.forEach { item ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(12, 12, 12, 12)
                setBackgroundColor(0xFF211E1B.toInt())
            }
            row.addView(TextView(this).apply {
                text = (if (item.isVideo) "▶  " else "▧  ") + item.name
                textSize = 13f
                setTextColor(0xFFF3E7D8.toInt())
            })
            row.addView(TextView(this).apply {
                text = item.mime + "  •  " + java.text.DateFormat.getDateTimeInstance().format(java.util.Date(item.date * 1000))
                textSize = 10f
                setTextColor(0xFFB7AAA0.toInt())
                setPadding(0, 5, 0, 8)
            })
            val actions = LinearLayout(this).apply { gravity = Gravity.END }
            actions.addView(Button(this@MediaLibraryActivity).apply {
                text = "OPEN"
                setOnClickListener { open(item) }
            })
            if (!item.isVideo) actions.addView(Button(this@MediaLibraryActivity).apply {
                text = "EDIT"
                setOnClickListener {
                    startActivity(Intent(this@MediaLibraryActivity, PhotoEditorActivity::class.java)
                        .putExtra(PhotoEditorActivity.EXTRA_URI, item.uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                }
            })
            actions.addView(Button(this@MediaLibraryActivity).apply {
                text = "SHARE"
                setOnClickListener { share(item) }
            })
            row.addView(actions)
            list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 8 })
        }
    }

    private fun query(collection: Uri, isVideo: Boolean, out: MutableList<MediaItem>) {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.RELATIVE_PATH
        )
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        val args = arrayOf(if (isVideo) "Movies/RetroCam%" else "Pictures/RetroCam%")
        try {
            contentResolver.query(collection, projection, selection, args, "${MediaStore.MediaColumns.DATE_ADDED} DESC")?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val mimeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val dateCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    out.add(MediaItem(
                        ContentUris.withAppendedId(collection, id),
                        c.getString(nameCol) ?: "Untitled",
                        c.getString(mimeCol) ?: if (isVideo) "video/*" else "image/*",
                        c.getLong(dateCol),
                        isVideo
                    ))
                }
            }
        } catch (_: SecurityException) {
            // MediaStore still returns app-owned entries on supported Android versions.
        }
    }

    private fun open(item: MediaItem) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(item.uri, item.mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try { startActivity(intent) } catch (_: Exception) {
            Toast.makeText(this, "No compatible viewer found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun share(item: MediaItem) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = item.mime
            putExtra(Intent.EXTRA_STREAM, item.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, "Share RetroCam media"))
    }
}
