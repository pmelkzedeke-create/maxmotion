package com.example.ui.components

import android.opengl.GLSurfaceView
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.RotateLeft
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLContext
import javax.microedition.khronos.egl.EGLDisplay
import com.example.engine.EvaluatedLayer
import com.example.engine.EvaluatedScene
import com.example.model.CompositionNode
import com.example.model.LayerType
import com.example.model.PreviewResolution
import com.example.model.ProjectDocument
import com.example.render.GlSceneRenderer
import com.example.ui.theme.BeatAmber
import com.example.ui.theme.ElectricPurple
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.OledBlack
import com.example.ui.theme.StudioHairline
import com.example.ui.theme.StudioPanel
import com.example.ui.theme.TextPrimary
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

private enum class ActiveHandle {
    NONE, BODY, ANCHOR_PIVOT, SCALE_CORNER, ROTATE_HANDLE, ORBIT_CAMERA, TRACKER_CROSSHAIR
}

private fun estimateLayerBoxSize(ev: EvaluatedLayer, comp: CompositionNode): Pair<Float, Float> {
    return when (ev.layer.type) {
        LayerType.VECTOR -> {
            val w = ev.layer.vectorSpec?.width ?: 360f
            val h = ev.layer.vectorSpec?.height ?: 240f
            w to h
        }
        LayerType.TEXT -> {
            val txt = ev.layer.textSpec?.content.orEmpty().ifEmpty { "Texto" }
            val lines = txt.split("\n")
            val maxChars = lines.maxOfOrNull { it.length }?.coerceAtLeast(3) ?: 4
            val fSize = ev.textFontSize
            val estW = (maxChars * fSize * 0.65f).coerceIn(180f, comp.width * 0.92f)
            val estH = (lines.size * fSize * 1.35f).coerceIn(90f, comp.height * 0.6f)
            estW to estH
        }
        LayerType.IMAGE, LayerType.VIDEO -> (comp.width * 0.72f) to (comp.height * 0.45f)
        LayerType.AUDIO -> 720f to 240f
        LayerType.LIGHT_3D -> 160f to 160f
        else -> 320f to 200f
    }
}

