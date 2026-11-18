package com.example

import com.example.model.LayerNode
import com.example.model.LayerType
import com.example.model.TextRangeMode
import com.example.model.TextSpec
import com.example.model.TypePresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TypePresetsTest {

    private fun textLayer() = LayerNode(
        id = "t1",
        name = "Título",
        type = LayerType.TEXT,
        inMs = 0L,
        outMs = 4000L,
        textSpec = TextSpec(content = "MAXMOTION", fontFamilyKey = "space_grotesk")
    )

    @Test
    fun `catalog has 30 unique type presets`() {
        assertEquals(30, TypePresets.catalog.size)
        assertEquals(30, TypePresets.catalog.map { it.id }.toSet().size)
    }

    @Test
    fun `typewriter sweeps range and hides unselected chars`() {
        val next = TypePresets.apply(textLayer(), "typewriter")
        val ts = next.textSpec!!
        assertEquals("MAXMOTION", ts.content)
        assertEquals(TextRangeMode.CHARACTER, ts.rangeMode)
        assertEquals(0f, ts.animOpacity.baseValue)
        assertTrue(ts.rangeEnd.keyframes.size >= 2)
        assertEquals(0f, ts.rangeEnd.keyframes.first().value)
        assertEquals(1f, ts.rangeEnd.keyframes.last().value)
        assertEquals("space_grotesk", ts.fontFamilyKey)
    }

    @Test
    fun `scramble_in keeps glyphs visible while resolving`() {
        val next = TypePresets.apply(textLayer(), "scramble_in")
        val ts = next.textSpec!!
        assertEquals(1f, ts.scrambleAmount, 0.01f)
        assertEquals(1f, ts.animOpacity.baseValue)
        assertTrue(ts.rangeEnd.isAnimated)
    }

    @Test
    fun `unknown preset leaves layer untouched`() {
        val layer = textLayer()
        assertEquals(layer, TypePresets.apply(layer, "nao_existe"))
    }

    @Test
    fun `outro_fade animates range start near the end`() {
        val next = TypePresets.apply(textLayer(), "outro_fade")
        val ts = next.textSpec!!
        assertTrue(ts.rangeStart.keyframes.last().timeMs >= 3000L)
        assertEquals(1f, ts.rangeStart.keyframes.last().value)
    }

    @Test
    fun `every catalog preset keeps content and applying typewriter after slam clears slam scale`() {
        TypePresets.catalog.forEach { info ->
            val next = TypePresets.apply(textLayer(), info.id)
            assertEquals("${info.id} não pode apagar o texto", "MAXMOTION", next.textSpec?.content)
            assertTrue("${info.id} precisa de textSpec", next.textSpec != null)
        }
        val slammed = TypePresets.apply(textLayer(), "slam_title")
        assertTrue(slammed.transform.scaleX.keyframes.isNotEmpty())
        val typed = TypePresets.apply(slammed, "typewriter")
        assertTrue(typed.transform.scaleX.keyframes.isEmpty())
        assertEquals(1f, typed.transform.scaleX.baseValue)
        assertTrue(typed.textSpec!!.tracking.keyframes.isEmpty())
    }
}
