package com.example.ui.components.timeline

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AssetItem
import com.example.model.CompositionNode
import com.example.model.LayerNode
import com.example.model.LayerType
import com.example.ui.theme.BeatAmber
import com.example.ui.theme.ElectricPurple
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.OledBlack
import com.example.ui.theme.StudioHairline
import com.example.ui.theme.StudioPanel
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TrackAudioColor
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun TimelineClipTrackLane(
    layer: LayerNode,
    composition: CompositionNode,
    asset: AssetItem?,
    playheadMs: Long,
    isSelected: Boolean,
    pxPerSec: Float,
    halfViewportPx: Float,
    totalScrollWidthDp: Dp,
    horizontalScroll: ScrollState,
    magneticSnapping: Boolean,
    selectedKeyframeIds: Set<String>,
    onSelectLayer: () -> Unit,
    onSeek: (Long) -> Unit,
    onBeginGesture: () -> Unit,
    onUpdateBounds: (String, Long, Long) -> Unit,
    onMoveKeyframeTime: (String, SubTrackTarget, String, Long) -> Unit,
    onEndGesture: (String) -> Unit,
    onSnapGuideChange: (Long?) -> Unit
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val trackColor = layerTypeColor(layer.type)

    val latestLayer by rememberUpdatedState(layer)
    val latestComp by rememberUpdatedState(composition)
    val latestPlayhead by rememberUpdatedState(playheadMs)
    val latestMagnetic by rememberUpdatedState(magneticSnapping)
    val latestPxPerSec by rememberUpdatedState(pxPerSec)

    Box(
        modifier = Modifier
            .fillMaxHeight()
            .clipToBounds()
            .scrollable(
                state = horizontalScroll,
                orientation = Orientation.Horizontal,
                reverseDirection = true
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .wrapContentWidth(unbounded = true, align = Alignment.Start)
                .width(totalScrollWidthDp)
                .graphicsLayer { translationX = -horizontalScroll.value.toFloat() }
        ) {
            val startPx = halfViewportPx + ((layer.inMs / 1000f) * pxPerSec)
            val clipDurationMs = (layer.outMs - layer.inMs).coerceAtLeast(200L)
            val widthPx = (clipDurationMs / 1000f) * pxPerSec
            val widthDp = with(density) { widthPx.toDp() }
            val audioWaveform = if (layer.type == LayerType.AUDIO) asset?.waveform.orEmpty() else emptyList()

            val showFilmstrip = layer.type == LayerType.VIDEO || layer.type == LayerType.IMAGE
            val tileCount = if (showFilmstrip) (widthPx / with(density) { 44.dp.toPx() }).roundToInt().coerceIn(1, 24) else 0
            val filmstripFrames = if (showFilmstrip) {
                rememberFilmstripFrames(
                    asset = asset,
                    clipDurationMs = clipDurationMs,
                    startOffsetMs = layer.startOffsetMs,
                    speedMultiplier = layer.speedMultiplier,
                    tileCount = tileCount
                )
            } else emptyList()

            Box(
                modifier = Modifier
                    .offset { IntOffset(startPx.roundToInt(), 0) }
                    .width(widthDp)
                    .padding(vertical = 5.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                trackColor.copy(alpha = 0.42f),
                                trackColor.copy(alpha = 0.22f)
                            )
                        )
                    )
                    .border(
                        width = if (isSelected) 1.6.dp else 0.7.dp,
                        color = if (isSelected) Color.White else trackColor.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { onSelectLayer() }
                    .pointerInput(layer.id) {
                        var rawDragInMs = 0L
                        var lastSnapGuide: Long? = null
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                if (!latestLayer.locked) {
                                    rawDragInMs = latestLayer.inMs
                                    lastSnapGuide = null
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onBeginGesture()
                                }
                            },
                            onDragEnd = {
                                onSnapGuideChange(null)
                                onEndGesture("Mover Clipe")
                            },
                            onDragCancel = {
                                onSnapGuideChange(null)
                                onEndGesture("Mover Clipe")
                            },
                            onDrag = { change, dragAmount ->
                                if (latestLayer.locked) return@detectDragGesturesAfterLongPress
                                change.consume()
                                val dMs = ((dragAmount.x / latestPxPerSec) * 1000f).toLong()
                                rawDragInMs += dMs
                                val dur = (latestLayer.outMs - latestLayer.inMs).coerceAtLeast(200L)
                                val snap = TimelineSnapEngine.snapClipMove(
                                    proposedInMs = rawDragInMs,
                                    clipDurationMs = dur,
                                    composition = latestComp,
                                    layerId = latestLayer.id,
                                    playheadMs = latestPlayhead,
                                    pxPerSec = latestPxPerSec,
                                    enabled = latestMagnetic
                                )
                                if (snap.snapGuideMs != null && snap.snapGuideMs != lastSnapGuide) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                lastSnapGuide = snap.snapGuideMs
                                onSnapGuideChange(snap.snapGuideMs)
                                onUpdateBounds(latestLayer.id, snap.snappedMs, snap.snappedMs + dur)
                            }
                        )
                    }
            ) {
                // Visual Filmstrip Strip for Video / Image
                if (showFilmstrip) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        for (i in 0 until tileCount.coerceAtLeast(1)) {
                            val bmp = filmstripFrames.getOrNull(i)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .border(0.4.dp, OledBlack.copy(alpha = 0.45f))
                            ) {
                                if (bmp != null) {
                                    Image(
                                        bitmap = bmp,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val perfH = 3.dp.toPx()
                                        drawRect(Color.White.copy(alpha = 0.14f), Offset(3f, 2f), androidx.compose.ui.geometry.Size(5.dp.toPx(), perfH))
                                        drawRect(Color.White.copy(alpha = 0.14f), Offset(3f, size.height - perfH - 2f), androidx.compose.ui.geometry.Size(5.dp.toPx(), perfH))
                                    }
                                }
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(OledBlack.copy(alpha = 0.25f))
                    )
                }

                // Audio Mirrored Waveform + Fade Curve Preview
                if (audioWaveform.isNotEmpty()) {
                    val fadeInRatio = ((layer.audioSpec?.fadeInMs ?: 0L).toFloat() / clipDurationMs).coerceIn(0f, 0.45f)
                    val fadeOutRatio = ((layer.audioSpec?.fadeOutMs ?: 0L).toFloat() / clipDurationMs).coerceIn(0f, 0.45f)
                    Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 4.dp)) {
                        val barW = (size.width / audioWaveform.size).coerceAtLeast(2f)
                        audioWaveform.forEachIndexed { idx, amp ->
                            val frac = idx.toFloat() / audioWaveform.size.coerceAtLeast(1)
                            val env = when {
                                fadeInRatio > 0f && frac < fadeInRatio -> (frac / fadeInRatio).coerceIn(0.08f, 1f)
                                fadeOutRatio > 0f && frac > 1f - fadeOutRatio -> ((1f - frac) / fadeOutRatio).coerceIn(0.08f, 1f)
                                else -> 1f
                            }
                            val bh = (amp * env * size.height).coerceAtLeast(2f)
                            val x = idx * barW
                            drawLine(
                                TrackAudioColor,
                                Offset(x, size.height / 2f - bh / 2f),
                                Offset(x, size.height / 2f + bh / 2f),
                                strokeWidth = (barW * 0.65f).coerceAtLeast(1.5f)
                            )
                        }
                        if (fadeInRatio > 0.01f) {
                            val pIn = Path().apply {
                                moveTo(0f, size.height)
                                lineTo(0f, 0f)
                                lineTo(size.width * fadeInRatio, 0f)
                                close()
                            }
                            drawPath(pIn, OledBlack.copy(alpha = 0.38f))
                        }
                        if (fadeOutRatio > 0.01f) {
                            val pOut = Path().apply {
                                moveTo(size.width, size.height)
                                lineTo(size.width, 0f)
                                lineTo(size.width * (1f - fadeOutRatio), 0f)
                                close()
                            }
                            drawPath(pOut, OledBlack.copy(alpha = 0.38f))
                        }
                    }
                }

                // Compact Clip Name & Speed/Freeze Badge
                if (widthDp >= 52.dp) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(horizontal = if (isSelected) 20.dp else 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = layer.name,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary.copy(alpha = 0.92f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (layer.speedMultiplier == 0f) {
                            Text(
                                text = " • Congelado",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonCyan,
                                maxLines = 1
                            )
                        } else if (layer.speedRamp != null || abs(layer.speedMultiplier - 1f) > 0.05f) {
                            val badge = if (layer.speedRamp != null) " • Ramp"
                            else " • ${String.format(Locale.US, "%.1f", layer.speedMultiplier)}x"
                            Text(
                                text = badge,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = BeatAmber,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Direct Draggable Keyframe Diamonds along the clip track
                val markers = remember(layer) { collectAllKeyframeMarkers(layer) }
                val distinctMarkers = remember(markers) { markers.groupBy { it.localTimeMs } }
                distinctMarkers.forEach { (kfLocalMs, refs) ->
                    val kfCompMs = layer.inMs + (kfLocalMs / layer.speedMultiplier.coerceAtLeast(0.05f)).toLong()
                    val relPx = ((kfCompMs - layer.inMs) / 1000f) * pxPerSec
                    val anySelected = refs.any { it.keyframeId in selectedKeyframeIds }
                    var rawCompMs by remember(refs.first().keyframeId) { mutableLongStateOf(kfCompMs) }
                    Icon(
                        imageVector = Icons.Rounded.Diamond,
                        contentDescription = "Keyframe em ${kfCompMs}ms",
                        tint = if (anySelected) BeatAmber else NeonCyan,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .offset { IntOffset((relPx - 6f).roundToInt(), (-3).dp.roundToPx()) }
                            .size(12.dp)
                            .clickable { onSeek(kfCompMs) }
                            .pointerInput(layer.id, kfLocalMs) {
                                var lastGuide: Long? = null
                                detectDragGestures(
                                    onDragStart = {
                                        if (!latestLayer.locked) {
                                            rawCompMs = latestLayer.inMs + (kfLocalMs / latestLayer.speedMultiplier.coerceAtLeast(0.05f)).toLong()
                                            lastGuide = null
                                            onBeginGesture()
                                        }
                                    },
                                    onDragEnd = {
                                        onSnapGuideChange(null)
                                        onEndGesture("Mover Keyframes")
                                    },
                                    onDragCancel = {
                                        onSnapGuideChange(null)
                                        onEndGesture("Mover Keyframes")
                                    },
                                    onDrag = { change, dragAmount ->
                                        if (latestLayer.locked) return@detectDragGestures
                                        change.consume()
                                        val dMs = ((dragAmount.x / latestPxPerSec) * 1000f).toLong()
                                        rawCompMs += dMs
                                        val targets = TimelineSnapEngine.collectKeyframeSnapTargets(
                                            composition = latestComp,
                                            layer = latestLayer,
                                            excludeKeyframeId = refs.first().keyframeId,
                                            playheadMs = latestPlayhead
                                        )
                                        val snap = TimelineSnapEngine.snapTimePoint(
                                            candidateMs = rawCompMs,
                                            targets = targets,
                                            pxPerSec = latestPxPerSec,
                                            enabled = latestMagnetic,
                                            minMs = latestLayer.inMs,
                                            maxMs = latestLayer.outMs
                                        )
                                        if (snap.snapGuideMs != null && snap.snapGuideMs != lastGuide) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                        lastGuide = snap.snapGuideMs
                                        onSnapGuideChange(snap.snapGuideMs)
                                        refs.forEach { ref ->
                                            onMoveKeyframeTime(latestLayer.id, ref.target, ref.keyframeId, snap.snappedMs)
                                        }
                                    }
                                )
                            }
                    )
                }

                // CapCut Left & Right White Trim Handles with Magnetic Snapping
                if (isSelected && !layer.locked) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .width(18.dp)
                            .fillMaxHeight()
                            .background(Color.White.copy(alpha = 0.94f))
                            .pointerInput(layer.id) {
                                var rawIn = 0L
                                var lastGuide: Long? = null
                                detectDragGestures(
                                    onDragStart = {
                                        rawIn = latestLayer.inMs
                                        lastGuide = null
                                        onBeginGesture()
                                    },
                                    onDragEnd = {
                                        onSnapGuideChange(null)
                                        onEndGesture("Aparar Início")
                                    },
                                    onDragCancel = {
                                        onSnapGuideChange(null)
                                        onEndGesture("Aparar Início")
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        val dMs = ((dragAmount.x / latestPxPerSec) * 1000f).toLong()
                                        rawIn += dMs
                                        val targets = TimelineSnapEngine.collectClipSnapTargets(latestComp, latestLayer.id, latestPlayhead)
                                        val snap = TimelineSnapEngine.snapTimePoint(
                                            candidateMs = rawIn,
                                            targets = targets,
                                            pxPerSec = latestPxPerSec,
                                            enabled = latestMagnetic,
                                            minMs = 0L,
                                            maxMs = (latestLayer.outMs - 200L).coerceAtLeast(0L)
                                        )
                                        if (snap.snapGuideMs != null && snap.snapGuideMs != lastGuide) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                        lastGuide = snap.snapGuideMs
                                        onSnapGuideChange(snap.snapGuideMs)
                                        onUpdateBounds(latestLayer.id, snap.snappedMs, latestLayer.outMs)
                                    }
                                )
                            }
                            .testTag("trim_left_${layer.id}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(modifier = Modifier.width(2.5.dp).height(16.dp).background(OledBlack, RoundedCornerShape(2.dp)))
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .width(18.dp)
                            .fillMaxHeight()
                            .background(Color.White.copy(alpha = 0.94f))
                            .pointerInput(layer.id) {
                                var rawOut = 0L
                                var lastGuide: Long? = null
                                detectDragGestures(
                                    onDragStart = {
                                        rawOut = latestLayer.outMs
                                        lastGuide = null
                                        onBeginGesture()
                                    },
                                    onDragEnd = {
                                        onSnapGuideChange(null)
                                        onEndGesture("Aparar Fim")
                                    },
                                    onDragCancel = {
                                        onSnapGuideChange(null)
                                        onEndGesture("Aparar Fim")
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        val dMs = ((dragAmount.x / latestPxPerSec) * 1000f).toLong()
                                        rawOut += dMs
                                        val targets = TimelineSnapEngine.collectClipSnapTargets(latestComp, latestLayer.id, latestPlayhead)
                                        val snap = TimelineSnapEngine.snapTimePoint(
                                            candidateMs = rawOut,
                                            targets = targets,
                                            pxPerSec = latestPxPerSec,
                                            enabled = latestMagnetic,
                                            minMs = latestLayer.inMs + 200L,
                                            maxMs = latestComp.durationMs
                                        )
                                        if (snap.snapGuideMs != null && snap.snapGuideMs != lastGuide) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                        lastGuide = snap.snapGuideMs
                                        onSnapGuideChange(snap.snapGuideMs)
                                        onUpdateBounds(latestLayer.id, latestLayer.inMs, snap.snappedMs)
                                    }
                                )
                            }
                            .testTag("trim_right_${layer.id}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(modifier = Modifier.width(2.5.dp).height(16.dp).background(OledBlack, RoundedCornerShape(2.dp)))
                    }
                }
            }
        }
    }
}

