package com.cyberoperative.game.platform

import com.studiohartman.jamepad.ControllerManager
import com.studiohartman.jamepad.ControllerState
import kotlin.math.sqrt

/**
 * Controllers through SDL2 (Jamepad): Xbox / PlayStation / Switch pads and the Steam Deck's
 * built-in controls (Steam Input presents them as a standard pad).
 *
 * Left stick moves, right stick aims and fires in that direction (twin-stick), START or
 * MENU pauses (the same as Esc), B backs out of menus, LB drinks an HP charge, RB sets off a
 * Packet Bomb. Any button continues the briefing.
 * Without SDL (or with no pad) this stays idle and the keyboard and mouse work as before.
 */
object Gamepad {
    private const val MOVE_DEAD = 0.2f
    private const val AIM_DEAD = 0.35f

    private var manager: ControllerManager? = null
    private var failed = false
    private var state: ControllerState? = null
    private var anyPress = false

    var connected = false
        private set
    var moveX = 0f
        private set
    var moveY = 0f
        private set
    val moving: Boolean get() = moveX != 0f || moveY != 0f
    var aiming = false
        private set
    var aimX = 0f
        private set
    var aimY = 0f
        private set

    /** Reads the first connected pad; called once per frame. */
    fun poll() {
        val m = manager ?: if (failed) return else try {
            ControllerManager().also { it.initSDLGamepad(); manager = it }
        } catch (_: Throwable) {
            failed = true
            return
        }
        try {
            m.update()
            val s = (0 until 4).asSequence().map { m.getState(it) }.firstOrNull { it.isConnected }
            state = s
            connected = s != null
            if (s == null) { moveX = 0f; moveY = 0f; aiming = false; return }
            // SDL's Y axis points up; the game's points down.
            val (mx, my) = dead(s.leftStickX, -s.leftStickY, MOVE_DEAD)
            val dx = (if (s.dpadRight) 1f else 0f) - (if (s.dpadLeft) 1f else 0f)
            val dy = (if (s.dpadDown) 1f else 0f) - (if (s.dpadUp) 1f else 0f)
            if (mx == 0f && my == 0f && (dx != 0f || dy != 0f)) {
                val l = sqrt(dx * dx + dy * dy); moveX = dx / l; moveY = dy / l
            } else { moveX = mx; moveY = my }
            val (ax, ay) = dead(s.rightStickX, -s.rightStickY, AIM_DEAD)
            aiming = ax != 0f || ay != 0f || s.rightTrigger > 0.5f
            if (ax != 0f || ay != 0f) { val l = sqrt(ax * ax + ay * ay); aimX = ax / l; aimY = ay / l }
            if (s.aJustPressed || s.bJustPressed || s.xJustPressed || s.yJustPressed || s.startJustPressed ||
                s.backJustPressed || s.lbJustPressed || s.rbJustPressed) anyPress = true
            // LB = HP charge, RB = Packet Bomb (thumbs stay on the sticks).
            if (s.lbJustPressed) hpCharge = true
            if (s.rbJustPressed) packetBomb = true
            // A = dash, X = class spell 1 (R), Y = class spell 2 (F).
            if (s.aJustPressed) dash = true
            // BACK / VIEW toggles the world map.
            if (s.backJustPressed) map = true
            if (s.xJustPressed) spell[0] = true
            if (s.yJustPressed) spell[1] = true
            // START / MENU = Esc (pause, or back out of a menu); B backs out of menus too.
            if (s.startJustPressed) DesktopBack.dispatch()
            if (s.bJustPressed && !inGame) DesktopBack.dispatch()
        } catch (_: Throwable) {
        }
    }

    /** Set by the game screen while a run is on screen (B is not "back" mid-fight). */
    @Volatile var inGame = false

    fun takeAnyPress(): Boolean = anyPress.also { anyPress = false }
    fun takeHpCharge(): Boolean = hpCharge.also { hpCharge = false }
    fun takePacketBomb(): Boolean = packetBomb.also { packetBomb = false }
    private var hpCharge = false
    private var packetBomb = false
    private var dash = false
    private var map = false
    fun takeMap(): Boolean = map.also { map = false }
    private val spell = BooleanArray(2)
    fun takeDash(): Boolean = dash.also { dash = false }
    fun takeSpell(slot: Int): Boolean = spell[slot].also { spell[slot] = false }

    private fun dead(x: Float, y: Float, dz: Float): Pair<Float, Float> {
        val m = sqrt(x * x + y * y)
        if (m < dz) return 0f to 0f
        // Rescale so movement starts at 0 just past the dead zone.
        val k = ((m - dz) / (1f - dz)).coerceIn(0f, 1f) / m
        return x * k to y * k
    }
}
