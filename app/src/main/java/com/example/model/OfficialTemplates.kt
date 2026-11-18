package com.example.model

data class TemplateInfo(
    val id: String,
    val name: String,
    val category: String,
    val aspect: AspectRatioPreset,
    val durationMs: Long,
    val family: TemplateFamily,
    val brand: BrandKit
)

enum class TemplateFamily {
    INTRO, LOWER_THIRD, COVER, SALE, QUOTE, BUMPER, END_SLATE, EVENT
}

object OfficialTemplates {

    val catalog: List<TemplateInfo> = listOf(
        t("tpl_neon_intro", "Intro Neon", "9:16", AspectRatioPreset.PORTRAIT_9_16, 5000, TemplateFamily.INTRO, BrandKit("MAXMOTION", "MOTION • SHADERS")),
        t("tpl_news_lt", "Lower-third News", "9:16", AspectRatioPreset.PORTRAIT_9_16, 4000, TemplateFamily.LOWER_THIRD, BrandKit("AO VIVO", "Belém • Pará", "#FF3B5C", "#111116")),
        t("tpl_podcast", "Podcast Cover", "9:16", AspectRatioPreset.PORTRAIT_9_16, 5000, TemplateFamily.COVER, BrandKit("EP. 12", "NA RÉGUA PODCAST", "#F59E0B", "#111116")),
        t("tpl_cheguei", "Cheguei", "9:16", AspectRatioPreset.PORTRAIT_9_16, 3000, TemplateFamily.INTRO, BrandKit("CHEGUEI", "NOVA ERA", "#00EAFF", "#7C3AED")),
        t("tpl_black_friday", "Black Friday", "9:16", AspectRatioPreset.PORTRAIT_9_16, 4000, TemplateFamily.SALE, BrandKit("70% OFF", "BLACK FRIDAY", "#F97316", "#0B0B0E")),
        t("tpl_igreja", "Igreja", "9:16", AspectRatioPreset.PORTRAIT_9_16, 5000, TemplateFamily.COVER, BrandKit("DOMINGO 19H", "CULTO DA FAMÍLIA", "#EAB308", "#1C1917")),
        t("tpl_futebol", "Futebol", "9:16", AspectRatioPreset.PORTRAIT_9_16, 4000, TemplateFamily.INTRO, BrandKit("É GOOOOL", "BRASILEIRÃO", "#22C55E", "#14532D")),
        t("tpl_acai", "Açaí", "9:16", AspectRatioPreset.PORTRAIT_9_16, 4000, TemplateFamily.SALE, BrandKit("R$ 12", "AÇAÍ DA FEIRA", "#A855F7", "#3B0764")),
        t("tpl_loja", "Loja de rua", "9:16", AspectRatioPreset.PORTRAIT_9_16, 4000, TemplateFamily.LOWER_THIRD, BrandKit("PROMOÇÃO", "LOJA DO CENTRO", "#F43F5E", "#1F2937")),
        t("tpl_dj", "DJ Set", "9:16", AspectRatioPreset.PORTRAIT_9_16, 5000, TemplateFamily.INTRO, BrandKit("DJ LUNA", "OPEN TO CLOSE", "#00EAFF", "#DB2777")),
        t("tpl_faculdade", "Faculdade", "9:16", AspectRatioPreset.PORTRAIT_9_16, 5000, TemplateFamily.EVENT, BrandKit("AULA MAGNA", "AUDITÓRIO 3 · 19H", "#38BDF8", "#0F172A")),
        t("tpl_prefeitura", "Prefeitura", "9:16", AspectRatioPreset.PORTRAIT_9_16, 4000, TemplateFamily.LOWER_THIRD, BrandKit("COMUNICADO", "PREFEITURA MUNICIPAL", "#2563EB", "#0B0B0E")),
        t("tpl_yt_bumper", "Bumper YouTube", "16:9", AspectRatioPreset.LANDSCAPE_16_9, 3000, TemplateFamily.BUMPER, BrandKit("MAXMOTION", "ESTÚDIO")),
        t("tpl_end_slate", "End Slate", "16:9", AspectRatioPreset.LANDSCAPE_16_9, 4000, TemplateFamily.END_SLATE, BrandKit("OBRIGADO", "@SEUCANAL")),
        t("tpl_feed_post", "Post Feed", "1:1", AspectRatioPreset.SQUARE_1_1, 4000, TemplateFamily.COVER, BrandKit("NOVIDADE", "NO FEED AGORA", "#00EAFF", "#8B5CF6")),
        t("tpl_thumb", "Thumbnail cinético", "16:9", AspectRatioPreset.LANDSCAPE_16_9, 3000, TemplateFamily.INTRO, BrandKit("NÃO ACREDITE", "NO TÍTULO", "#F43F5E", "#111116")),
        t("tpl_podcast_yt", "Podcast 16:9", "16:9", AspectRatioPreset.LANDSCAPE_16_9, 5000, TemplateFamily.COVER, BrandKit("PAPO RETO", "EPISÓDIO 08", "#F59E0B", "#1C1917")),
        t("tpl_lt_wide", "Lower-third 16:9", "16:9", AspectRatioPreset.LANDSCAPE_16_9, 4000, TemplateFamily.LOWER_THIRD, BrandKit("ENTREVISTA", "CONVIDADO ESPECIAL", "#00EAFF", "#111116")),
        t("tpl_quote", "Quote", "1:1", AspectRatioPreset.SQUARE_1_1, 5000, TemplateFamily.QUOTE, BrandKit("“O RITMO MANDA.”", "— DIREÇÃO MAXMOTION")),
        t("tpl_sale_sq", "Oferta 1:1", "1:1", AspectRatioPreset.SQUARE_1_1, 4000, TemplateFamily.SALE, BrandKit("R$ 49", "SÓ HOJE", "#FB7185", "#111116")),
        t("tpl_event_wide", "Evento 16:9", "16:9", AspectRatioPreset.LANDSCAPE_16_9, 5000, TemplateFamily.EVENT, BrandKit("FESTIVAL NORTE", "SÁB 20H · DOCA", "#A3E635", "#14532D")),
        t("tpl_logo_sting", "Logo Sting", "16:9", AspectRatioPreset.LANDSCAPE_16_9, 2500, TemplateFamily.BUMPER, BrandKit("MM", "MAXMOTION")),
        t("tpl_subscribe", "Se inscreva", "16:9", AspectRatioPreset.LANDSCAPE_16_9, 3500, TemplateFamily.END_SLATE, BrandKit("INSCREVA-SE", "ATIVA O SINO", "#EF4444", "#0B0B0E")),
        t("tpl_carousel", "Capa carrossel", "1:1", AspectRatioPreset.SQUARE_1_1, 4000, TemplateFamily.COVER, BrandKit("1 / 5", "DESLIZE →", "#00EAFF", "#1E1B4B"))
    )