@Composable
fun TimelineSubTracksSection(
    layer: LayerNode,
    composition: CompositionNode,
    subTracks: List<SubTrackEntry>,
    isExpanded: Boolean,
    playheadMs: Long,
    pxPerSec: Float,
    halfViewportPx: Float,
    totalScrollWidthDp: Dp,
    horizontalScroll: ScrollState,
    magneticSnapping: Boolean,
    selectedKeyframeIds: Set<String>,
    onToggleSelectKeyframe: (String) -> Unit,
    onSeek: (Long) -> Unit,
    onBeginGesture: () -> Unit,
    onMoveKeyframeTime: (String, SubTrackTarget, String, Long) -> Unit,
    onEndGesture: (String) -> Unit,
    onSnapGuideChange: (Long?) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val latestLayer by rememberUpdatedState(layer)
    val latestComp by rememberUpdatedState(composition)
    val latestPlayhead by rememberUpdatedState(playheadMs)
    val latestMagnetic by rememberUpdatedState(magneticSnapping)
    val latestPxPerSec by rememberUpdatedState(pxPerSec)

    AnimatedVisibility(
        visible = isExpanded && subTracks.isNotEmpty(),
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            subTracks.forEach { entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .background(StudioPanel.copy(alpha = 0.84f))
                        .border(0.5.dp, StudioHairline),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(TIMELINE_HEADER_WIDTH)
                            .fillMaxHeight()
                            .padding(start = 14.dp, end = 4.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = "↳ ${entry.label}",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = if (!entry.property.expression.isNullOrBlank()) ElectricPurple else TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clipToBounds()
                            .scrollable(
                                state = horizontalScroll,
                                orientation = Orientation.Horizontal,
                                reverseDirection = true
                            ),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .wrapContentWidth(unbounded = true, align = Alignment.Start)
                                .width(totalScrollWidthDp)
                                .graphicsLayer { translationX = -horizontalScroll.value.toFloat() }
                        ) {
                            entry.property.keyframes.forEach { kf ->
                                val kfCompMs = layer.inMs + (kf.timeMs / layer.speedMultiplier.coerceAtLeast(0.05f)).toLong()
                                val kfPx = halfViewportPx + (kfCompMs / 1000f) * pxPerSec
                                val isKfSelected = kf.id in selectedKeyframeIds
                                var rawCompMs by remember(kf.id) { mutableLongStateOf(kfCompMs) }
                                Icon(
                                    imageVector = Icons.Rounded.Diamond,
                                    contentDescription = "${entry.label} em ${kfCompMs}ms",
                                    tint = if (isKfSelected) BeatAmber else NeonCyan,
                                    modifier = Modifier
                                        .align(Alignment.CenterStart)
                                        .offset { IntOffset((kfPx - 7f).roundToInt(), 0) }
                                        .size(15.dp)
                                        .clickable {
                                            onToggleSelectKeyframe(kf.id)
                                            onSeek(kfCompMs)
                                        }
                                        .pointerInput(layer.id, kf.id) {
                                            var lastGuide: Long? = null
                                            detectDragGestures(
                                                onDragStart = {
                                                    if (!latestLayer.locked) {
                                                        rawCompMs = latestLayer.inMs + (kf.timeMs / latestLayer.speedMultiplier.coerceAtLeast(0.05f)).toLong()
                                                        lastGuide = null
                                                        onBeginGesture()
                                                    }
                                                },
                                                onDragEnd = {
                                                    onSnapGuideChange(null)
                                                    onEndGesture("Mover Keyframe")
                                                },
                                                onDragCancel = {
                                                    onSnapGuideChange(null)
                                                    onEndGesture("Mover Keyframe")
                                                },
                                                onDrag = { change, dragAmount ->
                                                    if (latestLayer.locked) return@detectDragGestures
                                                    change.consume()
                                                    val dMs = ((dragAmount.x / latestPxPerSec) * 1000f).toLong()
                                                    rawCompMs += dMs
                                                    val targets = TimelineSnapEngine.collectKeyframeSnapTargets(
                                                        composition = latestComp,
                                                        layer = latestLayer,
                                                        excludeKeyframeId = kf.id,
                                                        playheadMs = latestPlayhead
                                                    )
                                                    val snap = TimelineSnapEngine.snapTimePoint(
                                                        candidateMs = rawCompMs,
                                                        targets = targets,
                                                        pxPerSec = latestPxPerSec,
                                                        enabled = latestMagnetic,
                                                        minMs = latestLayer.inMs,
                                                        maxMs = latestLayer.outMs
                                                    )
                                                    if (snap.snapGuideMs != null && snap.snapGuideMs != lastGuide) {
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    }
                                                    lastGuide = snap.snapGuideMs
                                                    onSnapGuideChange(snap.snapGuideMs)
                                                    onMoveKeyframeTime(latestLayer.id, entry.target, kf.id, snap.snappedMs)
                                                }
                                            )
                                        }
                                        .testTag("subtrack_kf_${kf.id}")
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
