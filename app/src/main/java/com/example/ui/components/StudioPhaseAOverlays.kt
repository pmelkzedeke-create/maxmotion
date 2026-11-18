package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.MovieCreation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ElectricPurple
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.OledBlack
import com.example.ui.theme.StudioHairline
import com.example.ui.theme.StudioPanel
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun EditorEmptyCanvasHint(
    onAddShape: () -> Unit,
    onPickMedia: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Rounded.MovieCreation, null, tint = TextMuted, modifier = Modifier.size(40.dp))
            Text(
                "Nenhuma camada",
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Importe um vídeo ou toque em Forma para começar.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onPickMedia,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = OledBlack),
                    modifier = Modifier.testTag("empty_add_media_btn")
                ) { Text("Mídia") }
                Button(
                    onClick = onAddShape,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricPurple, contentColor = TextPrimary),
                    modifier = Modifier.testTag("empty_add_shape_btn")
                ) { Text("Forma") }
            }
        }
    }
}

@Composable
fun DemoTutorialOverlay(onDismiss: () -> Unit) {
    val steps = remember {
        listOf(
            "Arraste o playhead na timeline para scrubbar a intro.",
            "Toque no prisma no canvas para selecionar a camada.",
            "Abra Curvas no dock para editar o Bézier do scale."
        )
    }
    var step by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OledBlack.copy(alpha = 0.72f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {}
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = StudioPanel,
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, StudioHairline, RoundedCornerShape(22.dp))
                .testTag("demo_tutorial_overlay")
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = NeonCyan)
                    Text("Tour da Demo Neon", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.horizontalGradient(listOf(ElectricPurple.copy(alpha = 0.28f), NeonCyan.copy(alpha = 0.16f))))
                        .padding(14.dp)
                ) {
                    Text(
                        text = "${step + 1}/3  ·  ${steps[step]}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onDismiss, modifier = Modifier.testTag("tutorial_skip_btn")) {
                        Text("Pular")
                    }
                    Button(
                        onClick = {
                            if (step >= steps.lastIndex) onDismiss() else step += 1
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = OledBlack),
                        modifier = Modifier.testTag("tutorial_next_btn")
                    ) {
                        Text(if (step >= steps.lastIndex) "Entendi" else "Próximo")
                    }
                }
            }
        }
    }
}

@Composable
fun TimecodeEditDialog(
    currentLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember { mutableStateOf(currentLabel) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StudioPanel,
        title = { Text("Ir para timecode", color = TextPrimary) },
        text = {
            Column {
                Text("mm:ss:ff  ·  mm:ss  ·  milissegundos", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("timecode_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(value) },
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = OledBlack),
                modifier = Modifier.testTag("timecode_confirm_btn")
            ) { Text("Ir") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
