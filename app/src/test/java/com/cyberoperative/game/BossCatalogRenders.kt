package com.cyberoperative.game

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.cyberoperative.game.audio.AudioManager
import com.cyberoperative.game.data.Bosses
import com.cyberoperative.game.engine.AiState
import com.cyberoperative.game.engine.LevelPlanner
import com.cyberoperative.game.save.SaveRepository
import com.cyberoperative.game.ui.game.GameScreen
import com.cyberoperative.game.ui.game.GameSession
import com.cyberoperative.game.ui.theme.CyberOperativeTheme
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.random.Random

/**
 * Boss reference sheet (owner, 2026-10-10: "show me the bosses and their
 * attack patterns so I can design more"). For every boss: its look right after
 * the entrance (phase 0) and a fight screenshot in each of its three phases.
 * Opt-in like the other previews: ./gradlew testDebugUnitTest -PrenderPreviews --tests '*BossCatalogRenders*'
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [35])
class BossCatalogRenders(private val bossIndex: Int, private val phase: Int) {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val enabled = System.getProperty("cyberop.renderPreviews") == "true"
    private val outDir = File(System.getProperty("cyberop.previewDir") ?: "build/screens").resolveSibling("bosses")

    @Test fun render() {
        assumeTrue(enabled)
        val boss = Bosses.roster[bossIndex]
        val level = 10 * (bossIndex + 1)
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repo = SaveRepository(ctx)
        repo.update { it.copy(tutorialDone = true) }
        val session = GameSession(repo, AudioManager(ctx))
        val g = session.engine
        g.debugStartPlan(LevelPlanner.bossPlan(level, Random(bossIndex)).copy(glitchedBoss = false))
        val me = g.operatives[0]
        fun step(seconds: Float) {
            var t = 0f
            while (t < seconds) {
                me.invuln = 99f
                me.hp = me.build.stats.maxHp
                g.setInput(0f, 0f)
                // Hold the boss in the phase being shown.
                g.boss?.let { b ->
                    if (b.state != AiState.SPAWNING) {
                        val keep = when (phase) { 2 -> 0.55f; 3 -> 0.2f; else -> 0.95f }
                        b.hp = b.maxHp * keep
                    }
                }
                g.update(1f / 60f)
                t += 1f / 60f
            }
        }
        // Through the entrance.
        var guard = 0
        while ((g.boss == null || g.boss!!.state == AiState.SPAWNING) && guard++ < 600) step(1f / 30f)
        if (phase == 0) {
            // Design shot: no shots on screen yet, operative out of the way.
            for (p in g.projectiles.items) p.active = false
            for (h in g.hazards.items) h.active = false
            for (e in g.enemies.items) if (e.active && e.boss == null) e.active = false
        } else {
            // Wait for a busy moment of this phase's patterns.
            var t = 0f
            while (t < 9f) {
                step(0.25f)
                t += 0.25f
                val shots = g.projectiles.items.count { it.active && !it.friendly }
                val hazards = g.hazards.items.count { it.active }
                val adds = g.enemies.items.count { it.active && it.boss == null }
                if (t > 1.5f && (shots >= 18 || hazards >= 2 || adds >= 3)) break
            }
        }
        g.sounds.clear()
        compose.mainClock.autoAdvance = false
        compose.setContent { CyberOperativeTheme { GameScreen(session, false, {}, {}) } }
        compose.mainClock.advanceTimeBy(100)
        outDir.mkdirs()
        val view = compose.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { view.draw(Canvas(bmp)) }
        File(outDir, "boss_${"%02d".format(bossIndex + 1)}_${boss.id}_${if (phase == 0) "design" else "phase$phase"}.png")
            .outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "boss{0}_phase{1}")
        fun params(): List<Array<Any>> = Bosses.roster.indices.flatMap { i -> (0..3).map { p -> arrayOf<Any>(i, p) } }
    }
}
