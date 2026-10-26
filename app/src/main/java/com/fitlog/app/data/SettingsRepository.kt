package com.fitlog.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import com.fitlog.app.domain.TrainingSchedule

data class Preferences(val haptics: Boolean = true, val sounds: Boolean = true,
    val hintSeen: Boolean = false, val week: List<String> = List(7) { "" },
    val scheduleMode: String = "week", val cycle: List<String> = emptyList(),
    val cycleStartEpochDay: Long = LocalDate.now().toEpochDay(),
    val bodyWeightKg: Double? = null,
    val reduceMotion: Boolean = false, val reduceTransparency: Boolean = false,
    val livelyMotion: Boolean = true,
    val scheduleHandledThrough: Long = LocalDate.now().toEpochDay() - 1,
    val scheduleOverrideDay: Long = Long.MIN_VALUE, val scheduleOverrideKey: String = "",
    val scheduleCompletedDay: Long = Long.MIN_VALUE, val scheduleCompletedKey: String = "") {
    fun scheduleFor(date: LocalDate): String = if (date.toEpochDay() == scheduleOverrideDay && scheduleOverrideKey.isNotBlank()) scheduleOverrideKey else TrainingSchedule.resolve(scheduleMode, week, cycle,
        cycleStartEpochDay, date.toEpochDay(), date.dayOfWeek.value)
}

