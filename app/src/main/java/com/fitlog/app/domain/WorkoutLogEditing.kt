package com.fitlog.app.domain

data class LoggedSet @JvmOverloads constructor(val name: String, val weight: Double, val reps: Int,
    val isCardio: Boolean = false, val durationSeconds: Int = 0, val distanceMeters: Double = 0.0,
    val stroke: String = "", val poolLengthMeters: Double = 0.0) {
    fun valid() = name.isNotBlank() && '\n' !in name && '\r' !in name &&
        if (isCardio) durationSeconds in 0..86400 && distanceMeters.isFinite() && distanceMeters in 0.0..1000000.0 &&
            reps in 0..100 && poolLengthMeters.isFinite() && poolLengthMeters in 0.0..1000.0 && '\n' !in stroke && '\r' !in stroke && '·' !in stroke
        else weight.isFinite() && weight in 0.0..2500.0 && reps in 1..100
}

object WorkoutLogEditing {
    private val line = Regex("^(.+?)\\s*·\\s*第\\s*\\d+\\s*组\\s+([0-9]+(?:\\.[0-9]+)?)\\s*kg\\s*×\\s*(\\d+)\\s*次$")
    private val cardioLine = Regex("^(.+?)\\s*·\\s*第\\s*\\d+\\s*段\\s+有氧 · (\\d+) 秒 · ([0-9.]+) 米(?: · (\\d+) 次)?(?: · 泳姿：([^·]+))?(?: · 池长：([0-9.]+) 米)?$")
    fun cardioSet(raw: String): LoggedSet? = cardioLine.matchEntire(raw.trim())?.let { m ->
        LoggedSet(m.groupValues[1].trim(), 0.0, m.groupValues[4].toIntOrNull() ?: 0, true,
            m.groupValues[2].toIntOrNull() ?: -1, m.groupValues[3].toDoubleOrNull() ?: Double.NaN,
            m.groupValues[5].trim(), m.groupValues[6].toDoubleOrNull() ?: 0.0).takeIf { it.valid() }
    }
    fun fromCompleted(log: CompletedSet) = LoggedSet(log.name, log.weight, if (log.isCardio && log.cardioMode != "计次") 0 else log.reps,
        log.isCardio, log.durationSeconds, log.distanceMeters, log.stroke, log.poolLengthMeters)
    fun parse(summary: String, totalSets: Int): List<LoggedSet>? {
        val lines = summary.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.size != totalSets || totalSets <= 0) return null
        return lines.map { raw ->
            cardioSet(raw)?.let { return@map it }
            val match = line.matchEntire(raw.trim()) ?: return null
            val set = LoggedSet(match.groupValues[1].trim(), match.groupValues[2].toDoubleOrNull() ?: return null,
                match.groupValues[3].toIntOrNull() ?: return null)
            if (!set.valid()) return null
            set
        }
    }
    fun summary(sets: List<LoggedSet>): String {
        require(sets.isNotEmpty() && sets.all { it.valid() })
        val ordinals = mutableMapOf<String, Int>()
        return sets.joinToString("\n") { set ->
            val name = set.name.trim()
            val ordinal = (ordinals[name] ?: 0) + 1
            ordinals[name] = ordinal
            if (set.isCardio) "$name · 第 $ordinal 段  有氧 · ${set.durationSeconds} 秒 · ${number(set.distanceMeters)} 米" +
                (if (set.reps > 0) " · ${set.reps} 次" else "") +
                (if (set.stroke.isNotBlank()) " · 泳姿：${set.stroke}" else "") +
                (if (set.poolLengthMeters > 0) " · 池长：${number(set.poolLengthMeters)} 米" else "")
            else "$name · 第 $ordinal 组  ${number(set.weight)} kg × ${set.reps} 次"
        }
    }
    fun volume(sets: List<LoggedSet>): Double {
        require(sets.isNotEmpty() && sets.all { it.valid() })
        return sets.filterNot { it.isCardio }.sumOf { it.weight * it.reps }
    }
}
