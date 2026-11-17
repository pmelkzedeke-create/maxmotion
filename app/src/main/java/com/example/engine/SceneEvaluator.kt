package com.example.engine

import com.example.model.AnimatableProperty
import com.example.model.AssetItem
import com.example.model.AssetType
import com.example.model.BlendMode
import com.example.model.CompositionNode
import com.example.model.EffectNode
import com.example.model.EffectType
import com.example.model.LayerNode
import com.example.model.LayerType
import com.example.model.MaskSpec
import com.example.model.ProjectDocument
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

data class EvaluatedTransform(
    val anchorX: Float,
    val anchorY: Float,
    val posX: Float,
    val posY: Float,
    val posZ: Float,
    val scaleX: Float,
    val scaleY: Float,
    val rotX: Float,
    val rotY: Float,
    val rotZ: Float,
    val opacity: Float,
    val skewX: Float
)

data class EvaluatedMask(
    val spec: MaskSpec,
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float,
    val feather: Float,
    val expansion: Float,
    val opacity: Float
)

data class EvaluatedEffect(
    val type: EffectType,
    val params: Map<String, Float>
)

data class EvaluatedLayer(
    val layer: LayerNode,
    val localTimeMs: Long,
    val transform: EvaluatedTransform,
    val worldMatrix: FloatArray,
    val worldOpacity: Float,
    val worldCenterX: Float,
    val worldCenterY: Float,
    val worldRotationZ: Float,
    val worldScaleX: Float,
    val worldScaleY: Float,
    val evaluatedEffects: List<EvaluatedEffect>,
    val evaluatedMasks: List<EvaluatedMask>,
    val trimStart: Float,
    val trimEnd: Float,
    val trimOffset: Float,
    val strokeWidth: Float,
    val textRangeStart: Float,
    val textRangeEnd: Float,
    val textRangeOffset: Float,
    val textAnimOffsetY: Float,
    val textAnimScale: Float,
    val textAnimOpacity: Float,
    val textWiggly: Float,
    val textFontSize: Float,
    val textTracking: Float
)

data class EvaluatedLight(
    val type: com.example.model.LightType,
    val colorHex: String,
    val worldX: Float,
    val worldY: Float,
    val worldZ: Float,
    val intensity: Float,
    val radius: Float,
    val castShadow: Boolean
)

data class EvaluatedScene(
    val composition: CompositionNode,
    val timeMs: Long,
    val audioAmplitude: Float,
    val cameraMatrix: FloatArray,
    val layers: List<EvaluatedLayer>,
    val lights: List<EvaluatedLight> = emptyList(),
    val dofFocusDistance: Float = 0f,
    val dofBlur: Float = 0f,
    val cameraPerspective: Boolean = false,
    val cameraFov: Float = 48f
)

object SceneEvaluator {

    fun evaluateProperty(
        prop: AnimatableProperty,
        timeMs: Long,
        comp: CompositionNode,
        audioAmplitude: Float
    ): Float {
        val kfValue = CurveInterpolator.evaluateKeyframes(prop, timeMs)
        if (prop.expression.isNullOrBlank()) return kfValue
        val ctx = ExpressionContext(
            timeMs = timeMs,
            fps = comp.fps,
            durationMs = comp.durationMs,
            baseKeyframeValue = kfValue,
            currentProperty = prop,
            composition = comp,
            audioAmplitude = audioAmplitude,
            audioBass = (audioAmplitude * 1.25f).coerceIn(0f, 1f)
        )
        return ExpressionEvaluator.evaluate(prop.expression, ctx).value
    }

