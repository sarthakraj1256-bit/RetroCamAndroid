package com.sarthu.retrocam

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.Size
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.StaticOverlaySettings
import java.util.Random
import kotlin.math.max
import kotlin.math.min

/**
 * A full-frame GPU-composited texture layer for the live CameraX/Media3 and Transformer paths.
 * The transparent bitmap contains film grain, scanlines, a vignette and analog dropout streaks.
 * The still-photo renderer remains the higher-fidelity per-pixel shader path.
 */
@androidx.media3.common.util.UnstableApi
class FilmTextureOverlay(
    private val look: String,
    private val intensity: Float
) : BitmapOverlay() {
    private var bitmap: Bitmap? = null
    private var canvas: Canvas? = null
    private var width = 720
    private var height = 1280
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val settings: OverlaySettings = StaticOverlaySettings.Builder()
        .setAlphaScale(1f)
        .build()

    override fun configure(videoSize: Size) {
        super.configure(videoSize)
        val scale = min(1f, 960f / max(videoSize.width, videoSize.height).toFloat())
        width = max(2, (videoSize.width * scale).toInt())
        height = max(2, (videoSize.height * scale).toInt())
        bitmap?.recycle()
        bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        canvas = Canvas(bitmap!!)
    }

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val frame = bitmap ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            bitmap = it
            canvas = Canvas(it)
        }
        val target = canvas ?: Canvas(frame).also { canvas = it }
        target.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val amount = intensity.coerceIn(0f, 1f)
        if (amount <= 0.001f || look.equals("ORIGINAL", true)) return frame
        val upper = look.uppercase()
        val grain = when (upper) {
            "VHS" -> 0.20f
            "DV" -> 0.10f
            "SUPER 8", "8MM" -> 0.32f
            "16MM" -> 0.24f
            "35MM", "PORTRA 400", "GOLD 200" -> 0.13f
            "CCD" -> 0.08f
            "B&W", "BLACK & WHITE" -> 0.18f
            else -> 0.08f
        } * amount
        val scanlineAlpha = when (upper) {
            "VHS" -> 34
            "DV", "CCD" -> 18
            else -> 0
        }
        val rng = Random((presentationTimeUs / 33_333L) xor upper.hashCode().toLong())

        // Fine horizontal scan lines.
        if (scanlineAlpha > 0) {
            paint.color = Color.argb((scanlineAlpha * amount).toInt().coerceIn(0, 255), 0, 0, 0)
            paint.style = Paint.Style.FILL
            var y = 0
            while (y < height) {
                target.drawRect(0f, y.toFloat(), width.toFloat(), min(height, y + 1).toFloat(), paint)
                y += 3
            }
        }

        // A subtle radial darkening overlay, matching the still-photo look family.
        val vignetteAlpha = when (upper) {
            "SUPER 8", "8MM" -> 82
            "16MM", "B&W", "BLACK & WHITE" -> 58
            "VHS", "35MM", "GOLD 200" -> 34
            else -> 20
        }
        paint.shader = RadialGradient(
            width / 2f, height / 2f, max(width, height) * 0.72f,
            intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb((vignetteAlpha * amount).toInt(), 0, 0, 0)),
            floatArrayOf(0f, 0.58f, 1f), Shader.TileMode.CLAMP
        )
        target.drawRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), paint)
        paint.shader = null

        // Sparse monochrome grain. The seed changes with the frame timestamp so it is not frozen.
        val grainDots = (width * height / 95f * grain).toInt().coerceIn(0, 7_500)
        repeat(grainDots) {
            val x = rng.nextInt(width)
            val y = rng.nextInt(height)
            val bright = rng.nextBoolean()
            val alpha = (10 + rng.nextInt(30)) * amount
            paint.color = if (bright) Color.argb(alpha.toInt(), 255, 255, 255)
                else Color.argb(alpha.toInt(), 0, 0, 0)
            val dot = if (upper == "VHS" || upper == "DV") 1.35f else 1f
            target.drawRect(x.toFloat(), y.toFloat(), x + dot, y + dot, paint)
        }

        // VHS/DV tape dropouts: short, translucent coloured horizontal tracking marks.
        if (upper == "VHS" || upper == "DV") {
            repeat(if (upper == "VHS") 10 else 4) {
                val y = rng.nextInt(height).toFloat()
                val left = rng.nextInt(width).toFloat()
                val streakWidth = (width * (0.03f + rng.nextFloat() * 0.18f)).coerceAtLeast(2f)
                val color = if (rng.nextBoolean()) Color.rgb(220, 70, 95) else Color.rgb(75, 190, 220)
                paint.color = Color.argb((28 * amount).toInt(), Color.red(color), Color.green(color), Color.blue(color))
                target.drawRect(left, y, min(width.toFloat(), left + streakWidth), y + 1.5f, paint)
            }
        }
        return frame
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings = settings

    override fun release() {
        bitmap?.recycle()
        bitmap = null
        canvas = null
        super.release()
    }
}
