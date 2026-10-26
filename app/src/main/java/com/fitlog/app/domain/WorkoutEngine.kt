package com.fitlog.app.domain

import java.util.UUID

data class SetTarget(val weight: Double, val reps: Int, val restSeconds: Int) {
    fun normalized() = copy(weight = if (weight.isFinite()) weight.coerceIn(0.0, 2000.0) else 0.0,
        reps = reps.coerceIn(1, 100), restSeconds = restSeconds.coerceIn(0, 600))
}

data class ExerciseSpec @JvmOverloads constructor(
    val name: String,
    val sets: Int = 3,
    val reps: Int = 10,
    val weight: Double = 0.0,
    val restSeconds: Int = 60,
    val isBodyweight: Boolean = name.trim() in BodyWeight.exercises,
    val muscleGroup: String = "",
    val iconPath: String = "",
    val videoUrl: String = "",
    val isCardio: Boolean = false,
    val setTargets: List<SetTarget> = emptyList(),
    val cardioTarget: CardioTarget = CardioTarget()
) {
    val muscleGroups get() = MuscleGroups.parse(muscleGroup).ifEmpty { MuscleGroups.defaults(name) }
    fun targetAt(index: Int) = setTargets.getOrNull(index) ?: setTargets.lastOrNull() ?: SetTarget(weight, reps, restSeconds)
    val typeLabel get() = if (isCardio) "有氧" else if (isBodyweight) "自重" else "器械"
    fun normalized() = copy(name = name.trim(), sets = sets.coerceIn(1, 20),
        reps = reps.coerceIn(1, 100), weight = if (weight.isFinite()) weight.coerceIn(0.0, 2000.0) else 0.0,
        restSeconds = restSeconds.coerceIn(0, 600), muscleGroup = MuscleGroups.parse(muscleGroup).joinToString("、").take(200),
        videoUrl = videoUrl.trim(), isBodyweight = isBodyweight && !isCardio,
        setTargets = if (isCardio || setTargets.isEmpty()) emptyList() else List(sets.coerceIn(1, 20)) { targetAt(it).normalized() },
        cardioTarget = cardioTarget.normalized())
}

data class CompletedSet @JvmOverloads constructor(val exerciseIndex: Int, val name: String, val ordinal: Int,
    val weight: Double, val reps: Int, val finishedAt: Long,
    val isCardio: Boolean = false, val durationSeconds: Int = 0, val distanceMeters: Double = 0.0,
    val cardioMode: String = "计时", val stroke: String = "", val poolLengthMeters: Double = 0.0)

data class WorkoutSession(
    val key: String,
    val title: String,
    val startedAt: Long,
    val exercises: List<ExerciseSpec>,
    val index: Int = 0,
    val completed: List<CompletedSet> = emptyList(),
    val restUntil: Long? = null,
    val endedAt: Long? = null,
    val bodyWeightKg: Double? = null,
    val restStartedAt: Long? = null,
    val segmentStartedAt: Long = startedAt,
    val pausedAt: Long? = null,
    val pausedMillis: Long = 0,
    val segmentPausedMillis: Long = 0,
    val pendingCardioReview: Int? = null,
    val cardioStoppedEarly: Boolean = false
) {
    val current get() = exercises[index]
    val currentDone get() = completed.count { it.exerciseIndex == index }
    val totalSets get() = exercises.sumOf { it.sets }
    val progress get() = completed.size.toFloat() / totalSets.coerceAtLeast(1)
    val allComplete get() = completed.size == totalSets
    val volume get() = completed.filterNot { it.isCardio }.sumOf { it.weight * it.reps }
    val cardioSeconds get() = completed.filter { it.isCardio }.sumOf { it.durationSeconds }
    val cardioDistance get() = completed.filter { it.isCardio }.sumOf { it.distanceMeters }
    fun segmentSeconds(now: Long) = (((pausedAt ?: endedAt ?: now) - maxOf(segmentStartedAt, restUntil ?: segmentStartedAt) - segmentPausedMillis).coerceAtLeast(0) / 1000).coerceAtMost(86400).toInt()
    val currentTarget get() = current.targetAt(currentDone.coerceAtMost(current.sets - 1))
    val currentWeight get() = BodyWeight.effectiveWeight(current.copy(weight = currentTarget.weight), bodyWeightKg)
    fun durationSeconds(now: Long) = (((pausedAt ?: endedAt ?: now) - startedAt - pausedMillis).coerceAtLeast(0) / 1000).toInt()
    fun restSeconds(now: Long) = restUntil?.let { ((it - (pausedAt ?: now)).coerceAtLeast(0) + 999) / 1000 }?.toInt() ?: 0
    fun restProgress(now: Long): Float {
        val end = restUntil ?: return 0f
        val start = restStartedAt ?: completed.lastOrNull()?.finishedAt ?: return 0f
        if (end <= start) return 1f
        return (((pausedAt ?: now) - start).toDouble() / (end - start)).toFloat().coerceIn(0f, 1f)
    }
}

