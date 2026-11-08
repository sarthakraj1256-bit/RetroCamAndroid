package com.sarthu.retrocam

import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.OverlayEffect

/** Shared GPU color-grade presets for live capture and Transformer exports. */
@OptIn(UnstableApi::class)
object RetroVideoEffects {
    fun forLook(name: String, intensity: Float = 1f): List<Effect> {
        val amount = intensity.coerceIn(0f, 1f)
        if (name == "ORIGINAL" || amount <= 0.001f) return emptyList()
        val out = mutableListOf<Effect>()
        fun hsl(saturation: Float = 0f, lightness: Float = 0f, hue: Float = 0f) {
            out += HslAdjustment.Builder()
                .adjustHue(hue * amount)
                .adjustSaturation(saturation * amount)
                .adjustLightness(lightness * amount)
                .build()
        }
        fun rgb(red: Float = 1f, green: Float = 1f, blue: Float = 1f) {
            out += RgbAdjustment.Builder()
                .setRedScale(1f + (red - 1f) * amount)
                .setGreenScale(1f + (green - 1f) * amount)
                .setBlueScale(1f + (blue - 1f) * amount)
                .build()
        }
        fun contrast(value: Float) { out += Contrast(value * amount) }
        when (name.uppercase()) {
            "B&W", "BLACK & WHITE" -> { hsl(saturation = -100f); contrast(0.18f) }
            "VHS" -> { contrast(0.12f); hsl(saturation = -12f, hue = -3f); rgb(1.06f, 1.0f, 1.08f) }
            "DV" -> { contrast(0.14f); hsl(saturation = 8f); rgb(1.02f, 1.0f, 0.97f) }
            "SUPER 8", "8MM" -> { contrast(0.08f); hsl(saturation = 6f, lightness = 3f); rgb(1.08f, 1.0f, 0.88f) }
            "16MM" -> { contrast(0.10f); hsl(saturation = -4f, lightness = 2f); rgb(1.05f, 1.0f, 0.93f) }
            "35MM" -> { contrast(0.12f); hsl(saturation = 3f); rgb(1.04f, 1.0f, 0.96f) }
            "CCD" -> { contrast(0.20f); hsl(saturation = 10f); rgb(1.02f, 1.0f, 1.06f) }
            "PORTRA 400" -> { contrast(0.04f); hsl(saturation = -7f, lightness = 4f); rgb(1.05f, 1.0f, 0.96f) }
            "GOLD 200" -> { contrast(0.12f); hsl(saturation = 7f, lightness = 2f, hue = -2f); rgb(1.08f, 1.0f, 0.90f) }
            "FADED" -> { contrast(-0.12f); hsl(saturation = -18f, lightness = 6f); rgb(1.03f, 1.0f, 0.98f) }
            else -> { contrast(0.08f); hsl(saturation = -3f) }
        }
        // The overlay is also rendered by Media3's GPU effect graph, so the same texture layer
        // reaches the live CameraX preview, camera video capture and Transformer exports.
        out += OverlayEffect(listOf(FilmTextureOverlay(name, amount)))
        return out
    }
}
