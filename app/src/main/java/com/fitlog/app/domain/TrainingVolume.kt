package com.fitlog.app.domain

import kotlin.math.ceil

object TrainingVolume {
    private val group = Regex("(?<![\\d.\\-])([0-9]+(?:\\.[0-9]+)?)\\s*kg\\s*[×xX*]\\s*(\\d+)(?![\\d.])", RegexOption.IGNORE_CASE)

    fun resolve(saved: Double?, summary: String, totalSets: Int): Double? {
        if (saved != null && saved.isFinite() && saved >= 0) return saved
        if (totalSets <= 0) return if (totalSets == 0) 0.0 else null
        val groups = group.findAll(summary).toList()
        if (groups.size != totalSets) return null
        var total = 0.0
        for (match in groups) {
            val weight = match.groupValues[1].toDoubleOrNull() ?: return null
            val reps = match.groupValues[2].toIntOrNull() ?: return null
            if (!weight.isFinite() || weight !in 0.0..2500.0 || reps !in 1..100) return null
            total += weight * reps
        }
        return total.takeIf { it.isFinite() }
    }

    /** Five buckets, relative to the largest recorded daily volume in the displayed month. */
    fun level(volume: Double, peak: Double): Int {
        if (!volume.isFinite() || !peak.isFinite() || volume <= 0 || peak <= 0) return 0
        return ceil((volume / peak).coerceIn(0.0, 1.0) * 4).toInt().coerceIn(1, 4)
    }
}
