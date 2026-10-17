package com.cyberoperative.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.cyberoperative.game.ui.AppRoot
import com.cyberoperative.game.ui.theme.CyberOperativeTheme

class MainActivity : ComponentActivity() {

    private val app: CyberOperativeApp get() = application as CyberOperativeApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app.idle.permissionAsker = {
            // Android 13+ notification prompt for Idle Runs (asked when a run starts; the answer needs no handling).
            if (android.os.Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 41)
        }
        openFrom(intent)
        enableEdgeToEdge()
        hideSystemBars()
        val crash = CrashLog.read(this)
        setContent {
            CyberOperativeTheme {
                var report by remember { mutableStateOf(crash) }
                val r = report
                if (r != null) CrashReportScreen(r) { CrashLog.clear(this); report = null }
                else AppRoot(save = app.save, audio = app.audio, coop = app.coop)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openFrom(intent)
    }

    /** The Idle Run widget / notification opens Main Menu › Idle Runs. */
    private fun openFrom(intent: android.content.Intent?) {
        if (intent?.action == com.cyberoperative.game.widget.IdleRunWidget.ACTION_OPEN_PETS) {
            com.cyberoperative.game.ui.DeepLink.open(com.cyberoperative.game.ui.menu.MenuTarget.VAULT)
            com.cyberoperative.game.ui.DeepLink.petsTab = true
            intent.action = null
        }
        if (intent?.action == com.cyberoperative.game.widget.IdleRunWidget.ACTION_OPEN_IDLE_RUNS) {
            com.cyberoperative.game.ui.DeepLink.open(com.cyberoperative.game.ui.menu.MenuTarget.IDLE_RUNS)
            intent.action = null
        }
    }

    override fun onPause() {
        super.onPause()
        app.audio.onBackground()
        // Leaving the app: save the Idle Run, schedule its worker and redraw the widget.
        app.idle.sync(refreshWidget = true)
        // The incubator's hatch reminder follows the egg in it now.
        com.cyberoperative.game.meta.PetReminders.update(app.save.current)
    }

    override fun onResume() {
        super.onResume()
        app.audio.onForeground()
        // Opening the app catches the Idle Run up to now.
        app.idle.sync(refreshWidget = false)
        hideSystemBars()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    /**
     * Full immersive mode on every screen (owner, 2026-10-11: "hide the bottom home and back
     * buttons… swipe up from bottom to see them again… too easy to accidentally press back").
     * Status and navigation bars are hidden; a swipe from the edge shows them for a moment and
     * they hide again by themselves.
     */
    private fun hideSystemBars() {
        // Never while the keyboard is up (typing a character name): hiding the bars then made
        // Android close the keyboard again straight away, over and over (owner, 0.30.0).
        val insets = androidx.core.view.ViewCompat.getRootWindowInsets(window.decorView)
        if (insets?.isVisible(WindowInsetsCompat.Type.ime()) == true || com.cyberoperative.game.ui.common.TextEntry.focused) return
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }
}
