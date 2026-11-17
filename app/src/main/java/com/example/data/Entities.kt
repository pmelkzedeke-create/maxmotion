package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val width: Int,
    val height: Int,
    val fps: Int,
    val durationMs: Long,
    val layerCount: Int,
    val updatedAt: Long,
    val jsonFilePath: String
)

@Entity(tableName = "saved_curves")
data class SavedCurveEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val isBuiltIn: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
