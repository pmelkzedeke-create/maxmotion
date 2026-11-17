package com.example.engine

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.example.model.AssetItem
import com.example.model.AssetType
import com.example.model.EasingType
import com.example.model.Keyframe
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.SegmentationMask
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.math.abs

/**
 * Offline on-device ML Kit Selfie Segmentation for automatic rotoscoping / background removal.
 */
object MlKitRotoscoper {

    suspend fun generateCutoutMask(
        context: Context,
        asset: AssetItem,
        sampleTimeMs: Long
    ): Result<String> = withContext(Dispatchers.Default) {
        runCatching {
            val sourceBmp = when (asset.type) {
                AssetType.IMAGE -> context.contentResolver.openInputStream(Uri.parse(asset.uri))?.use {
                    BitmapFactory.decodeStream(it)
                }
                AssetType.VIDEO -> {
                    val ret = MediaMetadataRetriever()
                    ret.setDataSource(context, Uri.parse(asset.uri))
                    val f = ret.getFrameAtTime(sampleTimeMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST)
                    ret.release()
                    f
                }
                else -> null
            } ?: throw IllegalStateException("Não foi possível decodificar o quadro da camada.")

            val options = SelfieSegmenterOptions.Builder()
                .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
                .enableRawSizeMask()
                .build()
            val segmenter = Segmentation.getClient(options)
            val inputImage = InputImage.fromBitmap(sourceBmp, 0)

            val mask = suspendCancellableCoroutine<SegmentationMask?> { cont ->
                segmenter.process(inputImage)
                    .addOnSuccessListener { segMask -> cont.resume(segMask) }
                    .addOnFailureListener { _ -> cont.resume(null) }
            } ?: throw IllegalStateException("Falha no segmentador local ML Kit.")

            val mw = mask.width
            val mh = mask.height
            val buf = mask.buffer
            buf.rewind()

            val alphaBmp = Bitmap.createBitmap(mw, mh, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(mw * mh)
            for (i in 0 until mw * mh) {
                val conf = buf.float
                // Smooth soft-edge matte around 0.45..0.65 confidence
                val alpha = (((conf - 0.35f) / 0.35f).coerceIn(0f, 1f) * 255f).toInt()
                pixels[i] = Color.argb(alpha, 255, 255, 255)
            }
            alphaBmp.setPixels(pixels, 0, mw, 0, 0, mw, mh)
            val scaledMask = Bitmap.createScaledBitmap(alphaBmp, sourceBmp.width, sourceBmp.height, true)

            val outDir = File(context.filesDir, "maxmotion_media").apply { mkdirs() }
            val outFile = File(outDir, "roto_mask_${UUID.randomUUID()}.png")
            FileOutputStream(outFile).use { fos ->
                scaledMask.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
            segmenter.close()
            Uri.fromFile(outFile).toString()
        }
    }
}

/**
 * 2D Optical Motion Tracker using normalized Sum of Absolute Differences (SAD) template matching
 * across decoded video frames.
 */
object MotionTracker2D {

    data class TrackResult(
        val posXKeyframes: List<Keyframe>,
        val posYKeyframes: List<Keyframe>
    )

    suspend fun trackPointInVideo(
        context: Context,
        asset: AssetItem,
        inMs: Long,
        outMs: Long,
        targetNormX: Float,
        targetNormY: Float,
        compWidth: Int,
        compHeight: Int,
        invertForStabilize: Boolean = false
    ): Result<TrackResult> = withContext(Dispatchers.Default) {
        runCatching {
            val ret = MediaMetadataRetriever()
            ret.setDataSource(context, Uri.parse(asset.uri))

            val spanMs = (outMs - inMs).coerceIn(300L, 12_000L)
            val steps = 16
            val stepMs = spanMs / steps
            val patchRadius = 10
            val searchRadius = 18
            val analysisWidth = 180

            var templateGray: IntArray? = null
            var curX = 0
            var curY = 0
            var baseX = 0
            var baseY = 0
            val kfX = ArrayList<Keyframe>()
            val kfY = ArrayList<Keyframe>()

            for (i in 0..steps) {
                val sampleMs = (i * stepMs).coerceIn(0L, asset.durationMs)
                val rawBmp = ret.getFrameAtTime(sampleMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST)
                    ?: continue
                val ratio = rawBmp.height.toFloat() / rawBmp.width.coerceAtLeast(1).toFloat()
                val aw = analysisWidth
                val ah = (aw * ratio).toInt().coerceAtLeast(64)
                val small = Bitmap.createScaledBitmap(rawBmp, aw, ah, true)
                val gray = toGrayscale(small, aw, ah)

                if (templateGray == null) {
                    curX = ((targetNormX + 0.5f) * aw).toInt().coerceIn(patchRadius + 2, aw - patchRadius - 3)
                    curY = ((targetNormY + 0.5f) * ah).toInt().coerceIn(patchRadius + 2, ah - patchRadius - 3)
                    baseX = curX
                    baseY = curY
                    templateGray = extractPatch(gray, aw, curX, curY, patchRadius)
                } else {
                    var bestX = curX
                    var bestY = curY
                    var bestSad = Long.MAX_VALUE
                    val minX = (curX - searchRadius).coerceAtLeast(patchRadius + 1)
                    val maxX = (curX + searchRadius).coerceAtMost(aw - patchRadius - 2)
                    val minY = (curY - searchRadius).coerceAtLeast(patchRadius + 1)
                    val maxY = (curY + searchRadius).coerceAtMost(ah - patchRadius - 2)

                    for (sy in minY..maxY step 2) {
                        for (sx in minX..maxX step 2) {
                            val sad = computeSad(gray, aw, sx, sy, templateGray, patchRadius)
                            if (sad < bestSad) {
                                bestSad = sad
                                bestX = sx
                                bestY = sy
                            }
                        }
                    }
                    curX = bestX
                    curY = bestY
                }

                val dxNorm = (curX - baseX).toFloat() / aw.toFloat()
                val dyNorm = (curY - baseY).toFloat() / ah.toFloat()
                val sign = if (invertForStabilize) -1f else 1f
                val worldX = dxNorm * compWidth * sign
                val worldY = dyNorm * compHeight * sign
                val kfTime = i * stepMs
                kfX.add(Keyframe(timeMs = kfTime, value = worldX, easing = EasingType.LINEAR))
                kfY.add(Keyframe(timeMs = kfTime, value = worldY, easing = EasingType.LINEAR))
            }
            ret.release()
            TrackResult(kfX, kfY)
        }
    }

    private fun toGrayscale(bmp: Bitmap, w: Int, h: Int): IntArray {
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        return IntArray(w * h) { idx ->
            val c = px[idx]
            (((c shr 16) and 0xFF) * 30 + ((c shr 8) and 0xFF) * 59 + (c and 0xFF) * 11) / 100
        }
    }

    private fun extractPatch(gray: IntArray, w: Int, cx: Int, cy: Int, r: Int): IntArray {
        val side = r * 2 + 1
        val out = IntArray(side * side)
        var k = 0
        for (dy in -r..r) {
            for (dx in -r..r) {
                out[k++] = gray[(cy + dy) * w + (cx + dx)]
            }
        }
        return out
    }

    private fun computeSad(gray: IntArray, w: Int, cx: Int, cy: Int, tpl: IntArray, r: Int): Long {
        var sad = 0L
        var k = 0
        for (dy in -r..r) {
            val rowOff = (cy + dy) * w + cx
            for (dx in -r..r) {
                sad += abs(gray[rowOff + dx] - tpl[k++])
            }
        }
        return sad
    }
}

/**
 * Offline Auto-Captions generator combining Voice Activity Detection (VAD) over real decoded PCM
 * audio peaks with Android API 33+ offline SpeechRecognizer when supported on device.
 */
object OfflineCaptionGenerator {

    data class CaptionSegment(
        val startMs: Long,
        val endMs: Long,
        val text: String
    )

    suspend fun generateCaptions(context: Context, asset: AssetItem): List<CaptionSegment> {
        val waveform = if (asset.waveform.isNotEmpty()) {
            asset.waveform
        } else {
            AudioAnalyzer.analyzeAudio(context, Uri.parse(asset.uri), bucketCount = 120).waveform
        }
        val durationMs = asset.durationMs.coerceAtLeast(1000L)
        val segments = detectSpeechRegions(waveform, durationMs)

        // Attempt on-device offline SpeechRecognizer when API 33+ supports EXTRA_AUDIO_SOURCE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            val recognized = tryOfflineSpeechFile(context, Uri.parse(asset.uri))
            if (!recognized.isNullOrBlank()) {
                val words = recognized.trim().split(Regex("\\s+"))
                if (segments.isNotEmpty() && words.isNotEmpty()) {
                    val perSeg = (words.size / segments.size).coerceAtLeast(1)
                    return segments.mapIndexed { idx, seg ->
                        val from = (idx * perSeg).coerceAtMost(words.lastIndex)
                        val to = if (idx == segments.lastIndex) words.size else ((idx + 1) * perSeg).coerceAtMost(words.size)
                        val chunk = words.subList(from, to).joinToString(" ").ifBlank { "Legenda ${idx + 1}" }
                        seg.copy(text = chunk)
                    }
                }
            }
        }

        // Timed VAD-aligned editable captions based on real acoustic energy intervals
        return if (segments.isEmpty()) {
            listOf(CaptionSegment(200L, minOf(2400L, durationMs), "TRECHO DE ÁUDIO • TOQUE PARA EDITAR"))
        } else {
            segments.mapIndexed { idx, seg ->
                seg.copy(text = "LEGENDA ${idx + 1} • EDITAR")
            }
        }
    }

    private fun detectSpeechRegions(waveform: List<Float>, durationMs: Long): List<CaptionSegment> {
        if (waveform.isEmpty()) return emptyList()
        val avg = waveform.average().toFloat()
        val thresh = (avg * 0.85f).coerceIn(0.18f, 0.55f)
        val out = mutableListOf<CaptionSegment>()
        var inSpeech = false
        var startIdx = 0

        for (i in waveform.indices) {
            val active = waveform[i] >= thresh
            if (active && !inSpeech) {
                inSpeech = true
                startIdx = i
            } else if ((!active || i == waveform.lastIndex) && inSpeech) {
                inSpeech = false
                val endIdx = if (active) i else i - 1
                val startMs = ((startIdx.toFloat() / waveform.size) * durationMs).toLong()
                val endMs = (((endIdx + 1).toFloat() / waveform.size) * durationMs).toLong().coerceAtLeast(startMs + 450L)
                if (endMs - startMs >= 350L) {
                    out.add(CaptionSegment(startMs, minOf(durationMs, endMs), ""))
                }
            }
        }
        return out.take(16)
    }

    private suspend fun tryOfflineSpeechFile(context: Context, uri: Uri): String? = withContext(Dispatchers.Main) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@withContext null
        runCatching {
            val pfd: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return@withContext null
            suspendCancellableCoroutine { cont ->
                val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                    putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pfd)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                }
                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onError(error: Int) {
                        recognizer.destroy()
                        runCatching { pfd.close() }
                        if (cont.isActive) cont.resume(null)
                    }
                    override fun onResults(results: Bundle?) {
                        val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        recognizer.destroy()
                        runCatching { pfd.close() }
                        if (cont.isActive) cont.resume(list?.firstOrNull())
                    }
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
                recognizer.startListening(intent)
                cont.invokeOnCancellation {
                    recognizer.destroy()
                    runCatching { pfd.close() }
                }
            }
        }.getOrNull()
    }
}
