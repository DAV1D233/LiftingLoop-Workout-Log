package com.fitlog.app.ui.glass

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.fitlog.app.domain.MotionPolicy
import com.fitlog.app.domain.GlassPolicy

@Composable
fun GlassEnvironmentProvider(reduceMotion: Boolean, reduceTransparency: Boolean, dark: Boolean,
    livelyMotion: Boolean = true, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val owner = LocalLifecycleOwner.current
    val power = remember(context) { context.getSystemService(PowerManager::class.java) }
    val lowRam = remember(context) { context.getSystemService(ActivityManager::class.java).isLowRamDevice }
    var powerSave by remember { mutableStateOf(power.isPowerSaveMode) }
    var thermal by remember { mutableIntStateOf(0) }
    var hardware by remember { mutableStateOf(view.isHardwareAccelerated) }
    var foreground by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var systemMotionOff by remember { mutableStateOf(false) }
    fun refresh() {
        powerSave = power.isPowerSaveMode
        hardware = view.isHardwareAccelerated
        if (Build.VERSION.SDK_INT >= 29) thermal = power.currentThermalStatus
        systemMotionOff = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    LaunchedEffect(view) { withFrameNanos { }; withFrameNanos { }; refresh() }
    DisposableEffect(context, owner, view) {
        val focusListener = ViewTreeObserver.OnWindowFocusChangeListener { focused -> if (focused) view.post { refresh() } }
        val attachListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(attached: View) { attached.post { refresh() } }
            override fun onViewDetachedFromWindow(detached: View) { hardware = false }
        }
        val tree = view.viewTreeObserver
        tree.addOnWindowFocusChangeListener(focusListener)
        view.addOnAttachStateChangeListener(attachListener)
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { refresh() }
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { refresh() }
        }
        val lifecycle = LifecycleEventObserver { _, event ->
            foreground = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        val thermalListener = if (Build.VERSION.SDK_INT >= 29) PowerManager.OnThermalStatusChangedListener { thermal = it } else null
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        ContextCompat.registerReceiver(context, receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        owner.lifecycle.addObserver(lifecycle)
        if (Build.VERSION.SDK_INT >= 29 && thermalListener != null) power.addThermalStatusListener(context.mainExecutor, thermalListener)
        refresh()
        onDispose {
            if (tree.isAlive) tree.removeOnWindowFocusChangeListener(focusListener)
            view.removeOnAttachStateChangeListener(attachListener)
            context.contentResolver.unregisterContentObserver(observer)
            context.unregisterReceiver(receiver)
            owner.lifecycle.removeObserver(lifecycle)
            if (Build.VERSION.SDK_INT >= 29 && thermalListener != null) power.removeThermalStatusListener(thermalListener)
        }
    }
    val quality = GlassPolicy.quality(Build.VERSION.SDK_INT, hardware, lowRam, powerSave, thermal >= 3, reduceTransparency, false)
    val restriction = GlassPolicy.restriction(Build.VERSION.SDK_INT, hardware, lowRam, powerSave, thermal >= 3, reduceTransparency, false)
    LaunchedEffect(quality, restriction) {
        Log.i("FitLogGlass", "Material mode=$quality restriction=$restriction api=${Build.VERSION.SDK_INT}")
    }
    CompositionLocalProvider(LocalGlassEnvironment provides GlassEnvironment(quality, !MotionPolicy.enabled(livelyMotion, reduceMotion, systemMotionOff, foreground), dark, restriction), content = content)
}
