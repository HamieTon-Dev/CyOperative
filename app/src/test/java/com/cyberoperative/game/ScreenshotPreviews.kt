package com.cyberoperative.game

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.cyberoperative.game.audio.AudioManager
import com.cyberoperative.game.save.SaveRepository
import com.cyberoperative.game.ui.game.GameScreen
import com.cyberoperative.game.ui.game.GameSession
import com.cyberoperative.game.ui.menu.MainMenuScreen
import com.cyberoperative.game.ui.splash.BootTerminal
import com.cyberoperative.game.ui.theme.CyberOperativeTheme
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders real screens to PNG under docs/screenshots for visual review.
 * Skipped by default; run with `./gradlew testDebugUnitTest -PrenderPreviews`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [35])
class ScreenshotPreviews {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val enabled = System.getProperty("cyberop.renderPreviews") == "true"
    private val outDir = File(System.getProperty("cyberop.previewDir") ?: "build/screens")

    private fun save(name: String) {
        outDir.mkdirs()
        // Drawn straight from the view: captureToImage waits for an idle that
        // never comes while the game's frame loop holds the test clock.
        val view = compose.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { view.draw(Canvas(bmp)) }
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun mainMenu() {
        assumeTrue(enabled)
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repo = SaveRepository(ctx)
        compose.mainClock.autoAdvance = false
        compose.setContent { CyberOperativeTheme { MainMenuScreen(repo.current) {} } }
        compose.mainClock.advanceTimeBy(1500)
        save("main_menu")
    }

    @Test fun bootTerminal() {
        assumeTrue(enabled)
        compose.mainClock.autoAdvance = false
        compose.setContent { CyberOperativeTheme { BootTerminal({}) {} } }
        compose.mainClock.advanceTimeBy(1800)
        save("boot_terminal")
    }

    private fun gameplay(name: String, simulateSeconds: Float, setup: (GameSession) -> Unit = {}) {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repo = SaveRepository(ctx)
        repo.update { it.copy(tutorialDone = true) }
        val session = GameSession(repo, AudioManager(ctx))
        setup(session)
        val bot = Bot(session.engine, dodge = true)
        var t = 0f
        while (t < simulateSeconds) { bot.step(1f / 30f); t += 1f / 30f }
        session.engine.setInput(0f, 0f)
        compose.mainClock.autoAdvance = false
        compose.setContent { CyberOperativeTheme { GameScreen(session, true, {}, {}) } }
        compose.mainClock.advanceTimeBy(100)
        save(name)
    }

    @Test fun combat() {
        assumeTrue(enabled)
        gameplay("gameplay_combat", 4.0f)
    }

    @Test fun upgradeScreen() {
        assumeTrue(enabled)
        gameplay("upgrade_cards", 0f) { s ->
            s.engine.debugStartPlan(com.cyberoperative.game.engine.LevelPlanner.plan(3, kotlin.random.Random(3), null, true))
        }
    }

    @Test fun boss() {
        assumeTrue(enabled)
        gameplay("boss_breach", 9f) { s ->
            s.engine.debugStartPlan(com.cyberoperative.game.engine.LevelPlanner.bossPlan(10, kotlin.random.Random(1)))
        }
    }

    @Test fun event() {
        assumeTrue(enabled)
        gameplay("event_packet_flood", 4f) { s ->
            s.engine.debugStartPlan(
                com.cyberoperative.game.engine.LevelPlanner.eventPlan(
                    8, kotlin.random.Random(2), com.cyberoperative.game.data.Events.PACKET_FLOOD, null
                )
            )
        }
    }
}
