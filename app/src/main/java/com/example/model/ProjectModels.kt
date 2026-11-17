package com.example.model

import com.squareup.moshi.JsonClass
import java.util.UUID

@JsonClass(generateAdapter = true)
data class ProjectDocument(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Novo Projeto",
    val version: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val activeCompositionId: String = "comp_main",
    val assets: List<AssetItem> = emptyList(),
    val compositions: List<CompositionNode> = listOf(CompositionNode())
) {
    val activeComposition: CompositionNode
        get() = compositions.firstOrNull { it.id == activeCompositionId } ?: compositions.firstOrNull() ?: CompositionNode()
}

enum class AssetType {
    VIDEO, IMAGE, AUDIO, FONT
}

@JsonClass(generateAdapter = true)
data class AssetItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: AssetType,
    val uri: String,
    val durationMs: Long = 5000L,
    val width: Int = 1080,
    val height: Int = 1920,
    val waveform: List<Float> = emptyList(),
    val detectedBeatsMs: List<Long> = emptyList()
)

@JsonClass(generateAdapter = true)
data class CompositionNode(
    val id: String = "comp_main",
    val name: String = "Composição Principal",
    val width: Int = 1080,
    val height: Int = 1920,
    val fps: Int = 60,
    val durationMs: Long = 8000L,
    val backgroundColorHex: String = "#0B0B0E",
    val activeCameraId: String? = null,
    val beatMarkersMs: List<Long> = emptyList(),
    val layers: List<LayerNode> = emptyList()
)

enum class LayerType(val labelPt: String) {
    VIDEO("Vídeo"),
    IMAGE("Imagem"),
    VECTOR("Vetor"),
    TEXT("Texto"),
    NULL("Objeto Nulo"),
    CAMERA_3D("Câmera 3D"),
    LIGHT_3D("Luz 3D"),
    ADJUSTMENT("Ajuste"),
    PRE_COMP("Pré-Comp"),
    AUDIO("Áudio")
}

enum class BlendMode(val labelPt: String, val shaderIndex: Int) {
    NORMAL("Normal", 0),
    MULTIPLY("Multiplicar", 1),
    SCREEN("Tela (Screen)", 2),
    ADD("Adicionar (Linear Dodge)", 3),
    OVERLAY("Sobrepor (Overlay)", 4),
    DARKEN("Escurecer", 5),
    LIGHTEN("Clarear", 6),
    COLOR_DODGE("Subexposição de Cor", 7),
    COLOR_BURN("Superexposição de Cor", 8),
    HARD_LIGHT("Luz Direta", 9),
    SOFT_LIGHT("Luz Indireta", 10),
    DIFFERENCE("Diferença", 11),
    EXCLUSION("Exclusão", 12),
    SUBTRACT("Subtrair", 13),
    DIVIDE("Dividir", 14),
    HUE("Matiz", 15),
    SATURATION("Saturação", 16),
    COLOR("Cor", 17),
    LUMINOSITY("Luminosidade", 18),
    LINEAR_DODGE("Linear Dodge", 3),
    LINEAR_BURN("Linear Burn", 19),
    VIVID_LIGHT("Luz Brilhante", 20),
    LINEAR_LIGHT("Luz Linear", 21),
    PIN_LIGHT("Luz do Pino", 22),
    HARD_MIX("Mistura Sólida", 23),
    DARKER_COLOR("Cor Mais Escura", 24),
    LIGHTER_COLOR("Cor Mais Clara", 25),
    STENCIL_ALPHA("Alfa Estêncil", 26)
}

enum class EasingType(val labelPt: String) {
    LINEAR("Linear"),
    HOLD("Congelar (Hold)"),
    EASE_IN("Ease In"),
    EASE_OUT("Ease Out"),
    EASE_IN_OUT("Ease In-Out"),
    BEZIER("Bézier Contínuo")
}

@JsonClass(generateAdapter = true)
data class BezierCurve(
    val x1: Float = 0.42f,
    val y1: Float = 0.0f,
    val x2: Float = 0.58f,
    val y2: Float = 1.0f
)

