package com.fitlog.app.domain

data class CardioTarget @JvmOverloads constructor(
    val mode: String = "计时", val durationSeconds: Int = 60, val distanceMeters: Double = 0.0,
    val poolLengthMeters: Double = 0.0, val stroke: String = "", val training: String = "auto"
) {
    fun normalized() = copy(mode = mode.takeIf { it in listOf("自由", "计时", "距离", "计次") } ?: "计时",
        training = training.takeIf { it in listOf("auto", "continuous", "interval") } ?: "auto",
        durationSeconds = durationSeconds.coerceIn(0, 86400),
        distanceMeters = if (distanceMeters.isFinite()) distanceMeters.coerceIn(0.0, 1000000.0) else 0.0,
        poolLengthMeters = if (poolLengthMeters.isFinite()) poolLengthMeters.coerceIn(0.0, 1000.0) else 0.0,
        stroke = stroke.trim().replace("·", "").replace("\n", " ").replace("\r", " ").take(30))
    fun overview(reps: Int): String = listOfNotNull(
        stroke.takeIf { it.isNotBlank() },
        when (mode) { "自由" -> "自由计时"; "计次" -> "$reps 次"; "距离" -> "${number(distanceMeters)} 米"; else -> cardioTime(durationSeconds) },
        if (mode != "距离" && distanceMeters > 0) "${number(distanceMeters)} 米" else null,
        if (mode == "距离" && durationSeconds > 0) "目标 ${cardioTime(durationSeconds)}" else null,
        if (poolLengthMeters > 0) "${number(poolLengthMeters)} 米池 · ${number(distanceMeters / poolLengthMeters)} 趟" else null
    ).joinToString(" · ")
}

val ExerciseSpec.isInterval: Boolean get() = isCardio && (cardioTarget.training == "interval" ||
    (cardioTarget.training == "auto" && (sets > 1 || cardioTarget.mode == "计次")))

fun cardioTime(seconds: Int): String = "${seconds / 60} 分 ${seconds % 60} 秒"
fun cardioPerformance(name: String, seconds: Int, meters: Double, swimming: Boolean): String? {
    if (seconds <= 0 || meters <= 0) return null
    if (name == "骑行") return "均速 ${number(kotlin.math.round(meters / seconds * 3.6 * 10) / 10)} km/h"
    val unit = if (swimming) 100 else 1000
    return "配速 ${cardioTime((seconds * unit / meters).toInt())}/${if (swimming) "100米" else "公里"}"
}
fun trainingCount(exercises: List<ExerciseSpec>): String = listOfNotNull(
    exercises.filterNot { it.isCardio }.sumOf { it.sets }.takeIf { it > 0 }?.let { "$it 组" },
    exercises.filter { it.isCardio }.sumOf { it.sets }.takeIf { it > 0 }?.let { "$it 段有氧" }).joinToString(" · ")

object MuscleGroups {
    val choices = listOf("胸", "背", "腿", "臀", "肩", "手臂", "核心", "全身")
    fun parse(raw: String): List<String> = raw.split(Regex("[、,，/;；|]"))
        .map { it.trim().removeSuffix("部") }.filter { it.isNotBlank() }.distinct()
    fun defaults(name: String): List<String> = when (name) {
        "杠铃卧推", "上斜哑铃卧推", "双杠臂屈伸" -> listOf("胸", "肩", "手臂")
        "蝴蝶机夹胸" -> listOf("胸", "肩")
        "引体向上", "杠铃划船", "高位下拉", "反手高位下拉", "坐姿划船", "正握开肘胸托T杠划船" -> listOf("背", "手臂", "核心")
        "山羊挺身", "罗马尼亚硬拉", "单腿硬拉" -> listOf("腿", "臀", "背", "核心")
        "杠铃深蹲", "保加利亚单腿蹲", "高脚杯深蹲" -> listOf("腿", "臀", "核心")
        "腿举" -> listOf("腿", "臀")
        "俯卧腿弯举", "站姿提踵" -> listOf("腿")
        "哑铃坐姿推举" -> listOf("肩", "手臂", "核心")
        "哑铃侧平举" -> listOf("肩")
        "绳索面拉", "坐姿反向飞鸟" -> listOf("肩", "背")
        "哑铃弯举", "锤式弯举", "绳索下压", "仰卧臂屈伸", "上斜仰卧杠铃臂屈伸" -> listOf("手臂")
        "凳上反屈伸" -> listOf("手臂", "胸", "肩")
        "卷腹", "死虫式" -> listOf("核心")
        "悬垂举腿" -> listOf("核心", "腿")
        "登山跑" -> listOf("核心", "腿", "肩")
        "开合跳", "高抬腿", "跑步", "骑行" -> listOf("腿", "核心", "全身")
        "游泳" -> listOf("背", "肩", "胸", "腿", "核心", "全身")
        else -> emptyList()
    }
}
