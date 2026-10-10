package com.cyberoperative.game

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.cyberoperative.game.audio.AudioManager
import com.cyberoperative.game.data.Events
import com.cyberoperative.game.data.Upgrades
import com.cyberoperative.game.engine.GameMode
import com.cyberoperative.game.engine.LevelPlanner
import com.cyberoperative.game.engine.Phase
import com.cyberoperative.game.save.PlayerProfile
import com.cyberoperative.game.save.SaveRepository
import com.cyberoperative.game.ui.game.GameScreen
import com.cyberoperative.game.ui.game.GameSession
import com.cyberoperative.game.ui.menu.MainMenuScreen
import com.cyberoperative.game.ui.theme.CyberOperativeTheme
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.random.Random

/**
 * Google Play phone screenshots, rendered from the real game at 1080 x 1920
 * (9:16; Play rejects a long side more than 2x the short side).
 * Run: ./gradlew testDebugUnitTest --tests '*StoreScreenshots*' -PrenderPreviews
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xxhdpi", sdk = [35])
class StoreScreenshots {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val enabled = System.getProperty("cyberop.renderPreviews") == "true"
    private val outDir = File(System.getProperty("cyberop.previewDir") ?: "build/screens").resolveSibling("store-screenshots")

    private fun save(name: String) {
        outDir.mkdirs()
        val view = compose.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { view.draw(Canvas(bmp)) }
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val strong: (PlayerProfile) -> PlayerProfile = {
        it.copy(
            tutorialDone = true,
            permanentUpgrades = mapOf("max_hp" to 20, "base_damage" to 6, "attack_speed" to 6, "armor" to 6),
            ownedOperatives = setOf("operative", "neon_operative")
        )
    }

    private fun shoot(
        name: String, seconds: Float, mode: GameMode = GameMode.CAMPAIGN,
        profile: (PlayerProfile) -> PlayerProfile = strong, setup: (GameSession) -> Unit = {},
        stopWhen: (GameSession, Float) -> Boolean = { _, _ -> false },
        after: (GameSession) -> Unit = {}
    ) {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repo = SaveRepository(ctx)
        repo.update { profile(PlayerProfile()) }
        val session = GameSession(repo, AudioManager(ctx), mode)
        setup(session)
        val bot = Bot(session.engine, dodge = true)
        var t = 0f
        while (t < seconds && !stopWhen(session, t)) { bot.step(1f / 30f); t += 1f / 30f }
        session.engine.setInput(0f, 0f)
        // Let the operative settle into firing for the frame.
        repeat(8) { session.engine.update(1f / 60f) }
        after(session)
        compose.mainClock.autoAdvance = false
        compose.setContent { CyberOperativeTheme { GameScreen(session, true, {}, {}) } }
        compose.mainClock.advanceTimeBy(150)
        save(name)
    }

    @Test fun s1_beamCombat() {
        assumeTrue(enabled)
        shoot("01_plasma_beam", 40f, profile = { strong(it).copy(operativeBody = "neon_operative") }, setup = { s ->
            repeat(2) { s.engine.build.take(Upgrades.PLASMA_BEAM) }
            s.engine.build.take(Upgrades.PACKET_NODES)
            s.engine.debugStartPlan(LevelPlanner.plan(7, Random(31), null, true))
        }, stopWhen = { s, t -> t > 2.5f && s.engine.beamActive && near(s, 420f) >= 3 })
    }

    @Test fun s2_boss() {
        assumeTrue(enabled)
        shoot("02_boss_breach", 60f, profile = { strong(it).copy(operativeBody = "agent", selectedSkin = "ids") }, setup = { s ->
            s.engine.build.take(Upgrades.ENCRYPTION_BLADES)
            s.engine.debugStartPlan(LevelPlanner.bossPlan(10, Random(2)).copy(boss = com.cyberoperative.game.data.Bosses.forLevel(10)))
        }, stopWhen = { s, _ ->
            val b = s.engine.boss
            b != null && b.targetable && kotlin.math.abs(b.y - s.engine.py) < 360f &&
                s.engine.projectiles.items.count { it.active && !it.friendly } >= 8
        })
    }

    @Test fun s3_event() {
        assumeTrue(enabled)
        shoot("03_event_packet_flood", 30f, profile = { strong(it).copy(operativeBody = "runner", selectedSkin = "cryptographer") }, setup = { s ->
            s.engine.build.take(Upgrades.PACKET_NODES); s.engine.build.take(Upgrades.PACKET_NODES)
            s.engine.debugStartPlan(LevelPlanner.eventPlan(12, Random(5), Events.PACKET_FLOOD, null))
        }, stopWhen = { s, t -> t > 2f && near(s, 450f) >= 7 })
    }

    @Test fun s4_upgrades() {
        assumeTrue(enabled)
        shoot("04_power_ups", 0f, profile = { strong(it).copy(permanentUpgrades = mapOf("starting_power" to 1)) })
    }

    @Test fun s5_combatMech() {
        assumeTrue(enabled)
        shoot("05_sentinel_mech", 5f, profile = { strong(it).copy(operativeBody = "mech", selectedSkin = "zero_day_hunter") }, setup = { s ->
            s.engine.build.take(Upgrades.PACKET_SCATTER); s.engine.build.take(Upgrades.FIREWALL)
            s.engine.debugStartPlan(LevelPlanner.plan(15, Random(77), null, true))
        })
    }

    @Test fun s6_gateOpen() {
        assumeTrue(enabled)
        shoot("06_gate_open", 120f, profile = { strong(it).copy(operativeBody = "neon_operative") },
            setup = { s -> s.engine.debugStartPlan(LevelPlanner.plan(4, Random(8), null, true)) },
            stopWhen = { s, _ -> s.engine.phase == Phase.PORTAL },
            after = { s ->
                // Walk up toward the open gate so it is in frame.
                val g = s.engine
                var k = 0
                while (k++ < 240 && g.py > 330f) { g.setInput((g.arena.portalX - g.px) / 300f, -1f); g.update(1f / 60f) }
                g.setInput(0f, 0f)
            })
    }

    @Test fun s7_menu() {
        assumeTrue(enabled)
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CyberOperativeTheme {
                MainMenuScreen(PlayerProfile(euros = 2450, diamonds = 300, highestLevel = 37, bestScore = 184_220, operativeXp = 5200)) {}
            }
        }
        compose.mainClock.advanceTimeBy(1200)
        save("07_main_menu")
        ctx.hashCode()
    }
}

private fun near(s: GameSession, r: Float): Int {
    val g = s.engine
    return g.enemies.items.count { it.targetable && kotlin.math.hypot(it.x - g.px, it.y - g.py) < r }
}