    fun info(id: String): TemplateInfo? = catalog.firstOrNull { it.id == id }

    fun build(id: String): ProjectDocument {
        val info = info(id) ?: catalog.first()
        val doc = when (info.family) {
            TemplateFamily.INTRO -> intro(info)
            TemplateFamily.LOWER_THIRD -> lowerThird(info)
            TemplateFamily.COVER -> cover(info)
            TemplateFamily.SALE -> sale(info)
            TemplateFamily.QUOTE -> quote(info)
            TemplateFamily.BUMPER -> bumper(info)
            TemplateFamily.END_SLATE -> endSlate(info)
            TemplateFamily.EVENT -> event(info)
        }
        return applyBrand(doc, info.brand).copy(
            id = info.id + "_" + System.currentTimeMillis(),
            name = info.name
        )
    }

    fun applyBrand(document: ProjectDocument, brand: BrandKit): ProjectDocument {
        val comps = document.compositions.map { comp ->
            comp.copy(layers = comp.layers.map { layer -> brandLayer(layer, brand) })
        }
        return document.copy(brand = brand, compositions = comps)
    }

    private fun t(
        id: String, name: String, category: String, aspect: AspectRatioPreset,
        durationMs: Long, family: TemplateFamily, brand: BrandKit
    ) = TemplateInfo(id, name, category, aspect, durationMs, family, brand)

