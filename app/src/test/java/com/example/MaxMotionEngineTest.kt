package com.example

import com.example.engine.CubeLutParser
import com.example.engine.CurveInterpolator
import com.example.engine.ExpressionContext
import com.example.engine.ExpressionEvaluator
import com.example.engine.SceneEvaluator
import com.example.engine.SpeedRampEvaluator
import com.example.engine.UndoRedoManager
import com.example.model.AnimatableProperty
import com.example.model.BezierCurve
import com.example.model.CurvePoint
import com.example.model.DemoProjectFactory
import com.example.model.EasingType
import com.example.model.Keyframe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MaxMotionEngineTest {

    @Test
    fun `cubic bezier boundary and midpoint evaluation`() {
        assertEquals(0f, CurveInterpolator.solveCubicBezierY(0f, 0.22f, 1f, 0.36f, 1f), 1e-4f)
        assertEquals(1f, CurveInterpolator.solveCubicBezierY(1f, 0.22f, 1f, 0.36f, 1f), 1e-4f)
        val linearMid = CurveInterpolator.solveCubicBezierY(0.5f, 0f, 0f, 1f, 1f)
        assertEquals(0.5f, linearMid, 1e-3f)
        val snappyMid = CurveInterpolator.solveCubicBezierY(0.5f, 0.16f, 1f, 0.3f, 1f)
        assertTrue(snappyMid > 0.8f)
    }

    @Test
    fun `expression evaluator handles math wiggle loopOut and layer pickwhip`() {
        val demo = DemoProjectFactory.createDemoProject()
        val comp = demo.activeComposition
        val prop = AnimatableProperty(
            baseValue = 10f,
            keyframes = listOf(
                Keyframe(timeMs = 0L, value = 0f, easing = EasingType.LINEAR),
                Keyframe(timeMs = 1000L, value = 100f, easing = EasingType.LINEAR)
            )
        )
        val ctx = ExpressionContext(
            timeMs = 1500L,
            fps = 60,
            durationMs = 8000L,
            baseKeyframeValue = 100f,
            currentProperty = prop,
            composition = comp
        )
        val cycleRes = ExpressionEvaluator.evaluate("loopOut(\"cycle\")", ctx)
        assertNull(cycleRes.error)
        assertEquals(50f, cycleRes.value, 1f)

        val wiggleRes = ExpressionEvaluator.evaluate("wiggle(4, 20)", ctx)
        assertNull(wiggleRes.error)
        assertTrue(wiggleRes.value in 80f..120f)

        val pickWhipRes = ExpressionEvaluator.evaluate("layer(\"Prisma Cinético\").scaleX * 100", ctx)
        assertNull(pickWhipRes.error)
        assertTrue(pickWhipRes.value > 50f)
    }

    @Test
    fun `undo redo manager restores snapshots cleanly`() {
        val mgr = UndoRedoManager()
        val doc1 = DemoProjectFactory.createDemoProject()
        val doc2 = doc1.copy(name = "Alterado")
        mgr.record("Renomear", doc1)
        assertTrue(mgr.canUndo)
        val undone = mgr.undo(doc2)
        assertNotNull(undone)
        assertEquals(doc1.name, undone?.name)
        assertTrue(mgr.canRedo)
        val redone = mgr.redo(doc1)
        assertEquals("Alterado", redone?.name)
    }

    @Test
    fun `scene evaluator resolves parenting and demo layers`() {
        val demo = DemoProjectFactory.createDemoProject()
        val scene = SceneEvaluator.evaluateScene(demo, demo.activeComposition, 900L)
        assertTrue(scene.layers.isNotEmpty())
        val prism = scene.layers.firstOrNull { it.layer.name == "Prisma Cinético" }
        assertNotNull(prism)
    }

    @Test
    fun `speed ramp evaluator integrates constant and accelerated curves smoothly`() {
        val constantTime = SpeedRampEvaluator.integrateRemappedTimeMs(null, 1000L, 1.0f)
        assertEquals(1000L, constantTime)

        val rampUp = AnimatableProperty(
            baseValue = 1.0f,
            keyframes = listOf(
                Keyframe(timeMs = 0L, value = 1.0f, easing = EasingType.LINEAR),
                Keyframe(timeMs = 1000L, value = 3.0f, easing = EasingType.LINEAR)
            )
        )
        val accelerated = SpeedRampEvaluator.integrateRemappedTimeMs(rampUp, 1000L, 1.0f)
        assertTrue(accelerated in 1900L..2100L)
    }

    @Test
    fun `cube lut parser parses 3d cube format and evaluates rgb curves`() {
        val lines = mutableListOf(
            "TITLE \"Cinema Warm\"",
            "LUT_3D_SIZE 2",
            "DOMAIN_MIN 0.0 0.0 0.0",
            "DOMAIN_MAX 1.0 1.0 1.0"
        )
        for (b in 0..1) {
            for (g in 0..1) {
                for (r in 0..1) {
                    lines.add("$r.0 $g.0 $b.0")
                }
            }
        }
        val parsed = CubeLutParser.parseCubeLines("Fallback", lines)
        assertNotNull(parsed)
        assertEquals("Cinema Warm", parsed?.name)
        assertEquals(2, parsed?.size)
        assertEquals(2 * 2 * 2 * 3, parsed?.rgbData?.size)

        val sCurve = listOf(
            CurvePoint(0f, 0f),
            CurvePoint(0.25f, 0.15f),
            CurvePoint(0.75f, 0.88f),
            CurvePoint(1f, 1f)
        )
        assertEquals(0f, CubeLutParser.evalCurve(sCurve, 0f), 1e-4f)
        assertTrue(CubeLutParser.evalCurve(sCurve, 0.25f) < 0.25f)
        assertTrue(CubeLutParser.evalCurve(sCurve, 0.75f) > 0.75f)
        assertEquals(1f, CubeLutParser.evalCurve(sCurve, 1f), 1e-4f)
    }
}
