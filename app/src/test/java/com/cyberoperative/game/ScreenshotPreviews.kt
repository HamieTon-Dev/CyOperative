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

    @Test fun neonSkin() {
        assumeTrue(enabled)
        compose.mainClock.autoAdvance = false
        compose.setContent { CyberOperativeTheme { NeonBoard() } }
        compose.mainClock.advanceTimeBy(300)
        save("skin_neon")
    }

    @Test fun neonArena() {
        assumeTrue(enabled)
        gameplay("arena_neon_operative", 3f, profileSetup = { it.copy(operativeBody = "neon_operative", ownedOperatives = setOf("operative", "neon_operative")) })
    }

    @Test fun keyArtMatch() {
        assumeTrue(enabled)
        gameplay("keyart_neon_beam", 3.2f, profileSetup = {
            it.copy(operativeBody = "neon_operative", ownedOperatives = setOf("operative", "neon_operative"))
        }, setup = { s ->
            s.engine.build.take(com.cyberoperative.game.data.Upgrades.PLASMA_BEAM)
            s.engine.build.take(com.cyberoperative.game.data.Upgrades.PLASMA_BEAM)
            s.engine.debugStartPlan(com.cyberoperative.game.engine.LevelPlanner.plan(6, kotlin.random.Random(11), null, true))
        })
    }

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

    @Test fun bossRewards() {
        assumeTrue(enabled)
        gameplay("reward_counter_boss", 0f) { s ->
            s.engine.debugStartPlan(com.cyberoperative.game.engine.LevelPlanner.bossPlan(20, kotlin.random.Random(2)))
            repeat(240) { s.engine.update(1f / 60f) }
            s.engine.killEnemy(s.engine.boss!!)
            var guard = 0
            while (s.engine.phase != com.cyberoperative.game.engine.Phase.UPGRADE && guard++ < 600) s.engine.update(1f / 60f)
            s.chooseUpgrade(0)
        }
    }

    @Test fun pauseBossBlocked() {
        assumeTrue(enabled)
        gameplay("pause_boss_save_blocked", 0f) { s ->
            s.engine.debugStartPlan(com.cyberoperative.game.engine.LevelPlanner.bossPlan(10, kotlin.random.Random(1)))
            repeat(60) { s.engine.update(1f / 60f) }
            s.paused = true
        }
    }

    @Test fun pauseSave() {
        assumeTrue(enabled)
        gameplay("pause_save", 1f) { s -> s.paused = true }
    }

    @Test fun upgradeBar() {
        assumeTrue(enabled)
        gameplay("hud_upgrade_bar", 3f) { s ->
            val U = com.cyberoperative.game.data.Upgrades
            for (d in listOf(U.PAYLOAD_BOOST, U.PAYLOAD_BOOST, U.PACKET_NODES, U.MALWARE_MISSILES, U.LOGIC_BOMBS, U.ARC_DISCHARGE, U.FIREWALL, U.QUANTUM_RAILGUN, U.ORBITAL_STRIKE, U.PLASMA_BEAM)) s.engine.build.take(d)
        }
    }

    @Test fun menuContinue() {
        assumeTrue(enabled)
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repo = SaveRepository(ctx)
        val g = com.cyberoperative.game.engine.GameEngine(com.cyberoperative.game.engine.RunConfig(difficulty = com.cyberoperative.game.engine.Difficulty.HARD))
        g.debugJumpToLevel(12)
        repo.update { it.copy(savedRun = g.snapshot()!!.encode()) }
        compose.mainClock.autoAdvance = false
        compose.setContent { CyberOperativeTheme { MainMenuScreen(repo.current) {} } }
        compose.mainClock.advanceTimeBy(500)
        save("menu_continue")
    }

    @Test fun difficultyPicker() {
        assumeTrue(enabled)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CyberOperativeTheme {
                com.cyberoperative.game.ui.menu.DifficultyPicker(
                    com.cyberoperative.game.engine.GameMode.CAMPAIGN, com.cyberoperative.game.engine.Difficulty.MEDIUM,
                    "LEVEL 12 · HARD", opLevel = 11, onStart = {}, onCancel = {}
                )
            }
        }
        compose.mainClock.advanceTimeBy(100)
        save("difficulty_picker")
    }

    @Test fun enemyRoster() {
        assumeTrue(enabled)
        gameplay("enemy_roster", 0f) { s ->
            val g = s.engine
            g.debugJumpToLevel(45)
            for (e in g.enemies.items) e.active = false
            val picks = listOf(
                "poly_crawler", "enc_brute", "botnet_mite", "ransom_golem", "zeroday_spitter", "adware_scatter",
                "spyware_marksman", "cryptojack_beacon", "forkbomb_husk", "keylogger_gnat", "backdoor_bull", "stealth_lancer",
                "kernel_spire", "enc_sentry", "ransom_mortar", "zeroday_glitch", "adware_blinker", "kernel_phantom",
                "glitch_daemon", "glitch_shard", "glitch_hydra"
            )
            picks.forEachIndexed { i, id ->
                val e = g.spawnEnemyAt(com.cyberoperative.game.data.Enemies.byId(id), null, 90f + (i % 4) * 180f, 230f + (i / 4) * 130f, telegraph = false)
                e?.speed = 0f
            }
            repeat(10) { g.update(1f / 60f) }
        }
    }

    @Test fun glitchedBoss() {
        assumeTrue(enabled)
        gameplay("boss_glitched", 5f) { s ->
            s.engine.debugStartPlan(com.cyberoperative.game.engine.LevelPlanner.bossPlan(30, kotlin.random.Random(1)).copy(glitchedBoss = true))
        }
    }

    @Test fun arsenal() {
        assumeTrue(enabled)
        gameplay("arsenal_weapons", 0f) { s ->
            val g = s.engine
            g.debugJumpToLevel(12)
            for (id in listOf("subnet_laser", "malware_swamp", "golden_boomerang", "chain_storm", "seeker_swarm", "captcha_mines", "heartbeat_pulse"))
                g.build.take(com.cyberoperative.game.data.Upgrades.byId(id))
            for (e in g.enemies.items) if (e.active) { e.hp = 1e6f; e.maxHp = 1e6f }
            val bot = Bot(g, dodge = true)
            var t = 0f
            while (t < 5.2f) { bot.step(1f / 30f); t += 1f / 30f; for (e in g.enemies.items) if (e.active && e.hp < 1e5f) { e.hp = 1e6f; e.maxHp = 1e6f } }
        }
    }

    @Test fun xray() {
        assumeTrue(enabled)
        gameplay("xray_behind_cover", 0f) { s ->
            val g = s.engine
            var lvl = 5
            while (g.arena.obstacles.count { it.kind == com.cyberoperative.game.data.ObstacleKind.SERVER_RACK } < 2 && lvl < 80) g.debugJumpToLevel(++lvl)
            for (e in g.enemies.items) e.active = false
            val defs = listOf(com.cyberoperative.game.data.Enemies.TROJAN, com.cyberoperative.game.data.Enemies.MALWARE, com.cyberoperative.game.data.Enemies.SQL_INJECTOR)
            var i = 0
            for (o in g.arena.obstacles.sortedByDescending { it.rect.bottom }) {
                if (i >= 5) break
                val d = defs[i % defs.size]
                val x = (o.rect.left + o.rect.right) / 2f
                val y = o.rect.top - d.radius - 4f
                if (!g.arena.isFree(x, y, d.radius)) continue
                g.spawnEnemyAt(d, null, x, y, telegraph = false)?.speed = 0f
                i++
            }
            repeat(10) { g.update(1f / 60f) }
        }
    }

    @Test fun bossWithUpgradeBar() {
        assumeTrue(enabled)
        gameplay("hud_upgrade_bar_boss", 5f) { s ->
            val U = com.cyberoperative.game.data.Upgrades
            for (d in listOf(U.PAYLOAD_BOOST, U.PACKET_NODES, U.MALWARE_MISSILES, U.LOGIC_BOMBS, U.ARC_DISCHARGE, U.FIREWALL, U.PLASMA_BEAM)) s.engine.build.take(d)
            s.engine.debugStartPlan(com.cyberoperative.game.engine.LevelPlanner.bossPlan(10, kotlin.random.Random(1)))
        }
    }

    @Test fun env0() { assumeTrue(enabled); envShot("env_0", 2) }
    @Test fun env1() { assumeTrue(enabled); envShot("env_1", 5) }
    @Test fun env2() { assumeTrue(enabled); envShot("env_2", 8) }
    @Test fun env3() { assumeTrue(enabled); envShot("env_3", 11) }
    @Test fun env4() { assumeTrue(enabled); envShot("env_4", 14) }
    @Test fun env5() { assumeTrue(enabled); envShot("env_5", 17) }
    @Test fun env6() { assumeTrue(enabled); envShot("env_6", 20) }
    @Test fun env7() { assumeTrue(enabled); envShot("env_7", 23) }

    private fun envShot(name: String, lvl: Int) = gameplay(name, 0f) { s ->
        s.engine.debugJumpToLevel(lvl)
        for (e in s.engine.enemies.items) e.active = false
        repeat(200) { s.engine.update(1f / 60f) }
        for (e in s.engine.enemies.items) e.active = false
    }

    /** Clears the current level (no threats left) and picks every upgrade, ending at the open gate. */
    private fun clearToPortal(g: com.cyberoperative.game.engine.GameEngine) {
        var guard = 0
        while (g.phase != com.cyberoperative.game.engine.Phase.PORTAL && guard++ < 3000) {
            for (e in g.enemies.items) if (e.active) g.killEnemy(e)
            if (g.phase == com.cyberoperative.game.engine.Phase.UPGRADE) g.chooseUpgrade(0)
            g.update(1f / 60f)
        }
    }

    @Test fun shopRoom() {
        assumeTrue(enabled)
        gameplay("upgrade_shop", 0f) { s ->
            val g = s.engine
            g.debugJumpToLevel(12)
            clearToPortal(g)
            g.offerShop()
            var guard = 0
            while (!g.inShop && guard++ < 1500) { g.setInput(-1f, (g.shopGateY - g.py) / 200f); g.update(1f / 60f) }
            g.debugGrantEuros(3000)
            guard = 0
            while (!g.atShopCounter && guard++ < 1200) { g.setInput((g.arena.width / 2f - g.px) / 100f, -1f); g.update(1f / 60f) }
            g.setInput(0f, 0f)
            repeat(30) { g.update(1f / 60f) }
        }
    }

    @Test fun shopGate() {
        assumeTrue(enabled)
        gameplay("shop_gate_message", 0f) { s ->
            val g = s.engine
            g.debugJumpToLevel(6)
            clearToPortal(g)
            g.offerShop()
        }
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


@androidx.compose.runtime.Composable
private fun NeonBoard() {
    val skin = com.cyberoperative.game.data.OperativeSkins.DEFAULT
    androidx.compose.foundation.Canvas(
        androidx.compose.ui.Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF040B18))
    ) {
        with(com.cyberoperative.game.ui.game.OperativeFigures) {
            // Hero size, idle.
            drawFigure(com.cyberoperative.game.ui.game.BodyStyle.NEON, skin, size.width / 2f, size.height * 0.52f, size.width / 95f, -0.25f, false, 0.4f)
            // Walking left / aiming right, and in-game scale.
            drawFigure(com.cyberoperative.game.ui.game.BodyStyle.NEON, skin, size.width * 0.2f, size.height * 0.9f, size.width / 260f, 2.8f, true, 0.13f)
            drawFigure(com.cyberoperative.game.ui.game.BodyStyle.NEON, skin, size.width * 0.5f, size.height * 0.9f, size.width / 260f, -0.4f, true, 0.3f)
            drawFigure(com.cyberoperative.game.ui.game.BodyStyle.NEON, skin, size.width * 0.8f, size.height * 0.9f, size.width / 260f, 0.2f, false, 1.1f)
        }
    }
}
