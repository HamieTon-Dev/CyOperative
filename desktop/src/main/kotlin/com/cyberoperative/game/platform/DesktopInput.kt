package com.cyberoperative.game.platform

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.type
import com.cyberoperative.game.ui.common.KeyAction
import com.cyberoperative.game.ui.common.KeyBinds
import kotlin.math.sqrt

/**
 * PC keyboard state. The window forwards key events here; the game screen reads [GameKeys]
 * every frame. Movement and actions follow [KeyBinds] (rebindable in Settings); arrow keys
 * always move and Esc always pauses / goes back.
 */
object DesktopInput {
    private val held = HashSet<Long>()
    private var confirm = false
    private var hpCharge = false
    private var packetBomb = false
    private var dash = false
    private val spell = BooleanArray(2)
    /** Settings is waiting for a key to bind. */
    private var capture: ((Long?) -> Unit)? = null

    val capturing: Boolean get() = capture != null

    fun takeConfirm(): Boolean = confirm.also { confirm = false }
    fun takeHpCharge(): Boolean = hpCharge.also { hpCharge = false }
    fun takePacketBomb(): Boolean = packetBomb.also { packetBomb = false }
    fun takeDash(): Boolean = dash.also { dash = false }
    fun takeSpell(slot: Int): Boolean = spell[slot].also { spell[slot] = false }

    fun captureKey(onKey: (Long?) -> Unit) { capture = onKey }

    /** True if the event was used here (not passed on). */
    fun onKey(e: KeyEvent): Boolean {
        val code = e.key.keyCode
        val cap = capture
        if (cap != null) {
            if (e.type == KeyEventType.KeyDown) {
                capture = null
                cap(if (e.key == Key.Escape) null else code)
            }
            return true
        }
        when (e.type) {
            KeyEventType.KeyDown -> {
                if (code in held) return false // key repeat
                held += code
                if (e.key == Key.Enter || e.key == Key.Spacebar || e.key == Key.NumPadEnter) confirm = true
                when (code) {
                    KeyBinds.code(KeyAction.HP_CHARGE) -> hpCharge = true
                    KeyBinds.code(KeyAction.PACKET_BOMB) -> packetBomb = true
                    KeyBinds.code(KeyAction.DASH) -> dash = true
                    KeyBinds.code(KeyAction.SPELL_1) -> spell[0] = true
                    KeyBinds.code(KeyAction.SPELL_2) -> spell[1] = true
                    KeyBinds.code(KeyAction.PAUSE) -> DesktopBack.dispatch()
                }
            }
            KeyEventType.KeyUp -> held -= code
        }
        return false
    }

    /** Window lost focus: let go of everything so the operative doesn't keep walking. */
    fun releaseAll() = held.clear()

    private fun down(a: KeyAction, alt: Key): Boolean = KeyBinds.code(a) in held || alt.keyCode in held

    internal fun axis(): Pair<Float, Float> {
        var x = 0f
        var y = 0f
        if (down(KeyAction.MOVE_LEFT, Key.DirectionLeft)) x -= 1f
        if (down(KeyAction.MOVE_RIGHT, Key.DirectionRight)) x += 1f
        if (down(KeyAction.MOVE_UP, Key.DirectionUp)) y -= 1f
        if (down(KeyAction.MOVE_DOWN, Key.DirectionDown)) y += 1f
        val m = sqrt(x * x + y * y)
        return if (m > 0f) Pair(x / m, y / m) else Pair(0f, 0f)
    }

    fun keyName(code: Long): String = try {
        java.awt.event.KeyEvent.getKeyText(Key(code).nativeKeyCode)
    } catch (_: Throwable) {
        "?"
    }
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
    fun takeHpCharge(): Boolean = Gamepad.takeHpCharge() or DesktopInput.takeHpCharge()
    fun takePacketBomb(): Boolean = Gamepad.takePacketBomb() or DesktopInput.takePacketBomb()
    fun takeDash(): Boolean = Gamepad.takeDash() or DesktopInput.takeDash()
    fun takeSpell(slot: Int): Boolean = Gamepad.takeSpell(slot) or DesktopInput.takeSpell(slot)
    fun keyName(code: Long): String = DesktopInput.keyName(code)
    fun captureKey(onKey: (Long?) -> Unit) = DesktopInput.captureKey(onKey)
}
