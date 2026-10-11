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

    private var confirm = false

    /** Enter / Space since the last call (continues the briefing). */
    fun takeConfirm(): Boolean = confirm.also { confirm = false }

    /** True if the key is one the game uses (consumed so it doesn't reach text fields twice). */
    fun onKey(e: KeyEvent): Boolean {
        if (e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.Spacebar || e.key == Key.NumPadEnter)) confirm = true
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

/** Keyboard + controller input for the shared game screen. */
object GameKeys {
    val active: Boolean get() = true
    var inGame: Boolean
        get() = Gamepad.inGame
        set(v) { Gamepad.inGame = v }
    val x: Float get() = if (Gamepad.moving) Gamepad.moveX else DesktopInput.axis().first
    val y: Float get() = if (Gamepad.moving) Gamepad.moveY else DesktopInput.axis().second
    val stickAim: Boolean get() = Gamepad.aiming
    val aimX: Float get() = Gamepad.aimX
    val aimY: Float get() = Gamepad.aimY
    val padConnected: Boolean get() = Gamepad.connected
    fun takeAnyPress(): Boolean = Gamepad.takeAnyPress() or DesktopInput.takeConfirm()
}
