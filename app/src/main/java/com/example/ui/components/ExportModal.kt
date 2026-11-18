package com.example.ui.components

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.engine.DeliveryPreset
import com.example.engine.StudioEditOps
import com.example.render.ExportCodec
import com.example.render.ExportResolution
import com.example.render.ExportResult
import com.example.render.ExportSettings
import com.example.ui.theme.AlertRed
import com.example.ui.theme.CacheEmerald
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.OledBlack
import com.example.ui.theme.StudioPanel
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun ExportDialog(
    durationMs: Long,
    workAreaInMs: Long = 0L,
    workAreaOutMs: Long = -1L,
    exportProgress: Float?,
    exportMessage: String?,
    exportResult: ExportResult?,
    onStartExport: (ExportSettings) -> Unit,
    onCopyProjectJson: () -> Unit,
    onDismiss: () -> Unit
) {
    var preset by remember { mutableStateOf(DeliveryPreset.REELS_1080_60) }
    var resolution by remember { mutableStateOf(ExportResolution.FHD_1080) }
    var fps by remember { mutableIntStateOf(60) }
    var codec by remember { mutableStateOf(ExportCodec.H264) }
    var bitrate by remember { mutableIntStateOf(16) }
    val rangeEnd = if (preset == DeliveryPreset.WORK_AREA) {
        if (workAreaOutMs < 0L) durationMs else workAreaOutMs
    } else {
        -1L
    }
    val rangeStart = if (preset == DeliveryPreset.WORK_AREA) workAreaInMs else 0L
    val settings = ExportSettings(resolution, fps, codec, bitrate, rangeStart, rangeEnd)
    val exportDuration = if (rangeEnd > rangeStart) rangeEnd - rangeStart else durationMs
    val estMb = settings.estimateSizeMb(exportDuration)
    val context = LocalContext.current

    fun shareExported(uri: Uri?, packageName: String?) {
        if (uri == null) return
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("video", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (!packageName.isNullOrBlank()) {
            val targeted = Intent(send).setPackage(packageName)
            val ok = runCatching { context.startActivity(targeted) }.isSuccess
            if (ok) return
        }
        runCatching {
            context.startActivity(Intent.createChooser(send, "Compartilhar MaxMotion"))
        }
    }

    AlertDialog(
        onDismissRequest = { if (exportProgress == null) onDismiss() },
        containerColor = StudioPanel,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Movie, null, tint = NeonCyan)
                Spacer(Modifier.width(8.dp))
                Text("Exportar Composição", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (exportProgress != null) {
                    Text(exportMessage ?: "Renderizando...", style = MaterialTheme.typography.bodyMedium, color = NeonCyan)
                    LinearProgressIndicator(
                        progress = { exportProgress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = NeonCyan
                    )
                } else if (exportResult != null) {
                    when (exportResult) {
                        is ExportResult.Success -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.CheckCircle, null, tint = CacheEmerald)
                                Spacer(Modifier.width(8.dp))
                                Text("Vídeo MP4 salvo na Galeria (Movies/MaxMotion)!", style = MaterialTheme.typography.bodyMedium, color = CacheEmerald)
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { shareExported(exportResult.outputUri, "com.whatsapp") },
                                    modifier = Modifier.weight(1f).testTag("share_whatsapp_btn")
                                ) { Text("WhatsApp", style = MaterialTheme.typography.labelSmall) }
                                OutlinedButton(
                                    onClick = { shareExported(exportResult.outputUri, "com.instagram.android") },
                                    modifier = Modifier.weight(1f).testTag("share_reels_btn")
                                ) { Text("Reels", style = MaterialTheme.typography.labelSmall) }
                                OutlinedButton(
                                    onClick = { shareExported(exportResult.outputUri, null) },
                                    modifier = Modifier.weight(1f).testTag("share_more_btn")
                                ) { Text("Mais", style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                        is ExportResult.Error -> {
                            Text("Erro: ${exportResult.message}", style = MaterialTheme.typography.bodyMedium, color = AlertRed)
                        }
                    }
                } else {
                    Text("Entrega", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        DeliveryPreset.entries.filter { it != DeliveryPreset.CUSTOM }.chunked(2).forEach { row ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                row.forEach { p ->
                                    FilterChip(
                                        selected = preset == p,
                                        onClick = {
                                            preset = p
                                            val mapped = StudioEditOps.deliverySettings(p, fps)
                                            resolution = mapped.resolution
                                            fps = mapped.fps
                                            codec = mapped.codec
                                            bitrate = mapped.bitrateMbps
                                        },
                                        label = { Text(p.labelPt, style = MaterialTheme.typography.labelSmall) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("export_preset_${p.name}")
                                    )
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }

                    Text("Resolução", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ExportResolution.entries.forEach { res ->
                            FilterChip(
                                selected = resolution == res,
                                onClick = { resolution = res },
                                label = { Text(res.label.substringBefore(" "), style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    Text("Taxa de Quadros (FPS)", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(24, 30, 60).forEach { f ->
                            FilterChip(
                                selected = fps == f,
                                onClick = { fps = f },
                                label = { Text("$f fps", style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    Text("Codec de Hardware", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ExportCodec.entries.forEach { c ->
                            FilterChip(
                                selected = codec == c,
                                onClick = { codec = c },
                                label = { Text(c.label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    Text(
                        text = "Bitrate: $bitrate Mbps  •  Estimativa: ${String.format(Locale.US, "%.1f", estMb)} MB",
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonCyan
                    )
                    Slider(
                        value = bitrate.toFloat(),
                        onValueChange = { bitrate = it.toInt() },
                        valueRange = 4f..35f
                    )

                    OutlinedButton(
                        onClick = onCopyProjectJson,
                        modifier = Modifier.fillMaxWidth().testTag("export_json_btn")
                    ) {
                        Icon(Icons.Rounded.Code, null, tint = NeonCyan)
                        Spacer(Modifier.width(6.dp))
                        Text("Copiar Pacote JSON do Projeto")
                    }
                }
            }
        },
        confirmButton = {
            if (exportProgress == null && exportResult == null) {
                Button(
                    onClick = { onStartExport(settings) },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = OledBlack),
                    modifier = Modifier.testTag("start_export_mp4_btn")
                ) {
                    Text("Renderizar MP4")
                }
            } else if (exportResult != null) {
                Button(onClick = onDismiss) { Text("Concluir") }
            }
        },
        dismissButton = {
            if (exportProgress == null && exportResult == null) {
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        }
    )
}
