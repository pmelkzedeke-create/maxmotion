package com.example.engine

import com.example.model.CompositionNode
import com.example.model.LayerNode
import com.example.render.ExportCodec
import com.example.render.ExportResolution
import com.example.render.ExportSettings

/**
 * Pure timeline / delivery ops for Fase A (ripple, slip, work area, timecode, presets).
 * Kept side-effect free so unit tests can lock the behaviour without Compose.
 */
object StudioEditOps {

    fun effectiveWorkArea(comp: CompositionNode): Pair<Long, Long> {
        val start = comp.workAreaInMs.coerceIn(0L, (comp.durationMs - 80L).coerceAtLeast(0L))
        val rawEnd = if (comp.workAreaOutMs < 0L) comp.durationMs else comp.workAreaOutMs
        val end = rawEnd.coerceIn(start + 80L, comp.durationMs.coerceAtLeast(start + 80L))
        return start to end
    }

    fun setWorkAreaIn(comp: CompositionNode, playheadMs: Long): CompositionNode {
        val inMs = playheadMs.coerceIn(0L, (comp.durationMs - 80L).coerceAtLeast(0L))
        val currentOut = if (comp.workAreaOutMs < 0L) comp.durationMs else comp.workAreaOutMs
        val outMs = currentOut.coerceAtLeast(inMs + 80L)
        return comp.copy(workAreaInMs = inMs, workAreaOutMs = outMs)
    }

    fun setWorkAreaOut(comp: CompositionNode, playheadMs: Long): CompositionNode {
        val outMs = playheadMs.coerceIn(80L, comp.durationMs.coerceAtLeast(80L))
        val inMs = comp.workAreaInMs.coerceAtMost(outMs - 80L).coerceAtLeast(0L)
        return comp.copy(workAreaInMs = inMs, workAreaOutMs = outMs)
    }

    fun clearWorkArea(comp: CompositionNode): CompositionNode =
        comp.copy(workAreaInMs = 0L, workAreaOutMs = -1L)

    fun wrapPlayhead(playheadMs: Long, comp: CompositionNode, loopWorkArea: Boolean): Long {
        val dur = comp.durationMs.coerceAtLeast(500L)
        if (!loopWorkArea) {
            return if (playheadMs >= dur) 0L else playheadMs.coerceAtLeast(0L)
        }
        val (start, end) = effectiveWorkArea(comp)
        return when {
            playheadMs >= end -> start
            playheadMs < start -> start
            else -> playheadMs
        }
    }

    /**
     * Ripple trim: change the selected clip duration and slide every clip that starts
     * at or after the original out-point, then grow/shrink the composition if needed.
     */
    fun rippleTrim(
        comp: CompositionNode,
        layerId: String,
        newInMs: Long,
        newOutMs: Long
    ): CompositionNode {
        val layer = comp.layers.firstOrNull { it.id == layerId } ?: return comp
        val clampedIn = newInMs.coerceIn(0L, (comp.durationMs - 200L).coerceAtLeast(0L))
        val clampedOut = newOutMs.coerceAtLeast(clampedIn + 200L)
        val delta = (clampedOut - clampedIn) - (layer.outMs - layer.inMs)
        if (delta == 0L && clampedIn == layer.inMs && clampedOut == layer.outMs) return comp

        val pivot = layer.outMs
        val updated = comp.layers.map { l ->
            when {
                l.id == layerId -> l.copy(inMs = clampedIn, outMs = clampedOut)
                l.inMs >= pivot - 1L -> l.copy(
                    inMs = (l.inMs + delta).coerceAtLeast(0L),
                    outMs = (l.outMs + delta).coerceAtLeast((l.inMs + delta) + 200L)
                )
                else -> l
            }
        }
        val maxOut = updated.maxOfOrNull { it.outMs } ?: clampedOut
        val nextDur = maxOf(500L, if (delta > 0L) maxOf(comp.durationMs + delta, maxOut) else maxOf(maxOut, 500L))
        val waOut = if (comp.workAreaOutMs < 0L) -1L else comp.workAreaOutMs.coerceAtMost(nextDur)
        return comp.copy(
            durationMs = nextDur,
            layers = updated,
            workAreaOutMs = waOut
        )
    }

