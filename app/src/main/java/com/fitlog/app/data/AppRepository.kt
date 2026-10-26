package com.fitlog.app.data

import androidx.room.withTransaction
import com.fitlog.app.domain.ExerciseSpec

class AppRepository(private val db: AppDatabase) {
    val foods = db.food().observe()
    val plans = db.workouts().plans()
    val workouts = db.workouts().records()
    val sleeps = db.sleep().observe()

    suspend fun saveFood(record: FoodRecord) {
        require(record.name.isNotBlank() && record.calories.isFinite() && record.calories >= 0)
        require(listOfNotNull(record.proteinGrams, record.carbsGrams, record.fatGrams).all { it.isFinite() && it >= 0 })
        db.food().insert(record.copy(name = record.name.trim()))
    }
    suspend fun deleteFood(record: FoodRecord) = db.food().delete(record)
    suspend fun saveFoods(records: List<FoodRecord>) = db.withTransaction {
        require(records.isNotEmpty() && records.size <= 200)
        records.forEach { saveFood(it) }
    }
    suspend fun deleteWorkout(record: WorkoutRecord) = db.workouts().deleteRecord(record)
    suspend fun saveWorkout(record: WorkoutRecord) = db.workouts().insertRecord(record)
    suspend fun updateWorkout(record: WorkoutRecord) {
        require(record.id > 0 && record.planName.isNotBlank() && record.durationSeconds >= 0 && record.totalSets > 0)
        require(record.volumeKg == null || (record.volumeKg.isFinite() && record.volumeKg >= 0))
        require(record.cardioSeconds >= 0 && record.cardioRounds in 0..record.totalSets &&
            record.cardioDistanceMeters.isFinite() && record.cardioDistanceMeters >= 0)
        check(db.workouts().updateRecord(record.copy(planName = record.planName.trim())) == 1) { "记录已删除，请重新打开" }
    }
    suspend fun exercises(id: Long) = db.workouts().exercises(id).map { it.spec() }

    suspend fun savePlan(name: String, exercises: List<ExerciseSpec>, planId: Long? = null) = db.withTransaction {
        require(name.isNotBlank() && exercises.isNotEmpty())
        val clean = exercises.map { it.normalized() }
        require(clean.all { it.name.isNotBlank() })
        val id = if (planId == null) {
            db.workouts().insertPlan(WorkoutPlan(name = name.trim(), exerciseCount = clean.size, createdAt = System.currentTimeMillis()))
        } else {
            require(planId > 0)
            check(db.workouts().updatePlan(planId, name.trim(), clean.size) == 1) { "计划已删除，请重新打开" }
            // Preserve the plan ID and creation date, so cycle references remain valid.
            // Replacing children is atomic with the header update and all insertions.
            db.workouts().deleteExercises(planId)
            planId
        }
        db.workouts().insertExercises(clean.mapIndexed { index, e ->
            PlanExercise(planId = id, name = e.name, targetSets = e.sets, weightKg = e.weight,
                reps = e.reps, restSeconds = e.restSeconds, orderIndex = index, isBodyweight = e.isBodyweight,
                muscleGroup = e.muscleGroup, iconPath = e.iconPath, videoUrl = e.videoUrl, isCardio = e.isCardio,
                setTargetsJson = ExerciseJson.encodeTargets(e.setTargets), cardioJson = ExerciseJson.encodeCardio(e.cardioTarget))
        })
        id
    }
    suspend fun deletePlan(id: Long) = db.workouts().deletePlan(id)
    suspend fun saveSleep(day: Long, hours: Double) {
        require(hours.isFinite() && hours in 0.0..24.0)
        if (hours == 0.0) db.sleep().delete(day) else db.sleep().save(SleepRecord(day, hours))
    }
}
