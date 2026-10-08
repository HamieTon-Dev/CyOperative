package com.cyberoperative.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.cyberoperative.game.ui.AppRoot
import com.cyberoperative.game.ui.theme.CyberOperativeTheme

class MainActivity : ComponentActivity() {

    private val app: CyberOperativeApp get() = application as CyberOperativeApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CyberOperativeTheme {
                AppRoot(save = app.save, audio = app.audio, coop = app.coop)
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
    }
}