    private fun brandLayer(layer: LayerNode, brand: BrandKit): LayerNode = when (layer.essentialRole) {
        EssentialRole.TITLE -> layer.copy(
            textSpec = (layer.textSpec ?: TextSpec()).copy(
                content = brand.title,
                fontFamilyKey = brand.fontFamilyKey,
                colorHex = "#F4F4F7",
                strokeColorHex = brand.primaryHex
            )
        )
        EssentialRole.SUBTITLE -> layer.copy(
            textSpec = (layer.textSpec ?: TextSpec()).copy(
                content = brand.subtitle,
                colorHex = brand.primaryHex,
                fontFamilyKey = "jetbrains_mono"
            )
        )
        EssentialRole.LOGO -> layer.copy(
            vectorSpec = (layer.vectorSpec ?: VectorSpec()).copy(
                fillColorHex = brand.primaryHex,
                fillColorEndHex = brand.secondaryHex,
                strokeColorHex = "#F4F4F7"
            )
        )
        EssentialRole.ACCENT -> layer.copy(
            vectorSpec = (layer.vectorSpec ?: VectorSpec()).copy(
                strokeColorHex = brand.primaryHex,
                fillColorHex = brand.secondaryHex,
                fillColorEndHex = brand.primaryHex
            )
        )
        EssentialRole.BACKGROUND -> layer.copy(
            vectorSpec = (layer.vectorSpec ?: VectorSpec()).copy(
                fillColorHex = brand.secondaryHex,
                fillColorEndHex = "#0B0B0E"
            )
        )
        EssentialRole.NONE -> layer
    }

    private fun baseComp(info: TemplateInfo, layers: List<LayerNode>) = CompositionNode(
        id = "comp_main",
        name = info.name,
        width = info.aspect.width,
        height = info.aspect.height,
        fps = 60,
        durationMs = info.durationMs,
        backgroundColorHex = "#0B0B0E",
        layers = layers
    )

    private fun doc(info: TemplateInfo, layers: List<LayerNode>) = ProjectDocument(
        id = info.id,
        name = info.name,
        brand = info.brand,
        activeCompositionId = "comp_main",
        compositions = listOf(baseComp(info, layers))
    )

    private fun popIn(id: String, from: Long, to: Long) = AnimatableProperty(
        baseValue = 1f,
        keyframes = listOf(
            Keyframe(id = "${id}_0", timeMs = from, value = 0f, easing = EasingType.EASE_OUT),
            Keyframe(id = "${id}_1", timeMs = to, value = 1f, easing = EasingType.EASE_OUT)
        )
    )

    private fun scaleSlam(id: String) = AnimatableProperty(
        baseValue = 1f,
        keyframes = listOf(
            Keyframe(id = "${id}_s0", timeMs = 0L, value = 0.2f, easing = EasingType.BEZIER, bezier = BezierCurve(0.16f, 1f, 0.3f, 1f)),
            Keyframe(id = "${id}_s1", timeMs = 700L, value = 1.08f, easing = EasingType.BEZIER, bezier = BezierCurve(0.34f, 1.56f, 0.64f, 1f)),
            Keyframe(id = "${id}_s2", timeMs = 1100L, value = 1f, easing = EasingType.EASE_OUT)
        )
    )

    private fun intro(info: TemplateInfo): ProjectDocument {
        val dur = info.durationMs
        val ring = LayerNode(
            name = "Anel", type = LayerType.VECTOR, inMs = 0, outMs = dur, zIndex = 1,
            blendMode = BlendMode.ADD, essentialRole = EssentialRole.ACCENT,
            transform = TransformSpec(rotZ = AnimatableProperty(0f, expression = "time * 24"), opacity = AnimatableProperty(0.85f)),
            vectorSpec = VectorSpec(preset = VectorShapePreset.CIRCLE, width = 560f, height = 560f, fillType = VectorFillType.NONE, strokeWidth = AnimatableProperty(12f), dashLength = 22f)
        )
        val logo = LayerNode(
            name = "Prisma", type = LayerType.VECTOR, inMs = 0, outMs = dur, zIndex = 2, is3D = true,
            essentialRole = EssentialRole.LOGO,
            transform = TransformSpec(scaleX = scaleSlam("logo"), scaleY = scaleSlam("logoy"), rotZ = AnimatableProperty(45f)),
            vectorSpec = VectorSpec(preset = VectorShapePreset.RECTANGLE, width = 280f, height = 280f, cornerRadius = 48f, fillType = VectorFillType.LINEAR_GRADIENT, strokeWidth = AnimatableProperty(8f))
        )
        val title = titleLayer(info, posY = 280f, size = if (info.aspect.height > info.aspect.width) 96f else 72f, dur = dur)
        val sub = subtitleLayer(info, posY = 390f, dur = dur)
        return doc(info, listOf(ring, logo, title, sub))
    }

