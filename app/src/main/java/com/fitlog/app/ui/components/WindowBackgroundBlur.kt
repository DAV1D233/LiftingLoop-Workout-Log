package com.fitlog.app.ui.components

import android.os.Build
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider

/** Blur only the page behind the modal window, never its text or controls.
 * Android 8–11 and devices disabling cross-window blur retain the modal's dim scrim. */
@Composable
internal fun WindowBackgroundBlur(enabled: Boolean) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) NativeWindowBackgroundBlur(enabled)
}

@RequiresApi(Build.VERSION_CODES.S)
@Composable
private fun NativeWindowBackgroundBlur(enabled: Boolean) {
    val view = LocalView.current
    val radius = with(LocalDensity.current) { 12.dp.roundToPx() }
    DisposableEffect(view, radius, enabled) {
        val window = (view.parent as? DialogWindowProvider)?.window
        val previousRadius = window?.attributes?.blurBehindRadius ?: 0
        val previouslyEnabled = window?.attributes?.flags?.and(WindowManager.LayoutParams.FLAG_BLUR_BEHIND) != 0
        if (window != null && enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes = window.attributes.apply { blurBehindRadius = radius }
        }
        onDispose {
            if (window != null && enabled) {
                window.attributes = window.attributes.apply { blurBehindRadius = previousRadius }
                if (!previouslyEnabled) window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            }
        }
    }
}
