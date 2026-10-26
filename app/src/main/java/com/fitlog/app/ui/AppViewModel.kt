package com.fitlog.app.ui

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fitlog.app.data.*
import com.fitlog.app.domain.*
import com.fitlog.app.feedback.FeedbackCue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class LogData(val foods: List<FoodRecord> = emptyList(), val plans: List<WorkoutPlan> = emptyList(),
    val workouts: List<WorkoutRecord> = emptyList(), val sleeps: List<SleepRecord> = emptyList(), val ready: Boolean = false)
data class SessionSummary(val title: String, val sets: Int, val exercises: Int, val seconds: Int, val volume: Double, val complete: Boolean,
    val cardioSeconds: Int = 0, val cardioDistance: Double = 0.0, val cardioRounds: Int = 0,
    val completed: List<CompletedSet> = emptyList(), val dateEpochDay: Long = 0)
data class TrainingPreview(val title: String, val exercises: List<ExerciseSpec>)
sealed interface UiEvent {
    data class Feedback(val cue: FeedbackCue) : UiEvent
    data class Message(val text: String) : UiEvent
}

class AppViewModel(context: Context) : ViewModel() {
    private val repo = AppRepository(AppDatabase.open(context))
    val settings = SettingsRepository(context)
    val mijia = com.fitlog.app.data.mijia.MiCloudRepository(context, settings)
    fun syncMiWeight(automatic: Boolean = false) { viewModelScope.launch { mijia.sync(automatic) } }
    fun connectMi(uid: String, pass: String, region: String) { viewModelScope.launch { mijia.connect(uid, pass, region) } }
    fun refreshMiMembers() { viewModelScope.launch { mijia.refreshProfiles() } }
    fun selectMiMember(key: String) { viewModelScope.launch { mijia.select(key); mijia.sync() } }
    fun setMiAuto(enabled: Boolean) { viewModelScope.launch { mijia.setAuto(enabled) } }
    fun disconnectMi() { viewModelScope.launch { mijia.disconnect() } }
    private val store = SessionStore(context)
    private val customStore = CustomExerciseStore(context)
    val customExercises = customStore.values
    private val exerciseMemory = ExerciseMemoryStore(context)
    val rememberedExercises = exerciseMemory.values
    fun rememberedExercise(spec: ExerciseSpec) = exerciseMemory.apply(spec)
    val exerciseLibrary = combine(customStore.values, customStore.hiddenNames, exerciseMemory.values) { _, _, _ ->
        customStore.library().map(exerciseMemory::apply)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, customStore.library().map(exerciseMemory::apply))
    private val foodStore = FoodCatalogStore(context)
    val foodLibrary = foodStore.builtIn
    val customFoods = foodStore.values
    suspend fun importFoodImage(uri: android.net.Uri) = foodStore.importImage(uri)
    fun saveCustomFood(food: FoodSpec, done: () -> Unit) = operation { foodStore.save(food); done() }
    fun deleteCustomFood(food: FoodSpec) = operation { foodStore.delete(food) }
    fun saveMeal(items: List<FoodPortion>, date: LocalDate, done: () -> Unit) = operation {
        require(FoodNutrition.canSave(items))
        val timestamp = System.currentTimeMillis()
        repo.saveFoods(items.map { (food, quantity) -> FoodRecord(
            name = "${food.name} · ${foodNumber(quantity)}份（${food.portion}）", calories = food.calories!! * quantity,
            photoPath = food.image.takeIf { it.isNotBlank() }, dateEpochDay = date.toEpochDay(), createdAt = timestamp,
            proteinGrams = food.protein?.times(quantity), carbsGrams = food.carbs?.times(quantity), fatGrams = food.fat?.times(quantity)) })
        cue(FeedbackCue.CONFIRM); done()
    }
    private val wallOrigin = System.currentTimeMillis()
    private val monotonicOrigin = SystemClock.elapsedRealtime()
    private fun time() = wallOrigin + SystemClock.elapsedRealtime() - monotonicOrigin
    val data = combine(repo.foods, repo.plans, repo.workouts, repo.sleeps) { f, p, w, s -> LogData(f, p, w, s, true) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, LogData())
    val preferences = settings.values
    private val active = MutableStateFlow(store.load())
    val session = active.asStateFlow()
    private val undoSteps = MutableStateFlow(store.loadUndo(active.value?.key).filter { it.isUndoable })
    val undoLabel = undoSteps.map { it.lastOrNull()?.label }
        .stateIn(viewModelScope, SharingStarted.Eagerly, undoSteps.value.lastOrNull()?.label)
    private val clock = MutableStateFlow(time())
    val now = clock.asStateFlow()
    val today = now.map { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
        .distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, LocalDate.now())
    private val working = MutableStateFlow(false)
    val busy = working.asStateFlow()
    private val result = MutableStateFlow<SessionSummary?>(null)
    val summary = result.asStateFlow()
    private val eventsChannel = Channel<UiEvent>(Channel.BUFFERED)
    val events = eventsChannel.receiveAsFlow()
    private val sessionLock = Mutex()

