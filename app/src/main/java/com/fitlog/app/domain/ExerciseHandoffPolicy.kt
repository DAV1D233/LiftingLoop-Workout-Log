package com.fitlog.app.domain

/** Only finishing the last set/interval advances with an introduction. Restore, undo and edits do not. */
object ExerciseHandoffPolicy {
    fun shouldShow(previous: WorkoutSession, next: WorkoutSession): Boolean {
        if (previous.key != next.key || previous.endedAt != null || next.endedAt != null ||
            next.index != previous.index + 1 || next.completed.size != previous.completed.size + 1) return false
        val last = next.completed.lastOrNull() ?: return false
        return last.exerciseIndex == previous.index && last.ordinal == previous.current.sets &&
            next.completed.dropLast(1) == previous.completed
    }
}
