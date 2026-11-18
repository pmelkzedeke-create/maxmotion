package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AssetItem
import com.example.model.BezierCurve
import com.example.model.CompositionNode
import com.example.ui.components.timeline.MultiKeyframeBatchBar
import com.example.ui.components.timeline.SubTrackTarget
import com.example.ui.components.timeline.TIMELINE_HEADER_WIDTH
import com.example.ui.components.timeline.TIMELINE_ROW_HEIGHT
import com.example.ui.components.timeline.TimelineClipTrackLane
import com.example.ui.components.timeline.TimelineLayerHeader
import com.example.ui.components.timeline.TimelineQuickPrecisionStrip
import com.example.ui.components.timeline.TimelineSnapEngine
import com.example.ui.components.timeline.TimelineSubTracksSection
import com.example.ui.components.timeline.TimelineTransportBar
import com.example.ui.components.timeline.collectLayerSubTracks
import com.example.ui.components.timeline.formatTimelineTimecode
import com.example.ui.theme.BeatAmber
import com.example.ui.theme.CacheEmerald
import com.example.ui.theme.ElectricPurple
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.OledBlack
import com.example.ui.theme.StudioCard
import com.example.ui.theme.StudioElevated
import com.example.ui.theme.StudioHairline
import com.example.ui.theme.StudioPanel
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun TimelinePanel(
    composition: CompositionNode,
    assets: List<AssetItem>,
    playheadMs: Long,
    isPlaying: Boolean,
    selectedLayerIds: Set<String>,
    expandedLayerIds: Set<String>,
    selectedKeyframeIds: Set<String>,
    pickWhipSourceId: String?,
    timelineZoom: Float,
    magneticSnapping: Boolean,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onZoomChange: (Float) -> Unit,
    onToggleMagnetic: () -> Unit,
    onSelectLayer: (String, Boolean) -> Unit,
    onToggleExpandLayer: (String) -> Unit,
    onToggleSelectKeyframe: (String) -> Unit,
    onShiftSelectedKeyframes: (Long) -> Unit,
    onDeleteSelectedKeyframes: () -> Unit,
    onApplyCurveToSelectedKeyframes: (BezierCurve) -> Unit,
    onClearSelectedKeyframes: () -> Unit,
    onToggleVisibility: (String) -> Unit,
    onToggleLock: (String) -> Unit,
    onToggleSolo: (String) -> Unit,
    onToggle3D: (String) -> Unit,
    onStartPickWhip: (String) -> Unit,
    onMoveZOrder: (String, Int) -> Unit = { _, _ -> },
    onUpdateBounds: (String, Long, Long) -> Unit,
    onEnterPreComp: (String) -> Unit,
    onBeginGesture: () -> Unit = {},
    onEndGesture: (String) -> Unit = {},
    onSplitAtPlayhead: () -> Unit = {},
    onToggleBeatMarker: () -> Unit = {},
    onJumpBeatMarker: (Int) -> Unit = {},
    onToggleKeyframeAtPlayhead: () -> Unit = {},
    onJumpKeyframe: (Int) -> Unit = {},
    onOpenSpeedShortcut: () -> Unit = {},
    onFreezeFrame: () -> Unit = {},
    onPreCompose: () -> Unit = {},
    onDuplicateSelectedKeyframes: (Long) -> Unit = {},
    onMoveKeyframeTime: (String, SubTrackTarget, String, Long) -> Unit = { _, _, _, _ -> },
    onReorderLayerToIndex: (String, Int) -> Unit = { _, _ -> },
    rippleEnabled: Boolean = false,
    onToggleRipple: () -> Unit = {},
    onSlip: (Long) -> Unit = {},
    onSetWorkAreaIn: () -> Unit = {},
    onSetWorkAreaOut: () -> Unit = {},
    onClearWorkArea: () -> Unit = {},
    onTimecodeClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    val pxPerSec = with(density) { (96.dp * timelineZoom).toPx() }
    val rowHeightPx = with(density) { TIMELINE_ROW_HEIGHT.toPx() }
    val horizontalScroll = rememberScrollState()
    var trackViewportWidthPx by remember { mutableIntStateOf(0) }
    var activeSnapGuideMs by remember { mutableStateOf<Long?>(null) }
    var draggingReorderId by remember { mutableStateOf<String?>(null) }

    val halfViewportPx = trackViewportWidthPx / 2f
    val halfViewportDp = with(density) { halfViewportPx.toDp() }
    val trackContentDp = ((composition.durationMs / 1000f) * 96f * timelineZoom).dp.coerceAtLeast(180.dp)
    val totalScrollWidthDp = halfViewportDp + trackContentDp + halfViewportDp

    val currentPxPerSec by rememberUpdatedState(pxPerSec)
    val currentDurationMs by rememberUpdatedState(composition.durationMs)
    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentZoom by rememberUpdatedState(timelineZoom)
    val currentOnZoomChange by rememberUpdatedState(onZoomChange)
    val frameStepMs = (1000L / composition.fps.coerceAtLeast(15)).coerceAtLeast(16L)

    LaunchedEffect(horizontalScroll) {
        snapshotFlow { horizontalScroll.value to horizontalScroll.isScrollInProgress }
            .distinctUntilChanged()
            .collect { (scrollVal, scrolling) ->
                if (scrolling && currentPxPerSec > 0f) {
                    val ms = ((scrollVal / currentPxPerSec) * 1000f).toLong().coerceIn(0L, currentDurationMs)
                    currentOnSeek(ms)
                }
            }
    }

    LaunchedEffect(playheadMs, pxPerSec, trackViewportWidthPx) {
        if (!horizontalScroll.isScrollInProgress && trackViewportWidthPx > 0) {
            val targetScroll = ((playheadMs / 1000f) * pxPerSec).roundToInt().coerceAtLeast(0)
            if (abs(horizontalScroll.value - targetScroll) > 1) {
                horizontalScroll.scrollTo(targetScroll)
            }
        }
    }

    val hasBeatNearPlayhead = remember(composition.beatMarkersMs, playheadMs) {
        composition.beatMarkersMs.any { abs(it - playheadMs) <= 45L }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(StudioPanel)
    ) {
        TimelineTransportBar(
            playheadMs = playheadMs,
            durationMs = composition.durationMs,
            fps = composition.fps,
            isPlaying = isPlaying,
            timelineZoom = timelineZoom,
            magneticSnapping = magneticSnapping,
            onTogglePlay = onTogglePlay,
            onSeek = onSeek,
            onZoomChange = onZoomChange,
            onToggleMagnetic = onToggleMagnetic,
            onTimecodeClick = onTimecodeClick
        )

        TimelineQuickPrecisionStrip(
            hasSelection = selectedLayerIds.isNotEmpty(),
            selectionCount = selectedLayerIds.size,
            hasBeatNearPlayhead = hasBeatNearPlayhead,
            beatCount = composition.beatMarkersMs.size,
            onSplitAtPlayhead = onSplitAtPlayhead,
            onToggleBeatMarker = onToggleBeatMarker,
            onJumpBeatMarker = onJumpBeatMarker,
            onToggleKeyframe = onToggleKeyframeAtPlayhead,
            onJumpKeyframe = onJumpKeyframe,
            onOpenSpeedShortcut = onOpenSpeedShortcut,
            onFreezeFrame = onFreezeFrame,
            onPreCompose = onPreCompose,
            rippleEnabled = rippleEnabled,
            workAreaActive = composition.workAreaOutMs >= 0L,
            onToggleRipple = onToggleRipple,
            onSlip = onSlip,
            onSetWorkAreaIn = onSetWorkAreaIn,
            onSetWorkAreaOut = onSetWorkAreaOut,
            onClearWorkArea = onClearWorkArea
        )

        MultiKeyframeBatchBar(
            selectedCount = selectedKeyframeIds.size,
            frameStepMs = frameStepMs,
            onShiftKeyframes = onShiftSelectedKeyframes,
            onDuplicateKeyframes = onDuplicateSelectedKeyframes,
            onApplyCurve = onApplyCurveToSelectedKeyframes,
            onDeleteKeyframes = onDeleteSelectedKeyframes,
            onClearSelection = onClearSelectedKeyframes
        )

        // Ruler + Layers Stack + Center Needle + Magnetic Guide
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(Unit) {
                    detectTwoFingerPinchZoom { zoomFactor ->
                        currentOnZoomChange(currentZoom * zoomFactor)
                    }
                }
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Adaptive Ruler Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(26.dp)
                        .background(StudioElevated.copy(alpha = 0.88f))
                ) {
                    Box(
                        modifier = Modifier
                            .width(TIMELINE_HEADER_WIDTH)
                            .fillMaxHeight()
                            .border(width = 0.5.dp, color = StudioHairline),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = "CAMADAS (${composition.layers.size})",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = TextSecondary,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .onSizeChanged { trackViewportWidthPx = it.width }
                            .horizontalScroll(horizontalScroll)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(totalScrollWidthDp)
                                .fillMaxHeight()
                                .pointerInput(halfViewportPx) {
                                    detectTapGestures { offset ->
                                        val ms = (((offset.x - halfViewportPx) / currentPxPerSec) * 1000f)
                                            .toLong()
                                            .coerceIn(0L, currentDurationMs)
                                        currentOnSeek(ms)
                                    }
                                }
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize().clipToBounds()) {
                                val tickConfig = TimelineSnapEngine.computeRulerTickStep(pxPerSec, composition.fps)
                                drawRect(
                                    color = CacheEmerald.copy(alpha = 0.7f),
                                    topLeft = Offset(halfViewportPx, 0f),
                                    size = androidx.compose.ui.geometry.Size(
                                        ((composition.durationMs / 1000f) * pxPerSec).coerceAtLeast(4f),
                                        2.5.dp.toPx()
                                    )
                                )

                                var tMs = 0L
                                val maxMs = composition.durationMs.coerceAtLeast(1000L)
                                while (tMs <= maxMs) {
                                    val x = halfViewportPx + (tMs / 1000f) * pxPerSec
                                    drawLine(TextMuted, Offset(x, 15.dp.toPx()), Offset(x, size.height), strokeWidth = 1.4f)
                                    val labelX = x + 3.dp.toPx()
                                    val labelY = 3.dp.toPx()
                                    if (labelX >= 0f && labelX < size.width - 2f && labelY < size.height) {
                                        val label = if (tickConfig.showFrames) {
                                            val secs = tMs / 1000L
                                            val fr = ((tMs % 1000L) * composition.fps / 1000L)
                                            if (fr == 0L) "${secs}s" else "${secs}.${fr}f"
                                        } else {
                                            "${tMs / 1000L}s"
                                        }
                                        drawText(
                                            textMeasurer = textMeasurer,
                                            text = label,
                                            style = TextStyle(color = TextSecondary, fontSize = 9.sp),
                                            topLeft = Offset(labelX, labelY),
                                            softWrap = false,
                                            maxLines = 1,
                                            overflow = TextOverflow.Visible
                                        )
                                    }
                                    if (tMs < maxMs) {
                                        val subStepMs = tickConfig.majorStepMs / tickConfig.minorSubdivisions.coerceAtLeast(1)
                                        for (sub in 1 until tickConfig.minorSubdivisions) {
                                            val subMs = tMs + sub * subStepMs
                                            if (subMs <= maxMs) {
                                                val sx = halfViewportPx + (subMs / 1000f) * pxPerSec
                                                drawLine(
                                                    TextMuted.copy(alpha = 0.36f),
                                                    Offset(sx, 19.dp.toPx()),
                                                    Offset(sx, size.height),
                                                    strokeWidth = 1f
                                                )
                                            }
                                        }
                                    }
                                    tMs += tickConfig.majorStepMs
                                }

                                // Beat markers on ruler
                                composition.beatMarkersMs.forEach { bMs ->
                                    val bx = halfViewportPx + (bMs / 1000f) * pxPerSec
                                    val pin = Path().apply {
                                        moveTo(bx, 11.dp.toPx())
                                        lineTo(bx + 4.dp.toPx(), 16.dp.toPx())
                                        lineTo(bx, 21.dp.toPx())
                                        lineTo(bx - 4.dp.toPx(), 16.dp.toPx())
                                        close()
                                    }
                                    drawPath(pin, BeatAmber)
                                }
                            }
                        }
                    }
                }

                // Layers List
                val orderedLayers = remember(composition.layers) { composition.layers.sortedByDescending { it.zIndex } }
                val assetsById = remember(assets) { assets.associateBy { it.id } }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(OledBlack)
                        .testTag("timeline_layers_list")
                ) {
                    itemsIndexed(orderedLayers, key = { _, layer -> layer.id }) { visualIndex, layer ->
                        val isSelected = layer.id in selectedLayerIds
                        val isExpanded = layer.id in expandedLayerIds
                        val isPickTarget = pickWhipSourceId != null && pickWhipSourceId != layer.id
                        val isReorderingThis = draggingReorderId == layer.id
                        val subTracks = remember(layer) { collectLayerSubTracks(layer) }

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(TIMELINE_ROW_HEIGHT)
                                    .background(
                                        when {
                                            isReorderingThis -> ElectricPurple.copy(alpha = 0.24f)
                                            isSelected -> StudioCard
                                            isPickTarget -> ElectricPurple.copy(alpha = 0.14f)
                                            else -> OledBlack
                                        }
                                    )
                                    .border(
                                        width = if (isReorderingThis) 1.dp else 0.5.dp,
                                        color = if (isReorderingThis) NeonCyan else StudioHairline
                                    ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TimelineLayerHeader(
                                    layer = layer,
                                    visualIndex = visualIndex,
                                    hasSubTracks = subTracks.isNotEmpty(),
                                    isSelected = isSelected,
                                    isExpanded = isExpanded,
                                    isPickWhipSource = pickWhipSourceId == layer.id,
                                    rowHeightPx = rowHeightPx,
                                    onSelect = { multi -> onSelectLayer(layer.id, multi) },
                                    onEnterPreComp = { layer.preCompId?.let(onEnterPreComp) },
                                    onToggleExpand = { onToggleExpandLayer(layer.id) },
                                    onToggleVisibility = { onToggleVisibility(layer.id) },
                                    onToggleLock = { onToggleLock(layer.id) },
                                    onToggleSolo = { onToggleSolo(layer.id) },
                                    onToggle3D = { onToggle3D(layer.id) },
                                    onStartPickWhip = { onStartPickWhip(layer.id) },
                                    onBeginDragReorder = {
                                        draggingReorderId = layer.id
                                        onBeginGesture()
                                    },
                                    onDragReorderToIndex = { targetIdx ->
                                        onReorderLayerToIndex(layer.id, targetIdx.coerceIn(0, orderedLayers.lastIndex))
                                    },
                                    onEndDragReorder = {
                                        draggingReorderId = null
                                        onEndGesture("Reordenar Camada")
                                    }
                                )

                                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                    TimelineClipTrackLane(
                                        layer = layer,
                                        composition = composition,
                                        asset = layer.assetId?.let { assetsById[it] },
                                        playheadMs = playheadMs,
                                        isSelected = isSelected,
                                        pxPerSec = pxPerSec,
                                        halfViewportPx = halfViewportPx,
                                        totalScrollWidthDp = totalScrollWidthDp,
                                        horizontalScroll = horizontalScroll,
                                        magneticSnapping = magneticSnapping,
                                        selectedKeyframeIds = selectedKeyframeIds,
                                        onSelectLayer = { onSelectLayer(layer.id, false) },
                                        onSeek = onSeek,
                                        onBeginGesture = onBeginGesture,
                                        onUpdateBounds = onUpdateBounds,
                                        onMoveKeyframeTime = onMoveKeyframeTime,
                                        onEndGesture = onEndGesture,
                                        onSnapGuideChange = { activeSnapGuideMs = it }
                                    )
                                }
                            }

                            TimelineSubTracksSection(
                                layer = layer,
                                composition = composition,
                                subTracks = subTracks,
                                isExpanded = isExpanded,
                                playheadMs = playheadMs,
                                pxPerSec = pxPerSec,
                                halfViewportPx = halfViewportPx,
                                totalScrollWidthDp = totalScrollWidthDp,
                                horizontalScroll = horizontalScroll,
                                magneticSnapping = magneticSnapping,
                                selectedKeyframeIds = selectedKeyframeIds,
                                onToggleSelectKeyframe = onToggleSelectKeyframe,
                                onSeek = onSeek,
                                onBeginGesture = onBeginGesture,
                                onMoveKeyframeTime = onMoveKeyframeTime,
                                onEndGesture = onEndGesture,
                                onSnapGuideChange = { activeSnapGuideMs = it }
                            )
                        }
                    }
                }
            }

            // Dead-Center CapCut White Razor Playhead Needle + Live Snap Guide Overlay
            if (trackViewportWidthPx > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = TIMELINE_HEADER_WIDTH)
                        .clipToBounds()
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("playhead_needle_handle")
                    ) {
                        val snapMs = activeSnapGuideMs
                        if (snapMs != null) {
                            val snapX = halfViewportPx + ((snapMs / 1000f) * pxPerSec) - horizontalScroll.value.toFloat()
                            if (snapX in 0f..size.width) {
                                drawLine(
                                    color = NeonCyan,
                                    start = Offset(snapX, 0f),
                                    end = Offset(snapX, size.height),
                                    strokeWidth = 1.8.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
                                )
                            }
                        }

                        val cx = size.width / 2f
                        val capTop = 1.dp.toPx()
                        val capMidY = 11.dp.toPx()
                        val capTipY = 18.dp.toPx()
                        val halfCapW = 6.5.dp.toPx()

                        drawLine(
                            color = NeonCyan.copy(alpha = 0.28f),
                            start = Offset(cx, capTipY - 2f),
                            end = Offset(cx, size.height),
                            strokeWidth = 5.dp.toPx()
                        )
                        drawLine(
                            color = Color.White,
                            start = Offset(cx, capTipY - 2f),
                            end = Offset(cx, size.height),
                            strokeWidth = 2.dp.toPx()
                        )
                        val cap = Path().apply {
                            moveTo(cx - halfCapW, capTop)
                            lineTo(cx + halfCapW, capTop)
                            lineTo(cx + halfCapW, capMidY)
                            lineTo(cx, capTipY)
                            lineTo(cx - halfCapW, capMidY)
                            close()
                        }
                        drawPath(path = cap, color = Color.White)
                        drawPath(path = cap, color = NeonCyan, style = Stroke(width = 1.3.dp.toPx()))
                    }

                    if (horizontalScroll.isScrollInProgress) {
                        Surface(
                            color = OledBlack.copy(alpha = 0.88f),
                            shape = RoundedCornerShape(99.dp),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .offset(y = 28.dp)
                                .border(0.7.dp, NeonCyan.copy(alpha = 0.7f), RoundedCornerShape(99.dp))
                        ) {
                            Text(
                                text = formatTimelineTimecode(playheadMs, composition.fps),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonCyan,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

fun formatTimecode(ms: Long, fps: Int): String = formatTimelineTimecode(ms, fps)

/**
 * Only intercepts multi-touch (2+ fingers) zoom gestures so single-finger scroll,
 * keyframe taps, trim handles, and clip drags pass through completely uninterrupted.
 */
private suspend fun PointerInputScope.detectTwoFingerPinchZoom(
    onZoomFactor: (Float) -> Unit
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            val pressedCount = event.changes.count { it.pressed }
            if (pressedCount >= 2) {
                val zoomChange = event.calculateZoom()
                if (abs(zoomChange - 1f) > 0.005f) {
                    onZoomFactor(zoomChange)
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                }
            }
        } while (event.changes.any { it.pressed })
    }
}
