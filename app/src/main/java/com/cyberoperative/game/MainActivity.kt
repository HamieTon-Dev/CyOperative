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

    override fun onPause() {
        super.onPause()
        app.audio.onBackground()
    }

    override fun onResume() {
        super.onResume()
        app.audio.onForeground()
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
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }
}