    fun evaluateScene(
        doc: ProjectDocument,
        comp: CompositionNode,
        timeMs: Long
    ): EvaluatedScene {
        val audioAmp = computeAudioAmplitude(doc.assets, comp, timeMs)
        val hasSolo = comp.layers.any { it.solo && it.visible && it.type != LayerType.AUDIO }

        val activeLayers = comp.layers
            .filter { layer ->
                layer.visible &&
                    layer.type != LayerType.AUDIO &&
                    timeMs in layer.inMs..layer.outMs &&
                    (!hasSolo || layer.solo || layer.type == LayerType.CAMERA_3D || layer.type == LayerType.LIGHT_3D)
            }
            .sortedBy { it.zIndex }

        val byId = comp.layers.associateBy { it.id }
        val localCache = HashMap<String, EvaluatedTransform>()

        fun computeLocalTime(layer: LayerNode): Long {
            val elapsed = (timeMs - layer.inMs).coerceAtLeast(0L)
            return SpeedRampEvaluator.integrateRemappedTimeMs(layer.speedRamp, elapsed, layer.speedMultiplier) + layer.startOffsetMs
        }

        fun evalTransformFor(layer: LayerNode): EvaluatedTransform {
            return localCache.getOrPut(layer.id) {
                val localT = computeLocalTime(layer)
                val t = layer.transform
                EvaluatedTransform(
                    anchorX = evaluateProperty(t.anchorX, localT, comp, audioAmp),
                    anchorY = evaluateProperty(t.anchorY, localT, comp, audioAmp),
                    posX = evaluateProperty(t.posX, localT, comp, audioAmp),
                    posY = evaluateProperty(t.posY, localT, comp, audioAmp),
                    posZ = evaluateProperty(t.posZ, localT, comp, audioAmp),
                    scaleX = evaluateProperty(t.scaleX, localT, comp, audioAmp),
                    scaleY = evaluateProperty(t.scaleY, localT, comp, audioAmp),
                    rotX = evaluateProperty(t.rotX, localT, comp, audioAmp),
                    rotY = evaluateProperty(t.rotY, localT, comp, audioAmp),
                    rotZ = evaluateProperty(t.rotZ, localT, comp, audioAmp),
                    opacity = evaluateProperty(t.opacity, localT, comp, audioAmp).coerceIn(0f, 1f),
                    skewX = evaluateProperty(t.skewX, localT, comp, audioAmp)
                )
            }
        }

        // Camera 3D transform matrix (inverse view transform) + Perspective & DOF
        val cameraMatrix = Mat4.identity()
        val camLayer = comp.layers.firstOrNull {
            it.id == comp.activeCameraId || (it.type == LayerType.CAMERA_3D && it.visible && timeMs in it.inMs..it.outMs)
        }
        var camPerspective = false
        var camFov = 48f
        var camDofBlur = 0f
        var camFocusDist = 0f

        if (camLayer != null && camLayer.visible && timeMs in camLayer.inMs..camLayer.outMs) {
            val camT = evalTransformFor(camLayer)
            val cSpec = camLayer.cameraSpec
            camFov = cSpec?.let { evaluateProperty(it.fov, timeMs, comp, audioAmp) }?.coerceIn(15f, 120f) ?: 48f
            camPerspective = cSpec?.enablePerspective ?: true
            camDofBlur = cSpec?.let { evaluateProperty(it.dofBlur, timeMs, comp, audioAmp) }?.coerceIn(0f, 2f) ?: 0f
            camFocusDist = cSpec?.let { evaluateProperty(it.focusDistance, timeMs, comp, audioAmp) } ?: 0f

            if (camPerspective && cSpec != null) {
                val poiX = evaluateProperty(cSpec.pointOfInterestX, timeMs, comp, audioAmp)
                val poiY = evaluateProperty(cSpec.pointOfInterestY, timeMs, comp, audioAmp)
                val poiZ = evaluateProperty(cSpec.pointOfInterestZ, timeMs, comp, audioAmp)
                Mat4.translate(cameraMatrix, -camT.posX, -camT.posY, camT.posZ)
                Mat4.rotateZ(cameraMatrix, -camT.rotZ)
                Mat4.rotateX(cameraMatrix, cSpec.orbitElevation)
                Mat4.rotateY(cameraMatrix, cSpec.orbitAzimuth)
                Mat4.translate(cameraMatrix, -poiX, -poiY, -poiZ)
            } else {
                val zoomFactor = 48f / camFov
                Mat4.scale(cameraMatrix, zoomFactor, zoomFactor, 1f)
                Mat4.rotateZ(cameraMatrix, -camT.rotZ)
                Mat4.translate(cameraMatrix, -camT.posX, -camT.posY, 0f)
            }
        }

        val lightsList = ArrayList<EvaluatedLight>()

        val evaluatedList = activeLayers.map { layer ->
            val localT = computeLocalTime(layer)
            val tr = evalTransformFor(layer)

            // Resolve parenting chain (protection against circular parenting)
            val chain = mutableListOf<LayerNode>()
            var cur: LayerNode? = layer
            val visited = HashSet<String>()
            while (cur != null && visited.add(cur.id)) {
                chain.add(cur)
                cur = cur.parentId?.let { byId[it] }
            }
            chain.reverse()

            var worldMat = Mat4.identity()
            var accumScaleX = 1f
            var accumScaleY = 1f
            var accumRotZ = 0f
            var accumOpacity = 1f

            for (node in chain) {
                val nt = evalTransformFor(node)
                val use3D = node.is3D || node.type == LayerType.LIGHT_3D || node.type == LayerType.CAMERA_3D
                var step = Mat4.identity()
                Mat4.translate(step, nt.posX, nt.posY, if (use3D) nt.posZ else 0f)
                if (use3D) {
                    Mat4.rotateX(step, nt.rotX)
                    Mat4.rotateY(step, nt.rotY)
                }
                Mat4.rotateZ(step, nt.rotZ)
                Mat4.scale(step, nt.scaleX, nt.scaleY, 1f)
                if (abs(nt.skewX) > 0.001f) {
                    val skewMat = Mat4.identity()
                    skewMat[4] = tan(Math.toRadians(nt.skewX.toDouble())).toFloat()
                    step = Mat4.multiply(step, skewMat)
                }
                Mat4.translate(step, -nt.anchorX, -nt.anchorY, 0f)

                worldMat = Mat4.multiply(worldMat, step)

                accumScaleX *= nt.scaleX
                accumScaleY *= nt.scaleY
                accumRotZ += nt.rotZ
                if (node.id == layer.id) {
                    accumOpacity *= nt.opacity
                }
            }

            // World origin of the layer (center before anchor offset)
            val mappedOrigin = Mat4.multiplyVec4(worldMat, tr.anchorX, tr.anchorY, 0f, 1f)

            if (layer.type == LayerType.LIGHT_3D && layer.lightSpec != null && lightsList.size < 4) {
                val ls = layer.lightSpec
                lightsList.add(
                    EvaluatedLight(
                        type = ls.lightType,
                        colorHex = ls.colorHex,
                        worldX = mappedOrigin[0],
                        worldY = mappedOrigin[1],
                        worldZ = mappedOrigin[2],
                        intensity = evaluateProperty(ls.intensity, localT, comp, audioAmp).coerceIn(0f, 4f),
                        radius = evaluateProperty(ls.radius, localT, comp, audioAmp).coerceAtLeast(100f),
                        castShadow = ls.castShadow
                    )
                )
            }

            // Evaluate Effects (honoring Posterize Time if present)
            val posterize = layer.effects.firstOrNull { it.enabled && it.type == EffectType.POSTERIZE_TIME }
            val effectTimeMs = if (posterize != null) {
                val targetFps = evaluateProperty(
                    posterize.params["targetFps"] ?: AnimatableProperty(12f),
                    localT, comp, audioAmp
                ).coerceIn(1f, 60f)
                val stepMs = (1000f / targetFps).toLong().coerceAtLeast(16L)
                (localT / stepMs) * stepMs
            } else {
                localT
            }

            val effects = layer.effects.filter { it.enabled }.map { eff ->
                EvaluatedEffect(
                    type = eff.type,
                    params = eff.params.mapValues { (_, p) ->
                        evaluateProperty(p, effectTimeMs, comp, audioAmp)
                    }
                )
            }

            val masks = layer.masks.map { m ->
                EvaluatedMask(
                    spec = m,
                    centerX = evaluateProperty(m.centerX, localT, comp, audioAmp),
                    centerY = evaluateProperty(m.centerY, localT, comp, audioAmp),
                    width = evaluateProperty(m.width, localT, comp, audioAmp).coerceIn(0.02f, 2f),
                    height = evaluateProperty(m.height, localT, comp, audioAmp).coerceIn(0.02f, 2f),
                    feather = evaluateProperty(m.feather, localT, comp, audioAmp).coerceIn(0f, 0.5f),
                    expansion = evaluateProperty(m.expansion, localT, comp, audioAmp).coerceIn(-0.4f, 0.4f),
                    opacity = evaluateProperty(m.opacity, localT, comp, audioAmp).coerceIn(0f, 1f)
                )
            }

            val vs = layer.vectorSpec
            val ts = layer.textSpec

            EvaluatedLayer(
                layer = layer,
                localTimeMs = effectTimeMs,
                transform = tr,
                worldMatrix = worldMat,
                worldOpacity = accumOpacity,
                worldCenterX = mappedOrigin[0],
                worldCenterY = mappedOrigin[1],
                worldRotationZ = accumRotZ,
                worldScaleX = accumScaleX,
                worldScaleY = accumScaleY,
                evaluatedEffects = effects,
                evaluatedMasks = masks,
                trimStart = vs?.let { evaluateProperty(it.trimStart, effectTimeMs, comp, audioAmp).coerceIn(0f, 1f) } ?: 0f,
                trimEnd = vs?.let { evaluateProperty(it.trimEnd, effectTimeMs, comp, audioAmp).coerceIn(0f, 1f) } ?: 1f,
                trimOffset = vs?.let { evaluateProperty(it.trimOffset, effectTimeMs, comp, audioAmp) } ?: 0f,
                strokeWidth = vs?.let { evaluateProperty(it.strokeWidth, effectTimeMs, comp, audioAmp).coerceAtLeast(0f) } ?: 4f,
                textRangeStart = ts?.let { evaluateProperty(it.rangeStart, effectTimeMs, comp, audioAmp).coerceIn(0f, 1f) } ?: 0f,
                textRangeEnd = ts?.let { evaluateProperty(it.rangeEnd, effectTimeMs, comp, audioAmp).coerceIn(0f, 1f) } ?: 1f,
                textRangeOffset = ts?.let { evaluateProperty(it.rangeOffset, effectTimeMs, comp, audioAmp) } ?: 0f,
                textAnimOffsetY = ts?.let { evaluateProperty(it.animOffsetY, effectTimeMs, comp, audioAmp) } ?: 0f,
                textAnimScale = ts?.let { evaluateProperty(it.animScale, effectTimeMs, comp, audioAmp) } ?: 1f,
                textAnimOpacity = ts?.let { evaluateProperty(it.animOpacity, effectTimeMs, comp, audioAmp).coerceIn(0f, 1f) } ?: 1f,
                textWiggly = ts?.let { evaluateProperty(it.wigglyAmount, effectTimeMs, comp, audioAmp) } ?: 0f,
                textFontSize = ts?.let { evaluateProperty(it.fontSize, effectTimeMs, comp, audioAmp).coerceIn(12f, 400f) } ?: 80f,
                textTracking = ts?.let { evaluateProperty(it.tracking, effectTimeMs, comp, audioAmp) } ?: 0f
            )
        }

        return EvaluatedScene(
            composition = comp,
            timeMs = timeMs,
            audioAmplitude = audioAmp,
            cameraMatrix = cameraMatrix,
            layers = evaluatedList,
            lights = lightsList,
            dofFocusDistance = camFocusDist,
            dofBlur = camDofBlur,
            cameraPerspective = camPerspective,
            cameraFov = camFov
        )
    }

