package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.engine.EvaluatedLayer
import com.example.engine.SceneEvaluator
import com.example.model.AspectRatioPreset
import com.example.model.BlendMode
import com.example.model.CompositionNode
import com.example.model.EffectType
import com.example.model.LayerNode
import com.example.model.LayerType
import com.example.model.MaskMode
import com.example.model.MaskShape
import com.example.model.TextRangeMode
import com.example.model.VectorFillType
import com.example.model.VectorShapePreset
import com.example.ui.InspectorTab
import com.example.ui.MaxMotionViewModel
import com.example.ui.PropertyKey
import com.example.ui.theme.AlertRed
import com.example.ui.theme.CacheEmerald
import com.example.ui.theme.ElectricPurple
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.OledBlack
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioElevated
import com.example.ui.theme.StudioHairline
import com.example.ui.theme.StudioPanel
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun ContextualInspectorSheet(
    tab: InspectorTab,
    layer: LayerNode?,
    evaluatedLayer: EvaluatedLayer?,
    composition: CompositionNode,
    playheadMs: Long,
    editingProperty: PropertyKey,
    orbitMode: Boolean,
    trackerTargetMode: Boolean,
    trackerTargetNorm: Pair<Float, Float>,
    isProcessingAi: Boolean,
    onToggleOrbit: () -> Unit,
    onToggleTrackerTarget: () -> Unit,
    onPickCubeFile: () -> Unit,
    viewModel: MaxMotionViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 320.dp)
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(StudioPanel)
            .border(0.5.dp, StudioHairline, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("inspector_sheet_${tab.name.lowercase()}")
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 6.dp)
                .width(36.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(TextMuted.copy(alpha = 0.45f))
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (layer != null) "${tab.labelPt} • ${layer.name}" else tab.labelPt,
                style = MaterialTheme.typography.titleSmall,
                color = NeonCyan
            )
            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.Close, "Fechar painel", tint = TextPrimary, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            when (tab) {
                InspectorTab.TRANSFORM -> {
                    if (layer != null) {
                        TransformInspectorContent(layer, evaluatedLayer, composition, playheadMs, viewModel)
                    }
                }
                InspectorTab.CAMERA_LIGHT -> {
                    CameraLightInspectorContent(layer, composition, orbitMode, onToggleOrbit, viewModel)
                }
                InspectorTab.EFFECTS -> {
                    if (layer != null) {
                        EffectsInspectorContent(layer, composition, playheadMs, viewModel)
                    }
                }
                InspectorTab.COLOR_CURVES -> {
                    if (layer != null) {
                        ColorCurvesLutInspectorContent(layer, onPickCubeFile, viewModel)
                    }
                }
                InspectorTab.MASKS -> {
                    if (layer != null) {
                        MasksInspectorContent(layer, viewModel)
                    }
                }
                InspectorTab.BLENDING -> {
                    if (layer != null) {
                        BlendingInspectorContent(layer, viewModel)
                    }
                }
                InspectorTab.VECTOR_TEXT -> {
                    if (layer != null) {
                        VectorTextInspectorContent(layer, evaluatedLayer, playheadMs, viewModel)
                    }
                }
                InspectorTab.ANIMATE -> {
                    if (layer != null) {
                        AnimatePresetsContent(layer, viewModel)
                    }
                }
                InspectorTab.SPEED_RAMP -> {
                    if (layer != null) {
                        SpeedRampInspectorContent(layer, viewModel)
                    }
                }
                InspectorTab.TRACKER_AI -> {
                    if (layer != null) {
                        TrackerAiInspectorContent(
                            layer = layer,
                            isProcessingAi = isProcessingAi,
                            trackerTargetMode = trackerTargetMode,
                            trackerTargetNorm = trackerTargetNorm,
                            onToggleTrackerTarget = onToggleTrackerTarget,
                            vm = viewModel
                        )
                    }
                }
                InspectorTab.EXPRESSION -> {
                    if (layer != null) {
                        ExpressionInspectorContent(layer, editingProperty, composition, viewModel)
                    }
                }
                InspectorTab.AUDIO -> {
                    if (layer != null) {
                        AudioInspectorContent(layer, viewModel)
                    }
                }
                InspectorTab.ASPECT_BG -> {
                    AspectBgInspectorContent(composition, viewModel)
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun TransformInspectorContent(
    layer: LayerNode,
    ev: EvaluatedLayer?,
    comp: CompositionNode,
    playheadMs: Long,
    vm: MaxMotionViewModel
) {
    val localMs = ((playheadMs - layer.inMs) * layer.speedMultiplier).toLong()
    val tr = ev?.transform

    @Composable
    fun row(label: String, key: PropertyKey, value: Float, step: Float, minV: Float, maxV: Float, suffix: String = "", dec: Int = 1) {
        ScrubberRow(
            label = label,
            value = value,
            stepSensitivity = step,
            minValue = minV,
            maxValue = maxV,
            suffix = suffix,
            decimals = dec,
            property = vm.readLayerProperty(layer, key),
            playheadLocalMs = localMs,
            testTagPrefix = key.name.lowercase(),
            onDragStart = { vm.beginContinuousGesture() },
            onValueChange = { vm.setPropertyScalar(layer.id, key, it, recordUndo = false) },
            onDragEnd = { vm.endContinuousGesture("Ajustar $label") },
            onToggleKeyframe = { vm.toggleKeyframeOnProperty(layer.id, key) },
            onOpenGraph = { vm.openGraphEditorFor(key) },
            onOpenExpression = { vm.openExpressionEditorFor(key) }
        )
    }

    row("Posição X", PropertyKey.POS_X, tr?.posX ?: layer.transform.posX.baseValue, 1.5f, -3000f, 3000f, "px", 0)
    row("Posição Y", PropertyKey.POS_Y, tr?.posY ?: layer.transform.posY.baseValue, 1.5f, -3000f, 3000f, "px", 0)
    if (layer.is3D) {
        row("Posição Z (3D)", PropertyKey.POS_Z, tr?.posZ ?: layer.transform.posZ.baseValue, 2f, -3000f, 3000f, "px", 0)
        row("Rotação X (3D)", PropertyKey.ROT_X, tr?.rotX ?: layer.transform.rotX.baseValue, 0.6f, -360f, 360f, "°", 1)
        row("Rotação Y (3D)", PropertyKey.ROT_Y, tr?.rotY ?: layer.transform.rotY.baseValue, 0.6f, -360f, 360f, "°", 1)
    }
    row("Escala X", PropertyKey.SCALE_X, tr?.scaleX ?: layer.transform.scaleX.baseValue, 0.01f, 0.05f, 8f, "x", 2)
    row("Escala Y", PropertyKey.SCALE_Y, tr?.scaleY ?: layer.transform.scaleY.baseValue, 0.01f, 0.05f, 8f, "x", 2)
    row("Rotação Z", PropertyKey.ROT_Z, tr?.rotZ ?: layer.transform.rotZ.baseValue, 0.6f, -1440f, 1440f, "°", 1)
    row("Âncora X", PropertyKey.ANCHOR_X, tr?.anchorX ?: layer.transform.anchorX.baseValue, 1f, -1000f, 1000f, "px", 0)
    row("Âncora Y", PropertyKey.ANCHOR_Y, tr?.anchorY ?: layer.transform.anchorY.baseValue, 1f, -1000f, 1000f, "px", 0)
    row("Opacidade", PropertyKey.OPACITY, (tr?.opacity ?: layer.transform.opacity.baseValue) * 100f, 0.5f, 0f, 100f, "%", 0)
    row("Inclinação (Skew)", PropertyKey.SKEW_X, tr?.skewX ?: layer.transform.skewX.baseValue, 0.4f, -65f, 65f, "°", 1)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EffectsInspectorContent(
    layer: LayerNode,
    comp: CompositionNode,
    playheadMs: Long,
    vm: MaxMotionViewModel
) {
    var showAddCatalog by remember { mutableStateOf(false) }
    val localMs = ((playheadMs - layer.inMs) * layer.speedMultiplier).toLong()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Pilha de Shaders (${layer.effects.size})", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Button(
            onClick = { showAddCatalog = !showAddCatalog },
            modifier = Modifier.height(34.dp).testTag("add_effect_toggle")
        ) {
            Icon(Icons.Rounded.Add, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(if (showAddCatalog) "Ver Pilha" else "Adicionar Efeito", style = MaterialTheme.typography.labelSmall)
        }
    }

    Spacer(Modifier.height(8.dp))

    if (showAddCatalog) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            EffectType.entries.forEach { effType ->
                FilterChip(
                    selected = false,
                    onClick = {
                        vm.addEffect(layer.id, effType)
                        showAddCatalog = false
                    },
                    label = { Text("${effType.labelPt} (${effType.categoryPt})", style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
    } else if (layer.effects.isEmpty()) {
        Text("Nenhum efeito na pilha. Toque em Adicionar Efeito para aplicar shaders GLSL.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    } else {
        layer.effects.forEach { eff ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(StudioElevated)
                    .border(1.dp, StudioBorder, RoundedCornerShape(10.dp))
                    .padding(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = eff.enabled,
                            onCheckedChange = { vm.toggleEffectEnabled(layer.id, eff.id) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(eff.type.labelPt, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    }
                    Row {
                        IconButton(onClick = { vm.moveEffectOrder(layer.id, eff.id, -1) }, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Rounded.ArrowUpward, "Subir", tint = TextSecondary, modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = { vm.moveEffectOrder(layer.id, eff.id, 1) }, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Rounded.ArrowDownward, "Descer", tint = TextSecondary, modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = { vm.removeEffect(layer.id, eff.id) }, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Rounded.Delete, "Remover", tint = AlertRed, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                eff.params.forEach { (pKey, prop) ->
                    val currentVal = SceneEvaluator.evaluateProperty(prop, localMs, comp, 0f)
                    val (minR, maxR) = effectParamRange(pKey)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(pKey, style = MaterialTheme.typography.labelSmall, color = TextSecondary, modifier = Modifier.width(86.dp))
                        Slider(
                            value = currentVal.coerceIn(minR, maxR),
                            onValueChange = { vm.updateEffectParam(layer.id, eff.id, pKey, it) },
                            valueRange = minR..maxR,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = String.format(Locale.US, "%.2f", currentVal),
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonCyan,
                            modifier = Modifier.width(42.dp)
                        )
                        IconButton(
                            onClick = { vm.toggleEffectParamKeyframe(layer.id, eff.id, pKey) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Diamond,
                                "Keyframe parâmetro",
                                tint = if (prop.keyframes.isNotEmpty()) NeonCyan else TextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun effectParamRange(key: String): Pair<Float, Float> = when (key) {
    "tileWidth", "tileHeight" -> 0.5f to 4.0f
    "mirrorEdges" -> 0f to 1f
    "frequency", "waveWidth", "size" -> 1f to 30f
    "rotationAmp", "angle", "angleDeg" -> -180f to 180f
    "shutterAngle" -> 0f to 360f
    "targetFps" -> 2f to 60f
    "scanDensity", "dotScale", "pixelSize" -> 8f to 240f
    "contrast", "gamma", "gain" -> 0.3f to 2.5f
    "exposure", "temperature", "lift" -> -1.5f to 1.5f
    "saturation" -> 0f to 2.5f
    "intensity", "strength", "radius", "amount", "tolerance", "spillSuppression", "threshold", "glowMix", "roundness", "chromaShift", "jitter" -> 0f to 2f
    else -> 0f to 10f
}

@Composable
private fun MasksInspectorContent(layer: LayerNode, vm: MaxMotionViewModel) {
    val existing = layer.masks.firstOrNull()
    var shape by remember(existing?.id) { mutableStateOf(existing?.shape ?: MaskShape.RECTANGLE) }
    var mode by remember(existing?.id) { mutableStateOf(existing?.mode ?: MaskMode.ADD) }
    var feather by remember(existing?.id) { mutableFloatStateOf(existing?.feather?.baseValue ?: 0.04f) }
    var width by remember(existing?.id) { mutableFloatStateOf(existing?.width?.baseValue ?: 0.72f) }
    var height by remember(existing?.id) { mutableFloatStateOf(existing?.height?.baseValue ?: 0.72f) }
    var inverted by remember(existing?.id) { mutableStateOf(existing?.inverted ?: false) }

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        MaskShape.entries.forEach { s ->
            FilterChip(
                selected = shape == s,
                onClick = {
                    shape = s
                    vm.addOrUpdateMask(layer.id, shape, mode, feather, width, height, inverted)
                },
                label = { Text(s.labelPt, style = MaterialTheme.typography.labelSmall) }
            )
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        MaskMode.entries.forEach { m ->
            FilterChip(
                selected = mode == m,
                onClick = {
                    mode = m
                    vm.addOrUpdateMask(layer.id, shape, mode, feather, width, height, inverted)
                },
                label = { Text(m.labelPt, style = MaterialTheme.typography.labelSmall) }
            )
        }
        FilterChip(
            selected = inverted,
            onClick = {
                inverted = !inverted
                vm.addOrUpdateMask(layer.id, shape, mode, feather, width, height, inverted)
            },
            label = { Text("Inverter", style = MaterialTheme.typography.labelSmall) }
        )
    }

    Text("Suavização (Feather): ${String.format(Locale.US, "%.2f", feather)}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    Slider(value = feather, onValueChange = { feather = it; vm.addOrUpdateMask(layer.id, shape, mode, feather, width, height, inverted) }, valueRange = 0f..0.4f)

    Text("Largura / Altura", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    Slider(value = width, onValueChange = { width = it; vm.addOrUpdateMask(layer.id, shape, mode, feather, width, height, inverted) }, valueRange = 0.1f..1.2f)
    Slider(value = height, onValueChange = { height = it; vm.addOrUpdateMask(layer.id, shape, mode, feather, width, height, inverted) }, valueRange = 0.1f..1.2f)

    if (existing != null) {
        OutlinedButton(onClick = { vm.clearMask(layer.id) }, colors = ButtonDefaults.outlinedButtonColors(contentColor = AlertRed)) {
            Text("Remover Máscara")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BlendingInspectorContent(layer: LayerNode, vm: MaxMotionViewModel) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BlendMode.entries.forEach { mode ->
            FilterChip(
                selected = layer.blendMode == mode,
                onClick = { vm.setBlendMode(layer.id, mode) },
                label = { Text(mode.labelPt, style = MaterialTheme.typography.labelSmall) }
            )
        }
    }
}

@Composable
private fun VectorTextInspectorContent(
    layer: LayerNode,
    ev: EvaluatedLayer?,
    playheadMs: Long,
    vm: MaxMotionViewModel
) {
    val localMs = ((playheadMs - layer.inMs) * layer.speedMultiplier).toLong()
    val ts = layer.textSpec
    val vs = layer.vectorSpec

    if (layer.type == LayerType.TEXT && ts != null) {
        var textInput by remember(layer.id, ts.content) { mutableStateOf(ts.content) }
        OutlinedTextField(
            value = textInput,
            onValueChange = {
                textInput = it
                vm.updateTextSpec(layer.id) { cur -> cur.copy(content = it) }
            },
            label = { Text("Conteúdo do Texto") },
            modifier = Modifier.fillMaxWidth().testTag("text_content_input")
        )
        Spacer(Modifier.height(6.dp))
        Text("Fonte & Animador por Faixa", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            listOf("space_grotesk" to "Space Grotesk", "plus_jakarta_sans" to "Jakarta Sans", "jetbrains_mono" to "JetBrains Mono").forEach { (key, label) ->
                FilterChip(
                    selected = ts.fontFamilyKey == key,
                    onClick = { vm.updateTextSpec(layer.id) { it.copy(fontFamilyKey = key) } },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            TextRangeMode.entries.forEach { mode ->
                FilterChip(
                    selected = ts.rangeMode == mode,
                    onClick = { vm.updateTextSpec(layer.id) { it.copy(rangeMode = mode) } },
                    label = { Text("Por ${mode.labelPt}", style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
        ScrubberRow(
            label = "Seletor de Faixa (Início)",
            value = ev?.textRangeStart ?: ts.rangeStart.baseValue,
            stepSensitivity = 0.01f,
            minValue = 0f,
            maxValue = 1f,
            decimals = 2,
            property = ts.rangeStart,
            playheadLocalMs = localMs,
            testTagPrefix = "text_range",
            onDragStart = { vm.beginContinuousGesture() },
            onValueChange = { vm.setPropertyScalar(layer.id, PropertyKey.TEXT_RANGE_START, it) },
            onDragEnd = { vm.endContinuousGesture("Animador de Texto") },
            onToggleKeyframe = { vm.toggleKeyframeOnProperty(layer.id, PropertyKey.TEXT_RANGE_START) },
            onOpenGraph = { vm.openGraphEditorFor(PropertyKey.TEXT_RANGE_START) },
            onOpenExpression = { vm.openExpressionEditorFor(PropertyKey.TEXT_RANGE_START) }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("#F4F4F7", "#00E5FF", "#7C3AED", "#10B981", "#F59E0B", "#EC4899").forEach { hex ->
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(android.graphics.Color.parseColor(hex)))
                        .border(if (ts.colorHex == hex) 2.dp else 1.dp, NeonCyan, CircleShape)
                        .clickable { vm.updateTextSpec(layer.id) { it.copy(colorHex = hex) } }
                )
            }
        }
    } else if (layer.type == LayerType.VECTOR && vs != null) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            VectorShapePreset.entries.forEach { p ->
                FilterChip(
                    selected = vs.preset == p,
                    onClick = { vm.updateVectorSpec(layer.id) { it.copy(preset = p) } },
                    label = { Text(p.labelPt, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            VectorFillType.entries.forEach { f ->
                FilterChip(
                    selected = vs.fillType == f,
                    onClick = { vm.updateVectorSpec(layer.id) { it.copy(fillType = f) } },
                    label = { Text(f.labelPt, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
        ScrubberRow(
            label = "Trim Path (Aparar Fim)",
            value = ev?.trimEnd ?: vs.trimEnd.baseValue,
            stepSensitivity = 0.01f,
            minValue = 0f,
            maxValue = 1f,
            decimals = 2,
            property = vs.trimEnd,
            playheadLocalMs = localMs,
            testTagPrefix = "trim_end",
            onDragStart = { vm.beginContinuousGesture() },
            onValueChange = { vm.setPropertyScalar(layer.id, PropertyKey.TRIM_END, it) },
            onDragEnd = { vm.endContinuousGesture("Trim Path") },
            onToggleKeyframe = { vm.toggleKeyframeOnProperty(layer.id, PropertyKey.TRIM_END) },
            onOpenGraph = { vm.openGraphEditorFor(PropertyKey.TRIM_END) },
            onOpenExpression = { vm.openExpressionEditorFor(PropertyKey.TRIM_END) }
        )
        Text("Espessura do Contorno: ${vs.strokeWidth.baseValue.toInt()}px", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Slider(
            value = vs.strokeWidth.baseValue.coerceIn(0f, 40f),
            onValueChange = { w -> vm.updateVectorSpec(layer.id) { it.copy(strokeWidth = it.strokeWidth.copy(baseValue = w)) } },
            valueRange = 0f..40f
        )
    } else {
        Text("Selecione uma camada de Vetor ou Texto para editar estilo e Trim Paths.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnimatePresetsContent(layer: LayerNode, vm: MaxMotionViewModel) {
    Text("Presets Prontos com Curvas Bézier", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    Spacer(Modifier.height(6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            "pop_spring" to "Pop Elástico",
            "slide_up" to "Subir Suave (Flow)",
            "spin_360" to "Giro Dinâmico",
            "beat_pulse" to "Pulsar na Batida (Expressão)"
        ).forEach { (id, label) ->
            Button(
                onClick = { vm.applyAnimationPreset(layer.id, id) },
                modifier = Modifier.testTag("anim_preset_$id")
            ) {
                Text(label, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ExpressionInspectorContent(
    layer: LayerNode,
    propKey: PropertyKey,
    comp: CompositionNode,
    vm: MaxMotionViewModel
) {
    val prop = vm.readLayerProperty(layer, propKey)
    var exprText by remember(layer.id, propKey, prop.expression) { mutableStateOf(prop.expression ?: "") }
    val errorMsg = vm.validateExpression(exprText, propKey)

    Text("Expressão em ${propKey.labelPt} (JS simplificado)", style = MaterialTheme.typography.labelSmall, color = ElectricPurple)
    Spacer(Modifier.height(4.dp))
    OutlinedTextField(
        value = exprText,
        onValueChange = { exprText = it },
        placeholder = { Text("Ex: wiggle(3, 25) ou time * 45") },
        modifier = Modifier.fillMaxWidth().testTag("expression_input"),
        textStyle = MaterialTheme.typography.labelMedium
    )

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        if (errorMsg == null) {
            Icon(Icons.Rounded.CheckCircle, null, tint = CacheEmerald, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(if (exprText.isBlank()) "Sem expressão ativa" else "Expressão válida", style = MaterialTheme.typography.labelSmall, color = CacheEmerald)
        } else {
            Icon(Icons.Rounded.ErrorOutline, null, tint = AlertRed, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(errorMsg, style = MaterialTheme.typography.labelSmall, color = AlertRed)
        }
    }

    // Quick mobile expression chips
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        listOf(
            "wiggle(4, 25)",
            "time * 45",
            "loopOut(\"pingpong\")",
            "loopOut(\"cycle\")",
            "1 + audioBass * 0.35",
            "linear(time, 0, 2, 0, 100)"
        ).forEach { chip ->
            FilterChip(
                selected = exprText == chip,
                onClick = { exprText = chip },
                label = { Text(chip, style = MaterialTheme.typography.labelSmall) }
            )
        }
    }

    // Pick-Whip to another layer's scale/rotation
    val otherLayers = comp.layers.filter { it.id != layer.id }
    if (otherLayers.isNotEmpty()) {
        Text("Pick-Whip Rápido (Vincular à escala de):", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            otherLayers.forEach { other ->
                FilterChip(
                    selected = false,
                    onClick = { exprText = "layer(\"${other.name}\").scaleX" },
                    label = { Text(other.name, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
    }

    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = { vm.updateExpression(layer.id, propKey, exprText) },
            modifier = Modifier.testTag("apply_expression_btn")
        ) {
            Text("Aplicar Expressão")
        }
        OutlinedButton(onClick = {
            exprText = ""
            vm.updateExpression(layer.id, propKey, null)
        }) {
            Text("Limpar")
        }
    }
}

@Composable
private fun AudioInspectorContent(layer: LayerNode, vm: MaxMotionViewModel) {
    val spec = layer.audioSpec ?: return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Text("Espectro Visual (Audio Visualizer)", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        Switch(
            checked = spec.visualizerEnabled,
            onCheckedChange = { en -> vm.updateAudioSpec(layer.id) { it.copy(visualizerEnabled = en) } }
        )
    }
    Text("Volume: ${(spec.volume.baseValue * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    Slider(
        value = spec.volume.baseValue.coerceIn(0f, 1.5f),
        onValueChange = { v -> vm.updateAudioSpec(layer.id) { it.copy(volume = it.volume.copy(baseValue = v)) } },
        valueRange = 0f..1.5f
    )
}

@Composable
private fun AspectBgInspectorContent(comp: CompositionNode, vm: MaxMotionViewModel) {
    Text("Proporção da Composição", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        AspectRatioPreset.entries.forEach { preset ->
            FilterChip(
                selected = comp.width == preset.width && comp.height == preset.height,
                onClick = { vm.setCompositionAspect(preset) },
                label = { Text(preset.label, style = MaterialTheme.typography.labelSmall) }
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    Text("Fundo da Composição", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    Spacer(Modifier.height(4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("#0B0B0E", "#16161C", "#0F172A", "#1E1B4B", "#052E16", "#FFFFFF").forEach { hex ->
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(android.graphics.Color.parseColor(hex)))
                    .border(if (comp.backgroundColorHex == hex) 2.dp else 1.dp, NeonCyan, CircleShape)
                    .clickable { vm.setCompositionBackground(hex) }
            )
        }
    }
}
