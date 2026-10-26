package com.fitlog.app.domain

import java.math.BigDecimal
import java.math.RoundingMode

/** Presentation only: retain original precision in records and nutrition calculations. */
fun foodNumber(value: Double): String = if (!value.isFinite()) "—" else
    BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

data class FoodSpec(val id: String, val name: String, val brand: String, val category: String,
    val portion: String, val grams: Double? = null, val calories: Double? = null,
    val protein: Double? = null, val carbs: Double? = null, val fat: Double? = null,
    val image: String = "", val source: String = "", val sourceUrl: String = "",
    val imageSource: String = "", val estimated: Boolean = true) {
    fun valid() = id.isNotBlank() && name.isNotBlank() && name.length <= 120 && portion.isNotBlank() &&
        (grams == null || grams.isFinite() && grams > 0 && grams <= 10000) &&
        listOfNotNull(calories, protein, carbs, fat).all { it.isFinite() && it in 0.0..100000.0 }
}

data class FoodPortion(val food: FoodSpec, val quantity: Double = 1.0) {
    fun valid() = food.valid() && quantity.isFinite() && quantity > 0 && quantity <= 1000
    fun ready() = valid() && food.calories != null
}

/** A food uses one catalogue tag; reuse category storage to preserve existing records. */
object FoodTags {
    fun normalize(value: String) = value.trim().replace(Regex("\\s+"), " ")
    fun valid(value: String): Boolean {
        val label = normalize(value)
        return label.isNotEmpty() && label.length <= 20 && label != "全部" && label.none { it.isISOControl() }
    }
    fun options(foods: List<FoodSpec>, selected: String): List<String> =
        (foods.map { normalize(it.category) } + normalize(selected) + "自定义").filter { valid(it) }.distinct()
}

object FoodPortions {
    fun adjust(items: List<FoodPortion>, food: FoodSpec, delta: Double): List<FoodPortion> {
        if (!food.valid() || !delta.isFinite() || delta == 0.0) return items
        val existing = items.firstOrNull { it.food.id == food.id }
        val quantity = BigDecimal.valueOf(existing?.quantity ?: 0.0)
            .add(BigDecimal.valueOf(delta)).toDouble().coerceIn(0.0, 1000.0)
        if (quantity == 0.0) return items.filterNot { it.food.id == food.id }
        if (existing == null) return if (items.size < 200) items + FoodPortion(food, quantity) else items
        // Keep any per-meal nutrition correction when adjusting from the original catalogue.
        return items.map { if (it.food.id == food.id) it.copy(quantity = quantity) else it }
    }
}

object FoodNutrition {
    /** Protein/carbohydrate/fat energy shares, never infer a missing nutrient as zero. */
    fun energyShares(items: List<FoodPortion>): List<Double>? {
        val protein = total(items, "protein") ?: return null
        val carbs = total(items, "carbs") ?: return null
        val fat = total(items, "fat") ?: return null
        val energies = listOf(protein * 4, carbs * 4, fat * 9)
        val sum = energies.sum()
        return if (sum.isFinite() && sum > 0) energies.map { it / sum } else null
    }
    // Unknown is not zero: one missing constituent makes the meal's corresponding total unknown.
    fun total(items: List<FoodPortion>, nutrient: String): Double? {
        if (items.isEmpty() || items.any { !it.valid() }) return null
        var sum = 0.0
        for (item in items) {
            val value = when (nutrient) { "calories" -> item.food.calories; "protein" -> item.food.protein
                "carbs" -> item.food.carbs; "fat" -> item.food.fat; else -> return null } ?: return null
            sum += value * item.quantity
        }
        return sum.takeIf { it.isFinite() }
    }
    fun canSave(items: List<FoodPortion>) = items.isNotEmpty() && items.size <= 200 &&
        items.all { it.ready() } && total(items, "calories") != null
}
