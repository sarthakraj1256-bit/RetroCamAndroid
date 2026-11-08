package com.sarthu.retrocam

import android.graphics.Bitmap
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/** CPU fallback photo pipeline. Deterministic, on-device and independent of cloud services. */
object RetroLookProcessor {
    private data class Tuning(val saturation: Float, val contrast: Float, val warmth: Float, val grain: Float, val vignette: Float, val fade: Float, val scanlines: Float = 0f)

    private fun tuning(look: String) = when (look.uppercase()) {
        "VHS" -> Tuning(.78f, 1.12f, -.04f, 13f, .18f, .035f, .22f)
        "DV" -> Tuning(.92f, 1.08f, -.02f, 5f, .12f, .015f)
        "SUPER 8", "8MM" -> Tuning(.82f, 1.16f, .16f, 19f, .38f, .075f)
        "16MM" -> Tuning(.90f, 1.10f, .09f, 12f, .27f, .045f)
        "35MM" -> Tuning(1.08f, 1.06f, .055f, 7f, .16f, .018f)
        "CCD" -> Tuning(1.20f, 1.18f, -.025f, 4f, .10f, 0f)
        "B&W", "BLACK & WHITE" -> Tuning(0f, 1.18f, 0f, 9f, .20f, .025f)
        "PORTRA 400" -> Tuning(1.02f, 1.02f, .075f, 5f, .10f, .025f)
        "GOLD 200" -> Tuning(1.12f, 1.08f, .13f, 8f, .16f, .035f)
        "FADED" -> Tuning(.72f, .88f, .04f, 4f, .08f, .12f)
        else -> Tuning(1f, 1f, 0f, 0f, 0f, 0f)
    }

    fun apply(source: Bitmap, look: String, intensity: Float = 1f): Bitmap {
        val input = source.copy(Bitmap.Config.ARGB_8888, true)
        val w = input.width; val h = input.height
        val pixels = IntArray(w * h)
        input.getPixels(pixels, 0, w, 0, 0, w, h)
        val t = tuning(look)
        val amount = intensity.coerceIn(0f, 1f)
        val cx = (w - 1) / 2f; val cy = (h - 1) / 2f
        val maxDist = max(1f, cx * cx + cy * cy)
        val rng = Random(look.hashCode() xor (w * 31 + h))
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x; val p = pixels[i]
            var r = ((p shr 16) and 255).toFloat(); var g = ((p shr 8) and 255).toFloat(); var b = (p and 255).toFloat()
            val gray = .299f * r + .587f * g + .114f * b
            r = gray + (r - gray) * (1f + (t.saturation - 1f) * amount)
            g = gray + (g - gray) * (1f + (t.saturation - 1f) * amount)
            b = gray + (b - gray) * (1f + (t.saturation - 1f) * amount)
            val contrast = 1f + (t.contrast - 1f) * amount
            r = (r - 128f) * contrast + 128f + t.warmth * 35f * amount
            g = (g - 128f) * contrast + 128f + t.warmth * 10f * amount
            b = (b - 128f) * contrast + 128f - t.warmth * 32f * amount
            val d = ((x - cx).pow(2) + (y - cy).pow(2)) / maxDist
            val vignette = 1f - t.vignette * amount * d.pow(1.35f)
            val noise = (rng.nextFloat() - .5f) * t.grain * amount
            val line = if (t.scanlines > 0f && y % 3 == 0) 1f - t.scanlines * amount else 1f
            val fade = t.fade * amount * 255f
            r = ((r * vignette * line) + noise + fade).coerceIn(0f, 255f)
            g = ((g * vignette * line) + noise + fade).coerceIn(0f, 255f)
            b = ((b * vignette * line) + noise + fade).coerceIn(0f, 255f)
            pixels[i] = (p and -0x1000000) or (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()
        }
        input.setPixels(pixels, 0, w, 0, 0, w, h)
        return input
    }
}
