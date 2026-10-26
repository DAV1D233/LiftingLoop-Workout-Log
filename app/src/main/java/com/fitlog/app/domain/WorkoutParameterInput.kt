package com.fitlog.app.domain

/** Bounds used by the plan parameter keypad; never silently round or clamp entered values. */
object WorkoutParameterInput {
    fun parse(text: String, minimum: Double, maximum: Double, wholeNumber: Boolean): Double? {
        if (!text.matches(Regex("\\d+(\\.\\d{0,2})?"))) return null
        val value = text.toDoubleOrNull() ?: return null
        return value.takeIf { it.isFinite() && it in minimum..maximum && (!wholeNumber || it % 1.0 == 0.0) }
    }
}
