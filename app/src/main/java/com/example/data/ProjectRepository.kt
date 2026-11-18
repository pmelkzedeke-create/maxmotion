package com.example.data

import android.content.Context
import android.net.Uri
import com.example.engine.CurveInterpolator
import com.example.model.BezierCurve
import com.example.model.DemoProjectFactory
import com.example.model.ProjectDocument
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class ProjectRepository(
    private val context: Context,
    private val dao: MaxMotionDao
) {
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val projectAdapter = moshi.adapter(ProjectDocument::class.java).indent("  ")

    private val projectsDir: File
        get() = File(context.filesDir, "maxmotion_projects").apply { mkdirs() }

    private val mediaDir: File
        get() = File(context.filesDir, "maxmotion_media").apply { mkdirs() }

    private val crashFile: File
        get() = File(context.filesDir, "maxmotion_crash_snapshot.json")

    private val versionsRoot: File
        get() = File(context.filesDir, "maxmotion_versions").apply { mkdirs() }

    private val prefs = context.getSharedPreferences("maxmotion_studio", Context.MODE_PRIVATE)

    val projectsFlow: Flow<List<ProjectEntity>> = dao.observeProjects()
    val curvesFlow: Flow<List<SavedCurveEntity>> = dao.observeSavedCurves()

    suspend fun ensureSeeded() = withContext(Dispatchers.IO) {
        val builtIns = CurveInterpolator.builtInPresets.map { preset ->
            SavedCurveEntity(
                id = "builtin_${preset.id}",
                name = preset.namePt,
                category = preset.categoryPt,
                x1 = preset.bezier.x1,
                y1 = preset.bezier.y1,
                x2 = preset.bezier.x2,
                y2 = preset.bezier.y2,
                isBuiltIn = true,
                createdAt = 0L
            )
        }
        dao.insertCurvesIgnore(builtIns)

        val demo = DemoProjectFactory.createDemoProject()
        if (dao.getProjectById(demo.id) == null) {
            saveProject(demo)
        }
    }

    suspend fun saveProject(document: ProjectDocument): ProjectEntity = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val updatedDoc = document.copy(updatedAt = now)
        val file = File(projectsDir, "${updatedDoc.id}.json")
        file.writeText(projectAdapter.toJson(updatedDoc))

        val comp = updatedDoc.activeComposition
        val entity = ProjectEntity(
            id = updatedDoc.id,
            name = updatedDoc.name,
            width = comp.width,
            height = comp.height,
            fps = comp.fps,
            durationMs = comp.durationMs,
            layerCount = comp.layers.size,
            updatedAt = now,
            jsonFilePath = file.absolutePath
        )
        dao.upsertProject(entity)
        writeVersionSnapshot(updatedDoc)
        entity
    }

    fun writeCrashSnapshot(document: ProjectDocument) {
        runCatching { crashFile.writeText(projectAdapter.toJson(document.copy(updatedAt = System.currentTimeMillis()))) }
    }

    fun loadCrashSnapshot(): ProjectDocument? {
        if (!crashFile.exists() || crashFile.length() < 8L) return null
        return runCatching { projectAdapter.fromJson(crashFile.readText()) }.getOrNull()
    }

    fun clearCrashSnapshot() {
        if (crashFile.exists()) crashFile.delete()
    }

    fun hasUnseenDemoTutorial(): Boolean = !prefs.getBoolean("tutorial_demo_seen", false)

    fun markDemoTutorialSeen() {
        prefs.edit().putBoolean("tutorial_demo_seen", true).apply()
    }

    private fun writeVersionSnapshot(document: ProjectDocument) {
        runCatching {
            val dir = File(versionsRoot, document.id).apply { mkdirs() }
            val file = File(dir, "${document.updatedAt}.json")
            file.writeText(projectAdapter.toJson(document))
            dir.listFiles()
                ?.filter { it.extension == "json" }
                ?.sortedByDescending { it.name }
                ?.drop(10)
                ?.forEach { it.delete() }
        }
    }

    suspend fun loadProject(projectId: String): ProjectDocument? = withContext(Dispatchers.IO) {
        val entity = dao.getProjectById(projectId)
        val file = if (entity != null) File(entity.jsonFilePath) else File(projectsDir, "$projectId.json")
        if (!file.exists()) return@withContext null
        runCatching { projectAdapter.fromJson(file.readText()) }.getOrNull()
    }

    suspend fun deleteProject(projectId: String) = withContext(Dispatchers.IO) {
        val file = File(projectsDir, "$projectId.json")
        if (file.exists()) file.delete()
        dao.deleteProjectById(projectId)
    }

    suspend fun duplicateProject(projectId: String): ProjectDocument? = withContext(Dispatchers.IO) {
        val original = loadProject(projectId) ?: return@withContext null
        val copy = original.copy(
            id = UUID.randomUUID().toString(),
            name = "${original.name} (Cópia)",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        saveProject(copy)
        copy
    }

    fun exportProjectJson(document: ProjectDocument): String {
        return projectAdapter.toJson(document)
    }

    suspend fun importProjectJson(rawJson: String): Result<ProjectDocument> = withContext(Dispatchers.IO) {
        runCatching {
            val parsed = projectAdapter.fromJson(rawJson)
                ?: throw IllegalArgumentException("JSON inválido para projeto MaxMotion")
            val reId = parsed.copy(
                id = UUID.randomUUID().toString(),
                name = "${parsed.name} (Importado)",
                updatedAt = System.currentTimeMillis()
            )
            saveProject(reId)
            reId
        }
    }

    suspend fun saveCustomCurve(name: String, bezier: BezierCurve) = withContext(Dispatchers.IO) {
        dao.upsertCurve(
            SavedCurveEntity(
                id = UUID.randomUUID().toString(),
                name = name.ifBlank { "Curva Personalizada" },
                category = "Minhas Curvas",
                x1 = bezier.x1,
                y1 = bezier.y1,
                x2 = bezier.x2,
                y2 = bezier.y2,
                isBuiltIn = false
            )
        )
    }

    suspend fun deleteCustomCurve(id: String) = withContext(Dispatchers.IO) {
        dao.deleteCustomCurveById(id)
    }

    /**
     * Copies a selected Photo Picker / SAF URI into app-local media storage so it stays playable
     * even after temporary URI grants expire.
     */
    suspend fun copyMediaToLocalStorage(uri: Uri, prefix: String, ext: String): String = withContext(Dispatchers.IO) {
        val target = File(mediaDir, "${prefix}_${UUID.randomUUID()}.$ext")
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        Uri.fromFile(target).toString()
    }
}
