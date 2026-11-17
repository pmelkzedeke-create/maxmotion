package com.example.ui.components.timeline

import com.example.model.AnimatableProperty
import com.example.model.CompositionNode
import com.example.model.LayerNode
import com.example.ui.PropertyKey
import kotlin.math.abs

sealed interface SubTrackTarget {
    data class TransformProp(val key: PropertyKey) : SubTrackTarget
    data class EffectParamProp(val effectId: String, val effectLabel: String, val paramKey: String) : SubTrackTarget
    data object VectorTrimEnd : SubTrackTarget
    data object TextRangeStart : SubTrackTarget
    data object TextRangeEnd : SubTrackTarget
    data object SpeedRampProp : SubTrackTarget
}

data class SubTrackEntry(
    val label: String,
    val target: SubTrackTarget,
    val property: AnimatableProperty
)

data class KeyframeMarkerRef(
    val keyframeId: String,
    val localTimeMs: Long,
    val label: String,
    val target: SubTrackTarget
)

data class SnapResult(
    val snappedMs: Long,
    val snapGuideMs: Long?,
    val didSnap: Boolean
)

data class RulerTickStep(
    val majorStepMs: Long,
    val minorSubdivisions: Int,
    val showFrames: Boolean
)

object TimelineSnapEngine {

    fun computeSnapThresholdMs(pxPerSec: Float, thresholdPx: Float = 24f): Long {
        if (pxPerSec <= 1f) return 120L
        return ((thresholdPx / pxPerSec) * 1000f).toLong().coerceIn(12L, 240L)
    }

    fun collectClipSnapTargets(
        composition: CompositionNode,
        excludeLayerId: String,
        playheadMs: Long
    ): List<Long> {
        val targets = ArrayList<Long>(composition.layers.size * 2 + composition.beatMarkersMs.size + 4)
        targets.add(0L)
        targets.add(composition.durationMs)
        targets.add(playheadMs)
        targets.addAll(composition.beatMarkersMs)
        composition.layers.forEach { layer ->
            if (layer.id != excludeLayerId) {
                targets.add(layer.inMs)
                targets.add(layer.outMs)
            }
        }
        return targets
    }

    fun collectKeyframeSnapTargets(
        composition: CompositionNode,
        layer: LayerNode,
        excludeKeyframeId: String,
        playheadMs: Long
    ): List<Long> {
        val targets = ArrayList<Long>(composition.beatMarkersMs.size + 16)
        targets.add(layer.inMs)
        targets.add(layer.outMs)
        targets.add(playheadMs)
        targets.addAll(composition.beatMarkersMs)
        collectAllKeyframeMarkers(layer).forEach { marker ->
            if (marker.keyframeId != excludeKeyframeId) {
                val compTimeMs = layer.inMs + (marker.localTimeMs / layer.speedMultiplier.coerceAtLeast(0.05f)).toLong()
                targets.add(compTimeMs)
            }
        }
        return targets
    }

    fun snapTimePoint(
        candidateMs: Long,
        targets: List<Long>,
        pxPerSec: Float,
        enabled: Boolean,
        minMs: Long = 0L,
        maxMs: Long = Long.MAX_VALUE
    ): SnapResult {
        val clamped = candidateMs.coerceIn(minMs, maxMs.coerceAtLeast(minMs))
        if (!enabled || targets.isEmpty()) {
            return SnapResult(clamped, null, false)
        }
        val thresholdMs = computeSnapThresholdMs(pxPerSec)
        var bestTarget: Long? = null
        var bestDist = Long.MAX_VALUE
        for (target in targets) {
            if (target < minMs || target > maxMs) continue
            val d = abs(target - clamped)
            if (d <= thresholdMs && d < bestDist) {
                bestDist = d
                bestTarget = target
            }
        }
        return if (bestTarget != null) {
            SnapResult(bestTarget, bestTarget, true)
        } else {
            SnapResult(clamped, null, false)
        }
    }

    fun snapClipMove(
        proposedInMs: Long,
        clipDurationMs: Long,
        composition: CompositionNode,
        layerId: String,
        playheadMs: Long,
        pxPerSec: Float,
        enabled: Boolean
    ): SnapResult {
        val maxIn = (composition.durationMs - clipDurationMs).coerceAtLeast(0L)
        val clampedIn = proposedInMs.coerceIn(0L, maxIn)
        if (!enabled) return SnapResult(clampedIn, null, false)

        val targets = collectClipSnapTargets(composition, layerId, playheadMs)
        val thresholdMs = computeSnapThresholdMs(pxPerSec)

        var bestIn = clampedIn
        var bestGuide: Long? = null
        var bestDist = Long.MAX_VALUE

        for (target in targets) {
            val dIn = abs(target - clampedIn)
            if (dIn <= thresholdMs && dIn < bestDist) {
                bestDist = dIn
                bestIn = target.coerceIn(0L, maxIn)
                bestGuide = target
            }
            val proposedOut = clampedIn + clipDurationMs
            val dOut = abs(target - proposedOut)
            if (dOut <= thresholdMs && dOut < bestDist) {
                bestDist = dOut
                bestIn = (target - clipDurationMs).coerceIn(0L, maxIn)
                bestGuide = target
            }
        }
        return SnapResult(bestIn, bestGuide, bestGuide != null)
    }