    private fun computeAudioAmplitude(assets: List<AssetItem>, comp: CompositionNode, timeMs: Long): Float {
        val audioLayer = comp.layers.firstOrNull {
            it.type == LayerType.AUDIO && it.visible && timeMs in it.inMs..it.outMs
        }
        if (audioLayer != null) {
            val asset = assets.firstOrNull { it.id == audioLayer.assetId && it.type == AssetType.AUDIO }
            if (asset != null && asset.waveform.isNotEmpty()) {
                val localMs = timeMs - audioLayer.inMs + audioLayer.startOffsetMs
                return AudioAnalyzer.amplitudeAt(asset.waveform, asset.durationMs, localMs)
            }
        }
        // Fallback beat pulse from composition beat markers so audio-reactive demos still respond cleanly
        val nearestBeat = comp.beatMarkersMs.minOfOrNull { abs(it - timeMs) } ?: return 0f
        return if (nearestBeat < 160L) 1f - (nearestBeat / 160f) else 0.1f
    }

    private object Mat4 {
        fun identity(): FloatArray = floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f
        )

        fun multiply(lhs: FloatArray, rhs: FloatArray): FloatArray {
            val out = FloatArray(16)
            for (col in 0..3) {
                for (row in 0..3) {
                    out[col * 4 + row] =
                        lhs[0 * 4 + row] * rhs[col * 4 + 0] +
                        lhs[1 * 4 + row] * rhs[col * 4 + 1] +
                        lhs[2 * 4 + row] * rhs[col * 4 + 2] +
                        lhs[3 * 4 + row] * rhs[col * 4 + 3]
                }
            }
            return out
        }

