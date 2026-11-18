package com.example.render

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.Surface
import com.example.engine.SceneEvaluator
import com.example.model.LayerNode
import com.example.model.LayerType
import com.example.model.ProjectDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

enum class ExportResolution(val label: String, val longEdge: Int) {
    HD_720("720p HD", 1280),
    FHD_1080("1080p Full HD", 1920),
    QHD_2K("2K QHD", 2560),
    UHD_4K("4K Ultra HD", 3840)
}

enum class ExportCodec(val label: String, val mime: String) {
    H264("H.264 (AVC)", MediaFormat.MIMETYPE_VIDEO_AVC),
    HEVC("H.265 (HEVC)", MediaFormat.MIMETYPE_VIDEO_HEVC)
}

data class ExportSettings(
    val resolution: ExportResolution = ExportResolution.FHD_1080,
    val fps: Int = 60,
    val codec: ExportCodec = ExportCodec.H264,
    val bitrateMbps: Int = 14,
    val rangeStartMs: Long = 0L,
    val rangeEndMs: Long = -1L
) {
    fun resolveDimensions(compW: Int, compH: Int): Pair<Int, Int> {
        val ratio = compW.toFloat() / compH.coerceAtLeast(1).toFloat()
        val (rawW, rawH) = if (compH >= compW) {
            val h = resolution.longEdge
            val w = (h * ratio).toInt()
            w to h
        } else {
            val w = resolution.longEdge
            val h = (w / ratio).toInt()
            w to h
        }
        // MediaCodec requires even macroblock dimensions (multiple of 16 or at least 2)
        val alignedW = (rawW / 16) * 16
        val alignedH = (rawH / 16) * 16
        return alignedW.coerceAtLeast(320) to alignedH.coerceAtLeast(320)
    }

    fun estimateSizeMb(durationMs: Long): Float {
        val seconds = durationMs / 1000f
        return (bitrateMbps * seconds) / 8f
    }
}

sealed class ExportResult {
    data class Success(val outputUri: Uri?, val filePath: String) : ExportResult()
    data class Error(val message: String) : ExportResult()
}

/**
 * Hardware-accelerated offscreen EGL14 + OpenGL ES 3.0 + MediaCodec + MediaMuxer video exporter
 * with synchronized AAC audio track muxing.
 */
object VideoExporter {

    private data class PreparedAudioSource(
        val layer: LayerNode,
        val extractor: MediaExtractor,
        val format: MediaFormat
    )

    private fun prepareAudioTrack(context: Context, document: ProjectDocument): PreparedAudioSource? {
        val comp = document.activeComposition
        val audioLayers = comp.layers.filter {
            it.visible && (it.type == LayerType.AUDIO || it.type == LayerType.VIDEO) && it.assetId != null
        }.sortedBy { if (it.type == LayerType.AUDIO) 0 else 1 }

        for (layer in audioLayers) {
            val asset = document.assets.firstOrNull { it.id == layer.assetId } ?: continue
            val ext = MediaExtractor()
            val prepared = runCatching {
                ext.setDataSource(context, Uri.parse(asset.uri), null)
                for (i in 0 until ext.trackCount) {
                    val fmt = ext.getTrackFormat(i)
                    val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                    if (mime == MediaFormat.MIMETYPE_AUDIO_AAC) {
                        ext.selectTrack(i)
                        return@runCatching PreparedAudioSource(layer, ext, fmt)
                    }
                }
                null
            }.getOrNull()
            if (prepared != null) return prepared
            runCatching { ext.release() }
        }
        return null
    }

