package com.example.engine

import com.example.model.AnimatableProperty
import com.example.model.BezierCurve
import com.example.model.EasingType
import com.example.model.Keyframe
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

object CurveInterpolator {

    data class CurvePreset(
        val id: String,
        val namePt: String,
        val categoryPt: String,
        val bezier: BezierCurve
    )

    val builtInPresets: List<CurvePreset> = listOf(
        CurvePreset("linear", "Linear", "Básico", BezierCurve(0f, 0f, 1f, 1f)),
        CurvePreset("snappy_flow", "Snappy Flow", "Edits", BezierCurve(0.16f, 1.0f, 0.30f, 1.0f)),
        CurvePreset("expo_out", "Expo Out", "Edits", BezierCurve(0.19f, 1.0f, 0.22f, 1.0f)),
        CurvePreset("expo_in", "Expo In", "Edits", BezierCurve(0.70f, 0.0f, 0.84f, 0.0f)),
        CurvePreset("ease_in_out", "Suave In-Out", "Básico", BezierCurve(0.42f, 0.0f, 0.58f, 1.0f)),
        CurvePreset("overshoot", "Overshoot", "Dinâmico", BezierCurve(0.34f, 1.56f, 0.64f, 1.0f)),
        CurvePreset("anticipate", "Antecipação", "Dinâmico", BezierCurve(0.60f, -0.35f, 0.40f, 1.0f)),
        CurvePreset("elastic_snap", "Elástico Rápido", "Dinâmico", BezierCurve(0.25f, 1.45f, 0.35f, 0.96f))
    )

    fun evaluateKeyframes(prop: AnimatableProperty, timeMs: Long): Float {
        val kfs = prop.keyframes
        if (kfs.isEmpty()) return prop.baseValue
        if (kfs.size == 1) return kfs.first().value
        if (timeMs <= kfs.first().timeMs) return kfs.first().value
        if (timeMs >= kfs.last().timeMs) return kfs.last().value

        for (i in 0 until kfs.size - 1) {
            val k0 = kfs[i]
            val k1 = kfs[i + 1]
            if (timeMs in k0.timeMs..k1.timeMs) {
                val span = (k1.timeMs - k0.timeMs).coerceAtLeast(1L)
                val t = (timeMs - k0.timeMs).toFloat() / span.toFloat()
                val eased = easeProgress(t, k0.easing, k0.bezier)
                return k0.value + (k1.value - k0.value) * eased
            }
        }
        return prop.baseValue
    }

    fun easeProgress(tRaw: Float, easing: EasingType, bezier: BezierCurve): Float {
        val t = tRaw.coerceIn(0f, 1f)
        return when (easing) {
            EasingType.LINEAR -> t
            EasingType.HOLD -> if (t >= 1f) 1f else 0f
            EasingType.EASE_IN -> solveCubicBezierY(t, 0.42f, 0f, 1f, 1f)
            EasingType.EASE_OUT -> solveCubicBezierY(t, 0f, 0f, 0.58f, 1f)
            EasingType.EASE_IN_OUT -> solveCubicBezierY(t, 0.42f, 0f, 0.58f, 1f)
            EasingType.BEZIER -> solveCubicBezierY(t, bezier.x1, bezier.y1, bezier.x2, bezier.y2)
        }
    }

    /**
     * Exact cubic Bezier evaluation using Newton-Raphson with binary search fallback.
     * Supports overshoot control points where y1/y2 lie outside [0, 1].
     */
    fun solveCubicBezierY(xTarget: Float, x1In: Float, y1: Float, x2In: Float, y2: Float): Float {
        if (xTarget <= 0f) return 0f
        if (xTarget >= 1f) return 1f
        val x1 = x1In.coerceIn(0f, 1f)
        val x2 = x2In.coerceIn(0f, 1f)
        if (x1 == 0f && y1 == 0f && x2 == 1f && y2 == 1f) return xTarget

        var u = xTarget
        for (i in 0 until 8) {
            val currentX = sampleBezier1D(u, x1, x2) - xTarget
            if (abs(currentX) < 1e-5f) {
                return sampleBezier1D(u, y1, y2)
            }
            val dx = sampleBezierDerivative1D(u, x1, x2)
            if (abs(dx) < 1e-6f) break
            u = (u - currentX / dx).coerceIn(0f, 1f)
        }

        // Binary search fallback for extreme curves
        var lo = 0f
        var hi = 1f
        u = xTarget
        for (i in 0 until 16) {
            val xVal = sampleBezier1D(u, x1, x2)
            if (abs(xVal - xTarget) < 1e-5f) break
            if (xTarget > xVal) lo = u else hi = u
            u = (lo + hi) * 0.5f
        }
        return sampleBezier1D(u, y1, y2)
    }

