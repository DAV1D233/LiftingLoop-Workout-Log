package com.fitlog.app.ui.workout

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.fitlog.app.R
import com.fitlog.app.domain.ExerciseSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun ExerciseIcon(spec: ExerciseSpec, modifier: Modifier = Modifier) {
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, spec.iconPath) {
        value = if (spec.iconPath.isBlank()) null else withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeFile(spec.iconPath)?.asImageBitmap() }.getOrNull()
        }
    }
    val custom = bitmap
    if (custom != null) Image(custom, null, modifier, contentScale = ContentScale.Fit)
    else Image(painterResource(exerciseIllustration(spec.name) ?: when {
        spec.isCardio -> R.drawable.exercise_cardio_placeholder
        spec.isBodyweight -> R.drawable.exercise_bodyweight_placeholder
        else -> R.drawable.exercise_equipment_placeholder }), null,
        modifier, contentScale = ContentScale.Fit)
}