    init {
        if (store.restoreFailed) message("上次训练恢复失败；历史记录不受影响。")
        // A process can stop after the final set was checkpointed but before Room committed.
        if (active.value?.let { it.endedAt != null && it.pendingCardioReview == null && it.completed.isNotEmpty() } == true) operation {
            sessionLock.withLock { saveSessionLocked() }
        }
        viewModelScope.launch {
            while (true) {
                clock.value = time()
                sessionLock.withLock {
                    val current = active.value
                    if (current != null) {
                        val updated = CardioEngine.tick(current, clock.value)
                        if (updated != current) runCatching {
                            store.save(updated, undoSteps.value); active.value = updated
                            cue(if (updated.completed.size > current.completed.size) FeedbackCue.SET else FeedbackCue.REST)
                        }.onFailure { message("训练状态保存失败，请重试") }
                    }
                }
                delay(250)
            }
        }
    }

    fun cue(cue: FeedbackCue) { eventsChannel.trySend(UiEvent.Feedback(cue)) }
    fun message(text: String) { eventsChannel.trySend(UiEvent.Message(text)) }
    suspend fun scheduleExerciseNames(key: String): List<String> {
        val template = PresetData.templates.firstOrNull { it.key == key }
        val id = key.removePrefix("plan:").takeIf { key.startsWith("plan:") }?.toLongOrNull()
        return (template?.exercises ?: id?.let { repo.exercises(it) }.orEmpty()).map { it.name }
    }
    suspend fun trainingPreview(key: String): TrainingPreview? {
        if (key.startsWith("scheduled|")) return trainingPreview(ScheduleCatchUp.key(key))
        if (key == "active") return active.value?.let { TrainingPreview(it.title, it.exercises) }
        val template = PresetData.templates.firstOrNull { it.key == key }
        if (template != null) return TrainingPreview(template.title, template.exercises.map(exerciseMemory::apply))
        val id = key.takeIf { it.startsWith("plan:") }?.removePrefix("plan:")?.toLongOrNull() ?: return null
        val plans = if (data.value.ready) data.value.plans else data.first { it.ready }.plans
        val title = plans.firstOrNull { it.id == id }?.name ?: return null
        return TrainingPreview(title, repo.exercises(id).map(exerciseMemory::apply)).takeIf { it.exercises.isNotEmpty() }
    }
    private fun operation(block: suspend () -> Unit) {
        if (working.value) return
        working.value = true
        viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { message("操作未完成，请重试。"); cue(FeedbackCue.ERROR) }
            finally { working.value = false }
        }
    }

    fun begin(title: String, exercises: List<ExerciseSpec>, ready: () -> Unit) = operation {
        sessionLock.withLock {
            if (active.value == null) {
                if (exercises.any { it.isBodyweight } && preferences.value.bodyWeightKg == null) {
                    message("请先填写体重，再开始自重训练"); return@operation
                }
                val fresh = WorkoutEngine.start(title, exercises.map(exerciseMemory::apply), time(), bodyWeightKg = preferences.value.bodyWeightKg)
                store.save(fresh, emptyList()); undoSteps.value = emptyList()
                active.value = fresh
                result.value = null
            }
        }
        ready()
    }

    fun beginPlan(key: String, ready: () -> Unit) = operation {
        sessionLock.withLock {
            if (active.value == null) {
                val planKey = ScheduleCatchUp.key(key)
                val choice = ScheduleCatchUp.choice(key)
                val date = today.value.toEpochDay()
                if (choice != null && choice.first != date) { message("日期已变化，请返回重新选择今日训练"); return@operation }
                if (choice != null && todayPlanCompleted(planKey, today.value)) { message("今日计划已完成，可返回查看训练记录"); return@operation }
                if (choice != null) {
                    val expected = if (choice.second) missedTraining()?.key else preferences.value.scheduleFor(today.value)
                    if (expected != planKey) { message("训练安排已更新，请返回重新选择"); return@operation }
                }
                val template = PresetData.templates.firstOrNull { it.key == planKey }
                val id = planKey.removePrefix("plan:").toLongOrNull()
                val title = template?.title ?: data.value.plans.firstOrNull { it.id == id }?.name
                val exercises = (template?.exercises ?: id?.let { repo.exercises(it) }.orEmpty()).map(exerciseMemory::apply)
                if (title == null || exercises.isEmpty()) { message("计划已不存在，请重新安排"); return@operation }
                if (exercises.any { it.isBodyweight } && preferences.value.bodyWeightKg == null) {
                    message("请先填写体重，再开始自重训练"); return@operation
                }
                val fresh = WorkoutEngine.start(title, exercises, time(), bodyWeightKg = preferences.value.bodyWeightKg)
                val missedDay = if (choice?.second == true) missedTraining()?.day else null
                store.save(fresh, emptyList())
                if (choice != null || planKey == preferences.value.scheduleFor(today.value)) settings.startedSchedule(date, planKey, missedDay, fresh.key)
                undoSteps.value = emptyList(); active.value = fresh; result.value = null
            } else if (active.value?.bodyWeightKg == null && active.value?.endedAt == null && preferences.value.bodyWeightKg != null) {
                // Older sessions acquire a snapshot for future sets; completed logs remain intact.
                val kg = preferences.value.bodyWeightKg
                val updated = active.value!!.copy(bodyWeightKg = kg)
                val steps = undoSteps.value.map { it.copy(previous = it.previous.copy(bodyWeightKg = kg)) }
                store.save(updated, steps); active.value = updated; undoSteps.value = steps
            }
        }
        ready()
    }

    fun missedTraining(): MissedTraining? {
        if (active.value != null || !data.value.ready) return null
        val prefs = preferences.value
        val plans = data.value.plans
        val available = plans.filter { it.exerciseCount > 0 }.map { "plan:${it.id}" }.toSet() + PresetData.templates.map { it.key }
        val started = data.value.workouts.flatMap { record -> available.filter { planLabel(it, plans) == record.planName }
            .map { record.dateEpochDay to it } }.toSet()
        return ScheduleCatchUp.pending(prefs.scheduleMode, prefs.week, prefs.cycle, prefs.cycleStartEpochDay,
            prefs.scheduleHandledThrough, today.value.toEpochDay(), available, started)
    }

    fun completeSet(cardioSeconds: Int? = null, cardioDistance: Double? = null, cardioReps: Int? = null) = operation {
        sessionLock.withLock {
            val old = active.value ?: return@withLock
            if (old.endedAt != null && old.allComplete) {
                saveSessionLocked()
                return@withLock
            }
            val at = time()
            val next = WorkoutEngine.complete(old, at, cardioSeconds, cardioDistance, cardioReps)
            if (next == old) return@withLock
            saveTransition(old, next, at, "完成本组")
            val finished = next.endedAt != null
            if (finished) saveSessionLocked()
            else cue(if (next.index != old.index) FeedbackCue.EXERCISE else FeedbackCue.SET)
        }
    }

    fun skipRest() = operation {
        sessionLock.withLock {
            val current = active.value ?: return@withLock
            if (current.restUntil == null || current.pausedAt != null || current.pendingCardioReview != null) return@withLock
            val next = WorkoutEngine.skipRest(current, time())
            saveTransition(current, next, time(), "跳过休息"); cue(FeedbackCue.SELECT)
        }
    }
    fun pauseCardio() = operation {
        sessionLock.withLock {
            val current = active.value ?: return@withLock
            if (!current.current.isCardio) return@withLock
            val at = time()
            val synced = CardioEngine.tick(current, at)
            val next = if (synced.pausedAt == null) CardioEngine.pause(synced, at) else CardioEngine.resume(synced, at)
            store.save(next, undoSteps.value); active.value = next; cue(FeedbackCue.SELECT)
        }
    }
    fun completeCardioRound(key: String, index: Int, ordinal: Int) = operation {
        sessionLock.withLock {
            val current = active.value ?: return@withLock
            if (current.key != key || current.index != index || current.currentDone != ordinal) return@withLock
            val next = CardioEngine.completeRound(current, time())
            store.save(next, undoSteps.value); active.value = next; cue(FeedbackCue.SET)
        }
    }
    fun finishCardio(wholeWorkout: Boolean = false) = operation {
        sessionLock.withLock {
            val current = active.value ?: return@withLock
            val at = time()
            val synced = CardioEngine.tick(current, at)
            val next = if (synced.pendingCardioReview != null && wholeWorkout) synced.copy(endedAt = synced.pausedAt ?: at, pausedAt = null)
                else CardioEngine.finishCurrent(synced, at, wholeWorkout)
            store.save(next, undoSteps.value); active.value = next
        }
    }
    fun confirmCardio(seconds: Int, meters: Double, reps: Int) = operation {
        sessionLock.withLock {
            val current = active.value ?: return@withLock
            val next = CardioEngine.confirm(current, seconds, meters, reps, time())
            store.save(next, emptyList()); active.value = next; undoSteps.value = emptyList()
            if (next.endedAt != null) saveSessionLocked() else cue(FeedbackCue.EXERCISE)
        }
    }
    fun adjust(weight: Double = 0.0, reps: Int = 0) = operation {
        sessionLock.withLock {
            val current = active.value ?: return@withLock
            val next = WorkoutEngine.adjust(current, weight, reps)
            saveTransition(current, next, time(), "调整参数"); cue(FeedbackCue.SELECT)
            exerciseMemory.save(listOf(next.exercises[current.index]))
        }
    }
    fun setCurrentWeight(weight: Double) = operation {
        if (!weight.isFinite() || weight !in 0.0..2000.0) return@operation
        sessionLock.withLock {
            val current = active.value ?: return@withLock
            val parameters = current.current.copy(weight = weight,
                setTargets = current.current.setTargets.mapIndexed { i, target ->
                    if (i == current.currentDone) target.copy(weight = weight) else target
                })
            val next = WorkoutEngine.editCurrent(current, parameters, time())
            saveTransition(current, next, time(), "调整重量"); cue(FeedbackCue.SELECT)
            exerciseMemory.save(listOf(next.exercises[current.index]))
        }
    }
    fun adjustRest(seconds: Int) = operation {
        sessionLock.withLock {
            val current = active.value ?: return@withLock
            val at = time()
            val next = WorkoutEngine.adjustRest(current, seconds, at)
            saveTransition(current, next, at, "调整本次休息")
            if (next != current) cue(if (next.restUntil == null) FeedbackCue.REST else FeedbackCue.SELECT)
        }
    }
    private suspend fun saveTransition(old: WorkoutSession, next: WorkoutSession, at: Long, label: String) {
        if (old == next) return
        val step = WorkoutUndoStep(old, at, label)
        val steps = if (step.isUndoable) (undoSteps.value + step).takeLast(64) else undoSteps.value
        store.save(next, steps)
        undoSteps.value = steps
        active.value = next
    }
    fun changeCurrentSets(sets: Int) = operation {
        sessionLock.withLock {
            val current = active.value ?: return@withLock
            val at = time()
            val next = WorkoutEngine.resizeCurrentSets(current, sets, at)
            saveTransition(current, next, at, "修改动作组数")
            exerciseMemory.save(listOf(next.exercises[current.index]))
            cue(FeedbackCue.SELECT)
        }
    }
    fun editCurrentExercise(key: String, index: Int, spec: ExerciseSpec, done: () -> Unit) = operation {
        sessionLock.withLock {
            val current = active.value ?: return@withLock
            if (current.key != key || current.index != index || current.endedAt != null) {
                message("当前动作已变化，请重新打开设置")
                return@withLock
            }
            val at = time()
            val next = WorkoutEngine.editCurrent(current, spec, at)
            saveTransition(current, next, at, "修改动作参数")
            exerciseMemory.save(listOf(next.exercises[current.index]))
            cue(FeedbackCue.SELECT)
            done()
        }
    }
    fun saveCustomExercise(spec: ExerciseSpec, originalName: String? = null, done: () -> Unit) = operation {
        customStore.save(spec, originalName); exerciseMemory.save(listOf(spec)); cue(FeedbackCue.CONFIRM); done()
    }
    fun deleteExercise(spec: ExerciseSpec, done: () -> Unit) = operation {
        customStore.delete(spec.name); cue(FeedbackCue.CONFIRM); done()
    }
    fun importExerciseIcon(uri: android.net.Uri, done: (String) -> Unit) = operation {
        done(customStore.importIcon(uri))
    }
    fun undo() = operation {
        sessionLock.withLock {
            val current = active.value ?: return@withLock
            val step = undoSteps.value.lastOrNull() ?: return@withLock
            if (step.previous.key != current.key) return@withLock
            val restored = step.restoreProgress(current, time())
            val remaining = undoSteps.value.dropLast(1)
            store.save(restored, remaining)
            active.value = restored; undoSteps.value = remaining
            cue(FeedbackCue.SELECT)
            message("已撤销：${step.label}")
        }
    }
    fun finish(done: () -> Unit = {}, returnToMenu: Boolean = false) = operation {
        sessionLock.withLock {
            val current = active.value ?: return@withLock
            val next = WorkoutEngine.end(current, time())
            store.save(next, emptyList()); active.value = next; undoSteps.value = emptyList()
        }
        if (active.value?.completed?.isEmpty() == true) {
            store.save(null); active.value = null; undoSteps.value = emptyList(); done()
        } else {
            saveSession()
            if (returnToMenu) { result.value = null; done() }
        }
    }
    private suspend fun saveSession() {
        sessionLock.withLock { saveSessionLocked() }
    }
    /** Caller owns sessionLock. Checkpoint + unique sessionKey keep final-set retries idempotent. */
    private suspend fun saveSessionLocked() {
            val current = active.value ?: return
            require(current.endedAt != null)
            val seconds = current.durationSeconds(time())
            val record = WorkoutRecord(planName = current.title,
                dateEpochDay = Instant.ofEpochMilli(current.startedAt).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay(),
                totalSets = current.completed.size,
                summary = WorkoutLogEditing.summary(current.completed.map(WorkoutLogEditing::fromCompleted)),
                durationSeconds = seconds, createdAt = current.endedAt, sessionKey = current.key, volumeKg = current.volume,
                cardioSeconds = current.cardioSeconds, cardioDistanceMeters = current.cardioDistance,
                cardioRounds = current.completed.count { it.isCardio })
            repo.saveWorkout(record)
            if (current.allComplete && CardioEngine.goalsMet(current)) withContext(Dispatchers.IO) {
                settings.completedSchedule(record.dateEpochDay, current.key)
            }
            store.save(null)
            result.value = SessionSummary(current.title, current.completed.size, current.completed.map { it.exerciseIndex }.distinct().size,
                seconds, current.volume, current.allComplete && CardioEngine.goalsMet(current), current.cardioSeconds, current.cardioDistance, current.completed.count { it.isCardio },
                current.completed.toList(), record.dateEpochDay)
            active.value = null
            undoSteps.value = emptyList()
            cue(FeedbackCue.FINISH)
    }
    fun discard(done: () -> Unit) = operation {
        sessionLock.withLock { store.save(null); active.value = null; result.value = null; undoSteps.value = emptyList() }
        done()
    }
    fun dismissSummary() { result.value = null }
    suspend fun todayPlanCompleted(key: String, date: LocalDate): Boolean {
        val prefs = preferences.value
        if (prefs.scheduleCompletedDay == date.toEpochDay() && prefs.scheduleCompletedKey == key) return true
        val preview = trainingPreview(key) ?: return false
        // Existing strength records have no completion flag; require all prescribed sets.
        // Cardio completion additionally depends on confirmed goals, so only use its new marker.
        if (preview.exercises.any { it.isCardio }) return false
        val expected = preview.exercises.sumOf { it.sets }
        return data.value.workouts.any { it.dateEpochDay == date.toEpochDay() && it.planName == preview.title && it.totalSets >= expected }
    }
    fun saveFood(record: FoodRecord, done: () -> Unit) = operation { repo.saveFood(record); cue(FeedbackCue.CONFIRM); done() }
    fun saveBodyWeight(kg: Double, done: () -> Unit) = operation {
        withContext(Dispatchers.IO) { settings.saveBodyWeight(kg) }
        sessionLock.withLock {
            val current = active.value
            if (current != null && current.bodyWeightKg == null && current.endedAt == null) {
                val updated = current.copy(bodyWeightKg = kg)
                val steps = undoSteps.value.map { it.copy(previous = it.previous.copy(bodyWeightKg = kg)) }
                store.save(updated, steps); active.value = updated; undoSteps.value = steps
            }
        }
        cue(FeedbackCue.CONFIRM); done()
    }
    fun deleteFood(record: FoodRecord) = operation { repo.deleteFood(record) }
    fun deleteWorkout(record: WorkoutRecord) = operation { repo.deleteWorkout(record) }
    fun updateWorkout(record: WorkoutRecord, name: String, date: LocalDate, durationSeconds: Int,
        sets: List<LoggedSet>?, done: () -> Unit) = operation {
        val updated = record.copy(planName = name.trim(), dateEpochDay = date.toEpochDay(), durationSeconds = durationSeconds,
            totalSets = sets?.size ?: record.totalSets,
            summary = sets?.let(WorkoutLogEditing::summary) ?: record.summary,
            volumeKg = sets?.let(WorkoutLogEditing::volume) ?: record.volumeKg,
            cardioSeconds = sets?.filter { it.isCardio }?.sumOf { it.durationSeconds } ?: record.cardioSeconds,
            cardioDistanceMeters = sets?.filter { it.isCardio }?.sumOf { it.distanceMeters } ?: record.cardioDistanceMeters,
            cardioRounds = sets?.count { it.isCardio } ?: record.cardioRounds)
        repo.updateWorkout(updated)
        cue(FeedbackCue.CONFIRM)
        done()
    }
    fun savePlan(name: String, exercises: List<ExerciseSpec>, planId: Long? = null, done: (Long) -> Unit) = operation {
        val id = repo.savePlan(name, exercises, planId)
        exerciseMemory.save(exercises)
        cue(FeedbackCue.CONFIRM); done(id)
    }
    fun saveSchedule(mode: String, week: List<String>, cycle: List<String>, startEpochDay: Long, done: () -> Unit) = operation {
        withContext(Dispatchers.IO) { settings.saveSchedule(mode, week, cycle, startEpochDay) }
        cue(FeedbackCue.CONFIRM); done()
    }
    fun deletePlan(plan: WorkoutPlan) = operation { repo.deletePlan(plan.id); settings.removePlan(plan.id) }
    fun saveSleep(day: LocalDate, hours: Double) = operation { repo.saveSleep(day.toEpochDay(), hours); cue(FeedbackCue.CONFIRM) }

    companion object {
        fun factory(context: Context) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(context.applicationContext) as T
        }
    }
}