/** Rest resumes from the remaining time at the operation being undone. */
data class WorkoutUndoStep(val previous: WorkoutSession, val capturedAt: Long, val label: String) {
    val isTrainingAdvance: Boolean get() = label == "完成本组" || label == "跳过休息"
    val isUndoable: Boolean get() = isTrainingAdvance || label == "调整本次休息"
    fun restore(now: Long) = previous.copy(restUntil = previous.restUntil?.let {
        (it - capturedAt).coerceAtLeast(0).takeIf { remaining -> remaining > 0 }?.let { remaining -> now + remaining }
    }, restStartedAt = if (previous.restSeconds(capturedAt) > 0)
        (previous.restStartedAt ?: previous.completed.lastOrNull()?.finishedAt)?.plus(now - capturedAt) else null)

    /** Undo progress while retaining the user's latest exercise configuration. */
    fun restoreProgress(current: WorkoutSession, now: Long): WorkoutSession {
        if (current.key != previous.key) return current
        val restored = restore(now)
        return restored.copy(bodyWeightKg = current.bodyWeightKg, segmentStartedAt = now,
            exercises = current.exercises.mapIndexed { i, spec ->
            val logged = restored.completed.count { it.exerciseIndex == i }
            val minimum = logged + if (i == restored.index && restored.endedAt == null) 1 else 0
            spec.copy(sets = spec.sets.coerceAtLeast(minimum)).normalized()
        })
    }
}

/** Pure state transitions. Android lifecycle and storage never participate in these rules. */
object WorkoutEngine {
    @JvmOverloads
    fun start(title: String, exercises: List<ExerciseSpec>, now: Long, key: String = UUID.randomUUID().toString(), bodyWeightKg: Double? = null): WorkoutSession {
        require(title.isNotBlank())
        val clean = exercises.map { it.normalized() }.filter { it.name.isNotBlank() }
        require(clean.isNotEmpty())
        require(bodyWeightKg == null || BodyWeight.valid(bodyWeightKg))
        return WorkoutSession(key, title.trim(), now, clean, bodyWeightKg = bodyWeightKg)
    }

