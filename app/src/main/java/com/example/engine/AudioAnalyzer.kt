package com.example.engine

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max

data class AudioAnalysisResult(
    val durationMs: Long,
    val waveform: List<Float>,
    val beatMarkersMs: List<Long>
)

/**
 * Decodes real PCM audio via MediaExtractor + MediaCodec to generate timeline waveforms,
 * automatic energy-onset beat markers, and real-time audio amplitude envelopes.
 */
object AudioAnalyzer {

    suspend fun analyzeAudio(context: Context, uri: Uri, bucketCount: Int = 160): AudioAnalysisResult =
        withContext(Dispatchers.IO) {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(context, uri, null)
                var audioTrackIndex = -1
                var audioFormat: MediaFormat? = null
                for (i in 0 until extractor.trackCount) {
                    val fmt = extractor.getTrackFormat(i)
                    val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                    if (mime.startsWith("audio/")) {
                        audioTrackIndex = i
                        audioFormat = fmt
                        break
                    }
                }
                if (audioTrackIndex < 0 || audioFormat == null) {
                    return@withContext AudioAnalysisResult(5000L, List(bucketCount) { 0.1f }, emptyList())
                }

                val durationUs = if (audioFormat.containsKey(MediaFormat.KEY_DURATION)) {
                    audioFormat.getLong(MediaFormat.KEY_DURATION)
                } else {
                    5_000_000L
                }
                val durationMs = (durationUs / 1000L).coerceIn(500L, 600_000L)
                val mime = audioFormat.getString(MediaFormat.KEY_MIME)!!
                extractor.selectTrack(audioTrackIndex)

                val codec = MediaCodec.createDecoderByType(mime)
                codec.configure(audioFormat, null, null, 0)
                codec.start()

                val buckets = FloatArray(bucketCount)
                val bucketCounts = IntArray(bucketCount)
                val bufferInfo = MediaCodec.BufferInfo()
                var sawInputEOS = false
                var sawOutputEOS = false
                var totalSamples = 0L

                while (!sawOutputEOS) {
                    if (!sawInputEOS) {
                        val inIdx = codec.dequeueInputBuffer(5000L)
                        if (inIdx >= 0) {
                            val inBuf = codec.getInputBuffer(inIdx)!!
                            val sampleSize = extractor.readSampleData(inBuf, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                sawInputEOS = true
                            } else {
                                codec.queueInputBuffer(inIdx, 0, sampleSize, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }

                    val outIdx = codec.dequeueOutputBuffer(bufferInfo, 5000L)
                    if (outIdx >= 0) {
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            sawOutputEOS = true
                        }
                        val outBuf = codec.getOutputBuffer(outIdx)
                        if (outBuf != null && bufferInfo.size > 0) {
                            outBuf.order(ByteOrder.LITTLE_ENDIAN)
                            val shortCount = bufferInfo.size / 2
                            val ptsMs = (bufferInfo.presentationTimeUs / 1000L).coerceIn(0L, durationMs)
                            val bucketIdx = ((ptsMs.toDouble() / durationMs.toDouble()) * (bucketCount - 1))
                                .toInt().coerceIn(0, bucketCount - 1)
                            var peak = 0f
                            var step = 0
                            while (step < shortCount) {
                                val sample = abs(outBuf.getShort(step * 2).toInt()) / 32768f
                                if (sample > peak) peak = sample
                                step += 4
                            }
                            buckets[bucketIdx] = max(buckets[bucketIdx], peak)
                            bucketCounts[bucketIdx]++
                            totalSamples += shortCount
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                    }
                }

                codec.stop()
                codec.release()

                val maxPeak = buckets.maxOrNull()?.coerceAtLeast(0.05f) ?: 1f
                val normalized = buckets.map { (it / maxPeak).coerceIn(0.04f, 1.0f) }
                val beats = detectBeatOnsets(normalized, durationMs)
                AudioAnalysisResult(durationMs, normalized, beats)
            } catch (_: Exception) {
                AudioAnalysisResult(5000L, List(bucketCount) { 0.15f }, emptyList())
            } finally {
                extractor.release()
            }
        }

    fun detectBeatOnsets(waveform: List<Float>, durationMs: Long): List<Long> {
        if (waveform.size < 8) return emptyList()
        val beats = mutableListOf<Long>()
        val avg = waveform.average().toFloat()
        val threshold = (avg * 1.38f).coerceAtLeast(0.38f)
        var lastBeatMs = -300L
        for (i in 1 until waveform.lastIndex) {
            val cur = waveform[i]
            val prev = waveform[i - 1]
            val next = waveform[i + 1]
            if (cur >= threshold && cur > prev && cur >= next) {
                val ms = ((i.toDouble() / waveform.size.toDouble()) * durationMs).toLong()
                if (ms - lastBeatMs >= 260L) {
                    beats.add(ms)
                    lastBeatMs = ms
                }
            }
        }
        return beats
    }

    fun amplitudeAt(waveform: List<Float>, durationMs: Long, timeMs: Long): Float {
        if (waveform.isEmpty() || durationMs <= 0L) return 0f
        val norm = (timeMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        val idx = (norm * (waveform.size - 1)).toInt().coerceIn(0, waveform.lastIndex)
        return waveform[idx]
    }
}