@JsonClass(generateAdapter = true)
data class Keyframe(
    val id: String = UUID.randomUUID().toString(),
    val timeMs: Long,
    val value: Float,
    val easing: EasingType = EasingType.EASE_IN_OUT,
    val bezier: BezierCurve = BezierCurve()
)

@JsonClass(generateAdapter = true)
data class AnimatableProperty(
    val baseValue: Float = 0f,
    val keyframes: List<Keyframe> = emptyList(),
    val expression: String? = null
) {
    val isAnimated: Boolean get() = keyframes.isNotEmpty() || !expression.isNullOrBlank()
}

@JsonClass(generateAdapter = true)
data class TransformSpec(
    val anchorX: AnimatableProperty = AnimatableProperty(0f),
    val anchorY: AnimatableProperty = AnimatableProperty(0f),
    val posX: AnimatableProperty = AnimatableProperty(0f),
    val posY: AnimatableProperty = AnimatableProperty(0f),
    val posZ: AnimatableProperty = AnimatableProperty(0f),
    val scaleX: AnimatableProperty = AnimatableProperty(1f),
    val scaleY: AnimatableProperty = AnimatableProperty(1f),
    val rotX: AnimatableProperty = AnimatableProperty(0f),
    val rotY: AnimatableProperty = AnimatableProperty(0f),
    val rotZ: AnimatableProperty = AnimatableProperty(0f),
    val opacity: AnimatableProperty = AnimatableProperty(1f),
    val skewX: AnimatableProperty = AnimatableProperty(0f)
)

enum class VectorShapePreset(val labelPt: String) {
    RECTANGLE("Retângulo"),
    CIRCLE("Círculo"),
    STAR("Estrela"),
    POLYGON("Polígono"),
    FREEHAND("Caneta Bézier")
}

enum class VectorFillType(val labelPt: String) {
    SOLID("Sólido"),
    LINEAR_GRADIENT("Gradiente Linear"),
    RADIAL_GRADIENT("Gradiente Radial"),
    NONE("Sem Preenchimento")
}

@JsonClass(generateAdapter = true)
data class VectorPoint(
    val x: Float,
    val y: Float,
    val inHandleX: Float = 0f,
    val inHandleY: Float = 0f,
    val outHandleX: Float = 0f,
    val outHandleY: Float = 0f
)

@JsonClass(generateAdapter = true)
data class VectorSpec(
    val preset: VectorShapePreset = VectorShapePreset.RECTANGLE,
    val width: Float = 360f,
    val height: Float = 360f,
    val cornerRadius: Float = 32f,
    val starPoints: Int = 5,
    val points: List<VectorPoint> = emptyList(),
    val closed: Boolean = true,
    val fillType: VectorFillType = VectorFillType.LINEAR_GRADIENT,
    val fillColorHex: String = "#00E5FF",
    val fillColorEndHex: String = "#7C3AED",
    val strokeColorHex: String = "#FFFFFF",
    val strokeWidth: AnimatableProperty = AnimatableProperty(8f),
    val strokeCapRound: Boolean = true,
    val dashLength: Float = 0f,
    val trimStart: AnimatableProperty = AnimatableProperty(0f),
    val trimEnd: AnimatableProperty = AnimatableProperty(1f),
    val trimOffset: AnimatableProperty = AnimatableProperty(0f)
)

enum class TextRangeMode(val labelPt: String) {
    CHARACTER("Caractere"),
    WORD("Palavra"),
    LINE("Linha")
}

@JsonClass(generateAdapter = true)
data class TextSpec(
    val content: String = "MAXMOTION",
    val fontFamilyKey: String = "space_grotesk",
    val customFontUri: String? = null,
    val fontSize: AnimatableProperty = AnimatableProperty(96f),
    val colorHex: String = "#FFFFFF",
    val strokeColorHex: String = "#00E5FF",
    val strokeWidth: Float = 0f,
    val tracking: AnimatableProperty = AnimatableProperty(4f),
    val rangeMode: TextRangeMode = TextRangeMode.CHARACTER,
    val rangeStart: AnimatableProperty = AnimatableProperty(0f),
    val rangeEnd: AnimatableProperty = AnimatableProperty(1f),
    val rangeOffset: AnimatableProperty = AnimatableProperty(0f),
    val animOffsetY: AnimatableProperty = AnimatableProperty(0f),
    val animScale: AnimatableProperty = AnimatableProperty(1f),
    val animRotation: AnimatableProperty = AnimatableProperty(0f),
    val animOpacity: AnimatableProperty = AnimatableProperty(1f),
    val wigglyAmount: AnimatableProperty = AnimatableProperty(0f)
)

