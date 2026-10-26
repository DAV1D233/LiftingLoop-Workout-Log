package com.fitlog.app.ui.workout

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.fitlog.app.ui.SessionSummary
import com.fitlog.app.ui.components.*
import com.fitlog.app.ui.theme.*
import java.io.File
import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.UUID

@Composable
internal fun WorkoutShareButton(summary: SessionSummary, compact: Boolean = false) {
    var editing by rememberSaveable { mutableStateOf(false) }
    if (compact) AppTextButton({ editing = true }) { Text("编辑分享卡片 ↗", color = Green) }
    else AppButton({ editing = true }, Modifier.fillMaxWidth().heightIn(min = 54.dp),
        colors = ButtonDefaults.buttonColors(containerColor = GreenAccent, contentColor = Ink)) { Text("编辑 / 分享训练卡片 ↗") }
    if (editing) WorkoutPosterEditor(summary) { editing = false }
}

internal suspend fun exportWorkoutPoster(context: Context, summary: SessionSummary, options: PosterOptions): File {
    val directory = File(context.cacheDir, "shared-workouts").apply { check(isDirectory || mkdirs()) }
    directory.listFiles()?.filter { it.name.startsWith("poster-") && System.currentTimeMillis() - it.lastModified() > 7 * 86400000L }?.forEach { it.delete() }
    val file = File(directory, "poster-${UUID.randomUUID()}.png")
    val job = currentCoroutineContext()
    try {
        WorkoutPosterRenderer(summary, options, { job.ensureActive() }).use { document ->
            val output = document.render(outputWidth = 1080)
            try { file.outputStream().use { check(output.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
            finally { output.recycle() }
        }
        job.ensureActive()
        return file
    } catch (error: Throwable) { file.delete(); throw error }
}

/** Scoped album write; no broad storage permission or access to other photos. */
@RequiresApi(29)
internal suspend fun saveWorkoutPosterToGallery(context: Context, file: File): android.net.Uri {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "LiftingLoop-${System.currentTimeMillis()}.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/LiftingLoop")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val uri = requireNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
    try {
        requireNotNull(resolver.openOutputStream(uri)).use { output -> file.inputStream().use { it.copyTo(output) } }
        currentCoroutineContext().ensureActive()
        check(resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null) == 1)
        return uri
    } catch (error: Throwable) { resolver.delete(uri, null, null); throw error }
}

/** Local files are only sent after the user chooses a recipient in the system chooser. */
internal fun shareWorkoutPosters(context: Context, files: List<File>) {
    require(files.isNotEmpty())
    val uris = ArrayList(files.map { FileProvider.getUriForFile(context, "${context.packageName}.workout-sharing", it) })
    val intent = Intent(if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
        type = "image/png"
        if (uris.size == 1) putExtra(Intent.EXTRA_STREAM, uris.first()) else putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        clipData = ClipData.newUri(context.contentResolver, "LiftingLoop 训练海报", uris.first()).also { clip ->
            uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
        }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "分享训练海报"))
}
