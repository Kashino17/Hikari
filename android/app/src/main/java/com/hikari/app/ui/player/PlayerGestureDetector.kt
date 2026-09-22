package com.hikari.app.ui.player

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.hypot

/** Vertikaler Wisch: links Helligkeit, rechts Lautstärke. */
enum class DragSide { Left, Right }

/**
 * Ein einziger Detektor für die Videofläche, damit Tipp, Doppeltipp,
 * vertikales Wischen und Pinch sich nicht gegenseitig verschlucken.
 *
 * Verhalten (bewusst träge, damit nichts „aus Versehen" passiert):
 *  - Tipp: erst nach [doubleTapWindowMs] ohne zweiten Tipp gemeldet.
 *  - Doppeltipp: zwei Tipps innerhalb des Fensters, nahe beieinander.
 *  - Vertikaler Wisch: ab [dragSlop]; die Richtung muss klar vertikal sein
 *    (|dy| > 1.5·|dx|), sonst wird die Geste ignoriert.
 *  - Pinch: zweiter Finger → Skalierung relativ zum Startabstand.
 */
suspend fun PointerInputScope.detectPlayerGestures(
    onTap: (Offset) -> Unit,
    onDoubleTap: (Offset) -> Unit,
    onVerticalDragStart: (DragSide) -> Unit,
    onVerticalDrag: (side: DragSide, deltaY: Float) -> Unit,
    onVerticalDragEnd: () -> Unit,
    onPinch: (scale: Float) -> Unit,
    doubleTapWindowMs: Long = 260L,
) {
    val slopPx = dragSlop.toPx()
    var lastTapAt = 0L
    var lastTapPos: Offset? = null

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        // Ein Kind (Button, Leiste, Scrim) hat den Druck schon beansprucht —
        // dann ist das keine Flächengeste. Sonst klappte bei jedem Knopfdruck
        // auch die Bedienleiste weg.
        if (down.isConsumed) {
            while (true) {
                val ev = awaitPointerEvent(PointerEventPass.Main)
                if (ev.changes.none { it.pressed }) break
            }
            return@awaitEachGesture
        }
        val downPos = down.position
        val downAt = down.uptimeMillis
        val width = size.width.toFloat()

        var mode = Mode.Undecided
        var side = DragSide.Right
        var pinchStartDist = 0f
        var pinchScale = 1f
        var lastEventAt = downAt

        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Main)
            val pressed = event.changes.filter { it.pressed }

            // Zweiter Finger → Pinch, egal was vorher war.
            if (pressed.size >= 2) {
                val a = pressed[0].position
                val b = pressed[1].position
                val dist = hypot(a.x - b.x, a.y - b.y)
                if (mode != Mode.Pinch) {
                    mode = Mode.Pinch
                    pinchStartDist = dist.coerceAtLeast(1f)
                } else {
                    pinchScale = dist / pinchStartDist
                }
                event.changes.forEach { it.consume() }
                continue
            }

            val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull()
            if (change == null) break
            lastEventAt = change.uptimeMillis
            if (mode == Mode.Undecided && change.isConsumed) mode = Mode.Ignored

            when (mode) {
                Mode.Undecided -> {
                    val dx = change.position.x - downPos.x
                    val dy = change.position.y - downPos.y
                    if (abs(dy) > slopPx && abs(dy) > abs(dx) * 1.5f) {
                        mode = Mode.VerticalDrag
                        side = if (downPos.x < width / 2f) DragSide.Left else DragSide.Right
                        onVerticalDragStart(side)
                        change.consume()
                    } else if (abs(dx) > slopPx * 2f) {
                        // Horizontales Wischen ist bewusst KEINE Spul-Geste
                        // (zu leicht ausgelöst). Geste beenden.
                        mode = Mode.Ignored
                    }
                }
                Mode.VerticalDrag -> {
                    val moved = change.positionChange()
                    if (moved.y != 0f) onVerticalDrag(side, moved.y)
                    change.consume()
                }
                else -> Unit
            }

            if (!change.pressed) break
        }

        when (mode) {
            Mode.VerticalDrag -> onVerticalDragEnd()
            Mode.Pinch -> onPinch(pinchScale)
            Mode.Ignored -> Unit
            Mode.Undecided -> {
                // Kurzer Tipp ohne Bewegung.
                val now = lastEventAt
                val prevPos = lastTapPos
                val isDouble = prevPos != null &&
                    now - lastTapAt <= doubleTapWindowMs + 220L &&
                    hypot(prevPos.x - downPos.x, prevPos.y - downPos.y) < slopPx * 6f
                if (isDouble) {
                    lastTapAt = 0L
                    lastTapPos = null
                    onDoubleTap(downPos)
                } else {
                    lastTapAt = now
                    lastTapPos = downPos
                    // Warten, ob ein zweiter Tipp folgt; sonst als Einzeltipp melden.
                    val second = withTimeoutOrNull(doubleTapWindowMs) {
                        awaitFirstDown(requireUnconsumed = false)
                    }
                    if (second == null) {
                        onTap(downPos)
                    } else {
                        // Zweiter Tipp: bis zum Loslassen warten, dann Doppeltipp.
                        while (true) {
                            val ev = awaitPointerEvent(PointerEventPass.Main)
                            ev.changes.forEach { it.consume() }
                            if (ev.changes.none { it.pressed }) break
                        }
                        lastTapAt = 0L
                        lastTapPos = null
                        onDoubleTap(second.position)
                    }
                }
            }
        }
    }
}

private enum class Mode { Undecided, VerticalDrag, Pinch, Ignored }

private val dragSlop = 14.dp