enum class MaskMode(val labelPt: String) {
    ADD("Adicionar"),
    SUBTRACT("Subtrair"),
    INTERSECT("Interseção")
}

enum class MaskShape(val labelPt: String) {
    RECTANGLE("Retângulo"),
    CIRCLE("Círculo"),
    STAR("Estrela"),
    BEZIER("Livre")
}

@JsonClass(generateAdapter = true)
data class MaskSpec(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Máscara 1",
    val shape: MaskShape = MaskShape.RECTANGLE,
    val mode: MaskMode = MaskMode.ADD,
    val inverted: Boolean = false,
    val centerX: AnimatableProperty = AnimatableProperty(0f),
    val centerY: AnimatableProperty = AnimatableProperty(0f),
    val width: AnimatableProperty = AnimatableProperty(0.75f),
    val height: AnimatableProperty = AnimatableProperty(0.75f),
    val feather: AnimatableProperty = AnimatableProperty(0.05f),
    val expansion: AnimatableProperty = AnimatableProperty(0f),
    val opacity: AnimatableProperty = AnimatableProperty(1f),
    val points: List<VectorPoint> = emptyList()
)

enum class EffectType(val labelPt: String, val categoryPt: String) {
    MOTION_TILE("Motion Tile", "Distorção"),
    S_SHAKE("S_Shake (Oscilador)", "Distorção"),
    WAVE_WARP("Onda (Wave Warp)", "Distorção"),
    TURBULENT_DISPLACE("Deslocamento Turbulento", "Distorção"),
    TWIRL("Redemoinho (Twirl)", "Distorção"),
    SPHERIZE("Esferizar (Spherize)", "Distorção"),
    OPTICS_COMPENSATION("Compensação Óptica", "Distorção"),
    DEEP_GLOW("Deep Glow", "Luz & Desfoque"),
    MOTION_BLUR("Motion Blur Direcional", "Luz & Desfoque"),
    GAUSSIAN_BLUR("Desfoque Gaussiano", "Luz & Desfoque"),
    ZOOM_BLUR("Zoom Blur", "Luz & Desfoque"),
    RADIAL_BLUR("Desfoque Radial", "Luz & Desfoque"),
    LIGHT_RAYS("Raios de Luz", "Luz & Desfoque"),
    CHROMATIC_ABERRATION("Aberração Cromática", "Luz & Desfoque"),
    VHS_SCANLINES("VHS / Scanlines CRT", "Estilização"),
    HALFTONE("Meio-Tom (Halftone)", "Estilização"),
    PIXELATE("Pixelizar", "Estilização"),
    POSTERIZE_TIME("Posterize Time (Stop-Motion)", "Estilização"),
    EDGE_DETECT("Detectar Bordas", "Estilização"),
    VIGNETTE("Vinheta Cinematográfica", "Estilização"),
    CHROMA_KEY("Ultra Chroma Key", "Recorte & Cor"),
    COLOR_GRADING("Color Grading (Lift/Gamma/Gain)", "Recorte & Cor")
}

@JsonClass(generateAdapter = true)
data class EffectNode(
    val id: String = UUID.randomUUID().toString(),
    val type: EffectType,
    val enabled: Boolean = true,
    val params: Map<String, AnimatableProperty> = defaultEffectParams(type)
)

