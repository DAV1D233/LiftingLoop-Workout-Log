package com.fitlog.app

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.key
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.fitlog.app.data.AppDatabase
import com.fitlog.app.data.SessionStore
import com.fitlog.app.domain.*
import com.fitlog.app.ui.AppViewModel
import com.fitlog.app.ui.AppNavHost
import androidx.core.view.WindowCompat
import com.fitlog.app.ui.theme.FitLogTheme
import com.fitlog.app.ui.workout.CompletionScreen
import com.fitlog.app.ui.workout.CompletedTrainingSheet
import com.fitlog.app.ui.workout.WorkoutSessionScreen
import com.fitlog.app.ui.workout.WorkoutPosterRenderer
import com.fitlog.app.ui.workout.PosterOptions
import com.fitlog.app.ui.workout.TrainingScheduleScreen
import com.fitlog.app.ui.workout.NewTrainingPlanScreen
import com.fitlog.app.ui.components.BodyWeightDialog
import com.fitlog.app.ui.theme.*
import androidx.compose.ui.graphics.toArgb
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.UUID

/** Runs production ViewModel/Room on a separate database and preference namespace.
 * Never seeds or deletes the user's live training records. No extra test runtime library. */
class WorkoutSaveValidation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val report = Bundle()
        val tag = UUID.randomUUID().toString()
        val root = File(targetContext.cacheDir, "save-validation-$tag").apply { mkdirs() }
        val isolated = object : ContextWrapper(targetContext) {
            override fun getApplicationContext(): Context = this
            override fun getDatabasePath(name: String): File = File(root, "db/$name").apply { parentFile!!.mkdirs() }
            override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase =
                openOrCreateDatabase(name, mode, factory, null)
            override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, handler: DatabaseErrorHandler?): SQLiteDatabase =
                SQLiteDatabase.openDatabase(getDatabasePath(name).path, factory,
                    SQLiteDatabase.CREATE_IF_NECESSARY or SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING, handler)
            override fun getSharedPreferences(name: String, mode: Int) = baseContext.getSharedPreferences("save-validation-$tag-$name", mode)
            override fun getFilesDir(): File = File(root, "files").apply { mkdirs() }
            override fun getCacheDir(): File = File(root, "cache").apply { mkdirs() }
            override fun getNoBackupFilesDir(): File = File(root, "no-backup").apply { mkdirs() }
        }
        var owner: ViewModelStoreOwner = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
        lateinit var vm: AppViewModel
        lateinit var db: AppDatabase
        var activity: WorkoutValidationActivity? = null
        val trace = mutableListOf<String>()
        var assertions = 0
        fun verify(value: Boolean, message: String) { assertions++; check(value) { message } }
        fun await(message: String, predicate: () -> Boolean) {
            val limit = System.currentTimeMillis() + 10000
            while (!predicate()) { check(System.currentTimeMillis() < limit) { "Timeout: $message" }; Thread.sleep(25) }
        }
        fun operation(action: () -> Unit) { runOnMainSync(action); await("operation") { !vm.busy.value } }
        fun freshVm() {
            runOnMainSync {
                owner.viewModelStore.clear()
                owner = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
                vm = ViewModelProvider(owner, object : ViewModelProvider.Factory {
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST") return AppViewModel(isolated) as T
                    }
                })[AppViewModel::class.java]
            }
            await("initialization") { !vm.busy.value && vm.data.value.ready }
        }
        fun count() = runBlocking { db.workouts().records().first().size }
        fun palette(scheme: androidx.compose.material3.ColorScheme, name: String) {
            File(root, "material-$name.txt").writeText(listOf(
                "primary" to scheme.primary,
                "onPrimary" to scheme.onPrimary,
                "primaryContainer" to scheme.primaryContainer,
                "onPrimaryContainer" to scheme.onPrimaryContainer,
                "inversePrimary" to scheme.inversePrimary,
                "secondary" to scheme.secondary,
                "onSecondary" to scheme.onSecondary,
                "secondaryContainer" to scheme.secondaryContainer,
                "onSecondaryContainer" to scheme.onSecondaryContainer,
                "tertiary" to scheme.tertiary,
                "onTertiary" to scheme.onTertiary,
                "tertiaryContainer" to scheme.tertiaryContainer,
                "onTertiaryContainer" to scheme.onTertiaryContainer,
                "background" to scheme.background,
                "onBackground" to scheme.onBackground,
                "surface" to scheme.surface,
                "onSurface" to scheme.onSurface,
                "surfaceVariant" to scheme.surfaceVariant,
                "onSurfaceVariant" to scheme.onSurfaceVariant,
                "surfaceTint" to scheme.surfaceTint,
                "inverseSurface" to scheme.inverseSurface,
                "inverseOnSurface" to scheme.inverseOnSurface,
                "error" to scheme.error,
                "onError" to scheme.onError,
                "errorContainer" to scheme.errorContainer,
                "onErrorContainer" to scheme.onErrorContainer,
                "outline" to scheme.outline,
                "outlineVariant" to scheme.outlineVariant,
                "scrim" to scheme.scrim,
                "surfaceBright" to scheme.surfaceBright,
                "surfaceDim" to scheme.surfaceDim,
                "surfaceContainer" to scheme.surfaceContainer,
                "surfaceContainerHigh" to scheme.surfaceContainerHigh,
                "surfaceContainerHighest" to scheme.surfaceContainerHighest,
                "surfaceContainerLow" to scheme.surfaceContainerLow,
                "surfaceContainerLowest" to scheme.surfaceContainerLowest
            ).joinToString("\n") { (role, color) -> "$role=#${Integer.toHexString(color.toArgb()).uppercase()}" })
        }

        var resultCode = Activity.RESULT_OK
        try {
            db = AppDatabase.open(isolated)
            // Refuse to test if the application unexpectedly initialized the real DB first.
            verify(File(requireNotNull(db.openHelper.writableDatabase.path)).canonicalPath.startsWith(root.canonicalPath + File.separator),
                "Database must be isolated: ${db.openHelper.writableDatabase.path}")
            freshVm()
            operation { vm.begin("自动保存验证", listOf(ExerciseSpec("动作一", 1, 8, 20.0, 0), ExerciseSpec("动作二", 1, 10, 30.0, 0))) {} }
            operation { vm.completeSet() }
            verify(vm.summary.value == null && vm.session.value?.completed?.size == 1 && count() == 0, "Nonfinal set stays active")
            operation { vm.completeSet(); vm.completeSet(); vm.finish() }
            val strength = requireNotNull(vm.summary.value)
            verify(vm.session.value == null && strength.complete && strength.sets == 2 && strength.exercises == 2, "Final set saves without another click")
            verify(strength.volume == 460.0 && count() == 1, "Strength volume and one committed record")
            verify(SessionStore(isolated).load() == null, "Active checkpoint cleared only after commit")
            operation { vm.completeSet(); vm.finish() }
            verify(count() == 1, "Repeated final completion/finish does not duplicate")
            operation { vm.begin("有氧保存验证", listOf(ExerciseSpec("跑步", 1, restSeconds = 0, isCardio = true))) {} }
            operation { vm.completeSet(35, 400.0) }
            val cardio = requireNotNull(vm.summary.value)
            verify(cardio.complete && cardio.cardioSeconds == 35 && cardio.cardioDistance == 400.0 && cardio.cardioRounds == 1 && count() == 2, "Cardio final segment auto-saves all metrics")
            val pending = WorkoutEngine.complete(WorkoutEngine.start("完成后恢复验证", listOf(ExerciseSpec("动作", 1, 10, 20.0, 0)), System.currentTimeMillis()), System.currentTimeMillis())
            runBlocking { SessionStore(isolated).save(pending) }
            freshVm()
            verify(vm.summary.value?.complete == true && vm.session.value == null && count() == 3, "Finished checkpoint automatically committed on restoration")
            freshVm()
            verify(count() == 3, "Restoration is idempotent")
            operation { vm.begin("提前结束验证", listOf(ExerciseSpec("动作", 2, 10, 20.0, 0))) {} }
            operation { vm.completeSet() }
            operation { vm.finish() }
            verify(vm.summary.value?.complete == false && vm.summary.value?.sets == 1 && count() == 4, "Manual early finish preserved")
            operation { vm.begin("空训练验证", listOf(ExerciseSpec("动作", 1, 10, 20.0, 0))) {} }
            operation { vm.finish() }
            verify(vm.session.value == null && vm.summary.value == null && count() == 4, "Empty workout does not create a record")
            operation { vm.begin("休息阻挡验证", listOf(ExerciseSpec("动作", 2, 10, 20.0, 60))) {} }
            operation { vm.completeSet() }
            operation { vm.completeSet() }
            verify(vm.session.value?.completed?.size == 1 && vm.summary.value == null && count() == 4, "Rest prevents premature final completion")
            operation { vm.skipRest() }
            operation { vm.completeSet() }
            verify(vm.session.value == null && vm.summary.value?.complete == true && count() == 5, "Completion after rest auto-saves")
            var linkedPlan = 0L
            operation { vm.savePlan("周期关联验证", listOf(ExerciseSpec("旧动作", 1)), done = { linkedPlan = it }) }
            operation { vm.saveSchedule("cycle", List(7) { "" }, listOf("plan:$linkedPlan", "rest"), java.time.LocalDate.now().toEpochDay()) {} }
            operation { vm.savePlan("更新后的计划", listOf(ExerciseSpec("新动作", 3)), linkedPlan) {} }
            await("plan title propagated") { vm.data.value.plans.any { it.id == linkedPlan && it.name == "更新后的计划" } }
            val linked = runBlocking { vm.trainingPreview(vm.settings.today()) }
            verify(linked?.title == "更新后的计划" && linked.exercises.single().name == "新动作", "Cycle resolves latest name and exercises by stable plan ID")
            operation { vm.deletePlan(vm.data.value.plans.first { it.id == linkedPlan }) }
            verify(vm.settings.today() == "rest", "Deleting a linked plan safely changes its scheduled day to rest")
            // Render the real finish/details composables without booting MainActivity,
            // a production account sync, or a production ViewModel.
            val automation = uiAutomation
            val host = startActivitySync(Intent(targetContext, WorkoutValidationActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as WorkoutValidationActivity
            activity = host
            val details = mutableStateOf(false)
            // Compose virtual nodes are traversable but their provider does not consistently
            // implement the framework's findAccessibilityNodeInfosByText on all devices.
            fun nodes(text: String): List<AccessibilityNodeInfo> {
                val found = mutableListOf<AccessibilityNodeInfo>()
                fun visit(node: AccessibilityNodeInfo) {
                    node.refresh()
                    if (text.isEmpty() || node.text?.contains(text) == true || node.contentDescription?.contains(text) == true) found.add(node)
                    repeat(node.childCount) { node.getChild(it)?.let(::visit) }
                }
                automation.rootInActiveWindow?.let(::visit)
                return found
            }
            val epoch = mutableIntStateOf(0)
            runOnMainSync { host.setContent { key(epoch.intValue) { AppNavHost(vm) } } }
            await("validation window focus") { host.hasWindowFocus() }
            waitForIdleSync()
            Thread.sleep(1000) // Let the system's Activity launch transform settle before screen-coordinate input.
            val density = targetContext.resources.displayMetrics.density
            fun send(action: Int, downTime: Long, x: Float, y: Float) {
                val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
                event.source = InputDevice.SOURCE_TOUCHSCREEN
                try { check(automation.injectInputEvent(event, true)) { "Touch injection refused" } }
                finally { event.recycle() }
            }
            listOf("锻炼" to "运动日历", "饮食" to "搜索食物", "添加" to "长按训练计划可编辑或删除").forEachIndexed { index, (label, expected) ->
                if (index > 0) runOnMainSync { epoch.intValue++ }
                await("fresh Dock $label") { nodes(label).any { it.contentDescription?.toString() == label } }
                val circle = nodes(label).first { it.contentDescription?.toString() == label }
                val bounds = android.graphics.Rect().also { circle.getBoundsInScreen(it) }
                val x = bounds.exactCenterX(); val y = bounds.exactCenterY()
                val targetY = y - 60 * density
                trace.add("$label bounds=$bounds enabled=${circle.isEnabled} density=$density")
                val downTime = SystemClock.uptimeMillis()
                send(MotionEvent.ACTION_DOWN, downTime, x, y)
                if (index == 1) {
                    // Leaving the small circle before timeout must not lose the pending hold.
                    Thread.sleep(80)
                    send(MotionEvent.ACTION_MOVE, downTime, x, targetY)
                }
                Thread.sleep(ViewConfiguration.getLongPressTimeout().toLong() + 150)
                send(MotionEvent.ACTION_MOVE, downTime, x, targetY)
                send(MotionEvent.ACTION_UP, downTime, x, targetY)
                await("hold-release navigates $label") { nodes(expected).isNotEmpty() }
                verify(nodes(expected).isNotEmpty(), "First hold-drag-release navigates $label")
            }
            operation { vm.begin("返回确认验证", listOf(ExerciseSpec("动作", 2, 10, 20.0, 0))) {} }
            runOnMainSync { host.setContent { FitLogTheme(workout = true) {
                val scheme = androidx.compose.material3.MaterialTheme.colorScheme
                androidx.compose.runtime.SideEffect { palette(scheme, "dark") }
                WorkoutSessionScreen(rememberNavController(), vm)
            } } }
            await("session screen") { nodes("完成本组").isNotEmpty() }
            waitForIdleSync()
            Thread.sleep(400)
            android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("input keyevent 4")).use { it.readBytes() }
            await("back confirmation") { nodes("结束本次训练？").isNotEmpty() }
            verify(vm.session.value != null && count() == 5, "System Back asks before ending or saving")
            fun click(text: String) {
                val matches = nodes(text)
                var button = matches.firstOrNull { it.text?.toString() == text || it.contentDescription?.toString() == text } ?: matches.firstOrNull()
                while (button != null && !button.isClickable) button = button.parent
                check(button?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) { "Cannot click $text" }
            }
            click("继续")
            await("continue dismisses confirmation") { nodes("结束本次训练？").isEmpty() }
            verify(vm.session.value != null, "Continue after Back preserves active session")
            operation { vm.discard {} }
            val cardSets = List(13) { i ->
                val exercise = if (i < 5) 0 else if (i < 9) 1 else 2
                CompletedSet(exercise, listOf("动作一", "动作二", "动作三")[exercise],
                    if (i < 5) i + 1 else if (i < 9) i - 4 else i - 8, 20.0 + i, 10, System.currentTimeMillis())
            }
            val cardSummary = strength.copy(title = "推日", sets = 13, exercises = 3, seconds = 529, volume = cardSets.sumOf { it.weight * it.reps }, completed = cardSets)
            WorkoutPosterRenderer(cardSummary, PosterOptions(cardSummary.title)).use { document ->
                verify(document.pageCount == 1, "Thirteen-set compact poster fits one page")
                document.render(0).let { image ->
                    File(root, "poster-compact.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                    image.recycle()
                }
            }
            WorkoutPosterRenderer(cardSummary, PosterOptions(cardSummary.title, detailed = true)).use { document ->
                verify(document.pageCount == 1 && document.columns.flatten().sumOf { it.untilRow - it.fromRow } == 13, "One detailed poster preserves every set in columns")
            }
            runOnMainSync {
                WindowCompat.getInsetsController(host.window, host.window.decorView).apply {
                    isAppearanceLightStatusBars = false; isAppearanceLightNavigationBars = false
                }
                host.setContent {
                    FitLogTheme(workout = true) {
                        CompletionScreen(strength, {}, { details.value = true })
                        if (details.value) CompletedTrainingSheet(cardSummary) { details.value = false }
                    }
                }
            }
            await("minimal finish UI") { nodes("查看运动详情").isNotEmpty() }
            verify(nodes("动作与逐组记录").isEmpty(), "Finish page must not automatically expose poster/details")
            uiAutomation.takeScreenshot()?.let { image ->
                File(root, "minimal-finish.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                image.recycle()
            }
            var button: AccessibilityNodeInfo? = nodes("查看运动详情").first()
            while (button != null && !button.isClickable) button = button.parent
            verify(button?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true, "Details button clickable")
            await("details opened") { nodes("动作与逐组记录").isNotEmpty() }
            await("details expansion completed") { nodes("动作一").isNotEmpty() && nodes("动作二").isNotEmpty() }
            verify(nodes("动作一").isNotEmpty() && nodes("动作二").isNotEmpty(), "Details exposes all actual exercises")
            Thread.sleep(450) // Capture after the platform dialog entrance, not its translucent first frame.
            uiAutomation.takeScreenshot()?.let { image ->
                File(root, "workout-details.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                image.recycle()
            }
            verify(nodes("简洁").isEmpty() && nodes("详细").isEmpty(), "Details uses one full record style without mode controls")
            await("detailed units") { nodes("kg × 10 次").isNotEmpty() }
            await("scroll semantics") { nodes("").any { it.isScrollable } }
            verify(nodes("").first { it.isScrollable }.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD), "Detailed record scrolls forward")
            click("编辑分享卡片")
            await("poster editor") { nodes("训练图片编辑画布").isNotEmpty() && nodes("调整").isNotEmpty() }
            verify(nodes("保存图片").isNotEmpty() && nodes("拍照").isNotEmpty() && nodes("背景柔化").isEmpty(), "Editor exposes save/share and hides secondary adjustments")
            await("poster preview rendered") { nodes("完整训练记录，单张图片").isNotEmpty() }
            Thread.sleep(450)
            verify(nodes("分享").any { it.isEnabled }, "Rendered editor exposes sharing")
            uiAutomation.takeScreenshot()?.let { image ->
                File(root, "share-editor.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                image.recycle()
            }
            click("调整")
            await("layout tools") { nodes("竖版").isNotEmpty() && nodes("方形").isNotEmpty() }
            verify(nodes("拍照").isNotEmpty(), "Camera stays directly available in the toolbar")
            // Synthetic landscape photo: no access to the user's gallery/camera is needed.
            val landscape = File(root, "landscape.png")
            android.graphics.Bitmap.createBitmap(1800, 900, android.graphics.Bitmap.Config.ARGB_8888).let { image ->
                val canvas = android.graphics.Canvas(image)
                val paint = android.graphics.Paint()
                paint.color = android.graphics.Color.BLUE; canvas.drawRect(0f, 0f, 600f, 900f, paint)
                paint.color = android.graphics.Color.GREEN; canvas.drawRect(600f, 0f, 1200f, 900f, paint)
                paint.color = android.graphics.Color.RED; canvas.drawRect(1200f, 0f, 1800f, 900f, paint)
                landscape.outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; image.recycle()
            }
            val emptyCard = cardSummary.copy(sets = 0, exercises = 0, completed = emptyList())
            WorkoutPosterRenderer(emptyCard, PosterOptions("横图检查", landscape.path, ratio = .5f)).use { document ->
                document.render().let { image ->
                    val photoTop = ((image.height - image.width * .5f) / 2).toInt().coerceAtLeast(0)
                    val left = image.getPixel(8, photoTop + 8); val right = image.getPixel(image.width - 9, photoTop + 8)
                    verify(android.graphics.Color.blue(left) > android.graphics.Color.red(left) * 2 &&
                        android.graphics.Color.red(right) > android.graphics.Color.blue(right) * 2, "Landscape photo keeps both source edges without automatic crop")
                    image.recycle()
                }
            }
            val longSets = List(400) { i -> CompletedSet(i / 20, "动作${i / 20 + 1}", i % 20 + 1, 42.5, 10, 0) }
            WorkoutPosterRenderer(cardSummary.copy(sets = 400, exercises = 20, completed = longSets), PosterOptions("长记录检查")).use { document ->
                verify(document.pageCount == 1 && document.columns.flatten().sumOf { it.untilRow - it.fromRow } == 400,
                    "Long workout stays one image and preserves all 400 sets")
                document.render(outputWidth = 360).let { image ->
                    verify(image.height > image.width * 2 && image.height <= 16384, "One long image is bounded without clipping sets")
                    image.recycle()
                }
            }
            runOnMainSync { host.setContent { FitLogTheme { com.fitlog.app.ui.workout.WorkoutPosterEditor(cardSummary, landscape.path) {} } } }
            await("photo canvas") { nodes("训练图片编辑画布").isNotEmpty() && nodes("完整训练记录，单张图片").isNotEmpty() }
            Thread.sleep(900)
            val photoBounds = android.graphics.Rect().also { nodes("训练图片编辑画布").first().getBoundsInScreen(it) }
            verify(photoBounds.width() > 200 && photoBounds.height() > 200, "Editor gives the photo an interactive canvas")
            fun canvasSignature(): Long {
                val image = requireNotNull(uiAutomation.takeScreenshot())
                var signature = 0L
                for (y in photoBounds.top + 20 until photoBounds.bottom - 20 step 17)
                    for (x in photoBounds.left + 20 until photoBounds.right - 20 step 19) signature = signature * 31 + image.getPixel(x, y)
                image.recycle(); return signature
            }
            val beforePan = canvasSignature()
            val touchDown = SystemClock.uptimeMillis()
            fun finger(action: Int, x: Float, y: Float) {
                val event = MotionEvent.obtain(touchDown, SystemClock.uptimeMillis(), action, x, y, 0)
                event.source = InputDevice.SOURCE_TOUCHSCREEN; uiAutomation.injectInputEvent(event, true); event.recycle()
            }
            val cx = photoBounds.exactCenterX(); val cy = photoBounds.exactCenterY()
            finger(MotionEvent.ACTION_DOWN, cx, cy)
            repeat(12) { index -> Thread.sleep(18); finger(MotionEvent.ACTION_MOVE, cx + (index + 1) * 9, cy - (index + 1) * 4) }
            finger(MotionEvent.ACTION_UP, cx + 108, cy - 48)
            Thread.sleep(250)
            verify(beforePan != canvasSignature(), "Dragging moves photo beneath stationary record text")
            val beforePinch = canvasSignature()
            val pinchDown = SystemClock.uptimeMillis()
            fun pinch(action: Int, distance: Float, count: Int = 2) {
                val props = Array(count) { i -> MotionEvent.PointerProperties().apply { id = i; toolType = MotionEvent.TOOL_TYPE_FINGER } }
                val coords = Array(count) { i -> MotionEvent.PointerCoords().apply { x = cx + if (i == 0) -distance else distance; y = cy; pressure = 1f; size = 1f } }
                val event = MotionEvent.obtain(pinchDown, SystemClock.uptimeMillis(), action, count, props, coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
                uiAutomation.injectInputEvent(event, true); event.recycle()
            }
            pinch(MotionEvent.ACTION_DOWN, 55f, 1); pinch(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 55f)
            repeat(12) { index -> Thread.sleep(18); pinch(MotionEvent.ACTION_MOVE, 55f + (index + 1) * 5) }
            pinch(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 115f); pinch(MotionEvent.ACTION_UP, 115f, 1)
            Thread.sleep(250)
            verify(beforePinch != canvasSignature(), "Two-finger pinch changes photo scale")
            uiAutomation.takeScreenshot()?.let { image -> File(root, "photo-editor-gesture.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; image.recycle() }
            val exportFile = runBlocking { com.fitlog.app.ui.workout.exportWorkoutPoster(isolated, cardSummary,
                PosterOptions(cardSummary.title, landscape.path, photoScale = 1.5f, photoX = .1f)) }
            verify(exportFile.isFile && exportFile.length() > 0, "Export produces exactly one PNG")
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                val uri = runBlocking { com.fitlog.app.ui.workout.saveWorkoutPosterToGallery(targetContext, exportFile) }
                try {
                    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    targetContext.contentResolver.openInputStream(uri).use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
                    verify(bounds.outWidth > 0 && bounds.outHeight > 0, "Saved album image can be decoded")
                    targetContext.contentResolver.query(uri, arrayOf(android.provider.MediaStore.Images.Media.IS_PENDING), null, null, null).use { cursor ->
                        verify(cursor != null && cursor.moveToFirst() && cursor.getInt(0) == 0, "Album image is published after successful write")
                    }
                } finally { targetContext.contentResolver.delete(uri, null, null) }
            }
            val shared = java.util.concurrent.atomic.AtomicReference<Intent?>()
            val monitor = object : ActivityMonitor() {
                override fun onStartActivity(intent: Intent): ActivityResult? {
                    if (intent.action == Intent.ACTION_CHOOSER) {
                        @Suppress("DEPRECATION") val nested = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                        shared.set(nested); return ActivityResult(Activity.RESULT_CANCELED, null)
                    }
                    return null
                }
            }
            addMonitor(monitor)
            try {
                click("分享")
                await("single share intent") { shared.get() != null }
                val intent = shared.get()!!
                verify(intent.action == Intent.ACTION_SEND && intent.type == "image/png" && intent.clipData?.itemCount == 1,
                    "Direct share hands one image to chooser without sending to any recipient")
            } finally { removeMonitor(monitor) }
            operation { vm.savePlan("周期列表示例", listOf(ExerciseSpec("杠铃卧推", 3), ExerciseSpec("上斜哑铃卧推", 3))) {} }
            await("plan list updated") { vm.data.value.plans.any { it.name == "周期列表示例" } }
            runOnMainSync { host.setContent { FitLogTheme {
                val nav = rememberNavController()
                NavHost(nav, startDestination = "schedule-test") { composable("schedule-test") { TrainingScheduleScreen(nav, vm, it) } }
            } } }
            await("schedule setup") { nodes("设置训练内容").isNotEmpty() }
            click("设置训练内容")
            await("schedule choices with actions") { nodes("周期列表示例").isNotEmpty() && nodes("上斜哑铃卧推").isNotEmpty() }
            val firstAction = android.graphics.Rect().also { nodes("杠铃卧推").first().getBoundsInScreen(it) }
            val secondAction = android.graphics.Rect().also { nodes("上斜哑铃卧推").first().getBoundsInScreen(it) }
            verify(firstAction.top < secondAction.top, "Cycle chooser lists plan exercises vertically")
            Thread.sleep(450)
            uiAutomation.takeScreenshot()?.let { image ->
                File(root, "schedule-choices.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                image.recycle()
            }
            runOnMainSync { host.setContent { FitLogTheme {
                val scheme = androidx.compose.material3.MaterialTheme.colorScheme
                androidx.compose.runtime.SideEffect { palette(scheme, "light") }
                NewTrainingPlanScreen(rememberNavController(), vm)
            } } }
            await("exercise filters") { nodes("全部类型").isNotEmpty() }
            Thread.sleep(500)
            uiAutomation.takeScreenshot()?.let { image ->
                File(root, "flat-controls.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                image.recycle()
            }
            vm.settings.saveBodyWeight(70.3)
            runOnMainSync { host.setContent { FitLogTheme { BodyWeightDialog(vm) {} } } }
            await("weight panel") { nodes("我的体重").isNotEmpty() && nodes("同步体重秤数据").isNotEmpty() }
            Thread.sleep(650)
            uiAutomation.takeScreenshot()?.let { image ->
                File(root, "weight-panel.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                verify(image.getPixel(20, image.height - 30) == android.graphics.Color.WHITE, "Weight sheet covers navigation area with opaque white")
                image.recycle()
            }
            val palette = listOf("GreenTint" to GreenTint, "OrangeTint" to OrangeTint, "DockGreenTint" to DockGreenTint,
                "DockOrangeTint" to DockOrangeTint, "HeatLevel2" to androidx.compose.ui.graphics.lerp(Paper, GreenAccent, .45f))
                .joinToString("\n") { (name, color) -> "$name=#${Integer.toHexString(color.toArgb()).uppercase()}" }
            File(root, "derived-palette.txt").writeText(palette)
            val original = ExerciseSpec("杠铃卧推", 12, 10, 20.0, 0)
            operation { vm.begin("参数记忆验证", listOf(original)) {} }
            val editingSession = requireNotNull(vm.session.value)
            val edited = PlanSets.update(PlanSets.explicit(original), 0, SetTarget(42.5, 13, 45))
            operation { vm.editCurrentExercise(editingSession.key, 0, edited) {} }
            verify(vm.session.value!!.current.targetAt(0) == SetTarget(42.5, 13, 45), "Current exercise uses edited per-set targets")
            val memory = com.fitlog.app.data.ExerciseMemoryStore(isolated)
            val withMetadata = original.copy(videoUrl = "https://www.bilibili.com/video/BV1kx4y1U714/", iconPath = "kept")
            verify(memory.apply(withMetadata).targetAt(0) == SetTarget(42.5, 13, 45) && memory.apply(withMetadata).iconPath == "kept",
                "Remembered targets survive store reload and preserve exercise metadata")
            verify(memory.apply(original.copy(isCardio = true)).weight == original.weight, "Different exercise types do not share targets")
            runOnMainSync { host.setContent { FitLogTheme { com.fitlog.app.ui.workout.CurrentExerciseSheet(
                requireNotNull(vm.session.value), false, {}, vm, openTutorial = true) } } }
            await("direct tutorial section") { nodes("观看动作讲解").any { it.isVisibleToUser } }
            val tutorialBounds = android.graphics.Rect().also { nodes("观看动作讲解").first().getBoundsInScreen(it) }
            verify(tutorialBounds.top > 0 && tutorialBounds.bottom < targetContext.resources.displayMetrics.heightPixels,
                "Tutorial entry locates the bottom section without manual scrolling")
            operation { vm.discard {} }
            operation { vm.begin("下次训练", listOf(original)) {} }
            verify(vm.session.value!!.current.targetAt(0) == SetTarget(42.5, 13, 45), "Future training restores remembered targets")
            operation { vm.adjust(reps = 1) }
            verify(vm.rememberedExercise(original).targetAt(0).reps == 14, "Quick repetition adjustment updates memory")
            operation { vm.discard {} }
            freshVm()
            verify(vm.rememberedExercise(original).targetAt(0).reps == 14, "Memory survives ViewModel recreation")
            runOnMainSync { host.setContent { FitLogTheme { NewTrainingPlanScreen(rememberNavController(), vm) } } }
            await("new plan library") { nodes("杠铃卧推").isNotEmpty() }
            click("杠铃卧推")
            waitForIdleSync(); Thread.sleep(400)
            await("exercise selected") { nodes("已选 1 个动作").isNotEmpty() }
            click("查看概览")
            await("new plan remembered targets") { nodes("42.5").isNotEmpty() }
            verify(nodes("42.5").isNotEmpty() && nodes("14").isNotEmpty(), "New plan selections use most recent targets")
            runOnMainSync { host.setContent { FitLogTheme {
                com.fitlog.app.ui.components.StepControl("次", "12", {}, {})
            } } }
            await("step control") { nodes("12").isNotEmpty() }
            val minusBounds = android.graphics.Rect().also { nodes("−").first().getBoundsInScreen(it) }
            val plusBounds = android.graphics.Rect().also { nodes("+").first().getBoundsInScreen(it) }
            val valueBounds = android.graphics.Rect().also { nodes("12").first().getBoundsInScreen(it) }
            verify(kotlin.math.abs(valueBounds.centerX() - (minusBounds.centerX() + plusBounds.centerX()) / 2) < 4,
                "Repetition value is centered between plus and minus")
            verify(count() == 5, "Parameter memory does not rewrite training history")
            val managed = ExerciseSpec("管理测试动作", 2, 8, 17.5, 60, muscleGroup = "胸")
            operation { vm.saveCustomExercise(managed) {} }
            await("created library action") { vm.exerciseLibrary.value.any { it.name == managed.name } }
            verify(vm.exerciseLibrary.value.single { it.name == managed.name }.weight == 17.5, "Create action appears in unified library")
            val renamed = managed.copy(name = "管理编辑动作", weight = 22.5)
            operation { vm.saveCustomExercise(renamed, managed.name) {} }
            await("renamed action") { vm.exerciseLibrary.value.any { it.name == renamed.name } }
            verify(vm.exerciseLibrary.value.none { it.name == managed.name }, "Rename replaces original action without duplicates")
            verify(com.fitlog.app.data.CustomExerciseStore(isolated).library().single { it.name == renamed.name }.weight == 22.5,
                "Edited custom action persists")
            val bench = vm.exerciseLibrary.value.single { it.name == "杠铃卧推" }
            operation { vm.saveCustomExercise(bench.copy(muscleGroup = "胸、肩"), bench.name) {} }
            await("builtin edit") { vm.exerciseLibrary.value.any { it.name == bench.name && it.muscleGroup == "胸、肩" } }
            verify(vm.exerciseLibrary.value.count { it.name == bench.name } == 1, "Builtin edit replaces rather than duplicates its library entry")
            val managementStore = com.fitlog.app.data.CustomExerciseStore(isolated)
            verify(runCatching { runBlocking { managementStore.save(renamed.copy(name = "杠铃卧推"), renamed.name) } }.isFailure,
                "Rename to an existing action is rejected")
            runOnMainSync { host.setContent { FitLogTheme {
                com.fitlog.app.ui.workout.ExerciseManagementScreen(rememberNavController(), vm)
            } } }
            await("management screen") { nodes("动作管理").isNotEmpty() && nodes("新增动作").isNotEmpty() }
            click("新增动作")
            await("new action form") { nodes("动作名称").isNotEmpty() }
            verify(nodes("动作名称").isNotEmpty(), "Management plus opens shared action editor")
            // Dismiss form without creating a draft, then verify the actual edit/delete controls.
            ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("input keyevent 4")).use { it.readBytes() }
            await("action form dismissed") { nodes("动作名称").isEmpty() }
            await("management edit available") { nodes("编辑杠铃卧推").isNotEmpty() }
            waitForIdleSync(); Thread.sleep(450)
            click("编辑杠铃卧推")
            await("edit action form") { nodes("编辑动作").isNotEmpty() }
            verify(nodes("编辑动作").isNotEmpty() && nodes("杠铃卧推").isNotEmpty(), "Management edit preloads selected action")
            ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("input keyevent 4")).use { it.readBytes() }
            await("edit dismissed") { nodes("动作名称").isEmpty() }
            await("management delete available") { nodes("删除杠铃卧推").isNotEmpty() }
            waitForIdleSync(); Thread.sleep(450)
            click("删除杠铃卧推")
            await("delete confirmation") { nodes("从动作库移除").isNotEmpty() }
            verify(nodes("已有训练计划和历史记录会保留").isNotEmpty(), "Delete requires confirmation with retained-plan explanation")
            click("取消")
            operation { vm.deleteExercise(bench) {} }
            await("builtin deletion") { vm.exerciseLibrary.value.none { it.name == bench.name } }
            verify(com.fitlog.app.data.CustomExerciseStore(isolated).library().none { it.name == bench.name },
                "Deleted builtin remains removed after reload")
            val retainedPlan = vm.data.value.plans.first { it.name == "周期列表示例" }
            verify(runBlocking { db.workouts().exercises(retainedPlan.id) }.any { it.name == bench.name },
                "Deleting a library action preserves saved plan exercises")
            operation { vm.deleteExercise(renamed) {} }
            await("custom deletion") { vm.exerciseLibrary.value.none { it.name == renamed.name } }
            verify(com.fitlog.app.data.CustomExerciseStore(isolated).library().none { it.name == renamed.name }, "Custom action deletion persists")
            verify(count() == 5, "Action CRUD does not rewrite workout history")
            val run = ExerciseSpec("跑步", 1, 1, 0.0, 0, isCardio = true,
                cardioTarget = CardioTarget("距离", 0, 5000.0, training = "continuous"))
            operation { vm.begin("连续跑步验证", listOf(run)) {} }
            runOnMainSync { host.setContent { FitLogTheme(workout = true) { WorkoutSessionScreen(rememberNavController(), vm) } } }
            await("continuous cardio UI") { nodes("连续运动").isNotEmpty() && nodes("暂停").isNotEmpty() }
            Thread.sleep(1100)
            click("暂停")
            await("paused cardio") { vm.session.value?.pausedAt != null }
            val pausedSession = requireNotNull(vm.session.value)
            val pausedSeconds = pausedSession.segmentSeconds(System.currentTimeMillis())
            Thread.sleep(600)
            verify(vm.session.value!!.segmentSeconds(System.currentTimeMillis()) == pausedSeconds, "Cardio pause freezes elapsed time")
            val checkpoint = requireNotNull(SessionStore(isolated).load())
            verify(checkpoint.pausedAt == pausedSession.pausedAt && checkpoint.current.cardioTarget.training == "continuous",
                "Paused cardio and explicit training mode survive checkpoint reload")
            verify(nodes("继续运动").isNotEmpty(), "Paused cardio exposes resume control")
            click("继续运动")
            await("cardio resumed") { vm.session.value?.pausedAt == null }
            val backgroundAt = vm.session.value!!.segmentSeconds(System.currentTimeMillis())
            runOnMainSync { host.moveTaskToBack(true) }
            Thread.sleep(1300)
            verify(vm.session.value!!.segmentSeconds(System.currentTimeMillis()) >= backgroundAt + 1,
                "Cardio elapsed time remains accurate while the Activity is backgrounded")
            runOnMainSync { host.startActivity(Intent(host, WorkoutValidationActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) }
            await("cardio window restored") { host.hasWindowFocus() && nodes("结束本项").isNotEmpty() }
            Thread.sleep(400)
            uiAutomation.takeScreenshot()?.let { image ->
                File(root, "cardio-continuous.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; image.recycle()
            }
            click("结束本项")
            await("single cardio review") { vm.session.value?.pendingCardioReview == 0 && nodes("实际距离").isNotEmpty() }
            verify(count() == 5, "Cardio waits for actual-data confirmation before saving")
            val actualDistance = nodes("").firstOrNull { it.isEditable && it.text?.toString() == "5" }
            verify(actualDistance != null, "Five-kilometer goal is prefilled in actual distance field")
            verify(actualDistance!!.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "3.2")
            }), "Actual distance can be corrected")
            waitForIdleSync(); Thread.sleep(400)
            click("保存训练")
            await("continuous workout saved") { vm.summary.value?.cardioDistance == 3200.0 }
            verify(count() == 6 && vm.summary.value!!.cardioSeconds >= 1, "Corrected continuous record saves once with active time")
            operation { vm.confirmCardio(10, 3200.0, 0) }
            verify(count() == 6, "Repeated cardio confirmation cannot duplicate a record")
            val intervalSpec = ExerciseSpec("开合跳", 2, 20, 0.0, 1, isCardio = true,
                cardioTarget = CardioTarget("计时", 2, training = "interval"))
            operation { vm.begin("间歇验证", listOf(intervalSpec)) {} }
            runOnMainSync { host.setContent { FitLogTheme(workout = true) { WorkoutSessionScreen(rememberNavController(), vm) } } }
            await("automatic interval rest") { vm.session.value?.completed?.size == 1 && vm.session.value?.restUntil != null }
            operation { vm.pauseCardio() }
            await("paused rest UI") { nodes("跳过休息").isNotEmpty() }
            verify(vm.session.value?.pausedAt != null && nodes("跳过休息").isNotEmpty(), "Interval rest can be paused")
            val pausedRest = vm.session.value!!.restSeconds(System.currentTimeMillis())
            Thread.sleep(500)
            verify(vm.session.value!!.restSeconds(System.currentTimeMillis()) == pausedRest, "Paused rest remains frozen")
            operation { vm.pauseCardio() }
            await("interval completion review") { vm.session.value?.pendingCardioReview == 0 && nodes("实际距离").isNotEmpty() }
            val intervalDone = requireNotNull(vm.session.value)
            verify(intervalDone.completed.size == 2 && intervalDone.cardioSeconds == 4 && count() == 6,
                "Automatic intervals finish exact work time without per-round forms or premature saving")
            verify(SessionStore(isolated).load()?.pendingCardioReview == 0, "Completed interval review survives checkpoint reload")
            click("保存训练")
            await("interval save") { vm.summary.value?.title == "间歇验证" }
            verify(count() == 7 && vm.summary.value!!.cardioRounds == 2 && vm.summary.value!!.cardioSeconds == 4,
                "One interval confirmation saves all rounds")
            operation { vm.begin("混合验证", listOf(intervalSpec.copy(restSeconds = 0,
                cardioTarget = intervalSpec.cardioTarget.copy(durationSeconds = 1)), ExerciseSpec("混合卧推", 1, 10, 20.0, 0))) {} }
            runOnMainSync { host.setContent { FitLogTheme(workout = true) { WorkoutSessionScreen(rememberNavController(), vm) } } }
            await("mixed cardio review") { vm.session.value?.pendingCardioReview == 0 }
            verify(vm.session.value!!.index == 1 && vm.session.value!!.pausedAt != null, "Mixed workout does not start next exercise before cardio review")
            operation { vm.confirmCardio(2, 0.0, 0) }
            verify(vm.session.value!!.current.name == "混合卧推" && vm.session.value!!.segmentSeconds(System.currentTimeMillis()) <= 1,
                "Mixed workout continues strength with a fresh timer")
            operation { vm.discard {} }
            val input = java.util.concurrent.atomic.AtomicReference<ExerciseSpec?>()
            runOnMainSync { host.setContent { FitLogTheme {
                androidx.compose.foundation.layout.Column { com.fitlog.app.ui.workout.CardioInputFields(run) { input.set(it) } }
            } } }
            await("cardio configuration") { nodes("自由计时").isNotEmpty() }
            click("自由计时")
            await("free configuration") { input.get()?.cardioTarget?.mode == "自由" }
            verify(input.get()!!.sets == 1 && input.get()!!.restSeconds == 0 && nodes("轮数").isEmpty(),
                "Continuous configuration hides rounds/rest and produces one continuous exercise")
            verify(input.get()!!.cardioTarget.distanceMeters == 0.0, "Switching to free mode clears hidden distance goal")
            val swimming = run.copy(name = "游泳", cardioTarget = CardioTarget("距离", 0, 1000.0, 25.0, "自由泳", "continuous"))
            runOnMainSync { host.setContent { FitLogTheme {
                androidx.compose.foundation.layout.Column { com.fitlog.app.ui.workout.CardioInputFields(swimming) {} }
            } } }
            await("swimming settings") { nodes("25 米池").isNotEmpty() }
            verify(nodes("泳池长度").isNotEmpty() && nodes("泳姿").isNotEmpty(), "Swimming exposes dedicated pool and stroke settings")
            operation { vm.begin("休息回退验证", listOf(ExerciseSpec("卧推", 2, 10, 20.0, 60))) {} }
            operation { vm.completeSet() }
            val originalRest = vm.session.value!!.restSeconds(System.currentTimeMillis())
            operation { vm.adjustRest(15) }
            verify(vm.session.value!!.restSeconds(System.currentTimeMillis()) > originalRest + 10, "Rest plus button extends live time")
            freshVm()
            await("rest adjustment undo checkpoint") { vm.undoLabel.value == "调整本次休息" }
            operation { vm.undo() }
            verify(kotlin.math.abs(vm.session.value!!.restSeconds(System.currentTimeMillis()) - originalRest) <= 2,
                "Rest adjustment undo survives ViewModel recreation")
            operation { vm.adjustRest(-15) }; operation { vm.undo() }
            verify(kotlin.math.abs(vm.session.value!!.restSeconds(System.currentTimeMillis()) - originalRest) <= 2,
                "Rest minus button can be undone independently")
            operation { vm.discard {} }
            var completePlan = 0L
            operation { vm.savePlan("今日完成验证", listOf(ExerciseSpec("卧推", 1, 10, 20.0, 0)), done = { completePlan = it }) }
            val todayDate = java.time.LocalDate.now()
            val completeKey = "plan:$completePlan"
            operation { vm.saveSchedule("week", List(7) { completeKey }, emptyList(), todayDate.toEpochDay()) {} }
            verify(!runBlocking { vm.todayPlanCompleted(completeKey, todayDate) }, "New daily plan starts incomplete")
            operation { vm.beginPlan(ScheduleCatchUp.route(completeKey, todayDate.toEpochDay(), false)) {} }
            operation { vm.completeSet() }
            verify(vm.summary.value?.complete == true && vm.preferences.value.scheduleCompletedKey == completeKey,
                "Saving final set marks scheduled plan completed")
            freshVm()
            verify(runBlocking { vm.todayPlanCompleted(completeKey, todayDate) }, "Daily completion persists across reopening")
            operation { vm.beginPlan(ScheduleCatchUp.route(completeKey, todayDate.toEpochDay(), false)) {} }
            verify(vm.session.value == null, "Daily start route cannot restart a completed plan")
            runOnMainSync { host.setContent { FitLogTheme { AppNavHost(vm) } } }
            await("completed home state") { nodes("今日计划已完成").isNotEmpty() }
            click("今日计划已完成")
            await("completed daily plan opens details") { nodes("编辑分享卡片").isNotEmpty() }
            verify(vm.session.value == null, "Completed daily entry views records rather than starting training")
            report.putString("stream", "$assertions production ViewModel / Room autosave assertions passed; user DB untouched\n")
            report.putString("rendering", root.path)
        } catch (error: Throwable) {
            if (activity != null) runCatching {
                uiAutomation.takeScreenshot()?.let { image ->
                    File(root, "failure.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                    image.recycle()
                }
            }
            report.putString("stream", "FAILED: ${error.stackTraceToString()}\n${trace.joinToString("\n")}\nFixture: ${root.path}\n")
            resultCode = Activity.RESULT_CANCELED
        } finally {
            runOnMainSync { activity?.finish() }
            runOnMainSync { owner.viewModelStore.clear() }
            runCatching { db.close() }
        }
        finish(resultCode, report)
    }
}