    private fun lowerThird(info: TemplateInfo): ProjectDocument {
        val dur = info.durationMs
        val y = if (info.aspect.height >= info.aspect.width) 620f else 360f
        val bar = LayerNode(
            name = "Barra", type = LayerType.VECTOR, inMs = 0, outMs = dur, zIndex = 1,
            essentialRole = EssentialRole.ACCENT,
            transform = TransformSpec(posY = AnimatableProperty(y), opacity = popIn("bar", 0, 400)),
            vectorSpec = VectorSpec(preset = VectorShapePreset.RECTANGLE, width = info.aspect.width * 0.82f, height = 140f, cornerRadius = 18f, fillType = VectorFillType.LINEAR_GRADIENT)
        )
        val title = titleLayer(info, posY = y - 18f, size = 48f, dur = dur, inMs = 120)
        val sub = subtitleLayer(info, posY = y + 36f, dur = dur, inMs = 280)
        return doc(info, listOf(bar, title, sub))
    }

    private fun cover(info: TemplateInfo): ProjectDocument {
        val dur = info.durationMs
        val bg = LayerNode(
            name = "Fundo", type = LayerType.VECTOR, inMs = 0, outMs = dur, zIndex = 0,
            essentialRole = EssentialRole.BACKGROUND,
            vectorSpec = VectorSpec(preset = VectorShapePreset.RECTANGLE, width = info.aspect.width.toFloat(), height = info.aspect.height.toFloat(), fillType = VectorFillType.LINEAR_GRADIENT)
        )
        val line = LayerNode(
            name = "Linha", type = LayerType.VECTOR, inMs = 0, outMs = dur, zIndex = 1,
            essentialRole = EssentialRole.ACCENT,
            transform = TransformSpec(posY = AnimatableProperty(80f), opacity = popIn("ln", 200, 700)),
            vectorSpec = VectorSpec(preset = VectorShapePreset.RECTANGLE, width = 280f, height = 8f, cornerRadius = 8f)
        )
        val title = titleLayer(info, posY = -40f, size = 88f, dur = dur)
        val sub = subtitleLayer(info, posY = 80f, dur = dur, inMs = 400)
        return doc(info, listOf(bg, line, title, sub))
    }

    private fun sale(info: TemplateInfo): ProjectDocument {
        val dur = info.durationMs
        val stamp = LayerNode(
            name = "Selo", type = LayerType.VECTOR, inMs = 0, outMs = dur, zIndex = 1, is3D = true,
            essentialRole = EssentialRole.LOGO,
            transform = TransformSpec(scaleX = scaleSlam("st"), scaleY = scaleSlam("sty"), rotZ = AnimatableProperty(-8f)),
            vectorSpec = VectorSpec(preset = VectorShapePreset.STAR, width = 420f, height = 420f, fillType = VectorFillType.LINEAR_GRADIENT, strokeWidth = AnimatableProperty(6f))
        )
        val title = titleLayer(info, posY = 20f, size = 110f, dur = dur)
        val sub = subtitleLayer(info, posY = 160f, dur = dur, inMs = 350)
        return doc(info, listOf(stamp, title, sub))
    }

    private fun quote(info: TemplateInfo): ProjectDocument {
        val dur = info.durationMs
        val title = titleLayer(info, posY = -40f, size = 52f, dur = dur)
        val sub = subtitleLayer(info, posY = 140f, dur = dur, inMs = 500)
        val accent = LayerNode(
            name = "Aspas", type = LayerType.VECTOR, inMs = 0, outMs = dur, zIndex = 0,
            essentialRole = EssentialRole.ACCENT,
            transform = TransformSpec(posY = AnimatableProperty(-220f), opacity = AnimatableProperty(0.35f)),
            vectorSpec = VectorSpec(preset = VectorShapePreset.CIRCLE, width = 220f, height = 220f, fillType = VectorFillType.NONE, strokeWidth = AnimatableProperty(10f))
        )
        return doc(info, listOf(accent, title, sub))
    }

