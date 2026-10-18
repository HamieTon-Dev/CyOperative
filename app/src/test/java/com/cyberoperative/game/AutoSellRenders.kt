package com.cyberoperative.game

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.cyberoperative.game.audio.AudioManager
import com.cyberoperative.game.data.GearRarity
import com.cyberoperative.game.save.SaveRepository
import com.cyberoperative.game.ui.menu.SettingsScreen
import com.cyberoperative.game.ui.theme.CyberOperativeTheme
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp

/** SETTINGS → AUTO-SELL with the rarity list open (0.46.0). Opt-in (-PrenderPreviews). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xhdpi", sdk = [35])
class AutoSellRenders {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun autoSellPanel() {
        assumeTrue(System.getProperty("cyberop.renderPreviews") == "true")
        val dir = File(System.getProperty("cyberop.previewDir") ?: "build/screens").also { it.mkdirs() }
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repo = SaveRepository(ctx)
        repo.update { it.copy(settings = it.settings.copy(autoSellBelow = GearRarity.EPIC)) }
        compose.mainClock.autoAdvance = false
        compose.setContent { CyberOperativeTheme { SettingsScreen(repo, AudioManager(ctx)) {} } }
        compose.mainClock.advanceTimeBy(500)
        // Every window (the screen and the dropdown popup), drawn where it sits on screen.
        fun save(name: String) {
            val views = Class.forName("android.view.WindowManagerGlobal").let { c ->
                val g = c.getMethod("getInstance").invoke(null)
                @Suppress("UNCHECKED_CAST")
                (c.getDeclaredField("mViews").apply { isAccessible = true }.get(g) as List<android.view.View>).toList()
            }
            val root = compose.activity.window.decorView
            val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bmp)
            compose.runOnUiThread {
                for (v in views) {
                    if (v.width == 0 || v.height == 0) continue
                    val at = IntArray(2).also { v.getLocationOnScreen(it) }
                    canvas.save(); canvas.translate(at[0].toFloat(), at[1].toFloat()); v.draw(canvas); canvas.restore()
                }
            }
            File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        save("settings_autosell_closed")
    }

    /** The open list on its own (Robolectric doesn't draw popup windows); composited in docs. */
    @Test fun autoSellList() {
        assumeTrue(System.getProperty("cyberop.renderPreviews") == "true")
        val dir = File(System.getProperty("cyberop.previewDir") ?: "build/screens").also { it.mkdirs() }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CyberOperativeTheme {
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF05080F))) {
                    androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.width(230.dp).background(com.cyberoperative.game.ui.theme.Palette.Surface).padding(vertical = 8.dp)) {
                        com.cyberoperative.game.ui.menu.AutoSellOptions {}
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(700)
        val root = compose.activity.window.decorView
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { root.draw(android.graphics.Canvas(bmp)) }
        File(dir, "settings_autosell_list.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
