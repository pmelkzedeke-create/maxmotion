package com.example.model

data class TypePresetInfo(
    val id: String,
    val name: String,
    val category: String
)

/**
 * 30 looks de type cinético. Um tap escreve keyframes no animador de faixa
 * (range / offset / scale / rotação / scramble) sem destruir o texto.
 */
object TypePresets {

    val catalog: List<TypePresetInfo> = listOf(
        t("pop_palavra", "Pop palavra", "Entrada"),
        t("typewriter", "Máquina", "Entrada"),
        t("scramble_in", "Scramble", "Entrada"),
        t("split_flip", "Split flip", "Entrada"),
        t("blur_in", "Blur-in", "Entrada"),
        t("tracking_explode", "Tracking explode", "Entrada"),
        t("slide_up_chars", "Sobe letra", "Entrada"),
        t("slide_down_chars", "Desce letra", "Entrada"),
        t("drop_in", "Queda", "Entrada"),
        t("bounce_words", "Bounce", "Entrada"),
        t("fade_chars", "Fade letra", "Entrada"),
        t("zoom_line", "Zoom linha", "Entrada"),
        t("slam_title", "Slam", "Entrada"),
        t("kinetic_left", "Cinética", "Entrada"),
        t("type_fast", "Type rápido", "Entrada"),
        t("wave_in", "Onda", "Entrada"),
        t("glitch_pop", "Glitch", "Entrada"),
        t("neon_flicker", "Flicker", "Entrada"),
        t("punch_word", "Soco", "Entrada"),
        t("cascade", "Cascata", "Entrada"),
        t("center_burst", "Burst", "Entrada"),
        t("whisper", "Sussurro", "Entrada"),
        t("stamp", "Carimbo", "Entrada"),
        t("elastic", "Elástico", "Entrada"),
        t("random_pop", "Random", "Entrada"),
        t("ticker", "Ticker", "Loop"),
        t("rise_rotate", "Gira sobe", "Entrada"),
        t("lower_third_type", "Lower-third", "Entrada"),
        t("outro_fade", "Some", "Saída"),
        t("explode_out", "Explode", "Saída")
    )

    fun info(id: String): TypePresetInfo? = catalog.firstOrNull { it.id == id }

