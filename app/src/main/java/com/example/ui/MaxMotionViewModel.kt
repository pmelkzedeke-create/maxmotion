package com.example.ui

import android.app.Application
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.ProjectEntity
import com.example.data.ProjectRepository
import com.example.data.SavedCurveEntity
import com.example.engine.AudioAnalyzer
import com.example.engine.AudioPreviewPlayer
import com.example.engine.CubeLutParser
import com.example.engine.CurveInterpolator
import com.example.engine.EvaluatedScene
import com.example.engine.ExpressionContext
import com.example.engine.ExpressionEvaluator
import com.example.engine.MlKitRotoscoper
import com.example.engine.MotionTracker2D
import com.example.engine.OfflineCaptionGenerator
import com.example.engine.SceneEvaluator
import com.example.engine.SpeedRampEvaluator
import com.example.engine.StudioEditOps
import com.example.engine.UndoRedoManager
import com.example.model.AnimatableProperty
import com.example.model.AspectRatioPreset
import com.example.model.BrandKit
import com.example.model.OfficialTemplates
import com.example.model.TypePresets
import com.example.model.AssetItem
import com.example.model.AssetType
import com.example.model.AudioSpec
import com.example.model.BezierCurve
import com.example.model.BlendMode
import com.example.model.CameraSpec
import com.example.model.ColorGradingSpec
import com.example.model.CompositionNode
import com.example.model.DemoProjectFactory
import com.example.model.EasingType
import com.example.model.EffectNode
import com.example.model.EffectType
import com.example.model.Keyframe
import com.example.model.LayerNode
import com.example.model.LayerType
import com.example.model.LightSpec
import com.example.model.MaskMode
import com.example.model.MaskShape
import com.example.model.MaskSpec
import com.example.model.PreviewResolution
import com.example.model.ProjectDocument
import com.example.model.TextRangeMode
import com.example.model.TextSpec
import com.example.model.TransformSpec
import com.example.model.VectorPoint
import com.example.model.VectorShapePreset
import com.example.model.VectorSpec
import com.example.render.ExportResult
import com.example.render.ExportSettings
import com.example.render.VideoExporter
import com.example.ui.components.timeline.SubTrackTarget
import com.example.ui.components.timeline.collectAllKeyframeMarkers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.abs

enum class InspectorTab(val labelPt: String) {
    TRANSFORM("Transformar"),
    CAMERA_LIGHT("Câmera & Luz 3D"),
    EFFECTS("Efeitos"),
    COLOR_CURVES("Curvas RGB / LUT"),
    MASKS("Máscara"),
    BLENDING("Mistura"),
    VECTOR_TEXT("Forma / Texto"),
    ANIMATE("Animar"),
    SPEED_RAMP("Velocidade"),
    TRACKER_AI("IA & Rastreador"),
    GRAPH_EDITOR("Curvas"),
    EXPRESSION("Expressão"),
    AUDIO("Áudio"),
    ASPECT_BG("Proporção"),
    BRAND("Marca"),
    EXPORT("Exportar")
}

enum class PropertyKey(val labelPt: String) {
    POS_X("Posição X"),
    POS_Y("Posição Y"),
    POS_Z("Posição Z"),
    ANCHOR_X("Âncora X"),
    ANCHOR_Y("Âncora Y"),
    SCALE_X("Escala X"),
    SCALE_Y("Escala Y"),
    ROT_X("Rotação X"),
    ROT_Y("Rotação Y"),
    ROT_Z("Rotação Z"),
    OPACITY("Opacidade"),
    SKEW_X("Inclinação"),
    TRIM_END("Trim Fim"),
    TEXT_RANGE_START("Texto Início")
}

class MaxMotionViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val repository = ProjectRepository(application, db.maxMotionDao())
    private val undoRedo = UndoRedoManager()
    private val audioPreviewPlayer = AudioPreviewPlayer(application)

    val savedProjects: StateFlow<List<ProjectEntity>> = repository.projectsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedCurves: StateFlow<List<SavedCurveEntity>> = repository.curvesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _document = MutableStateFlow(DemoProjectFactory.createDemoProject())
    val document: StateFlow<ProjectDocument> = _document.asStateFlow()

    private val _playheadMs = MutableStateFlow(600L)
    val playheadMs: StateFlow<Long> = _playheadMs.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _selectedLayerIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedLayerIds: StateFlow<Set<String>> = _selectedLayerIds.asStateFlow()

    val primaryLayerId: String?
        get() = _selectedLayerIds.value.firstOrNull()

    private val _previewResolution = MutableStateFlow(PreviewResolution.FULL)
    val previewResolution: StateFlow<PreviewResolution> = _previewResolution.asStateFlow()

    private val _showSafetyGrid = MutableStateFlow(true)
    val showSafetyGrid: StateFlow<Boolean> = _showSafetyGrid.asStateFlow()

    private val _magneticSnapping = MutableStateFlow(true)
    val magneticSnapping: StateFlow<Boolean> = _magneticSnapping.asStateFlow()

    private val _nudgeMode = MutableStateFlow(false)
    val nudgeMode: StateFlow<Boolean> = _nudgeMode.asStateFlow()

    private val _penToolActive = MutableStateFlow(false)
    val penToolActive: StateFlow<Boolean> = _penToolActive.asStateFlow()

    private val _orbitMode = MutableStateFlow(false)
    val orbitMode: StateFlow<Boolean> = _orbitMode.asStateFlow()

    private val _trackerTargetMode = MutableStateFlow(false)
    val trackerTargetMode: StateFlow<Boolean> = _trackerTargetMode.asStateFlow()

    private val _trackerTargetNorm = MutableStateFlow(0f to 0f)
    val trackerTargetNorm: StateFlow<Pair<Float, Float>> = _trackerTargetNorm.asStateFlow()

    private val _isProcessingAi = MutableStateFlow(false)
    val isProcessingAi: StateFlow<Boolean> = _isProcessingAi.asStateFlow()

    private val _expandedLayerIds = MutableStateFlow<Set<String>>(emptySet())
    val expandedLayerIds: StateFlow<Set<String>> = _expandedLayerIds.asStateFlow()

    private val _selectedKeyframeIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedKeyframeIds: StateFlow<Set<String>> = _selectedKeyframeIds.asStateFlow()

    private val _activeInspector = MutableStateFlow<InspectorTab?>(null)
    val activeInspector: StateFlow<InspectorTab?> = _activeInspector.asStateFlow()

    private val _editingProperty = MutableStateFlow(PropertyKey.SCALE_X)
    val editingProperty: StateFlow<PropertyKey> = _editingProperty.asStateFlow()

    private val _copiedCurve = MutableStateFlow<BezierCurve?>(null)
    val copiedCurve: StateFlow<BezierCurve?> = _copiedCurve.asStateFlow()

    private val _pickWhipSourceId = MutableStateFlow<String?>(null)
    val pickWhipSourceId: StateFlow<String?> = _pickWhipSourceId.asStateFlow()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private val _evaluatedScene = MutableStateFlow<EvaluatedScene?>(null)
    val evaluatedScene: StateFlow<EvaluatedScene?> = _evaluatedScene.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _exportProgress = MutableStateFlow<Float?>(null)
    val exportProgress: StateFlow<Float?> = _exportProgress.asStateFlow()

    private val _exportMessage = MutableStateFlow<String?>(null)
    val exportMessage: StateFlow<String?> = _exportMessage.asStateFlow()

    private val _exportResult = MutableStateFlow<ExportResult?>(null)
    val exportResult: StateFlow<ExportResult?> = _exportResult.asStateFlow()

    private val _timelineZoom = MutableStateFlow(1.0f)
    val timelineZoom: StateFlow<Float> = _timelineZoom.asStateFlow()

    private val _rippleEdit = MutableStateFlow(false)
    val rippleEdit: StateFlow<Boolean> = _rippleEdit.asStateFlow()

    private val _crashSnapshot = MutableStateFlow<ProjectDocument?>(null)
    val crashSnapshot: StateFlow<ProjectDocument?> = _crashSnapshot.asStateFlow()

    private val _showDemoTutorial = MutableStateFlow(false)
    val showDemoTutorial: StateFlow<Boolean> = _showDemoTutorial.asStateFlow()

    private var playJob: Job? = null
    private var autoSaveJob: Job? = null
    private var periodicSaveJob: Job? = null
    private var dragSnapshotBeforeGesture: ProjectDocument? = null
    private var editorSessionActive = false

    init {
        viewModelScope.launch {
            repository.ensureSeeded()
            _crashSnapshot.value = repository.loadCrashSnapshot()
            refreshSceneEvaluation()
        }
        periodicSaveJob = viewModelScope.launch {
            while (isActive) {
                delay(15_000L)
                if (editorSessionActive) {
                    runCatching { repository.writeCrashSnapshot(_document.value) }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        periodicSaveJob?.cancel()
        autoSaveJob?.cancel()
        playJob?.cancel()
        audioPreviewPlayer.releaseAll()
        if (editorSessionActive) {
            runCatching { repository.writeCrashSnapshot(_document.value) }
        }
    }

    fun enterEditorSession() {
        editorSessionActive = true
    }

    fun leaveEditorSession() {
        editorSessionActive = false
        viewModelScope.launch {
            val saved = runCatching { repository.saveProject(_document.value) }.isSuccess
            if (saved) repository.clearCrashSnapshot()
        }
    }

    fun clearStatusBanner() {
        _statusBanner.value = null
    }

    fun postBanner(msg: String) {
        _statusBanner.value = msg
    }

    fun openProject(projectId: String, onLoaded: () -> Unit = {}) {
        viewModelScope.launch {
            val loaded = repository.loadProject(projectId)
            if (loaded != null) {
                undoRedo.clear()
                _document.value = loaded
                _playheadMs.value = 0L
                _selectedLayerIds.value = emptySet()
                _activeInspector.value = null
                syncUndoFlags()
                refreshSceneEvaluation()
                onLoaded()
            } else {
                postBanner("Não foi possível carregar o projeto.")
            }
        }
    }

    fun openDemoProject(onLoaded: () -> Unit = {}) {
        val demo = DemoProjectFactory.createDemoProject()
        undoRedo.clear()
        _document.value = demo
        _playheadMs.value = 600L
        _selectedLayerIds.value = setOfNotNull(demo.activeComposition.layers.getOrNull(1)?.id)
        _activeInspector.value = null
        syncUndoFlags()
        refreshSceneEvaluation()
        viewModelScope.launch { repository.saveProject(demo) }
        _showDemoTutorial.value = repository.hasUnseenDemoTutorial()
        onLoaded()
    }

    fun dismissDemoTutorial() {
        repository.markDemoTutorialSeen()
        _showDemoTutorial.value = false
    }

    fun restoreCrashSnapshot(onLoaded: () -> Unit = {}) {
        val snap = _crashSnapshot.value ?: repository.loadCrashSnapshot() ?: return
        undoRedo.clear()
        _document.value = snap
        _playheadMs.value = 0L
        _selectedLayerIds.value = emptySet()
        _activeInspector.value = null
        _crashSnapshot.value = null
        repository.clearCrashSnapshot()
        syncUndoFlags()
        refreshSceneEvaluation()
        viewModelScope.launch { repository.saveProject(snap) }
        postBanner("Restauramos ${snap.name}")
        onLoaded()
    }

    fun dismissCrashSnapshot() {
        repository.clearCrashSnapshot()
        _crashSnapshot.value = null
    }

    fun openOfficialTemplate(templateId: String, onOpened: () -> Unit = {}) {
        viewModelScope.launch {
            val doc = OfficialTemplates.build(templateId)
            repository.saveProject(doc)
            undoRedo.clear()
            _document.value = doc
            _playheadMs.value = 0L
            _selectedLayerIds.value = setOfNotNull(doc.activeComposition.layers.firstOrNull { it.essentialRole == com.example.model.EssentialRole.TITLE }?.id)
            _activeInspector.value = InspectorTab.BRAND
            syncUndoFlags()
            refreshSceneEvaluation()
            postBanner("Template “${doc.name}” — troque a marca")
            onOpened()
        }
    }

    fun applyBrandKit(brand: BrandKit) {
        val next = OfficialTemplates.applyBrand(_document.value, brand)
        applyMutation("Trocar marca") { next }
        postBanner("Marca aplicada em ${next.activeComposition.layers.count { it.essentialRole != com.example.model.EssentialRole.NONE }} camadas")
    }

    fun createNewProject(name: String, aspect: AspectRatioPreset, fps: Int, durationSec: Int, onCreated: () -> Unit) {
        viewModelScope.launch {
            val doc = DemoProjectFactory.createBlankProject(name, aspect, fps, durationSec * 1000L)
            repository.saveProject(doc)
            undoRedo.clear()
            _document.value = doc
            _playheadMs.value = 0L
            _selectedLayerIds.value = setOfNotNull(doc.activeComposition.layers.firstOrNull()?.id)
            _activeInspector.value = null
            syncUndoFlags()
            refreshSceneEvaluation()
            onCreated()
        }
    }

    fun duplicateSavedProject(projectId: String) {
        viewModelScope.launch {
            repository.duplicateProject(projectId)
            postBanner("Projeto duplicado!")
        }
    }

    fun deleteSavedProject(projectId: String) {
        viewModelScope.launch {
            repository.deleteProject(projectId)
            postBanner("Projeto removido.")
        }
    }

    fun importProjectJson(rawJson: String, onImported: () -> Unit) {
        viewModelScope.launch {
            repository.importProjectJson(rawJson).onSuccess { doc ->
                undoRedo.clear()
                _document.value = doc
                _playheadMs.value = 0L
                _selectedLayerIds.value = emptySet()
                syncUndoFlags()
                refreshSceneEvaluation()
                postBanner("Projeto JSON importado com sucesso!")
                onImported()
            }.onFailure { err ->
                postBanner(err.message ?: "Erro ao importar JSON")
            }
        }
    }

    fun exportCurrentProjectJson(): String = repository.exportProjectJson(_document.value)

    // --- Playback & Timeline Navigation ---

    fun togglePlayback() {
        if (_isPlaying.value) {
            pausePlayback()
        } else {
            startPlayback()
        }
    }

    fun startPlayback() {
        if (_isPlaying.value) return
        _isPlaying.value = true
        playJob?.cancel()
        playJob = viewModelScope.launch {
            val fps = _document.value.activeComposition.fps.coerceIn(15, 60)
            val stepMs = (1000L / fps).coerceAtLeast(16L)
            var lastTick = System.nanoTime()
            audioPreviewPlayer.syncPlayback(true, _playheadMs.value, _document.value.activeComposition, _document.value.assets)
            while (isActive && _isPlaying.value) {
                delay(stepMs)
                val now = System.nanoTime()
                val dtMs = ((now - lastTick) / 1_000_000L).coerceIn(1L, 100L)
                lastTick = now
                val doc = _document.value
                val comp = doc.activeComposition
                val next = _playheadMs.value + dtMs
                val wrapped = StudioEditOps.wrapPlayhead(next, comp, loopWorkArea = true)
                _playheadMs.value = wrapped
                audioPreviewPlayer.syncPlayback(true, wrapped, comp, doc.assets)
                refreshSceneEvaluation()
            }
        }
    }

    fun pausePlayback() {
        _isPlaying.value = false
        playJob?.cancel()
        val doc = _document.value
        audioPreviewPlayer.syncPlayback(false, _playheadMs.value, doc.activeComposition, doc.assets)
    }

    fun seekTo(ms: Long) {
        val doc = _document.value
        val dur = doc.activeComposition.durationMs
        _playheadMs.value = ms.coerceIn(0L, dur)
        audioPreviewPlayer.syncPlayback(_isPlaying.value, _playheadMs.value, doc.activeComposition, doc.assets)
        refreshSceneEvaluation()
    }

    fun setTimelineZoom(zoom: Float) {
        _timelineZoom.value = zoom.coerceIn(0.25f, 12.0f)
    }

    fun toggleBeatMarkerAtPlayhead() {
        val t = _playheadMs.value
        val existing = _document.value.activeComposition.beatMarkersMs
        val near = existing.firstOrNull { abs(it - t) <= 45L }
        if (near != null) {
            updateActiveComposition("Remover Batida") { c ->
                c.copy(beatMarkersMs = c.beatMarkersMs.filterNot { it == near })
            }
            postBanner("Marcador de batida removido")
        } else {
            updateActiveComposition("Adicionar Batida") { c ->
                c.copy(beatMarkersMs = (c.beatMarkersMs + t).distinct().sorted())
            }
            postBanner("Marcador de batida adicionado")
        }
    }

    fun jumpToBeatMarker(direction: Int) {
        val beats = _document.value.activeComposition.beatMarkersMs.sorted()
        if (beats.isEmpty()) {
            postBanner("Nenhum marcador de batida. Toque em + Batida para marcar.")
            return
        }
        val t = _playheadMs.value
        val target = if (direction < 0) {
            beats.lastOrNull { it < t - 25L } ?: beats.last()
        } else {
            beats.firstOrNull { it > t + 25L } ?: beats.first()
        }
        seekTo(target)
    }

    fun toggleKeyframeAtPlayheadSelected() {
        val layer = getSelectedLayer() ?: return
        toggleKeyframeOnProperty(layer.id, _editingProperty.value)
    }

    fun jumpToLayerKeyframe(direction: Int) {
        val comp = _document.value.activeComposition
        val targetLayers = _selectedLayerIds.value.takeIf { it.isNotEmpty() }
            ?.let { ids -> comp.layers.filter { it.id in ids } }
            ?: comp.layers
        val times = targetLayers.flatMap { layer ->
            collectAllKeyframeMarkers(layer).map { marker ->
                layer.inMs + (marker.localTimeMs / layer.speedMultiplier.coerceAtLeast(0.05f)).toLong()
            }
        }.distinct().sorted()
        if (times.isEmpty()) {
            postBanner("Nenhum keyframe nesta camada.")
            return
        }
        val t = _playheadMs.value
        val target = if (direction < 0) {
            times.lastOrNull { it < t - 15L } ?: times.last()
        } else {
            times.firstOrNull { it > t + 15L } ?: times.first()
        }
        seekTo(target)
    }

    fun togglePreviewResolution() {
        _previewResolution.value = when (_previewResolution.value) {
            PreviewResolution.FULL -> PreviewResolution.HALF
            PreviewResolution.HALF -> PreviewResolution.QUARTER
            PreviewResolution.QUARTER -> PreviewResolution.FULL
        }
    }

    fun toggleSafetyGrid() {
        _showSafetyGrid.value = !_showSafetyGrid.value
    }

    fun toggleMagneticSnapping() {
        _magneticSnapping.value = !_magneticSnapping.value
    }

    fun toggleNudgeMode() {
        _nudgeMode.value = !_nudgeMode.value
    }

    fun togglePenTool() {
        _penToolActive.value = !_penToolActive.value
        if (_penToolActive.value) {
            _orbitMode.value = false
            _trackerTargetMode.value = false
            val selected = getSelectedLayer()
            if (selected == null || selected.type != LayerType.VECTOR || selected.vectorSpec?.preset != VectorShapePreset.FREEHAND) {
                addVectorLayer(VectorShapePreset.FREEHAND)
            }
            postBanner("Modo Caneta Bézier ativo: toque no canvas para traçar pontos")
        }
    }

    fun toggleOrbitMode() {
        _orbitMode.value = !_orbitMode.value
        if (_orbitMode.value) {
            _penToolActive.value = false
            _trackerTargetMode.value = false
            val comp = _document.value.activeComposition
            if (comp.layers.none { it.type == LayerType.CAMERA_3D }) {
                addUtilityLayer(LayerType.CAMERA_3D)
            }
            postBanner("Órbita 3D ativa: arraste no canvas para girar a câmera")
        }
    }

    fun toggleTrackerTargetMode() {
        _trackerTargetMode.value = !_trackerTargetMode.value
        if (_trackerTargetMode.value) {
            _penToolActive.value = false
            _orbitMode.value = false
            postBanner("Arraste a mira sobre o ponto a rastrear")
        }
    }

    fun setTrackerTarget(nx: Float, ny: Float) {
        _trackerTargetNorm.value = nx.coerceIn(-0.48f, 0.48f) to ny.coerceIn(-0.48f, 0.48f)
    }

    fun toggleLayerExpanded(layerId: String) {
        val cur = _expandedLayerIds.value.toMutableSet()
        if (cur.contains(layerId)) cur.remove(layerId) else cur.add(layerId)
        _expandedLayerIds.value = cur
    }

    fun toggleKeyframeSelection(keyframeId: String) {
        val cur = _selectedKeyframeIds.value.toMutableSet()
        if (cur.contains(keyframeId)) cur.remove(keyframeId) else cur.add(keyframeId)
        _selectedKeyframeIds.value = cur
    }

    fun clearKeyframeSelection() {
        _selectedKeyframeIds.value = emptySet()
    }

    fun selectLayer(layerId: String?, multiSelect: Boolean = false) {
        val pickSource = _pickWhipSourceId.value
        if (pickSource != null && layerId != null) {
            linkParentLayer(pickSource, if (pickSource == layerId) null else layerId)
            _pickWhipSourceId.value = null
            return
        }
        if (layerId == null) {
            _selectedLayerIds.value = emptySet()
            _activeInspector.value = null
            _penToolActive.value = false
        } else if (multiSelect) {
            val cur = _selectedLayerIds.value.toMutableSet()
            if (cur.contains(layerId)) cur.remove(layerId) else cur.add(layerId)
            _selectedLayerIds.value = cur
        } else {
            _selectedLayerIds.value = setOf(layerId)
        }
    }

    fun startPickWhip(layerId: String) {
        _pickWhipSourceId.value = if (_pickWhipSourceId.value == layerId) null else layerId
        if (_pickWhipSourceId.value != null) {
            postBanner("Toque em outra camada para definir como Pai (ou na mesma para desvincular)")
        }
    }

    fun openInspector(tab: InspectorTab?) {
        _activeInspector.value = if (_activeInspector.value == tab) null else tab
    }

    fun openGraphEditorFor(propKey: PropertyKey) {
        _editingProperty.value = propKey
        _activeInspector.value = InspectorTab.GRAPH_EDITOR
    }

    fun openExpressionEditorFor(propKey: PropertyKey) {
        _editingProperty.value = propKey
        _activeInspector.value = InspectorTab.EXPRESSION
    }

    // --- Undo / Redo & Mutation Pipeline ---

    fun undo() {
        val prev = undoRedo.undo(_document.value) ?: return
        _document.value = prev
        syncUndoFlags()
        refreshSceneEvaluation()
        scheduleAutoSave()
        postBanner("Desfeito")
    }

    fun redo() {
        val next = undoRedo.redo(_document.value) ?: return
        _document.value = next
        syncUndoFlags()
        refreshSceneEvaluation()
        scheduleAutoSave()
        postBanner("Refeito")
    }

    fun beginContinuousGesture() {
        if (dragSnapshotBeforeGesture == null) {
            dragSnapshotBeforeGesture = _document.value
        }
    }

    fun endContinuousGesture(label: String) {
        val prev = dragSnapshotBeforeGesture
        dragSnapshotBeforeGesture = null
        if (prev != null && prev != _document.value) {
            undoRedo.record(label, prev)
            syncUndoFlags()
            scheduleAutoSave()
        }
    }

    private fun applyMutation(label: String, recordUndo: Boolean = true, transform: (ProjectDocument) -> ProjectDocument) {
        val current = _document.value
        val updated = transform(current)
        if (updated == current) return
        if (recordUndo) {
            undoRedo.record(label, current)
            syncUndoFlags()
            scheduleAutoSave()
        }
        _document.value = updated
        refreshSceneEvaluation()
    }

    private fun updateActiveComposition(
        label: String,
        recordUndo: Boolean = true,
        transform: (CompositionNode) -> CompositionNode
    ) {
        applyMutation(label, recordUndo) { doc ->
            val compId = doc.activeCompositionId
            doc.copy(
                compositions = doc.compositions.map { c ->
                    if (c.id == compId) transform(c) else c
                }
            )
        }
    }

    private fun updateLayer(
        layerId: String,
        label: String,
        recordUndo: Boolean = true,
        transform: (LayerNode) -> LayerNode
    ) {
        updateActiveComposition(label, recordUndo) { comp ->
            comp.copy(layers = comp.layers.map { l -> if (l.id == layerId) transform(l) else l })
        }
    }

    private fun syncUndoFlags() {
        _canUndo.value = undoRedo.canUndo
        _canRedo.value = undoRedo.canRedo
    }

    private fun scheduleAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(450L)
            repository.saveProject(_document.value)
        }
    }

    fun refreshSceneEvaluation() {
        val doc = _document.value
        _evaluatedScene.value = SceneEvaluator.evaluateScene(doc, doc.activeComposition, _playheadMs.value)
    }

    fun getSelectedLayer(): LayerNode? {
        val id = primaryLayerId ?: return null
        return _document.value.activeComposition.layers.firstOrNull { it.id == id }
    }

    // --- Layer Creation ---

    fun addVectorLayer(preset: VectorShapePreset) {
        val comp = _document.value.activeComposition
        val nextZ = (comp.layers.maxOfOrNull { it.zIndex } ?: 0) + 1
        val newLayer = LayerNode(
            name = if (preset == VectorShapePreset.FREEHAND) "Traçado Bézier" else preset.labelPt,
            type = LayerType.VECTOR,
            inMs = 0L,
            outMs = comp.durationMs,
            zIndex = nextZ,
            vectorSpec = VectorSpec(
                preset = preset,
                width = 360f,
                height = 360f,
                points = if (preset == VectorShapePreset.FREEHAND) emptyList() else emptyList()
            )
        )
        updateActiveComposition("Adicionar Forma") { it.copy(layers = it.layers + newLayer) }
        _selectedLayerIds.value = setOf(newLayer.id)
    }

    fun addPenPointToSelected(localX: Float, localY: Float) {
        val layer = getSelectedLayer() ?: return
        val spec = layer.vectorSpec ?: return
        val pts = spec.points + VectorPoint(localX, localY)
        updateLayer(layer.id, "Ponto Bézier") {
            it.copy(vectorSpec = spec.copy(preset = VectorShapePreset.FREEHAND, closed = pts.size >= 3, points = pts))
        }
    }

    fun addTextLayer(text: String = "NOVO TEXTO") {
        val comp = _document.value.activeComposition
        val nextZ = (comp.layers.maxOfOrNull { it.zIndex } ?: 0) + 1
        val newLayer = LayerNode(
            name = text.take(18),
            type = LayerType.TEXT,
            inMs = 0L,
            outMs = comp.durationMs,
            zIndex = nextZ,
            textSpec = TextSpec(content = text)
        )
        updateActiveComposition("Adicionar Texto") { it.copy(layers = it.layers + newLayer) }
        _selectedLayerIds.value = setOf(newLayer.id)
        _activeInspector.value = InspectorTab.VECTOR_TEXT
    }

    fun addUtilityLayer(type: LayerType) {
        val comp = _document.value.activeComposition
        val nextZ = (comp.layers.maxOfOrNull { it.zIndex } ?: 0) + 1
        val newLayer = LayerNode(
            name = when (type) {
                LayerType.NULL -> "Objeto Nulo"
                LayerType.CAMERA_3D -> "Câmera 3D"
                LayerType.LIGHT_3D -> "Luz Pontual 3D"
                LayerType.ADJUSTMENT -> "Camada de Ajuste"
                else -> type.labelPt
            },
            type = type,
            inMs = 0L,
            outMs = comp.durationMs,
            zIndex = nextZ,
            is3D = (type == LayerType.LIGHT_3D || type == LayerType.CAMERA_3D),
            transform = if (type == LayerType.LIGHT_3D) {
                TransformSpec(posX = AnimatableProperty(180f), posY = AnimatableProperty(-240f), posZ = AnimatableProperty(-360f))
            } else TransformSpec(),
            cameraSpec = if (type == LayerType.CAMERA_3D) CameraSpec() else null,
            lightSpec = if (type == LayerType.LIGHT_3D) LightSpec() else null,
            effects = if (type == LayerType.ADJUSTMENT) listOf(EffectNode(type = EffectType.COLOR_GRADING)) else emptyList()
        )
        updateActiveComposition("Adicionar ${type.labelPt}") {
            val updatedCam = if (type == LayerType.CAMERA_3D) newLayer.id else it.activeCameraId
            it.copy(activeCameraId = updatedCam, layers = it.layers + newLayer)
        }
        _selectedLayerIds.value = setOf(newLayer.id)
        if (type == LayerType.CAMERA_3D || type == LayerType.LIGHT_3D) {
            _activeInspector.value = InspectorTab.CAMERA_LIGHT
        }
    }

    fun importVisualMedia(uri: Uri, isVideo: Boolean) {
        viewModelScope.launch {
            val ext = if (isVideo) "mp4" else "jpg"
            val localUriStr = repository.copyMediaToLocalStorage(uri, if (isVideo) "vid" else "img", ext)
            var durationMs = 5000L
            var w = 1080
            var h = 1920
            if (isVideo) {
                runCatching {
                    val ret = MediaMetadataRetriever()
                    ret.setDataSource(getApplication(), Uri.parse(localUriStr))
                    durationMs = ret.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 5000L
                    w = ret.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1080
                    h = ret.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 1920
                    ret.release()
                }
            }
            val asset = AssetItem(
                name = if (isVideo) "Vídeo Importado" else "Imagem Importada",
                type = if (isVideo) AssetType.VIDEO else AssetType.IMAGE,
                uri = localUriStr,
                durationMs = durationMs,
                width = w,
                height = h
            )
            val comp = _document.value.activeComposition
            val nextZ = (comp.layers.maxOfOrNull { it.zIndex } ?: 0) + 1
            val layer = LayerNode(
                name = asset.name,
                type = if (isVideo) LayerType.VIDEO else LayerType.IMAGE,
                assetId = asset.id,
                inMs = _playheadMs.value,
                outMs = (_playheadMs.value + durationMs).coerceAtMost(comp.durationMs.coerceAtLeast(_playheadMs.value + durationMs)),
                zIndex = nextZ
            )
            applyMutation("Importar Mídia") { doc ->
                val c = doc.activeComposition
                val newDur = maxOf(c.durationMs, layer.outMs)
                doc.copy(
                    assets = doc.assets + asset,
                    compositions = doc.compositions.map {
                        if (it.id == doc.activeCompositionId) it.copy(durationMs = newDur, layers = it.layers + layer) else it
                    }
                )
            }
            _selectedLayerIds.value = setOf(layer.id)
            postBanner("${asset.name} adicionada!")
        }
    }

    fun importAudioTrack(uri: Uri) {
        viewModelScope.launch {
            postBanner("Analisando áudio e batidas...")
            val localUriStr = repository.copyMediaToLocalStorage(uri, "aud", "m4a")
            val analysis = AudioAnalyzer.analyzeAudio(getApplication(), Uri.parse(localUriStr))
            val asset = AssetItem(
                name = "Trilha de Áudio",
                type = AssetType.AUDIO,
                uri = localUriStr,
                durationMs = analysis.durationMs,
                waveform = analysis.waveform,
                detectedBeatsMs = analysis.beatMarkersMs
            )
            val comp = _document.value.activeComposition
            val nextZ = (comp.layers.maxOfOrNull { it.zIndex } ?: 0) + 1
            val audioLayer = LayerNode(
                name = "Áudio (${analysis.beatMarkersMs.size} batidas)",
                type = LayerType.AUDIO,
                assetId = asset.id,
                inMs = 0L,
                outMs = minOf(comp.durationMs, analysis.durationMs),
                zIndex = nextZ,
                audioSpec = AudioSpec(visualizerEnabled = false)
            )
            applyMutation("Importar Áudio") { doc ->
                doc.copy(
                    assets = doc.assets + asset,
                    compositions = doc.compositions.map { c ->
                        if (c.id == doc.activeCompositionId) {
                            c.copy(
                                beatMarkersMs = (c.beatMarkersMs + analysis.beatMarkersMs).distinct().sorted(),
                                layers = c.layers + audioLayer
                            )
                        } else c
                    }
                )
            }
            _selectedLayerIds.value = setOf(audioLayer.id)
            postBanner("Áudio importado: ${analysis.beatMarkersMs.size} batidas detectadas!")
        }
    }

    // --- Direct Manipulation & Transform Mutations ---

    fun updateLayerPosition(layerId: String, newX: Float, newY: Float, recordUndo: Boolean = false) {
        val layer = _document.value.activeComposition.layers.firstOrNull { it.id == layerId } ?: return
        val localT = ((_playheadMs.value - layer.inMs) * layer.speedMultiplier).toLong()
        updateLayer(layerId, "Mover Camada", recordUndo) { l ->
            val px = updatePropValueAtTime(l.transform.posX, localT, newX)
            val py = updatePropValueAtTime(l.transform.posY, localT, newY)
            l.copy(transform = l.transform.copy(posX = px, posY = py))
        }
    }

    fun updateLayerAnchorPoint(layerId: String, deltaAnchorX: Float, deltaAnchorY: Float) {
        val layer = _document.value.activeComposition.layers.firstOrNull { it.id == layerId } ?: return
        val localT = ((_playheadMs.value - layer.inMs) * layer.speedMultiplier).toLong()
        val curAx = SceneEvaluator.evaluateProperty(layer.transform.anchorX, localT, _document.value.activeComposition, 0f)
        val curAy = SceneEvaluator.evaluateProperty(layer.transform.anchorY, localT, _document.value.activeComposition, 0f)
        val curPx = SceneEvaluator.evaluateProperty(layer.transform.posX, localT, _document.value.activeComposition, 0f)
        val curPy = SceneEvaluator.evaluateProperty(layer.transform.posY, localT, _document.value.activeComposition, 0f)

        updateLayer(layerId, "Ajustar Âncora", recordUndo = false) { l ->
            l.copy(
                transform = l.transform.copy(
                    anchorX = updatePropValueAtTime(l.transform.anchorX, localT, curAx + deltaAnchorX),
                    anchorY = updatePropValueAtTime(l.transform.anchorY, localT, curAy + deltaAnchorY),
                    // Compensate world position so the layer doesn't jump visually when moving pivot
                    posX = updatePropValueAtTime(l.transform.posX, localT, curPx + deltaAnchorX),
                    posY = updatePropValueAtTime(l.transform.posY, localT, curPy + deltaAnchorY)
                )
            )
        }
    }

    fun nudgeSelected(dx: Float, dy: Float, dRot: Float = 0f) {
        val layer = getSelectedLayer() ?: return
        val localT = ((_playheadMs.value - layer.inMs) * layer.speedMultiplier).toLong()
        val comp = _document.value.activeComposition
        val curX = SceneEvaluator.evaluateProperty(layer.transform.posX, localT, comp, 0f)
        val curY = SceneEvaluator.evaluateProperty(layer.transform.posY, localT, comp, 0f)
        val curR = SceneEvaluator.evaluateProperty(layer.transform.rotZ, localT, comp, 0f)
        updateLayer(layer.id, "Ajuste Fino") { l ->
            l.copy(
                transform = l.transform.copy(
                    posX = updatePropValueAtTime(l.transform.posX, localT, curX + dx),
                    posY = updatePropValueAtTime(l.transform.posY, localT, curY + dy),
                    rotZ = if (dRot != 0f) updatePropValueAtTime(l.transform.rotZ, localT, curR + dRot) else l.transform.rotZ
                )
            )
        }
    }

    fun setPropertyScalar(layerId: String, propKey: PropertyKey, value: Float, recordUndo: Boolean = false) {
        val layer = _document.value.activeComposition.layers.firstOrNull { it.id == layerId } ?: return
        val localT = ((_playheadMs.value - layer.inMs) * layer.speedMultiplier).toLong()
        updateLayer(layerId, "Editar ${propKey.labelPt}", recordUndo) { l ->
            mutateLayerProperty(l, propKey) { prop -> updatePropValueAtTime(prop, localT, value) }
        }
    }

    fun toggleKeyframeOnProperty(layerId: String, propKey: PropertyKey) {
        val layer = _document.value.activeComposition.layers.firstOrNull { it.id == layerId } ?: return
        val localT = ((_playheadMs.value - layer.inMs) * layer.speedMultiplier).toLong()
        val comp = _document.value.activeComposition
        updateLayer(layerId, "Keyframe ${propKey.labelPt}") { l ->
            mutateLayerProperty(l, propKey) { prop ->
                val curVal = SceneEvaluator.evaluateProperty(prop, localT, comp, 0f)
                CurveInterpolator.toggleKeyframeAt(prop, localT, curVal)
            }
        }
    }

    fun updateKeyframeBezier(layerId: String, propKey: PropertyKey, keyframeId: String, bezier: BezierCurve) {
        updateLayer(layerId, "Editar Curva Bézier") { l ->
            mutateLayerProperty(l, propKey) { prop ->
                prop.copy(
                    keyframes = prop.keyframes.map { k ->
                        if (k.id == keyframeId) k.copy(easing = EasingType.BEZIER, bezier = bezier) else k
                    }
                )
            }
        }
    }

    fun moveKeyframeTime(layerId: String, propKey: PropertyKey, keyframeId: String, newLocalTimeMs: Long) {
        moveAnyKeyframeTime(
            layerId = layerId,
            target = SubTrackTarget.TransformProp(propKey),
            keyframeId = keyframeId,
            newCompTimeMs = (_document.value.activeComposition.layers.firstOrNull { it.id == layerId }?.inMs ?: 0L) + newLocalTimeMs
        )
    }

    fun moveAnyKeyframeTime(
        layerId: String,
        target: SubTrackTarget,
        keyframeId: String,
        newCompTimeMs: Long
    ) {
        val layer = _document.value.activeComposition.layers.firstOrNull { it.id == layerId } ?: return
        val localMs = (((newCompTimeMs - layer.inMs).coerceAtLeast(0L)) * layer.speedMultiplier.coerceAtLeast(0.05f)).toLong()
        updateLayer(layerId, "Mover Keyframe", recordUndo = false) { l ->
            mutateTargetProperty(l, target) { prop ->
                prop.copy(
                    keyframes = prop.keyframes.map { k ->
                        if (k.id == keyframeId) k.copy(timeMs = localMs.coerceAtLeast(0L)) else k
                    }.sortedBy { it.timeMs }
                )
            }
        }
    }

    private fun mutateTargetProperty(
        layer: LayerNode,
        target: SubTrackTarget,
        fn: (AnimatableProperty) -> AnimatableProperty
    ): LayerNode = when (target) {
        is SubTrackTarget.TransformProp -> mutateLayerProperty(layer, target.key, fn)
        is SubTrackTarget.EffectParamProp -> layer.copy(
            effects = layer.effects.map { eff ->
                if (eff.id == target.effectId) {
                    val existing = eff.params[target.paramKey] ?: AnimatableProperty(0f)
                    eff.copy(params = eff.params + (target.paramKey to fn(existing)))
                } else eff
            }
        )
        SubTrackTarget.VectorTrimEnd -> layer.copy(
            vectorSpec = (layer.vectorSpec ?: VectorSpec()).let { it.copy(trimEnd = fn(it.trimEnd)) }
        )
        SubTrackTarget.TextRangeStart -> layer.copy(
            textSpec = (layer.textSpec ?: TextSpec()).let { it.copy(rangeStart = fn(it.rangeStart)) }
        )
        SubTrackTarget.TextRangeEnd -> layer.copy(
            textSpec = (layer.textSpec ?: TextSpec()).let { it.copy(rangeEnd = fn(it.rangeEnd)) }
        )
        SubTrackTarget.SpeedRampProp -> layer.copy(
            speedRamp = layer.speedRamp?.let(fn)
        )
    }

    fun copyCurve(bezier: BezierCurve) {
        _copiedCurve.value = bezier
        postBanner("Curva copiada!")
    }

    fun saveCustomCurve(name: String, bezier: BezierCurve) {
        viewModelScope.launch {
            repository.saveCustomCurve(name, bezier)
            postBanner("Curva salva na biblioteca!")
        }
    }

    fun updateExpression(layerId: String, propKey: PropertyKey, expr: String?) {
        updateLayer(layerId, "Expressão ${propKey.labelPt}") { l ->
            mutateLayerProperty(l, propKey) { prop ->
                prop.copy(expression = expr?.takeIf { it.isNotBlank() })
            }
        }
    }

    fun validateExpression(expr: String, propKey: PropertyKey): String? {
        if (expr.isBlank()) return null
        val layer = getSelectedLayer() ?: return null
        val prop = readLayerProperty(layer, propKey)
        val ctx = ExpressionContext(
            timeMs = _playheadMs.value,
            fps = _document.value.activeComposition.fps,
            durationMs = _document.value.activeComposition.durationMs,
            baseKeyframeValue = prop.baseValue,
            currentProperty = prop,
            composition = _document.value.activeComposition
        )
        return ExpressionEvaluator.evaluate(expr, ctx).error
    }

    // --- Layer Actions (Split, Duplicate, Delete, Pre-Compose, Flags, Effects, Masks, Blend) ---

    fun splitSelectedLayerAtPlayhead() {
        val layer = getSelectedLayer() ?: return
        val t = _playheadMs.value
        if (t <= layer.inMs + 80L || t >= layer.outMs - 80L) {
            postBanner("Posicione a agulha dentro da duração da camada para dividir.")
            return
        }
        val firstHalf = layer.copy(outMs = t)
        val secondHalf = regenerateKeyframeIds(
            layer.copy(
                id = UUID.randomUUID().toString(),
                name = "${layer.name} (2)",
                inMs = t,
                startOffsetMs = layer.startOffsetMs + (t - layer.inMs),
                zIndex = layer.zIndex + 1
            )
        )
        updateActiveComposition("Dividir Camada") { comp ->
            comp.copy(
                layers = comp.layers.flatMap { l ->
                    if (l.id == layer.id) listOf(firstHalf, secondHalf) else listOf(l)
                }
            )
        }
        _selectedLayerIds.value = setOf(secondHalf.id)
        postBanner("Camada dividida em ${t}ms")
    }

    fun duplicateSelectedLayer() {
        val layer = getSelectedLayer() ?: return
        val comp = _document.value.activeComposition
        val nextZ = (comp.layers.maxOfOrNull { it.zIndex } ?: 0) + 1
        val copy = regenerateKeyframeIds(
            layer.copy(
                id = UUID.randomUUID().toString(),
                name = "${layer.name} (Cópia)",
                zIndex = nextZ
            )
        )
        updateActiveComposition("Duplicar Camada") { it.copy(layers = it.layers + copy) }
        _selectedLayerIds.value = setOf(copy.id)
    }

    fun deleteSelectedLayer() {
        val ids = _selectedLayerIds.value
        if (ids.isEmpty()) return
        updateActiveComposition("Excluir Camada") { comp ->
            comp.copy(
                layers = comp.layers
                    .filterNot { it.id in ids }
                    .map { l -> if (l.parentId in ids) l.copy(parentId = null) else l }
            )
        }
        _selectedLayerIds.value = emptySet()
        _activeInspector.value = null
    }

    fun preComposeSelectedLayers() {
        val ids = _selectedLayerIds.value
        if (ids.isEmpty()) return
        val doc = _document.value
        val activeComp = doc.activeComposition
        val selected = activeComp.layers.filter { it.id in ids }
        if (selected.isEmpty()) return

        val subCompId = "precomp_${UUID.randomUUID().toString().take(6)}"
        val minIn = selected.minOf { it.inMs }
        val maxOut = selected.maxOf { it.outMs }
        val subComp = CompositionNode(
            id = subCompId,
            name = "Pré-Comp (${selected.size} camadas)",
            width = activeComp.width,
            height = activeComp.height,
            fps = activeComp.fps,
            durationMs = (maxOut - minIn).coerceAtLeast(1000L),
            backgroundColorHex = "#00000000",
            layers = selected.mapIndexed { idx, l -> l.copy(zIndex = idx, inMs = (l.inMs - minIn).coerceAtLeast(0L), outMs = (l.outMs - minIn).coerceAtLeast(500L)) }
        )
        val preCompLayer = LayerNode(
            name = subComp.name,
            type = LayerType.PRE_COMP,
            preCompId = subCompId,
            inMs = minIn,
            outMs = maxOut,
            zIndex = selected.maxOf { it.zIndex }
        )
        applyMutation("Pré-compor Camadas") { d ->
            d.copy(
                compositions = d.compositions.map { c ->
                    if (c.id == d.activeCompositionId) {
                        c.copy(layers = c.layers.filterNot { it.id in ids } + preCompLayer)
                    } else c
                } + subComp
            )
        }
        _selectedLayerIds.value = setOf(preCompLayer.id)
        postBanner("Pré-composição criada!")
    }

    fun enterPreComp(preCompId: String) {
        _document.value = _document.value.copy(activeCompositionId = preCompId)
        _selectedLayerIds.value = emptySet()
        _playheadMs.value = 0L
        refreshSceneEvaluation()
    }

    fun returnToMainComposition() {
        val mainId = _document.value.compositions.firstOrNull()?.id ?: return
        _document.value = _document.value.copy(activeCompositionId = mainId)
        _selectedLayerIds.value = emptySet()
        _playheadMs.value = 0L
        refreshSceneEvaluation()
    }

    fun toggleLayerVisibility(layerId: String) = updateLayer(layerId, "Visibilidade") { it.copy(visible = !it.visible) }
    fun toggleLayerLock(layerId: String) = updateLayer(layerId, "Travar") { it.copy(locked = !it.locked) }
    fun toggleLayerSolo(layerId: String) = updateLayer(layerId, "Solo") { it.copy(solo = !it.solo) }
    fun toggleLayer3D(layerId: String) = updateLayer(layerId, "Modo 3D") { it.copy(is3D = !it.is3D) }

    fun linkParentLayer(childId: String, parentId: String?) {
        updateLayer(childId, "Vincular Pai") { it.copy(parentId = parentId) }
        postBanner(if (parentId == null) "Parentesco removido" else "Camada vinculada!")
    }

    fun moveLayerZOrder(layerId: String, delta: Int) {
        val comp = _document.value.activeComposition
        val sorted = comp.layers.sortedBy { it.zIndex }.toMutableList()
        val idx = sorted.indexOfFirst { it.id == layerId }
        val target = (idx + delta).coerceIn(0, sorted.lastIndex)
        if (idx < 0 || idx == target) return
        val item = sorted.removeAt(idx)
        sorted.add(target, item)
        updateActiveComposition("Reordenar Camada") { c ->
            c.copy(layers = sorted.mapIndexed { i, l -> l.copy(zIndex = i) })
        }
    }

    fun reorderLayerToVisualIndex(layerId: String, targetVisualIndex: Int) {
        val comp = _document.value.activeComposition
        val visualList = comp.layers.sortedByDescending { it.zIndex }.toMutableList()
        val curIdx = visualList.indexOfFirst { it.id == layerId }
        if (curIdx < 0) return
        val clampedTarget = targetVisualIndex.coerceIn(0, visualList.lastIndex)
        if (curIdx == clampedTarget) return
        val moved = visualList.removeAt(curIdx)
        visualList.add(clampedTarget, moved)
        val total = visualList.size
        updateActiveComposition("Reordenar Camada", recordUndo = false) { c ->
            c.copy(layers = visualList.mapIndexed { i, l -> l.copy(zIndex = total - 1 - i) })
        }
    }

    fun freezeFrameAtPlayhead() {
        val layer = getSelectedLayer() ?: return
        val t = _playheadMs.value.coerceIn(layer.inMs, layer.outMs)
        val elapsed = (t - layer.inMs).coerceAtLeast(0L)
        val frozenLocalTime = SpeedRampEvaluator.integrateRemappedTimeMs(layer.speedRamp, elapsed, layer.speedMultiplier) + layer.startOffsetMs
        val freezeDur = 1200L
        val frozenLayer = regenerateKeyframeIds(
            layer.copy(
                id = UUID.randomUUID().toString(),
                name = "${layer.name.take(12)} [Congelado]",
                inMs = t,
                outMs = t + freezeDur,
                startOffsetMs = frozenLocalTime,
                speedMultiplier = 0f,
                speedRamp = null,
                zIndex = layer.zIndex + 1
            )
        )
        val hasTail = t < layer.outMs - 100L && t > layer.inMs + 80L
        updateActiveComposition("Congelar Quadro") { comp ->
            val updatedLayers = if (hasTail) {
                val head = layer.copy(outMs = t)
                val tail = regenerateKeyframeIds(
                    layer.copy(
                        id = UUID.randomUUID().toString(),
                        name = "${layer.name} (Cont.)",
                        inMs = t + freezeDur,
                        outMs = layer.outMs + freezeDur,
                        startOffsetMs = frozenLocalTime,
                        zIndex = layer.zIndex + 2
                    )
                )
                comp.layers.flatMap { l -> if (l.id == layer.id) listOf(head, frozenLayer, tail) else listOf(l) }
            } else {
                comp.layers + frozenLayer
            }
            val requiredDur = maxOf(comp.durationMs, updatedLayers.maxOfOrNull { it.outMs } ?: comp.durationMs)
            comp.copy(durationMs = requiredDur, layers = updatedLayers)
        }
        _selectedLayerIds.value = setOf(frozenLayer.id)
        postBanner("Quadro congelado por 1.2s na agulha!")
    }

    private fun regenerateKeyframeIds(layer: LayerNode): LayerNode =
        transformAllLayerProperties(layer) { prop ->
            if (prop.keyframes.isEmpty()) prop
            else prop.copy(keyframes = prop.keyframes.map { it.copy(id = UUID.randomUUID().toString()) })
        }

    fun updateLayerBounds(layerId: String, inMs: Long, outMs: Long, recordUndo: Boolean = false) {
        val dur = _document.value.activeComposition.durationMs
        val clampedIn = inMs.coerceIn(0L, dur - 200L)
        val clampedOut = outMs.coerceAtLeast(clampedIn + 200L)
        if (_rippleEdit.value) {
            updateActiveComposition("Ripple", recordUndo) { comp ->
                StudioEditOps.rippleTrim(comp, layerId, clampedIn, clampedOut)
            }
        } else {
            val hardOut = clampedOut.coerceAtMost(dur)
            updateLayer(layerId, "Aparar Clipe", recordUndo) { it.copy(inMs = clampedIn, outMs = hardOut) }
        }
    }

    fun toggleRippleEdit() {
        _rippleEdit.value = !_rippleEdit.value
        postBanner(if (_rippleEdit.value) "Ripple ligado — aparar empurra o resto" else "Ripple desligado")
    }

    fun slipSelected(deltaMs: Long) {
        val layer = getSelectedLayer() ?: return
        val assetDur = _document.value.assets.firstOrNull { it.id == layer.assetId }?.durationMs
            ?: (layer.outMs - layer.inMs + layer.startOffsetMs + 4_000L)
        val slipped = StudioEditOps.slipClip(layer, deltaMs, assetDur)
        if (slipped === layer) {
            postBanner("Slip no limite da mídia")
            return
        }
        updateLayer(layer.id, "Slip") { slipped }
    }

    fun setWorkAreaInAtPlayhead() {
        updateActiveComposition("Área B") { StudioEditOps.setWorkAreaIn(it, _playheadMs.value) }
        postBanner("Início da área de trabalho")
    }

    fun setWorkAreaOutAtPlayhead() {
        updateActiveComposition("Área N") { StudioEditOps.setWorkAreaOut(it, _playheadMs.value) }
        postBanner("Fim da área de trabalho")
    }

    fun clearWorkArea() {
        updateActiveComposition("Limpar área") { StudioEditOps.clearWorkArea(it) }
        postBanner("Área de trabalho = composição inteira")
    }

    fun seekToTimecode(raw: String) {
        val fps = _document.value.activeComposition.fps
        val parsed = StudioEditOps.parseTimecode(raw, fps)
        if (parsed == null) {
            postBanner("Timecode inválido. Use mm:ss:ff")
            return
        }
        seekTo(parsed)
    }

    fun setBlendMode(layerId: String, mode: BlendMode) {
        updateLayer(layerId, "Modo de Mesclagem") { it.copy(blendMode = mode) }
    }

    fun addEffect(layerId: String, type: EffectType) {
        updateLayer(layerId, "Adicionar ${type.labelPt}") { l ->
            l.copy(effects = l.effects + EffectNode(type = type))
        }
    }

    fun toggleEffectEnabled(layerId: String, effectId: String) {
        updateLayer(layerId, "Alternar Efeito") { l ->
            l.copy(effects = l.effects.map { e -> if (e.id == effectId) e.copy(enabled = !e.enabled) else e })
        }
    }

    fun removeEffect(layerId: String, effectId: String) {
        updateLayer(layerId, "Remover Efeito") { l ->
            l.copy(effects = l.effects.filterNot { it.id == effectId })
        }
    }

    fun moveEffectOrder(layerId: String, effectId: String, delta: Int) {
        updateLayer(layerId, "Reordenar Efeito") { l ->
            val list = l.effects.toMutableList()
            val idx = list.indexOfFirst { it.id == effectId }
            val dst = (idx + delta).coerceIn(0, list.lastIndex)
            if (idx >= 0 && idx != dst) {
                val e = list.removeAt(idx)
                list.add(dst, e)
            }
            l.copy(effects = list)
        }
    }

    fun updateEffectParam(layerId: String, effectId: String, paramKey: String, value: Float, recordUndo: Boolean = false) {
        val layer = _document.value.activeComposition.layers.firstOrNull { it.id == layerId } ?: return
        val localT = ((_playheadMs.value - layer.inMs) * layer.speedMultiplier).toLong()
        updateLayer(layerId, "Ajustar Efeito", recordUndo) { l ->
            l.copy(
                effects = l.effects.map { e ->
                    if (e.id == effectId) {
                        val curProp = e.params[paramKey] ?: AnimatableProperty(value)
                        val updatedProp = updatePropValueAtTime(curProp, localT, value)
                        e.copy(params = e.params + (paramKey to updatedProp))
                    } else e
                }
            )
        }
    }

    fun toggleEffectParamKeyframe(layerId: String, effectId: String, paramKey: String) {
        val layer = _document.value.activeComposition.layers.firstOrNull { it.id == layerId } ?: return
        val localT = ((_playheadMs.value - layer.inMs) * layer.speedMultiplier).toLong()
        val comp = _document.value.activeComposition
        updateLayer(layerId, "Keyframe de Efeito") { l ->
            l.copy(
                effects = l.effects.map { e ->
                    if (e.id == effectId) {
                        val curProp = e.params[paramKey] ?: AnimatableProperty(0f)
                        val curVal = SceneEvaluator.evaluateProperty(curProp, localT, comp, 0f)
                        e.copy(params = e.params + (paramKey to CurveInterpolator.toggleKeyframeAt(curProp, localT, curVal)))
                    } else e
                }
            )
        }
    }

    fun addOrUpdateMask(layerId: String, shape: MaskShape, mode: MaskMode, feather: Float, width: Float, height: Float, inverted: Boolean) {
        updateLayer(layerId, "Configurar Máscara") { l ->
            val current = l.masks.firstOrNull() ?: MaskSpec()
            val updated = current.copy(
                shape = shape,
                mode = mode,
                inverted = inverted,
                feather = AnimatableProperty(feather),
                width = AnimatableProperty(width),
                height = AnimatableProperty(height)
            )
            l.copy(masks = listOf(updated))
        }
    }

    fun clearMask(layerId: String) {
        updateLayer(layerId, "Remover Máscara") { it.copy(masks = emptyList()) }
    }

    fun updateTextSpec(layerId: String, transform: (TextSpec) -> TextSpec) {
        updateLayer(layerId, "Editar Texto") { l ->
            val ts = l.textSpec ?: TextSpec()
            l.copy(name = transform(ts).content.take(18).ifBlank { l.name }, textSpec = transform(ts))
        }
    }

    fun updateVectorSpec(layerId: String, transform: (VectorSpec) -> VectorSpec) {
        updateLayer(layerId, "Editar Vetor") { l ->
            val vs = l.vectorSpec ?: VectorSpec()
            l.copy(vectorSpec = transform(vs))
        }
    }

    fun updateAudioSpec(layerId: String, transform: (AudioSpec) -> AudioSpec) {
        updateLayer(layerId, "Configurar Áudio") { l ->
            val spec = l.audioSpec ?: AudioSpec()
            l.copy(audioSpec = transform(spec))
        }
    }

    fun applyAnimationPreset(layerId: String, presetId: String) {
        val layer = _document.value.activeComposition.layers.firstOrNull { it.id == layerId } ?: return
        val t0 = 0L
        val t1 = 650L
        val snappy = BezierCurve(0.16f, 1f, 0.3f, 1f)
        val overshoot = BezierCurve(0.34f, 1.56f, 0.64f, 1f)

        updateLayer(layerId, "Aplicar Preset de Animação") { l ->
            when (presetId) {
                "pop_spring" -> l.copy(
                    transform = l.transform.copy(
                        scaleX = AnimatableProperty(1f, listOf(Keyframe(timeMs = t0, value = 0.2f, easing = EasingType.BEZIER, bezier = overshoot), Keyframe(timeMs = t1, value = 1f))),
                        scaleY = AnimatableProperty(1f, listOf(Keyframe(timeMs = t0, value = 0.2f, easing = EasingType.BEZIER, bezier = overshoot), Keyframe(timeMs = t1, value = 1f))),
                        opacity = AnimatableProperty(1f, listOf(Keyframe(timeMs = t0, value = 0f), Keyframe(timeMs = 260L, value = 1f)))
                    )
                )
                "slide_up" -> l.copy(
                    transform = l.transform.copy(
                        posY = AnimatableProperty(0f, listOf(Keyframe(timeMs = t0, value = 320f, easing = EasingType.BEZIER, bezier = snappy), Keyframe(timeMs = t1, value = 0f))),
                        opacity = AnimatableProperty(1f, listOf(Keyframe(timeMs = t0, value = 0f), Keyframe(timeMs = 350L, value = 1f)))
                    )
                )
                "spin_360" -> l.copy(
                    transform = l.transform.copy(
                        rotZ = AnimatableProperty(0f, listOf(Keyframe(timeMs = t0, value = -180f, easing = EasingType.BEZIER, bezier = snappy), Keyframe(timeMs = 900L, value = 0f)))
                    )
                )
                "beat_pulse" -> l.copy(
                    transform = l.transform.copy(
                        scaleX = AnimatableProperty(1f, expression = "1 + audioBass * 0.22"),
                        scaleY = AnimatableProperty(1f, expression = "1 + audioBass * 0.22")
                    )
                )
                else -> l
            }
        }
        postBanner("Animação aplicada!")
    }

    fun applyTypePreset(layerId: String, presetId: String) {
        val layer = _document.value.activeComposition.layers.firstOrNull { it.id == layerId } ?: return
        if (layer.type != LayerType.TEXT || layer.textSpec == null) {
            postBanner("Presets de type pedem uma camada de texto")
            return
        }
        val next = TypePresets.apply(layer, presetId)
        if (next === layer) return
        updateLayer(layerId, "Preset de type") { next }
        _playheadMs.value = layer.inMs
        postBanner("Type: ${TypePresets.info(presetId)?.name ?: presetId}")
    }

    fun importCustomFont(uri: Uri) {
        val layerId = primaryLayerId ?: return
        val current = _document.value.activeComposition.layers.firstOrNull { it.id == layerId }
        if (current?.type != LayerType.TEXT) {
            postBanner("Selecione um texto para aplicar a fonte")
            return
        }
        viewModelScope.launch {
            val local = repository.copyMediaToLocalStorage(uri, "font", "ttf")
            updateLayer(layerId, "Fonte TTF") { l ->
                val ts = l.textSpec ?: return@updateLayer l
                l.copy(textSpec = ts.copy(customFontUri = local, fontFamilyKey = com.example.model.StudioFonts.CUSTOM_KEY))
            }
            postBanner("Fonte TTF aplicada na camada")
        }
    }

    fun setCompositionAspect(preset: AspectRatioPreset) {
        updateActiveComposition("Alterar Proporção") { c ->
            c.copy(width = preset.width, height = preset.height)
        }
    }

    fun setCompositionBackground(hex: String) {
        updateActiveComposition("Cor de Fundo") { c -> c.copy(backgroundColorHex = hex) }
    }

    // --- Phase 5: 3D Camera / Light / DOF ---

    fun updateCameraSpec(layerId: String, transform: (CameraSpec) -> CameraSpec) {
        updateLayer(layerId, "Configurar Câmera 3D") { l ->
            val cs = l.cameraSpec ?: CameraSpec()
            l.copy(cameraSpec = transform(cs))
        }
    }

    fun orbitActiveCamera(dAzimuth: Float, dElevation: Float) {
        val comp = _document.value.activeComposition
        val cam = comp.layers.firstOrNull { it.id == comp.activeCameraId || it.type == LayerType.CAMERA_3D } ?: return
        updateLayer(cam.id, "Órbita 3D", recordUndo = false) { l ->
            val cs = l.cameraSpec ?: CameraSpec()
            l.copy(
                cameraSpec = cs.copy(
                    enablePerspective = true,
                    orbitAzimuth = (cs.orbitAzimuth + dAzimuth).coerceIn(-85f, 85f),
                    orbitElevation = (cs.orbitElevation + dElevation).coerceIn(-65f, 65f)
                )
            )
        }
    }

    fun updateLightSpec(layerId: String, transform: (LightSpec) -> LightSpec) {
        updateLayer(layerId, "Configurar Luz 3D") { l ->
            val ls = l.lightSpec ?: LightSpec()
            l.copy(lightSpec = transform(ls))
        }
    }

    // --- Phase 5: Color Grading (RGB Curves + .cube LUTs) ---

    fun updateColorGrading(layerId: String, transform: (ColorGradingSpec) -> ColorGradingSpec) {
        updateLayer(layerId, "Color Grading / Curvas") { l ->
            val cg = l.colorGrading ?: ColorGradingSpec()
            l.copy(colorGrading = transform(cg))
        }
    }

    fun importCubeLut(layerId: String, uri: Uri) {
        viewModelScope.launch {
            val localUriStr = repository.copyMediaToLocalStorage(uri, "lut", "cube")
            val parsed = CubeLutParser.parseCubeFromUri(getApplication(), Uri.parse(localUriStr))
            if (parsed == null) {
                postBanner("Arquivo .cube inválido ou corrompido.")
                return@launch
            }
            updateColorGrading(layerId) {
                it.copy(lutCubeUri = localUriStr, lutPresetId = null, lutName = parsed.name)
            }
            postBanner("LUT 3D '${parsed.name}' (${parsed.size}³) aplicada!")
        }
    }

    // --- Phase 5: Speed Ramping ---

    fun updateSpeedRamp(layerId: String, prop: AnimatableProperty?) {
        updateLayer(layerId, "Speed Ramp") { l -> l.copy(speedRamp = prop) }
    }

    fun applySpeedRampPreset(layerId: String, presetId: String) {
        val layer = _document.value.activeComposition.layers.firstOrNull { it.id == layerId } ?: return
        val dur = (layer.outMs - layer.inMs).coerceAtLeast(500L)
        val preset = SpeedRampEvaluator.presets(dur).firstOrNull { it.id == presetId } ?: return
        updateSpeedRamp(layerId, preset.property)
        postBanner("Rampa '${preset.labelPt}' aplicada!")
    }

    // --- Phase 5: Offline AI (ML Kit Rotoscope, Auto-Captions) & 2D Motion Tracker ---

    fun runAutoCutout(layerId: String) {
        val layer = _document.value.activeComposition.layers.firstOrNull { it.id == layerId } ?: return
        val asset = _document.value.assets.firstOrNull { it.id == layer.assetId }
        if (asset == null || (asset.type != AssetType.IMAGE && asset.type != AssetType.VIDEO)) {
            postBanner("Selecione uma camada de Imagem ou Vídeo para o Recorte IA.")
            return
        }
        viewModelScope.launch {
            _isProcessingAi.value = true
            postBanner("Processando Recorte IA offline...")
            val localT = (_playheadMs.value - layer.inMs).coerceAtLeast(0L)
            val result = MlKitRotoscoper.generateCutoutMask(getApplication(), asset, localT)
            _isProcessingAi.value = false
            result.onSuccess { maskUri ->
                updateLayer(layerId, "Recorte IA") { it.copy(aiCutoutMaskUri = maskUri) }
                postBanner("Recorte Automático aplicado com sucesso!")
            }.onFailure { err ->
                postBanner(err.message ?: "Falha no Recorte IA.")
            }
        }
    }

    fun clearAutoCutout(layerId: String) {
        updateLayer(layerId, "Remover Recorte IA") { it.copy(aiCutoutMaskUri = null) }
    }

    fun runAutoCaptions(layerId: String) {
        val layer = _document.value.activeComposition.layers.firstOrNull { it.id == layerId } ?: return
        val asset = _document.value.assets.firstOrNull { it.id == layer.assetId }
        if (asset == null || (asset.type != AssetType.AUDIO && asset.type != AssetType.VIDEO)) {
            postBanner("Selecione uma camada de Áudio ou Vídeo para gerar legendas.")
            return
        }
        viewModelScope.launch {
            _isProcessingAi.value = true
            postBanner("Analisando voz offline para legendas...")
            val segments = OfflineCaptionGenerator.generateCaptions(getApplication(), asset)
            _isProcessingAi.value = false
            val baseZ = (_document.value.activeComposition.layers.maxOfOrNull { it.zIndex } ?: 0) + 1
            val captionLayers = segments.mapIndexed { idx, seg ->
                LayerNode(
                    name = "Legenda ${idx + 1}",
                    type = LayerType.TEXT,
                    inMs = layer.inMs + seg.startMs,
                    outMs = (layer.inMs + seg.endMs).coerceAtLeast(layer.inMs + seg.startMs + 400L),
                    zIndex = baseZ + idx,
                    transform = TransformSpec(
                        posY = AnimatableProperty(460f),
                        scaleX = AnimatableProperty(0.75f),
                        scaleY = AnimatableProperty(0.75f)
                    ),
                    textSpec = TextSpec(
                        content = seg.text,
                        fontSize = AnimatableProperty(64f),
                        colorHex = "#FFFFFF",
                        strokeColorHex = "#00E5FF",
                        strokeWidth = 4f
                    )
                )
            }
            updateActiveComposition("Gerar Legendas Automáticas") { c ->
                c.copy(layers = c.layers + captionLayers)
            }
            postBanner("${captionLayers.size} legendas sincronizadas criadas!")
        }
    }

    fun runMotionTrack2D(layerId: String, stabilize: Boolean) {
        val layer = _document.value.activeComposition.layers.firstOrNull { it.id == layerId } ?: return
        val asset = _document.value.assets.firstOrNull { it.id == layer.assetId && it.type == AssetType.VIDEO }
        if (asset == null) {
            postBanner("Selecione uma camada de Vídeo para rastrear movimento 2D.")
            return
        }
        val comp = _document.value.activeComposition
        val (nx, ny) = _trackerTargetNorm.value
        viewModelScope.launch {
            _isProcessingAi.value = true
            postBanner(if (stabilize) "Estabilizando vídeo quadro a quadro..." else "Rastreando ponto 2D...")
            val result = MotionTracker2D.trackPointInVideo(
                context = getApplication(),
                asset = asset,
                inMs = layer.inMs,
                outMs = layer.outMs,
                targetNormX = nx,
                targetNormY = ny,
                compWidth = comp.width,
                compHeight = comp.height,
                invertForStabilize = stabilize
            )
            _isProcessingAi.value = false
            result.onSuccess { track ->
                if (stabilize) {
                    updateLayer(layer.id, "Estabilizar Vídeo 2D") { l ->
                        l.copy(
                            transform = l.transform.copy(
                                posX = AnimatableProperty(0f, keyframes = track.posXKeyframes),
                                posY = AnimatableProperty(0f, keyframes = track.posYKeyframes)
                            )
                        )
                    }
                    postBanner("Estabilização 2D aplicada (${track.posXKeyframes.size} quadros)!")
                } else {
                    val nextZ = (comp.layers.maxOfOrNull { it.zIndex } ?: 0) + 1
                    val nullTracker = LayerNode(
                        name = "Tracker 2D (${layer.name.take(10)})",
                        type = LayerType.NULL,
                        inMs = layer.inMs,
                        outMs = layer.outMs,
                        zIndex = nextZ,
                        transform = TransformSpec(
                            posX = AnimatableProperty(0f, keyframes = track.posXKeyframes),
                            posY = AnimatableProperty(0f, keyframes = track.posYKeyframes)
                        )
                    )
                    updateActiveComposition("Criar Nulo Rastreador 2D") { c ->
                        c.copy(layers = c.layers + nullTracker)
                    }
                    _selectedLayerIds.value = setOf(nullTracker.id)
                    postBanner("Rastreamento concluído! Vincule qualquer camada ao 'Tracker 2D'.")
                }
            }.onFailure { err ->
                postBanner(err.message ?: "Falha no rastreamento 2D.")
            }
        }
    }

    // --- Phase 5: Multi-Keyframe Batch Actions ---

    fun shiftSelectedKeyframes(deltaMs: Long) {
        val sel = _selectedKeyframeIds.value
        if (sel.isEmpty()) return
        updateActiveComposition("Deslocar Keyframes") { comp ->
            comp.copy(
                layers = comp.layers.map { l ->
                    transformAllLayerProperties(l) { prop ->
                        if (prop.keyframes.none { it.id in sel }) prop
                        else prop.copy(
                            keyframes = prop.keyframes.map { k ->
                                if (k.id in sel) k.copy(timeMs = (k.timeMs + deltaMs).coerceAtLeast(0L)) else k
                            }.sortedBy { it.timeMs }
                        )
                    }
                }
            )
        }
    }

    fun deleteSelectedKeyframes() {
        val sel = _selectedKeyframeIds.value
        if (sel.isEmpty()) return
        updateActiveComposition("Excluir Keyframes") { comp ->
            comp.copy(
                layers = comp.layers.map { l ->
                    transformAllLayerProperties(l) { prop ->
                        if (prop.keyframes.none { it.id in sel }) prop
                        else prop.copy(keyframes = prop.keyframes.filterNot { it.id in sel })
                    }
                }
            )
        }
        _selectedKeyframeIds.value = emptySet()
    }

    fun applyCurveToSelectedKeyframes(bezier: BezierCurve) {
        val sel = _selectedKeyframeIds.value
        if (sel.isEmpty()) return
        updateActiveComposition("Curva em Lote") { comp ->
            comp.copy(
                layers = comp.layers.map { l ->
                    transformAllLayerProperties(l) { prop ->
                        if (prop.keyframes.none { it.id in sel }) prop
                        else prop.copy(
                            keyframes = prop.keyframes.map { k ->
                                if (k.id in sel) k.copy(easing = EasingType.BEZIER, bezier = bezier) else k
                            }
                        )
                    }
                }
            )
        }
        postBanner("Curva aplicada a ${sel.size} keyframes!")
    }

    fun duplicateSelectedKeyframes(offsetMs: Long = 120L) {
        val sel = _selectedKeyframeIds.value
        if (sel.isEmpty()) return
        val clonedIds = mutableSetOf<String>()
        updateActiveComposition("Duplicar Keyframes") { comp ->
            comp.copy(
                layers = comp.layers.map { l ->
                    transformAllLayerProperties(l) { prop ->
                        val clones = prop.keyframes
                            .filter { it.id in sel }
                            .map { k ->
                                val newId = UUID.randomUUID().toString()
                                clonedIds.add(newId)
                                k.copy(id = newId, timeMs = (k.timeMs + offsetMs).coerceAtLeast(0L))
                            }
                        if (clones.isEmpty()) prop
                        else prop.copy(keyframes = (prop.keyframes + clones).sortedBy { it.timeMs })
                    }
                }
            )
        }
        if (clonedIds.isNotEmpty()) {
            _selectedKeyframeIds.value = clonedIds
            postBanner("${clonedIds.size} keyframes duplicados!")
        }
    }

    private fun transformAllLayerProperties(layer: LayerNode, fn: (AnimatableProperty) -> AnimatableProperty): LayerNode {
        val t = layer.transform
        val nt = t.copy(
            anchorX = fn(t.anchorX), anchorY = fn(t.anchorY),
            posX = fn(t.posX), posY = fn(t.posY), posZ = fn(t.posZ),
            scaleX = fn(t.scaleX), scaleY = fn(t.scaleY),
            rotX = fn(t.rotX), rotY = fn(t.rotY), rotZ = fn(t.rotZ),
            opacity = fn(t.opacity), skewX = fn(t.skewX)
        )
        return layer.copy(
            transform = nt,
            vectorSpec = layer.vectorSpec?.let { it.copy(trimEnd = fn(it.trimEnd), trimStart = fn(it.trimStart), strokeWidth = fn(it.strokeWidth)) },
            textSpec = layer.textSpec?.let { it.copy(rangeStart = fn(it.rangeStart), rangeEnd = fn(it.rangeEnd), fontSize = fn(it.fontSize)) },
            speedRamp = layer.speedRamp?.let { fn(it) },
            effects = layer.effects.map { e -> e.copy(params = e.params.mapValues { (_, p) -> fn(p) }) }
        )
    }

    // --- Export MP4 ---

    fun startExport(settings: ExportSettings) {
        if (_exportProgress.value != null) return
        viewModelScope.launch {
            _exportResult.value = null
            _exportProgress.value = 0.01f
            _exportMessage.value = "Preparando exportação..."
            val result = VideoExporter.exportMp4(
                context = getApplication(),
                document = _document.value,
                settings = settings
            ) { prog, msg ->
                _exportProgress.value = prog
                _exportMessage.value = msg
            }
            _exportResult.value = result
            _exportProgress.value = null
            _exportMessage.value = null
        }
    }

    fun dismissExportResult() {
        _exportResult.value = null
    }

    // --- Helpers ---

    private fun updatePropValueAtTime(prop: AnimatableProperty, localTimeMs: Long, newValue: Float): AnimatableProperty {
        if (prop.keyframes.isEmpty()) {
            return prop.copy(baseValue = newValue)
        }
        val near = prop.keyframes.firstOrNull { abs(it.timeMs - localTimeMs) <= 25L }
        return if (near != null) {
            prop.copy(keyframes = prop.keyframes.map { if (it.id == near.id) it.copy(value = newValue) else it })
        } else {
            CurveInterpolator.sortAndUpsertKeyframe(prop, localTimeMs, newValue)
        }
    }

    fun readLayerProperty(layer: LayerNode, key: PropertyKey): AnimatableProperty = when (key) {
        PropertyKey.POS_X -> layer.transform.posX
        PropertyKey.POS_Y -> layer.transform.posY
        PropertyKey.POS_Z -> layer.transform.posZ
        PropertyKey.ANCHOR_X -> layer.transform.anchorX
        PropertyKey.ANCHOR_Y -> layer.transform.anchorY
        PropertyKey.SCALE_X -> layer.transform.scaleX
        PropertyKey.SCALE_Y -> layer.transform.scaleY
        PropertyKey.ROT_X -> layer.transform.rotX
        PropertyKey.ROT_Y -> layer.transform.rotY
        PropertyKey.ROT_Z -> layer.transform.rotZ
        PropertyKey.OPACITY -> layer.transform.opacity
        PropertyKey.SKEW_X -> layer.transform.skewX
        PropertyKey.TRIM_END -> layer.vectorSpec?.trimEnd ?: AnimatableProperty(1f)
        PropertyKey.TEXT_RANGE_START -> layer.textSpec?.rangeStart ?: AnimatableProperty(0f)
    }

    private fun mutateLayerProperty(
        layer: LayerNode,
        key: PropertyKey,
        fn: (AnimatableProperty) -> AnimatableProperty
    ): LayerNode = when (key) {
        PropertyKey.POS_X -> layer.copy(transform = layer.transform.copy(posX = fn(layer.transform.posX)))
        PropertyKey.POS_Y -> layer.copy(transform = layer.transform.copy(posY = fn(layer.transform.posY)))
        PropertyKey.POS_Z -> layer.copy(transform = layer.transform.copy(posZ = fn(layer.transform.posZ)))
        PropertyKey.ANCHOR_X -> layer.copy(transform = layer.transform.copy(anchorX = fn(layer.transform.anchorX)))
        PropertyKey.ANCHOR_Y -> layer.copy(transform = layer.transform.copy(anchorY = fn(layer.transform.anchorY)))
        PropertyKey.SCALE_X -> layer.copy(transform = layer.transform.copy(scaleX = fn(layer.transform.scaleX)))
        PropertyKey.SCALE_Y -> layer.copy(transform = layer.transform.copy(scaleY = fn(layer.transform.scaleY)))
        PropertyKey.ROT_X -> layer.copy(transform = layer.transform.copy(rotX = fn(layer.transform.rotX)))
        PropertyKey.ROT_Y -> layer.copy(transform = layer.transform.copy(rotY = fn(layer.transform.rotY)))
        PropertyKey.ROT_Z -> layer.copy(transform = layer.transform.copy(rotZ = fn(layer.transform.rotZ)))
        PropertyKey.OPACITY -> layer.copy(transform = layer.transform.copy(opacity = fn(layer.transform.opacity)))
        PropertyKey.SKEW_X -> layer.copy(transform = layer.transform.copy(skewX = fn(layer.transform.skewX)))
        PropertyKey.TRIM_END -> layer.copy(vectorSpec = (layer.vectorSpec ?: VectorSpec()).let { it.copy(trimEnd = fn(it.trimEnd)) })
        PropertyKey.TEXT_RANGE_START -> layer.copy(textSpec = (layer.textSpec ?: TextSpec()).let { it.copy(rangeStart = fn(it.rangeStart)) })
    }
}
