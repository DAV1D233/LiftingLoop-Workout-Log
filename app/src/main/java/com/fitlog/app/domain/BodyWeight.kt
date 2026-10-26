package com.fitlog.app.domain

object BodyWeight {
    val exercises = setOf("引体向上", "双杠臂屈伸", "凳上反屈伸", "山羊挺身", "卷腹", "悬垂举腿", "死虫式")
    fun valid(kg: Double?) = kg != null && kg.isFinite() && kg in 1.0..500.0
    fun parse(text: String): Double? = text.trim().toDoubleOrNull()?.takeIf { valid(it) }
    fun effectiveWeight(spec: ExerciseSpec, kg: Double?): Double =
        spec.weight + if (spec.isBodyweight && valid(kg)) kg!! else 0.0
}
