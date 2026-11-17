package com.example.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import android.util.Log
import com.example.engine.CubeLutParser
import com.example.engine.EvaluatedEffect
import com.example.engine.EvaluatedLayer
import com.example.engine.EvaluatedScene
import com.example.engine.ParsedLut3D
import com.example.engine.SceneEvaluator
import com.example.model.EffectType
import com.example.model.LayerType
import com.example.model.LightType
import com.example.model.PreviewResolution
import com.example.model.ProjectDocument
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.tan

class GlSceneRenderer(
    private val context: Context
) : GLSurfaceView.Renderer {

    private val rasterizer = LayerRasterizer(context)

    @Volatile
    var currentDocument: ProjectDocument? = null

    @Volatile
    var currentTimeMs: Long = 0L

    @Volatile
    var previewResolution: PreviewResolution = PreviewResolution.FULL

    @Volatile
    var onSceneEvaluated: ((EvaluatedScene) -> Unit)? = null

    private var compositeProgram = 0
    private var effectProgram = 0
    private var quadVao = 0

    private val layerTextureIds = HashMap<String, Int>()
    private val lutParsedCache = HashMap<String, ParsedLut3D>()
    private val lutTextureCache = HashMap<String, Pair<Int, Int>>() // key -> (texId, lutSize)
    private val curvesTextureCache = HashMap<String, Pair<Int, Int>>() // layerId -> (texId, specHash)

    // Ping-Pong FBOs for Effect Stacks + Dedicated Backdrop FBO
    private var compFbo = 0
    private var compTex = 0
    private var pingFbo = 0
    private var pingTex = 0
    private var pongFbo = 0
    private var pongTex = 0
    private var backdropFbo = 0
    private var backdropTex = 0
    private var fboWidth = 0
    private var fboHeight = 0

    private var surfaceWidth = 1080
    private var surfaceHeight = 1920

    private val quadVertices = floatArrayOf(
        // X, Y, Z, U, V
        -1f, -1f, 0f, 0f, 0f,
         1f, -1f, 0f, 1f, 0f,
        -1f,  1f, 0f, 0f, 1f,
         1f,  1f, 0f, 1f, 1f
    )

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        runCatching {
            initGlResources()
        }.onFailure {
            Log.e("GlSceneRenderer", "onSurfaceCreated failed", it)
        }
    }

    fun initGlResources() {
        // Reset handles because a recreated EGL context invalidates prior GL objects
        layerTextureIds.clear()
        lutTextureCache.clear()
        curvesTextureCache.clear()
        compFbo = 0; compTex = 0
        pingFbo = 0; pingTex = 0
        pongFbo = 0; pongTex = 0
        backdropFbo = 0; backdropTex = 0
        fboWidth = 0; fboHeight = 0

        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_BLEND)

        compositeProgram = ShaderLibrary.compileProgram(
            ShaderLibrary.VERTEX_SHADER,
            ShaderLibrary.COMPOSITE_FRAGMENT_SHADER
        )
        effectProgram = ShaderLibrary.compileProgram(
            ShaderLibrary.FULLSCREEN_VERTEX_SHADER,
            ShaderLibrary.EFFECT_FRAGMENT_SHADER
        )

        val vaos = IntArray(1)
        val vbos = IntArray(1)
        GLES30.glGenVertexArrays(1, vaos, 0)
        GLES30.glGenBuffers(1, vbos, 0)
        quadVao = vaos[0]

        val bb: FloatBuffer = ByteBuffer.allocateDirect(quadVertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(quadVertices)
        bb.position(0)

        GLES30.glBindVertexArray(quadVao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbos[0])
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, quadVertices.size * 4, bb, GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 20, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 20, 12)
        GLES30.glBindVertexArray(0)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        runCatching {
            surfaceWidth = width.coerceAtLeast(1)
            surfaceHeight = height.coerceAtLeast(1)
            GLES30.glViewport(0, 0, surfaceWidth, surfaceHeight)
        }.onFailure {
            Log.e("GlSceneRenderer", "onSurfaceChanged failed", it)
        }
    }

    override fun onDrawFrame(gl: GL10?) {
        runCatching {
            val doc = currentDocument ?: return
            val comp = doc.activeComposition
            val scale = previewResolution.scale
            val rw = (comp.width * scale).toInt().coerceIn(240, 2160)
            val rh = (comp.height * scale).toInt().coerceIn(240, 2160)

            val scene = SceneEvaluator.evaluateScene(doc, comp, currentTimeMs)
            onSceneEvaluated?.invoke(scene)

            renderSceneToViewport(doc, scene, rw, rh, surfaceWidth, surfaceHeight)
        }.onFailure {
            Log.e("GlSceneRenderer", "onDrawFrame failed", it)
        }
    }

    fun renderSceneToViewport(
        doc: ProjectDocument,
        scene: EvaluatedScene,
        renderW: Int,
        renderH: Int,
        outW: Int,
        outH: Int
    ) {
        if (compositeProgram == 0 || effectProgram == 0 || quadVao == 0 ||
            renderW <= 0 || renderH <= 0 || outW <= 0 || outH <= 0
        ) {
            return
        }
        ensureFbos(renderW, renderH)
        if (compFbo == 0 || compTex == 0) return

        // 1. Clear composition FBO with comp background color
        val bgCol = runCatching { Color.parseColor(scene.composition.backgroundColorHex) }
            .getOrDefault(Color.parseColor("#0B0B0E"))
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, compFbo)
        GLES30.glViewport(0, 0, renderW, renderH)
        GLES30.glClearColor(
            Color.red(bgCol) / 255f,
            Color.green(bgCol) / 255f,
            Color.blue(bgCol) / 255f,
            1f
        )
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        // Projection (3D Perspective or 2D Ortho centered at (0,0) matching comp dimensions)
        val compW = scene.composition.width.toFloat()
        val compH = scene.composition.height.toFloat()
        val projMatrix = FloatArray(16)
        val viewMatrix = FloatArray(16)

        if (scene.cameraPerspective) {
            val baseDist = (compH / 2f) / tan(Math.toRadians(scene.cameraFov.coerceIn(15f, 120f) / 2.0)).toFloat()
            val near = 20f
            val far = 14000f
            val frustumScale = near / baseDist.coerceAtLeast(100f)
            Matrix.frustumM(
                projMatrix, 0,
                -compW / 2f * frustumScale, compW / 2f * frustumScale,
                compH / 2f * frustumScale, -compH / 2f * frustumScale,
                near, far
            )
            val camDistMat = FloatArray(16)
            Matrix.setIdentityM(camDistMat, 0)
            Matrix.translateM(camDistMat, 0, 0f, 0f, -baseDist)
            Matrix.multiplyMM(viewMatrix, 0, camDistMat, 0, scene.cameraMatrix, 0)
        } else {
            Matrix.orthoM(projMatrix, 0, -compW / 2f, compW / 2f, compH / 2f, -compH / 2f, -2000f, 2000f)
            System.arraycopy(scene.cameraMatrix, 0, viewMatrix, 0, 16)
        }

        val viewProj = FloatArray(16)
        Matrix.multiplyMM(viewProj, 0, projMatrix, 0, viewMatrix, 0)

        // 2. Render each active layer in Z-order
        for (ev in scene.layers) {
            when (ev.layer.type) {
                LayerType.NULL, LayerType.CAMERA_3D, LayerType.LIGHT_3D, LayerType.AUDIO -> continue
                LayerType.ADJUSTMENT -> {
                    if (ev.evaluatedEffects.isNotEmpty()) {
                        applyEffectsOnFbo(compFbo, compTex, ev.evaluatedEffects, scene.timeMs, renderW, renderH)
                    }
                    continue
                }
                LayerType.PRE_COMP -> {
                    // Render nested Pre-Comp layers inline with parent world transform
                    val subComp = doc.compositions.firstOrNull { it.id == ev.layer.preCompId }
                    if (subComp != null) {
                        val subScene = SceneEvaluator.evaluateScene(doc, subComp, ev.localTimeMs)
                        for (subLayer in subScene.layers) {
                            renderSingleVisualLayer(doc, scene, subLayer, viewProj, ev.worldMatrix, ev.worldOpacity, renderW, renderH, scene.timeMs, scene.audioAmplitude)
                        }
                    }
                    continue
                }
                else -> {
                    renderSingleVisualLayer(doc, scene, ev, viewProj, null, 1f, renderW, renderH, scene.timeMs, scene.audioAmplitude)
                }
            }
        }

        // 3. Blit final compTex onto default screen/encoder framebuffer (letterboxed or exact fit)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, outW, outH)
        GLES30.glClearColor(0.043f, 0.043f, 0.055f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        val ident = FloatArray(16)
        Matrix.setIdentityM(ident, 0)
        drawCompositeQuad(
            sourceTex = compTex,
            backdropTex = compTex,
            mvp = ident,
            modelMatrix = ident,
            blendModeIndex = 0,
            opacity = 1f,
            isPremultiplied = false,
            evaluated = null,
            scene = null
        )
    }

    private fun renderSingleVisualLayer(
        doc: ProjectDocument,
        scene: EvaluatedScene,
        ev: EvaluatedLayer,
        viewProj: FloatArray,
        parentPreCompMatrix: FloatArray?,
        parentOpacity: Float,
        renderW: Int,
        renderH: Int,
        timeMs: Long,
        audioAmp: Float
    ) {
        val bmp = rasterizer.rasterizeLayerBitmap(
            evaluated = ev,
            assets = doc.assets,
            compWidth = doc.activeComposition.width,
            compHeight = doc.activeComposition.height,
            audioAmplitude = audioAmp
        ) ?: return

        val texId = uploadBitmapTexture(ev.layer.id, bmp)
        if (texId == 0) return
        val quadHalfW = bmp.width / 2f
        val quadHalfH = bmp.height / 2f

        // Step A: Draw layer into pingFbo cleanly or with its effects
        var finalLayerTex = texId
        val isPremultiplied = true
        if (ev.evaluatedEffects.isNotEmpty()) {
            copyTexToFbo(texId, pingFbo, renderW, renderH)
            finalLayerTex = applyEffectsOnFbo(pingFbo, pingTex, ev.evaluatedEffects, timeMs, renderW, renderH)
        }

        // Step B: Composite transformed layer onto compFbo
        val model = FloatArray(16)
        System.arraycopy(ev.worldMatrix, 0, model, 0, 16)
        if (parentPreCompMatrix != null) {
            val combined = FloatArray(16)
            Matrix.multiplyMM(combined, 0, parentPreCompMatrix, 0, model, 0)
            System.arraycopy(combined, 0, model, 0, 16)
        }

        Matrix.scaleM(model, 0, quadHalfW, quadHalfH, 1f)

        val mvp = FloatArray(16)
        Matrix.multiplyMM(mvp, 0, viewProj, 0, model, 0)

        // Copy current compTex to dedicated backdropFbo/backdropTex so odd-effect pongTex output is never overwritten
        copyTexToFbo(compTex, backdropFbo, renderW, renderH)

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, compFbo)
        GLES30.glViewport(0, 0, renderW, renderH)
        drawCompositeQuad(
            sourceTex = finalLayerTex,
            backdropTex = backdropTex,
            mvp = mvp,
            modelMatrix = model,
            blendModeIndex = ev.layer.blendMode.shaderIndex,
            opacity = (ev.worldOpacity * parentOpacity).coerceIn(0f, 1f),
            isPremultiplied = isPremultiplied,
            evaluated = ev,
            scene = scene
        )
    }

    private fun applyEffectsOnFbo(
        startFbo: Int,
        startTex: Int,
        effects: List<EvaluatedEffect>,
        timeMs: Long,
        w: Int,
        h: Int
    ): Int {
        var readTex = startTex
        var writeFbo = if (startFbo == pongFbo) pingFbo else pongFbo
        var writeTex = if (startFbo == pongFbo) pingTex else pongTex

        GLES30.glUseProgram(effectProgram)
        GLES30.glBindVertexArray(quadVao)
        val timeSec = timeMs / 1000f

        for (eff in effects) {
            if (eff.type == EffectType.POSTERIZE_TIME) continue
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, writeFbo)
            GLES30.glViewport(0, 0, w, h)
            GLES30.glClearColor(0f, 0f, 0f, 0f)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, readTex)
            GLES30.glUniform1i(GLES30.glGetUniformLocation(effectProgram, "uTexture"), 0)
            GLES30.glUniform1i(GLES30.glGetUniformLocation(effectProgram, "uEffectType"), effectTypeToShaderIndex(eff.type))
            GLES30.glUniform1f(GLES30.glGetUniformLocation(effectProgram, "uTimeSec"), timeSec)
            GLES30.glUniform2f(GLES30.glGetUniformLocation(effectProgram, "uResolution"), w.toFloat(), h.toFloat())

            val orderedParams = eff.params.values.toList()
            for (i in 0..6) {
                GLES30.glUniform1f(
                    GLES30.glGetUniformLocation(effectProgram, "uP$i"),
                    orderedParams.getOrNull(i) ?: 0f
                )
            }

            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

            // Swap ping/pong cleanly so readTex != writeFbo on next pass
            val wroteToPing = (writeFbo == pingFbo)
            readTex = writeTex
            writeFbo = if (wroteToPing) pongFbo else pingFbo
            writeTex = if (wroteToPing) pongTex else pingTex
        }
        GLES30.glBindVertexArray(0)

        if (startFbo == compFbo && readTex != compTex) {
            copyTexToFbo(readTex, compFbo, w, h)
            return compTex
        }
        return readTex
    }

    private fun copyTexToFbo(srcTex: Int, dstFbo: Int, w: Int, h: Int) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, dstFbo)
        GLES30.glViewport(0, 0, w, h)
        GLES30.glClearColor(0f, 0f, 0f, 0f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        GLES30.glUseProgram(effectProgram)
        GLES30.glBindVertexArray(quadVao)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, srcTex)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(effectProgram, "uTexture"), 0)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(effectProgram, "uEffectType"), -1)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glBindVertexArray(0)
    }

    private fun drawCompositeQuad(
        sourceTex: Int,
        backdropTex: Int,
        mvp: FloatArray,
        modelMatrix: FloatArray,
        blendModeIndex: Int,
        opacity: Float,
        isPremultiplied: Boolean,
        evaluated: EvaluatedLayer?,
        scene: EvaluatedScene?
    ) {
        GLES30.glUseProgram(compositeProgram)
        GLES30.glBindVertexArray(quadVao)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, sourceTex)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(compositeProgram, "uSourceTex"), 0)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, backdropTex)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(compositeProgram, "uBackdropTex"), 1)

        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(compositeProgram, "uMvpMatrix"), 1, false, mvp, 0)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(compositeProgram, "uModelMatrix"), 1, false, modelMatrix, 0)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(compositeProgram, "uBlendMode"), blendModeIndex)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(compositeProgram, "uOpacity"), opacity)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(compositeProgram, "uIsPremultiplied"), if (isPremultiplied) 1 else 0)

        // Masking
        val mask = evaluated?.evaluatedMasks?.firstOrNull()
        if (mask != null) {
            GLES30.glUniform1i(GLES30.glGetUniformLocation(compositeProgram, "uHasMask"), 1)
            val shapeIdx = when (mask.spec.shape.name) {
                "CIRCLE" -> 1
                "STAR" -> 2
                else -> 0
            }
            val modeIdx = when (mask.spec.mode.name) {
                "SUBTRACT" -> 1
                "INTERSECT" -> 2
                else -> 0
            }
            GLES30.glUniform1i(GLES30.glGetUniformLocation(compositeProgram, "uMaskShape"), shapeIdx)
            GLES30.glUniform1i(GLES30.glGetUniformLocation(compositeProgram, "uMaskMode"), modeIdx)
            GLES30.glUniform1i(GLES30.glGetUniformLocation(compositeProgram, "uMaskInverted"), if (mask.spec.inverted) 1 else 0)
            GLES30.glUniform4f(
                GLES30.glGetUniformLocation(compositeProgram, "uMaskRect"),
                0.5f + mask.centerX,
                0.5f + mask.centerY,
                (mask.width * 0.5f + mask.expansion).coerceAtLeast(0.02f),
                (mask.height * 0.5f + mask.expansion).coerceAtLeast(0.02f)
            )
            GLES30.glUniform1f(GLES30.glGetUniformLocation(compositeProgram, "uMaskFeather"), mask.feather)
            GLES30.glUniform1f(GLES30.glGetUniformLocation(compositeProgram, "uMaskOpacity"), mask.opacity)
        } else {
            GLES30.glUniform1i(GLES30.glGetUniformLocation(compositeProgram, "uHasMask"), 0)
        }

        // 3D Lighting + Depth of Field
        if (evaluated != null && scene != null && scene.lights.isNotEmpty()) {
            val count = scene.lights.size.coerceAtMost(4)
            GLES30.glUniform1i(GLES30.glGetUniformLocation(compositeProgram, "uLightCount"), count)
            val posArr = FloatArray(16)
            val colArr = FloatArray(16)
            val radArr = FloatArray(4)
            for (i in 0 until count) {
                val l = scene.lights[i]
                posArr[i * 4 + 0] = l.worldX
                posArr[i * 4 + 1] = l.worldY
                posArr[i * 4 + 2] = l.worldZ
                posArr[i * 4 + 3] = when (l.type) {
                    LightType.AMBIENT -> 0f
                    LightType.POINT -> 1f
                    LightType.DIRECTIONAL -> 2f
                }
                val c = runCatching { Color.parseColor(l.colorHex) }.getOrDefault(Color.WHITE)
                colArr[i * 4 + 0] = Color.red(c) / 255f
                colArr[i * 4 + 1] = Color.green(c) / 255f
                colArr[i * 4 + 2] = Color.blue(c) / 255f
                colArr[i * 4 + 3] = l.intensity
                radArr[i] = l.radius
            }
            GLES30.glUniform4fv(GLES30.glGetUniformLocation(compositeProgram, "uLightPos"), 4, posArr, 0)
            GLES30.glUniform4fv(GLES30.glGetUniformLocation(compositeProgram, "uLightColor"), 4, colArr, 0)
            GLES30.glUniform1fv(GLES30.glGetUniformLocation(compositeProgram, "uLightRadius"), 4, radArr, 0)
        } else {
            GLES30.glUniform1i(GLES30.glGetUniformLocation(compositeProgram, "uLightCount"), 0)
        }

        if (evaluated != null && scene != null) {
            GLES30.glUniform1f(GLES30.glGetUniformLocation(compositeProgram, "uLayerPosZ"), if (evaluated.layer.is3D) evaluated.transform.posZ else 0f)
            GLES30.glUniform1f(GLES30.glGetUniformLocation(compositeProgram, "uFocusDistance"), scene.dofFocusDistance)
            GLES30.glUniform1f(GLES30.glGetUniformLocation(compositeProgram, "uDofBlur"), scene.dofBlur)
        } else {
            GLES30.glUniform1f(GLES30.glGetUniformLocation(compositeProgram, "uLayerPosZ"), 0f)
            GLES30.glUniform1f(GLES30.glGetUniformLocation(compositeProgram, "uFocusDistance"), 0f)
            GLES30.glUniform1f(GLES30.glGetUniformLocation(compositeProgram, "uDofBlur"), 0f)
        }

        // RGB Curves & 3D LUT Color Grading
        val cg = evaluated?.layer?.colorGrading
        if (evaluated != null && cg != null) {
            val curvesHash = cg.curveMaster.hashCode() * 31 + cg.curveRed.hashCode() * 17 + cg.curveGreen.hashCode() * 7 + cg.curveBlue.hashCode()
            val cachedCurve = curvesTextureCache[evaluated.layer.id]
            val curveTexId = if (cachedCurve != null && cachedCurve.second == curvesHash) {
                cachedCurve.first
            } else {
                val bmp = CubeLutParser.buildCurvesBitmap(cg)
                val tex = uploadAuxiliaryTexture(cachedCurve?.first ?: 0, bmp)
                curvesTextureCache[evaluated.layer.id] = tex to curvesHash
                tex
            }
            GLES30.glActiveTexture(GLES30.GL_TEXTURE2)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, curveTexId)
            GLES30.glUniform1i(GLES30.glGetUniformLocation(compositeProgram, "uCurvesTex"), 2)
            GLES30.glUniform1i(GLES30.glGetUniformLocation(compositeProgram, "uHasCurves"), 1)

            val lutKey = cg.lutCubeUri?.takeIf { it.isNotBlank() } ?: cg.lutPresetId?.takeIf { it.isNotBlank() }
            if (lutKey != null) {
                val cachedLutTex = lutTextureCache[lutKey] ?: run {
                    val parsed = lutParsedCache.getOrPut(lutKey) {
                        if (cg.lutCubeUri != null) {
                            CubeLutParser.parseCubeFromUri(context, Uri.parse(cg.lutCubeUri))
                                ?: CubeLutParser.generateBuiltInLut("teal_orange")
                        } else {
                            CubeLutParser.generateBuiltInLut(cg.lutPresetId!!)
                        }
                    }
                    val atlasBmp = CubeLutParser.buildAtlasBitmap(parsed)
                    val tex = uploadAuxiliaryTexture(0, atlasBmp)
                    (tex to parsed.size).also { lutTextureCache[lutKey] = it }
                }
                GLES30.glActiveTexture(GLES30.GL_TEXTURE3)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, cachedLutTex.first)
                GLES30.glUniform1i(GLES30.glGetUniformLocation(compositeProgram, "uColorLutTex"), 3)
                GLES30.glUniform1i(GLES30.glGetUniformLocation(compositeProgram, "uHasLut"), 1)
                GLES30.glUniform1f(GLES30.glGetUniformLocation(compositeProgram, "uLutSize"), cachedLutTex.second.toFloat())
                GLES30.glUniform1f(GLES30.glGetUniformLocation(compositeProgram, "uLutIntensity"), cg.lutIntensity.coerceIn(0f, 1f))
            } else {
                GLES30.glUniform1i(GLES30.glGetUniformLocation(compositeProgram, "uHasLut"), 0)
            }
        } else {
            GLES30.glUniform1i(GLES30.glGetUniformLocation(compositeProgram, "uHasCurves"), 0)
            GLES30.glUniform1i(GLES30.glGetUniformLocation(compositeProgram, "uHasLut"), 0)
        }

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glBindVertexArray(0)
    }

    private fun uploadAuxiliaryTexture(existingId: Int, bitmap: Bitmap): Int {
        if (bitmap.isRecycled) return 0
        val texId = if (existingId != 0) existingId else {
            val ids = IntArray(1)
            GLES30.glGenTextures(1, ids, 0)
            ids[0]
        }
        val uploadBmp = if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, false)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, uploadBmp, 0)
        if (uploadBmp !== bitmap) uploadBmp.recycle()
        return texId
    }

    private fun uploadBitmapTexture(layerId: String, bitmap: Bitmap): Int {
        if (bitmap.isRecycled) return 0
        val existing = layerTextureIds[layerId]
        val texId = if (existing != null && existing != 0) {
            existing
        } else {
            val ids = IntArray(1)
            GLES30.glGenTextures(1, ids, 0)
            layerTextureIds[layerId] = ids[0]
            ids[0]
        }
        val uploadBmp = if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, false)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, uploadBmp, 0)
        if (uploadBmp !== bitmap) uploadBmp.recycle()
        return texId
    }

    private fun ensureFbos(w: Int, h: Int) {
        if (fboWidth == w && fboHeight == h && compFbo != 0 && backdropFbo != 0) return
        deleteFbos()
        fboWidth = w
        fboHeight = h
        createFbo(w, h).let { (f, t) -> compFbo = f; compTex = t }
        createFbo(w, h).let { (f, t) -> pingFbo = f; pingTex = t }
        createFbo(w, h).let { (f, t) -> pongFbo = f; pongTex = t }
        createFbo(w, h).let { (f, t) -> backdropFbo = f; backdropTex = t }
    }

    private fun createFbo(w: Int, h: Int): Pair<Int, Int> {
        val fbos = IntArray(1)
        val texs = IntArray(1)
        GLES30.glGenFramebuffers(1, fbos, 0)
        GLES30.glGenTextures(1, texs, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texs[0])
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, w, h, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbos[0])
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, texs[0], 0)
        val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) {
            Log.e("GlSceneRenderer", "Incomplete FBO status=0x${Integer.toHexString(status)}")
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        return fbos[0] to texs[0]
    }

    private fun deleteFbos() {
        val fbos = intArrayOf(compFbo, pingFbo, pongFbo, backdropFbo).filter { it != 0 }.toIntArray()
        val texs = intArrayOf(compTex, pingTex, pongTex, backdropTex).filter { it != 0 }.toIntArray()
        if (fbos.isNotEmpty()) GLES30.glDeleteFramebuffers(fbos.size, fbos, 0)
        if (texs.isNotEmpty()) GLES30.glDeleteTextures(texs.size, texs, 0)
        compFbo = 0; pingFbo = 0; pongFbo = 0; backdropFbo = 0
        compTex = 0; pingTex = 0; pongTex = 0; backdropTex = 0
    }

    private fun effectTypeToShaderIndex(type: EffectType): Int = when (type) {
        EffectType.MOTION_TILE -> 0
        EffectType.S_SHAKE -> 1
        EffectType.WAVE_WARP -> 2
        EffectType.TURBULENT_DISPLACE -> 3
        EffectType.TWIRL -> 4
        EffectType.SPHERIZE -> 5
        EffectType.OPTICS_COMPENSATION -> 6
        EffectType.DEEP_GLOW -> 7
        EffectType.MOTION_BLUR -> 8
        EffectType.GAUSSIAN_BLUR -> 9
        EffectType.ZOOM_BLUR -> 10
        EffectType.RADIAL_BLUR -> 11
        EffectType.LIGHT_RAYS -> 12
        EffectType.CHROMATIC_ABERRATION -> 13
        EffectType.VHS_SCANLINES -> 14
        EffectType.HALFTONE -> 15
        EffectType.PIXELATE -> 16
        EffectType.POSTERIZE_TIME -> 17
        EffectType.EDGE_DETECT -> 18
        EffectType.VIGNETTE -> 19
        EffectType.CHROMA_KEY -> 20
        EffectType.COLOR_GRADING -> 21
    }
}
