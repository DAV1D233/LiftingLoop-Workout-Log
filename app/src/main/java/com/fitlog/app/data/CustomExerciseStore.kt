package com.fitlog.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.fitlog.app.domain.ExerciseSpec
import com.fitlog.app.domain.ExerciseVideoLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class CustomExerciseStore(private val context: Context) {
    private val preferences = context.getSharedPreferences("fitlog_custom_exercises", Context.MODE_PRIVATE)
    private val items = MutableStateFlow(runCatching {
        ExerciseJson.decode(preferences.getString("items", "[]") ?: "[]")
    }.getOrDefault(emptyList()))
    val values = items.asStateFlow()
    private val hidden = MutableStateFlow(preferences.getStringSet("hidden", emptySet()).orEmpty().toSet())
    val hiddenNames = hidden.asStateFlow()

    fun library(): List<ExerciseSpec> {
        val defaults = PresetData.library.values.flatten()
        val names = defaults.map { it.name }.toSet()
        return defaults.filterNot { it.name in hidden.value }
            .map { original -> items.value.firstOrNull { it.name == original.name } ?: original } +
            items.value.filterNot { it.name in names || it.name in hidden.value }
    }

    suspend fun save(spec: ExerciseSpec, originalName: String? = null) = withContext(Dispatchers.IO) {
        val clean = spec.normalized()
        require(clean.name.isNotBlank() && ExerciseVideoLink.valid(clean.videoUrl))
        require(library().none { it.name == clean.name && it.name != originalName }) { "动作名称已存在" }
        val next = items.value.filterNot { it.name == clean.name || it.name == originalName } + clean
        val removed = hidden.value.toMutableSet().apply {
            if (originalName != null && originalName != clean.name) add(originalName)
            remove(clean.name)
        }
        check(preferences.edit().putString("items", ExerciseJson.encode(next)).putStringSet("hidden", removed).commit())
        items.value = next
        hidden.value = removed
    }

    suspend fun delete(name: String) = withContext(Dispatchers.IO) {
        val next = items.value.filterNot { it.name == name }
        val removed = hidden.value + name
        check(preferences.edit().putString("items", ExerciseJson.encode(next)).putStringSet("hidden", removed).commit())
        items.value = next; hidden.value = removed
    }

    suspend fun importIcon(uri: Uri): String = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "无法读取图片" }
        val options = BitmapFactory.Options().apply {
            inSampleSize = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 512) inSampleSize *= 2
        }
        val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("无法读取图片")
        val directory = File(context.filesDir, "exercise_icons").apply { check(exists() || mkdirs()) }
        val file = File(directory, "${UUID.randomUUID()}.png")
        try {
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            file.absolutePath
        } catch (error: Exception) {
            file.delete()
            throw error
        } finally { bitmap.recycle() }
    }
}
