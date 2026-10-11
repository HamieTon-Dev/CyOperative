package com.cyberoperative.game.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.cyberoperative.game.save.GameSettings

/**
 * How this device plays (owner, 2026-10-11).
 *
 * - [pcRules]: the PC gameplay set (upgrade chips, cash bags, HP charges, Packet Bombs, dash,
 *   class spells, multiple exit doors). Always on for Steam; Android's optional PC MODE.
 * - [landscape]: the landscape layout (wide zoomed-out rooms, HUD strip above the arena,
 *   landscape menus). Always on for Steam; Android's PC MODE in LANDSCAPE.
 *
 * Observable, so screens re-lay themselves out when the setting changes. A run keeps the
 * shape and rules it started with (saved in its snapshot).
 */
object PlayMode {
    var pcRules by mutableStateOf(false)
    var landscape by mutableStateOf(false)

    /** Android: follow the settings. */
    fun apply(s: GameSettings) {
        pcRules = s.pcMode
        landscape = s.pcMode && s.pcLandscape
    }
}
