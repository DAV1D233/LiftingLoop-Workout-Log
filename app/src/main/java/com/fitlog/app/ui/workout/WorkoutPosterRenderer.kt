package com.fitlog.app.ui.workout

import android.content.Context
import android.graphics.*
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.fitlog.app.domain.*
import com.fitlog.app.ui.SessionSummary
import com.fitlog.app.ui.components.WeekNames
import com.fitlog.app.ui.components.timerLabel
import java.io.File
import java.time.LocalDate
import java.util.UUID
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

internal data class PosterOptions(val title: String, val photo: String? = null,
    val bottom: Boolean = true, val ratio: Float = 2f, val detailed: Boolean = true,
    val photoScale: Float = 1f, val photoX: Float = 0f, val photoY: Float = 0f)

/** One measured canvas: preview and export use identical columns and photo placement. */
internal class WorkoutPosterRenderer(private val summary: SessionSummary, private val options: PosterOptions,
    private val cancelled: () -> Unit = {}, loadPhoto: Boolean = true) : AutoCloseable {
    private val width = 1080f
    private val ink = Color.rgb(29, 40, 34)
    private val foreground = if (options.photo != null) Color.rgb(250, 252, 249) else ink
    private val muted = if (options.photo != null) Color.rgb(220, 230, 223) else Color.rgb(95, 107, 98)
    private val actions = WorkoutShareLayout.actions(summary.completed)
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 30f; color = foreground }
    private val chipWidth = (actions.flatMap { it.rows.flatten() }.maxOfOrNull {
        textPaint.measureText(WorkoutShareLayout.setText(it)) + 90f
    } ?: 180f).coerceIn(180f, 920f)
    private val columnCount = ((952f / (maxOf(260f, chipWidth) + 32f)).toInt()).coerceIn(1, 3)
    private val columnWidth = (920f - 32f * (columnCount - 1)) / columnCount
    private val actionHeaders = actions.map { layout(it.name, 36f, columnWidth.toInt(), foreground, true) }
    private val rowLayouts = actions.map { action -> action.rows.flatten().map {
        layout(WorkoutShareLayout.setText(it), 30f, (chipWidth - 82).toInt(), foreground)
    } }
    private val measured = actions.mapIndexed { index, action -> MeasuredShareAction(
        actionHeaders[index].height + 54f, action.rows.flatten().indices.map { row ->
            rowLayouts[index][row].height.toFloat().coerceAtLeast(36f) + 24f }) }
    private val titleLayout = fittedTitle(options.title.ifBlank { summary.title })
    private val baseHeight = (1080 * options.ratio.coerceIn(.5f, 3f)).toInt()
    private val minimumRows = measured.maxOfOrNull { it.headerHeight + (it.rowHeights.maxOrNull() ?: 0f) } ?: 0f
    private val start = if (options.photo == null) 64f else
        minOf(baseHeight * if (options.bottom) .38f else .12f,
            baseHeight - titleLayout.height - 370f - minimumRows).coerceAtLeast(48f)
    private val top = start + 108 + titleLayout.height + 174
    private val arrangement: ShareColumns = WorkoutShareLayout.columns(measured, columnCount, (baseHeight - top - 130f).coerceAtLeast(1f))
    private val height = maxOf(baseHeight, kotlin.math.ceil(top + arrangement.capacity + 130f).toInt())
    val canvasRatio get() = width / height
    val pageCount get() = 1
    val columns: List<List<ShareSlice>> get() = arrangement.columns
    private var photo: Bitmap? = null

    init {
        try {
            if (loadPhoto) options.photo?.let { path ->
                photo = decodeBounded(File(path), 1800)
            }
        } catch (error: Throwable) { close(); throw error }
    }

    fun render(page: Int = 0, outputWidth: Int = 720, background: Boolean = true): Bitmap {
        require(page == 0) { "Single-image export" }
        cancelled()
        // Bound allocation and texture size without dropping records or creating another image.
        val scale = minOf(outputWidth / width, 16384f / height,
            kotlin.math.sqrt(16_000_000f / (width * height)))
        val result = Bitmap.createBitmap((width * scale).toInt().coerceAtLeast(1),
            (height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        try {
            val c = Canvas(result); c.scale(scale, scale)
            if (options.photo == null) c.drawColor(Color.rgb(243, 245, 246))
            else {
                if (background) {
                    c.drawColor(Color.rgb(22, 28, 25))
                    photo?.let { placePhoto(c, it, width, height.toFloat()) }
                }
                val veil = Paint().apply { shader = LinearGradient(0f, start - 100, 0f, height.toFloat(),
                    intArrayOf(Color.TRANSPARENT, 0xB8101814.toInt(), 0xD9101814.toInt()),
                    floatArrayOf(0f, (180f / (height - start + 100)).coerceIn(.001f, .99f), 1f), Shader.TileMode.CLAMP) }
                c.drawRect(0f, start - 100, width, height.toFloat(), veil)
            }
            text(c, "LiftingLoop  /  TRAINING RECORD", 80f, start + 25, 24f, muted, true)
            val date = LocalDate.ofEpochDay(summary.dateEpochDay)
            text(c, "${date.year}.${date.monthValue.toString().padStart(2, '0')}.${date.dayOfMonth.toString().padStart(2, '0')}  ${WeekNames[date.dayOfWeek.value - 1]}", 80f, start + 78, 26f, muted)
            drawLayout(c, titleLayout, 80f, start + 108)
            val metrics = start + 108 + titleLayout.height + 63
            val strength = summary.sets > summary.cardioRounds
            text(c, timerLabel(summary.seconds), 80f, metrics, 52f, foreground, true)
            text(c, if (strength) "${number(summary.volume)} kg" else "${number(summary.cardioDistance)} 米", 560f, metrics, 46f, foreground, true, 440f)
            text(c, "时长 · ${summary.exercises} 个动作 / ${summary.sets} ${if (summary.cardioRounds > 0) "段 / 组" else "组"}", 80f, metrics + 42, 23f, muted)
            text(c, if (strength) "训练容量" else "有氧距离", 560f, metrics + 42, 23f, muted)
            if (summary.cardioRounds > 0 && strength) text(c, "有氧 ${cardioTime(summary.cardioSeconds)} · ${number(summary.cardioDistance)} 米", 80f, metrics + 78, 23f, muted)
            arrangement.columns.forEachIndexed { column, slices ->
                var y = top
                val x = 80f + column * (columnWidth + 32f)
                slices.forEach { slice ->
                    cancelled()
                    val action = actions[slice.action]
                    drawLayout(c, actionHeaders[slice.action], x, y + 20f)
                    y += measured[slice.action].headerHeight
                    val sets = action.rows.flatten()
                    for (row in slice.fromRow until slice.untilRow) {
                        val h = measured[slice.action].rowHeights[row]
                        c.drawRoundRect(x, y, x + chipWidth, y + h - 8, 12f, 12f,
                            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (options.photo != null) 0x25FFFFFF else 0xFFE9EEF1.toInt() })
                        val centerY = y + (h - 8) / 2
                        c.drawCircle(x + 27, centerY, 18f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = if (options.photo != null) 0x35FFFFFF else 0xFFD5EDE4.toInt() })
                        val ordinal = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = foreground; textSize = 22f
                            textAlign = Paint.Align.CENTER; typeface = Typeface.create("sans-serif", Typeface.BOLD) }
                        c.drawText(sets[row].ordinal.toString(), x + 27, centerY - (ordinal.ascent() + ordinal.descent()) / 2, ordinal)
                        val rowLayout = rowLayouts[slice.action][row]
                        drawLayout(c, rowLayout, x + 58, y + (h - 8 - rowLayout.height) / 2)
                        y += h
                    }
                }
            }
            text(c, if (actions.any { !it.cardio }) "逐组记录 · 力量 kg × 次数" else "有氧训练记录", 80f, height - 64f, 21f, muted)
            return result
        } catch (error: Throwable) { result.recycle(); throw error }
    }

    private fun placePhoto(c: Canvas, b: Bitmap, w: Float, h: Float) {
        val scale = minOf(w / b.width, h / b.height) * options.photoScale.coerceIn(.25f, 6f)
        val bw = b.width * scale; val bh = b.height * scale
        val x = (w - bw) / 2 + options.photoX.coerceIn(-2f, 2f) * w
        val y = (h - bh) / 2 + options.photoY.coerceIn(-2f, 2f) * h
        c.drawBitmap(b, null, RectF(x, y, x + bw, y + bh), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    }
    override fun close() { photo?.recycle(); photo = null }
    private fun fittedTitle(value: String): StaticLayout {
        var size = 66f; var result = layout(value, size, 920, foreground, true)
        while (result.height > 180 && size > 24f) { size -= 2f; result = layout(value, size, 920, foreground, true) }
        return result
    }
    private fun layout(value: String, size: Float, w: Int, color: Int, bold: Boolean = false): StaticLayout {
        val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; textSize = size
            typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL) }
        return StaticLayout.Builder.obtain(value, 0, value.length, p, w).setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).build()
    }
    private fun drawLayout(c: Canvas, l: StaticLayout, x: Float, y: Float) { c.save(); c.translate(x, y); l.draw(c); c.restore() }
    private fun text(c: Canvas, value: String, x: Float, y: Float, size: Float, color: Int, bold: Boolean = false, max: Float = 920f) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; textSize = size; typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL) }
        if (p.measureText(value) > max) p.textSize *= max / p.measureText(value)
        c.drawText(value, x, y, p)
    }
}

