package com.fitlog.app.domain

object NutritionInput {
    fun amount(text: String): Double? = text.trim().toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
    fun optionalValid(text: String) = text.isBlank() || amount(text) != null
    fun canSave(name: String, calories: String, protein: String, carbs: String, fat: String) =
        name.isNotBlank() && amount(calories) != null && listOf(protein, carbs, fat).all(::optionalValid)
}
