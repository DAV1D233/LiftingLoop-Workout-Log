package com.fitlog.app.ui.workout

import com.fitlog.app.data.WorkoutRecord
import com.fitlog.app.domain.CompletedSet
import com.fitlog.app.domain.WorkoutLogEditing
import com.fitlog.app.domain.WorkoutRecordDraft
import com.fitlog.app.ui.SessionSummary

/** Reopen the same achievement details from the saved record after app restart. */
internal fun WorkoutRecord.completedSummary(): SessionSummary? {
    val sets = WorkoutLogEditing.parse(summary, totalSets) ?: return null
    val groups = WorkoutRecordDraft.groups(WorkoutRecordDraft.fromSets(sets))
    val completed = groups.flatMapIndexed { action, rows -> rows.mapIndexed { ordinal, row ->
        val set = sets[row]
        CompletedSet(action, set.name, ordinal + 1, set.weight, set.reps, createdAt,
            set.isCardio, set.durationSeconds, set.distanceMeters,
            if (set.reps > 0 && set.isCardio) "计次" else "计时", set.stroke, set.poolLengthMeters)
    } }
    return SessionSummary(planName, totalSets, groups.size, durationSeconds,
        volumeKg ?: WorkoutLogEditing.volume(sets), true, cardioSeconds, cardioDistanceMeters,
        cardioRounds, completed, dateEpochDay)
}
