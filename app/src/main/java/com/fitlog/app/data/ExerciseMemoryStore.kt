package com.fitlog.app.data

import android.content.Context
import com.fitlog.app.domain.ExerciseSpec
import com.fitlog.app.domain.PlanSets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** Latest explicitly saved targets; exercise identity, artwork and completed logs stay separate. */
class ExerciseMemoryStore(context: Context) {
    private val preferences = context.getSharedPreferences("fitlog_exercise_memory", Context.MODE_PRIVATE)
    private val items = MutableStateFlow(runCatching {
        ExerciseJson.decode(preferences.getString("items", "[]") ?: "[]")
    }.getOrDefault(emptyList()))
    val values = items.asStateFlow()

    fun apply(spec: ExerciseSpec): ExerciseSpec {
        val last = items.value.lastOrNull { same(it, spec) } ?: return spec
        return spec.copy(sets = last.sets, reps = last.reps, weight = last.weight,
            restSeconds = last.restSeconds, setTargets = last.setTargets, cardioTarget = last.cardioTarget).normalized()
    }

    suspend fun save(specs: List<ExerciseSpec>) = withContext(Dispatchers.IO) {
        val clean = specs.map(PlanSets::explicit).filter { it.name.isNotBlank() }
        val next = items.value.toMutableList()
        clean.forEach { spec -> next.removeAll { same(it, spec) }; next.add(spec) }
        check(preferences.edit().putString("items", ExerciseJson.encode(next)).commit())
        items.value = next
    }

    private fun same(a: ExerciseSpec, b: ExerciseSpec) = a.name.trim() == b.name.trim() &&
        a.isCardio == b.isCardio && a.isBodyweight == b.isBodyweight
}