    @JvmOverloads
    fun complete(session: WorkoutSession, now: Long, cardioSeconds: Int? = null, cardioDistance: Double? = null,
        cardioReps: Int? = null): WorkoutSession {
        if (session.endedAt != null || session.pausedAt != null || session.pendingCardioReview != null || session.restSeconds(now) > 0) return session
        val spec = session.current
        if (session.currentDone >= spec.sets) return session
        val target = session.currentTarget
        val log = CompletedSet(session.index, spec.name, session.currentDone + 1,
            if (spec.isCardio) 0.0 else session.currentWeight, cardioReps ?: target.reps, now,
            spec.isCardio, if (spec.isCardio) (cardioSeconds ?: session.segmentSeconds(now)).coerceIn(0, 86400) else 0,
            if (spec.isCardio) (cardioDistance ?: 0.0).takeIf { it.isFinite() }?.coerceIn(0.0, 1000000.0) ?: 0.0 else 0.0,
            spec.cardioTarget.mode, spec.cardioTarget.stroke, spec.cardioTarget.poolLengthMeters)
        val done = session.completed + log
        val advance = log.ordinal == spec.sets
        val finish = advance && session.index == session.exercises.lastIndex
        return session.copy(completed = done, index = if (advance && !finish) session.index + 1 else session.index,
            restUntil = if (!finish && target.restSeconds > 0) now + target.restSeconds * 1000L else null,
            restStartedAt = if (!finish && target.restSeconds > 0) now else null,
            endedAt = if (finish) now else null, segmentStartedAt = now, segmentPausedMillis = 0)
    }

    @JvmOverloads
    fun skipRest(session: WorkoutSession, now: Long = session.restUntil ?: session.segmentStartedAt) = session.copy(restUntil = null, restStartedAt = null,
        segmentStartedAt = now, segmentPausedMillis = 0)
    fun adjustRest(session: WorkoutSession, deltaSeconds: Int, now: Long): WorkoutSession {
        val end = session.restUntil ?: return session
        if (session.endedAt != null || end <= now) return session
        val remaining = (end - now + deltaSeconds.toLong() * 1000).coerceIn(0L, 600000L)
        if (remaining == 0L) return skipRest(session, now)
        return session.copy(restUntil = now + remaining,
            restStartedAt = session.restStartedAt ?: session.completed.lastOrNull()?.finishedAt ?: now)
    }
    fun resizeCurrentSets(session: WorkoutSession, requested: Int, now: Long): WorkoutSession {
        if (session.endedAt != null) return session
        val target = requested.coerceIn(session.currentDone.coerceAtLeast(1), 20)
        if (target == session.current.sets) return session
        val updated = session.copy(exercises = session.exercises.mapIndexed { i, spec ->
            if (i == session.index) spec.copy(sets = target).normalized() else spec
        })
        if (target > session.currentDone) return updated
        return if (session.index < session.exercises.lastIndex) updated.copy(index = session.index + 1)
        else updated.copy(endedAt = now, restUntil = null, restStartedAt = null)
    }
    fun end(session: WorkoutSession, now: Long) = session.copy(restUntil = null, restStartedAt = null, endedAt = session.endedAt ?: now)
    fun editCurrent(session: WorkoutSession, requested: ExerciseSpec, now: Long): WorkoutSession {
        if (session.endedAt != null) return session
        val parameters = requested.normalized()
        val edited = session.copy(exercises = session.exercises.mapIndexed { i, item ->
            if (i == session.index) item.copy(reps = parameters.reps, weight = parameters.weight,
                cardioTarget = parameters.cardioTarget, muscleGroup = parameters.muscleGroup,
                restSeconds = parameters.restSeconds, setTargets = if (parameters.setTargets.isEmpty()) emptyList()
                else List(parameters.sets.coerceAtLeast(session.currentDone.coerceAtLeast(1))) { j ->
                    if (j < session.currentDone) item.targetAt(j) else parameters.targetAt(j)
                }) else item
        })
        return resizeCurrentSets(edited, parameters.sets, now)
    }
    fun adjust(session: WorkoutSession, weightDelta: Double = 0.0, repsDelta: Int = 0): WorkoutSession {
        if (session.endedAt != null) return session
        val current = session.current
        val spec = if (current.setTargets.isEmpty()) current.copy(weight = current.weight + weightDelta, reps = current.reps + repsDelta).normalized()
        else current.copy(setTargets = current.setTargets.mapIndexed { i, target ->
            if (i == session.currentDone) target.copy(weight = target.weight + weightDelta, reps = target.reps + repsDelta).normalized() else target
        })
        return session.copy(exercises = session.exercises.mapIndexed { i, item -> if (i == session.index) spec else item })
    }
}

fun number(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
