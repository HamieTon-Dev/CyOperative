package com.cyberoperative.game.ui.common

import com.cyberoperative.game.i18n.tr
import androidx.compose.ui.input.key.Key

/** Rebindable PC actions (Settings → KEYBINDS). Arrow keys always move and Esc always pauses too. */
enum class KeyAction(val label: String, val default: Key) {
    MOVE_UP(tr("MOVE UP"), Key.W),
    MOVE_LEFT(tr("MOVE LEFT"), Key.A),
    MOVE_DOWN(tr("MOVE DOWN"), Key.S),
    MOVE_RIGHT(tr("MOVE RIGHT"), Key.D),
    HP_CHARGE(tr("USE HP CHARGE"), Key.Q),
    PACKET_BOMB(tr("PACKET BOMB"), Key.E),
    DASH(tr("DASH"), Key.Spacebar),
    SPELL_1(tr("CLASS SPELL 1"), Key.R),
    SPELL_2(tr("CLASS SPELL 2"), Key.F),
    PAUSE(tr("PAUSE"), Key.P),
    MAP(tr("MAP"), Key.M),
    INVENTORY(tr("INVENTORY"), Key.I)
}

/** The live key map (key codes by action name), loaded from the save's settings. */
object KeyBinds {
    @Volatile
    var map: Map<String, Long> = emptyMap()

    fun code(a: KeyAction): Long = map[a.name] ?: a.default.keyCode

    /** Binding a key already used elsewhere swaps the two, so no action is ever left without one. */
    fun rebind(current: Map<String, Long>, a: KeyAction, code: Long): Map<String, Long> {
        val full = KeyAction.entries.associate { it.name to (current[it.name] ?: it.default.keyCode) }.toMutableMap()
        val old = full[a.name]!!
        full.entries.firstOrNull { it.key != a.name && it.value == code }?.let { it.setValue(old) }
        full[a.name] = code
        return full
    }
}
