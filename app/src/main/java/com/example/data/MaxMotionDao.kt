package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MaxMotionDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun observeProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    suspend fun getProjectById(id: String): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProject(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteProjectById(id: String)

    @Query("SELECT * FROM saved_curves ORDER BY isBuiltIn DESC, createdAt DESC")
    fun observeSavedCurves(): Flow<List<SavedCurveEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCurve(curve: SavedCurveEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCurvesIgnore(curves: List<SavedCurveEntity>)

    @Query("DELETE FROM saved_curves WHERE id = :id AND isBuiltIn = 0")
    suspend fun deleteCustomCurveById(id: String)
}
