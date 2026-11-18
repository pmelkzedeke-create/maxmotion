package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.MovieCreation
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.ProjectEntity
import com.example.model.AspectRatioPreset
import com.example.model.OfficialTemplates
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProjectHubScreen(
    projects: List<ProjectEntity>,
    onOpenProject: (String) -> Unit,
    onOpenDemo: () -> Unit,
    onCreateProject: (String, AspectRatioPreset, Int, Int) -> Unit,
    onDuplicateProject: (String) -> Unit,
    onDeleteProject: (String) -> Unit,
    onImportJson: (String) -> Unit,
    crashSnapshotName: String? = null,
    onRestoreCrash: () -> Unit = {},
    onDismissCrash: () -> Unit = {},
    onOpenTemplate: (String) -> Unit = {}
) {
    var showCreateModal by remember { mutableStateOf(false) }
    var showImportModal by remember { mutableStateOf(false) }
    var showTemplatesModal by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = OledBlack,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(14.dp))

            // Cinema Studio Hero Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                ElectricPurple.copy(alpha = 0.34f),
                                StudioPanel,
                                NeonCyan.copy(alpha = 0.24f)
                            ),
                            start = Offset.Zero,
                            end = Offset(900f, 520f)
                        )
                    )
                    .border(1.dp, StudioHairline, RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "MAXMOTION",
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Motion Graphics • Shaders • Edição Pro",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        Surface(
                            color = NeonCyan.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(99.dp)
                        ) {
                            Text(
                                text = "60 FPS",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = NeonCyan,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // CapCut-style Primary "Novo Projeto" Gradient CTA
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Brush.horizontalGradient(listOf(NeonCyan, ElectricPurple)))
                            .clickable { showCreateModal = true }
                            .testTag("create_project_btn"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.Add, null, tint = OledBlack, modifier = Modifier.size(22.dp))
                            Text(
                                text = "Novo Projeto",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = OledBlack
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            color = StudioCard,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .border(0.5.dp, StudioHairline, RoundedCornerShape(14.dp))
                                .clickable(onClick = onOpenDemo)
                                .testTag("open_demo_btn")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.AutoAwesome, null, tint = ElectricPurple, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Demo Neon", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                            }
                        }

                        Surface(
                            color = StudioCard,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .border(0.5.dp, StudioHairline, RoundedCornerShape(14.dp))
                                .clickable { showImportModal = true }
                                .testTag("import_json_btn")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.Code, null, tint = NeonCyan, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Importar JSON", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = StudioCard,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(0.5.dp, StudioHairline, RoundedCornerShape(14.dp))
                            .clickable { showTemplatesModal = true }
                            .testTag("open_templates_btn")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, null, tint = NeonCyan, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Templates oficiais (${OfficialTemplates.catalog.size})", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                        }
                    }
                }
            }

            if (!crashSnapshotName.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = StudioCard,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(0.5.dp, NeonCyan.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                        .testTag("crash_restore_banner")
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Sessão não salva encontrada",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = crashSnapshotName,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onRestoreCrash,
                                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = OledBlack),
                                modifier = Modifier.testTag("restore_crash_btn")
                            ) { Text("Abrir") }
                            TextButton(onClick = onDismissCrash, modifier = Modifier.testTag("dismiss_crash_btn")) {
                                Text("Descartar")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PROJETOS (${projects.size})",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            if (projects.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.MovieCreation, null, tint = TextMuted, modifier = Modifier.size(44.dp))
                        Text(
                            text = "Toque em Novo Projeto ou Demo Neon para começar.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(projects, key = { it.id }) { proj ->
                        ProjectCard(
                            project = proj,
                            onOpen = { onOpenProject(proj.id) },
                            onDuplicate = { onDuplicateProject(proj.id) },
                            onDelete = { onDeleteProject(proj.id) }
                        )
                    }
                }
            }
        }
    }

    if (showCreateModal || showImportModal || showTemplatesModal) {
        BackHandler {
            showCreateModal = false
            showImportModal = false
            showTemplatesModal = false
        }
    }

    AnimatedVisibility(
        visible = showCreateModal,
        enter = fadeIn() + scaleIn(initialScale = 0.94f),
        exit = fadeOut() + scaleOut(targetScale = 0.94f)
    ) {
        var name by remember { mutableStateOf("Edit Cinético") }
        var aspect by remember { mutableStateOf(AspectRatioPreset.PORTRAIT_9_16) }
        var fps by remember { mutableIntStateOf(60) }
        val durationSec = 8

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(OledBlack.copy(alpha = 0.78f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { showCreateModal = false }
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = StudioPanel,
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .border(1.dp, StudioHairline, RoundedCornerShape(22.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {}
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Criar Nova Composição",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nome do Projeto") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("new_project_name_input")
                    )
                    Text("Proporção", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        AspectRatioPreset.entries.chunked(2).forEach { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                pair.forEach { preset ->
                                    FilterChip(
                                        selected = aspect == preset,
                                        onClick = { aspect = preset },
                                        label = { Text(preset.label, style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }
                        }
                    }
                    Text("FPS", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(24, 30, 60).forEach { f ->
                            FilterChip(
                                selected = fps == f,
                                onClick = { fps = f },
                                label = { Text("$f fps") }
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showCreateModal = false }) { Text("Cancelar") }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                showCreateModal = false
                                onCreateProject(name, aspect, fps, durationSec)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = OledBlack),
                            modifier = Modifier.testTag("confirm_create_project_btn")
                        ) {
                            Text("Abrir Estúdio", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    AnimatedVisibility(
        visible = showImportModal,
        enter = fadeIn() + scaleIn(initialScale = 0.94f),
        exit = fadeOut() + scaleOut(targetScale = 0.94f)
    ) {
        var jsonInput by remember { mutableStateOf("") }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(OledBlack.copy(alpha = 0.78f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { showImportModal = false }
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = StudioPanel,
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .border(1.dp, StudioHairline, RoundedCornerShape(22.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {}
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Importar Projeto JSON",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    OutlinedTextField(
                        value = jsonInput,
                        onValueChange = { jsonInput = it },
                        label = { Text("Cole o JSON do MaxMotion aqui") },
                        minLines = 5,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showImportModal = false }) { Text("Cancelar") }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            showImportModal = false
                            onImportJson(jsonInput)
                        }) {
                            Text("Importar")
                        }
                    }
                }
            }
        }
    }

    AnimatedVisibility(
        visible = showTemplatesModal,
        enter = fadeIn() + scaleIn(initialScale = 0.94f),
        exit = fadeOut() + scaleOut(targetScale = 0.94f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(OledBlack.copy(alpha = 0.78f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { showTemplatesModal = false }
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = StudioPanel,
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp)
                    .border(1.dp, StudioHairline, RoundedCornerShape(22.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {}
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Templates oficiais", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("24 looks. Abra e toque em Marca para trocar título e cor.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Spacer(Modifier.height(10.dp))
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.height(420.dp).testTag("templates_list")
                    ) {
                        items(OfficialTemplates.catalog, key = { it.id }) { tpl ->
                            Surface(
                                color = StudioCard,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showTemplatesModal = false
                                        onOpenTemplate(tpl.id)
                                    }
                                    .testTag("template_card_${tpl.id}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(tpl.name, style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                        Text("${tpl.category} · ${tpl.durationMs / 1000}s · ${tpl.brand.title}", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                    }
                                    Text(tpl.aspect.label.substringBefore(" "), style = MaterialTheme.typography.labelSmall, color = NeonCyan)
                                }
                            }
                        }
                    }
                    TextButton(onClick = { showTemplatesModal = false }, modifier = Modifier.align(Alignment.End)) {
                        Text("Fechar")
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectCard(
    project: ProjectEntity,
    onOpen: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFmt = remember { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }
    Surface(
        color = StudioPanel,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(0.5.dp, StudioHairline, RoundedCornerShape(18.dp))
            .clickable(onClick = onOpen)
            .testTag("project_card_${project.id}")
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Stylized Preview Thumbnail Badge
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(ElectricPurple.copy(alpha = 0.35f), NeonCyan.copy(alpha = 0.25f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(OledBlack.copy(alpha = 0.65f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = project.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${project.width}×${project.height}", style = MaterialTheme.typography.labelSmall, color = NeonCyan)
                    Text("${project.fps}fps", style = MaterialTheme.typography.labelSmall, color = ElectricPurple)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Icon(Icons.Rounded.Layers, null, tint = TextMuted, modifier = Modifier.size(12.dp))
                        Text("${project.layerCount}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                }
                Text("Editado ${dateFmt.format(Date(project.updatedAt))}", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            }
            IconButton(onClick = onDuplicate, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.ContentCopy, "Duplicar projeto", tint = TextSecondary, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.Delete, "Excluir projeto", tint = AlertRed.copy(alpha = 0.85f), modifier = Modifier.size(18.dp))
            }
        }
    }
}