    fun apply(layer: LayerNode, presetId: String): LayerNode {
        val ts = layer.textSpec ?: return layer
        val dur = (layer.outMs - layer.inMs).coerceAtLeast(900L)
        val nextTs = when (presetId) {
            "pop_palavra" -> enter(ts, TextRangeMode.WORD, 700L, offsetY = 40f, scale = 1.42f, settle = true, outOp = 0f)
            "typewriter" -> enter(ts, TextRangeMode.CHARACTER, 1300L, outOp = 0f)
            "scramble_in" -> enter(ts, TextRangeMode.CHARACTER, 1100L, scramble = 1f, outOp = 1f)
            "split_flip" -> enter(ts, TextRangeMode.CHARACTER, 820L, rotation = 88f, outOp = 0f)
            "blur_in" -> enter(ts, TextRangeMode.CHARACTER, 920L, scale = 1.8f, settle = true, outOp = 0f)
            "tracking_explode" -> ts.copy(
                rangeMode = TextRangeMode.CHARACTER,
                rangeStart = AnimatableProperty(0f),
                rangeEnd = AnimatableProperty(1f),
                rangeOffset = AnimatableProperty(0f),
                animOffsetY = AnimatableProperty(0f),
                animScale = AnimatableProperty(1f),
                animRotation = AnimatableProperty(0f),
                animOpacity = AnimatableProperty(1f),
                wigglyAmount = AnimatableProperty(0f),
                scrambleAmount = 0f,
                tracking = p(4f, k(0, 78f), k(900, 4f, EasingType.BEZIER, snappy))
            )
            "slide_up_chars" -> enter(ts, TextRangeMode.CHARACTER, 800L, offsetY = 72f, outOp = 0f)
            "slide_down_chars" -> enter(ts, TextRangeMode.CHARACTER, 800L, offsetY = -72f, outOp = 0f)
            "drop_in" -> enter(ts, TextRangeMode.WORD, 760L, offsetY = -130f, scale = 0.72f, settle = true, outOp = 0f)
            "bounce_words" -> enter(ts, TextRangeMode.WORD, 640L, offsetY = 28f, scale = 1.55f, settle = true, outOp = 0f)
            "fade_chars" -> enter(ts, TextRangeMode.CHARACTER, 1000L, outOp = 0f)
            "zoom_line" -> enter(ts, TextRangeMode.LINE, 720L, scale = 1.7f, settle = true, outOp = 0f)
            "slam_title" -> enter(ts, TextRangeMode.LINE, 480L, scale = 1.2f, settle = true, outOp = 0f)
            "kinetic_left" -> ts.copy(
                rangeMode = TextRangeMode.CHARACTER,
                rangeStart = AnimatableProperty(0f),
                rangeEnd = p(1f, k(0, 0f), k(860, 1f, EasingType.BEZIER, snappy)),
                rangeOffset = AnimatableProperty(0f),
                animOffsetY = AnimatableProperty(0f),
                animScale = AnimatableProperty(1f),
                animRotation = AnimatableProperty(-12f),
                animOpacity = AnimatableProperty(0f),
                wigglyAmount = AnimatableProperty(0f),
                scrambleAmount = 0f,
                tracking = p(4f, k(0, -18f), k(860, 6f, EasingType.BEZIER, snappy))
            )
            "type_fast" -> enter(ts, TextRangeMode.CHARACTER, 420L, outOp = 0f)
            "wave_in" -> enter(ts, TextRangeMode.CHARACTER, 980L, offsetY = 46f, wiggly = 10f, outOp = 0f)
            "glitch_pop" -> enter(ts, TextRangeMode.CHARACTER, 640L, scale = 1.25f, settle = true, wiggly = 18f, scramble = 0.7f, outOp = 1f)
            "neon_flicker" -> enter(ts, TextRangeMode.LINE, 200L, outOp = 0f)
            "punch_word" -> enter(ts, TextRangeMode.WORD, 520L, scale = 1.85f, settle = true, offsetY = 16f, outOp = 0f)
            "cascade" -> enter(ts, TextRangeMode.CHARACTER, 1600L, offsetY = 90f, outOp = 0f)
            "center_burst" -> ts.copy(
                rangeMode = TextRangeMode.CHARACTER,
                rangeStart = AnimatableProperty(0f),
                rangeEnd = AnimatableProperty(1f),
                rangeOffset = AnimatableProperty(0f),
                animOffsetY = AnimatableProperty(0f),
                animScale = p(1f, k(0, 2.4f, EasingType.BEZIER, overshoot), k(780, 1f, EasingType.BEZIER, snappy)),
                animRotation = AnimatableProperty(0f),
                animOpacity = AnimatableProperty(1f),
                wigglyAmount = AnimatableProperty(0f),
                scrambleAmount = 0f,
                tracking = p(4f, k(0, 42f), k(780, 3f, EasingType.BEZIER, snappy))
            )
            "whisper" -> enter(ts, TextRangeMode.CHARACTER, 1400L, outOp = 0f).copy(
                tracking = p(4f, k(0, 22f), k(1400, 8f))
            )
            "stamp" -> enter(ts, TextRangeMode.LINE, 380L, scale = 1.6f, settle = true, rotation = -10f, outOp = 0f)
            "elastic" -> enter(ts, TextRangeMode.CHARACTER, 900L, scale = 1.5f, settle = true, offsetY = 24f, outOp = 0f)
            "random_pop" -> enter(ts, TextRangeMode.CHARACTER, 880L, scale = 1.3f, settle = true, wiggly = 14f, outOp = 0f)
            "ticker" -> ts.copy(
                rangeMode = TextRangeMode.CHARACTER,
                rangeStart = AnimatableProperty(0f),
                rangeEnd = AnimatableProperty(0.28f),
                rangeOffset = p(0f, k(0, 0f), k(dur.coerceAtMost(2400L), 1f, EasingType.LINEAR)),
                animOffsetY = AnimatableProperty(0f),
                animScale = AnimatableProperty(1f),
                animRotation = AnimatableProperty(0f),
                animOpacity = AnimatableProperty(0.15f),
                wigglyAmount = AnimatableProperty(0f),
                scrambleAmount = 0f,
                tracking = AnimatableProperty(ts.tracking.baseValue)
            )
            "rise_rotate" -> enter(ts, TextRangeMode.CHARACTER, 860L, offsetY = 60f, rotation = 35f, outOp = 0f)
            "lower_third_type" -> enter(ts, TextRangeMode.WORD, 1100L, offsetY = 28f, outOp = 0f)
            "outro_fade" -> ts.copy(
                rangeMode = TextRangeMode.CHARACTER,
                rangeStart = p(0f, k((dur - 800L).coerceAtLeast(0L), 0f), k(dur, 1f)),
                rangeEnd = AnimatableProperty(1f),
                rangeOffset = AnimatableProperty(0f),
                animOffsetY = AnimatableProperty(-24f),
                animScale = AnimatableProperty(1f),
                animRotation = AnimatableProperty(0f),
                animOpacity = AnimatableProperty(0f),
                wigglyAmount = AnimatableProperty(0f),
                scrambleAmount = 0f,
                tracking = AnimatableProperty(ts.tracking.baseValue)
            )
            "explode_out" -> ts.copy(
                rangeMode = TextRangeMode.CHARACTER,
                rangeStart = AnimatableProperty(0f),
                rangeEnd = AnimatableProperty(1f),
                rangeOffset = AnimatableProperty(0f),
                animOffsetY = AnimatableProperty(0f),
                animScale = p(1f, k((dur - 700L).coerceAtLeast(0L), 1f), k(dur, 1.35f)),
                animRotation = AnimatableProperty(0f),
                animOpacity = AnimatableProperty(1f),
                wigglyAmount = AnimatableProperty(8f),
                scrambleAmount = 0f,
                tracking = p(4f, k((dur - 700L).coerceAtLeast(0L), 4f), k(dur, 86f))
            )
            else -> return layer
        }

        var transform = layer.transform.copy(
            scaleX = still(layer.transform.scaleX, 1f),
            scaleY = still(layer.transform.scaleY, 1f),
            rotZ = still(layer.transform.rotZ, 0f),
            opacity = still(layer.transform.opacity, 1f)
        )
        when (presetId) {
            "slam_title" -> transform = transform.copy(
                scaleX = p(1f, k(0, 0.35f, EasingType.BEZIER, overshoot), k(520, 1f)),
                scaleY = p(1f, k(0, 0.35f, EasingType.BEZIER, overshoot), k(520, 1f)),
                rotZ = p(0f, k(0, -8f), k(480, 0f)),
                opacity = p(1f, k(0, 0f), k(180, 1f))
            )
            "stamp" -> transform = transform.copy(
                scaleX = p(1f, k(0, 1.55f, EasingType.BEZIER, overshoot), k(400, 1f)),
                scaleY = p(1f, k(0, 1.55f, EasingType.BEZIER, overshoot), k(400, 1f)),
                rotZ = p(0f, k(0, -12f), k(360, 0f))
            )
            "neon_flicker" -> transform = transform.copy(
                opacity = AnimatableProperty(
                    1f,
                    listOf(
                        k(0, 0f, EasingType.HOLD),
                        k(80, 1f, EasingType.HOLD),
                        k(140, 0.15f, EasingType.HOLD),
                        k(200, 1f, EasingType.HOLD),
                        k(260, 0.4f, EasingType.HOLD),
                        k(340, 1f)
                    )
                )
            )
            "center_burst" -> transform = transform.copy(
                opacity = p(1f, k(0, 0f), k(160, 1f))
            )
            "explode_out" -> transform = transform.copy(
                opacity = p(1f, k((dur - 280L).coerceAtLeast(0L), 1f), k(dur, 0f))
            )
            "tracking_explode" -> transform = transform.copy(
                opacity = p(1f, k(0, 0f), k(280, 1f))
            )
        }
        return layer.copy(textSpec = nextTs, transform = transform)
    }

