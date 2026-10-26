package com.fitlog.app.ui

import android.app.Activity
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.fitlog.app.feedback.FeedbackPlayer
import com.fitlog.app.ui.calendar.CalendarScreen
import com.fitlog.app.ui.diet.DietHistoryScreen
import com.fitlog.app.ui.diet.RecordFoodScreen
import com.fitlog.app.ui.home.HomeScreen
import com.fitlog.app.ui.settings.SettingsScreen
import com.fitlog.app.ui.settings.AboutScreen
import com.fitlog.app.ui.theme.FitLogTheme
import com.fitlog.app.ui.glass.LocalGlassEnvironment
import com.fitlog.app.ui.glass.GlassBackdropHost
import com.fitlog.app.ui.glass.GlassEnvironmentProvider
import com.fitlog.app.ui.workout.*
import kotlinx.coroutines.launch

object Routes {
    const val HOME = "home"
    const val PLANS = "plans"
    const val PLAN_EDITOR = "editor?mode=plan"
    const val SCHEDULE_DAY_EDITOR = "editor?mode=schedule"
    const val FREE = "free-training"
    const val PLAN_PICKER = "plan-picker"
    const val PLAN_LIBRARY = "training-plans"
    const val EXERCISES = "exercise-management"
    const val PLAN_EDIT = "edit-plan/{planId}"
    fun editPlan(id: Long) = "edit-plan/$id"
    const val SESSION = "session"
    const val PREVIEW = "training-preview?key={key}"
    const val COUNTDOWN = "training-countdown?key={key}"
    fun preview(key: String) = "training-preview?key=${android.net.Uri.encode(key)}"
    fun countdown(key: String = "") = "training-countdown?key=${android.net.Uri.encode(key)}"
    const val HISTORY = "history"
    const val CALENDAR = "calendar"
    const val FOOD = "food?mode=manual"
    const val FOOD_HISTORY = "food-history"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
}

@Composable
fun AppNavHost(vm: AppViewModel) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val workout = entry?.destination?.route in listOf(Routes.SESSION, Routes.COUNTDOWN)
    val context = LocalContext.current
    val view = LocalView.current
    val prefs by vm.preferences.collectAsState()
    val currentPreferences by rememberUpdatedState(prefs)
    val snackbar = remember { SnackbarHostState() }
    val player = remember { FeedbackPlayer() }
    val scope = rememberCoroutineScope()
    DisposableEffect(player) { onDispose { player.close() } }
    SideEffect {
        val window = (context as Activity).window
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !workout
            isAppearanceLightNavigationBars = !workout
        }
    }
    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is UiEvent.Feedback -> player.play(event.cue, view, currentPreferences)
                is UiEvent.Message -> scope.launch { snackbar.showSnackbar(event.text) }
            }
        }
    }
    FitLogTheme(workout) {
        GlassEnvironmentProvider(prefs.reduceMotion, prefs.reduceTransparency, workout, livelyMotion = prefs.livelyMotion) {
        val reducedMotion = LocalGlassEnvironment.current.reduceMotion
        GlassBackdropHost {
            NavHost(nav, Routes.HOME,
                enterTransition = { fadeIn(tween(if (reducedMotion) 0 else 220)) +
                    slideInHorizontally(tween(if (reducedMotion) 0 else 260, easing = FastOutSlowInEasing)) { if (reducedMotion) 0 else it / 16 } },
                exitTransition = { fadeOut(tween(if (reducedMotion) 0 else 180)) +
                    slideOutHorizontally(tween(if (reducedMotion) 0 else 260, easing = FastOutSlowInEasing)) { if (reducedMotion) 0 else -it / 32 } },
                popEnterTransition = { fadeIn(tween(if (reducedMotion) 0 else 220)) +
                    slideInHorizontally(tween(if (reducedMotion) 0 else 260, easing = FastOutSlowInEasing)) { if (reducedMotion) 0 else -it / 32 } },
                popExitTransition = { fadeOut(tween(if (reducedMotion) 0 else 180)) +
                    slideOutHorizontally(tween(if (reducedMotion) 0 else 260, easing = FastOutSlowInEasing)) { if (reducedMotion) 0 else it / 16 } }) {
                composable(Routes.HOME) { HomeScreen(nav, vm) }
                composable(Routes.PLANS) { PlansScreen(nav, vm, scheduleEntry = it) }
                composable(Routes.FREE) { FreeTrainingMenuScreen(nav) }
                composable(Routes.PLAN_PICKER) { PlansScreen(nav, vm, selecting = true) }
                composable(Routes.PLAN_LIBRARY) { PlansScreen(nav, vm, managing = true) }
                composable(Routes.EXERCISES) { ExerciseManagementScreen(nav, vm) }
                composable(Routes.PLAN_EDIT, arguments = listOf(navArgument("planId") { type = androidx.navigation.NavType.LongType })) {
                    NewTrainingPlanScreen(nav, vm, editPlanId = requireNotNull(it.arguments).getLong("planId"))
                }
                composable("editor?mode={mode}", arguments = listOf(navArgument("mode") { defaultValue = "plan" })) {
                    if (it.arguments?.getString("mode") == "free") FreeTrainingMenuScreen(nav)
                    else NewTrainingPlanScreen(nav, vm, attachToSchedule = it.arguments?.getString("mode") == "schedule")
                }
                composable(Routes.SESSION) { WorkoutSessionScreen(nav, vm) }
                composable(Routes.PREVIEW, arguments = listOf(navArgument("key") { defaultValue = "" })) {
                    TrainingPreviewScreen(nav, vm, it.arguments?.getString("key").orEmpty())
                }
                composable(Routes.COUNTDOWN, arguments = listOf(navArgument("key") { defaultValue = "" })) {
                    TrainingCountdownScreen(nav, vm, it.arguments?.getString("key").orEmpty())
                }
                composable(Routes.HISTORY) { WorkoutHistoryScreen(nav, vm) }
                composable(Routes.CALENDAR) { CalendarScreen(nav, vm) }
                composable("food?mode={mode}", arguments = listOf(navArgument("mode") { defaultValue = "manual" })) {
                    RecordFoodScreen(nav, vm)
                }
                composable(Routes.FOOD_HISTORY) { DietHistoryScreen(nav, vm) }
                composable(Routes.SETTINGS) { SettingsScreen(nav, vm) }
                composable(Routes.ABOUT) { AboutScreen { nav.popBackStack() } }
            }
            SnackbarHost(snackbar, Modifier.align(androidx.compose.ui.Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing))
        }
        }
    }
}
