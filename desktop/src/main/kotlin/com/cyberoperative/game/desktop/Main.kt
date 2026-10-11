package com.cyberoperative.game.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.cyberoperative.game.audio.AudioManager
import com.cyberoperative.game.net.CoopBackend
import com.cyberoperative.game.net.OfflineCoopBackend
import com.cyberoperative.game.platform.DesktopBack
import com.cyberoperative.game.platform.DesktopInput
import com.cyberoperative.game.save.SaveRepository
import com.cyberoperative.game.steam.SteamCoopBackend
import com.cyberoperative.game.steam.SteamService
import com.cyberoperative.game.ui.AppRoot
import com.cyberoperative.game.ui.theme.CyberOperativeTheme

fun main(args: Array<String>) {
    // Landscape rooms on PC (must be set before any room is generated).
    com.cyberoperative.game.data.ArenaShape.wide = true
    com.cyberoperative.game.data.ArenaShape.pcRules = true
    com.cyberoperative.game.ui.common.PlayMode.pcRules = true
    com.cyberoperative.game.ui.common.PlayMode.landscape = true
    // Steam first: when launched outside the Steam client in a release build, Steam relaunches it.
    SteamService.start(args)
    val save = SaveRepository()
    val audio = AudioManager().apply {
        val s = save.current.settings
        setVolumes(s.musicVolume, s.sfxVolume)
        hapticsEnabled = false
        initialize()
    }
    com.cyberoperative.game.ui.common.KeyBinds.map = save.current.settings.keyBinds
    val coop: CoopBackend = if (SteamService.running) SteamCoopBackend(SteamService) else OfflineCoopBackend()

    application {
        // Landscape 16:9 at 80% of the screen; F11 for fullscreen.
        val screen = java.awt.Toolkit.getDefaultToolkit().screenSize
        val w = (screen.width * 0.8f).coerceIn(1024f, 2560f)
        val h = w * 9f / 16f
        val state = rememberWindowState(size = DpSize(w.dp, h.dp), position = WindowPosition(Alignment.Center))
        Window(
            onCloseRequest = {
                SteamService.shutdown()
                exitApplication()
            },
            title = "Cyber Operative",
            state = state,
            onPreviewKeyEvent = { e ->
                when {
                    DesktopInput.capturing -> DesktopInput.onKey(e)
                    e.type == KeyEventType.KeyDown && e.key == Key.Escape -> DesktopBack.dispatch()
                    e.type == KeyEventType.KeyDown && e.key == Key.F11 -> {
                        state.placement = if (state.placement == WindowPlacement.Fullscreen) WindowPlacement.Floating else WindowPlacement.Fullscreen
                        true
                    }
                    else -> DesktopInput.onKey(e)
                }
            }
        ) {
            LaunchedEffect(state.isMinimized) { if (state.isMinimized) audio.onBackground() else audio.onForeground() }
            LaunchedEffect(Unit) { SteamService.pumpCallbacks() }
            // Controllers: polled every frame (menus too: START = Esc, B = back).
            LaunchedEffect(Unit) { while (true) { androidx.compose.runtime.withFrameNanos { }; com.cyberoperative.game.platform.Gamepad.poll() } }
            LaunchedEffect(state.placement) { DesktopInput.releaseAll() }
            Box(
                Modifier.fillMaxSize().background(Color.Black)
                    // The owner's neon arrow is the cursor everywhere (aim with its tip).
                    .pointerHoverIcon(androidx.compose.ui.input.pointer.PointerIcon(com.cyberoperative.game.platform.DesktopCursor.cursor), overrideDescendants = true)
            ) {
                CyberOperativeTheme { AppRoot(save = save, audio = audio, coop = coop) }
            }
        }
    }
}
