package com.fitlog.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import com.fitlog.app.domain.ExerciseSpec

@Entity(tableName = "food_records")
data class FoodRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String, val calories: Double, val photoPath: String? = null,
    val dateEpochDay: Long, val createdAt: Long,
    val proteinGrams: Double? = null, val carbsGrams: Double? = null, val fatGrams: Double? = null
)

@Entity(tableName = "workout_plans")
data class WorkoutPlan(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String, val exerciseCount: Int, val createdAt: Long
)

@Entity(tableName = "plan_exercises", foreignKeys = [
    ForeignKey(entity = WorkoutPlan::class, parentColumns = ["id"], childColumns = ["planId"], onDelete = ForeignKey.CASCADE)
], indices = [Index("planId")])
data class PlanExercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long, val name: String, val targetSets: Int, val weightKg: Double,
    val reps: Int = 10, val restSeconds: Int, val orderIndex: Int,
    @ColumnInfo(defaultValue = "0") val isBodyweight: Boolean = false,
    @ColumnInfo(defaultValue = "''") val muscleGroup: String = "",
    @ColumnInfo(defaultValue = "''") val iconPath: String = "",
    @ColumnInfo(defaultValue = "''") val videoUrl: String = "",
    @ColumnInfo(defaultValue = "0") val isCardio: Boolean = false,
    @ColumnInfo(defaultValue = "'[]'") val setTargetsJson: String = "[]",
    @ColumnInfo(defaultValue = "'{}'") val cardioJson: String = "{}"
) {
    fun spec() = ExerciseSpec(name, targetSets, reps, weightKg, restSeconds,
        isBodyweight || name.trim() in com.fitlog.app.domain.BodyWeight.exercises, muscleGroup, iconPath, videoUrl, isCardio,
        ExerciseJson.decodeTargets(setTargetsJson), ExerciseJson.decodeCardio(cardioJson)).normalized()
}

@Entity(tableName = "workout_records", indices = [Index(value = ["sessionKey"], unique = true)])
data class WorkoutRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planName: String, val dateEpochDay: Long, val totalSets: Int, val summary: String,
    val durationSeconds: Int, val createdAt: Long, val sessionKey: String? = null,
    val volumeKg: Double? = null,
    @ColumnInfo(defaultValue = "0") val cardioSeconds: Int = 0,
    @ColumnInfo(defaultValue = "0") val cardioDistanceMeters: Double = 0.0,
    @ColumnInfo(defaultValue = "0") val cardioRounds: Int = 0
)

@Entity(tableName = "sleep_records")
data class SleepRecord(@PrimaryKey val dateEpochDay: Long, val hours: Double)
