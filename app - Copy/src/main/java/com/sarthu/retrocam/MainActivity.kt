package com.sarthu.retrocam

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.app.AlertDialog
import android.net.Uri
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.widget.FrameLayout
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.MediaStore
import android.view.Gravity
import android.view.MotionEvent
import android.widget.*
import android.text.InputType
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraEffect
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.media3.effect.Media3Effect
import androidx.media3.common.util.UnstableApi
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors

/**
 * RetroCam's native camera foundation. CameraX chooses the device camera implementation,
 * while this screen adapts controls to the capabilities actually reported by the device.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var gridOverlay: View
    private lateinit var lookLabel: TextView
    private lateinit var filterStrengthLabel: TextView
    private lateinit var recordButton: Button
    private lateinit var qualityButton: Button
    private lateinit var zoomLabel: TextView
    private lateinit var timerButton: Button
    private lateinit var exposureLabel: TextView
    private lateinit var exposureSeek: SeekBar
    private var captureTimerSeconds = 0
    private var captureCountDown: CountDownTimer? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var cameraMediaEffect: Media3Effect? = null
    private var lens = CameraSelector.LENS_FACING_BACK
    private var selectedLook = "ORIGINAL"
    private var filterIntensity = 1f
    private var torchEnabled = false
    private var flashEnabled = false
    private var selectedQuality = Quality.HD
    private var recordingAudio = true
    private val imageWorker = Executors.newSingleThreadExecutor()
    private val photoPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) openPhotoEditor(uri)
    }
    private val videoPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) showVideoTrimDialog(uri)
    }

    private val permissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val cameraGranted = result[Manifest.permission.CAMERA] == true ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (cameraGranted) startCamera()
        else Toast.makeText(this, "Camera permission is required", Toast.LENGTH_LONG).show()
        // Microphone is optional: camera preview and silent recording remain available if denied.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = 0xFF100F0E.toInt()
        window.navigationBarColor = 0xFF100F0E.toInt()
        buildInterface()
        val cameraGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val audioGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (cameraGranted) {
            startCamera()
            if (!audioGranted) permissionRequest.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
        } else permissionRequest.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
    }

    private fun buildInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF100F0E.toInt())
            setPadding(16, 12, 16, 10)
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply {
            text = "RETROCAM"; textSize = 21f; letterSpacing = .18f
            setTextColor(0xFFF3E7D8.toInt())
        }, LinearLayout.LayoutParams(0, -2, 1f))
        qualityButton = makeButton("QUALITY · HD") { cycleQuality() }
        header.addView(qualityButton)
        header.addView(makeButton("GRID") {
            gridOverlay.visibility = if (gridOverlay.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        })
        header.addView(makeButton("FLIP") { flipCamera() })
        root.addView(header)

        previewView = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
        val previewFrame = FrameLayout(this)
        previewFrame.addView(previewView, FrameLayout.LayoutParams(-1, -1))
        gridOverlay = object : View(this) {
            private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x99FFFFFF.toInt()
                strokeWidth = resources.displayMetrics.density
                style = Paint.Style.STROKE
            }
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val thirdW = width / 3f
                val thirdH = height / 3f
                canvas.drawLine(thirdW, 0f, thirdW, height.toFloat(), linePaint)
                canvas.drawLine(thirdW * 2f, 0f, thirdW * 2f, height.toFloat(), linePaint)
                canvas.drawLine(0f, thirdH, width.toFloat(), thirdH, linePaint)
                canvas.drawLine(0f, thirdH * 2f, width.toFloat(), thirdH * 2f, linePaint)
            }
        }.apply { visibility = View.GONE }
        previewFrame.addView(gridOverlay, FrameLayout.LayoutParams(-1, -1))
        root.addView(previewFrame, LinearLayout.LayoutParams(-1, 0, 1f).apply {
            topMargin = 12; bottomMargin = 10
        })
        previewView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) focusAt(event.x, event.y)
            true
        }

        lookLabel = TextView(this).apply {
            text = "LOOK  ·  ORIGINAL  ·  STILL PHOTO EFFECTS READY"
            setTextColor(0xFFE9C49A.toInt()); textSize = 10f; gravity = Gravity.CENTER
        }
        root.addView(lookLabel)
        val looks = listOf("ORIGINAL", "VHS", "DV", "SUPER 8", "8MM", "16MM", "35MM", "CCD", "PORTRA 400", "GOLD 200", "FADED", "B&W")
        val lookRow = LinearLayout(this).apply { gravity = Gravity.CENTER }
        looks.forEach { look ->
            lookRow.addView(TextView(this).apply {
                text = look; textSize = 9f; gravity = Gravity.CENTER
                setTextColor(if (look == selectedLook) 0xFFFFD6A7.toInt() else 0xFFB7AAA0.toInt())
                setPadding(5, 12, 5, 12)
                setOnClickListener {
                    selectedLook = look
                    updateLiveLook()
                    lookLabel.text = "LOOK  ·  $look  ·  ${if (cameraMediaEffect == null) "STILL PHOTO ONLY" else "LIVE PREVIEW + VIDEO"}"
                    for (i in 0 until lookRow.childCount) {
                        val item = lookRow.getChildAt(i) as TextView
                        item.setTextColor(if (item.text == selectedLook) 0xFFFFD6A7.toInt() else 0xFFB7AAA0.toInt())
                    }
                }
            })
        }
        val lookScroller = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(lookRow)
        }
        root.addView(lookScroller)
        filterStrengthLabel = TextView(this).apply {
            text = "LOOK STRENGTH  100%"
            textSize = 10f
            setTextColor(0xFFE9C49A.toInt())
            gravity = Gravity.CENTER
        }
        root.addView(filterStrengthLabel)
        root.addView(SeekBar(this).apply {
            max = 100
            progress = 100
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    filterIntensity = progress / 100f
                    filterStrengthLabel.text = "LOOK STRENGTH  ${progress}%"
                    updateLiveLook()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        })

        val zoomHeader = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        zoomLabel = TextView(this).apply { text = "ZOOM  1.0×"; textSize = 10f; setTextColor(0xFFE9C49A.toInt()) }
        zoomHeader.addView(zoomLabel, LinearLayout.LayoutParams(0, -2, 1f))
        timerButton = makeButton("TIMER · OFF") { cycleTimer() }
        zoomHeader.addView(timerButton)
        zoomHeader.addView(makeButton("TORCH") { toggleTorch() })
        zoomHeader.addView(makeButton("FLASH") { toggleFlash() })
        root.addView(zoomHeader)
        val zoom = SeekBar(this).apply {
            max = 100
            progress = 0
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    val info = camera?.cameraInfo ?: return
                    val range = info.zoomState.value ?: return
                    val ratio = range.minZoomRatio + (range.maxZoomRatio - range.minZoomRatio) * progress / 100f
                    camera?.cameraControl?.setZoomRatio(ratio)
                    zoomLabel.text = "ZOOM  ${String.format(Locale.US, "%.1f", ratio)}×"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        root.addView(zoom)
        exposureLabel = TextView(this).apply {
            text = "EXPOSURE COMPENSATION · AUTO"
            textSize = 9f; gravity = Gravity.CENTER; setTextColor(0xFFE9C49A.toInt())
        }
        root.addView(exposureLabel)
        exposureSeek = SeekBar(this).apply {
            max = 2; min = -2; progress = 0
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    val state = camera?.cameraInfo?.exposureState ?: return
                    val ev = progress * state.exposureCompensationStep.toFloat()
                    exposureLabel.text = "EXPOSURE COMPENSATION · ${String.format(Locale.US, "%+.1f", ev)} EV"
                    camera?.cameraControl?.setExposureCompensationIndex(progress)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        root.addView(exposureSeek)

        val actions = LinearLayout(this).apply { gravity = Gravity.CENTER }
        actions.addView(makeButton("PHOTO") { takePhoto() }, LinearLayout.LayoutParams(0, 54, 1f))
        recordButton = makeButton("●  RECORD") { if (recording == null) startRecording() else stopRecording() }
        actions.addView(recordButton, LinearLayout.LayoutParams(0, 54, 1f).apply { leftMargin = 8 })
        root.addView(actions)
        val studioActions = LinearLayout(this).apply { gravity = Gravity.CENTER; setPadding(0, 6, 0, 0) }
        studioActions.addView(makeButton("IMPORT PHOTO · STUDIO") { photoPicker.launch("image/*") }, LinearLayout.LayoutParams(0, 46, 1f))
        studioActions.addView(makeButton("TIMELINE") { startActivity(Intent(this, TimelineEditorActivity::class.java)) }, LinearLayout.LayoutParams(0, 46, 1f).apply { leftMargin = 8 })
        root.addView(studioActions)
        root.addView(makeButton("OPEN RETROCAM MEDIA LIBRARY") { startActivity(Intent(this, MediaLibraryActivity::class.java)) })
        root.addView(makeButton("IMPORT VIDEO · QUICK TRIM") { videoPicker.launch("video/*") })
        root.addView(TextView(this).apply {
            text = "ON-DEVICE STORAGE  •  NO ACCOUNT  •  NO SUBSCRIPTION"
            textSize = 9f; gravity = Gravity.CENTER; setTextColor(0xFF887B70.toInt()); setPadding(0, 10, 0, 0)
        })
        setContentView(root)
    }

    private fun makeButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label; textSize = 10f; isAllCaps = false; setOnClickListener { action() }
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            if (isFinishing || isDestroyed) return@addListener
            val provider = try {
                future.get()
            } catch (error: Exception) {
                Toast.makeText(this, "Camera service unavailable: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
                return@addListener
            }
            val selector = CameraSelector.Builder().requireLensFacing(lens).build()
            if (!provider.hasCamera(selector)) {
                Toast.makeText(this, "This camera is not available on this device", Toast.LENGTH_LONG).show()
                return@addListener
            }
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val imageUseCase = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setFlashMode(if (flashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF)
                .build()
            imageCapture = imageUseCase

            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(
                    selectedQuality,
                    FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
                )).build()
            val videoUseCase = VideoCapture.withOutput(recorder)
            videoCapture = videoUseCase
            provider.unbindAll()
            cameraMediaEffect?.close()
            cameraMediaEffect = null
            try {
                val effect = createCameraMediaEffect()
                cameraMediaEffect = effect
                val group = UseCaseGroup.Builder()
                    .addUseCase(preview)
                    .addUseCase(imageUseCase)
                    .addUseCase(videoUseCase)
                    .addEffect(effect)
                    .build()
                camera = provider.bindToLifecycle(this, selector, group)
                lookLabel.text = "LOOK  ·  $selectedLook  ·  LIVE PREVIEW + VIDEO"
            } catch (effectError: Exception) {
                cameraMediaEffect?.close()
                cameraMediaEffect = null
                try {
                    provider.unbindAll()
                    camera = provider.bindToLifecycle(this, selector, preview, imageUseCase, videoUseCase)
                    lookLabel.text = "LOOK  ·  $selectedLook  ·  STILL PHOTO ONLY ON THIS DEVICE"
                    Toast.makeText(this, "Live GPU effects unavailable here; normal camera mode is active.", Toast.LENGTH_LONG).show()
                } catch (cameraError: Exception) {
                    Toast.makeText(this, "Camera could not start: ${cameraError.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
            updateZoomLabel()
            updateExposureControl()
        }, ContextCompat.getMainExecutor(this))
    }

    @OptIn(UnstableApi::class)
    private fun createCameraMediaEffect(): Media3Effect {
        return Media3Effect(
            this,
            CameraEffect.PREVIEW or CameraEffect.VIDEO_CAPTURE,
            ContextCompat.getMainExecutor(this)
        ) { error ->
            android.util.Log.e("RetroCam", "Live camera effect failed", error)
            runOnUiThread {
                Toast.makeText(this, "Live look processing failed; camera may need to restart", Toast.LENGTH_LONG).show()
            }
        }.also { it.setEffects(RetroVideoEffects.forLook(selectedLook, filterIntensity)) }
    }

    @OptIn(UnstableApi::class)
    private fun updateLiveLook() {
        val effect = cameraMediaEffect ?: return
        try {
            effect.setEffects(RetroVideoEffects.forLook(selectedLook, filterIntensity))
        } catch (error: Exception) {
            Toast.makeText(this, "Could not apply live look: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }


    private fun flipCamera() {
        if (recording != null) { Toast.makeText(this, "Stop recording before switching camera", Toast.LENGTH_SHORT).show(); return }
        lens = if (lens == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
        torchEnabled = false
        startCamera()
    }

    private fun toggleTorch() {
        val info = camera?.cameraInfo ?: return
        if (!info.hasFlashUnit()) {
            Toast.makeText(this, "Flash/torch is not available on this camera", Toast.LENGTH_SHORT).show(); return
        }
        torchEnabled = !torchEnabled
        camera?.cameraControl?.enableTorch(torchEnabled)
    }

    private fun toggleFlash() {
        flashEnabled = !flashEnabled
        imageCapture?.flashMode = if (flashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
        Toast.makeText(this, if (flashEnabled) "Photo flash enabled" else "Photo flash disabled", Toast.LENGTH_SHORT).show()
    }

    private fun cycleQuality() {
        selectedQuality = when (selectedQuality) {
            Quality.SD -> Quality.HD
            Quality.HD -> Quality.FHD
            Quality.FHD -> Quality.UHD
            else -> Quality.SD
        }
        qualityButton.text = "QUALITY · ${qualityName(selectedQuality)}"
        Toast.makeText(this, "Preferred recording quality: ${qualityName(selectedQuality)}. Device fallback applies.", Toast.LENGTH_LONG).show()
        if (recording == null) startCamera()
    }

    private fun qualityName(q: Quality) = when (q) {
        Quality.SD -> "SD"; Quality.HD -> "HD"; Quality.FHD -> "Full HD"; Quality.UHD -> "4K"; else -> "Auto"
    }

    private fun focusAt(x: Float, y: Float) {
        val factory = SurfaceOrientedMeteringPointFactory(previewView.width.toFloat(), previewView.height.toFloat())
        val point = factory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .setAutoCancelDuration(3, TimeUnit.SECONDS).build()
        camera?.cameraControl?.startFocusAndMetering(action)
    }

    private fun updateZoomLabel() {
        val state = camera?.cameraInfo?.zoomState?.value ?: return
        zoomLabel.text = "ZOOM  ${String.format(Locale.US, "%.1f", state.zoomRatio)}×"
    }

    private fun cycleTimer() {
        if (captureCountDown != null) {
            captureCountDown?.cancel()
            captureCountDown = null
            updateTimerButton()
            Toast.makeText(this, "Capture timer cancelled", Toast.LENGTH_SHORT).show()
            return
        }
        captureTimerSeconds = when (captureTimerSeconds) { 0 -> 3; 3 -> 10; else -> 0 }
        updateTimerButton()
    }

    private fun updateTimerButton() {
        timerButton.text = when (captureTimerSeconds) {
            3 -> "TIMER · 3S"
            10 -> "TIMER · 10S"
            else -> "TIMER · OFF"
        }
    }

    private fun updateExposureControl() {
        val state = camera?.cameraInfo?.exposureState ?: return
        val range = state.exposureCompensationRange
        exposureSeek.min = range.lower
        exposureSeek.max = range.upper
        exposureSeek.progress = state.exposureCompensationIndex.coerceIn(range.lower, range.upper)
        exposureSeek.isEnabled = range.lower != range.upper
        val ev = exposureSeek.progress * state.exposureCompensationStep.toFloat()
        exposureLabel.text = if (exposureSeek.isEnabled) {
            "EXPOSURE COMPENSATION · ${String.format(Locale.US, "%+.1f", ev)} EV"
        } else "EXPOSURE COMPENSATION · AUTO ONLY"
    }

    private fun takePhoto() {
        if (captureCountDown != null) {
            cycleTimer()
            return
        }
        if (captureTimerSeconds <= 0) {
            takePhotoNow()
            return
        }
        val seconds = captureTimerSeconds
        timerButton.text = "CANCEL · ${seconds}S"
        captureCountDown = object : CountDownTimer(seconds * 1000L, 250L) {
            override fun onTick(millisUntilFinished: Long) {
                timerButton.text = "CANCEL · ${(millisUntilFinished + 999L) / 1000L}S"
            }
            override fun onFinish() {
                captureCountDown = null
                updateTimerButton()
                takePhotoNow()
            }
        }.start()
    }

    private fun takePhotoNow() {
        val capture = imageCapture ?: return
        val lookAtCapture = selectedLook
        val intensityAtCapture = filterIntensity
        val name = "RetroCam_${timestamp()}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/RetroCam")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val options = ImageCapture.OutputFileOptions.Builder(
            contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
        ).build()
        capture.takePicture(options, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                val uri = output.savedUri
                if (uri == null) {
                    Toast.makeText(this@MainActivity, "Photo capture finished", Toast.LENGTH_SHORT).show()
                } else if (lookAtCapture == "ORIGINAL" || intensityAtCapture <= 0.001f) {
                    contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                    Toast.makeText(this@MainActivity, "Photo saved to Pictures/RetroCam", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "Applying $lookAtCapture film look…", Toast.LENGTH_SHORT).show()
                    imageWorker.execute {
                        var source: android.graphics.Bitmap? = null
                        var edited: android.graphics.Bitmap? = null
                        var filteredUri: Uri? = null
                        var effectSucceeded = false
                        try {
                            source = BitmapDecodeUtils.decodeExifCorrected(contentResolver, uri, maxLongEdge = 3840)
                            edited = GpuRetroLookProcessor.apply(source!!, lookAtCapture, intensityAtCapture)
                            // Never overwrite the only copy. Publish a new filtered item first, then
                            // remove the unfiltered capture only after the new JPEG is committed.
                            val filteredName = name.removeSuffix(".jpg") + "_${lookAtCapture.replace(' ', '_')}.jpg"
                            val filteredValues = ContentValues().apply {
                                put(MediaStore.MediaColumns.DISPLAY_NAME, filteredName)
                                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/RetroCam")
                                put(MediaStore.MediaColumns.IS_PENDING, 1)
                            }
                            filteredUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, filteredValues)
                                ?: throw IllegalStateException("Could not create filtered photo")
                            contentResolver.openOutputStream(filteredUri!!, "w")?.use { stream ->
                                check(edited!!.compress(android.graphics.Bitmap.CompressFormat.JPEG, 94, stream)) { "JPEG encoding failed" }
                            } ?: throw IllegalStateException("Could not write filtered photo")
                            contentResolver.update(filteredUri!!, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                            contentResolver.delete(uri, null, null)
                            effectSucceeded = true
                        } catch (e: Exception) {
                            android.util.Log.e("RetroCam", "Still photo effect failed", e)
                            filteredUri?.let { contentResolver.delete(it, null, null) }
                        } finally {
                            source?.recycle()
                            if (edited !== source) edited?.recycle()
                            if (!effectSucceeded) {
                                contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                            }
                        }
                        runOnUiThread {
                            Toast.makeText(
                                this@MainActivity,
                                if (effectSucceeded) "$lookAtCapture photo saved" else "Original photo saved; film effect could not be applied",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
            override fun onError(exception: ImageCaptureException) {
                Toast.makeText(this@MainActivity, "Photo failed: ${exception.message}", Toast.LENGTH_LONG).show()
            }
        })
    }


    private fun openPhotoEditor(uri: Uri) {
        startActivity(Intent(this, PhotoEditorActivity::class.java).putExtra(PhotoEditorActivity.EXTRA_URI, uri))
    }


    private fun showVideoTrimDialog(uri: Uri) {
        val retriever = MediaMetadataRetriever()
        val durationMs = try {
            retriever.setDataSource(this, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Exception) { 0L } finally { retriever.release() }
        if (durationMs <= 0L) {
            Toast.makeText(this, "Could not read video duration", Toast.LENGTH_LONG).show(); return
        }
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 12, 48, 0) }
        val start = EditText(this).apply { hint = "Start seconds (0)"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL; setText("0") }
        val end = EditText(this).apply { hint = "End seconds (${durationMs / 1000})"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL; setText(String.format(Locale.US, "%.1f", durationMs / 1000f)) }
        panel.addView(start); panel.addView(end)
        AlertDialog.Builder(this).setTitle("Quick video trim")
            .setMessage("Stream-copy trim keeps original quality. Exact cuts follow source keyframes.")
            .setView(panel).setNegativeButton("Cancel", null)
            .setPositiveButton("Export clip") { _, _ ->
                val a = start.text.toString().toDoubleOrNull() ?: 0.0
                val b = end.text.toString().toDoubleOrNull() ?: durationMs / 1000.0
                if (a < 0 || b <= a || b * 1000 > durationMs + 100) {
                    Toast.makeText(this, "Enter a valid trim range", Toast.LENGTH_SHORT).show(); return@setPositiveButton
                }
                imageWorker.execute {
                    val temp = File(cacheDir, "retrocam_trim_${System.currentTimeMillis()}.mp4")
                    var outUri: Uri? = null
                    try {
                        VideoTrimmer.trim(this, uri, (a * 1000).toLong(), (b * 1000).toLong(), temp)
                        val values = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, "RetroCam_Trim_${timestamp()}.mp4")
                            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/RetroCam/Edited")
                            put(MediaStore.MediaColumns.IS_PENDING, 1)
                        }
                        outUri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                            ?: throw IllegalStateException("Could not create output video")
                        contentResolver.openOutputStream(outUri!!, "w")?.use { output -> temp.inputStream().use { it.copyTo(output) } }
                            ?: throw IllegalStateException("Could not save output video")
                        contentResolver.update(outUri!!, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                        runOnUiThread { Toast.makeText(this, "Trimmed clip saved to Movies/RetroCam/Edited", Toast.LENGTH_LONG).show() }
                    } catch (e: Exception) {
                        outUri?.let { contentResolver.delete(it, null, null) }
                        runOnUiThread { Toast.makeText(this, "Trim failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show() }
                    } finally { temp.delete() }
                }
            }.show()
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_VIEW).apply { setType("image/*"); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        try { startActivity(intent) } catch (_: Exception) {
            Toast.makeText(this, "No gallery app found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startRecording() {
        val capture = videoCapture ?: run {
            Toast.makeText(this, "Camera is not ready for recording", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val name = "RetroCam_${timestamp()}.mp4"
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/RetroCam")
            }
            val output = MediaStoreOutputOptions.Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                .setContentValues(values).build()
            var pending = capture.output.prepareRecording(this, output)
            if (recordingAudio && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                pending = pending.withAudioEnabled()
            }
            recording = pending.start(ContextCompat.getMainExecutor(this)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> recordButton.text = "■  STOP"
                    is VideoRecordEvent.Finalize -> {
                        recording = null
                        recordButton.text = "●  RECORD"
                        Toast.makeText(this, if (event.hasError()) "Recording failed (${event.error})" else "Saved to Movies/RetroCam", Toast.LENGTH_LONG).show()
                    }
                }
            }
        } catch (error: Exception) {
            recording = null
            recordButton.text = "●  RECORD"
            Toast.makeText(this, "Could not start recording: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopRecording() {
        recording?.stop()
        recordButton.text = "SAVING…"
    }

    private fun timestamp() = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())

    override fun onDestroy() {
        captureCountDown?.cancel()
        captureCountDown = null
        recording?.stop()
        cameraMediaEffect?.close()
        cameraMediaEffect = null
        imageWorker.shutdown()
        super.onDestroy()
    }
}
