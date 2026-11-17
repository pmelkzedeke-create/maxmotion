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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.SavedCurveEntity
import com.example.engine.CurveInterpolator
import com.example.model.AnimatableProperty
import com.example.model.BezierCurve
import com.example.ui.PropertyKey
import com.example.ui.theme.ElectricPurple
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.OledBlack
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioElevated
import com.example.ui.theme.StudioPanel
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale
import kotlin.math.hypot

@Composable
fun GraphEditorOverlay(
    propertyKey: PropertyKey,
    property: AnimatableProperty,
    savedCurves: List<SavedCurveEntity>,
    copiedCurve: BezierCurve?,
    onUpdateBezier: (keyframeId: String, BezierCurve) -> Unit,
    onCopyCurve: (BezierCurve) -> Unit,
    onSaveCurveToLibrary: (name: String, BezierCurve) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val keyframes = property.keyframes
    var selectedSegmentIndex by remember(keyframes.size) { mutableIntStateOf(0) }
    var isSpeedGraph by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var customName by remember { mutableStateOf("") }

    val activeKf = keyframes.getOrNull(selectedSegmentIndex)
    val currentBezier = activeKf?.bezier ?: BezierCurve(0.22f, 1f, 0.36f, 1f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(StudioPanel)
            .border(1.dp, StudioBorder)
            .padding(12.dp)
            .testTag("graph_editor_overlay")
    ) {
        // Header Row: Value/Speed Graph Switcher + Copy/Paste + Save
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Editor de Curvas • ${propertyKey.labelPt}",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary
                )
                Text(
                    text = String.format(
                        Locale.US,
                        "Bézier [%.2f, %.2f, %.2f, %.2f]",
                        currentBezier.x1, currentBezier.y1, currentBezier.x2, currentBezier.y2
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonCyan
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(
                    selected = !isSpeedGraph,
                    onClick = { isSpeedGraph = false },
                    label = { Text("Valor", style = MaterialTheme.typography.labelSmall) }
                )
                FilterChip(
                    selected = isSpeedGraph,
                    onClick = { isSpeedGraph = true },
                    label = { Text("Velocidade", style = MaterialTheme.typography.labelSmall) }
                )
                IconButton(onClick = { onCopyCurve(currentBezier) }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.ContentCopy, "Copiar curva", tint = TextSecondary, modifier = Modifier.size(18.dp))
                }
                if (copiedCurve != null && activeKf != null) {
                    IconButton(onClick = { onUpdateBezier(activeKf.id, copiedCurve) }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Rounded.ContentPaste, "Colar curva", tint = NeonCyan, modifier = Modifier.size(18.dp))
                    }
                }
                IconButton(onClick = { showSaveDialog = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.BookmarkAdd, "Salvar curva na biblioteca", tint = ElectricPurple, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.Close, "Fechar Editor de Curvas", tint = TextPrimary)
                }
            }
        }

        if (keyframes.size < 2) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(OledBlack),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Adicione pelo menos 2 keyframes em ${propertyKey.labelPt} para editar a curva.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        } else {
            // Segment selector when more than 2 keyframes exist
            if (keyframes.size > 2) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (i in 0 until keyframes.size - 1) {
                        FilterChip(
                            selected = selectedSegmentIndex == i,
                            onClick = { selectedSegmentIndex = i },
                            label = {
                                Text("KF ${i + 1} → ${i + 2}", style = MaterialTheme.typography.labelSmall)
                            }
                        )
                    }
                }
            }

            // Interactive Cubic Bezier Canvas
            var draggingHandle by remember { mutableIntStateOf(0) } // 1 = P1, 2 = P2
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(OledBlack)
                    .border(1.dp, StudioBorder, RoundedCornerShape(12.dp))
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(22.dp)
                        .pointerInput(activeKf?.id, currentBezier) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val w = size.width.toFloat().coerceAtLeast(1f)
                                    val h = size.height.toFloat().coerceAtLeast(1f)
                                    val p1 = Offset(currentBezier.x1 * w, (1f - currentBezier.y1) * h)
                                    val p2 = Offset(currentBezier.x2 * w, (1f - currentBezier.y2) * h)
                                    val d1 = hypot(offset.x - p1.x, offset.y - p1.y)
                                    val d2 = hypot(offset.x - p2.x, offset.y - p2.y)
                                    draggingHandle = if (d1 <= d2) 1 else 2
                                },
                                onDragEnd = { draggingHandle = 0 },
                                onDrag = { change, _ ->
                                    val kf = activeKf ?: return@detectDragGestures
                                    change.consume()
                                    val w = size.width.toFloat().coerceAtLeast(1f)
                                    val h = size.height.toFloat().coerceAtLeast(1f)
                                    val nx = (change.position.x / w).coerceIn(0f, 1f)
                                    val ny = (1f - change.position.y / h).coerceIn(-0.6f, 1.6f)
                                    val updated = if (draggingHandle == 1) {
                                        currentBezier.copy(x1 = nx, y1 = ny)
                                    } else {
                                        currentBezier.copy(x2 = nx, y2 = ny)
                                    }
                                    onUpdateBezier(kf.id, updated)
                                }
                            )
                        }
                ) {
                    val w = size.width
                    val h = size.height
                    // Grid lines
                    for (i in 0..4) {
                        val gx = w * (i / 4f)
                        val gy = h * (i / 4f)
                        drawLine(StudioBorder.copy(alpha = 0.55f), Offset(gx, 0f), Offset(gx, h), strokeWidth = 1f)
                        drawLine(StudioBorder.copy(alpha = 0.55f), Offset(0f, gy), Offset(w, gy), strokeWidth = 1f)
                    }

                    // Sample either Value Curve or Speed Curve
                    val samples = if (isSpeedGraph) {
                        CurveInterpolator.sampleSpeedCurve(currentBezier, 64)
                    } else {
                        CurveInterpolator.sampleValueCurve(currentBezier, 64)
                    }
                    val maxSpeed = if (isSpeedGraph) (samples.maxOfOrNull { it.second } ?: 1f).coerceAtLeast(1f) else 1f

                    val path = Path()
                    samples.forEachIndexed { idx, (x, y) ->
                        val px = x * w
                        val py = if (isSpeedGraph) h - (y / maxSpeed) * h else (1f - y) * h
                        if (idx == 0) path.moveTo(px, py) else path.lineTo(px, py)
                    }
                    drawPath(
                        path = path,
                        color = if (isSpeedGraph) ElectricPurple else NeonCyan,
                        style = Stroke(width = 4.5f, cap = StrokeCap.Round)
                    )

                    // Bezier Handles P1 and P2
                    val startPt = Offset(0f, h)
                    val endPt = Offset(w, 0f)
                    val p1 = Offset(currentBezier.x1 * w, (1f - currentBezier.y1) * h)
                    val p2 = Offset(currentBezier.x2 * w, (1f - currentBezier.y2) * h)

                    drawLine(ElectricPurple, startPt, p1, strokeWidth = 2.5f)
                    drawLine(NeonCyan, endPt, p2, strokeWidth = 2.5f)

                    drawCircle(Color.White, radius = 12f, center = p1)
                    drawCircle(ElectricPurple, radius = 12f, center = p1, style = Stroke(width = 4f))

                    drawCircle(Color.White, radius = 12f, center = p2)
                    drawCircle(NeonCyan, radius = 12f, center = p2, style = Stroke(width = 4f))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Saved Curves & Presets Strip (from Room)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                savedCurves.forEach { preset ->
                    val b = BezierCurve(preset.x1, preset.y1, preset.x2, preset.y2)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(StudioElevated)
                            .border(1.dp, if (currentBezier == b) NeonCyan else StudioBorder, RoundedCornerShape(10.dp))
                            .clickable {
                                activeKf?.let { onUpdateBezier(it.id, b) }
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = preset.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (currentBezier == b) NeonCyan else TextPrimary
                        )
                    }
                }
            }
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Salvar Curva na Biblioteca") },
            text = {
                OutlinedTextField(
                    value = customName,
                    onValueChange = { customName = it },
                    label = { Text("Nome da Curva") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    onSaveCurveToLibrary(customName, currentBezier)
                    customName = ""
                    showSaveDialog = false
                }) {
                    Text("Salvar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("Cancelar") }
            }
        )
    }
}
