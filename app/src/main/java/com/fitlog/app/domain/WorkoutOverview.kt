package com.fitlog.app.domain

data class RecordedAction(val name: String, val prescriptions: List<String>, val isCardio: Boolean = false) {
    val overview: String get() = if (prescriptions.distinct().size == 1)
        "${prescriptions.size} ${if (isCardio) "段" else "组"} · ${prescriptions.first()}" else prescriptions.joinToString(" → ")
}

object WorkoutOverview {
    private val line = Regex("^(.+?)\\s*·\\s*第\\s*\\d+\\s*[组段]\\s+(.+)$")
    fun actions(summary: String): List<RecordedAction> {
        val groups = linkedMapOf<String, MutableList<String>>()
        val cardioNames = mutableSetOf<String>()
        summary.lineSequence().forEach { raw ->
            val match = line.matchEntire(raw.trim()) ?: return@forEach
            val name = match.groupValues[1].trim()
            val cardio = WorkoutLogEditing.cardioSet(raw)
            if (cardio != null) cardioNames.add(name)
            val description = if (cardio == null) match.groupValues[2].trim() else
                listOfNotNull(cardio.stroke.takeIf { it.isNotBlank() }, cardioTime(cardio.durationSeconds),
                    if (cardio.distanceMeters > 0) "${number(cardio.distanceMeters)} 米" else null,
                    if (cardio.reps > 0) "${cardio.reps} 次" else null,
                    cardioPerformance(cardio.name, cardio.durationSeconds, cardio.distanceMeters, cardio.poolLengthMeters > 0 || cardio.name.contains("泳")),
                    if (cardio.poolLengthMeters > 0) "${number(cardio.distanceMeters / cardio.poolLengthMeters)} 趟" else null).joinToString(" · ")
            if (name.isNotEmpty()) groups.getOrPut(name) { mutableListOf() }.add(description)
        }
        return groups.map { (name, details) -> RecordedAction(name, details, name in cardioNames) }
    }
    fun shortAction(name: String): String = when (name) {
        "杠铃卧推", "上斜哑铃卧推" -> "卧推"
        "引体向上" -> "引体"
        "杠铃划船", "坐姿划船", "正握开肘胸托T杠划船" -> "划船"
        "高位下拉", "反手高位下拉" -> "下拉"
        "杠铃深蹲", "高脚杯深蹲", "保加利亚单腿蹲" -> "深蹲"
        "罗马尼亚硬拉", "单腿硬拉" -> "硬拉"
        "哑铃坐姿推举" -> "推举"
        "哑铃侧平举" -> "侧举"
        "哑铃弯举", "锤式弯举" -> "弯举"
        else -> name.take(2)
    }
}