    fun computeRulerTickStep(pxPerSec: Float, fps: Int): RulerTickStep {
        val safeFps = fps.coerceAtLeast(1)
        return when {
            pxPerSec >= 520f -> RulerTickStep(majorStepMs = 250L, minorSubdivisions = (safeFps / 4).coerceIn(3, 15), showFrames = true)
            pxPerSec >= 260f -> RulerTickStep(majorStepMs = 500L, minorSubdivisions = 5, showFrames = true)
            pxPerSec >= 115f -> RulerTickStep(majorStepMs = 1000L, minorSubdivisions = 4, showFrames = false)
            pxPerSec >= 58f -> RulerTickStep(majorStepMs = 2000L, minorSubdivisions = 4, showFrames = false)
            else -> RulerTickStep(majorStepMs = 5000L, minorSubdivisions = 5, showFrames = false)
        }
    }
}

fun collectLayerSubTracks(layer: LayerNode): List<SubTrackEntry> {
    val t = layer.transform
    val entries = mutableListOf(
        SubTrackEntry("Posição X", SubTrackTarget.TransformProp(PropertyKey.POS_X), t.posX),
        SubTrackEntry("Posição Y", SubTrackTarget.TransformProp(PropertyKey.POS_Y), t.posY),
        SubTrackEntry("Posição Z", SubTrackTarget.TransformProp(PropertyKey.POS_Z), t.posZ),
        SubTrackEntry("Escala X", SubTrackTarget.TransformProp(PropertyKey.SCALE_X), t.scaleX),
        SubTrackEntry("Escala Y", SubTrackTarget.TransformProp(PropertyKey.SCALE_Y), t.scaleY),
        SubTrackEntry("Rotação Z", SubTrackTarget.TransformProp(PropertyKey.ROT_Z), t.rotZ),
        SubTrackEntry("Rotação X", SubTrackTarget.TransformProp(PropertyKey.ROT_X), t.rotX),
        SubTrackEntry("Rotação Y", SubTrackTarget.TransformProp(PropertyKey.ROT_Y), t.rotY),
        SubTrackEntry("Opacidade", SubTrackTarget.TransformProp(PropertyKey.OPACITY), t.opacity),
        SubTrackEntry("Âncora X", SubTrackTarget.TransformProp(PropertyKey.ANCHOR_X), t.anchorX),
        SubTrackEntry("Âncora Y", SubTrackTarget.TransformProp(PropertyKey.ANCHOR_Y), t.anchorY),
        SubTrackEntry("Inclinação", SubTrackTarget.TransformProp(PropertyKey.SKEW_X), t.skewX)
    )
    layer.vectorSpec?.let { entries.add(SubTrackEntry("Trim Fim", SubTrackTarget.VectorTrimEnd, it.trimEnd)) }
    layer.textSpec?.let {
        entries.add(SubTrackEntry("Texto Início", SubTrackTarget.TextRangeStart, it.rangeStart))
        entries.add(SubTrackEntry("Texto Fim", SubTrackTarget.TextRangeEnd, it.rangeEnd))
    }
    layer.speedRamp?.let { entries.add(SubTrackEntry("Speed Ramp", SubTrackTarget.SpeedRampProp, it)) }
    layer.effects.forEach { eff ->
        eff.params.forEach { (k, prop) ->
            entries.add(
                SubTrackEntry(
                    label = "${eff.type.labelPt.take(8)}.$k",
                    target = SubTrackTarget.EffectParamProp(eff.id, eff.type.labelPt, k),
                    property = prop
                )
            )
        }
    }
    return entries.filter { it.property.keyframes.isNotEmpty() || !it.property.expression.isNullOrBlank() }
}

fun collectAllKeyframeMarkers(layer: LayerNode): List<KeyframeMarkerRef> {
    val list = mutableListOf<KeyframeMarkerRef>()
    collectLayerSubTracks(layer).forEach { entry ->
        entry.property.keyframes.forEach { kf ->
            list.add(
                KeyframeMarkerRef(
                    keyframeId = kf.id,
                    localTimeMs = kf.timeMs,
                    label = entry.label,
                    target = entry.target
                )
            )
        }
    }
    return list.sortedBy { it.localTimeMs }
}
