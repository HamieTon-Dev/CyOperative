package com.cyberoperative.game.platform

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import kotlin.math.sqrt

/**
 * PC keyboard state. The window forwards key events here; the game screen reads
 * [GameKeys] every frame. WASD and the arrow keys move.
 */
object DesktopInput {
    private val held = HashSet<Key>()

    /** True if the key is one the game uses (consumed so it doesn't reach text fields twice). */
    fun onKey(e: KeyEvent): Boolean {
        if (e.key !in MOVE_KEYS) return false
        when (e.type) {
            KeyEventType.KeyDown -> held += e.key
            KeyEventType.KeyUp -> held -= e.key
        }
        return false
    }

    /** Window lost focus: let go of everything so the operative doesn't keep walking. */
    fun releaseAll() = held.clear()

    internal fun axis(): Pair<Float, Float> {
        var x = 0f
        var y = 0f
        if (Key.A in held || Key.DirectionLeft in held) x -= 1f
        if (Key.D in held || Key.DirectionRight in held) x += 1f
        if (Key.W in held || Key.DirectionUp in held) y -= 1f
        if (Key.S in held || Key.DirectionDown in held) y += 1f
        val m = sqrt(x * x + y * y)
        return if (m > 0f) Pair(x / m, y / m) else Pair(0f, 0f)
    }

    private val MOVE_KEYS = setOf(
        Key.W, Key.A, Key.S, Key.D,
        Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight
    )
}

/** Keyboard movement for the shared game screen. */
object GameKeys {
    val active: Boolean get() = true
    val x: Float get() = DesktopInput.axis().first
    val y: Float get() = DesktopInput.axis().second
}