    private fun bumper(info: TemplateInfo): ProjectDocument {
        val dur = info.durationMs
        val logo = LayerNode(
            name = "Marca", type = LayerType.VECTOR, inMs = 0, outMs = dur, zIndex = 1,
            essentialRole = EssentialRole.LOGO,
            transform = TransformSpec(scaleX = scaleSlam("bm"), scaleY = scaleSlam("bmy")),
            vectorSpec = VectorSpec(preset = VectorShapePreset.RECTANGLE, width = 220f, height = 220f, cornerRadius = 40f, fillType = VectorFillType.LINEAR_GRADIENT, strokeWidth = AnimatableProperty(8f))
        )
        val title = titleLayer(info, posY = 200f, size = 56f, dur = dur, inMs = 200)
        return doc(info, listOf(logo, title))
    }

    private fun endSlate(info: TemplateInfo): ProjectDocument {
        val dur = info.durationMs
        val logo = LayerNode(
            name = "Logo", type = LayerType.VECTOR, inMs = 0, outMs = dur, zIndex = 1,
            essentialRole = EssentialRole.LOGO,
            transform = TransformSpec(posY = AnimatableProperty(-80f), opacity = popIn("es", 0, 500)),
            vectorSpec = VectorSpec(preset = VectorShapePreset.CIRCLE, width = 180f, height = 180f, fillType = VectorFillType.LINEAR_GRADIENT)
        )
        val title = titleLayer(info, posY = 80f, size = 64f, dur = dur)
        val sub = subtitleLayer(info, posY = 170f, dur = dur, inMs = 400)
        return doc(info, listOf(logo, title, sub))
    }

    private fun event(info: TemplateInfo): ProjectDocument {
        val dur = info.durationMs
        val bar = LayerNode(
            name = "Faixa", type = LayerType.VECTOR, inMs = 0, outMs = dur, zIndex = 0,
            essentialRole = EssentialRole.BACKGROUND,
            transform = TransformSpec(opacity = popIn("ev", 0, 360)),
            vectorSpec = VectorSpec(preset = VectorShapePreset.RECTANGLE, width = info.aspect.width.toFloat(), height = 280f, fillType = VectorFillType.LINEAR_GRADIENT)
        )
        val title = titleLayer(info, posY = -20f, size = 72f, dur = dur)
        val sub = subtitleLayer(info, posY = 80f, dur = dur, inMs = 280)
        return doc(info, listOf(bar, title, sub))
    }

    private fun titleLayer(info: TemplateInfo, posY: Float, size: Float, dur: Long, inMs: Long = 160L) = LayerNode(
        name = "Título", type = LayerType.TEXT, inMs = inMs, outMs = dur, zIndex = 8,
        essentialRole = EssentialRole.TITLE,
        transform = TransformSpec(posY = AnimatableProperty(posY), opacity = popIn("ttl", inMs, inMs + 500)),
        textSpec = TextSpec(
            content = info.brand.title,
            fontFamilyKey = info.brand.fontFamilyKey,
            fontSize = AnimatableProperty(size),
            colorHex = "#F4F4F7",
            strokeColorHex = info.brand.primaryHex,
            strokeWidth = 2f,
            tracking = AnimatableProperty(6f)
        )
    )

    private fun subtitleLayer(info: TemplateInfo, posY: Float, dur: Long, inMs: Long = 400L) = LayerNode(
        name = "Subtítulo", type = LayerType.TEXT, inMs = inMs, outMs = dur, zIndex = 9,
        essentialRole = EssentialRole.SUBTITLE,
        transform = TransformSpec(posY = AnimatableProperty(posY), opacity = popIn("sub", inMs, inMs + 450)),
        textSpec = TextSpec(
            content = info.brand.subtitle,
            fontFamilyKey = "jetbrains_mono",
            fontSize = AnimatableProperty(28f),
            colorHex = info.brand.primaryHex,
            tracking = AnimatableProperty(4f)
        )
    )
}
