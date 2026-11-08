package com.sarthu.retrocam

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import java.io.File
import java.nio.ByteBuffer

/** Fast, on-device stream-copy trim. Cuts are bounded by source keyframes; no quality loss. */
object VideoTrimmer {
    fun trim(context: Context, source: Uri, startMs: Long, endMs: Long, destination: File) {
        require(startMs >= 0 && endMs > startMs) { "Invalid trim range" }
        destination.parentFile?.mkdirs()
        destination.delete()
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var wroteSamples = false
        var wroteVideoSamples = false
        try {
            context.contentResolver.openFileDescriptor(source, "r")?.use { extractor.setDataSource(it.fileDescriptor) }
                ?: throw IllegalStateException("Cannot open selected video")
            val trackMap = mutableMapOf<Int, Int>()
            val videoTracks = mutableSetOf<Int>()
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                    extractor.selectTrack(i)
                    trackMap[i] = i
                    if (mime.startsWith("video/")) videoTracks += i
                }
            }
            if (trackMap.isEmpty() || videoTracks.isEmpty()) {
                throw IllegalStateException("No supported video track found")
            }
            muxer = MediaMuxer(destination.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val outputTracks = mutableMapOf<Int, Int>()
            trackMap.keys.forEach { outputTracks[it] = muxer.addTrack(extractor.getTrackFormat(it)) }
            muxer.start()
            muxerStarted = true
            var buffer = ByteBuffer.allocateDirect(4 * 1024 * 1024)
            val info = android.media.MediaCodec.BufferInfo()
            val startUs = startMs * 1000
            val endUs = endMs * 1000
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_NEXT_SYNC)
            while (true) {
                buffer.clear()
                val track = extractor.sampleTrackIndex
                if (track < 0) break
                val time = extractor.sampleTime
                if (time >= endUs) break
                if (time >= startUs && outputTracks.containsKey(track)) {
                    val requiredBytes = extractor.sampleSize
                    if (requiredBytes <= 0L) {
                        if (!extractor.advance()) break
                        continue
                    }
                    // Refuse pathological samples instead of risking an unbounded direct-memory allocation.
                    if (requiredBytes > 64L * 1024L * 1024L) {
                        throw IllegalStateException("A media sample exceeds the 64 MB safety limit")
                    }
                    if (requiredBytes > buffer.capacity()) {
                        buffer = ByteBuffer.allocateDirect(requiredBytes.toInt())
                    }
                    buffer.clear()
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) break
                    info.set(0, size, time - startUs, extractor.sampleFlags)
                    muxer.writeSampleData(outputTracks.getValue(track), buffer, info)
                    wroteSamples = true
                    if (track in videoTracks) wroteVideoSamples = true
                }
                if (!extractor.advance()) break
            }
            check(wroteSamples && wroteVideoSamples) {
                "No video frames were found in the selected trim range. Move the trim handles to include video frames."
            }
            muxer.stop()
            muxerStarted = false
        } catch (error: Exception) {
            destination.delete()
            throw error
        } finally {
            if (muxerStarted) try { muxer?.stop() } catch (_: Exception) { }
            try { muxer?.release() } catch (_: Exception) { }
            extractor.release()
        }
    }
}
