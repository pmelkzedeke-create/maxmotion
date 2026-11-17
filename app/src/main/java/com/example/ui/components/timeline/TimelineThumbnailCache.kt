package com.example.ui.components.timeline

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.example.model.AssetItem
import com.example.model.AssetType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object TimelineThumbnailCache {

    private const val MAX_CACHE_BYTES = 16 * 1024 * 1024

    private val cache = object : LruCache<String, Bitmap>(MAX_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount.coerceAtLeast(1)
    }

    fun quantizeTimestampMs(timestampMs: Long, stepMs: Long): Long {
        val safeStep = stepMs.coerceAtLeast(120L)
        return (timestampMs / safeStep) * safeStep
    }

    suspend fun getFrame(
        context: Context,
        asset: AssetItem,
        timestampMs: Long,
        quantStepMs: Long = 250L
    ): Bitmap? {
        if (asset.uri.isBlank()) return null
        val qTime = if (asset.type == AssetType.IMAGE) 0L else quantizeTimestampMs(timestampMs.coerceAtLeast(0L), quantStepMs)
        val key = "${asset.id}_${asset.type.name}_$qTime"
        cache.get(key)?.let { return it }

        val decoded = withContext(Dispatchers.IO) {
            when (asset.type) {
                AssetType.VIDEO -> decodeVideoFrame(context, asset.uri, qTime)
                AssetType.IMAGE -> decodeImageThumb(context, asset.uri)
                else -> null
            }
        }
        if (decoded != null) {
            cache.put(key, decoded)
        }
        return decoded
    }

    private fun decodeVideoFrame(context: Context, uriStr: String, timestampMs: Long): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, Uri.parse(uriStr))
            val us = timestampMs * 1000L
            val raw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 112, 72)
            } else {
                retriever.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }
            raw?.let { downscaleIfNeeded(it, 112, 72) }
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun decodeImageThumb(context: Context, uriStr: String): Bitmap? {
        return try {
            val uri = Uri.parse(uriStr)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            val sample = calculateInSampleSize(bounds.outWidth, bounds.outHeight, 112, 72)
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, opts)
            }
            bmp?.let { downscaleIfNeeded(it, 112, 72) }
        } catch (_: Throwable) {
            null
        }
    }

    private fun downscaleIfNeeded(src: Bitmap, maxW: Int, maxH: Int): Bitmap {
        if (src.width <= maxW && src.height <= maxH) return src
        val ratio = minOf(maxW.toFloat() / src.width.coerceAtLeast(1), maxH.toFloat() / src.height.coerceAtLeast(1))
        val targetW = (src.width * ratio).toInt().coerceAtLeast(16)
        val targetH = (src.height * ratio).toInt().coerceAtLeast(16)
        return Bitmap.createScaledBitmap(src, targetW, targetH, true)
    }

    private fun calculateInSampleSize(srcW: Int, srcH: Int, reqW: Int, reqH: Int): Int {
        var inSampleSize = 1
        if (srcH > reqH || srcW > reqW) {
            val halfH = srcH / 2
            val halfW = srcW / 2
            while (halfH / inSampleSize >= reqH && halfW / inSampleSize >= reqW) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }
}

@Composable
fun rememberFilmstripFrames(
    asset: AssetItem?,
    clipDurationMs: Long,
    startOffsetMs: Long,
    speedMultiplier: Float,
    tileCount: Int
): List<ImageBitmap?> {
    val context = LocalContext.current
    val safeTiles = tileCount.coerceIn(1, 28)
    val state = remember(asset?.id, safeTiles) {
        mutableStateOf<List<ImageBitmap?>>(List(safeTiles) { null })
    }

    LaunchedEffect(asset?.id, asset?.uri, clipDurationMs, startOffsetMs, speedMultiplier, safeTiles) {
        if (asset == null || asset.uri.isBlank() || (asset.type != AssetType.VIDEO && asset.type != AssetType.IMAGE)) {
            state.value = emptyList()
            return@LaunchedEffect
        }
        if (asset.type == AssetType.IMAGE) {
            val bmp = TimelineThumbnailCache.getFrame(context, asset, 0L)
            val img = bmp?.asImageBitmap()
            state.value = List(safeTiles) { img }
            return@LaunchedEffect
        }
        val stepMs = (clipDurationMs / safeTiles.coerceAtLeast(1)).coerceAtLeast(120L)
        val buffer = MutableList<ImageBitmap?>(safeTiles) { null }
        for (idx in 0 until safeTiles) {
            val sampleTime = startOffsetMs + ((idx * stepMs) * speedMultiplier.coerceAtLeast(0.05f)).toLong()
            val bmp = TimelineThumbnailCache.getFrame(context, asset, sampleTime, quantStepMs = (stepMs / 2L).coerceAtLeast(160L))
            if (bmp != null) {
                buffer[idx] = bmp.asImageBitmap()
                state.value = buffer.toList()
            }
        }
    }
    return state.value
}
