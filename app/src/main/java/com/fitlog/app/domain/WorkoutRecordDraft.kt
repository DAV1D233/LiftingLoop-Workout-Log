package com.fitlog.app.domain

import java.util.UUID

/** Saveable raw input; stable action IDs keep cards separate while names are edited. */
object WorkoutRecordDraft {
    const val WIDTH = 9
    fun fromSets(sets: List<LoggedSet>): List<String> {
        var group = -1
        return sets.flatMapIndexed { index, set ->
            val previous = sets.getOrNull(index - 1)
            if (previous == null || previous.name != set.name || previous.isCardio != set.isCardio) group++
            listOf(set.name, number(set.weight), set.reps.toString(), set.isCardio.toString(),
                set.durationSeconds.toString(), number(set.distanceMeters), set.stroke, number(set.poolLengthMeters), "action-$group")
        }
    }
    fun decode(raw: List<String>): List<LoggedSet> {
        require(raw.size % WIDTH == 0)
        return raw.chunked(WIDTH).map { row -> LoggedSet(row[0].trim(), decimal(row[1]), row[2].toIntOrNull() ?: -1,
            row[3].toBoolean(), row[4].toIntOrNull() ?: -1, decimal(row[5]), row[6], decimal(row[7])) }
    }
    fun groups(raw: List<String>): List<List<Int>> = raw.chunked(WIDTH).withIndex()
        .groupBy { it.value[8] }.values.map { group -> group.map { it.index } }
    fun update(raw: List<String>, row: Int, field: Int, value: String): List<String> {
        require(field in 0..7 && row in 0 until raw.size / WIDTH)
        return raw.toMutableList().also { it[row * WIDTH + field] = value }
    }
    fun rename(raw: List<String>, group: String, name: String): List<String> = raw.chunked(WIDTH).flatMap {
        if (it[8] == group) it.toMutableList().also { row -> row[0] = name } else it
    }
    fun remove(raw: List<String>, row: Int): List<String> = raw.chunked(WIDTH).filterIndexed { index, _ -> index != row }.flatten()
    fun removeAction(raw: List<String>, group: String): List<String> = raw.chunked(WIDTH).filterNot { it[8] == group }.flatten()
    fun appendSet(raw: List<String>, group: String): List<String> {
        val rows = raw.chunked(WIDTH).toMutableList()
        val last = rows.indexOfLast { it[8] == group }
        require(last >= 0)
        rows.add(last + 1, rows[last].toList())
        return rows.flatten()
    }
    fun appendAction(raw: List<String>, name: String, cardio: Boolean): List<String> = raw +
        listOf(name, "0", if (cardio) "0" else "10", cardio.toString(), if (cardio) "60" else "0", "0", "", "0", UUID.randomUUID().toString())
    private fun decimal(raw: String) = raw.replace(',', '.').toDoubleOrNull() ?: Double.NaN
}