        fun multiplyVec4(m: FloatArray, x: Float, y: Float, z: Float, w: Float): FloatArray = floatArrayOf(
            m[0] * x + m[4] * y + m[8] * z + m[12] * w,
            m[1] * x + m[5] * y + m[9] * z + m[13] * w,
            m[2] * x + m[6] * y + m[10] * z + m[14] * w,
            m[3] * x + m[7] * y + m[11] * z + m[15] * w
        )

        fun translate(m: FloatArray, tx: Float, ty: Float, tz: Float) {
            for (i in 0..3) {
                m[12 + i] += m[i] * tx + m[4 + i] * ty + m[8 + i] * tz
            }
        }

        fun scale(m: FloatArray, sx: Float, sy: Float, sz: Float) {
            for (i in 0..3) {
                m[i] *= sx
                m[4 + i] *= sy
                m[8 + i] *= sz
            }
        }

        fun rotateX(m: FloatArray, deg: Float) {
            if (abs(deg) < 1e-5f) return
            val rad = Math.toRadians(deg.toDouble())
            val c = cos(rad).toFloat()
            val s = sin(rad).toFloat()
            val r = identity()
            r[5] = c; r[6] = s; r[9] = -s; r[10] = c
            System.arraycopy(multiply(m, r), 0, m, 0, 16)
        }

        fun rotateY(m: FloatArray, deg: Float) {
            if (abs(deg) < 1e-5f) return
            val rad = Math.toRadians(deg.toDouble())
            val c = cos(rad).toFloat()
            val s = sin(rad).toFloat()
            val r = identity()
            r[0] = c; r[2] = -s; r[8] = s; r[10] = c
            System.arraycopy(multiply(m, r), 0, m, 0, 16)
        }

        fun rotateZ(m: FloatArray, deg: Float) {
            if (abs(deg) < 1e-5f) return
            val rad = Math.toRadians(deg.toDouble())
            val c = cos(rad).toFloat()
            val s = sin(rad).toFloat()
            val r = identity()
            r[0] = c; r[1] = s; r[4] = -s; r[5] = c
            System.arraycopy(multiply(m, r), 0, m, 0, 16)
        }
    }
}
