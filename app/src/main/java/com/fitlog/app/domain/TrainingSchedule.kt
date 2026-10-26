package com.fitlog.app.domain

import java.time.LocalDate
import java.time.YearMonth

data class SchedulePreviewDay(val date: LocalDate, val key: String, val position: Int)

object TrainingSchedule {
    fun month(mode: String, week: List<String>, cycle: List<String>, startEpochDay: Long,
        month: YearMonth): List<SchedulePreviewDay> = dates(mode, week, cycle, startEpochDay, month.atDay(1), month.lengthOfMonth())

    fun week(mode: String, week: List<String>, cycle: List<String>, startEpochDay: Long,
        date: LocalDate): List<SchedulePreviewDay> = dates(mode, week, cycle, startEpochDay,
            date.minusDays((date.dayOfWeek.value - 1).toLong()), 7)

    private fun dates(mode: String, week: List<String>, cycle: List<String>, startEpochDay: Long,
        first: LocalDate, count: Int): List<SchedulePreviewDay> = List(count) { offset ->
        val date = first.plusDays(offset.toLong())
        SchedulePreviewDay(date, resolve(mode, week, cycle, startEpochDay, date.toEpochDay(), date.dayOfWeek.value),
            index(mode, cycle.size, startEpochDay, date.toEpochDay(), date.dayOfWeek.value))
    }

    fun index(mode: String, cycleSize: Int, startEpochDay: Long, epochDay: Long, weekday: Int): Int {
        if (mode != "cycle") return (weekday - 1).coerceIn(0, 6)
        if (cycleSize <= 0 || epochDay < startEpochDay) return -1
        return Math.floorMod(epochDay - startEpochDay, cycleSize.toLong()).toInt()
    }

    fun resolve(mode: String, week: List<String>, cycle: List<String>, startEpochDay: Long,
        epochDay: Long, weekday: Int): String {
        val days = if (mode == "cycle") cycle else week
        if (days.isEmpty() || days.all { it.isBlank() }) return "unset"
        val position = index(mode, cycle.size, startEpochDay, epochDay, weekday)
        if (position < 0) return "unset"
        return days.getOrElse(position) { "rest" }.ifBlank { "rest" }
    }
}