fun defaultEffectParams(type: EffectType): Map<String, AnimatableProperty> = when (type) {
    EffectType.MOTION_TILE -> mapOf(
        "tileWidth" to AnimatableProperty(1.0f),
        "tileHeight" to AnimatableProperty(1.0f),
        "mirrorEdges" to AnimatableProperty(1.0f),
        "phase" to AnimatableProperty(0.0f)
    )
    EffectType.S_SHAKE -> mapOf(
        "amplitude" to AnimatableProperty(0.04f),
        "frequency" to AnimatableProperty(6.0f),
        "rotationAmp" to AnimatableProperty(3.5f),
        "seed" to AnimatableProperty(42f)
    )
    EffectType.WAVE_WARP -> mapOf(
        "waveHeight" to AnimatableProperty(0.03f),
        "waveWidth" to AnimatableProperty(12.0f),
        "speed" to AnimatableProperty(2.5f)
    )
    EffectType.TURBULENT_DISPLACE -> mapOf(
        "amount" to AnimatableProperty(0.04f),
        "size" to AnimatableProperty(6.0f),
        "evolution" to AnimatableProperty(1.0f)
    )
    EffectType.TWIRL -> mapOf(
        "angle" to AnimatableProperty(1.8f),
        "radius" to AnimatableProperty(0.6f)
    )
    EffectType.SPHERIZE -> mapOf(
        "strength" to AnimatableProperty(0.5f),
        "radius" to AnimatableProperty(0.75f)
    )
    EffectType.OPTICS_COMPENSATION -> mapOf(
        "fov" to AnimatableProperty(0.35f)
    )
    EffectType.DEEP_GLOW -> mapOf(
        "intensity" to AnimatableProperty(0.85f),
        "radius" to AnimatableProperty(0.55f),
        "threshold" to AnimatableProperty(0.35f)
    )
    EffectType.MOTION_BLUR -> mapOf(
        "shutterAngle" to AnimatableProperty(180f),
        "angleDeg" to AnimatableProperty(0f),
        "strength" to AnimatableProperty(0.35f)
    )
    EffectType.GAUSSIAN_BLUR -> mapOf(
        "radius" to AnimatableProperty(0.3f)
    )
    EffectType.ZOOM_BLUR -> mapOf(
        "amount" to AnimatableProperty(0.35f)
    )
    EffectType.RADIAL_BLUR -> mapOf(
        "angle" to AnimatableProperty(0.25f)
    )
    EffectType.LIGHT_RAYS -> mapOf(
        "intensity" to AnimatableProperty(0.6f),
        "decay" to AnimatableProperty(0.88f)
    )
    EffectType.CHROMATIC_ABERRATION -> mapOf(
        "intensity" to AnimatableProperty(0.025f),
        "angleDeg" to AnimatableProperty(15f)
    )
    EffectType.VHS_SCANLINES -> mapOf(
        "scanDensity" to AnimatableProperty(180f),
        "jitter" to AnimatableProperty(0.25f),
        "chromaShift" to AnimatableProperty(0.4f)
    )
    EffectType.HALFTONE -> mapOf(
        "dotScale" to AnimatableProperty(45f),
        "contrast" to AnimatableProperty(0.75f)
    )
    EffectType.PIXELATE -> mapOf(
        "pixelSize" to AnimatableProperty(28f)
    )
    EffectType.POSTERIZE_TIME -> mapOf(
        "targetFps" to AnimatableProperty(12f)
    )
    EffectType.EDGE_DETECT -> mapOf(
        "threshold" to AnimatableProperty(0.35f),
        "glowMix" to AnimatableProperty(0.85f)
    )
    EffectType.VIGNETTE -> mapOf(
        "amount" to AnimatableProperty(0.55f),
        "roundness" to AnimatableProperty(0.65f)
    )
    EffectType.CHROMA_KEY -> mapOf(
        "hueTarget" to AnimatableProperty(0.33f),
        "tolerance" to AnimatableProperty(0.28f),
        "spillSuppression" to AnimatableProperty(0.65f)
    )
    EffectType.COLOR_GRADING -> mapOf(
        "exposure" to AnimatableProperty(0.0f),
        "contrast" to AnimatableProperty(1.1f),
        "saturation" to AnimatableProperty(1.05f),
        "temperature" to AnimatableProperty(0.0f),
        "lift" to AnimatableProperty(0.0f),
        "gamma" to AnimatableProperty(1.0f),
        "gain" to AnimatableProperty(1.0f)
    )
}

