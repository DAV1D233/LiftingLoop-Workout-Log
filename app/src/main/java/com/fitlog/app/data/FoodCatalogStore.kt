package com.fitlog.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.fitlog.app.domain.FoodSpec
import com.fitlog.app.domain.FoodPortion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

object FoodJson {
    private fun JSONObject.amount(key: String) = if (isNull(key)) null else optDouble(key).takeIf { it.isFinite() && it >= 0 }
    fun decode(text: String): List<FoodSpec> {
        val array = JSONArray(text)
        return (0 until array.length()).mapNotNull { i -> runCatching {
            val o = array.getJSONObject(i)
            FoodSpec(o.getString("id"), o.getString("name"), o.optString("brand", "自定义"),
                o.optString("category", "自定义"), o.optString("portion", "1份"), o.amount("grams"),
                o.amount("calories"), o.amount("protein"), o.amount("carbs"), o.amount("fat"),
                o.optString("image"), o.optString("source"), o.optString("sourceUrl"),
                o.optString("imageSource"), o.optBoolean("estimated", true)).takeIf { it.valid() }
        }.getOrNull() }.distinctBy { it.id }
    }
    private fun encode(food: FoodSpec) = JSONObject().apply {
        put("id", food.id); put("name", food.name); put("brand", food.brand); put("category", food.category)
        put("portion", food.portion); put("grams", food.grams ?: JSONObject.NULL)
        put("calories", food.calories ?: JSONObject.NULL); put("protein", food.protein ?: JSONObject.NULL)
        put("carbs", food.carbs ?: JSONObject.NULL); put("fat", food.fat ?: JSONObject.NULL)
        put("image", food.image); put("source", food.source); put("sourceUrl", food.sourceUrl)
        put("imageSource", food.imageSource); put("estimated", food.estimated)
    }
    fun encode(foods: List<FoodSpec>) = JSONArray().apply { foods.forEach { put(encode(it)) } }.toString()
    fun cart(items: List<FoodPortion>) = JSONArray().apply { items.forEach { put(JSONObject().apply {
        put("food", encode(it.food)); put("quantity", it.quantity)
    }) } }.toString()
    fun cart(text: String): List<FoodPortion> = runCatching {
        val array = JSONArray(text)
        (0 until array.length()).mapNotNull { i -> runCatching {
            val o = array.getJSONObject(i)
            decode(JSONArray().put(o.getJSONObject("food")).toString()).firstOrNull()
                ?.let { FoodPortion(it, o.getDouble("quantity")) }?.takeIf { it.valid() }
        }.getOrNull() }.distinctBy { it.food.id }
    }.getOrDefault(emptyList())
}

class FoodCatalogStore(private val context: Context) {
    private val preferences = context.getSharedPreferences("fitlog_food_catalog", Context.MODE_PRIVATE)
    val builtIn: List<FoodSpec> = listOf("foods/home.json", "foods/catalog.json").flatMap { path ->
        runCatching { context.assets.open(path).bufferedReader().use { FoodJson.decode(it.readText()) } }.getOrDefault(emptyList())
    }
    private val custom = MutableStateFlow(runCatching { FoodJson.decode(preferences.getString("items", "[]") ?: "[]") }.getOrDefault(emptyList()))
    val values = custom.asStateFlow()
    suspend fun save(food: FoodSpec) = withContext(Dispatchers.IO) {
        require(food.valid() && food.calories != null && food.brand == "自定义")
        val next = custom.value.filterNot { it.id == food.id } + food.copy(name = food.name.trim())
        check(preferences.edit().putString("items", FoodJson.encode(next)).commit())
        custom.value = next
    }
    suspend fun delete(food: FoodSpec) = withContext(Dispatchers.IO) {
        val next = custom.value.filterNot { it.id == food.id }
        check(preferences.edit().putString("items", FoodJson.encode(next)).commit())
        custom.value = next
        // Historical records and in-progress carts can still reference this image.
    }
    suspend fun importImage(uri: Uri): String = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0)
        val options = BitmapFactory.Options().apply {
            inSampleSize = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 512) inSampleSize *= 2
        }
        val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: error("无法读取图片")
        val directory = File(context.filesDir, "food_library_images").apply { check(exists() || mkdirs()) }
        val file = File(directory, "${UUID.randomUUID()}.jpg")
        try { file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it)) }; file.absolutePath }
        catch (e: Exception) { file.delete(); throw e } finally { bitmap.recycle() }
    }
}
