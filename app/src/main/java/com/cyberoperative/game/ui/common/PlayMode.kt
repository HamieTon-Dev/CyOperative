package com.cyberoperative.game.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.cyberoperative.game.save.GameSettings

/**
 * How this device plays (owner, 2026-10-11: "Remove PC mode... cash, card and chips drop on
 * both modes always").
 *
 * - [pcRules]: the gameplay set every run now uses on every platform (upgrade chips and weapon
 *   cards, cash bags, HP charges, Packet Bombs, dash, class spells, exit doors).
 * - [landscape]: the landscape layout (wide zoomed-out rooms, HUD strip above the arena,
 *   landscape menus). Always on for Steam; Android's Settings → SCREEN: LANDSCAPE.
 *
 * Observable, so screens re-lay themselves out when the setting changes. A run keeps the
 * room shape it started with (saved in its snapshot).
 */
object PlayMode {
    var pcRules by mutableStateOf(true)
    var landscape by mutableStateOf(false)

    /** Android: follow the settings. */
    fun apply(s: GameSettings) {
        pcRules = true
        landscape = s.landscape
    }
}