@JsonClass(generateAdapter = true)
data class CameraSpec(
    val fov: AnimatableProperty = AnimatableProperty(48f),
    val pointOfInterestX: AnimatableProperty = AnimatableProperty(0f),
    val pointOfInterestY: AnimatableProperty = AnimatableProperty(0f),
    val pointOfInterestZ: AnimatableProperty = AnimatableProperty(0f),
    val dofBlur: AnimatableProperty = AnimatableProperty(0f),
    val focusDistance: AnimatableProperty = AnimatableProperty(0f),
    val enablePerspective: Boolean = true,
    val orbitAzimuth: Float = 0f,
    val orbitElevation: Float = 0f,
    val orbitDistance: Float = 1100f
)

enum class LightType(val labelPt: String) {
    AMBIENT("Ambiente"),
    POINT("Pontual"),
    DIRECTIONAL("Direcional")
}

@JsonClass(generateAdapter = true)
data class LightSpec(
    val lightType: LightType = LightType.POINT,
    val colorHex: String = "#00E5FF",
    val intensity: AnimatableProperty = AnimatableProperty(1.25f),
    val radius: AnimatableProperty = AnimatableProperty(900f),
    val castShadow: Boolean = true
)

@JsonClass(generateAdapter = true)
data class CurvePoint(
    val x: Float,
    val y: Float
)

@JsonClass(generateAdapter = true)
data class ColorGradingSpec(
    val lutPresetId: String? = null,
    val lutCubeUri: String? = null,
    val lutName: String? = null,
    val lutIntensity: Float = 0.85f,
    val curveMaster: List<CurvePoint> = listOf(CurvePoint(0f, 0f), CurvePoint(0.5f, 0.5f), CurvePoint(1f, 1f)),
    val curveRed: List<CurvePoint> = listOf(CurvePoint(0f, 0f), CurvePoint(0.5f, 0.5f), CurvePoint(1f, 1f)),
    val curveGreen: List<CurvePoint> = listOf(CurvePoint(0f, 0f), CurvePoint(0.5f, 0.5f), CurvePoint(1f, 1f)),
    val curveBlue: List<CurvePoint> = listOf(CurvePoint(0f, 0f), CurvePoint(0.5f, 0.5f), CurvePoint(1f, 1f))
)

@JsonClass(generateAdapter = true)
data class AudioSpec(
    val volume: AnimatableProperty = AnimatableProperty(1.0f),
    val fadeInMs: Long = 200L,
    val fadeOutMs: Long = 400L,
    val eqBass: Float = 1.0f,
    val eqMid: Float = 1.0f,
    val eqTreble: Float = 1.0f,
    val visualizerEnabled: Boolean = false
)

@JsonClass(generateAdapter = true)
data class LayerNode(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: LayerType,
    val assetId: String? = null,
    val preCompId: String? = null,
    val inMs: Long = 0L,
    val outMs: Long = 8000L,
    val startOffsetMs: Long = 0L,
    val zIndex: Int = 0,
    val parentId: String? = null,
    val is3D: Boolean = false,
    val visible: Boolean = true,
    val locked: Boolean = false,
    val solo: Boolean = false,
    val collapseTransform: Boolean = true,
    val blendMode: BlendMode = BlendMode.NORMAL,
    val transform: TransformSpec = TransformSpec(),
    val vectorSpec: VectorSpec? = null,
    val textSpec: TextSpec? = null,
    val cameraSpec: CameraSpec? = null,
    val lightSpec: LightSpec? = null,
    val audioSpec: AudioSpec? = null,
    val colorGrading: ColorGradingSpec? = null,
    val aiCutoutMaskUri: String? = null,
    val speedMultiplier: Float = 1.0f,
    val speedRamp: AnimatableProperty? = null,
    val masks: List<MaskSpec> = emptyList(),
    val effects: List<EffectNode> = emptyList()
)

enum class PreviewResolution(val label: String, val scale: Float) {
    FULL("Full", 1.0f),
    HALF("1/2", 0.5f),
    QUARTER("1/4", 0.25f)
}

enum class AspectRatioPreset(val label: String, val width: Int, val height: Int) {
    PORTRAIT_9_16("9:16 Reels", 1080, 1920),
    LANDSCAPE_16_9("16:9 YouTube", 1920, 1080),
    SQUARE_1_1("1:1 Feed", 1080, 1080),
    PORTRAIT_4_5("4:5 Social", 1080, 1350),
    CINEMA_21_9("2.35:1 Cinema", 1920, 816)
}