    suspend fun exportMp4(
        context: Context,
        document: ProjectDocument,
        settings: ExportSettings,
        onProgress: (Float, String) -> Unit
    ): ExportResult = withContext(Dispatchers.Default) {
        val comp = document.activeComposition
        val (outW, outH) = settings.resolveDimensions(comp.width, comp.height)
        val tempFile = File(context.cacheDir, "maxmotion_export_${System.currentTimeMillis()}.mp4")

        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var inputSurface: Surface? = null
        var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
        var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
        var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
        val audioSource = prepareAudioTrack(context, document)

        try {
            onProgress(0.02f, "Inicializando encoder ${settings.codec.label} (${outW}x${outH})...")

            val format = MediaFormat.createVideoFormat(settings.codec.mime, outW, outH).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, settings.bitrateMbps * 1_000_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, settings.fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            encoder = MediaCodec.createEncoderByType(settings.codec.mime)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = encoder.createInputSurface()
            encoder.start()

            // Set up offscreen EGL14 context bound to MediaCodec's Surface
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val version = IntArray(2)
            EGL14.eglInitialize(eglDisplay, version, 0, version, 1)

            val attribList = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT or 0x0040,
                0x3142, 1, // EGL_RECORDABLE_ANDROID
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, 1, numConfigs, 0)
            val eglConfig = configs[0] ?: throw IllegalStateException("Falha ao configurar EGL para exportação")

            val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE)
            eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)
            val surfAttribs = intArrayOf(EGL14.EGL_NONE)
            eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, inputSurface, surfAttribs, 0)
            EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)

            val renderer = GlSceneRenderer(context)
            renderer.initGlResources()

            val localMuxer = MediaMuxer(tempFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = localMuxer
            var videoTrackIndex = -1
            var audioTrackIndex = -1
            var muxerStarted = false
            val bufferInfo = MediaCodec.BufferInfo()

            val rangeStart = settings.rangeStartMs.coerceAtLeast(0L)
            val rangeEnd = if (settings.rangeEndMs > rangeStart) {
                settings.rangeEndMs.coerceAtMost(comp.durationMs)
            } else {
                comp.durationMs
            }
            val exportDurationMs = (rangeEnd - rangeStart).coerceAtLeast(200L)
            val totalFrames = ((exportDurationMs / 1000.0) * settings.fps).toInt().coerceAtLeast(1)
            val frameDurationUs = 1_000_000L / settings.fps

            fun drainEncoder(endOfStream: Boolean) {
                if (endOfStream) encoder.signalEndOfInputStream()
                while (true) {
                    val outIdx = encoder.dequeueOutputBuffer(bufferInfo, 10_000L)
                    if (outIdx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        if (!endOfStream) break
                    } else if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        if (!muxerStarted) {
                            videoTrackIndex = localMuxer.addTrack(encoder.outputFormat)
                            if (audioSource != null) {
                                runCatching {
                                    audioTrackIndex = localMuxer.addTrack(audioSource.format)
                                }
                            }
                            localMuxer.start()
                            muxerStarted = true
                        }
                    } else if (outIdx >= 0) {
                        val encoded = encoder.getOutputBuffer(outIdx)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            bufferInfo.size = 0
                        }
                        if (bufferInfo.size > 0 && muxerStarted && encoded != null) {
                            encoded.position(bufferInfo.offset)
                            encoded.limit(bufferInfo.offset + bufferInfo.size)
                            localMuxer.writeSampleData(videoTrackIndex, encoded, bufferInfo)
                        }
                        encoder.releaseOutputBuffer(outIdx, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                    }
                }
            }

            for (frameIdx in 0 until totalFrames) {
                val timeMs = rangeStart + (frameIdx * 1000L) / settings.fps
                val scene = SceneEvaluator.evaluateScene(document, comp, timeMs)
                renderer.renderSceneToViewport(document, scene, outW, outH, outW, outH)

                val ptsNs = frameIdx * frameDurationUs * 1000L
                EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, ptsNs)
                EGL14.eglSwapBuffers(eglDisplay, eglSurface)
                drainEncoder(false)

                val progress = (frameIdx + 1).toFloat() / totalFrames.toFloat() * 0.94f
                if (frameIdx % 4 == 0 || frameIdx == totalFrames - 1) {
                    onProgress(progress, "Renderizando quadro ${frameIdx + 1}/$totalFrames (${(progress * 100).toInt()}%)")
                }
            }

            drainEncoder(true)

            // Mux AAC audio samples trimmed to layer bounds
            if (muxerStarted && audioTrackIndex >= 0 && audioSource != null) {
                onProgress(0.96f, "Sincronizando faixa de áudio AAC...")
                runCatching {
                    val ext = audioSource.extractor
                    val layer = audioSource.layer
                    val startOffsetUs = layer.startOffsetMs * 1000L
                    val inUs = layer.inMs * 1000L
                    val maxOutUs = minOf(rangeEnd, layer.outMs) * 1000L
                    val rangeStartUs = rangeStart * 1000L
                    ext.seekTo(startOffsetUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

                    val audioBuf = ByteBuffer.allocate(256 * 1024)
                    val audioInfo = MediaCodec.BufferInfo()
                    while (true) {
                        audioBuf.clear()
                        val sampleSize = ext.readSampleData(audioBuf, 0)
                        if (sampleSize < 0) break
                        val rawSampleUs = ext.sampleTime
                        val compPtsUs = (rawSampleUs - startOffsetUs) + inUs
                        if (compPtsUs > maxOutUs) break
                        if (compPtsUs >= maxOf(inUs, rangeStartUs)) {
                            audioInfo.offset = 0
                            audioInfo.size = sampleSize
                            audioInfo.presentationTimeUs = (compPtsUs - rangeStartUs).coerceAtLeast(0L)
                            audioInfo.flags = ext.sampleFlags
                            localMuxer.writeSampleData(audioTrackIndex, audioBuf, audioInfo)
                        }
                        ext.advance()
                    }
                }
            }

            onProgress(1f, "Salvando vídeo na galeria...")

            localMuxer.stop()
            localMuxer.release()
            muxer = null

            val savedUri = saveMp4ToMediaStore(context, tempFile, "${document.name.replace(" ", "_")}.mp4")
            ExportResult.Success(savedUri, tempFile.absolutePath)
        } catch (e: Exception) {
            ExportResult.Error(e.message ?: "Erro ao exportar vídeo MP4")
        } finally {
            runCatching { audioSource?.extractor?.release() }
            runCatching {
                if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                    EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                    if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface)
                    if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext)
                    EGL14.eglTerminate(eglDisplay)
                }
            }
            runCatching { inputSurface?.release() }
            runCatching { encoder?.stop(); encoder?.release() }
            runCatching { muxer?.release() }
        }
    }

    private fun saveMp4ToMediaStore(context: Context, file: File, displayName: String): Uri? {
        if (!file.exists()) return null
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/MaxMotion")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return Uri.fromFile(file)
        resolver.openOutputStream(uri)?.use { out ->
            file.inputStream().use { inp -> inp.copyTo(out) }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        return uri
    }
}
