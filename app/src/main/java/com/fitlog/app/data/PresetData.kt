package com.fitlog.app.data

import com.fitlog.app.domain.ExerciseSpec

data class PlanTemplate(val key: String, val title: String, val focus: String, val exercises: List<ExerciseSpec>)

object PresetData {
    val library = linkedMapOf(
        "胸" to listOf(ExerciseSpec("杠铃卧推", 4, 10, 40.0, 90), ExerciseSpec("上斜哑铃卧推", 3, 10, 15.0, 90), ExerciseSpec("蝴蝶机夹胸", 3, 12, 25.0, 60),
            ExerciseSpec("双杠臂屈伸")),
        "背" to listOf(ExerciseSpec("引体向上", 4, 8, 0.0, 120), ExerciseSpec("杠铃划船", 4, 10, 30.0, 90), ExerciseSpec("高位下拉", 3, 12, 35.0, 90), ExerciseSpec("坐姿划船", 3, 12, 35.0, 60),
            ExerciseSpec("反手高位下拉"), ExerciseSpec("正握开肘胸托T杠划船"), ExerciseSpec("山羊挺身")),
        "腿" to listOf(ExerciseSpec("杠铃深蹲", 4, 10, 40.0, 120), ExerciseSpec("腿举", 4, 10, 80.0, 90), ExerciseSpec("罗马尼亚硬拉", 3, 10, 40.0, 90), ExerciseSpec("俯卧腿弯举", 3, 12, 25.0, 60), ExerciseSpec("站姿提踵", 3, 15, 30.0, 45),
            ExerciseSpec("单腿硬拉"), ExerciseSpec("保加利亚单腿蹲"), ExerciseSpec("高脚杯深蹲")),
        "肩" to listOf(ExerciseSpec("哑铃坐姿推举", 3, 10, 12.5, 90), ExerciseSpec("哑铃侧平举", 3, 12, 7.5, 60), ExerciseSpec("绳索面拉", 3, 12, 15.0, 60),
            ExerciseSpec("坐姿反向飞鸟")),
        "手臂" to listOf(ExerciseSpec("哑铃弯举", 3, 12, 10.0, 60), ExerciseSpec("锤式弯举", 3, 12, 10.0, 60), ExerciseSpec("绳索下压", 3, 12, 20.0, 60),
            ExerciseSpec("仰卧臂屈伸"), ExerciseSpec("凳上反屈伸"), ExerciseSpec("上斜仰卧杠铃臂屈伸")),
        "核心" to listOf(ExerciseSpec("卷腹", 3, 15, 0.0, 45), ExerciseSpec("悬垂举腿", 3, 12, 0.0, 60), ExerciseSpec("死虫式", 3, 12, 0.0, 45)),
        "有氧" to listOf(ExerciseSpec("开合跳", 3, 30, 0.0, 30, isCardio = true),
            ExerciseSpec("高抬腿", 3, 30, 0.0, 30, isCardio = true), ExerciseSpec("登山跑", 3, 20, 0.0, 30, isCardio = true),
            ExerciseSpec("游泳", 1, 1, 0.0, 30, isCardio = true,
                cardioTarget = com.fitlog.app.domain.CardioTarget("距离", 0, 1000.0, 25.0, "自由泳")),
            ExerciseSpec("跑步", 1, 1, 0.0, 0, isCardio = true,
                cardioTarget = com.fitlog.app.domain.CardioTarget("计时", 1800, 0.0)),
            ExerciseSpec("骑行", 1, 1, 0.0, 0, isCardio = true,
                cardioTarget = com.fitlog.app.domain.CardioTarget("计时", 1800, 0.0)),
            ExerciseSpec("快走", 1, 1, 0.0, 0, isCardio = true,
                cardioTarget = com.fitlog.app.domain.CardioTarget("自由", 0, training = "continuous")),
            ExerciseSpec("椭圆机", 1, 1, 0.0, 0, isCardio = true,
                cardioTarget = com.fitlog.app.domain.CardioTarget("自由", 0, training = "continuous")),
            ExerciseSpec("跳绳", 1, 1, 0.0, 0, isCardio = true,
                cardioTarget = com.fitlog.app.domain.CardioTarget("自由", 0, training = "continuous")))
    )
    // Classification describes the library variant, independently of the logged extra weight.
    val bodyweightExercises = com.fitlog.app.domain.BodyWeight.exercises
    private fun pick(vararg names: String) = names.map { name -> library.values.flatten().first { it.name == name } }
    val templates = listOf(
        PlanTemplate("push", "推日", "胸 / 肩 / 三头", pick("杠铃卧推", "上斜哑铃卧推", "哑铃坐姿推举", "哑铃侧平举", "绳索下压")),
        PlanTemplate("pull", "拉日", "背 / 二头", pick("引体向上", "杠铃划船", "高位下拉", "绳索面拉", "哑铃弯举")),
        PlanTemplate("legs", "腿日", "下肢 / 核心", pick("杠铃深蹲", "腿举", "罗马尼亚硬拉", "俯卧腿弯举", "卷腹"))
    )
}
