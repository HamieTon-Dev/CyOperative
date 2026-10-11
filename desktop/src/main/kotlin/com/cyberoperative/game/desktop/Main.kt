package com.cyberoperative.game.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

/** The game is laid out for a tall phone: the PC window keeps that shape (letterboxed when wider). */
private const val PHONE_ASPECT = 9f / 19.5f

fun main(args: Array<String>) {
    // Steam first: when launched outside the Steam client in a release build, Steam relaunches it.
    SteamService.start(args)
    val save = SaveRepository()
    val audio = AudioManager().apply {
        val s = save.current.settings
        setVolumes(s.musicVolume, s.sfxVolume)
        hapticsEnabled = false
        initialize()
    }
    val coop: CoopBackend = if (SteamService.running) SteamCoopBackend(SteamService) else OfflineCoopBackend()

    application {
        val screen = java.awt.Toolkit.getDefaultToolkit().screenSize
        val h = (screen.height * 0.88f).coerceAtLeast(640f)
        val w = h * PHONE_ASPECT
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
            Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxHeight().aspectRatio(PHONE_ASPECT, matchHeightConstraintsFirst = true)) {
                    CyberOperativeTheme { AppRoot(save = save, audio = audio, coop = coop) }
                }
            }
        }
    }
}
