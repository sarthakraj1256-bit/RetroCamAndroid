package com.sarthu.retrocam

import android.content.ContentValues
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.graphics.Typeface
import androidx.media3.common.MediaItem
import androidx.media3.common.C
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.common.Effects
import androidx.media3.common.audio.DefaultGainProvider
import androidx.media3.common.audio.GainProcessor
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.audio.ToInt16PcmAudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.effect.TextOverlay
import android.view.Gravity
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import android.media.MediaMetadataRetriever

/**
 * Multi-clip sequence editor. Clips are concatenated in order into one MP4 by Media3 Transformer.
 * Embedded audio travels with each clip. Each clip has an independent audio level and fade, and
 * one optional looping background-music sequence can be mixed over the video. A static title can
 * be overlaid on the sequence; transitions, animated text and additional audio tracks remain future work.
 */
@OptIn(UnstableApi::class)
class TimelineEditorActivity : AppCompatActivity() {
    private data class Clip(
        val uri: Uri,
        val label: String,
        val durationMs: Long,
        var startMs: Long = 0L,
        var endMs: Long = -1L,
        var speed: Float = 1f,
        var audioGain: Float = 1f,
        var audioFadeMs: Long = 0L
    )
    private val clips = mutableListOf<Clip>()
    private lateinit var rows: LinearLayout
    private lateinit var status: TextView
    private var activeTransformer: Transformer? = null
    private var activeExportFile: File? = null
    private var isPublishingExport = false
    private var backgroundAudioUri: Uri? = null
    private lateinit var lookSpinner: Spinner
    private lateinit var lookStrength: SeekBar
    private lateinit var clipAudioLevel: SeekBar
    private lateinit var musicAudioLevel: SeekBar
    private lateinit var musicFade: SeekBar
    private lateinit var titleInput: EditText
    private val videoLooks = listOf("ORIGINAL", "VHS", "DV", "SUPER 8", "8MM", "16MM", "35MM", "CCD", "PORTRA 400", "GOLD 200", "FADED", "B&W")
    private val musicPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        backgroundAudioUri = uri
        render()
        if (uri != null) Toast.makeText(this, "Background music added. It will loop to the end of the montage.", Toast.LENGTH_LONG).show()
    }
    private val picker = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        uris.forEach { uri ->
            val retriever = MediaMetadataRetriever()
            val duration = try {
                retriever.setDataSource(this, uri)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            } catch (_: Exception) { 0L } finally { retriever.release() }
            if (duration > 0L) clips.add(Clip(uri, "Clip ${clips.size + 1}", duration))
            else Toast.makeText(this, "Skipped a video whose duration could not be read", Toast.LENGTH_SHORT).show()
        }
        render()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = 0xFF100F0E.toInt()
        window.navigationBarColor = 0xFF100F0E.toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            setBackgroundColor(0xFF100F0E.toInt())
        }
        root.addView(TextView(this).apply {
            text = "RETROCAM  /  VIDEO TIMELINE"
            textSize = 20f
            setTextColor(0xFFF3E7D8.toInt())
        })
        status = TextView(this).apply {
            text = "Add multiple clips, arrange the order and set trim ranges."
            textSize = 11f
            setTextColor(0xFFE9C49A.toInt())
            setPadding(0, 8, 0, 12)
        }
        root.addView(status)
        val add = Button(this).apply {
            text = "+ ADD VIDEO CLIPS"
            setOnClickListener { picker.launch("video/*") }
        }
        root.addView(add)
        root.addView(Button(this).apply {
            text = "+ ADD BACKGROUND MUSIC"
            setOnClickListener { musicPicker.launch("audio/*") }
        })
        root.addView(Button(this).apply {
            text = "CLEAR BACKGROUND MUSIC"
            setOnClickListener { backgroundAudioUri = null; render() }
        })
        titleInput = EditText(this).apply {
            hint = "Optional title overlay on every clip"
            setTextColor(0xFFF3E7D8.toInt())
            setHintTextColor(0xFF887B70.toInt())
            setSingleLine(true)
            maxLines = 1
        }
        root.addView(titleInput)
        lookSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@TimelineEditorActivity, android.R.layout.simple_spinner_dropdown_item, videoLooks)
        }
        root.addView(TextView(this).apply { text = "APPLY GPU FILM LOOK TO ALL CLIPS"; textSize = 10f; setTextColor(0xFFE9C49A.toInt()) })
        root.addView(lookSpinner)
        lookStrength = SeekBar(this).apply { max = 100; progress = 100 }
        root.addView(lookStrength)
        root.addView(TextView(this).apply { text = "ORIGINAL CLIP AUDIO LEVEL"; textSize = 10f; setTextColor(0xFFE9C49A.toInt()) })
        clipAudioLevel = SeekBar(this).apply { max = 100; progress = 100 }
        root.addView(clipAudioLevel)
        root.addView(TextView(this).apply { text = "BACKGROUND MUSIC LEVEL"; textSize = 10f; setTextColor(0xFFE9C49A.toInt()) })
        musicAudioLevel = SeekBar(this).apply { max = 100; progress = 70 }
        root.addView(musicAudioLevel)
        root.addView(TextView(this).apply { text = "MUSIC FADE IN / OUT · 0–5 SECONDS"; textSize = 10f; setTextColor(0xFFE9C49A.toInt()) })
        musicFade = SeekBar(this).apply { max = 100; progress = 30 }
        root.addView(musicFade)
        val scroll = ScrollView(this)
        rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(rows)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(Button(this).apply {
            text = "EXPORT ONE COMBINED VIDEO"
            setOnClickListener { exportAll() }
        })
        setContentView(root)
        render()
    }

    private fun render() {
        rows.removeAllViews()
        status.text = "${clips.size} clip(s) in sequence • ${if (backgroundAudioUri == null) "no music bed" else "background music attached"} • order, trims and playback speed editable"
        if (clips.isEmpty()) {
            rows.addView(TextView(this).apply {
                text = "Your timeline is empty.\nAdd two or more videos to start arranging a sequence."
                gravity = Gravity.CENTER
                setTextColor(0xFFB7AAA0.toInt())
                setPadding(20, 55, 20, 55)
            })
        }
        clips.forEachIndexed { index, clip ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(12, 10, 12, 10)
                setBackgroundColor(0xFF211E1B.toInt())
            }
            card.addView(TextView(this).apply {
                text = "${index + 1}. ${clip.label}"
                textSize = 14f
                setTextColor(0xFFF3E7D8.toInt())
            })
            card.addView(TextView(this).apply {
                text = "Duration: ${clip.durationMs / 1000.0}s  •  Speed: ${clip.speed}×  •  Audio: ${(clip.audioGain * 100).toInt()}%  •  Start: ${clip.startMs / 1000.0}s  •  End: ${if (clip.endMs < 0) "source end" else "${clip.endMs / 1000.0}s"}"
                textSize = 10f
                setTextColor(0xFFB7AAA0.toInt())
                setPadding(0, 4, 0, 8)
            })
            val actions = LinearLayout(this).apply { gravity = Gravity.END }
            actions.addView(smallButton("UP") { if (index > 0) { val c = clips.removeAt(index); clips.add(index - 1, c); render() } })
            actions.addView(smallButton("DOWN") { if (index < clips.lastIndex) { val c = clips.removeAt(index); clips.add(index + 1, c); render() } })
            actions.addView(smallButton("TRIM") { editTrim(index) })
            actions.addView(smallButton("SPEED") { editSpeed(index) })
            actions.addView(smallButton("AUDIO") { editClipAudio(index) })
            actions.addView(smallButton("REMOVE") { clips.removeAt(index); render() })
            card.addView(actions)
            rows.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 8 })
        }
    }

    private fun smallButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 9f
        setOnClickListener { action() }
    }

    private fun editTrim(index: Int) {
        val clip = clips[index]
        val start = EditText(this).apply {
            hint = "Start seconds"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText((clip.startMs / 1000.0).toString())
        }
        val end = EditText(this).apply {
            hint = "End seconds (blank = source end)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(if (clip.endMs < 0) "" else (clip.endMs / 1000.0).toString())
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(35, 10, 35, 0)
            addView(start); addView(end)
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Trim ${clip.label}")
            .setMessage("The combined export uses Media3 decoding/encoding so trim boundaries can be rendered accurately. Original clip audio is retained.")
            .setView(panel)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save range") { _, _ ->
                val a = start.text.toString().toDoubleOrNull() ?: 0.0
                val b = end.text.toString().toDoubleOrNull()
                val startMs = (a * 1000.0).toLong()
                val endMs = if (b == null) -1L else (b * 1000.0).toLong()
                if (!TimelineMath.isValidTrim(clip.durationMs, startMs, endMs)) {
                    Toast.makeText(this, "Trim range must be inside the ${clip.durationMs / 1000.0}s source clip", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                clip.startMs = startMs
                clip.endMs = endMs
                render()
            }.show()
    }

    private fun editSpeed(index: Int) {
        val clip = clips[index]
        val choices = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 3f)
        val labels = choices.map { "${it}×" }.toTypedArray()
        val selected = choices.indexOf(clip.speed).coerceAtLeast(0)
        android.app.AlertDialog.Builder(this)
            .setTitle("Playback speed · ${clip.label}")
            .setSingleChoiceItems(labels, selected) { dialog, which ->
                clip.speed = choices[which]
                dialog.dismiss()
                render()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun editClipAudio(index: Int) {
        val clip = clips[index]
        val volume = SeekBar(this).apply { max = 100; progress = (clip.audioGain * 100).toInt() }
        val fade = SeekBar(this).apply { max = 3000; progress = clip.audioFadeMs.toInt().coerceIn(0, 3000) }
        val volumeLabel = TextView(this).apply { text = "Clip audio: ${volume.progress}%" }
        val fadeLabel = TextView(this).apply { text = "Fade in/out: ${fade.progress / 1000f}s each" }
        volume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) { volumeLabel.text = "Clip audio: $value%" }
            override fun onStartTrackingTouch(bar: SeekBar?) {}
            override fun onStopTrackingTouch(bar: SeekBar?) {}
        })
        fade.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) { fadeLabel.text = "Fade in/out: ${value / 1000f}s each" }
            override fun onStartTrackingTouch(bar: SeekBar?) {}
            override fun onStopTrackingTouch(bar: SeekBar?) {}
        })
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 8, 32, 0)
            addView(volumeLabel); addView(volume)
            addView(fadeLabel); addView(fade)
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Audio · ${clip.label}")
            .setMessage("The master clip-audio slider still controls the overall level. These settings apply only to this clip.")
            .setView(panel)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save audio settings") { _, _ ->
                clip.audioGain = volume.progress / 100f
                clip.audioFadeMs = fade.progress.toLong()
                render()
            }
            .show()
    }

    private fun exportAll() {
        if (clips.isEmpty()) {
            Toast.makeText(this, "Add clips first", Toast.LENGTH_SHORT).show()
            return
        }
        if (activeTransformer != null || isPublishingExport) {
            Toast.makeText(this, "An export is already running or being saved", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val editedItems = clips.map { clip ->
                require(TimelineMath.isValidTrim(clip.durationMs, clip.startMs, clip.endMs)) {
                    "A clip has an invalid trim range: ${clip.label}"
                }
                val clipping = MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(clip.startMs)
                    .apply { if (clip.endMs >= 0L) setEndPositionMs(clip.endMs) }
                    .build()
                val mediaItem = MediaItem.Builder()
                    .setUri(clip.uri)
                    .setClippingConfiguration(clipping)
                    .build()
                val clipEffects = RetroVideoEffects.forLook(videoLooks[lookSpinner.selectedItemPosition], lookStrength.progress / 100f).toMutableList()
                val title = titleInput.text.toString().trim()
                if (title.isNotEmpty()) clipEffects += createTitleOverlay(title)
                val effectiveClipGain = (clipAudioLevel.progress / 100f * clip.audioGain).coerceIn(0f, 1f)
                val sourceEnd = TimelineMath.resolvedEndMs(clip.durationMs, clip.endMs)
                val sourceDurationMs = sourceEnd - clip.startMs
                val outputDurationMs = TimelineMath.outputDurationMs(
                    clip.durationMs, clip.startMs, clip.endMs, clip.speed
                )
                val clipGainProvider = createFadeGainProvider(effectiveClipGain, outputDurationMs, clip.audioFadeMs)
                val clipAudio = audioProcessorsForGain(effectiveClipGain, clipGainProvider)
                val speedProvider = object : SpeedProvider {
                    override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = C.TIME_UNSET
                    override fun getSpeed(timeUs: Long): Float = clip.speed
                }
                EditedMediaItem.Builder(mediaItem)
                    .setSpeed(speedProvider)
                    .setEffects(Effects(clipAudio, clipEffects))
                    .build()
            }
            val videoSequence = EditedMediaItemSequence.withAudioAndVideoFrom(editedItems)
            val sequences = mutableListOf(videoSequence)
            backgroundAudioUri?.let { audioUri ->
                val totalDurationUs = TimelineMath.totalOutputDurationUs(clips.map { clip ->
                    Triple(clip.durationMs, clip.startMs to clip.endMs, clip.speed)
                })
                val fadeUs = (musicFade.progress * 50_000L).coerceAtMost(totalDurationUs / 2L)
                val musicProvider = DefaultGainProvider.Builder(musicAudioLevel.progress / 100f).apply {
                    if (fadeUs > 0L) {
                        addFadeAt(0L, fadeUs, DefaultGainProvider.FADE_IN_LINEAR)
                        addFadeAt((totalDurationUs - fadeUs).coerceAtLeast(0L), fadeUs, DefaultGainProvider.FADE_OUT_LINEAR)
                    }
                }.build()
                val musicAudio = audioProcessorsForGain(musicAudioLevel.progress / 100f, musicProvider)
                val musicItem = EditedMediaItem.Builder(MediaItem.fromUri(audioUri))
                    .setEffects(Effects(musicAudio, emptyList()))
                    .build()
                val loopingMusic = EditedMediaItemSequence.withAudioFrom(listOf(musicItem))
                    .buildUpon()
                    .setIsLooping(true)
                    .build()
                sequences += loopingMusic
            }
            val composition = Composition.Builder(sequences).build()
            val temp = File(cacheDir, "retrocam_timeline_${System.currentTimeMillis()}.mp4")
            activeExportFile = temp
            status.text = "Rendering ${clips.size} clips with ${videoLooks[lookSpinner.selectedItemPosition]} look into one video… Keep RetroCam open until export finishes."
            val listener = object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    activeTransformer = null
                    activeExportFile = null // The publisher now owns this file; rotation must not delete it.
                    isPublishingExport = true
                    status.text = "Combined video rendered. Saving to your media library…"
                    publishExport(temp)
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    activeTransformer = null
                    temp.delete()
                    activeExportFile = null
                    status.text = "Export failed. Check that all selected clips are readable and use a supported format."
                    Toast.makeText(this@TimelineEditorActivity, "Export failed: ${exportException.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
            activeTransformer = Transformer.Builder(this)
                .addListener(listener)
                .build()
            activeTransformer?.start(composition, temp.absolutePath)
        } catch (error: Exception) {
            activeTransformer = null
            activeExportFile?.delete()
            activeExportFile = null
            status.text = "Could not start export."
            Toast.makeText(this, "Could not start export: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }


    /**
     * Normalizes supported mono-to-5.1 inputs to 16-bit stereo before applying gain. Media3
     * Composition requires mixed audio items to agree on PCM encoding and channel count.
     */
    private fun createFadeGainProvider(gain: Float, durationMs: Long, requestedFadeMs: Long): DefaultGainProvider {
        val durationUs = durationMs.coerceAtLeast(0L) * 1000L
        val fadeUs = (requestedFadeMs.coerceAtLeast(0L) * 1000L).coerceAtMost(durationUs / 2L)
        return DefaultGainProvider.Builder(gain.coerceIn(0f, 1f)).apply {
            if (fadeUs > 0L) {
                addFadeAt(0L, fadeUs, DefaultGainProvider.FADE_IN_LINEAR)
                addFadeAt((durationUs - fadeUs).coerceAtLeast(0L), fadeUs, DefaultGainProvider.FADE_OUT_LINEAR)
            }
        }.build()
    }

    private fun createTitleOverlay(title: String): androidx.media3.common.Effect {
        val styled = SpannableString(title.take(80)).apply {
            if (isNotEmpty()) {
                setSpan(ForegroundColorSpan(Color.WHITE), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(BackgroundColorSpan(0x88000000.toInt()), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(StyleSpan(Typeface.BOLD), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(RelativeSizeSpan(1.25f), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        val settings = StaticOverlaySettings.Builder()
            .setBackgroundFrameAnchor(0f, -0.78f)
            .setOverlayFrameAnchor(0f, 0f)
            .setScale(0.85f, 0.85f)
            .build()
        return OverlayEffect(listOf(TextOverlay.createStaticTextOverlay(styled, settings)))
    }

    private fun audioProcessorsForGain(gain: Float, provider: DefaultGainProvider? = null): List<AudioProcessor> {
        val channelMixer = ChannelMixingAudioProcessor().apply {
            for (channels in 1..6) {
                putChannelMixingMatrix(ChannelMixingMatrix.createForConstantPower(channels, 2))
            }
        }
        val sampleRateNormalizer = SonicAudioProcessor().apply { setOutputSampleRateHz(48_000) }
        val processors = mutableListOf<AudioProcessor>(ToInt16PcmAudioProcessor(), channelMixer, sampleRateNormalizer)
        processors += GainProcessor(provider ?: DefaultGainProvider.Builder(gain.coerceIn(0f, 1f)).build())
        return processors
    }

    private fun publishExport(temp: File) {
        Thread({
            var output: Uri? = null
            var failure: Exception? = null
            try {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "RetroCam_Montage_${timestamp()}.mp4")
                    put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/RetroCam/Edited")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                output = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("Could not create media-library entry")
                contentResolver.openOutputStream(output!!, "w")?.use { destination ->
                    temp.inputStream().use { source -> source.copyTo(destination) }
                } ?: throw IllegalStateException("Could not write combined video")
                val updated = contentResolver.update(
                    output!!,
                    ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                    null,
                    null
                )
                check(updated > 0) { "Could not publish the exported video" }
            } catch (error: Exception) {
                failure = error
                output?.let { contentResolver.delete(it, null, null) }
            } finally {
                temp.delete()
                runOnUiThread {
                    isPublishingExport = false
                    activeExportFile = null
                    if (failure == null) {
                        status.text = "Combined montage saved to Movies/RetroCam/Edited."
                        Toast.makeText(this, "One combined video saved to RetroCam Library", Toast.LENGTH_LONG).show()
                    } else {
                        status.text = "Render completed, but saving the MP4 failed."
                        Toast.makeText(this, "Could not save export: ${failure?.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }, "RetroCam-MediaStore-Publisher").start()
    }

    override fun onDestroy() {
        activeTransformer?.cancel()
        activeTransformer = null
        // Once Transformer completes, publishExport owns the temp file and deletes it itself.
        activeExportFile?.delete()
        activeExportFile = null
        super.onDestroy()
    }

    private fun timestamp() = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
}
