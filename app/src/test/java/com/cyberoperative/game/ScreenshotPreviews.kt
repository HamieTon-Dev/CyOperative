package com.cyberoperative.game

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.nativeCanvas
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

    @Test fun skins() {
        assumeTrue(enabled)
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repo = SaveRepository(ctx)
        repo.update { it.copy(diamonds = 120, ownedSkins = setOf("default", "red_hat"), selectedSkin = "red_hat") }
        compose.mainClock.autoAdvance = false
        compose.setContent { CyberOperativeTheme { com.cyberoperative.game.ui.menu.SkinsScreen(repo, AudioManager(ctx)) {} } }
        compose.mainClock.advanceTimeBy(800)
        save("skins")
    }

    @Test fun store() {
        assumeTrue(enabled)
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repo = SaveRepository(ctx)
        repo.update { it.copy(diamonds = 650, reviveTokens = 3) }
        compose.mainClock.autoAdvance = false
        compose.setContent { CyberOperativeTheme { com.cyberoperative.game.ui.menu.StoreScreen(repo, AudioManager(ctx), {}, {}) } }
        compose.mainClock.advanceTimeBy(500)
        save("store")
    }

    @Test fun skinnedArena() {
        assumeTrue(enabled)
        gameplay("gameplay_skin_background", 3f, profileSetup = { it.copy(selectedSkin = "spectrum", selectedBackground = "aurora") })
    }

    @Test fun bodyDesigns() {
        assumeTrue(enabled)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CyberOperativeTheme {
                BodyBoard()
            }
        }
        compose.mainClock.advanceTimeBy(700)
        save("body_designs")
    }

    @Test fun arenaAgent() { assumeTrue(enabled); gameplay("arena_25d_agent", 2.5f, profileSetup = { it.copy(operativeBody = "agent") }) }
    @Test fun arenaMech() { assumeTrue(enabled); gameplay("arena_25d_mech", 3.5f, profileSetup = { it.copy(operativeBody = "mech", selectedSkin = "blue_hat") }) }
    @Test fun arenaRunner() { assumeTrue(enabled); gameplay("arena_25d_runner", 5f, profileSetup = { it.copy(operativeBody = "runner", selectedSkin = "cryptographer", selectedBackground = "rainfall") }) }
    @Test fun gateOpen() {
        assumeTrue(enabled)
        gameplay("arena_25d_gate_open", 0f, profileSetup = {
            it.copy(operativeBody = "agent", permanentUpgrades = mapOf("max_hp" to 20, "base_damage" to 20, "attack_speed" to 15))
        }, setup = { s ->
            val bot = Bot(s.engine, dodge = true)
            var t = 0f
            while (t < 90f && s.engine.phase != com.cyberoperative.game.engine.Phase.PORTAL) { bot.step(1f / 30f); t += 1f / 30f }
        })
    }

    @Test fun pauseMusicPlayer() {
        assumeTrue(enabled)
        gameplay("pause_music_player", 1f, setup = { s -> s.paused = true })
    }

    private fun bossIntro(name: String, seconds: Float) = gameplay(name, 0f, setup = { s ->
        s.engine.debugStartPlan(com.cyberoperative.game.engine.LevelPlanner.bossPlan(10, kotlin.random.Random(1)))
        var t = 0f
        while (t < seconds) { s.engine.update(1f / 60f); t += 1f / 60f }
    })

    @Test fun bossIntro1() { assumeTrue(enabled); bossIntro("boss_intro_1_bar", 0.8f) }
    @Test fun bossIntro2() { assumeTrue(enabled); bossIntro("boss_intro_2_name", 1.75f) }
    @Test fun bossIntro3() { assumeTrue(enabled); bossIntro("boss_intro_3_growl", 2.1f) }

    @Test fun bootTerminal() {
        assumeTrue(enabled)
        compose.mainClock.autoAdvance = false
        compose.setContent { CyberOperativeTheme { BootTerminal({}) {} } }
        compose.mainClock.advanceTimeBy(1800)
        save("boot_terminal")
    }

    private fun gameplay(
        name: String, simulateSeconds: Float,
        profileSetup: (com.cyberoperative.game.save.PlayerProfile) -> com.cyberoperative.game.save.PlayerProfile = { it },
        setup: (GameSession) -> Unit = {}
    ) {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repo = SaveRepository(ctx)
        repo.update { profileSetup(it.copy(tutorialDone = true)) }
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


/** The three full-body designs, each in three skins, idle and walking. */
@androidx.compose.runtime.Composable
private fun BodyBoard() {
    val skins = listOf("default", "blue_hat", "red_hat")
    var time by androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0.6f) }
    androidx.compose.foundation.Canvas(
        androidx.compose.ui.Modifier
            .fillMaxSize()
            .background(com.cyberoperative.game.ui.theme.Palette.Background)
    ) {
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true; color = 0xFF00E5FF.toInt(); textSize = 40f; textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
        }
        val small = android.graphics.Paint(paint).apply { textSize = 26f; color = 0xFF93A6C4.toInt() }
        val styles = com.cyberoperative.game.ui.game.BodyStyle.entries
        val rowH = size.height / styles.size
        styles.forEachIndexed { r, style ->
            val top = r * rowH
            drawRect(androidx.compose.ui.graphics.Color(0xFF0A1220), androidx.compose.ui.geometry.Offset(0f, top + 8f), androidx.compose.ui.geometry.Size(size.width, rowH - 16f))
            drawContext.canvas.nativeCanvas.drawText("${'A' + r}  ${style.label}", size.width / 2f, top + 56f, paint)
            drawContext.canvas.nativeCanvas.drawText(style.blurb, size.width / 2f, top + 92f, small)
            val u = rowH / 120f
            for (i in 0 until 4) {
                val skin = com.cyberoperative.game.data.OperativeSkins.byId(skins[minOf(i, 2)])
                val x = size.width * (i + 0.5f) / 4f
                val moving = i == 3
                val facing = if (i == 1) Math.PI.toFloat() * 0.85f else -0.3f
                with(com.cyberoperative.game.ui.game.OperativeFigures) {
                    drawFigure(style, skin, x, top + rowH - 40f, u, facing, moving, time + i * 0.07f)
                }
            }
        }
    }
}
