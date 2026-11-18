package com.example.ui.components.timeline

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.CopyAll
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.FolderCopy
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BezierCurve
import com.example.model.LayerNode
import com.example.model.LayerType
import com.example.ui.theme.AlertRed
import com.example.ui.theme.BeatAmber
import com.example.ui.theme.ElectricPurple
import com.example.ui.theme.ElectricPurpleLight
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.OledBlack
import com.example.ui.theme.StudioCard
import com.example.ui.theme.StudioElevated
import com.example.ui.theme.StudioHairline
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TrackAdjustColor
import com.example.ui.theme.TrackAudioColor
import com.example.ui.theme.TrackCameraColor
import com.example.ui.theme.TrackImageColor
import com.example.ui.theme.TrackNullColor
import com.example.ui.theme.TrackPreCompColor
import com.example.ui.theme.TrackTextColor
import com.example.ui.theme.TrackVectorColor
import com.example.ui.theme.TrackVideoColor
import java.util.Locale
import kotlin.math.abs

val TIMELINE_HEADER_WIDTH = 124.dp
val TIMELINE_ROW_HEIGHT = 50.dp

@Composable
fun TimelineTransportBar(
    playheadMs: Long,
    durationMs: Long,
    fps: Int,
    isPlaying: Boolean,
    timelineZoom: Float,
    magneticSnapping: Boolean,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onZoomChange: (Float) -> Unit,
    onToggleMagnetic: () -> Unit,
    onTimecodeClick: () -> Unit = {}
) {
    val frameStepMs = (1000L / fps.coerceAtLeast(15)).coerceAtLeast(16L)
    val playBtnColor by animateColorAsState(
        targetValue = if (isPlaying) ElectricPurple else NeonCyan,
        label = "play_btn_color"
    )
    val playBtnScale by animateFloatAsState(
        targetValue = if (isPlaying) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "play_btn_scale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .background(OledBlack.copy(alpha = 0.94f))
            .border(0.5.dp, StudioHairline)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = formatTimelineTimecode(playheadMs, fps),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onTimecodeClick)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
                    .testTag("timeline_timecode")
            )
            Text(
                text = " / ${formatTimelineTimecode(durationMs, fps)}",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            IconButton(
                onClick = { onSeek(0L) },
                modifier = Modifier
                    .size(32.dp)
                    .testTag("rewind_to_start_btn")
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipPrevious,
                    contentDescription = "Voltar ao início",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(
                onClick = { onSeek((playheadMs - frameStepMs).coerceAtLeast(0L)) },
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    contentDescription = "Voltar 1 quadro",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(
                onClick = onTogglePlay,
                modifier = Modifier
                    .size(36.dp)
                    .scale(playBtnScale)
                    .clip(CircleShape)
                    .background(playBtnColor)
                    .testTag("play_pause_button")
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pausar" else "Reproduzir",
                    tint = OledBlack,
                    modifier = Modifier.size(22.dp)
                )
            }
            IconButton(
                onClick = { onSeek((playheadMs + frameStepMs).coerceAtMost(durationMs)) },
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = "Avançar 1 quadro",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Surface(
                color = StudioElevated,
                shape = RoundedCornerShape(99.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .clickable {
                        val nextZoom = when {
                            timelineZoom < 0.9f -> 1.2f
                            timelineZoom < 2.2f -> 2.8f
                            timelineZoom < 5.0f -> 6.5f
                            timelineZoom < 9.0f -> 10.5f
                            else -> 0.6f
                        }
                        onZoomChange(nextZoom)
                    }
                    .testTag("timeline_zoom_pill")
            ) {
                Text(
                    text = if (timelineZoom >= 5f) "${String.format(Locale.US, "%.0f", timelineZoom)}x (Q)"
                    else "${String.format(Locale.US, "%.1f", timelineZoom)}x",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (timelineZoom >= 4f) NeonCyan else TextSecondary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            IconButton(
                onClick = onToggleMagnetic,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (magneticSnapping) NeonCyan.copy(alpha = 0.16f) else Color.Transparent)
                    .testTag("timeline_magnet_btn")
            ) {
                Icon(
                    imageVector = Icons.Rounded.CenterFocusStrong,
                    contentDescription = "Alinhamento magnético",
                    tint = if (magneticSnapping) NeonCyan else TextMuted,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

@Composable
fun TimelineQuickPrecisionStrip(
    hasSelection: Boolean,
    selectionCount: Int,
    hasBeatNearPlayhead: Boolean,
    beatCount: Int,
    onSplitAtPlayhead: () -> Unit,
    onToggleBeatMarker: () -> Unit,
    onJumpBeatMarker: (Int) -> Unit,
    onToggleKeyframe: () -> Unit,
    onJumpKeyframe: (Int) -> Unit,
    onOpenSpeedShortcut: () -> Unit,
    onFreezeFrame: () -> Unit,
    onPreCompose: () -> Unit,
    rippleEnabled: Boolean = false,
    workAreaActive: Boolean = false,
    onToggleRipple: () -> Unit = {},
    onSlip: (Long) -> Unit = {},
    onSetWorkAreaIn: () -> Unit = {},
    onSetWorkAreaOut: () -> Unit = {},
    onClearWorkArea: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(StudioElevated.copy(alpha = 0.7f))
            .border(0.5.dp, StudioHairline)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        QuickStripPill(
            icon = Icons.Rounded.ContentCut,
            label = "Dividir",
            accent = if (hasSelection) TextPrimary else TextMuted,
            enabled = hasSelection,
            tag = "timeline_quick_split_btn",
            onClick = onSplitAtPlayhead
        )
        QuickStripPill(
            icon = Icons.Rounded.ContentCut,
            label = if (rippleEnabled) "Ripple ON" else "Ripple",
            accent = if (rippleEnabled) NeonCyan else TextPrimary,
            enabled = true,
            tag = "timeline_ripple_btn",
            onClick = onToggleRipple
        )
        QuickStripPill(
            icon = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
            label = "Slip −",
            accent = if (hasSelection) TextPrimary else TextMuted,
            enabled = hasSelection,
            tag = "timeline_slip_minus_btn",
            onClick = { onSlip(-100L) }
        )
        QuickStripPill(
            icon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            label = "Slip +",
            accent = if (hasSelection) TextPrimary else TextMuted,
            enabled = hasSelection,
            tag = "timeline_slip_plus_btn",
            onClick = { onSlip(100L) }
        )
        QuickStripPill(
            icon = Icons.Rounded.BookmarkAdd,
            label = if (workAreaActive) "B / N ✓" else "B início",
            accent = if (workAreaActive) NeonCyan else TextPrimary,
            enabled = true,
            tag = "timeline_work_area_in_btn",
            onClick = onSetWorkAreaIn
        )
        QuickStripPill(
            icon = Icons.Rounded.BookmarkAdd,
            label = "N fim",
            accent = if (workAreaActive) NeonCyan else TextPrimary,
            enabled = true,
            tag = "timeline_work_area_out_btn",
            onClick = onSetWorkAreaOut
        )
        if (workAreaActive) {
            QuickStripPill(
                icon = Icons.Rounded.Delete,
                label = "Limpar área",
                accent = TextMuted,
                enabled = true,
                tag = "timeline_work_area_clear_btn",
                onClick = onClearWorkArea
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (hasBeatNearPlayhead) BeatAmber.copy(alpha = 0.22f) else StudioCard)
                .border(
                    0.5.dp,
                    if (hasBeatNearPlayhead) BeatAmber.copy(alpha = 0.65f) else StudioHairline,
                    RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                contentDescription = "Batida anterior",
                tint = if (beatCount > 0) BeatAmber else TextMuted,
                modifier = Modifier
                    .size(18.dp)
                    .clickable(enabled = beatCount > 0) { onJumpBeatMarker(-1) }
                    .testTag("timeline_beat_prev_btn")
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier
                    .clickable { onToggleBeatMarker() }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
                    .testTag("timeline_beat_toggle_btn")
            ) {
                Icon(
                    imageVector = Icons.Rounded.BookmarkAdd,
                    contentDescription = "Marcar batida",
                    tint = BeatAmber,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = if (hasBeatNearPlayhead) "Batida ✓" else "+ Batida",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (hasBeatNearPlayhead) BeatAmber else TextPrimary,
                    fontWeight = FontWeight.Medium
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = "Próxima batida",
                tint = if (beatCount > 0) BeatAmber else TextMuted,
                modifier = Modifier
                    .size(18.dp)
                    .clickable(enabled = beatCount > 0) { onJumpBeatMarker(1) }
                    .testTag("timeline_beat_next_btn")
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(StudioCard)
                .border(0.5.dp, StudioHairline, RoundedCornerShape(8.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                contentDescription = "Keyframe anterior",
                tint = if (hasSelection) NeonCyan else TextMuted,
                modifier = Modifier
                    .size(18.dp)
                    .clickable(enabled = hasSelection) { onJumpKeyframe(-1) }
                    .testTag("timeline_kf_prev_btn")
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier
                    .clickable(enabled = hasSelection) { onToggleKeyframe() }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
                    .testTag("timeline_kf_toggle_btn")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    tint = if (hasSelection) NeonCyan else TextMuted,
                    modifier = Modifier.size(12.dp)
                )
                Icon(
                    imageVector = Icons.Rounded.Diamond,
                    contentDescription = "Adicionar ou remover keyframe",
                    tint = if (hasSelection) NeonCyan else TextMuted,
                    modifier = Modifier.size(13.dp)
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = "Próximo keyframe",
                tint = if (hasSelection) NeonCyan else TextMuted,
                modifier = Modifier
                    .size(18.dp)
                    .clickable(enabled = hasSelection) { onJumpKeyframe(1) }
                    .testTag("timeline_kf_next_btn")
            )
        }

        QuickStripPill(
            icon = Icons.Rounded.Speed,
            label = "Velocidade",
            accent = if (hasSelection) ElectricPurpleLight else TextMuted,
            enabled = hasSelection,
            tag = "timeline_speed_btn",
            onClick = onOpenSpeedShortcut
        )
        QuickStripPill(
            icon = Icons.Rounded.AcUnit,
            label = "Congelar",
            accent = if (hasSelection) NeonCyan else TextMuted,
            enabled = hasSelection,
            tag = "timeline_freeze_btn",
            onClick = onFreezeFrame
        )
        if (selectionCount >= 1) {
            QuickStripPill(
                icon = Icons.Rounded.FolderZip,
                label = if (selectionCount > 1) "Pré-Comp ($selectionCount)" else "Pré-Comp",
                accent = TrackPreCompColor,
                enabled = true,
                tag = "timeline_precomp_btn",
                onClick = onPreCompose
            )
        }
    }
}

@Composable
private fun QuickStripPill(
    icon: ImageVector,
    label: String,
    accent: Color,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(StudioCard)
            .border(0.5.dp, if (enabled) accent.copy(alpha = 0.35f) else StudioHairline, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag(tag)
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = accent, modifier = Modifier.size(14.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = accent, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun MultiKeyframeBatchBar(
    selectedCount: Int,
    frameStepMs: Long,
    onShiftKeyframes: (Long) -> Unit,
    onDuplicateKeyframes: (Long) -> Unit,
    onApplyCurve: (BezierCurve) -> Unit,
    onDeleteKeyframes: () -> Unit,
    onClearSelection: () -> Unit
) {
    AnimatedVisibility(
        visible = selectedCount > 0,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ElectricPurple.copy(alpha = 0.18f))
                .border(0.5.dp, ElectricPurple.copy(alpha = 0.45f))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 4.dp)
                .testTag("multi_keyframe_toolbar"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "$selectedCount kf",
                style = MaterialTheme.typography.labelSmall,
                color = BeatAmber,
                fontWeight = FontWeight.Bold
            )
            BatchChip("-1q") { onShiftKeyframes(-frameStepMs) }
            BatchChip("+1q") { onShiftKeyframes(frameStepMs) }
            BatchChip("-100ms") { onShiftKeyframes(-100L) }
            BatchChip("+100ms") { onShiftKeyframes(100L) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(StudioElevated)
                    .clickable { onDuplicateKeyframes(120L) }
                    .padding(horizontal = 7.dp, vertical = 3.dp)
                    .testTag("batch_kf_duplicate_btn")
            ) {
                Icon(Icons.Rounded.CopyAll, null, tint = NeonCyan, modifier = Modifier.size(13.dp))
                Text("Duplicar", style = MaterialTheme.typography.labelSmall, color = NeonCyan)
            }
            BatchChip("Ease Out", TextPrimary) {
                onApplyCurve(BezierCurve(0.16f, 1f, 0.3f, 1f))
            }
            Icon(
                imageVector = Icons.Rounded.Delete,
                contentDescription = "Excluir keyframes selecionados",
                tint = AlertRed,
                modifier = Modifier
                    .size(19.dp)
                    .clickable { onDeleteKeyframes() }
            )
            Text(
                text = "Limpar",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                modifier = Modifier.clickable { onClearSelection() }
            )
        }
    }
}

@Composable
private fun BatchChip(label: String, tint: Color = NeonCyan, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = tint,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(StudioElevated)
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 3.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TimelineLayerHeader(
    layer: LayerNode,
    visualIndex: Int,
    hasSubTracks: Boolean,
    isSelected: Boolean,
    isExpanded: Boolean,
    isPickWhipSource: Boolean,
    rowHeightPx: Float,
    onSelect: (Boolean) -> Unit,
    onEnterPreComp: () -> Unit,
    onToggleExpand: () -> Unit,
    onToggleVisibility: () -> Unit,
    onToggleLock: () -> Unit,
    onToggleSolo: () -> Unit,
    onToggle3D: () -> Unit,
    onStartPickWhip: () -> Unit,
    onBeginDragReorder: () -> Unit,
    onDragReorderToIndex: (Int) -> Unit,
    onEndDragReorder: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val trackColor = layerTypeColor(layer.type)
    val latestVisualIndex by rememberUpdatedState(visualIndex)
    val latestRowHeightPx by rememberUpdatedState(rowHeightPx)

    Column(
        modifier = Modifier
            .width(TIMELINE_HEADER_WIDTH)
            .fillMaxHeight()
            .border(0.5.dp, StudioHairline)
            .combinedClickable(
                onClick = {
                    if (layer.type == LayerType.PRE_COMP && isSelected && layer.preCompId != null) {
                        onEnterPreComp()
                    } else {
                        onSelect(false)
                    }
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSelect(true)
                }
            )
            .padding(start = 4.dp, end = 3.dp, top = 4.dp, bottom = 3.dp)
            .testTag("layer_row_${layer.id}"),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.5.dp)
                    .height(22.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(trackColor)
            )
            if (hasSubTracks) {
                Icon(
                    imageVector = if (isExpanded) Icons.Rounded.KeyboardArrowDown else Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = "Expandir sub-trilhas",
                    tint = NeonCyan,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { onToggleExpand() }
                        .testTag("expand_layer_${layer.id}")
                )
            } else {
                Spacer(modifier = Modifier.width(4.dp))
            }
            Icon(
                imageVector = layerTypeIcon(layer.type),
                contentDescription = layer.type.labelPt,
                tint = trackColor,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = if (layer.parentId != null) "↳ ${layer.name}" else layer.name,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = when {
                    !layer.visible -> TextMuted
                    layer.parentId != null -> ElectricPurpleLight
                    else -> TextPrimary
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            var dragAccumY = 0f
            Icon(
                imageVector = Icons.Rounded.DragIndicator,
                contentDescription = "Reordenar camada",
                tint = TextMuted,
                modifier = Modifier
                    .size(17.dp)
                    .pointerInput(layer.id) {
                        detectVerticalDragGestures(
                            onDragStart = {
                                dragAccumY = 0f
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onBeginDragReorder()
                            },
                            onDragEnd = {
                                dragAccumY = 0f
                                onEndDragReorder()
                            },
                            onDragCancel = {
                                dragAccumY = 0f
                                onEndDragReorder()
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                dragAccumY += dragAmount
                                val stepPx = latestRowHeightPx.coerceAtLeast(40f)
                                val deltaSteps = (dragAccumY / stepPx).toInt()
                                if (deltaSteps != 0) {
                                    dragAccumY -= deltaSteps * stepPx
                                    onDragReorderToIndex(latestVisualIndex + deltaSteps)
                                }
                            }
                        )
                    }
                    .semantics(mergeDescendants = true) {}
                    .testTag("drag_reorder_${layer.id}")
            )
        }

        // Complete Always-Visible AE/Alight Layer Flags: Olho, Cadeado, Solo, 3D, Pick-Whip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = if (layer.visible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                contentDescription = "Visibilidade",
                tint = if (layer.visible) TextSecondary else AlertRed.copy(alpha = 0.8f),
                modifier = Modifier
                    .size(15.dp)
                    .clickable { onToggleVisibility() }
                    .testTag("vis_layer_${layer.id}")
            )
            Icon(
                imageVector = if (layer.locked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                contentDescription = "Travar",
                tint = if (layer.locked) BeatAmber else TextMuted,
                modifier = Modifier
                    .size(15.dp)
                    .clickable { onToggleLock() }
                    .testTag("lock_layer_${layer.id}")
            )
            Text(
                text = "S",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = if (layer.solo) OledBlack else TextMuted,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (layer.solo) BeatAmber else StudioElevated)
                    .clickable { onToggleSolo() }
                    .padding(horizontal = 4.dp, vertical = 1.dp)
                    .testTag("solo_layer_${layer.id}")
            )
            Text(
                text = "3D",
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = if (layer.is3D) OledBlack else TextMuted,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (layer.is3D) ElectricPurple else StudioElevated)
                    .clickable { onToggle3D() }
                    .padding(horizontal = 3.dp, vertical = 1.dp)
                    .testTag("3d_layer_${layer.id}")
            )
            Icon(
                imageVector = Icons.Rounded.Link,
                contentDescription = "Pick-Whip Parentesco",
                tint = when {
                    isPickWhipSource -> NeonCyan
                    layer.parentId != null -> ElectricPurpleLight
                    else -> TextMuted
                },
                modifier = Modifier
                    .size(15.dp)
                    .clickable { onStartPickWhip() }
                    .testTag("pickwhip_layer_${layer.id}")
            )
        }
    }
}

fun layerTypeColor(type: LayerType): Color = when (type) {
    LayerType.VIDEO -> TrackVideoColor
    LayerType.IMAGE -> TrackImageColor
    LayerType.VECTOR -> TrackVectorColor
    LayerType.TEXT -> TrackTextColor
    LayerType.AUDIO -> TrackAudioColor
    LayerType.NULL -> TrackNullColor
    LayerType.CAMERA_3D -> TrackCameraColor
    LayerType.LIGHT_3D -> BeatAmber
    LayerType.ADJUSTMENT -> TrackAdjustColor
    LayerType.PRE_COMP -> TrackPreCompColor
}

fun layerTypeIcon(type: LayerType): ImageVector = when (type) {
    LayerType.VIDEO -> Icons.Rounded.Movie
    LayerType.IMAGE -> Icons.Rounded.Image
    LayerType.VECTOR -> Icons.Rounded.Category
    LayerType.TEXT -> Icons.Rounded.TextFields
    LayerType.AUDIO -> Icons.Rounded.Audiotrack
    LayerType.NULL -> Icons.Rounded.MyLocation
    LayerType.CAMERA_3D -> Icons.Rounded.Videocam
    LayerType.LIGHT_3D -> Icons.Rounded.WbSunny
    LayerType.ADJUSTMENT -> Icons.Rounded.AutoAwesome
    LayerType.PRE_COMP -> Icons.Rounded.FolderCopy
}

fun formatTimelineTimecode(ms: Long, fps: Int): String =
    com.example.engine.StudioEditOps.formatTimecode(ms, fps)
