package com.example.model

object DemoProjectFactory {

    fun createDemoProject(): ProjectDocument {
        val nullRigId = "layer_null_rig"
        val bgShapeId = "layer_bg_grid_shape"
        val prismVectorId = "layer_prism_vector"
        val titleTextId = "layer_kinetic_title"
        val subtitleTextId = "layer_subtitle"
        val adjustId = "layer_adj_glow"

        val nullRig = LayerNode(
            id = nullRigId,
            name = "Nulo Controle Mestre",
            type = LayerType.NULL,
            inMs = 0L,
            outMs = 8000L,
            zIndex = 5,
            transform = TransformSpec(
                scaleX = AnimatableProperty(
                    baseValue = 1f,
                    keyframes = listOf(
                        Keyframe(id = "k_null_s0", timeMs = 0L, value = 0.85f, easing = EasingType.BEZIER, bezier = BezierCurve(0.16f, 1f, 0.3f, 1f)),
                        Keyframe(id = "k_null_s1", timeMs = 1400L, value = 1.0f, easing = EasingType.BEZIER, bezier = BezierCurve(0.22f, 1f, 0.36f, 1f))
                    )
                ),
                rotZ = AnimatableProperty(
                    baseValue = 0f,
                    keyframes = listOf(
                        Keyframe(id = "k_null_r0", timeMs = 0L, value = -6f, easing = EasingType.EASE_OUT),
                        Keyframe(id = "k_null_r1", timeMs = 1800L, value = 0f, easing = EasingType.EASE_IN_OUT)
                    )
                )
            )
        )

        val bgOrb = LayerNode(
            id = bgShapeId,
            name = "Anel Orbital Vetorial",
            type = LayerType.VECTOR,
            inMs = 0L,
            outMs = 8000L,
            zIndex = 1,
            parentId = nullRigId,
            blendMode = BlendMode.ADD,
            essentialRole = EssentialRole.ACCENT,
            transform = TransformSpec(
                posX = AnimatableProperty(0f),
                posY = AnimatableProperty(-60f),
                rotZ = AnimatableProperty(
                    baseValue = 0f,
                    expression = "time * 28"
                ),
                opacity = AnimatableProperty(0.88f)
            ),
            vectorSpec = VectorSpec(
                preset = VectorShapePreset.CIRCLE,
                width = 640f,
                height = 640f,
                fillType = VectorFillType.NONE,
                strokeColorHex = "#7C3AED",
                strokeWidth = AnimatableProperty(14f),
                dashLength = 26f,
                trimStart = AnimatableProperty(0f),
                trimEnd = AnimatableProperty(
                    baseValue = 1f,
                    keyframes = listOf(
                        Keyframe(id = "k_ring_t0", timeMs = 0L, value = 0.1f, easing = EasingType.BEZIER, bezier = BezierCurve(0.22f, 1f, 0.36f, 1f)),
                        Keyframe(id = "k_ring_t1", timeMs = 1600L, value = 1.0f, easing = EasingType.EASE_OUT)
                    )
                )
            ),
            effects = listOf(
                EffectNode(
                    type = EffectType.DEEP_GLOW,
                    params = mapOf(
                        "intensity" to AnimatableProperty(0.9f),
                        "radius" to AnimatableProperty(0.6f),
                        "threshold" to AnimatableProperty(0.25f)
                    )
                )
            )
        )

        val prismVector = LayerNode(
            id = prismVectorId,
            name = "Prisma Cinético",
            type = LayerType.VECTOR,
            inMs = 0L,
            outMs = 8000L,
            zIndex = 2,
            parentId = nullRigId,
            is3D = true,
            essentialRole = EssentialRole.LOGO,
            transform = TransformSpec(
                posX = AnimatableProperty(0f),
                posY = AnimatableProperty(-60f),
                scaleX = AnimatableProperty(
                    baseValue = 1f,
                    keyframes = listOf(
                        Keyframe(id = "k_prism_s0", timeMs = 0L, value = 0.25f, easing = EasingType.BEZIER, bezier = BezierCurve(0.16f, 1f, 0.3f, 1f)),
                        Keyframe(id = "k_prism_s1", timeMs = 950L, value = 1.08f, easing = EasingType.BEZIER, bezier = BezierCurve(0.34f, 1.56f, 0.64f, 1f)),
                        Keyframe(id = "k_prism_s2", timeMs = 1500L, value = 1.0f, easing = EasingType.EASE_OUT)
                    )
                ),
                scaleY = AnimatableProperty(
                    baseValue = 1f,
                    keyframes = listOf(
                        Keyframe(id = "k_prism_sy0", timeMs = 0L, value = 0.25f, easing = EasingType.BEZIER, bezier = BezierCurve(0.16f, 1f, 0.3f, 1f)),
                        Keyframe(id = "k_prism_sy1", timeMs = 950L, value = 1.08f, easing = EasingType.BEZIER, bezier = BezierCurve(0.34f, 1.56f, 0.64f, 1f)),
                        Keyframe(id = "k_prism_sy2", timeMs = 1500L, value = 1.0f, easing = EasingType.EASE_OUT)
                    )
                ),
                rotZ = AnimatableProperty(
                    baseValue = 45f,
                    keyframes = listOf(
                        Keyframe(id = "k_prism_r0", timeMs = 0L, value = -45f, easing = EasingType.BEZIER, bezier = BezierCurve(0.22f, 1f, 0.36f, 1f)),
                        Keyframe(id = "k_prism_r1", timeMs = 1800L, value = 45f, easing = EasingType.EASE_IN_OUT)
                    )
                ),
                rotY = AnimatableProperty(
                    baseValue = 0f,
                    keyframes = listOf(
                        Keyframe(id = "k_prism_ry0", timeMs = 1800L, value = 0f),
                        Keyframe(id = "k_prism_ry1", timeMs = 4200L, value = 24f),
                        Keyframe(id = "k_prism_ry2", timeMs = 6500L, value = -18f)
                    )
                )
            ),
            vectorSpec = VectorSpec(
                preset = VectorShapePreset.RECTANGLE,
                width = 360f,
                height = 360f,
                cornerRadius = 56f,
                fillType = VectorFillType.LINEAR_GRADIENT,
                fillColorHex = "#00E5FF",
                fillColorEndHex = "#7C3AED",
                strokeColorHex = "#F4F4F7",
                strokeWidth = AnimatableProperty(10f),
                trimStart = AnimatableProperty(0f),
                trimEnd = AnimatableProperty(
                    baseValue = 1f,
                    keyframes = listOf(
                        Keyframe(id = "k_trim0", timeMs = 0L, value = 0f, easing = EasingType.EASE_OUT),
                        Keyframe(id = "k_trim1", timeMs = 1100L, value = 1f, easing = EasingType.EASE_IN_OUT)
                    )
                )
            )
        )

        val kineticTitle = LayerNode(
            id = titleTextId,
            name = "Título MaxMotion",
            type = LayerType.TEXT,
            inMs = 200L,
            outMs = 8000L,
            zIndex = 3,
            parentId = nullRigId,
            essentialRole = EssentialRole.TITLE,
            transform = TransformSpec(
                posX = AnimatableProperty(0f),
                posY = AnimatableProperty(280f),
                opacity = AnimatableProperty(
                    baseValue = 1f,
                    keyframes = listOf(
                        Keyframe(id = "k_txt_o0", timeMs = 200L, value = 0f),
                        Keyframe(id = "k_txt_o1", timeMs = 750L, value = 1f)
                    )
                )
            ),
            textSpec = TextSpec(
                content = "MAXMOTION",
                fontFamilyKey = "space_grotesk",
                fontSize = AnimatableProperty(110f),
                colorHex = "#F4F4F7",
                strokeColorHex = "#00E5FF",
                strokeWidth = 2f,
                tracking = AnimatableProperty(12f),
                rangeMode = TextRangeMode.CHARACTER,
                rangeStart = AnimatableProperty(0f),
                rangeEnd = AnimatableProperty(
                    baseValue = 1f,
                    keyframes = listOf(
                        Keyframe(id = "k_rng0", timeMs = 200L, value = 0.05f, easing = EasingType.BEZIER, bezier = BezierCurve(0.22f, 1f, 0.36f, 1f)),
                        Keyframe(id = "k_rng1", timeMs = 1500L, value = 1f, easing = EasingType.EASE_OUT)
                    )
                ),
                animOffsetY = AnimatableProperty(-48f),
                animScale = AnimatableProperty(1.18f),
                animOpacity = AnimatableProperty(0.15f),
                wigglyAmount = AnimatableProperty(4f)
            )
        )

        val subtitle = LayerNode(
            id = subtitleTextId,
            name = "Subtítulo Studio",
            type = LayerType.TEXT,
            inMs = 700L,
            outMs = 8000L,
            zIndex = 4,
            essentialRole = EssentialRole.SUBTITLE,
            transform = TransformSpec(
                posX = AnimatableProperty(0f),
                posY = AnimatableProperty(390f),
                opacity = AnimatableProperty(
                    baseValue = 0.85f,
                    keyframes = listOf(
                        Keyframe(id = "k_sub_o0", timeMs = 700L, value = 0f),
                        Keyframe(id = "k_sub_o1", timeMs = 1350L, value = 0.9f)
                    )
                )
            ),
            textSpec = TextSpec(
                content = "MOTION GRAPHICS • SHADERS • CURVAS",
                fontFamilyKey = "jetbrains_mono",
                fontSize = AnimatableProperty(34f),
                colorHex = "#00E5FF",
                tracking = AnimatableProperty(6f)
            )
        )

        val adjustmentLayer = LayerNode(
            id = adjustId,
            name = "Ajuste Cinemático",
            type = LayerType.ADJUSTMENT,
            inMs = 0L,
            outMs = 8000L,
            zIndex = 6,
            effects = listOf(
                EffectNode(
                    type = EffectType.CHROMATIC_ABERRATION,
                    params = mapOf(
                        "intensity" to AnimatableProperty(
                            baseValue = 0.012f,
                            keyframes = listOf(
                                Keyframe(id = "k_ca0", timeMs = 800L, value = 0.035f, easing = EasingType.EASE_OUT),
                                Keyframe(id = "k_ca1", timeMs = 1400L, value = 0.008f, easing = EasingType.EASE_IN_OUT)
                            )
                        ),
                        "angleDeg" to AnimatableProperty(18f)
                    )
                ),
                EffectNode(
                    type = EffectType.VIGNETTE,
                    params = mapOf(
                        "amount" to AnimatableProperty(0.45f),
                        "roundness" to AnimatableProperty(0.7f)
                    )
                )
            )
        )

        val mainComp = CompositionNode(
            id = "comp_main",
            name = "Composição Principal",
            width = 1080,
            height = 1920,
            fps = 60,
            durationMs = 8000L,
            backgroundColorHex = "#0B0B0E",
            beatMarkersMs = listOf(500L, 1000L, 1500L, 2000L, 2500L, 3000L, 3500L, 4000L, 4500L, 5000L, 5500L, 6000L),
            layers = listOf(bgOrb, prismVector, kineticTitle, subtitle, nullRig, adjustmentLayer)
        )

        return ProjectDocument(
            id = "demo_neon_kinetic",
            name = "Intro Cinética Neon",
            compositions = listOf(mainComp),
            activeCompositionId = mainComp.id,
            brand = BrandKit("MAXMOTION", "MOTION GRAPHICS • SHADERS • CURVAS")
        )
    }

    fun createBlankProject(
        name: String,
        aspect: AspectRatioPreset,
        fps: Int,
        durationMs: Long
    ): ProjectDocument {
        val comp = CompositionNode(
            id = "comp_main",
            name = "Composição Principal",
            width = aspect.width,
            height = aspect.height,
            fps = fps,
            durationMs = durationMs,
            backgroundColorHex = "#0B0B0E",
            layers = listOf(
                LayerNode(
                    name = "Forma Inicial",
                    type = LayerType.VECTOR,
                    inMs = 0L,
                    outMs = durationMs,
                    zIndex = 0,
                    vectorSpec = VectorSpec(
                        preset = VectorShapePreset.RECTANGLE,
                        width = (aspect.width * 0.36f),
                        height = (aspect.width * 0.36f),
                        cornerRadius = 48f
                    )
                )
            )
        )
        return ProjectDocument(
            name = name.ifBlank { "Projeto MaxMotion" },
            activeCompositionId = comp.id,
            compositions = listOf(comp)
        )
    }
}
