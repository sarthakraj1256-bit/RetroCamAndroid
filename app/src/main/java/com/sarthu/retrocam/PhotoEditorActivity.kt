package com.sarthu.retrocam

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max

/**
 * Non-destructive-in-session photo editor. The original source stays untouched; Save writes
 * a new JPEG. To keep memory use predictable across Android devices, imported images are
 * decoded to a maximum long edge of 2560px before editing/export.
 */
class PhotoEditorActivity : AppCompatActivity() {
    private lateinit var preview: ImageView
    private lateinit var status: TextView
    private lateinit var lookSpinner: Spinner
    private lateinit var cropButton: Button
    private lateinit var strength: SeekBar
    private lateinit var brightness: SeekBar
    private lateinit var contrast: SeekBar
    private lateinit var saturation: SeekBar
    private lateinit var straighten: SeekBar
    private lateinit var cropHorizontal: SeekBar
    private lateinit var cropVertical: SeekBar
    private var original: Bitmap? = null
    private var rotation = 0
    private var cropMode = 0
    private val cropModes = listOf("Original", "1:1", "4:5", "16:9", "3:2", "2:3")
    private val looks = listOf("ORIGINAL", "VHS", "DV", "SUPER 8", "8MM", "16MM", "35MM", "CCD", "PORTRA 400", "GOLD 200", "FADED", "B&W")
    private val worker = Executors.newSingleThreadExecutor()
    private val renderToken = AtomicInteger(0)
    private data class EditSettings(
        val rotation: Int,
        val cropMode: String,
        val look: String,
        val strength: Int,
        val brightness: Int,
        val contrast: Int,
        val saturation: Int,
        val straightenDegrees: Float,
        val cropHorizontal: Int,
        val cropVertical: Int
    )

