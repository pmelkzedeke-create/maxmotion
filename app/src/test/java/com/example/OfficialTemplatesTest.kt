package com.example

import com.example.model.BrandKit
import com.example.model.EssentialRole
import com.example.model.OfficialTemplates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficialTemplatesTest {

    @Test
    fun `catalog has 24 official templates`() {
        assertEquals(24, OfficialTemplates.catalog.size)
        assertEquals(24, OfficialTemplates.catalog.map { it.id }.toSet().size)
        assertEquals(12, OfficialTemplates.catalog.count { it.category == "9:16" })
        assertEquals(12, OfficialTemplates.catalog.count { it.category == "16:9" || it.category == "1:1" })
    }

    @Test
    fun `every official template builds with a title slot`() {
        OfficialTemplates.catalog.forEach { info ->
            val doc = OfficialTemplates.build(info.id)
            assertEquals(info.name, doc.name)
            assertEquals(info.aspect.width, doc.activeComposition.width)
            assertEquals(info.aspect.height, doc.activeComposition.height)
            assertEquals(info.durationMs, doc.activeComposition.durationMs)
            val title = doc.activeComposition.layers.firstOrNull { it.essentialRole == EssentialRole.TITLE }
            assertTrue("${info.id} precisa de título", title != null)
            assertEquals(info.brand.title, title?.textSpec?.content)
        }
    }

    @Test
    fun `build template applies brand slots without breaking layers`() {
        val doc = OfficialTemplates.build("tpl_neon_intro")
        val layers = doc.activeComposition.layers
        assertTrue(layers.any { it.essentialRole == EssentialRole.TITLE })
        assertTrue(layers.any { it.essentialRole == EssentialRole.LOGO })
        val bgBefore = doc.activeComposition.backgroundColorHex
        val titleKfBefore = doc.activeComposition.layers
            .first { it.essentialRole == EssentialRole.TITLE }
            .transform.opacity.keyframes.size
        val branded = OfficialTemplates.applyBrand(
            doc,
            BrandKit(
                title = "LOJA X",
                subtitle = "MEDICILÂNDIA",
                primaryHex = "#FF3366",
                secondaryHex = "#110011",
                fontFamilyKey = "jetbrains_mono"
            )
        )
        val title = branded.activeComposition.layers.first { it.essentialRole == EssentialRole.TITLE }
        val sub = branded.activeComposition.layers.first { it.essentialRole == EssentialRole.SUBTITLE }
        val logo = branded.activeComposition.layers.first { it.essentialRole == EssentialRole.LOGO }
        assertEquals("LOJA X", title.textSpec?.content)
        assertEquals("jetbrains_mono", title.textSpec?.fontFamilyKey)
        assertEquals("MEDICILÂNDIA", sub.textSpec?.content)
        assertEquals("#FF3366", logo.vectorSpec?.fillColorHex)
        assertEquals("#110011", logo.vectorSpec?.fillColorEndHex)
        assertEquals("LOJA X", branded.brand.title)
        assertEquals(bgBefore, branded.activeComposition.backgroundColorHex)
        assertEquals(titleKfBefore, title.transform.opacity.keyframes.size)
        assertTrue(titleKfBefore >= 2)
    }
}