private fun decodeBounded(file: File, max: Int): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "无法读取图片" }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > max) sample *= 2
    val raw = requireNotNull(BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }))
    if (maxOf(raw.width, raw.height) <= max) return raw
    val factor = max.toFloat() / maxOf(raw.width, raw.height)
    return Bitmap.createScaledBitmap(raw, (raw.width * factor).toInt().coerceAtLeast(1), (raw.height * factor).toInt().coerceAtLeast(1), true).also { if (it !== raw) raw.recycle() }
}

/** Copy a selected URI before access expires, normalize EXIF, and cap decoded image memory. */
internal suspend fun importPosterPhoto(context: Context, uri: Uri): String {
    val directory = File(context.cacheDir, "workout-captures").apply { check(isDirectory || mkdirs()) }
    directory.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 7 * 86400000L }?.forEach { it.delete() }
    val original = File(directory, "import-${UUID.randomUUID()}")
    val normalized = File(directory, "photo-${UUID.randomUUID()}.jpg")
    var raw: Bitmap? = null; var rotated: Bitmap? = null
    try {
        context.contentResolver.openInputStream(uri).use { source ->
            requireNotNull(source)
            original.outputStream().use { out ->
                val buffer = ByteArray(32768); var total = 0L
                while (true) { coroutineContext.ensureActive(); val n = source.read(buffer); if (n < 0) break
                    total += n; require(total <= 80L * 1024 * 1024) { "图片文件过大" }; out.write(buffer, 0, n) }
            }
        }
        raw = decodeBounded(original, 2000)
        val orientation = ExifInterface(original.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(270f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
            }
        }
        rotated = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
        normalized.outputStream().use { check(rotated.compress(Bitmap.CompressFormat.JPEG, 92, it)) }
        coroutineContext.ensureActive()
        return normalized.path
    } catch (e: Throwable) { normalized.delete(); throw e }
    finally { if (rotated !== raw) rotated?.recycle(); raw?.recycle(); original.delete() }
}
