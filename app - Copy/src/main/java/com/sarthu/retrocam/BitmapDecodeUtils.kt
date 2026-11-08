package com.sarthu.retrocam

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlin.math.max

/** Shared, memory-bounded JPEG/image decoder that applies EXIF orientation to pixels. */
object BitmapDecodeUtils {
    fun decodeExifCorrected(
        resolver: ContentResolver,
        uri: Uri,
        maxLongEdge: Int = 2560
    ): Bitmap {
        require(maxLongEdge >= 256) { "maxLongEdge must be at least 256 pixels" }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: throw IllegalStateException("Image could not be read")
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Invalid image dimensions" }

        var sample = 1
        while (max(bounds.outWidth / sample, bounds.outHeight / sample) > maxLongEdge) {
            sample = (sample * 2).coerceAtMost(1 shl 14)
            if (sample == (1 shl 14)) break
        }
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(
                it,
                null,
                BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
            )
        } ?: throw IllegalStateException("Image could not be decoded")

        val orientation = try {
            resolver.openInputStream(uri)?.use { input ->
                ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }

        val transform = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> transform.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> transform.postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> transform.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                transform.postScale(-1f, 1f)
                transform.postRotate(270f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> transform.postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                transform.postScale(-1f, 1f)
                transform.postRotate(90f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> transform.postRotate(270f)
        }
        if (transform.isIdentity) return decoded
        val corrected = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, transform, true)
        if (corrected !== decoded) decoded.recycle()
        return corrected
    }
}
