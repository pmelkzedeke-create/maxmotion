package com.example.engine

import com.example.model.AnimatableProperty
import com.example.model.BezierCurve
import com.example.model.EasingType
import com.example.model.Keyframe

object SpeedRampEvaluator {

    data class SpeedPreset(
        val id: String,
        val labelPt: String,
        val property: AnimatableProperty
    )

    fun presets(durationMs: Long): List<SpeedPreset> {
        val d = durationMs.coerceAtLeast(1000L)
        val snap = BezierCurve(0.22f, 1f, 0.36f, 1f)
        return listOf(
            SpeedPreset(
                "flash_edit",
                "Flash Edit (2.8x → 0.3x → 2.5x)",
                AnimatableProperty(
                    baseValue = 1.0f,
                    keyframes = listOf(
                        Keyframe(timeMs = 0L, value = 2.8f, easing = EasingType.BEZIER, bezier = snap),
                        Keyframe(timeMs = (d * 0.35f).toLong(), value = 0.3f, easing = EasingType.BEZIER, bezier = snap),
                        Keyframe(timeMs = (d * 0.72f).toLong(), value = 0.35f, easing = EasingType.BEZIER, bezier = snap),
                        Keyframe(timeMs = d, value = 2.5f, easing = EasingType.EASE_OUT)
                    )
                )
            ),
            SpeedPreset(
                "bullet_time",
                "Bullet Time (Slow-Mo Central)",
                AnimatableProperty(
                    baseValue = 1.0f,
                    keyframes = listOf(
                        Keyframe(timeMs = 0L, value = 1.8f, easing = EasingType.EASE_IN_OUT),
                        Keyframe(timeMs = (d * 0.3f).toLong(), value = 0.25f, easing = EasingType.EASE_IN_OUT),
                        Keyframe(timeMs = (d * 0.75f).toLong(), value = 0.25f, easing = EasingType.EASE_IN_OUT),
                        Keyframe(timeMs = d, value = 1.8f, easing = EasingType.EASE_OUT)
                    )
                )
            ),
            SpeedPreset(
                "ramp_up",
                "Aceleração Progressiva",
                AnimatableProperty(
                    baseValue = 1.0f,
                    keyframes = listOf(
                        Keyframe(timeMs = 0L, value = 0.4f, easing = EasingType.BEZIER, bezier = BezierCurve(0.7f, 0f, 0.84f, 0f)),
                        Keyframe(timeMs = d, value = 3.2f, easing = EasingType.LINEAR)
                    )
                )
            )
        )
    }

    /**
     * Numerically integrates speed multiplier curve from 0..elapsedMs using trapezoidal rule
     * so source time advances smoothly without jumps.
     */
    fun integrateRemappedTimeMs(speedRamp: AnimatableProperty?, elapsedMs: Long, fallbackMultiplier: Float): Long {
        if (elapsedMs <= 0L) return 0L
        if (speedRamp == null || (speedRamp.keyframes.isEmpty() && speedRamp.expression.isNullOrBlank())) {
            return (elapsedMs * fallbackMultiplier).toLong()
        }
        val steps = 32
        val dtMs = elapsedMs.toFloat() / steps.toFloat()
        var accumMs = 0.0
        var prevSpeed = CurveInterpolator.evaluateKeyframes(speedRamp, 0L).coerceIn(0.1f, 6.0f)
        for (i in 1..steps) {
            val t = (i * dtMs).toLong()
            val curSpeed = CurveInterpolator.evaluateKeyframes(speedRamp, t).coerceIn(0.1f, 6.0f)
            accumMs += ((prevSpeed + curSpeed) * 0.5) * dtMs
            prevSpeed = curSpeed
        }
        return accumMs.toLong().coerceAtLeast(0L)
    }
}
