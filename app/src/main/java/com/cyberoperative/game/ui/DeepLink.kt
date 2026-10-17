package com.cyberoperative.game.ui

import com.cyberoperative.game.ui.menu.MenuTarget
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A menu page the app was asked to open from outside (Android: the Idle Run widget and its
 * notification). AppRoot opens it, straight past the splash, and clears it.
 */
object DeepLink {
    val pending = MutableStateFlow<MenuTarget?>(null)

    fun open(target: MenuTarget) { pending.value = target }

    /** The hatch notification: open the CHARACTER screen on its PETS tab. */
    @Volatile var petsTab = false
}