    fun slipClip(layer: LayerNode, deltaMs: Long, sourceDurationMs: Long): LayerNode {
        val clipLen = (layer.outMs - layer.inMs).coerceAtLeast(200L)
        val maxOffset = (sourceDurationMs - clipLen).coerceAtLeast(0L)
        val next = (layer.startOffsetMs + deltaMs).coerceIn(0L, maxOffset)
        return if (next == layer.startOffsetMs) layer else layer.copy(startOffsetMs = next)
    }

    fun formatTimecode(ms: Long, fps: Int): String {
        val safeFps = fps.coerceIn(1, 120)
        val clamped = ms.coerceAtLeast(0L)
        val totalSec = clamped / 1000L
        val mins = totalSec / 60L
        val secs = totalSec % 60L
        val frames = ((clamped % 1000L) * safeFps / 1000L).coerceIn(0L, (safeFps - 1).toLong())
        return String.format(java.util.Locale.US, "%02d:%02d:%02d", mins, secs, frames)
    }

    /**
     * Accepts `mm:ss:ff` (same as [formatTimecode]), `mm:ss`, `ss.ms`, or raw milliseconds.
     */
    fun parseTimecode(raw: String, fps: Int): Long? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        val safeFps = fps.coerceIn(1, 120)
        return runCatching {
            when {
                text.contains(':') -> {
                    val parts = text.split(':').map { it.trim() }
                    when (parts.size) {
                        2 -> {
                            val m = parts[0].toLong()
                            val s = parts[1].toLong()
                            ((m * 60) + s) * 1000L
                        }
                        3 -> {
                            val m = parts[0].toLong()
                            val s = parts[1].toLong()
                            val f = parts[2].toLong()
                            ((m * 60) + s) * 1000L + (f * 1000L) / safeFps
                        }
                        else -> null
                    }
                }
                text.contains('.') || text.contains(',') -> {
                    val seconds = text.replace(',', '.').toDouble()
                    (seconds * 1000.0).toLong()
                }
                else -> text.toLong()
            }
        }.getOrNull()?.coerceAtLeast(0L)
    }

    fun deliverySettings(preset: DeliveryPreset, fpsFallback: Int = 60): ExportSettings {
        return when (preset) {
            DeliveryPreset.REELS_1080_30 -> ExportSettings(
                resolution = ExportResolution.FHD_1080,
                fps = 30,
                codec = ExportCodec.H264,
                bitrateMbps = 12
            )
            DeliveryPreset.REELS_1080_60 -> ExportSettings(
                resolution = ExportResolution.FHD_1080,
                fps = 60,
                codec = ExportCodec.H264,
                bitrateMbps = 16
            )
            DeliveryPreset.YOUTUBE_1080 -> ExportSettings(
                resolution = ExportResolution.FHD_1080,
                fps = 30,
                codec = ExportCodec.H264,
                bitrateMbps = 14
            )
            DeliveryPreset.WHATSAPP -> ExportSettings(
                resolution = ExportResolution.HD_720,
                fps = 30,
                codec = ExportCodec.H264,
                bitrateMbps = 6
            )
            DeliveryPreset.WORK_AREA -> ExportSettings(
                resolution = ExportResolution.FHD_1080,
                fps = fpsFallback.coerceIn(24, 60),
                codec = ExportCodec.H264,
                bitrateMbps = 14
            )
            DeliveryPreset.CUSTOM -> ExportSettings()
        }
    }

    fun withWorkAreaRange(settings: ExportSettings, comp: CompositionNode): ExportSettings {
        val (start, end) = effectiveWorkArea(comp)
        return settings.copy(rangeStartMs = start, rangeEndMs = end)
    }
}

enum class DeliveryPreset(val labelPt: String) {
    REELS_1080_30("Reels 1080p 30"),
    REELS_1080_60("Reels 1080p 60"),
    YOUTUBE_1080("YouTube 1080p"),
    WHATSAPP("WhatsApp compacto"),
    WORK_AREA("Só área de trabalho"),
    CUSTOM("Personalizado")
}