@Composable
fun CanvasViewport(
    document: ProjectDocument,
    playheadMs: Long,
    evaluatedScene: EvaluatedScene?,
    selectedLayerIds: Set<String>,
    previewResolution: PreviewResolution,
    showSafetyGrid: Boolean,
    magneticSnapping: Boolean,
    nudgeMode: Boolean,
    penToolActive: Boolean,
    orbitMode: Boolean,
    trackerTargetMode: Boolean,
    trackerTargetNorm: Pair<Float, Float>,
    onSelectLayer: (String?) -> Unit,
    onBeginGesture: () -> Unit,
    onMoveLayer: (String, Float, Float) -> Unit,
    onMoveAnchor: (String, Float, Float) -> Unit,
    onScaleLayer: (String, Float, Float) -> Unit,
    onRotateLayer: (String, Float) -> Unit,
    onOrbitDrag: (Float, Float) -> Unit,
    onTrackerTargetDrag: (Float, Float) -> Unit,
    onEndGesture: (String) -> Unit,
    onAddPenPoint: (Float, Float) -> Unit,
    onNudge: (Float, Float, Float) -> Unit,
    onTwoFingerUndo: () -> Unit,
    onThreeFingerRedo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val renderer = remember { GlSceneRenderer(context) }

    LaunchedEffect(document, playheadMs, previewResolution) {
        renderer.currentDocument = document
        renderer.currentTimeMs = playheadMs
        renderer.previewResolution = previewResolution
    }

    var snapGuideX by remember { mutableStateOf<Float?>(null) }
    var snapGuideY by remember { mutableStateOf<Float?>(null) }
    var isDraggingLayer by remember { mutableStateOf(false) }

    val pulseTransition = rememberInfiniteTransition(label = "pivot_pulse")
    val pivotPulse by pulseTransition.animateFloat(
        initialValue = 0.75f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pivot_scale"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        ElectricPurple.copy(alpha = 0.08f),
                        OledBlack
                    )
                )
            )
            // Multi-finger Undo (2 fingers) / Redo (3 fingers) gesture detector
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var maxPointers = 1
                    var moved = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.count { it.pressed }
                        if (pressed > maxPointers) maxPointers = pressed
                        if (event.changes.any { (it.position - it.previousPosition).getDistance() > 14f }) {
                            moved = true
                        }
                        if (event.changes.all { !it.pressed }) break
                    }
                    if (!moved) {
                        if (maxPointers == 2) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onTwoFingerUndo()
                        } else if (maxPointers == 3) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onThreeFingerRedo()
                        }
                    }
                }
            }
            .padding(vertical = 6.dp, horizontal = 8.dp)
            .testTag("canvas_viewport"),
        contentAlignment = Alignment.Center
    ) {
        val density = LocalDensity.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val comp = document.activeComposition
        val compAspect = comp.width.toFloat() / comp.height.coerceAtLeast(1).toFloat()
        val availW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val availH = constraints.maxHeight.toFloat().coerceAtLeast(1f)

        val (fitW, fitH) = if (availW / availH > compAspect) {
            (availH * compAspect) to availH
        } else {
            availW to (availW / compAspect)
        }
        val pxToCompScale = comp.width.toFloat() / fitW.coerceAtLeast(1f)
        val fitWidthDp = with(density) { fitW.toDp() }
        val fitHeightDp = with(density) { fitH.toDp() }

        val selectedEval: EvaluatedLayer? = evaluatedScene?.layers?.firstOrNull { it.layer.id in selectedLayerIds }

        // Cinema Monitor Container (avoid clipping/shadow directly over SurfaceView hardware surface)
        Box(
            modifier = Modifier
                .size(width = fitWidthDp, height = fitHeightDp)
                .background(OledBlack)
                .border(1.dp, StudioHairline, RoundedCornerShape(4.dp))
        ) {
            val glViewHolder = remember { arrayOfNulls<GLSurfaceView>(1) }

            AndroidView(
                factory = { ctx ->
                    GLSurfaceView(ctx).apply {
                        setEGLConfigChooser(SafeEglConfigChooser())
                        setEGLContextFactory(SafeEglContextFactory())
                        setEGLContextClientVersion(3)
                        preserveEGLContextOnPause = true
                        setRenderer(renderer)
                        renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
                        glViewHolder[0] = this
                    }
                },
                update = { view ->
                    glViewHolder[0] = view
                    renderer.currentDocument = document
                    renderer.currentTimeMs = playheadMs
                    renderer.previewResolution = previewResolution
                    view.requestRender()
                },
                modifier = Modifier.fillMaxSize()
            )

            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> glViewHolder[0]?.onResume()
                        Lifecycle.Event.ON_PAUSE -> glViewHolder[0]?.onPause()
                        else -> Unit
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                    glViewHolder[0]?.onPause()
                    glViewHolder[0] = null
                }
            }

            // Direct Manipulation Overlay: Bounding Box, Corner Brackets, Scale/Rotate Handles, Anchor Pivot, Magnetic Guides
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(penToolActive, selectedEval?.layer?.id, comp.width, comp.height) {
                        if (penToolActive) {
                            detectTapGestures { offset ->
                                val compX = (offset.x - size.width / 2f) * pxToCompScale
                                val compY = (offset.y - size.height / 2f) * pxToCompScale
                                onAddPenPoint(compX, compY)
                            }
                        } else {
                            detectTapGestures { offset ->
                                val cx = size.width / 2f
                                val cy = size.height / 2f
                                val compX = (offset.x - cx) * pxToCompScale
                                val compY = (offset.y - cy) * pxToCompScale
                                val hit = evaluatedScene?.layers?.asReversed()?.firstOrNull { ev ->
                                    if (ev.layer.locked || ev.layer.type == LayerType.ADJUSTMENT || ev.layer.type == LayerType.CAMERA_3D) {
                                        return@firstOrNull false
                                    }
                                    val (baseW, baseH) = estimateLayerBoxSize(ev, comp)
                                    val halfW = baseW * abs(ev.worldScaleX) * 0.56f
                                    val halfH = baseH * abs(ev.worldScaleY) * 0.56f
                                    abs(compX - ev.worldCenterX) <= halfW && abs(compY - ev.worldCenterY) <= halfH
                                }
                                onSelectLayer(hit?.layer?.id)
                            }
                        }
                    }
                    .pointerInput(selectedEval?.layer?.id, magneticSnapping, penToolActive, orbitMode, trackerTargetMode) {
                        if (penToolActive) return@pointerInput
                        var activeHandle = ActiveHandle.NONE
                        var startPosX = 0f
                        var startPosY = 0f
                        var startScaleX = 1f
                        var startScaleY = 1f
                        var startRotZ = 0f
                        var dragAccumX = 0f
                        var dragAccumY = 0f
                        var wasSnappedX = false
                        var wasSnappedY = false

                        detectDragGestures(
                            onDragStart = { startOffset ->
                                if (orbitMode) {
                                    onBeginGesture()
                                    activeHandle = ActiveHandle.ORBIT_CAMERA
                                    isDraggingLayer = true
                                    return@detectDragGestures
                                }
                                if (trackerTargetMode) {
                                    activeHandle = ActiveHandle.TRACKER_CROSSHAIR
                                    val nx = (startOffset.x / size.width) - 0.5f
                                    val ny = (startOffset.y / size.height) - 0.5f
                                    onTrackerTargetDrag(nx, ny)
                                    return@detectDragGestures
                                }
                                val ev = selectedEval ?: return@detectDragGestures
                                if (ev.layer.locked) return@detectDragGestures
                                onBeginGesture()
                                isDraggingLayer = true

                                val cx = size.width / 2f + ev.worldCenterX / pxToCompScale
                                val cy = size.height / 2f + ev.worldCenterY / pxToCompScale
                                val anchorScreenX = cx + (ev.transform.anchorX / pxToCompScale)
                                val anchorScreenY = cy + (ev.transform.anchorY / pxToCompScale)
                                val (baseW, baseH) = estimateLayerBoxSize(ev, comp)
                                val boxHalfW = (baseW * abs(ev.worldScaleX) / 2f) / pxToCompScale
                                val boxHalfH = (baseH * abs(ev.worldScaleY) / 2f) / pxToCompScale
                                val rotHandlePos = Offset(cx, cy - boxHalfH - 38f)

                                activeHandle = when {
                                    hypot(startOffset.x - anchorScreenX, startOffset.y - anchorScreenY) < 34f -> ActiveHandle.ANCHOR_PIVOT
                                    hypot(startOffset.x - rotHandlePos.x, startOffset.y - rotHandlePos.y) < 34f -> ActiveHandle.ROTATE_HANDLE
                                    hypot(abs(startOffset.x - cx) - boxHalfW, abs(startOffset.y - cy) - boxHalfH) < 38f -> ActiveHandle.SCALE_CORNER
                                    else -> ActiveHandle.BODY
                                }
                                startPosX = ev.transform.posX
                                startPosY = ev.transform.posY
                                startScaleX = ev.transform.scaleX
                                startScaleY = ev.transform.scaleY
                                startRotZ = ev.transform.rotZ
                                dragAccumX = 0f
                                dragAccumY = 0f
                            },
                            onDragEnd = {
                                snapGuideX = null
                                snapGuideY = null
                                isDraggingLayer = false
                                val label = when (activeHandle) {
                                    ActiveHandle.ANCHOR_PIVOT -> "Ajustar Âncora"
                                    ActiveHandle.SCALE_CORNER -> "Escalar Camada"
                                    ActiveHandle.ROTATE_HANDLE -> "Rotacionar Camada"
                                    ActiveHandle.ORBIT_CAMERA -> "Órbita 3D"
                                    ActiveHandle.TRACKER_CROSSHAIR -> "Posicionar Mira"
                                    else -> "Mover Camada"
                                }
                                activeHandle = ActiveHandle.NONE
                                if (label != "Posicionar Mira") onEndGesture(label)
                            },
                            onDragCancel = {
                                snapGuideX = null
                                snapGuideY = null
                                isDraggingLayer = false
                                activeHandle = ActiveHandle.NONE
                                onEndGesture("Cancelar Gesto")
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                if (activeHandle == ActiveHandle.ORBIT_CAMERA) {
                                    onOrbitDrag(dragAmount.x * 0.25f, -dragAmount.y * 0.25f)
                                    return@detectDragGestures
                                }
                                if (activeHandle == ActiveHandle.TRACKER_CROSSHAIR) {
                                    val nx = (change.position.x / size.width) - 0.5f
                                    val ny = (change.position.y / size.height) - 0.5f
                                    onTrackerTargetDrag(nx, ny)
                                    return@detectDragGestures
                                }
                                val ev = selectedEval ?: return@detectDragGestures
                                val dxComp = dragAmount.x * pxToCompScale
                                val dyComp = dragAmount.y * pxToCompScale
                                dragAccumX += dxComp
                                dragAccumY += dyComp

                                when (activeHandle) {
                                    ActiveHandle.BODY -> {
                                        var targetX = startPosX + dragAccumX
                                        var targetY = startPosY + dragAccumY
                                        if (magneticSnapping) {
                                            val snapThresh = 22f
                                            val xGuides = listOf(0f, -comp.width / 6f, comp.width / 6f)
                                            val yGuides = listOf(0f, -comp.height / 6f, comp.height / 6f)

                                            val closestX = xGuides.minByOrNull { abs(targetX - it) }
                                            if (closestX != null && abs(targetX - closestX) < snapThresh) {
                                                targetX = closestX
                                                snapGuideX = closestX
                                                if (!wasSnappedX) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    wasSnappedX = true
                                                }
                                            } else {
                                                snapGuideX = null
                                                wasSnappedX = false
                                            }

                                            val closestY = yGuides.minByOrNull { abs(targetY - it) }
                                            if (closestY != null && abs(targetY - closestY) < snapThresh) {
                                                targetY = closestY
                                                snapGuideY = closestY
                                                if (!wasSnappedY) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    wasSnappedY = true
                                                }
                                            } else {
                                                snapGuideY = null
                                                wasSnappedY = false
                                            }
                                        }
                                        onMoveLayer(ev.layer.id, targetX, targetY)
                                    }
                                    ActiveHandle.ANCHOR_PIVOT -> onMoveAnchor(ev.layer.id, dxComp, dyComp)
                                    ActiveHandle.SCALE_CORNER -> {
                                        val deltaFactor = (dragAccumX - dragAccumY) / 380f
                                        val nextScale = (startScaleX + deltaFactor).coerceIn(0.1f, 5f)
                                        onScaleLayer(ev.layer.id, nextScale, (startScaleY + deltaFactor).coerceIn(0.1f, 5f))
                                    }
                                    ActiveHandle.ROTATE_HANDLE -> {
                                        val deltaDeg = dragAccumX * 0.35f
                                        onRotateLayer(ev.layer.id, startRotZ + deltaDeg)
                                    }
                                    else -> Unit
                                }
                            }
                        )
                    }
            ) {
                val w = size.width
                val h = size.height
                val cx = w / 2f
                val cy = h / 2f

                // Safety Grid (Rule of Thirds)
                if (showSafetyGrid) {
                    val gridColor = Color.White.copy(alpha = 0.08f)
                    val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 8f), 0f)
                    drawLine(gridColor, Offset(w / 3f, 0f), Offset(w / 3f, h), strokeWidth = 1f, pathEffect = dash)
                    drawLine(gridColor, Offset(w * 2f / 3f, 0f), Offset(w * 2f / 3f, h), strokeWidth = 1f, pathEffect = dash)
                    drawLine(gridColor, Offset(0f, h / 3f), Offset(w, h / 3f), strokeWidth = 1f, pathEffect = dash)
                    drawLine(gridColor, Offset(0f, h * 2f / 3f), Offset(w, h * 2f / 3f), strokeWidth = 1f, pathEffect = dash)
                }

                // Active Magnetic Guides
                snapGuideX?.let { gx ->
                    val sx = cx + gx / pxToCompScale
                    drawLine(NeonCyan.copy(alpha = 0.45f), Offset(sx, 0f), Offset(sx, h), strokeWidth = 5f)
                    drawLine(NeonCyan, Offset(sx, 0f), Offset(sx, h), strokeWidth = 1.8f)
                }
                snapGuideY?.let { gy ->
                    val sy = cy + gy / pxToCompScale
                    drawLine(NeonCyan.copy(alpha = 0.45f), Offset(0f, sy), Offset(w, sy), strokeWidth = 5f)
                    drawLine(NeonCyan, Offset(0f, sy), Offset(w, sy), strokeWidth = 1.8f)
                }

                // 3D Light Gizmos on Canvas
                evaluatedScene?.layers?.filter { it.layer.type == LayerType.LIGHT_3D }?.forEach { lightEv ->
                    val lx = cx + lightEv.worldCenterX / pxToCompScale
                    val ly = cy + lightEv.worldCenterY / pxToCompScale
                    drawCircle(BeatAmber.copy(alpha = 0.25f), radius = 18f, center = Offset(lx, ly))
                    drawCircle(BeatAmber, radius = 9f, center = Offset(lx, ly), style = Stroke(width = 2.2f))
                    drawCircle(Color.White, radius = 3.5f, center = Offset(lx, ly))
                }

                // CapCut-style Sleek Selection Frame + Corner L-Brackets + Pulsing Pivot
                if (selectedEval != null && selectedEval.layer.type != LayerType.ADJUSTMENT && selectedEval.layer.type != LayerType.CAMERA_3D) {
                    val lx = cx + selectedEval.worldCenterX / pxToCompScale
                    val ly = cy + selectedEval.worldCenterY / pxToCompScale
                    val (baseW, baseH) = estimateLayerBoxSize(selectedEval, comp)
                    val boxW = (baseW * abs(selectedEval.worldScaleX)) / pxToCompScale
                    val boxH = (baseH * abs(selectedEval.worldScaleY)) / pxToCompScale

                    rotate(degrees = selectedEval.worldRotationZ, pivot = Offset(lx, ly)) {
                        val left = lx - boxW / 2f
                        val top = ly - boxH / 2f
                        val right = left + boxW
                        val bottom = top + boxH

                        // Subtle translucent wireframe
                        drawRect(
                            color = Color.White.copy(alpha = 0.72f),
                            topLeft = Offset(left, top),
                            size = Size(boxW, boxH),
                            style = Stroke(width = 1.5f)
                        )

                        // Bold Neon Cyan Corner L-Brackets + Corner Dots
                        val bLen = (minOf(boxW, boxH) * 0.22f).coerceIn(10f, 24f)
                        val bracketStroke = 3.5f
                        // Top-Left
                        drawLine(NeonCyan, Offset(left, top), Offset(left + bLen, top), strokeWidth = bracketStroke, cap = StrokeCap.Round)
                        drawLine(NeonCyan, Offset(left, top), Offset(left, top + bLen), strokeWidth = bracketStroke, cap = StrokeCap.Round)
                        // Top-Right
                        drawLine(NeonCyan, Offset(right, top), Offset(right - bLen, top), strokeWidth = bracketStroke, cap = StrokeCap.Round)
                        drawLine(NeonCyan, Offset(right, top), Offset(right, top + bLen), strokeWidth = bracketStroke, cap = StrokeCap.Round)
                        // Bottom-Left
                        drawLine(NeonCyan, Offset(left, bottom), Offset(left + bLen, bottom), strokeWidth = bracketStroke, cap = StrokeCap.Round)
                        drawLine(NeonCyan, Offset(left, bottom), Offset(left, bottom - bLen), strokeWidth = bracketStroke, cap = StrokeCap.Round)
                        // Bottom-Right
                        drawLine(NeonCyan, Offset(right, bottom), Offset(right - bLen, bottom), strokeWidth = bracketStroke, cap = StrokeCap.Round)
                        drawLine(NeonCyan, Offset(right, bottom), Offset(right, bottom - bLen), strokeWidth = bracketStroke, cap = StrokeCap.Round)

                        listOf(
                            Offset(left, top),
                            Offset(right, top),
                            Offset(left, bottom),
                            Offset(right, bottom)
                        ).forEach { corner ->
                            drawCircle(Color.White, radius = 6.5f, center = corner)
                            drawCircle(NeonCyan, radius = 6.5f, center = corner, style = Stroke(width = 2.2f))
                        }

                        // Rotation Stem & Pill Handle
                        val rotPos = Offset(lx, top - 34f)
                        drawLine(NeonCyan.copy(alpha = 0.8f), Offset(lx, top), rotPos, strokeWidth = 1.8f)
                        drawCircle(ElectricPurple, radius = 10f, center = rotPos)
                        drawCircle(Color.White, radius = 10f, center = rotPos, style = Stroke(width = 2.2f))
                    }

                    // Independent Pulsing Anchor Point (Pivot)
                    val pivX = lx + selectedEval.transform.anchorX / pxToCompScale
                    val pivY = ly + selectedEval.transform.anchorY / pxToCompScale
                    drawCircle(ElectricPurple.copy(alpha = 0.25f), radius = 13f * pivotPulse, center = Offset(pivX, pivY))
                    drawCircle(NeonCyan, radius = 7.5f, center = Offset(pivX, pivY), style = Stroke(width = 2f))
                    drawLine(NeonCyan, Offset(pivX - 12f, pivY), Offset(pivX + 12f, pivY), strokeWidth = 1.8f)
                    drawLine(NeonCyan, Offset(pivX, pivY - 12f), Offset(pivX, pivY + 12f), strokeWidth = 1.8f)

                    val pts = selectedEval.layer.vectorSpec?.points.orEmpty()
                    if (penToolActive && pts.isNotEmpty()) {
                        pts.forEach { pt ->
                            val sx = cx + pt.x / pxToCompScale
                            val sy = cy + pt.y / pxToCompScale
                            drawCircle(NeonCyan, radius = 7f, center = Offset(sx, sy))
                            drawCircle(Color.White, radius = 7f, center = Offset(sx, sy), style = Stroke(width = 1.5f))
                        }
                    }
                }

                // 2D Motion Tracker Crosshair Overlay
                if (trackerTargetMode) {
                    val tx = (trackerTargetNorm.first + 0.5f) * w
                    val ty = (trackerTargetNorm.second + 0.5f) * h
                    drawRect(
                        color = BeatAmber,
                        topLeft = Offset(tx - 24f, ty - 24f),
                        size = Size(48f, 48f),
                        style = Stroke(width = 2.5f)
                    )
                    drawLine(BeatAmber, Offset(tx - 34f, ty), Offset(tx + 34f, ty), strokeWidth = 2f)
                    drawLine(BeatAmber, Offset(tx, ty - 34f), Offset(tx, ty + 34f), strokeWidth = 2f)
                    drawCircle(NeonCyan, radius = 5f, center = Offset(tx, ty))
                }
            }

            // Live Transform Readout HUD while dragging
            AnimatedVisibility(
                visible = isDraggingLayer && selectedEval != null,
                enter = fadeIn(tween(120)) + scaleIn(initialScale = 0.9f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
                exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.9f),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
            ) {
                selectedEval?.let { ev ->
                    Surface(
                        color = StudioPanel.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(99.dp),
                        modifier = Modifier.border(1.dp, NeonCyan.copy(alpha = 0.45f), RoundedCornerShape(99.dp))
                    ) {
                        Text(
                            text = "X:${ev.transform.posX.roundToInt()}  Y:${ev.transform.posY.roundToInt()}  ${ev.transform.rotZ.roundToInt()}°  ${(ev.transform.scaleX * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonCyan,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Animated Orbit 3D Badge
            AnimatedVisibility(
                visible = orbitMode,
                enter = fadeIn() + scaleIn(initialScale = 0.85f),
                exit = fadeOut() + scaleOut(targetScale = 0.85f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            ) {
                Surface(
                    color = NeonCyan.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(99.dp)
                ) {
                    Text(
                        text = "ÓRBITA 3D ATIVA",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = OledBlack,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Animated Precision Nudge D-Pad
            AnimatedVisibility(
                visible = nudgeMode && selectedEval != null,
                enter = fadeIn(tween(160)) + scaleIn(initialScale = 0.88f, animationSpec = spring(dampingRatio = 0.72f)),
                exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.88f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
            ) {
                Surface(
                    color = StudioPanel.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .border(1.dp, NeonCyan.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                        .testTag("nudge_pad")
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(6.dp)
                    ) {
                        Text("Ajuste 1px", style = MaterialTheme.typography.labelSmall, color = NeonCyan)
                        IconButton(onClick = { onNudge(0f, -2f, 0f) }, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Rounded.KeyboardArrowUp, "Mover cima", tint = TextPrimary)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(onClick = { onNudge(-2f, 0f, 0f) }, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Mover esquerda", tint = TextPrimary)
                            }
                            IconButton(onClick = { onNudge(0f, 0f, -1f) }, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Rounded.RotateLeft, "Girar -1°", tint = ElectricPurple)
                            }
                            IconButton(onClick = { onNudge(0f, 0f, 1f) }, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Rounded.RotateRight, "Girar +1°", tint = ElectricPurple)
                            }
                            IconButton(onClick = { onNudge(2f, 0f, 0f) }, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Mover direita", tint = TextPrimary)
                            }
                        }
                        IconButton(onClick = { onNudge(0f, 2f, 0f) }, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Rounded.KeyboardArrowDown, "Mover baixo", tint = TextPrimary)
                        }
                    }
                }
            }
        }
    }
}

private class SafeEglConfigChooser : GLSurfaceView.EGLConfigChooser {
    override fun chooseConfig(egl: EGL10, display: EGLDisplay): EGLConfig {
        val candidates = listOf(
            intArrayOf(
                EGL10.EGL_RED_SIZE, 8,
                EGL10.EGL_GREEN_SIZE, 8,
                EGL10.EGL_BLUE_SIZE, 8,
                EGL10.EGL_ALPHA_SIZE, 8,
                EGL10.EGL_DEPTH_SIZE, 0,
                EGL10.EGL_STENCIL_SIZE, 0,
                EGL10.EGL_RENDERABLE_TYPE, 0x0040, // EGL_OPENGL_ES3_BIT_KHR
                EGL10.EGL_NONE
            ),
            intArrayOf(
                EGL10.EGL_RED_SIZE, 8,
                EGL10.EGL_GREEN_SIZE, 8,
                EGL10.EGL_BLUE_SIZE, 8,
                EGL10.EGL_ALPHA_SIZE, 8,
                EGL10.EGL_DEPTH_SIZE, 0,
                EGL10.EGL_STENCIL_SIZE, 0,
                EGL10.EGL_RENDERABLE_TYPE, 4, // EGL_OPENGL_ES2_BIT
                EGL10.EGL_NONE
            ),
            intArrayOf(
                EGL10.EGL_RENDERABLE_TYPE, 4,
                EGL10.EGL_NONE
            ),
            intArrayOf(EGL10.EGL_NONE)
        )
        val numConfig = IntArray(1)
        for (spec in candidates) {
            if (egl.eglChooseConfig(display, spec, null, 0, numConfig) && numConfig[0] > 0) {
                val configs = arrayOfNulls<EGLConfig>(numConfig[0])
                if (egl.eglChooseConfig(display, spec, configs, configs.size, numConfig)) {
                    configs.firstOrNull()?.let { return it }
                }
            }
        }
        throw IllegalArgumentException("Nenhum EGLConfig compatível encontrado")
    }
}

private class SafeEglContextFactory : GLSurfaceView.EGLContextFactory {
    override fun createContext(egl: EGL10, display: EGLDisplay, eglConfig: EGLConfig): EGLContext {
        val es3Attr = intArrayOf(EGL_CONTEXT_CLIENT_VERSION, 3, EGL10.EGL_NONE)
        val ctx3 = egl.eglCreateContext(display, eglConfig, EGL10.EGL_NO_CONTEXT, es3Attr)
        if (ctx3 != null && ctx3 != EGL10.EGL_NO_CONTEXT) {
            return ctx3
        }
        Log.w("CanvasViewport", "OpenGL ES 3.0 unavailable (err=${egl.eglGetError()}), falling back to ES 2.0")
        val es2Attr = intArrayOf(EGL_CONTEXT_CLIENT_VERSION, 2, EGL10.EGL_NONE)
        return egl.eglCreateContext(display, eglConfig, EGL10.EGL_NO_CONTEXT, es2Attr)
    }

    override fun destroyContext(egl: EGL10, display: EGLDisplay, context: EGLContext) {
        if (!egl.eglDestroyContext(display, context)) {
            Log.e("CanvasViewport", "eglDestroyContext failed: ${egl.eglGetError()}")
        }
    }

    companion object {
        private const val EGL_CONTEXT_CLIENT_VERSION = 0x3098
    }
}

