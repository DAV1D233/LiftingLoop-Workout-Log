package com.fitlog.app.ui.home

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

/** Updated after composition, without replacing the stable parent input node. */
internal class DockDragController {
    var enabled = false
    var active = false
    var sectionAt: (Offset) -> DockSection? = { null }
    var tap: (DockSection) -> Unit = {}
    var hold: (DockSection) -> Unit = {}
    var hover: (Offset) -> Unit = {}
    var press: (DockSection?) -> Unit = {}
    var begin: () -> Unit = {}
    var travel: (Float) -> Unit = {}
    var end: (Boolean) -> Unit = {}
}

/** One root-coordinate stream owns a Dock press from DOWN through UP, even when
 * its finger leaves the circle or opening the menu changes the child hit tree.
 * Outside presses remain normal child taps until they become a menu-selection drag. */
internal fun Modifier.dockSelectionGesture(controller: DockDragController): Modifier =
    pointerInput(controller) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (!controller.enabled) return@awaitEachGesture
            val section = controller.sectionAt(down.position)
            if (section != null) {
                var held = false
                var released = false
                var cancelled = false
                var earlyUp = false
                var position = down.position
                down.consume()
                controller.press(section)
                try {
                    // Observe early travel in the home coordinate space, not the 64dp circle.
                    // A finger leaving that circle must not silently cancel a pending hold.
                    val early = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        while (!earlyUp && !cancelled) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (!controller.enabled || change == null || change.isConsumed ||
                                event.changes.any { it.id != down.id && it.pressed }) {
                                cancelled = true
                            } else {
                                position = change.position
                                earlyUp = !change.pressed
                                change.consume()
                            }
                        }
                        true
                    }
                    if (early == true) {
                        if (earlyUp && !cancelled && controller.sectionAt(position) == section) controller.tap(section)
                    } else {
                        held = true
                        controller.hold(section)
                        controller.hover(position)
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (!controller.enabled || change == null || change.isConsumed ||
                                event.changes.any { it.id != down.id && it.pressed }) break
                            controller.hover(change.position)
                            change.consume()
                            if (!change.pressed) { released = true; break }
                        }
                    }
                } finally {
                    controller.press(null)
                    if (held) controller.end(released)
                }
                return@awaitEachGesture
            }
            if (!controller.active) return@awaitEachGesture
            var dragging = false
            var confirmed = false
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!controller.active || !controller.enabled || event.changes.any { it.id != down.id && it.pressed }) break
                    val upward = down.position.y - change.position.y
                    if (!dragging && abs(upward) > viewConfiguration.touchSlop) {
                        dragging = true
                        controller.begin()
                    }
                    if (dragging) {
                        change.consume()
                        controller.travel(upward)
                    }
                    if (!change.pressed) { confirmed = dragging; break }
                }
            } finally {
                if (dragging) controller.end(confirmed)
            }
        }
    }
