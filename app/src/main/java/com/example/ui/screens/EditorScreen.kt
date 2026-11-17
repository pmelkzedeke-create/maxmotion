package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material.icons.rounded.Vignette
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.LayerType
import com.example.model.VectorShapePreset
import com.example.ui.InspectorTab
import com.example.ui.MaxMotionViewModel
import com.example.ui.PropertyKey
import com.example.ui.components.CanvasViewport
import com.example.ui.components.ContextualInspectorSheet
import com.example.ui.components.ExportDialog
import com.example.ui.components.GraphEditorOverlay
import com.example.ui.components.TimelinePanel
import com.example.ui.theme.AlertRed
import com.example.ui.theme.ElectricPurple
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.OledBlack
import com.example.ui.theme.StudioCard
import com.example.ui.theme.StudioElevated
import com.example.ui.theme.StudioHairline
import com.example.ui.theme.StudioPanel
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun EditorScreen(
    viewModel: MaxMotionViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val document by viewModel.document.collectAsStateWithLifecycle()
    val playheadMs by viewModel.playheadMs.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val selectedLayerIds by viewModel.selectedLayerIds.collectAsStateWithLifecycle()
    val previewRes by viewModel.previewResolution.collectAsStateWithLifecycle()
    val showSafetyGrid by viewModel.showSafetyGrid.collectAsStateWithLifecycle()
    val magneticSnapping by viewModel.magneticSnapping.collectAsStateWithLifecycle()
    val nudgeMode by viewModel.nudgeMode.collectAsStateWithLifecycle()
    val penToolActive by viewModel.penToolActive.collectAsStateWithLifecycle()
    val orbitMode by viewModel.orbitMode.collectAsStateWithLifecycle()
    val trackerTargetMode by viewModel.trackerTargetMode.collectAsStateWithLifecycle()
    val trackerTargetNorm by viewModel.trackerTargetNorm.collectAsStateWithLifecycle()
    val isProcessingAi by viewModel.isProcessingAi.collectAsStateWithLifecycle()
    val expandedLayerIds by viewModel.expandedLayerIds.collectAsStateWithLifecycle()
    val selectedKeyframeIds by viewModel.selectedKeyframeIds.collectAsStateWithLifecycle()
    val activeInspector by viewModel.activeInspector.collectAsStateWithLifecycle()
    val editingProperty by viewModel.editingProperty.collectAsStateWithLifecycle()
    val copiedCurve by viewModel.copiedCurve.collectAsStateWithLifecycle()
    val pickWhipSourceId by viewModel.pickWhipSourceId.collectAsStateWithLifecycle()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()
    val evaluatedScene by viewModel.evaluatedScene.collectAsStateWithLifecycle()
    val savedCurves by viewModel.savedCurves.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    val exportProgress by viewModel.exportProgress.collectAsStateWithLifecycle()
    val exportMessage by viewModel.exportMessage.collectAsStateWithLifecycle()
    val exportResult by viewModel.exportResult.collectAsStateWithLifecycle()
    val timelineZoom by viewModel.timelineZoom.collectAsStateWithLifecycle()

    var showExportDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusBanner) {
        statusBanner?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusBanner()
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val mime = context.contentResolver.getType(uri) ?: ""
            viewModel.importVisualMedia(uri, isVideo = mime.startsWith("video/"))
        }
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importAudioTrack(uri)
        }
    }

    val cubePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val layerId = viewModel.primaryLayerId
        if (uri != null && layerId != null) {
            viewModel.importCubeLut(layerId, uri)
        }
    }

    BackHandler {
        if (activeInspector != null) {
            viewModel.openInspector(null)
        } else if (document.activeCompositionId != document.compositions.firstOrNull()?.id) {
            viewModel.returnToMainComposition()
        } else {
            onNavigateBack()
        }
    }

    val comp = document.activeComposition
    val selectedLayer = comp.layers.firstOrNull { it.id in selectedLayerIds }
    val evaluatedSelected = evaluatedScene?.layers?.firstOrNull { it.layer.id == selectedLayer?.id }
    val isInPreComp = document.activeCompositionId != document.compositions.firstOrNull()?.id

    Scaffold(
        containerColor = OledBlack,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isWideLandscape = maxWidth > 720.dp && maxWidth > maxHeight

            Column(modifier = Modifier.fillMaxSize()) {
                // Sleek CapCut-Style Top Studio Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .background(StudioPanel)
                        .border(0.5.dp, StudioHairline)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = {
                                if (isInPreComp) viewModel.returnToMainComposition() else onNavigateBack()
                            },
                            modifier = Modifier.size(36.dp).testTag("editor_back_btn")
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Voltar", tint = TextPrimary, modifier = Modifier.size(20.dp))
                        }
                        Column(modifier = Modifier.padding(end = 6.dp)) {
                            Text(
                                text = if (isInPreComp) "↳ ${comp.name}" else document.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (isInPreComp) ElectricPurple else TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${comp.width}×${comp.height} • ${comp.fps}fps",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = TextMuted
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        IconButton(onClick = { viewModel.undo() }, enabled = canUndo, modifier = Modifier.size(34.dp).testTag("undo_btn")) {
                            Icon(Icons.AutoMirrored.Rounded.Undo, "Desfazer", tint = if (canUndo) TextPrimary else TextMuted.copy(alpha = 0.35f), modifier = Modifier.size(19.dp))
                        }
                        IconButton(onClick = { viewModel.redo() }, enabled = canRedo, modifier = Modifier.size(34.dp).testTag("redo_btn")) {
                            Icon(Icons.AutoMirrored.Rounded.Redo, "Refazer", tint = if (canRedo) TextPrimary else TextMuted.copy(alpha = 0.35f), modifier = Modifier.size(19.dp))
                        }
                        Surface(
                            color = StudioCard,
                            shape = RoundedCornerShape(99.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .clickable { viewModel.togglePreviewResolution() }
                                .testTag("preview_res_btn")
                        ) {
                            Text(
                                text = previewRes.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = NeonCyan,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                            )
                        }
                        IconButton(onClick = { viewModel.toggleSafetyGrid() }, modifier = Modifier.size(32.dp).testTag("safety_grid_btn")) {
                            Icon(Icons.Rounded.GridOn, "Grade de segurança", tint = if (showSafetyGrid) NeonCyan else TextMuted, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { viewModel.toggleOrbitMode() }, modifier = Modifier.size(32.dp).testTag("orbit_3d_top_btn")) {
                            Icon(Icons.Rounded.ViewInAr, "Órbita 3D", tint = if (orbitMode) NeonCyan else TextMuted, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { viewModel.toggleNudgeMode() }, modifier = Modifier.size(32.dp).testTag("nudge_toggle_btn")) {
                            Icon(Icons.Rounded.OpenWith, "Ajuste fino Nudge", tint = if (nudgeMode) NeonCyan else TextMuted, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(2.dp))
                        Box(
                            modifier = Modifier
                                .height(32.dp)
                                .clip(RoundedCornerShape(99.dp))
                                .background(
                                    Brush.horizontalGradient(listOf(NeonCyan, ElectricPurple))
                                )
                                .clickable { showExportDialog = true }
                                .padding(horizontal = 12.dp)
                                .testTag("open_export_btn"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Rounded.FileUpload, null, tint = OledBlack, modifier = Modifier.size(15.dp))
                                Text("Exportar", style = MaterialTheme.typography.labelSmall, color = OledBlack, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Adaptive Main Area (Portrait Stack vs Landscape Split)
                if (isWideLandscape) {
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        CanvasViewport(
                            document = document,
                            playheadMs = playheadMs,
                            evaluatedScene = evaluatedScene,
                            selectedLayerIds = selectedLayerIds,
                            previewResolution = previewRes,
                            showSafetyGrid = showSafetyGrid,
                            magneticSnapping = magneticSnapping,
                            nudgeMode = nudgeMode,
                            penToolActive = penToolActive,
                            orbitMode = orbitMode,
                            trackerTargetMode = trackerTargetMode,
                            trackerTargetNorm = trackerTargetNorm,
                            onSelectLayer = { viewModel.selectLayer(it) },
                            onBeginGesture = { viewModel.beginContinuousGesture() },
                            onMoveLayer = { id, x, y -> viewModel.updateLayerPosition(id, x, y) },
                            onMoveAnchor = { id, dx, dy -> viewModel.updateLayerAnchorPoint(id, dx, dy) },
                            onScaleLayer = { id, sx, sy ->
                                viewModel.setPropertyScalar(id, PropertyKey.SCALE_X, sx)
                                viewModel.setPropertyScalar(id, PropertyKey.SCALE_Y, sy)
                            },
                            onRotateLayer = { id, r -> viewModel.setPropertyScalar(id, PropertyKey.ROT_Z, r) },
                            onOrbitDrag = { dAz, dEl -> viewModel.orbitActiveCamera(dAz, dEl) },
                            onTrackerTargetDrag = { nx, ny -> viewModel.setTrackerTarget(nx, ny) },
                            onEndGesture = { viewModel.endContinuousGesture(it) },
                            onAddPenPoint = { x, y -> viewModel.addPenPointToSelected(x, y) },
                            onNudge = { dx, dy, dr -> viewModel.nudgeSelected(dx, dy, dr) },
                            onTwoFingerUndo = { viewModel.undo() },
                            onThreeFingerRedo = { viewModel.redo() },
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                        Column(modifier = Modifier.width(360.dp).fillMaxHeight()) {
                            TimelinePanel(
                                composition = comp,
                                assets = document.assets,
                                playheadMs = playheadMs,
                                isPlaying = isPlaying,
                                selectedLayerIds = selectedLayerIds,
                                expandedLayerIds = expandedLayerIds,
                                selectedKeyframeIds = selectedKeyframeIds,
                                pickWhipSourceId = pickWhipSourceId,
                                timelineZoom = timelineZoom,
                                magneticSnapping = magneticSnapping,
                                onTogglePlay = { viewModel.togglePlayback() },
                                onSeek = { viewModel.seekTo(it) },
                                onZoomChange = { viewModel.setTimelineZoom(it) },
                                onToggleMagnetic = { viewModel.toggleMagneticSnapping() },
                                onSelectLayer = { id, multi -> viewModel.selectLayer(id, multi) },
                                onToggleExpandLayer = { viewModel.toggleLayerExpanded(it) },
                                onToggleSelectKeyframe = { viewModel.toggleKeyframeSelection(it) },
                                onShiftSelectedKeyframes = { viewModel.shiftSelectedKeyframes(it) },
                                onDeleteSelectedKeyframes = { viewModel.deleteSelectedKeyframes() },
                                onApplyCurveToSelectedKeyframes = { viewModel.applyCurveToSelectedKeyframes(it) },
                                onClearSelectedKeyframes = { viewModel.clearKeyframeSelection() },
                                onToggleVisibility = { viewModel.toggleLayerVisibility(it) },
                                onToggleLock = { viewModel.toggleLayerLock(it) },
                                onToggleSolo = { viewModel.toggleLayerSolo(it) },
                                onToggle3D = { viewModel.toggleLayer3D(it) },
                                onStartPickWhip = { viewModel.startPickWhip(it) },
                                onMoveZOrder = { id, d -> viewModel.moveLayerZOrder(id, d) },
                                onUpdateBounds = { id, i, o -> viewModel.updateLayerBounds(id, i, o) },
                                onEnterPreComp = { viewModel.enterPreComp(it) },
                                onBeginGesture = { viewModel.beginContinuousGesture() },
                                onEndGesture = { viewModel.endContinuousGesture(it) },
                                onSplitAtPlayhead = { viewModel.splitSelectedLayerAtPlayhead() },
                                onToggleBeatMarker = { viewModel.toggleBeatMarkerAtPlayhead() },
                                onJumpBeatMarker = { viewModel.jumpToBeatMarker(it) },
                                onToggleKeyframeAtPlayhead = { viewModel.toggleKeyframeAtPlayheadSelected() },
                                onJumpKeyframe = { viewModel.jumpToLayerKeyframe(it) },
                                onOpenSpeedShortcut = { viewModel.openInspector(InspectorTab.SPEED_RAMP) },
                                onFreezeFrame = { viewModel.freezeFrameAtPlayhead() },
                                onPreCompose = { viewModel.preComposeSelectedLayers() },
                                onDuplicateSelectedKeyframes = { viewModel.duplicateSelectedKeyframes(it) },
                                onMoveKeyframeTime = { layerId, target, kfId, compMs ->
                                    viewModel.moveAnyKeyframeTime(layerId, target, kfId, compMs)
                                },
                                onReorderLayerToIndex = { layerId, idx ->
                                    viewModel.reorderLayerToVisualIndex(layerId, idx)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                } else {
                    Box(modifier = Modifier.weight(0.46f).fillMaxWidth()) {
                        CanvasViewport(
                            document = document,
                            playheadMs = playheadMs,
                            evaluatedScene = evaluatedScene,
                            selectedLayerIds = selectedLayerIds,
                            previewResolution = previewRes,
                            showSafetyGrid = showSafetyGrid,
                            magneticSnapping = magneticSnapping,
                            nudgeMode = nudgeMode,
                            penToolActive = penToolActive,
                            orbitMode = orbitMode,
                            trackerTargetMode = trackerTargetMode,
                            trackerTargetNorm = trackerTargetNorm,
                            onSelectLayer = { viewModel.selectLayer(it) },
                            onBeginGesture = { viewModel.beginContinuousGesture() },
                            onMoveLayer = { id, x, y -> viewModel.updateLayerPosition(id, x, y) },
                            onMoveAnchor = { id, dx, dy -> viewModel.updateLayerAnchorPoint(id, dx, dy) },
                            onScaleLayer = { id, sx, sy ->
                                viewModel.setPropertyScalar(id, PropertyKey.SCALE_X, sx)
                                viewModel.setPropertyScalar(id, PropertyKey.SCALE_Y, sy)
                            },
                            onRotateLayer = { id, r -> viewModel.setPropertyScalar(id, PropertyKey.ROT_Z, r) },
                            onOrbitDrag = { dAz, dEl -> viewModel.orbitActiveCamera(dAz, dEl) },
                            onTrackerTargetDrag = { nx, ny -> viewModel.setTrackerTarget(nx, ny) },
                            onEndGesture = { viewModel.endContinuousGesture(it) },
                            onAddPenPoint = { x, y -> viewModel.addPenPointToSelected(x, y) },
                            onNudge = { dx, dy, dr -> viewModel.nudgeSelected(dx, dy, dr) },
                            onTwoFingerUndo = { viewModel.undo() },
                            onThreeFingerRedo = { viewModel.redo() },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    AnimatedVisibility(
                        visible = activeInspector == InspectorTab.GRAPH_EDITOR && selectedLayer != null,
                        enter = slideInVertically(tween(220)) { it / 2 } + fadeIn(tween(180)),
                        exit = slideOutVertically(tween(180)) { it / 2 } + fadeOut(tween(140))
                    ) {
                        if (selectedLayer != null) {
                            GraphEditorOverlay(
                                propertyKey = editingProperty,
                                property = viewModel.readLayerProperty(selectedLayer, editingProperty),
                                savedCurves = savedCurves,
                                copiedCurve = copiedCurve,
                                onUpdateBezier = { kfId, bez ->
                                    viewModel.updateKeyframeBezier(selectedLayer.id, editingProperty, kfId, bez)
                                },
                                onCopyCurve = { viewModel.copyCurve(it) },
                                onSaveCurveToLibrary = { n, b -> viewModel.saveCustomCurve(n, b) },
                                onClose = { viewModel.openInspector(null) }
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = activeInspector != null && activeInspector != InspectorTab.GRAPH_EDITOR,
                        enter = slideInVertically(tween(220)) { it / 2 } + fadeIn(tween(180)),
                        exit = slideOutVertically(tween(180)) { it / 2 } + fadeOut(tween(140))
                    ) {
                        activeInspector?.let { tab ->
                            ContextualInspectorSheet(
                                tab = tab,
                                layer = selectedLayer,
                                evaluatedLayer = evaluatedSelected,
                                composition = comp,
                                playheadMs = playheadMs,
                                editingProperty = editingProperty,
                                orbitMode = orbitMode,
                                trackerTargetMode = trackerTargetMode,
                                trackerTargetNorm = trackerTargetNorm,
                                isProcessingAi = isProcessingAi,
                                onToggleOrbit = { viewModel.toggleOrbitMode() },
                                onToggleTrackerTarget = { viewModel.toggleTrackerTargetMode() },
                                onPickCubeFile = { cubePickerLauncher.launch(arrayOf("*/*")) },
                                viewModel = viewModel,
                                onClose = { viewModel.openInspector(null) }
                            )
                        }
                    }

                    TimelinePanel(
                        composition = comp,
                        assets = document.assets,
                        playheadMs = playheadMs,
                        isPlaying = isPlaying,
                        selectedLayerIds = selectedLayerIds,
                        expandedLayerIds = expandedLayerIds,
                        selectedKeyframeIds = selectedKeyframeIds,
                        pickWhipSourceId = pickWhipSourceId,
                        timelineZoom = timelineZoom,
                        magneticSnapping = magneticSnapping,
                        onTogglePlay = { viewModel.togglePlayback() },
                        onSeek = { viewModel.seekTo(it) },
                        onZoomChange = { viewModel.setTimelineZoom(it) },
                        onToggleMagnetic = { viewModel.toggleMagneticSnapping() },
                        onSelectLayer = { id, multi -> viewModel.selectLayer(id, multi) },
                        onToggleExpandLayer = { viewModel.toggleLayerExpanded(it) },
                        onToggleSelectKeyframe = { viewModel.toggleKeyframeSelection(it) },
                        onShiftSelectedKeyframes = { viewModel.shiftSelectedKeyframes(it) },
                        onDeleteSelectedKeyframes = { viewModel.deleteSelectedKeyframes() },
                        onApplyCurveToSelectedKeyframes = { viewModel.applyCurveToSelectedKeyframes(it) },
                        onClearSelectedKeyframes = { viewModel.clearKeyframeSelection() },
                        onToggleVisibility = { viewModel.toggleLayerVisibility(it) },
                        onToggleLock = { viewModel.toggleLayerLock(it) },
                        onToggleSolo = { viewModel.toggleLayerSolo(it) },
                        onToggle3D = { viewModel.toggleLayer3D(it) },
                        onStartPickWhip = { viewModel.startPickWhip(it) },
                        onMoveZOrder = { id, d -> viewModel.moveLayerZOrder(id, d) },
                        onUpdateBounds = { id, i, o -> viewModel.updateLayerBounds(id, i, o) },
                        onEnterPreComp = { viewModel.enterPreComp(it) },
                        onBeginGesture = { viewModel.beginContinuousGesture() },
                        onEndGesture = { viewModel.endContinuousGesture(it) },
                        onSplitAtPlayhead = { viewModel.splitSelectedLayerAtPlayhead() },
                        onToggleBeatMarker = { viewModel.toggleBeatMarkerAtPlayhead() },
                        onJumpBeatMarker = { viewModel.jumpToBeatMarker(it) },
                        onToggleKeyframeAtPlayhead = { viewModel.toggleKeyframeAtPlayheadSelected() },
                        onJumpKeyframe = { viewModel.jumpToLayerKeyframe(it) },
                        onOpenSpeedShortcut = { viewModel.openInspector(InspectorTab.SPEED_RAMP) },
                        onFreezeFrame = { viewModel.freezeFrameAtPlayhead() },
                        onPreCompose = { viewModel.preComposeSelectedLayers() },
                        onDuplicateSelectedKeyframes = { viewModel.duplicateSelectedKeyframes(it) },
                        onMoveKeyframeTime = { layerId, target, kfId, compMs ->
                            viewModel.moveAnyKeyframeTime(layerId, target, kfId, compMs)
                        },
                        onReorderLayerToIndex = { layerId, idx ->
                            viewModel.reorderLayerToVisualIndex(layerId, idx)
                        },
                        modifier = Modifier.weight(0.46f)
                    )
                }

                // CapCut-Style Sleek Borderless Contextual Dock
                ContextualBottomDock(
                    selectedLayer = selectedLayer,
                    multiSelectedCount = selectedLayerIds.size,
                    activeInspector = activeInspector,
                    penToolActive = penToolActive,
                    onPickMedia = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                    },
                    onAddText = { viewModel.addTextLayer() },
                    onAddVector = { viewModel.addVectorLayer(VectorShapePreset.RECTANGLE) },
                    onTogglePen = { viewModel.togglePenTool() },
                    onPickAudio = { audioPickerLauncher.launch(arrayOf("audio/*")) },
                    onAddNull = { viewModel.addUtilityLayer(LayerType.NULL) },
                    onAddCamera = { viewModel.addUtilityLayer(LayerType.CAMERA_3D) },
                    onAddLight = { viewModel.addUtilityLayer(LayerType.LIGHT_3D) },
                    onAddAdjustment = { viewModel.addUtilityLayer(LayerType.ADJUSTMENT) },
                    onOpenTab = { viewModel.openInspector(it) },
                    onSplit = { viewModel.splitSelectedLayerAtPlayhead() },
                    onPreCompose = { viewModel.preComposeSelectedLayers() },
                    onDuplicate = { viewModel.duplicateSelectedLayer() },
                    onDelete = { viewModel.deleteSelectedLayer() },
                    onDeselect = { viewModel.selectLayer(null) }
                )
            }
        }
    }

    if (showExportDialog) {
        ExportDialog(
            durationMs = comp.durationMs,
            exportProgress = exportProgress,
            exportMessage = exportMessage,
            exportResult = exportResult,
            onStartExport = { viewModel.startExport(it) },
            onCopyProjectJson = {
                val json = viewModel.exportCurrentProjectJson()
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("MaxMotion Project JSON", json))
                viewModel.postBanner("JSON do projeto copiado para a área de transferência!")
            },
            onDismiss = {
                viewModel.dismissExportResult()
                showExportDialog = false
            }
        )
    }
}

@Composable
private fun ContextualBottomDock(
    selectedLayer: com.example.model.LayerNode?,
    multiSelectedCount: Int,
    activeInspector: InspectorTab?,
    penToolActive: Boolean,
    onPickMedia: () -> Unit,
    onAddText: () -> Unit,
    onAddVector: () -> Unit,
    onTogglePen: () -> Unit,
    onPickAudio: () -> Unit,
    onAddNull: () -> Unit,
    onAddCamera: () -> Unit,
    onAddLight: () -> Unit,
    onAddAdjustment: () -> Unit,
    onOpenTab: (InspectorTab) -> Unit,
    onSplit: () -> Unit,
    onPreCompose: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onDeselect: () -> Unit
) {
    Surface(
        color = StudioPanel,
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, StudioHairline)
            .navigationBarsPadding()
    ) {
        AnimatedContent(
            targetState = selectedLayer != null,
            transitionSpec = {
                (fadeIn(tween(160)) + slideInVertically(tween(180)) { it / 4 }) togetherWith
                    (fadeOut(tween(120)))
            },
            label = "dock_mode_anim"
        ) { hasSelection ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (!hasSelection || selectedLayer == null) {
                    DockActionItem(Icons.Rounded.PhotoLibrary, "Mídia", tag = "dock_add_media", onClick = onPickMedia)
                    DockActionItem(Icons.Rounded.TextFields, "Texto", tag = "dock_add_text", onClick = onAddText)
                    DockActionItem(Icons.Rounded.Category, "Forma", tag = "dock_add_shape", onClick = onAddVector)
                    DockActionItem(Icons.Rounded.Draw, "Caneta", active = penToolActive, tag = "dock_pen_tool", onClick = onTogglePen)
                    DockActionItem(Icons.Rounded.Audiotrack, "Áudio", tag = "dock_add_audio", onClick = onPickAudio)
                    DockActionItem(Icons.Rounded.Videocam, "Câmera 3D", tag = "dock_add_camera", onClick = onAddCamera)
                    DockActionItem(Icons.Rounded.WbSunny, "Luz 3D", tag = "dock_add_light", onClick = onAddLight)
                    DockActionItem(Icons.Rounded.MyLocation, "Nulo", tag = "dock_add_null", onClick = onAddNull)
                    DockActionItem(Icons.Rounded.AutoAwesome, "Ajuste", tag = "dock_add_adjustment", onClick = onAddAdjustment)
                    DockActionItem(
                        Icons.Rounded.AspectRatio, "Proporção",
                        active = activeInspector == InspectorTab.ASPECT_BG,
                        tag = "dock_aspect",
                        onClick = { onOpenTab(InspectorTab.ASPECT_BG) }
                    )
                } else {
                    DockActionItem(Icons.AutoMirrored.Rounded.ArrowBack, "Voltar", tag = "dock_deselect", onClick = onDeselect)
                    DockActionItem(
                        Icons.Rounded.Tune, "Editar",
                        active = activeInspector == InspectorTab.TRANSFORM,
                        tag = "dock_transform",
                        onClick = { onOpenTab(InspectorTab.TRANSFORM) }
                    )
                    if (selectedLayer.type == LayerType.CAMERA_3D || selectedLayer.type == LayerType.LIGHT_3D) {
                        DockActionItem(
                            Icons.Rounded.Videocam, "3D / Foco",
                            active = activeInspector == InspectorTab.CAMERA_LIGHT,
                            tag = "dock_camera_light",
                            onClick = { onOpenTab(InspectorTab.CAMERA_LIGHT) }
                        )
                    }
                    DockActionItem(Icons.Rounded.ContentCut, "Dividir", tag = "dock_split", onClick = onSplit)
                    DockActionItem(
                        Icons.Rounded.AutoFixHigh, "Efeitos",
                        active = activeInspector == InspectorTab.EFFECTS,
                        tag = "dock_effects",
                        onClick = { onOpenTab(InspectorTab.EFFECTS) }
                    )
                    DockActionItem(
                        Icons.Rounded.Palette, "Cor / LUT",
                        active = activeInspector == InspectorTab.COLOR_CURVES,
                        tag = "dock_color_curves",
                        onClick = { onOpenTab(InspectorTab.COLOR_CURVES) }
                    )
                    DockActionItem(
                        Icons.Rounded.Speed, "Velocidade",
                        active = activeInspector == InspectorTab.SPEED_RAMP,
                        tag = "dock_speed_ramp",
                        onClick = { onOpenTab(InspectorTab.SPEED_RAMP) }
                    )
                    if (selectedLayer.type == LayerType.VIDEO || selectedLayer.type == LayerType.IMAGE || selectedLayer.type == LayerType.AUDIO) {
                        DockActionItem(
                            Icons.Rounded.AutoAwesome, "IA / Track",
                            active = activeInspector == InspectorTab.TRACKER_AI,
                            tag = "dock_tracker_ai",
                            onClick = { onOpenTab(InspectorTab.TRACKER_AI) }
                        )
                    }
                    DockActionItem(
                        Icons.Rounded.ShowChart, "Curvas",
                        active = activeInspector == InspectorTab.GRAPH_EDITOR,
                        tag = "dock_graph",
                        onClick = { onOpenTab(InspectorTab.GRAPH_EDITOR) }
                    )
                    DockActionItem(
                        Icons.Rounded.Bolt, "Animar",
                        active = activeInspector == InspectorTab.ANIMATE,
                        tag = "dock_animate",
                        onClick = { onOpenTab(InspectorTab.ANIMATE) }
                    )
                    if (selectedLayer.type == LayerType.VECTOR || selectedLayer.type == LayerType.TEXT) {
                        DockActionItem(
                            Icons.Rounded.TextFields, "Estilo/Trim",
                            active = activeInspector == InspectorTab.VECTOR_TEXT,
                            tag = "dock_vector_text",
                            onClick = { onOpenTab(InspectorTab.VECTOR_TEXT) }
                        )
                    }
                    if (selectedLayer.type == LayerType.VECTOR) {
                        DockActionItem(Icons.Rounded.Draw, "Caneta", active = penToolActive, tag = "dock_pen_edit", onClick = onTogglePen)
                    }
                    if (selectedLayer.type == LayerType.AUDIO) {
                        DockActionItem(
                            Icons.Rounded.Audiotrack, "Áudio",
                            active = activeInspector == InspectorTab.AUDIO,
                            tag = "dock_audio",
                            onClick = { onOpenTab(InspectorTab.AUDIO) }
                        )
                    }
                    DockActionItem(
                        Icons.Rounded.Vignette, "Máscara",
                        active = activeInspector == InspectorTab.MASKS,
                        tag = "dock_mask",
                        onClick = { onOpenTab(InspectorTab.MASKS) }
                    )
                    DockActionItem(
                        Icons.Rounded.Layers, "Mistura",
                        active = activeInspector == InspectorTab.BLENDING,
                        tag = "dock_blending",
                        onClick = { onOpenTab(InspectorTab.BLENDING) }
                    )
                    DockActionItem(
                        Icons.Rounded.Code, "Expressão",
                        active = activeInspector == InspectorTab.EXPRESSION,
                        tag = "dock_expression",
                        onClick = { onOpenTab(InspectorTab.EXPRESSION) }
                    )
                    DockActionItem(
                        Icons.Rounded.FolderZip,
                        if (multiSelectedCount > 1) "Pré-Compor ($multiSelectedCount)" else "Pré-Compor",
                        tag = "dock_precomp",
                        onClick = onPreCompose
                    )
                    DockActionItem(Icons.Rounded.ContentCopy, "Duplicar", tag = "dock_duplicate", onClick = onDuplicate)
                    DockActionItem(Icons.Rounded.Delete, "Excluir", tintOverride = AlertRed, tag = "dock_delete", onClick = onDelete)
                }
            }
        }
    }
}

@Composable
private fun DockActionItem(
    icon: ImageVector,
    label: String,
    active: Boolean = false,
    tintOverride: Color? = null,
    tag: String,
    onClick: () -> Unit
) {
    val iconScale by animateFloatAsState(
        targetValue = if (active) 1.12f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "dock_icon_scale"
    )
    val bgColor by animateColorAsState(
        targetValue = if (active) NeonCyan.copy(alpha = 0.14f) else Color.Transparent,
        label = "dock_bg"
    )
    val indicatorWidth by animateDpAsState(
        targetValue = if (active) 18.dp else 0.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "dock_pill"
    )

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp)
            .testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tintOverride ?: if (active) NeonCyan else TextPrimary,
            modifier = Modifier.size(20.dp).scale(iconScale)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            color = tintOverride ?: if (active) NeonCyan else TextSecondary,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .width(indicatorWidth.coerceAtLeast(0.dp))
                .height(2.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(NeonCyan)
        )
    }
}