    fun sampleBezier1D(u: Float, p1: Float, p2: Float): Float {
        val inv = 1f - u
        return 3f * inv * inv * u * p1 + 3f * inv * u * u * p2 + u * u * u
    }

    private fun sampleBezierDerivative1D(u: Float, p1: Float, p2: Float): Float {
        val inv = 1f - u
        return 3f * inv * inv * p1 + 6f * inv * u * (p2 - p1) + 3f * u * u * (1f - p2)
    }

    fun sampleValueCurve(bezier: BezierCurve, steps: Int = 64): List<Pair<Float, Float>> {
        return (0..steps).map { i ->
            val x = i.toFloat() / steps.toFloat()
            val y = solveCubicBezierY(x, bezier.x1, bezier.y1, bezier.x2, bezier.y2)
            x to y
        }
    }

    fun sampleSpeedCurve(bezier: BezierCurve, steps: Int = 64): List<Pair<Float, Float>> {
        val values = sampleValueCurve(bezier, steps)
        val dt = 1f / steps.toFloat()
        return values.mapIndexed { idx, (x, y) ->
            val prevY = if (idx > 0) values[idx - 1].second else y
            val nextY = if (idx < values.lastIndex) values[idx + 1].second else y
            val span = if (idx == 0 || idx == values.lastIndex) dt else 2f * dt
            val speed = (nextY - prevY) / span
            x to speed
        }
    }

    fun bounceProgress(t: Float): Float {
        val n1 = 7.5625f
        val d1 = 2.75f
        return when {
            t < 1f / d1 -> n1 * t * t
            t < 2f / d1 -> {
                val x = t - 1.5f / d1
                n1 * x * x + 0.75f
            }
            t < 2.5f / d1 -> {
                val x = t - 2.25f / d1
                n1 * x * x + 0.9375f
            }
            else -> {
                val x = t - 2.625f / d1
                n1 * x * x + 0.984375f
            }
        }
    }

    fun elasticProgress(t: Float): Float {
        if (t <= 0f) return 0f
        if (t >= 1f) return 1f
        val c4 = (2.0 * PI) / 3.0
        return (2.0.pow((-10.0 * t)) * sin((t * 10.0 - 0.75) * c4) + 1.0).toFloat()
    }

    fun sortAndUpsertKeyframe(
        prop: AnimatableProperty,
        timeMs: Long,
        value: Float,
        easing: EasingType = EasingType.BEZIER,
        bezier: BezierCurve = BezierCurve(0.22f, 1f, 0.36f, 1f)
    ): AnimatableProperty {
        val existingIdx = prop.keyframes.indexOfFirst { abs(it.timeMs - timeMs) <= 20L }
        val updated = if (existingIdx >= 0) {
            prop.keyframes.toMutableList().apply {
                this[existingIdx] = this[existingIdx].copy(timeMs = timeMs, value = value)
            }
        } else {
            (prop.keyframes + Keyframe(
                timeMs = timeMs,
                value = value,
                easing = easing,
                bezier = bezier
            )).sortedBy { it.timeMs }
        }
        return prop.copy(keyframes = updated)
    }

    fun toggleKeyframeAt(prop: AnimatableProperty, timeMs: Long, currentValue: Float): AnimatableProperty {
        val existing = prop.keyframes.firstOrNull { abs(it.timeMs - timeMs) <= 25L }
        return if (existing != null) {
            prop.copy(keyframes = prop.keyframes.filterNot { it.id == existing.id })
        } else {
            sortAndUpsertKeyframe(prop, timeMs, currentValue)
        }
    }
}
