package com.fitlog.app.domain

/** Timestamp-based phases: ticks never accumulate elapsed time or depend on a visible screen. */
object CardioEngine {
    fun pause(s: WorkoutSession, now: Long): WorkoutSession =
        if (s.pausedAt != null || s.endedAt != null || s.pendingCardioReview != null) s else s.copy(pausedAt = now)

    fun resume(s: WorkoutSession, now: Long): WorkoutSession {
        val paused = s.pausedAt ?: return s
        if (s.endedAt != null || s.pendingCardioReview != null) return s
        val delta = (now - paused).coerceAtLeast(0)
        return s.copy(pausedAt = null, pausedMillis = s.pausedMillis + delta,
            segmentPausedMillis = s.segmentPausedMillis + if (s.restUntil == null) delta else 0,
            restUntil = s.restUntil?.plus(delta), restStartedAt = s.restStartedAt?.plus(delta))
    }

    private fun review(next: WorkoutSession, index: Int, at: Long): WorkoutSession = next.copy(
        pendingCardioReview = index, restUntil = null, restStartedAt = null,
        exercises = next.exercises.mapIndexed { i, e -> if (i == index) e.copy(cardioTarget = e.cardioTarget.copy(
            training = if (e.isInterval) "interval" else "continuous")) else e },
        pausedAt = if (next.endedAt == null) at else null, segmentStartedAt = at, segmentPausedMillis = 0)

    /** One pass can catch up missed work/rest boundaries after screen-off or process recovery. */
    fun tick(initial: WorkoutSession, now: Long): WorkoutSession {
        var s = initial
        repeat(200) {
            if (s.pausedAt != null || s.endedAt != null || s.pendingCardioReview != null) return s
            val restEnd = s.restUntil
            if (restEnd != null) {
                if (now < restEnd) return s
                s = WorkoutEngine.skipRest(s, restEnd)
            }
            val spec = s.current
            if (!spec.isInterval || spec.cardioTarget.mode != "计时") return s
            val seconds = spec.cardioTarget.durationSeconds.coerceAtLeast(1)
            val boundary = s.segmentStartedAt + s.segmentPausedMillis + seconds * 1000L
            if (now < boundary) return s
            val index = s.index
            s = WorkoutEngine.complete(s, boundary, seconds, spec.cardioTarget.distanceMeters)
            if (s.index != index || s.endedAt != null) return review(s, index, boundary)
        }
        return s
    }

    fun completeRound(s: WorkoutSession, now: Long): WorkoutSession {
        if (!s.current.isCardio || s.pausedAt != null || s.pendingCardioReview != null || s.restSeconds(now) > 0 || s.endedAt != null) return s
        val next = WorkoutEngine.complete(s, now, s.segmentSeconds(now), s.current.cardioTarget.distanceMeters)
        return if (next.index != s.index || next.endedAt != null) review(next, s.index, now) else next
    }

    fun finishCurrent(s: WorkoutSession, now: Long, wholeWorkout: Boolean = false): WorkoutSession {
        if (!s.current.isCardio || s.pendingCardioReview != null || s.endedAt != null) return s
        val at = s.pausedAt ?: now
        var base = s.copy(pausedAt = null, exercises = s.exercises.mapIndexed { i, e ->
            if (i == s.index) e.copy(cardioTarget = e.cardioTarget.copy(training = if (e.isInterval) "interval" else "continuous")) else e
        })
        val next = if (s.restUntil != null && s.currentDone > 0) {
            WorkoutEngine.resizeCurrentSets(base, s.currentDone, at)
        } else {
            base = base.copy(restUntil = null, restStartedAt = null,
                exercises = base.exercises.mapIndexed { i, e -> if (i == base.index) e.copy(sets = s.currentDone + 1) else e })
            WorkoutEngine.complete(base, at, s.segmentSeconds(now), s.current.cardioTarget.distanceMeters)
        }
        val expectedRounds = s.currentDone + if (s.restUntil != null && s.currentDone > 0) 0 else 1
        val marked = next.copy(cardioStoppedEarly = s.cardioStoppedEarly || expectedRounds < s.current.sets)
        return review(if (wholeWorkout) marked.copy(endedAt = at) else marked, s.index, at)
    }

    fun goalsMet(s: WorkoutSession): Boolean = !s.cardioStoppedEarly && s.exercises.withIndex().all { (index, e) ->
        if (!e.isCardio || e.cardioTarget.training == "auto") true else {
            val logs = s.completed.filter { it.exerciseIndex == index }
            when (e.cardioTarget.mode) {
                "计时" -> logs.sumOf { it.durationSeconds }.toLong() >= e.cardioTarget.durationSeconds.toLong() * e.sets
                "距离" -> logs.sumOf { it.distanceMeters } + .000001 >= e.cardioTarget.distanceMeters * e.sets
                "计次" -> logs.sumOf { it.reps } >= e.reps * e.sets
                else -> logs.isNotEmpty()
            }
        }
    }

    fun confirm(s: WorkoutSession, seconds: Int, meters: Double, reps: Int, now: Long): WorkoutSession {
        val index = s.pendingCardioReview ?: return s
        val logs = s.completed.filter { it.exerciseIndex == index }
        require(logs.isNotEmpty() && seconds in 0..(86400 * logs.size) && meters.isFinite() && meters in 0.0..(1000000.0 * logs.size))
        require(reps in 0..(100 * logs.size))
        val oldSeconds = logs.sumOf { it.durationSeconds }
        val oldMeters = logs.sumOf { it.distanceMeters }
        var usedSeconds = 0; var usedReps = 0; var usedMeters = 0.0; var position = 0
        val updated = s.completed.map { log ->
            if (log.exerciseIndex != index) log else {
                val last = position++ == logs.lastIndex
                val proposedTime = if (last) seconds - usedSeconds else
                    if (oldSeconds > 0) (seconds.toLong() * log.durationSeconds / oldSeconds).toInt() else seconds / logs.size
                val time = proposedTime.coerceIn((seconds - usedSeconds - 86400 * (logs.size - position)).coerceAtLeast(0),
                    minOf(86400, seconds - usedSeconds))
                val proposedDistance = if (last) meters - usedMeters else
                    if (oldMeters > 0) meters * log.distanceMeters / oldMeters else meters / logs.size
                val distance = proposedDistance.coerceIn((meters - usedMeters - 1000000.0 * (logs.size - position)).coerceAtLeast(0.0),
                    minOf(1000000.0, (meters - usedMeters).coerceAtLeast(0.0)))
                val count = (reps - usedReps) / (logs.size - position + 1)
                usedSeconds += time; usedMeters += distance; usedReps += count
                log.copy(durationSeconds = time, distanceMeters = distance.coerceAtLeast(0.0),
                    reps = if (log.cardioMode == "计次") count else 0)
            }
        }
        val paused = s.pausedAt?.let { (now - it).coerceAtLeast(0) } ?: 0
        return s.copy(completed = updated, pendingCardioReview = null, pausedAt = null,
            pausedMillis = s.pausedMillis + paused, segmentStartedAt = now, segmentPausedMillis = 0)
    }
}
