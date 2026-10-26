package com.fitlog.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodDao {
    @Query("SELECT * FROM food_records ORDER BY dateEpochDay DESC, createdAt DESC")
    fun observe(): Flow<List<FoodRecord>>
    @Insert suspend fun insert(record: FoodRecord): Long
    @Delete suspend fun delete(record: FoodRecord)
}

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM workout_plans ORDER BY createdAt DESC")
    fun plans(): Flow<List<WorkoutPlan>>
    @Query("SELECT * FROM plan_exercises WHERE planId = :id ORDER BY orderIndex")
    suspend fun exercises(id: Long): List<PlanExercise>
    @Insert suspend fun insertPlan(plan: WorkoutPlan): Long
    @Insert suspend fun insertExercises(exercises: List<PlanExercise>)
    @Query("UPDATE workout_plans SET name = :name, exerciseCount = :count WHERE id = :id")
    suspend fun updatePlan(id: Long, name: String, count: Int): Int
    @Query("DELETE FROM plan_exercises WHERE planId = :id")
    suspend fun deleteExercises(id: Long)
    @Query("DELETE FROM workout_plans WHERE id = :id")
    suspend fun deletePlan(id: Long)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRecord(record: WorkoutRecord): Long
    @Query("SELECT * FROM workout_records ORDER BY dateEpochDay DESC, createdAt DESC")
    fun records(): Flow<List<WorkoutRecord>>
    @Delete suspend fun deleteRecord(record: WorkoutRecord)
    @Update suspend fun updateRecord(record: WorkoutRecord): Int
}

@Dao
interface SleepDao {
    @Query("SELECT * FROM sleep_records ORDER BY dateEpochDay DESC")
    fun observe(): Flow<List<SleepRecord>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun save(record: SleepRecord)
    @Query("DELETE FROM sleep_records WHERE dateEpochDay = :day") suspend fun delete(day: Long)
}
