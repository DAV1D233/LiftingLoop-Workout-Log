package com.fitlog.app.domain

import java.time.LocalDate

data class MissedTraining(val day: Long, val key: String)

object ScheduleCatchUp {
    fun pending(mode: String, week: List<String>, cycle: List<String>, start: Long,
        handledThrough: Long, today: Long, available: Set<String>, started: Set<Pair<Long, String>>): MissedTraining? {
        fun key(day: Long) = TrainingSchedule.resolve(mode, week, cycle, start, day,
            LocalDate.ofEpochDay(day).dayOfWeek.value)
        if (key(today) !in available || started.any { it.first == today }) return null
        // Only track arrangements since this feature/configuration became active.
        if (handledThrough >= today - 1) return null
        for (day in maxOf(handledThrough + 1, today - 3660)..today - 1) {
            val plan = key(day)
            if (plan in available && (day to plan) !in started) return MissedTraining(day, plan)
        }
        return null
    }
    fun shiftedStart(mode: String, start: Long, missedDay: Long, today: Long): Long =
        if (mode == "cycle") start + (today - missedDay).coerceAtLeast(0) else start

    fun route(key: String, day: Long, resume: Boolean) = "scheduled|$day|${if (resume) "resume" else "today"}|$key"
    fun key(route: String) = if (route.startsWith("scheduled|")) route.split('|', limit = 4).getOrElse(3) { "" } else route
    fun choice(route: String): Pair<Long, Boolean>? {
        if (!route.startsWith("scheduled|")) return null
        val parts = route.split('|', limit = 4)
        if (parts.size != 4 || parts[2] !in listOf("resume", "today")) return null
        return parts[1].toLongOrNull()?.let { it to (parts[2] == "resume") }
    }
}
