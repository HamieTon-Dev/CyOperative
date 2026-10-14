package com.cyberoperative.game.ui.common

/**
 * Quitting the game from the main menu (PC: EXIT TO DESKTOP, owner 0.31.0). The desktop build
 * sets [onExit] when its window opens; phones leave it null (Android apps are closed by the system).
 */
object AppExit {
    var onExit: (() -> Unit)? = null
}