    /** Snapshot view state on the main thread; the worker never reads Android Views. */
    private fun captureSettings() = EditSettings(
        rotation = rotation,
        cropMode = cropModes[cropMode],
        look = looks[lookSpinner.selectedItemPosition.coerceIn(looks.indices)],
        strength = strength.progress,
        brightness = brightness.progress,
        contrast = contrast.progress,
        saturation = saturation.progress,
        straightenDegrees = (straighten.progress - 100) / 10f,
        cropHorizontal = cropHorizontal.progress,
        cropVertical = cropVertical.progress
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = 0xFF100F0E.toInt()
        window.navigationBarColor = 0xFF100F0E.toInt()
        val uri = intent.getParcelableExtra<Uri>(EXTRA_URI)
        if (uri == null) { finish(); return }
        buildUi()
        status.text = "Loading image…"
        worker.execute {
            try {
                val bitmap = decodeCorrected(uri)
                original = bitmap
                runOnUiThread {
                    status.text = "Original preserved • edits save as a separate copy"
                    renderPreview()
                }
            } catch (error: Exception) {
                runOnUiThread {
                    status.text = "Could not open image: ${error.localizedMessage}"
                    Toast.makeText(this, status.text, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14, 12, 14, 12)
            setBackgroundColor(0xFF100F0E.toInt())
        }
        root.addView(TextView(this).apply {
            text = "RETROCAM  /  PHOTO STUDIO"; textSize = 19f
            setTextColor(0xFFF3E7D8.toInt())
        })
        status = TextView(this).apply {
            textSize = 10f; setTextColor(0xFFE9C49A.toInt()); setPadding(0, 5, 0, 8)
        }
        root.addView(status)
        preview = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(0xFF211E1B.toInt())
        }
        root.addView(preview, LinearLayout.LayoutParams(-1, 0, 1f).apply { bottomMargin = 8 })

        val actions = LinearLayout(this).apply { gravity = Gravity.CENTER }
        actions.addView(toolButton("ROTATE 90°") { rotation = (rotation + 90) % 360; renderPreview() }, LinearLayout.LayoutParams(0, 46, 1f))
        cropButton = toolButton("CROP · ${cropModes[cropMode]}") {
            cropMode = (cropMode + 1) % cropModes.size
            cropButton.text = "CROP · ${cropModes[cropMode]}"
            renderPreview()
        }
        actions.addView(cropButton, LinearLayout.LayoutParams(0, 46, 1f).apply { leftMargin = 6 })
        actions.addView(toolButton("RESET") { resetControls() }, LinearLayout.LayoutParams(0, 46, 1f).apply { leftMargin = 6 })
        root.addView(actions)

        lookSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@PhotoEditorActivity, android.R.layout.simple_spinner_dropdown_item, looks)
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) = renderPreview()
            }
        }
        root.addView(settingRow("FILM LOOK", lookSpinner))
        strength = settingSeekBar(100)
        brightness = settingSeekBar(50)
        contrast = settingSeekBar(50)
        saturation = settingSeekBar(50)
        root.addView(settingRow("LOOK STRENGTH", strength, "0–100%"))
        root.addView(settingRow("BRIGHTNESS", brightness, "−50 to +50"))
        root.addView(settingRow("CONTRAST", contrast, "−50 to +50"))
        root.addView(settingRow("SATURATION", saturation, "−50 to +50"))
        straighten = settingSeekBar(100)
        cropHorizontal = settingSeekBar(50)
        cropVertical = settingSeekBar(50)
        root.addView(settingRow("STRAIGHTEN", straighten, "−10° to +10°"))
        root.addView(settingRow("CROP LEFT / RIGHT", cropHorizontal, "move framing"))
        root.addView(settingRow("CROP UP / DOWN", cropVertical, "move framing"))
        listOf(strength, brightness, contrast, saturation, straighten, cropHorizontal, cropVertical).forEach { bar ->
            bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {}
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) { renderPreview() }
            })
        }
        root.addView(toolButton("SAVE EDITED PHOTO") { saveEdited() })
        setContentView(root)
    }

    private fun settingSeekBar(initial: Int) = SeekBar(this).apply { max = 100; progress = initial }

    private fun settingRow(label: String, control: android.view.View, hint: String? = null): LinearLayout {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, 2, 0, 2) }
        row.addView(TextView(this).apply {
            text = if (hint == null) label else "$label  ($hint)"
            textSize = 9f; setTextColor(0xFFE9C49A.toInt())
        }, LinearLayout.LayoutParams(104, -2))
        row.addView(control, LinearLayout.LayoutParams(0, -2, 1f))
        return row
    }

    private fun toolButton(label: String, action: (android.view.View) -> Unit) = Button(this).apply {
        text = label; textSize = 9f; isAllCaps = false; setOnClickListener { action(it) }
    }

    private fun resetControls() {
        rotation = 0; cropMode = 0
        cropButton.text = "CROP · ${cropModes[cropMode]}"
        strength.progress = 100; brightness.progress = 50; contrast.progress = 50; saturation.progress = 50
        straighten.progress = 100; cropHorizontal.progress = 50; cropVertical.progress = 50
        lookSpinner.setSelection(0)
        renderPreview()
    }

    private fun decodeCorrected(uri: Uri): Bitmap =
        BitmapDecodeUtils.decodeExifCorrected(contentResolver, uri, maxLongEdge = 2560)

    private fun createEditedBitmap(settings: EditSettings): Bitmap {
        var bitmap = original?.copy(Bitmap.Config.ARGB_8888, false) ?: throw IllegalStateException("No image loaded")
        val totalRotation = settings.rotation.toFloat() + settings.straightenDegrees
        if (kotlin.math.abs(totalRotation) > 0.01f) {
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(totalRotation) }, true)
            if (rotated !== bitmap) bitmap.recycle()
            bitmap = rotated
        }
        bitmap = cropBitmap(bitmap, settings.cropMode, settings.cropHorizontal, settings.cropVertical)
        val look = settings.look
        if (look != "ORIGINAL" && settings.strength > 0) {
            val effected = GpuRetroLookProcessor.apply(bitmap, look, settings.strength / 100f)
            if (effected !== bitmap) bitmap.recycle()
            bitmap = effected
        }
        val brightnessValue = (settings.brightness - 50) * 2.0f
        val contrastValue = 0.5f + settings.contrast / 100f
        val saturationValue = settings.saturation / 50f
        if (brightnessValue != 0f || contrastValue != 1f || saturationValue != 1f) {
            val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            val sat = ColorMatrix().apply { setSaturation(saturationValue) }
            val c = contrastValue
            val offset = (1f - c) * 127.5f + brightnessValue
            val tone = ColorMatrix(floatArrayOf(
                c,0f,0f,0f,offset, 0f,c,0f,0f,offset, 0f,0f,c,0f,offset, 0f,0f,0f,1f,0f
            ))
            sat.postConcat(tone)
            Canvas(result).drawBitmap(bitmap, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG).apply { colorFilter = ColorMatrixColorFilter(sat) })
            bitmap.recycle()
            bitmap = result
        }
        return bitmap
    }

    private fun cropBitmap(source: Bitmap, mode: String, horizontal: Int, vertical: Int): Bitmap {
        if (mode == "Original") return source
        val target = when (mode) {
            "1:1" -> 1f
            "4:5" -> 4f / 5f
            "16:9" -> 16f / 9f
            "3:2" -> 3f / 2f
            "2:3" -> 2f / 3f
            else -> return source
        }
        val sourceRatio = source.width.toFloat() / source.height
        val width: Int
        val height: Int
        if (sourceRatio > target) { height = source.height; width = max(1, (height * target).toInt()) }
        else { width = source.width; height = max(1, (width / target).toInt()) }
        val x = ((source.width - width) * horizontal / 100f).toInt().coerceIn(0, source.width - width)
        val y = ((source.height - height) * vertical / 100f).toInt().coerceIn(0, source.height - height)
        val cropped = Bitmap.createBitmap(source, x, y, width, height)
        if (cropped !== source) source.recycle()
        return cropped
    }

    private fun renderPreview() {
        val source = original ?: return
        val token = renderToken.incrementAndGet()
        val settings = captureSettings()
        status.text = "Rendering preview…"
        worker.execute {
            try {
                val edited = createEditedBitmap(settings)
                runOnUiThread {
                    if (token == renderToken.get() && !isFinishing) {
                        val old = preview.drawable
                        preview.setImageBitmap(edited)
                        status.text = "Preview • original is untouched • ${edited.width} × ${edited.height}"
                        // ImageView owns the displayed bitmap; old preview is safe to recycle after replacement.
                        (old as? android.graphics.drawable.BitmapDrawable)?.bitmap?.let { if (it !== edited && it !== source && !it.isRecycled) it.recycle() }
                    } else edited.recycle()
                }
            } catch (error: Exception) {
                runOnUiThread { if (token == renderToken.get()) status.text = "Preview failed: ${error.localizedMessage}" }
            }
        }
    }

    private fun saveEdited() {
        if (original == null) { Toast.makeText(this, "Still loading image", Toast.LENGTH_SHORT).show(); return }
        val settings = captureSettings()
        status.text = "Saving edited copy…"
        worker.execute {
            var edited: Bitmap? = null
            var output: Uri? = null
            try {
                edited = createEditedBitmap(settings)
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "RetroCam_Edit_${timestamp()}.jpg")
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/RetroCam/Edited")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                output = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("Could not create image in media library")
                contentResolver.openOutputStream(output!!)?.use { stream ->
                    check(edited!!.compress(Bitmap.CompressFormat.JPEG, 94, stream)) { "JPEG encoding failed" }
                } ?: throw IllegalStateException("Could not open output file")
                contentResolver.update(output!!, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                runOnUiThread {
                    status.text = "Saved to Pictures/RetroCam/Edited"
                    Toast.makeText(this, "Edited photo saved. Original preserved.", Toast.LENGTH_LONG).show()
                }
            } catch (error: Exception) {
                output?.let { contentResolver.delete(it, null, null) }
                runOnUiThread { status.text = "Save failed: ${error.localizedMessage}" }
            } finally { edited?.recycle() }
        }
    }

    private fun timestamp() = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())

    override fun onDestroy() {
        renderToken.incrementAndGet()
        // Do not recycle the source while a queued render may still be reading it. Once the
        // activity and worker references are gone, the bitmap is reclaimed normally.
        original = null
        worker.shutdownNow()
        super.onDestroy()
    }

    companion object { const val EXTRA_URI = "retrocam.photo.uri" }
}