    private fun t(id: String, name: String, category: String) = TypePresetInfo(id, name, category)

    private val snappy = BezierCurve(0.16f, 1f, 0.3f, 1f)
    private val overshoot = BezierCurve(0.34f, 1.56f, 0.64f, 1f)

    private fun k(
        timeMs: Long,
        value: Float,
        easing: EasingType = EasingType.EASE_OUT,
        bezier: BezierCurve = snappy
    ) = Keyframe(timeMs = timeMs, value = value, easing = easing, bezier = bezier)

    private fun p(base: Float, vararg frames: Keyframe) =
        AnimatableProperty(baseValue = base, keyframes = frames.toList())

    private fun enter(
        ts: TextSpec,
        mode: TextRangeMode,
        sweepMs: Long,
        offsetY: Float = 0f,
        scale: Float = 1f,
        settle: Boolean = false,
        rotation: Float = 0f,
        outOp: Float = 0f,
        wiggly: Float = 0f,
        scramble: Float = 0f
    ): TextSpec {
        val rangeEnd = p(1f, k(0, 0f), k(sweepMs, 1f, EasingType.BEZIER, snappy))
        val animScale = if (settle && scale != 1f) {
            p(1f, k(0, scale, EasingType.BEZIER, overshoot), k(sweepMs + 180L, 1f, EasingType.BEZIER, snappy))
        } else {
            AnimatableProperty(scale)
        }
        return ts.copy(
            rangeMode = mode,
            rangeStart = AnimatableProperty(0f),
            rangeEnd = rangeEnd,
            rangeOffset = AnimatableProperty(0f),
            animOffsetY = AnimatableProperty(offsetY),
            animScale = animScale,
            animRotation = AnimatableProperty(rotation),
            animOpacity = AnimatableProperty(outOp),
            wigglyAmount = AnimatableProperty(wiggly),
            scrambleAmount = scramble,
            tracking = AnimatableProperty(ts.tracking.baseValue)
        )
    }

    private fun still(prop: AnimatableProperty, fallback: Float): AnimatableProperty {
        return if (prop.keyframes.isEmpty() && prop.expression.isNullOrBlank()) prop
        else AnimatableProperty(fallback)
    }
}
