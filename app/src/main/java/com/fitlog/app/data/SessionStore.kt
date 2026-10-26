package com.fitlog.app.data

import android.content.Context
import com.fitlog.app.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class SessionStore(context: Context) {
    private val preferences = context.getSharedPreferences("fitlog_session_v1", Context.MODE_PRIVATE)
    var restoreFailed = false
        private set
    fun load(): WorkoutSession? {
        val raw = preferences.getString("active", null) ?: return null
        return runCatching {
            decode(JSONObject(raw))
        }.onFailure { restoreFailed = true }.getOrNull()
    }
    private fun decode(root: JSONObject): WorkoutSession {
            require(root.getInt("schema") == 1)
            val items = root.getJSONArray("exercises")
            val exercises = List(items.length()) { i ->
                val e = items.getJSONObject(i)
                ExerciseSpec(e.getString("name"), e.getInt("sets"), e.getInt("reps"), e.getDouble("weight"), e.getInt("rest"),
                    e.optBoolean("bodyweight", e.getString("name").trim() in BodyWeight.exercises),
                    e.optString("muscleGroup"), e.optString("iconPath"), e.optString("videoUrl"), e.optBoolean("cardio", false),
                    ExerciseJson.decodeTargets(e.optJSONArray("setTargets")?.toString() ?: "[]"),
                    ExerciseJson.decodeCardio(e.optJSONObject("cardioTarget")?.toString() ?: "{}")).normalized()
            }
            require(exercises.isNotEmpty() && exercises.all { it.name.isNotBlank() })
            val logs = root.getJSONArray("completed")
            val completed = List(logs.length()) { i ->
                val l = logs.getJSONObject(i)
                CompletedSet(l.getInt("index"), l.getString("name"), l.getInt("ordinal"),
                    l.getDouble("weight"), l.getInt("reps"), l.getLong("at"), l.optBoolean("cardio", false),
                    l.optInt("seconds", 0), l.optDouble("meters", 0.0), l.optString("mode", "计时"),
                    l.optString("stroke"), l.optDouble("pool", 0.0))
            }
            val index = root.getInt("index")
            require(index in exercises.indices)
            require(completed.all { it.exerciseIndex in exercises.indices && it.ordinal in 1..exercises[it.exerciseIndex].sets })
            require(completed.map { it.exerciseIndex to it.ordinal }.distinct().size == completed.size)
            return WorkoutSession(root.getString("key"), root.getString("title"), root.getLong("started"), exercises, index, completed,
                if (root.isNull("restUntil")) null else root.getLong("restUntil"),
                if (root.isNull("ended")) null else root.getLong("ended"),
                root.optDouble("bodyWeightKg", Double.NaN).takeIf { BodyWeight.valid(it) },
                if (root.has("restStartedAt") && !root.isNull("restStartedAt")) root.getLong("restStartedAt") else null,
                root.optLong("segmentStartedAt", root.getLong("started")),
                root.optLong("pausedAt").takeIf { root.has("pausedAt") && !root.isNull("pausedAt") },
                root.optLong("pausedMillis", 0).coerceAtLeast(0), root.optLong("segmentPausedMillis", 0).coerceAtLeast(0),
                root.optInt("pendingCardioReview").takeIf { root.has("pendingCardioReview") && !root.isNull("pendingCardioReview") && it in exercises.indices },
                root.optBoolean("cardioStoppedEarly", false))
    }
    fun loadUndo(key: String?): List<WorkoutUndoStep> = runCatching {
        if (key == null) return@runCatching emptyList()
        val items = JSONArray(preferences.getString("undo", "[]"))
        List(items.length()) { i ->
            val item = items.getJSONObject(i)
            WorkoutUndoStep(decode(item.getJSONObject("session")), item.getLong("at"), item.getString("label"))
        }.also { steps -> require(steps.all { it.previous.key == key }) }
    }.getOrDefault(emptyList())

    suspend fun save(session: WorkoutSession?, undo: List<WorkoutUndoStep>? = null) = withContext(Dispatchers.IO) {
        val editor = preferences.edit()
        if (session == null) { editor.remove("active"); editor.remove("undo") }
        else {
            editor.putString("active", encode(session))
            if (undo != null) {
                val items = JSONArray()
                undo.forEach { step -> items.put(JSONObject().put("session", JSONObject(encode(step.previous)))
                    .put("at", step.capturedAt).put("label", step.label)) }
                editor.putString("undo", items.toString())
            }
        }
        check(editor.commit()) { "Unable to persist session" }
    }
    private fun encode(session: WorkoutSession): String {
        val exercises = JSONArray()
        session.exercises.forEach { e -> exercises.put(JSONObject().put("name", e.name).put("sets", e.sets)
            .put("reps", e.reps).put("weight", e.weight).put("rest", e.restSeconds).put("bodyweight", e.isBodyweight)
            .put("muscleGroup", e.muscleGroup).put("iconPath", e.iconPath).put("videoUrl", e.videoUrl).put("cardio", e.isCardio)
            .put("setTargets", JSONArray(ExerciseJson.encodeTargets(e.setTargets)))
            .put("cardioTarget", JSONObject(ExerciseJson.encodeCardio(e.cardioTarget)))) }
        val completed = JSONArray()
        session.completed.forEach { l -> completed.put(JSONObject().put("index", l.exerciseIndex).put("name", l.name)
            .put("ordinal", l.ordinal).put("weight", l.weight).put("reps", l.reps).put("at", l.finishedAt)
            .put("cardio", l.isCardio).put("seconds", l.durationSeconds).put("meters", l.distanceMeters)
            .put("mode", l.cardioMode).put("stroke", l.stroke).put("pool", l.poolLengthMeters)) }
        return JSONObject().put("schema", 1).put("key", session.key).put("title", session.title).put("started", session.startedAt)
            .put("exercises", exercises).put("completed", completed).put("index", session.index)
            .put("restUntil", session.restUntil ?: JSONObject.NULL).put("ended", session.endedAt ?: JSONObject.NULL)
            .put("restStartedAt", session.restStartedAt ?: JSONObject.NULL)
            .put("segmentStartedAt", session.segmentStartedAt)
            .put("pausedAt", session.pausedAt ?: JSONObject.NULL).put("pausedMillis", session.pausedMillis)
            .put("segmentPausedMillis", session.segmentPausedMillis)
            .put("pendingCardioReview", session.pendingCardioReview ?: JSONObject.NULL)
            .put("cardioStoppedEarly", session.cardioStoppedEarly)
            .put("bodyWeightKg", session.bodyWeightKg ?: JSONObject.NULL).toString()
    }
}
