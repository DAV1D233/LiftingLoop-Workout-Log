package com.fitlog.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import com.fitlog.app.ui.glass.LocalGlassEnvironment

/** One interruptible spring, shared with the control's real input source. No added touch layer. */
@Composable
fun Modifier.motionPress(source: MutableInteractionSource, enabled: Boolean, compression: Float = .97f): Modifier {
    val pressed by source.collectIsPressedAsState()
    val reduced = LocalGlassEnvironment.current.reduceMotion
    val scale by animateFloatAsState(if (enabled && pressed && !reduced) compression else 1f,
        if (reduced) snap() else spring(dampingRatio = .72f, stiffness = 650f), label = "control press")
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

@Composable
fun motionColor(target: Color): Color = animateColorAsState(target,
    if (LocalGlassEnvironment.current.reduceMotion) snap() else tween(160), label = "control state color").value

@Composable
fun Modifier.motionClickable(enabled: Boolean = true, onClickLabel: String? = null,
    role: Role? = null, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    return motionPress(source, enabled, .985f).clickable(source, LocalIndication.current,
        enabled, onClickLabel, role, onClick)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.motionCombinedClickable(enabled: Boolean = true, onClickLabel: String? = null,
    onLongClickLabel: String? = null, onLongClick: (() -> Unit)? = null, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    return motionPress(source, enabled, .985f).combinedClickable(interactionSource = source,
        indication = LocalIndication.current, enabled = enabled, onClickLabel = onClickLabel,
        onLongClickLabel = onLongClickLabel, onLongClick = onLongClick, onClick = onClick)
}
