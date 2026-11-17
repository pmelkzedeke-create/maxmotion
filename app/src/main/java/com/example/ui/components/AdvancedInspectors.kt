package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.engine.CubeLutParser
import com.example.engine.SpeedRampEvaluator
import com.example.model.ColorGradingSpec
import com.example.model.CompositionNode
import com.example.model.CurvePoint
import com.example.model.LayerNode
import com.example.model.LayerType
import com.example.model.LightType
import com.example.ui.MaxMotionViewModel
import com.example.ui.theme.AlertRed
import com.example.ui.theme.BeatAmber
import com.example.ui.theme.CacheEmerald
import com.example.ui.theme.ElectricPurple
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.OledBlack
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale
import kotlin.math.abs

@Composable
fun CameraLightInspectorContent(
    layer: LayerNode?,
    comp: CompositionNode,
    orbitMode: Boolean,
    onToggleOrbit: () -> Unit,
    vm: MaxMotionViewModel
) {
    if (layer?.type == LayerType.CAMERA_3D && layer.cameraSpec != null) {
        val cs = layer.cameraSpec
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Perspectiva 3D Real", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            Switch(
                checked = cs.enablePerspective,
                onCheckedChange = { en -> vm.updateCameraSpec(layer.id) { it.copy(enablePerspective = en) } }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = onToggleOrbit,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (orbitMode) NeonCyan else StudioElevated,
                    contentColor = if (orbitMode) OledBlack else TextPrimary
                ),
                modifier = Modifier.testTag("toggle_orbit_btn")
            ) {
                Icon(Icons.Rounded.ViewInAr, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (orbitMode) "Órbita Ativa" else "Girar Câmera no Canvas", style = MaterialTheme.typography.labelSmall)
            }
            OutlinedButton(onClick = {
                vm.updateCameraSpec(layer.id) { it.copy(orbitAzimuth = 0f, orbitElevation = 0f) }
            }) {
                Text("Resetar Órbita", style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("Campo de Visão (FOV): ${cs.fov.baseValue.toInt()}°", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Slider(
            value = cs.fov.baseValue.coerceIn(20f, 110f),
            onValueChange = { f -> vm.updateCameraSpec(layer.id) { it.copy(fov = it.fov.copy(baseValue = f)) } },
            valueRange = 20f..110f
        )
        Text("Profundidade de Campo (DOF Bokeh): ${String.format(Locale.US, "%.2f", cs.dofBlur.baseValue)}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Slider(
            value = cs.dofBlur.baseValue.coerceIn(0f, 2f),
            onValueChange = { b -> vm.updateCameraSpec(layer.id) { it.copy(dofBlur = it.dofBlur.copy(baseValue = b)) } },
            valueRange = 0f..2f
        )
        Text("Distância do Foco (Z): ${cs.focusDistance.baseValue.toInt()}px", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Slider(
            value = cs.focusDistance.baseValue.coerceIn(-800f, 800f),
            onValueChange = { z -> vm.updateCameraSpec(layer.id) { it.copy(focusDistance = it.focusDistance.copy(baseValue = z)) } },
            valueRange = -800f..800f
        )
    } else if (layer?.type == LayerType.LIGHT_3D && layer.lightSpec != null) {
        val ls = layer.lightSpec
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            LightType.entries.forEach { lt ->
                FilterChip(
                    selected = ls.lightType == lt,
                    onClick = { vm.updateLightSpec(layer.id) { it.copy(lightType = lt) } },
                    label = { Text(lt.labelPt, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Cor:", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            listOf("#00E5FF", "#7C3AED", "#FFFFFF", "#F59E0B", "#EC4899", "#10B981").forEach { hex ->
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color(android.graphics.Color.parseColor(hex)))
                        .border(if (ls.colorHex == hex) 2.dp else 1.dp, TextPrimary, CircleShape)
                        .clickable { vm.updateLightSpec(layer.id) { it.copy(colorHex = hex) } }
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("Intensidade: ${String.format(Locale.US, "%.2f", ls.intensity.baseValue)}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Slider(
            value = ls.intensity.baseValue.coerceIn(0f, 3f),
            onValueChange = { v -> vm.updateLightSpec(layer.id) { it.copy(intensity = it.intensity.copy(baseValue = v)) } },
            valueRange = 0f..3f
        )
        Text("Raio de Alcance: ${ls.radius.baseValue.toInt()}px", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Slider(
            value = ls.radius.baseValue.coerceIn(200f, 2200f),
            onValueChange = { r -> vm.updateLightSpec(layer.id) { it.copy(radius = it.radius.copy(baseValue = r)) } },
            valueRange = 200f..2200f
        )
    } else {
        Text("Adicione uma Câmera 3D ou Luz 3D à composição:", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.addUtilityLayer(LayerType.CAMERA_3D) }) {
                Icon(Icons.Rounded.Videocam, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Câmera 3D", style = MaterialTheme.typography.labelSmall)
            }
            Button(onClick = { vm.addUtilityLayer(LayerType.LIGHT_3D) }) {
                Icon(Icons.Rounded.WbSunny, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Luz 3D", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun ColorCurvesLutInspectorContent(
    layer: LayerNode,
    onPickCubeFile: () -> Unit,
    vm: MaxMotionViewModel
) {
    val cg = layer.colorGrading ?: ColorGradingSpec()
    var activeChannel by remember { mutableIntStateOf(0) } // 0=Master, 1=R, 2=G, 3=B

    Text("LUTs 3D (.cube) & Presets Cinematográficos", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        FilterChip(
            selected = cg.lutPresetId == null && cg.lutCubeUri == null,
            onClick = { vm.updateColorGrading(layer.id) { it.copy(lutPresetId = null, lutCubeUri = null, lutName = null) } },
            label = { Text("Original", style = MaterialTheme.typography.labelSmall) }
        )
        CubeLutParser.builtInLuts.forEach { preset ->
            FilterChip(
                selected = cg.lutPresetId == preset.id,
                onClick = { vm.updateColorGrading(layer.id) { it.copy(lutPresetId = preset.id, lutCubeUri = null, lutName = preset.namePt) } },
                label = { Text(preset.namePt, style = MaterialTheme.typography.labelSmall) }
            )
        }
        OutlinedButton(onClick = onPickCubeFile, modifier = Modifier.height(32.dp)) {
            Icon(Icons.Rounded.FileUpload, null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(cg.lutName?.take(14) ?: "Importar .cube", style = MaterialTheme.typography.labelSmall)
        }
    }

    if (cg.lutPresetId != null || cg.lutCubeUri != null) {
        Text("Intensidade da LUT: ${(cg.lutIntensity * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Slider(
            value = cg.lutIntensity.coerceIn(0f, 1f),
            onValueChange = { v -> vm.updateColorGrading(layer.id) { it.copy(lutIntensity = v) } },
            valueRange = 0f..1f
        )
    }

    Spacer(Modifier.height(6.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Master" to TextPrimary, "R" to AlertRed, "G" to CacheEmerald, "B" to NeonCyan).forEachIndexed { idx, (lbl, col) ->
                FilterChip(
                    selected = activeChannel == idx,
                    onClick = { activeChannel = idx },
                    label = { Text(lbl, style = MaterialTheme.typography.labelSmall, color = if (activeChannel == idx) col else TextSecondary) }
                )
            }
        }
        OutlinedButton(
            onClick = {
                val def = listOf(CurvePoint(0f, 0f), CurvePoint(0.5f, 0.5f), CurvePoint(1f, 1f))
                vm.updateColorGrading(layer.id) {
                    it.copy(curveMaster = def, curveRed = def, curveGreen = def, curveBlue = def)
                }
            },
            modifier = Modifier.height(30.dp)
        ) {
            Text("Resetar", style = MaterialTheme.typography.labelSmall)
        }
    }

    val curPoints = when (activeChannel) {
        1 -> cg.curveRed
        2 -> cg.curveGreen
        3 -> cg.curveBlue
        else -> cg.curveMaster
    }
    val curveColor = when (activeChannel) {
        1 -> AlertRed
        2 -> CacheEmerald
        3 -> NeonCyan
        else -> TextPrimary
    }

    // Interactive RGB Curve Graph
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(OledBlack)
            .border(1.dp, StudioBorder, RoundedCornerShape(10.dp))
            .testTag("rgb_curve_canvas")
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(layer.id, activeChannel, curPoints) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val nx = (change.position.x / size.width).coerceIn(0f, 1f)
                        val ny = (1f - change.position.y / size.height).coerceIn(0f, 1f)
                        val nearestIdx = curPoints.indices.minByOrNull { abs(curPoints[it].x - nx) } ?: 1
                        val updated = curPoints.mapIndexed { idx, pt ->
                            if (idx == nearestIdx) pt.copy(y = ny) else pt
                        }
                        vm.updateColorGrading(layer.id) { spec ->
                            when (activeChannel) {
                                1 -> spec.copy(curveRed = updated)
                                2 -> spec.copy(curveGreen = updated)
                                3 -> spec.copy(curveBlue = updated)
                                else -> spec.copy(curveMaster = updated)
                            }
                        }
                    }
                }
        ) {
            val w = size.width
            val h = size.height
            drawLine(StudioBorder, Offset(0f, h), Offset(w, 0f), strokeWidth = 1f)
            drawLine(StudioBorder, Offset(w * 0.5f, 0f), Offset(w * 0.5f, h), strokeWidth = 1f)
            drawLine(StudioBorder, Offset(0f, h * 0.5f), Offset(w, h * 0.5f), strokeWidth = 1f)

            val path = Path()
            for (s in 0..40) {
                val t = s / 40f
                val y = CubeLutParser.evalCurve(curPoints, t)
                val px = t * w
                val py = (1f - y) * h
                if (s == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            drawPath(path, curveColor, style = Stroke(width = 3f))
            curPoints.forEach { pt ->
                val px = pt.x * w
                val py = (1f - pt.y) * h
                drawCircle(curveColor, radius = 8f, center = Offset(px, py))
                drawCircle(OledBlack, radius = 4f, center = Offset(px, py))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpeedRampInspectorContent(layer: LayerNode, vm: MaxMotionViewModel) {
    val dur = (layer.outMs - layer.inMs).coerceAtLeast(500L)
    val ramp = layer.speedRamp

    Text("Speed Ramping (Remapeamento Temporal)", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    Spacer(Modifier.height(6.dp))

    // Curve preview
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(OledBlack)
            .border(1.dp, StudioBorder, RoundedCornerShape(10.dp))
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            val w = size.width
            val h = size.height
            val baseY = h * (1f - (1f / 3.5f))
            drawLine(StudioBorder, Offset(0f, baseY), Offset(w, baseY), strokeWidth = 1f)
            val path = Path()
            for (i in 0..40) {
                val frac = i / 40f
                val tMs = (dur * frac).toLong()
                val spd = if (ramp != null) {
                    com.example.engine.CurveInterpolator.evaluateKeyframes(ramp, tMs).coerceIn(0.1f, 3.5f)
                } else {
                    layer.speedMultiplier.coerceIn(0.1f, 3.5f)
                }
                val px = frac * w
                val py = h * (1f - (spd / 3.5f))
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            drawPath(path, NeonCyan, style = Stroke(width = 3f))
        }
    }

    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SpeedRampEvaluator.presets(dur).forEach { preset ->
            FilterChip(
                selected = false,
                onClick = { vm.applySpeedRampPreset(layer.id, preset.id) },
                label = { Text(preset.labelPt, style = MaterialTheme.typography.labelSmall) }
            )
        }
        FilterChip(
            selected = ramp == null,
            onClick = { vm.updateSpeedRamp(layer.id, null) },
            label = { Text("Velocidade Linear (1x)", style = MaterialTheme.typography.labelSmall) }
        )
    }
}

@Composable
fun TrackerAiInspectorContent(
    layer: LayerNode,
    isProcessingAi: Boolean,
    trackerTargetMode: Boolean,
    trackerTargetNorm: Pair<Float, Float>,
    onToggleTrackerTarget: () -> Unit,
    vm: MaxMotionViewModel
) {
    if (isProcessingAi) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = NeonCyan, strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text("Processando modelo local no dispositivo...", style = MaterialTheme.typography.bodySmall, color = NeonCyan)
        }
    }

    Text("IA Offline & Rastreamento 2D", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    Spacer(Modifier.height(6.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        if (layer.type == LayerType.IMAGE || layer.type == LayerType.VIDEO) {
            Button(
                onClick = { vm.runAutoCutout(layer.id) },
                enabled = !isProcessingAi,
                modifier = Modifier.testTag("ai_cutout_btn")
            ) {
                Icon(Icons.Rounded.AutoFixHigh, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Recorte IA (Rotoscopia)", style = MaterialTheme.typography.labelSmall)
            }
            if (!layer.aiCutoutMaskUri.isNullOrBlank()) {
                OutlinedButton(onClick = { vm.clearAutoCutout(layer.id) }) {
                    Text("Remover Recorte", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        if (layer.type == LayerType.AUDIO || layer.type == LayerType.VIDEO) {
            Button(
                onClick = { vm.runAutoCaptions(layer.id) },
                enabled = !isProcessingAi,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricPurple, contentColor = TextPrimary),
                modifier = Modifier.testTag("ai_captions_btn")
            ) {
                Icon(Icons.Rounded.ClosedCaption, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Legendas Automáticas", style = MaterialTheme.typography.labelSmall)
            }
        }
    }

    if (layer.type == LayerType.VIDEO) {
        Spacer(Modifier.height(10.dp))
        Text(
            "Rastreador 2D • Alvo: (${String.format(Locale.US, "%.2f", trackerTargetNorm.first)}, ${String.format(Locale.US, "%.2f", trackerTargetNorm.second)})",
            style = MaterialTheme.typography.labelSmall,
            color = BeatAmber
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            OutlinedButton(
                onClick = onToggleTrackerTarget,
                modifier = Modifier.testTag("toggle_tracker_crosshair_btn")
            ) {
                Icon(Icons.Rounded.CenterFocusStrong, null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (trackerTargetMode) "Mira Ativa" else "Posicionar Mira", style = MaterialTheme.typography.labelSmall)
            }
            Button(
                onClick = { vm.runMotionTrack2D(layer.id, stabilize = false) },
                enabled = !isProcessingAi,
                modifier = Modifier.testTag("run_tracker_2d_btn")
            ) {
                Icon(Icons.Rounded.MyLocation, null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text("Rastrear → Nulo", style = MaterialTheme.typography.labelSmall)
            }
            Button(
                onClick = { vm.runMotionTrack2D(layer.id, stabilize = true) },
                enabled = !isProcessingAi,
                colors = ButtonDefaults.buttonColors(containerColor = CacheEmerald, contentColor = OledBlack),
                modifier = Modifier.testTag("stabilize_2d_btn")
            ) {
                Icon(Icons.Rounded.Speed, null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text("Estabilizar 2D", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
