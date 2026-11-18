package com.example

import com.example.engine.DeliveryPreset
import com.example.engine.StudioEditOps
import com.example.model.CompositionNode
import com.example.model.LayerNode
import com.example.model.LayerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioEditOpsTest {

    private fun clip(id: String, inMs: Long, outMs: Long, offset: Long = 0L) = LayerNode(
        id = id,
        name = id,
        type = LayerType.VIDEO,
        inMs = inMs,
        outMs = outMs,
        startOffsetMs = offset
    )

    @Test
    fun `parse timecode mm ss ff and raw ms`() {
        assertEquals(0L, StudioEditOps.parseTimecode("00:00:00", 60))
        assertEquals(1000L, StudioEditOps.parseTimecode("00:01:00", 60))
        assertEquals(61_000L, StudioEditOps.parseTimecode("01:01", 60))
        assertEquals(1500L, StudioEditOps.parseTimecode("1.5", 60))
        assertEquals(2400L, StudioEditOps.parseTimecode("2400", 60))
        assertNull(StudioEditOps.parseTimecode("abc", 60))
        assertNull(StudioEditOps.parseTimecode("", 60))
        val labeled = StudioEditOps.formatTimecode(600L, 60)
        assertEquals("00:00:36", labeled)
        assertEquals(600L, StudioEditOps.parseTimecode(labeled, 60))
        assertEquals(1_000L, StudioEditOps.parseTimecode(StudioEditOps.formatTimecode(1_000L, 60), 60))
    }

    @Test
    fun `work area clamps and wrap loops the region`() {
        val comp = CompositionNode(durationMs = 8000L, workAreaInMs = 1000L, workAreaOutMs = 4000L)
        val (start, end) = StudioEditOps.effectiveWorkArea(comp)
        assertEquals(1000L, start)
        assertEquals(4000L, end)
        assertEquals(1000L, StudioEditOps.wrapPlayhead(4000L, comp, loopWorkArea = true))
        assertEquals(1000L, StudioEditOps.wrapPlayhead(500L, comp, loopWorkArea = true))
        assertEquals(2500L, StudioEditOps.wrapPlayhead(2500L, comp, loopWorkArea = true))
        assertEquals(0L, StudioEditOps.wrapPlayhead(8000L, CompositionNode(durationMs = 8000L), loopWorkArea = true))
    }

    @Test
    fun `ripple trim out slides later clips and grows duration`() {
        val a = clip("a", 0, 2000)
        val b = clip("b", 2000, 5000)
        val c = clip("c", 5500, 7000)
        val comp = CompositionNode(durationMs = 8000L, layers = listOf(a, b, c))
        val next = StudioEditOps.rippleTrim(comp, "a", 0, 3000)
        val nb = next.layers.first { it.id == "b" }
        val nc = next.layers.first { it.id == "c" }
        assertEquals(3000L, next.layers.first { it.id == "a" }.outMs)
        assertEquals(3000L, nb.inMs)
        assertEquals(6000L, nb.outMs)
        assertEquals(6500L, nc.inMs)
        assertTrue(next.durationMs >= 8000L)

        val shrunk = StudioEditOps.rippleTrim(comp, "a", 0, 1000)
        assertEquals(1000L, shrunk.layers.first { it.id == "a" }.outMs)
        assertEquals(1000L, shrunk.layers.first { it.id == "b" }.inMs)
        assertEquals(4000L, shrunk.layers.first { it.id == "b" }.outMs)
    }

    @Test
    fun `slip stays inside source duration`() {
        val layer = clip("v", 0, 2000, offset = 0)
        val slipped = StudioEditOps.slipClip(layer, 500, sourceDurationMs = 5000)
        assertEquals(500L, slipped.startOffsetMs)
        val clamped = StudioEditOps.slipClip(layer, 9_000, sourceDurationMs = 5000)
        assertEquals(3000L, clamped.startOffsetMs)
        val same = StudioEditOps.slipClip(layer.copy(startOffsetMs = 0), -200, sourceDurationMs = 5000)
        assertEquals(0L, same.startOffsetMs)
    }

    @Test
    fun `delivery presets map bitrate and fps`() {
        val wa = StudioEditOps.deliverySettings(DeliveryPreset.WHATSAPP)
        assertEquals(30, wa.fps)
        assertEquals(6, wa.bitrateMbps)
        val reels = StudioEditOps.deliverySettings(DeliveryPreset.REELS_1080_60)
        assertEquals(60, reels.fps)
        assertEquals(16, reels.bitrateMbps)
        val work = StudioEditOps.withWorkAreaRange(
            StudioEditOps.deliverySettings(DeliveryPreset.WORK_AREA, 60),
            CompositionNode(durationMs = 8000L, workAreaInMs = 1000L, workAreaOutMs = 3000L)
        )
        assertEquals(1000L, work.rangeStartMs)
        assertEquals(3000L, work.rangeEndMs)
    }
}