class SettingsRepository(context: Context) {
    private val store = context.getSharedPreferences("fitlog_settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(Preferences(
        store.getBoolean("haptic_enabled", true), store.getBoolean("sound_enabled", true),
        store.getBoolean("seen_long_press_hint", false),
        store.getString("weekly_plan", "")!!.split(",").let { saved -> List(7) { saved.getOrElse(it) { "" } } },
        store.getString("schedule_mode", "week") ?: "week",
        store.getString("cycle_plan", "")!!.split(",").filter { it.isNotBlank() },
        store.getLong("cycle_start_epoch_day", LocalDate.now().toEpochDay()),
        store.getString("body_weight_kg", null)?.toDoubleOrNull()?.takeIf { com.fitlog.app.domain.BodyWeight.valid(it) },
        store.getBoolean("reduce_motion", false), store.getBoolean("reduce_transparency", false),
        store.getBoolean("lively_motion", true),
        store.getLong("schedule_handled_through", LocalDate.now().toEpochDay() - 1),
        store.getLong("schedule_override_day", Long.MIN_VALUE), store.getString("schedule_override_key", "") ?: "",
        store.getLong("schedule_completed_day", Long.MIN_VALUE), store.getString("schedule_completed_key", "") ?: ""
    ))
    val values = state.asStateFlow()
    init {
        if (!store.contains("schedule_handled_through")) store.edit()
            .putLong("schedule_handled_through", state.value.scheduleHandledThrough).apply()
    }
    @Synchronized fun startedSchedule(today: Long, key: String, missedDay: Long? = null, sessionKey: String = "") {
        val old = state.value
        val start = missedDay?.let { com.fitlog.app.domain.ScheduleCatchUp.shiftedStart(old.scheduleMode, old.cycleStartEpochDay, it, today) } ?: old.cycleStartEpochDay
        check(store.edit().putLong("schedule_handled_through", today)
            .putLong("cycle_start_epoch_day", start).putLong("schedule_override_day", today)
            .putString("schedule_override_key", key).putString("schedule_session_key", sessionKey).commit())
        state.value = old.copy(cycleStartEpochDay = start, scheduleHandledThrough = today,
            scheduleOverrideDay = today, scheduleOverrideKey = key)
    }
    @Synchronized fun completedSchedule(day: Long, sessionKey: String) {
        val current = state.value
        if (sessionKey.isBlank() || store.getString("schedule_session_key", "") != sessionKey || current.scheduleOverrideDay != day) return
        check(store.edit().putLong("schedule_completed_day", day).putString("schedule_completed_key", current.scheduleOverrideKey)
            .putString("schedule_completed_session_key", sessionKey).commit())
        state.value = current.copy(scheduleCompletedDay = day, scheduleCompletedKey = current.scheduleOverrideKey)
    }
    @Synchronized fun completedScheduleSession(day: Long, key: String): String? =
        store.getString("schedule_completed_session_key", null)?.takeIf {
            state.value.scheduleCompletedDay == day && state.value.scheduleCompletedKey == key && it.isNotBlank()
        }
    fun appearance(reduceMotion: Boolean = state.value.reduceMotion, reduceTransparency: Boolean = state.value.reduceTransparency,
        livelyMotion: Boolean = state.value.livelyMotion) {
        state.value = state.value.copy(reduceMotion = reduceMotion, reduceTransparency = reduceTransparency, livelyMotion = livelyMotion)
        store.edit().putBoolean("reduce_motion", reduceMotion).putBoolean("reduce_transparency", reduceTransparency).putBoolean("lively_motion", livelyMotion).apply()
    }
    @Synchronized fun saveBodyWeight(kg: Double) {
        require(com.fitlog.app.domain.BodyWeight.valid(kg))
        check(store.edit().putString("body_weight_kg", kg.toString())
            .putLong("body_weight_updated_at", System.currentTimeMillis()).commit()) { "体重保存失败" }
        state.value = state.value.copy(bodyWeightKg = kg)
    }
    @Synchronized fun importBodyWeight(kg: Double, measuredAt: Long): Boolean {
        if (!com.fitlog.app.domain.WeightSync.shouldImport(kg, measuredAt,
                store.getLong("body_weight_updated_at", 0), System.currentTimeMillis())) return false
        check(store.edit().putString("body_weight_kg", kg.toString()).putLong("body_weight_updated_at", measuredAt).commit()) { "体重保存失败" }
        state.value = state.value.copy(bodyWeightKg = kg)
        return true
    }
    fun feedback(haptics: Boolean = state.value.haptics, sounds: Boolean = state.value.sounds) {
        state.value = state.value.copy(haptics = haptics, sounds = sounds)
        store.edit().putBoolean("haptic_enabled", haptics).putBoolean("sound_enabled", sounds).apply()
    }
    fun dismissHint() {
        state.value = state.value.copy(hintSeen = true)
        store.edit().putBoolean("seen_long_press_hint", true).apply()
    }
    fun schedule(day: Int, value: String) {
        require(day in 0..6)
        val week = state.value.week.mapIndexed { i, old -> if (i == day) value else old }
        saveSchedule("week", week, state.value.cycle, state.value.cycleStartEpochDay)
    }
    fun saveSchedule(mode: String, week: List<String>, cycle: List<String>, startEpochDay: Long) {
        require(mode == "week" || mode == "cycle")
        require(week.size == 7)
        require(mode != "cycle" || cycle.isNotEmpty())
        require(cycle.all { it.isNotBlank() && !it.contains(',') })
        val success = store.edit().putString("schedule_mode", mode)
            .putString("weekly_plan", week.joinToString(","))
            .putString("cycle_plan", cycle.joinToString(","))
            .putLong("schedule_handled_through", LocalDate.now().toEpochDay() - 1)
            .remove("schedule_override_day").remove("schedule_override_key")
            .putLong("cycle_start_epoch_day", startEpochDay).commit()
        check(success) { "训练周期保存失败" }
        state.value = state.value.copy(scheduleMode = mode, week = week.toList(), cycle = cycle.toList(),
            cycleStartEpochDay = startEpochDay, scheduleHandledThrough = LocalDate.now().toEpochDay() - 1,
            scheduleOverrideDay = Long.MIN_VALUE, scheduleOverrideKey = "")
    }
    fun removePlan(id: Long) {
        val week = state.value.week.map { if (it == "plan:$id") "rest" else it }
        val cycle = state.value.cycle.map { if (it == "plan:$id") "rest" else it }
        saveSchedule(state.value.scheduleMode, week, cycle, state.value.cycleStartEpochDay)
    }
    fun today(date: LocalDate = LocalDate.now()): String =
        state.value.scheduleFor(date)
}

fun planLabel(value: String, plans: List<WorkoutPlan> = emptyList()): String = when (value) {
    "", "unset" -> "未安排"
    "rest" -> "休息"
    "push" -> "推日"
    "pull" -> "拉日"
    "legs" -> "腿日"
    else -> plans.firstOrNull { "plan:${it.id}" == value }?.name ?: "计划已移除"
}
