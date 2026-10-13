package com.cyberoperative.game.ui.common

/** A text field has focus (the keyboard is, or is about to be, up). Read by the Android activity. */
object TextEntry {
    @Volatile var focused: Boolean = false
}
