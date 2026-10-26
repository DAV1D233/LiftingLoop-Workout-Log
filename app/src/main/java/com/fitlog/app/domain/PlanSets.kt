package com.fitlog.app.domain

/** Explicit targets are the default in the plan editor; weight remains extra load for bodyweight exercises. */
object PlanSets {
    fun explicit(spec: ExerciseSpec): ExerciseSpec {
        val clean = spec.normalized()
        if (clean.isCardio) return clean
        return sync(clean, List(clean.sets) { clean.targetAt(it) })
    }
    fun resize(spec: ExerciseSpec, count: Int): ExerciseSpec {
        if (spec.isCardio) return spec.copy(sets = count).normalized()
        val clean = explicit(spec)
        return sync(clean, List(count.coerceIn(1, 20)) { clean.targetAt(it) })
    }
    fun update(spec: ExerciseSpec, index: Int, target: SetTarget): ExerciseSpec {
        require(!spec.isCardio && index in 0 until spec.sets)
        val clean = explicit(spec)
        return sync(clean, clean.setTargets.mapIndexed { i, item -> if (i == index) target.normalized() else item })
    }
    fun remove(spec: ExerciseSpec, index: Int): ExerciseSpec {
        require(!spec.isCardio && index in 0 until spec.sets)
        val clean = explicit(spec)
        if (clean.sets == 1) return clean
        return sync(clean, clean.setTargets.filterIndexed { i, _ -> i != index })
    }
    private fun sync(spec: ExerciseSpec, targets: List<SetTarget>): ExerciseSpec = spec.copy(sets = targets.size,
        weight = targets.first().weight, reps = targets.first().reps, restSeconds = targets.first().restSeconds,
        setTargets = targets).normalized()
}
