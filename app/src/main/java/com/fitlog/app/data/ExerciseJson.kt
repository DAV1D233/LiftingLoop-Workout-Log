package com.fitlog.app.data

import com.fitlog.app.domain.ExerciseSpec
import org.json.JSONArray
import org.json.JSONObject

object ExerciseJson {
    fun encodeCardio(target: com.fitlog.app.domain.CardioTarget): String = JSONObject()
        .put("mode", target.mode).put("seconds", target.durationSeconds).put("meters", target.distanceMeters)
        .put("pool", target.poolLengthMeters).put("stroke", target.stroke).put("training", target.training).toString()
    fun decodeCardio(raw: String): com.fitlog.app.domain.CardioTarget = if (raw.isBlank() || raw == "{}")
        com.fitlog.app.domain.CardioTarget(mode = "计次") else JSONObject(raw).let {
        com.fitlog.app.domain.CardioTarget(it.optString("mode", "计时"), it.optInt("seconds", 60),
            it.optDouble("meters", 0.0), it.optDouble("pool", 0.0), it.optString("stroke"), it.optString("training", "auto")).normalized()
    }
    fun encodeTargets(items: List<com.fitlog.app.domain.SetTarget>) = JSONArray().apply {
        items.forEach { put(JSONObject().put("weight", it.weight).put("reps", it.reps).put("rest", it.restSeconds)) }
    }.toString()
    fun decodeTargets(raw: String): List<com.fitlog.app.domain.SetTarget> = JSONArray(raw).let { array ->
        List(array.length().coerceAtMost(20)) { i -> array.getJSONObject(i).let {
            com.fitlog.app.domain.SetTarget(it.getDouble("weight"), it.getInt("reps"), it.getInt("rest")).normalized()
        } }
    }
    fun encode(items: List<ExerciseSpec>): String = JSONArray().apply {
        items.forEach { e -> put(JSONObject().put("name", e.name).put("sets", e.sets)
            .put("reps", e.reps).put("weight", e.weight).put("rest", e.restSeconds).put("bodyweight", e.isBodyweight)
            .put("muscleGroup", e.muscleGroup).put("iconPath", e.iconPath).put("videoUrl", e.videoUrl).put("cardio", e.isCardio)
            .put("setTargets", JSONArray(encodeTargets(e.setTargets))).put("cardioTarget", JSONObject(encodeCardio(e.cardioTarget)))) }
    }.toString()
    fun decode(raw: String): List<ExerciseSpec> = JSONArray(raw).let { array ->
        List(array.length()) { i -> array.getJSONObject(i).let { e ->
            ExerciseSpec(e.getString("name"), e.getInt("sets"), e.getInt("reps"), e.getDouble("weight"), e.getInt("rest"),
                e.optBoolean("bodyweight", e.getString("name").trim() in com.fitlog.app.domain.BodyWeight.exercises),
                e.optString("muscleGroup"), e.optString("iconPath"), e.optString("videoUrl"), e.optBoolean("cardio", false),
                decodeTargets(e.optJSONArray("setTargets")?.toString() ?: "[]"),
                decodeCardio(e.optJSONObject("cardioTarget")?.toString() ?: "{}")).normalized()
        } }
    }
}
