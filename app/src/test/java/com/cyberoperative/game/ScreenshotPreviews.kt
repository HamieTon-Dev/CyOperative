package com.cyberoperative.game

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import com.cyberoperative.game.ui.common.BuildStamp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.test.core.app.ApplicationProvider
import com.cyberoperative.game.audio.AudioManager
import com.cyberoperative.game.save.SaveRepository
import com.cyberoperative.game.ui.game.GameScreen
import com.cyberoperative.game.ui.game.GameSession
import com.cyberoperative.game.ui.menu.MainMenuScreen
import com.cyberoperative.game.ui.splash.BootTerminal
import com.cyberoperative.game.ui.theme.CyberOperativeTheme
import org.junit.Assume.assumeTrue
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
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
        compose.setContent {
            CyberOperativeTheme {
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
                    MainMenuScreen(repo.current) {}
                    BuildStamp()
                }
            }
        }
        compose.mainClock.advanceTimeBy(1500)
        save("main_menu")
    }

    @Test fun skins() {
        assumeTrue(enabled)
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repo = SaveRepository(ctx)
        repo.update { it.copy(diamonds = 1200, ownedSkins = setOf("default", "red_hat", "neon_custom"), selectedSkin = "red_hat", neonColor = 0xFF00FFD0, campaignsCompleted = 1) }
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

    private fun cls(p: com.cyberoperative.game.save.PlayerProfile, id: String, skin: String = "default") =
        com.cyberoperative.game.meta.StoreManager.selectClass(p, id).copy(selectedSkin = skin, ownedSkins = p.ownedSkins + skin)
    @Test fun arenaAgent() { assumeTrue(enabled); gameplay("arena_25d_agent", 2.5f, profileSetup = { cls(it, "operative") }) }
    @Test fun arenaMech() { assumeTrue(enabled); gameplay("arena_25d_mech", 3.5f, profileSetup = { cls(it, "juggernaut", "blue_hat") }) }
    @Test fun arenaRunner() { assumeTrue(enabled); gameplay("arena_25d_runner", 5f, profileSetup = { cls(it, "phantom", "cryptographer").copy(selectedBackground = "rainfall") }) }
    @Test fun arenaMage() { assumeTrue(enabled); gameplay("arena_25d_mage", 4f, profileSetup = { cls(it, "packet_mage") }) }
    @Test fun arenaTechnician() { assumeTrue(enabled); gameplay("arena_25d_technician", 4f, profileSetup = { cls(it, "technician") }) }
    @Test fun arenaPhantomCloaked() {
        assumeTrue(enabled)
        gameplay("arena_25d_phantom_cloaked", 7.6f, profileSetup = { cls(it, "phantom") })
    }

    /** Every class in DEFAULT, TITANIUM and PURPLE (reference sheet for the owner). */
    @Test fun classSkinSheet() {
        assumeTrue(enabled)
        val skins = listOf(com.cyberoperative.game.data.OperativeSkins.DEFAULT, com.cyberoperative.game.data.OperativeSkins.TITANIUM, com.cyberoperative.game.data.OperativeSkins.PURPLE)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CyberOperativeTheme {
                androidx.compose.foundation.layout.Column(
                    androidx.compose.ui.Modifier.fillMaxSize().background(com.cyberoperative.game.ui.theme.Palette.Background).padding(12.dp)
                ) {
                    androidx.compose.foundation.layout.Row(androidx.compose.ui.Modifier.fillMaxWidth()) {
                        androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.width(96.dp))
                        for (sk in skins) androidx.compose.material3.Text(
                            if (sk.id == "default") "DEFAULT" else sk.name, color = androidx.compose.ui.graphics.Color(sk.edge),
                            modifier = androidx.compose.ui.Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            style = androidx.compose.material3.MaterialTheme.typography.titleSmall
                        )
                    }
                    for (cls in com.cyberoperative.game.data.Operatives.all) {
                        androidx.compose.foundation.layout.Row(
                            androidx.compose.ui.Modifier.fillMaxWidth().weight(1f),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.Text(cls.name, color = com.cyberoperative.game.ui.theme.Palette.TextPrimary,
                                modifier = androidx.compose.ui.Modifier.width(96.dp), style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
                            for (sk in skins) {
                                androidx.compose.foundation.Canvas(androidx.compose.ui.Modifier.weight(1f).fillMaxSize()) {
                                    val u = size.height / 80f
                                    with(com.cyberoperative.game.ui.game.OperativeFigures) {
                                        drawFigure(com.cyberoperative.game.ui.game.BodyStyle.byId(cls.bodyId), sk, size.width / 2f, size.height * 0.92f, u, -0.4f, false, 1.3f)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(300)
        save("class_skin_sheet")
    }

    private fun goodGame(name: String, until: (GameSession) -> Boolean) = gameplay(name, 0f, setup = { s ->
        s.engine.debugStartPlan(com.cyberoperative.game.engine.LevelPlanner.bossPlan(10, kotlin.random.Random(1))
            .copy(boss = com.cyberoperative.game.data.Bosses.byId("good_game"), glitchedBoss = false))
        val me = s.engine.operatives[0]
        var t = 0f
        while (t < 20f && !until(s)) { me.invuln = 99f; me.hp = me.build.stats.maxHp; s.engine.setInput(0f, 0f); s.engine.update(1f / 60f); t += 1f / 60f }
    })
    @Test fun goodGameCharging() { assumeTrue(enabled); goodGame("boss_good_game_charging") { it.engine.bossCharge > 0.4f } }
    @Test fun goodGameShell() {
        assumeTrue(enabled)
        var seen = 0f
        goodGame("boss_good_game_shell") { s ->
            val shell = s.engine.projectiles.items.any { it.active && !it.friendly && it.splash > 0f }
            if (shell) seen += 1f / 60f
            shell && seen > 0.7f
        }
    }

    @Test fun classPicker() {
        assumeTrue(enabled)
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repo = SaveRepository(ctx)
        repo.update {
            cls(it, "technician").copy(classRecords = mapOf(
                "technician" to com.cyberoperative.game.save.ClassRecord(bestNormal = 112, bestHard = 40),
                "operative" to com.cyberoperative.game.save.ClassRecord(bestNormal = 104, bestHard = 101)
            ), selectedSkin = "titanium")
        }
        compose.mainClock.autoAdvance = false
        compose.setContent { CyberOperativeTheme { com.cyberoperative.game.ui.menu.OperativeScreen(repo) {} } }
        compose.mainClock.advanceTimeBy(800)
        save("class_picker")
    }
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
    @Test fun bossIntro4() { assumeTrue(enabled); bossIntro("boss_intro_4_dossier", 2.2f) }

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

    /** Cleared room: the next-room hint and a just-picked-up chip line must not overlap. */
    @Test fun hintAndPickupSeparated() {
        assumeTrue(enabled)
        gameplay("hint_and_pickup", 0f, setup = { s ->
            val g = s.engine
            var guard = 0
            while (g.phase != com.cyberoperative.game.engine.Phase.PORTAL && guard++ < 60 * 90) {
                for (e in g.enemies.items) if (e.active) g.killEnemy(e)
                g.update(1f / 60f)
            }
            repeat(200) { g.update(1f / 60f) }
            g.loot += com.cyberoperative.game.engine.Loot(com.cyberoperative.game.engine.LootKind.UPGRADE, g.px, g.py, upgradeId = com.cyberoperative.game.data.Upgrades.PAYLOAD_BOOST.id)
            repeat(10) { g.update(1f / 60f) }
        })
    }

    private fun gameplay(
        name: String, simulateSeconds: Float,
        profileSetup: (com.cyberoperative.game.save.PlayerProfile) -> com.cyberoperative.game.save.PlayerProfile = { it },
        interact: () -> Unit = {},
        difficulty: com.cyberoperative.game.engine.Difficulty = com.cyberoperative.game.engine.Difficulty.MEDIUM,
        setup: (GameSession) -> Unit = {}
    ) {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repo = SaveRepository(ctx)
        repo.update { profileSetup(it.copy(tutorialDone = true)) }
        val session = GameSession(repo, AudioManager(ctx), com.cyberoperative.game.engine.GameMode.CAMPAIGN, difficulty)
        setup(session)
        val bot = Bot(session.engine, dodge = true)
        var t = 0f
        while (t < simulateSeconds) { bot.step(1f / 30f); t += 1f / 30f }
        session.engine.setInput(0f, 0f)
        compose.mainClock.autoAdvance = false
        compose.setContent { CyberOperativeTheme { GameScreen(session, true, {}, {}) } }
        compose.mainClock.advanceTimeBy(100)
        interact()
        compose.mainClock.advanceTimeBy(400)
        save(name)
    }

    /** Android PC MODE renders (owner, 2026-10-11): touch buttons, chips, landscape layout. */
    private fun pcMode(landscape: Boolean, block: () -> Unit) {
        com.cyberoperative.game.ui.common.PlayMode.pcRules = true
        com.cyberoperative.game.ui.common.PlayMode.landscape = landscape
        try { block() } finally {
            com.cyberoperative.game.ui.common.PlayMode.pcRules = false
            com.cyberoperative.game.ui.common.PlayMode.landscape = false
            com.cyberoperative.game.data.ArenaShape.wide = false
            com.cyberoperative.game.data.ArenaShape.pcRules = false
        }
    }

    private fun pcProfile(p: com.cyberoperative.game.save.PlayerProfile, landscape: Boolean) =
        p.copy(settings = p.settings.copy(landscape = landscape))

    @Test fun pcModePortrait() {
        assumeTrue(enabled)
        pcMode(false) {
            gameplay("pc_mode_portrait", 6f, profileSetup = { pcProfile(it, false) }) { s ->
                val e = s.engine
                val up = com.cyberoperative.game.data.Upgrades.all.first { it.rarity == com.cyberoperative.game.data.Rarity.EPIC && !com.cyberoperative.game.data.Upgrades.isWeapon(it) }
                e.loot += com.cyberoperative.game.engine.Loot(com.cyberoperative.game.engine.LootKind.UPGRADE, e.px - 120f, e.py - 220f, upgradeId = up.id)
            }
        }
    }

    @Test @Config(qualifiers = "w891dp-h411dp-xxhdpi") fun pcModeLandscape() {
        assumeTrue(enabled)
        pcMode(true) {
            gameplay("pc_mode_landscape", 6f, profileSetup = { pcProfile(it, true) })
        }
    }

    /** 0.47.3 HUD review: 34 buffs, a cleared room's door banner, damage numbers. */
    private fun hudReview(name: String) {
        gameplay(name, 8f, profileSetup = { it.copy(highestLevel = 30) }) { s ->
            val e = s.engine
            e.debugJumpToLevel(14)
            for (u in com.cyberoperative.game.data.Upgrades.all.filter { !it.instant && e.build.isEligible(it) }.take(34)) e.build.take(u)
            for (k in 0 until 4) e.addText(e.px - 120f + k * 80f, e.py - 160f - k * 30f, listOf("11867", "100826", "7543247", "12345678901234")[k], com.cyberoperative.game.engine.TextKind.NORMAL)
        }
    }

    @Test fun hudReviewPortrait() { assumeTrue(enabled); hudReview("hud_review_portrait") }

    @Test @Config(qualifiers = "w891dp-h411dp-xxhdpi") fun hudReviewLandscape() {
        assumeTrue(enabled)
        pcMode(true) { hudReview("hud_review_landscape") }
    }

    @Test @Config(qualifiers = "w891dp-h411dp-xxhdpi") fun pcModeLandscapeMenu() {
        assumeTrue(enabled)
        pcMode(true) {
            val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
            val repo = SaveRepository(ctx)
            compose.mainClock.autoAdvance = false
            compose.setContent { CyberOperativeTheme { MainMenuScreen(repo.current) {} } }
            compose.mainClock.advanceTimeBy(1500)
            save("pc_mode_landscape_menu")
        }
    }

    @Test fun pcModeSettings() {
        assumeTrue(enabled)
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repo = SaveRepository(ctx)
        repo.update { pcProfile(it, true) }
        compose.mainClock.autoAdvance = false
        compose.setContent { CyberOperativeTheme { com.cyberoperative.game.ui.menu.SettingsScreen(repo, AudioManager(ctx)) {} } }
        compose.mainClock.advanceTimeBy(500)
        save("pc_mode_settings")
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

    /** Victory sequence: the boss shattering, then the THREAT NEUTRALIZED card. */
    private fun victorySetup(s: GameSession, after: Float) {
        s.engine.debugStartPlan(com.cyberoperative.game.engine.LevelPlanner.bossPlan(30, kotlin.random.Random(1)).copy(boss = com.cyberoperative.game.data.Bosses.byId("white_eye"), glitchedBoss = false))
        repeat(((com.cyberoperative.game.engine.BossBrain.INTRO_SECONDS + 1.5f) * 60).toInt()) { s.engine.update(1f / 60f) }
        s.engine.killEnemy(s.engine.boss!!)
        var t = 0f
        while (t < after) { s.onFrame(1f / 60f); t += 1f / 60f }
    }

    @Test fun victoryShatter() { assumeTrue(enabled); gameplay("victory_shatter", 0f) { victorySetup(it, 0f) } }
    @Test fun victoryCard() { assumeTrue(enabled); gameplay("victory_card", 0f) { victorySetup(it, 2.2f) } }

    @Test fun pauseBuildTab() {
        assumeTrue(enabled)
        gameplay("pause_build", 0f, interact = { compose.onAllNodesWithText("BUILD")[0].performClick() }) { s ->
            for (id in listOf("encryption_blades", "multishot", "firewall", "packet_node")) com.cyberoperative.game.data.Upgrades.all.firstOrNull { it.id == id }?.let { s.engine.build.take(it) }
            s.engine.build.take(com.cyberoperative.game.data.Upgrades.all.first { !it.instant })
            s.onFrame(1f / 60f)
            s.paused = true
        }
    }

    @Test fun pauseSettingsTab() {
        assumeTrue(enabled)
        gameplay("pause_settings", 0f, interact = { compose.onAllNodesWithText("SETTINGS")[0].performClick() }) { s -> s.paused = true }
    }

    @Test fun bossCodex() {
        assumeTrue(enabled)
        val profile = com.cyberoperative.game.save.PlayerProfile(bossRecords = mapOf(
            "breach" to com.cyberoperative.game.save.BossRecord(3, 3, 74.2f),
            "botmaster" to com.cyberoperative.game.save.BossRecord(2, 1, 102.5f),
            "worm_prime" to com.cyberoperative.game.save.BossRecord(1, 0, 0f),
            "ransom" to com.cyberoperative.game.save.BossRecord(1, 1, 88f),
            "circuit_hydra" to com.cyberoperative.game.save.BossRecord(1, 1, 160f)
        ))
        compose.mainClock.autoAdvance = false
        compose.setContent { CyberOperativeTheme { com.cyberoperative.game.ui.menu.BossCodexScreen(profile) {} } }
        compose.mainClock.advanceTimeBy(1200)
        save("boss_codex")
        compose.onAllNodesWithText("BREACH")[0].performClick()
        compose.mainClock.advanceTimeBy(1200)
        save("boss_codex_dossier")
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

    private fun manyBuffs(s: GameSession) {
        val U = com.cyberoperative.game.data.Upgrades
        for (d in listOf(U.PAYLOAD_BOOST, U.PAYLOAD_BOOST, U.PACKET_NODES, U.MALWARE_MISSILES, U.LOGIC_BOMBS, U.ARC_DISCHARGE, U.ENCRYPTION_ARMOR, U.QUANTUM_RAILGUN, U.ORBITAL_STRIKE, U.PLASMA_BEAM)) s.engine.build.take(d)
    }

    private class FakeCoop(registered: Boolean) : com.cyberoperative.game.net.CoopBackend {
        override val available = true
        override val profile = kotlinx.coroutines.flow.MutableStateFlow(
            if (registered) com.cyberoperative.game.net.CoopProfile("me", "NEON_FOX", "K7QM-4XPZ") else null
        )
        override suspend fun start() = Result.success(Unit)
        override suspend fun register(callsign: String) = Result.failure<com.cyberoperative.game.net.CoopProfile>(Exception())
        override fun friends() = kotlinx.coroutines.flow.flowOf(listOf(
            com.cyberoperative.game.net.Friend("a", "GHOSTWIRE", true),
            com.cyberoperative.game.net.Friend("b", "BYTE_RAVEN", true),
            com.cyberoperative.game.net.Friend("c", "NULLPTR", false)
        ))
        override fun friendRequests() = kotlinx.coroutines.flow.flowOf(listOf(com.cyberoperative.game.net.FriendRequest("r", "z", "SYNTH_ACE")))
        override suspend fun sendFriendRequest(code: String) = Result.success("")
        override suspend fun answerFriendRequest(request: com.cyberoperative.game.net.FriendRequest, accept: Boolean) = Result.success(Unit)
        override suspend fun removeFriend(uid: String) = Result.success(Unit)
        override fun invites() = kotlinx.coroutines.flow.flowOf(listOf(com.cyberoperative.game.net.Invite("i", "a", "GHOSTWIRE", "room", 0L)))
        override suspend fun invite(friend: com.cyberoperative.game.net.Friend, me: com.cyberoperative.game.net.RoomPlayer) = Result.failure<com.cyberoperative.game.net.CoopRoom>(Exception())
        override suspend fun answerInvite(invite: com.cyberoperative.game.net.Invite, accept: Boolean, me: com.cyberoperative.game.net.RoomPlayer) = Result.failure<com.cyberoperative.game.net.CoopRoom?>(Exception())
    }

    private fun coopShot(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent { CyberOperativeTheme { content() } }
        compose.mainClock.advanceTimeBy(400)
        save(name)
    }

    @Test fun coopHub() {
        assumeTrue(enabled)
        coopShot("coop_hub") { com.cyberoperative.game.ui.menu.CoopScreen(FakeCoop(true), com.cyberoperative.game.save.PlayerProfile(), {}, {}) }
    }

    @Test fun coopRegister() {
        assumeTrue(enabled)
        coopShot("coop_register") { com.cyberoperative.game.ui.menu.CoopScreen(FakeCoop(false), com.cyberoperative.game.save.PlayerProfile(), {}, {}) }
    }

    @Test fun coopLobby() {
        assumeTrue(enabled)
        val st = com.cyberoperative.game.net.RoomState(
            "room", host = com.cyberoperative.game.net.RoomPlayer("h", "NEON_FOX", opLevel = 42), guest = com.cyberoperative.game.net.RoomPlayer("g", "GHOSTWIRE", opLevel = 17),
            hostOnline = true, guestOnline = true
        )
        val (room, _) = LoopbackRoom.pair(st)
        coopShot("coop_lobby") { com.cyberoperative.game.ui.menu.CoopLobbyScreen(room, com.cyberoperative.game.engine.Difficulty.MEDIUM, onStart = {}, onLeave = {}) }
    }

    @Test fun coopFight() {
        assumeTrue(enabled)
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repo = SaveRepository(ctx)
        repo.update { it.copy(tutorialDone = true) }
        val st = com.cyberoperative.game.net.RoomState(
            "room", status = com.cyberoperative.game.net.RoomStatus.PLAYING,
            host = com.cyberoperative.game.net.RoomPlayer("h", "NEON_FOX", opLevel = 42),
            guest = com.cyberoperative.game.net.RoomPlayer("g", "GHOSTWIRE", opLevel = 17, skin = "blue_hat", body = "mech"),
            hostOnline = true, guestOnline = true, seed = 7L
        )
        val (hostRoom, guestRoom) = LoopbackRoom.pair(st)
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
        val host = GameSession(repo, AudioManager(ctx), coop = com.cyberoperative.game.ui.game.CoopLink(hostRoom, st, scope))
        val guest = GameSession(repo, AudioManager(ctx), coop = com.cyberoperative.game.ui.game.CoopLink(guestRoom, st, scope))
        var t = 0f
        while (t < 4f) {
            val g = guest.engine
            g.setInput(0.6f, -0.3f)
            host.onFrame(1f / 30f); guest.onFrame(1f / 30f); t += 1f / 30f
        }
        g0@ run { guest.engine.setInput(0f, 0f) }
        repeat(6) { host.onFrame(1f / 30f); guest.onFrame(1f / 30f) }
        compose.mainClock.autoAdvance = false
        compose.setContent { CyberOperativeTheme { GameScreen(guest, true, {}, {}) } }
        compose.mainClock.advanceTimeBy(100)
        save("coop_fight_guest_view")
    }

    private fun swapSetup(s: GameSession) {
        val W = com.cyberoperative.game.data.Weapons.upgrades
        for (w in W.take(7)) s.engine.build.take(w)
        val U = com.cyberoperative.game.data.Upgrades
        s.engine.debugOffer(listOf(
            com.cyberoperative.game.engine.UpgradeOffer(U.PAYLOAD_BOOST, 1),
            com.cyberoperative.game.engine.UpgradeOffer(U.KERNEL_OVERCLOCK, 1),
            com.cyberoperative.game.engine.UpgradeOffer(U.ORBITAL_STRIKE, 1)
        ))
    }

    @Test fun cardDetails() {
        assumeTrue(enabled)
        gameplay("card_hold_details", 0f, setup = ::swapSetup, interact = {
            compose.onAllNodesWithText("PAYLOAD BOOST")[0].performTouchInput { longClick() }
        })
    }

    @Test fun weaponCards() {
        assumeTrue(enabled)
        gameplay("weapon_cards", 0f, setup = ::swapSetup)
    }

    @Test fun weaponSwapGrid() {
        assumeTrue(enabled)
        gameplay("weapon_swap_grid", 0f, setup = ::swapSetup, interact = {
            compose.onAllNodesWithText("ORBITAL STRIKE")[0].performClick()
        })
    }

    @Test fun weaponSwapConfirm() {
        assumeTrue(enabled)
        val third = com.cyberoperative.game.data.Weapons.upgrades[2].name
        gameplay("weapon_swap_confirm", 0f, setup = ::swapSetup, interact = {
            compose.onAllNodesWithText("ORBITAL STRIKE")[0].performClick()
            compose.mainClock.advanceTimeBy(200)
            compose.mainClock.advanceTimeBy(200)
            compose.onAllNodesWithText(third)[0].performClick()
        })
    }

    @Test fun upgradesWeaponSlots() {
        assumeTrue(enabled)
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repo = SaveRepository(ctx)
        repo.update { it.copy(euros = 74_710, operativeXp = 22_000L, permanentUpgrades = mapOf("weapon_slots" to 2, "max_hp" to 8)) }
        compose.mainClock.autoAdvance = false
        compose.setContent { CyberOperativeTheme { com.cyberoperative.game.ui.menu.PermanentUpgradesScreen(repo, AudioManager(ctx)) {} } }
        compose.mainClock.advanceTimeBy(300)
        save("upgrades_weapon_slots")
    }

    @Test fun upgradesMastery() {
        assumeTrue(enabled)
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repo = SaveRepository(ctx)
        repo.update {
            it.copy(
                euros = 2_500_000, operativeXp = 30_000_000L,
                permanentUpgrades = mapOf("max_hp" to 145, "base_damage" to 60, "attack_speed" to 90, "move_speed" to 10, "attack_range" to 4)
            )
        }
        compose.mainClock.autoAdvance = false
        compose.setContent { CyberOperativeTheme { com.cyberoperative.game.ui.menu.PermanentUpgradesScreen(repo, AudioManager(ctx)) {} } }
        compose.mainClock.advanceTimeBy(300)
        save("upgrades_mastery")
    }

    @Test fun hudCollapsed() {
        assumeTrue(enabled)
        gameplay("hud_collapsed", 3f, setup = ::manyBuffs, interact = {
            compose.onNodeWithContentDescription("Hide HUD").performClick()
        })
    }

    @Test fun hudChipDetails() {
        assumeTrue(enabled)
        gameplay("hud_chip_details", 3f, setup = ::manyBuffs, interact = {
            compose.onAllNodesWithContentDescription("PLASMA", substring = true)[0].performClick()
        })
    }

    @Test @Config(qualifiers = "w320dp-h640dp-hdpi") fun hudNarrow() {
        assumeTrue(enabled)
        gameplay("hud_narrow_320dp", 3f, setup = ::manyBuffs)
    }

    @Test fun upgradeBar() {
        assumeTrue(enabled)
        gameplay("hud_upgrade_bar", 3f) { s ->
            val U = com.cyberoperative.game.data.Upgrades
            for (d in listOf(U.PAYLOAD_BOOST, U.PAYLOAD_BOOST, U.PACKET_NODES, U.MALWARE_MISSILES, U.LOGIC_BOMBS, U.ARC_DISCHARGE, U.ENCRYPTION_ARMOR, U.QUANTUM_RAILGUN, U.ORBITAL_STRIKE, U.PLASMA_BEAM)) s.engine.build.take(d)
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
            for (d in listOf(U.PAYLOAD_BOOST, U.PACKET_NODES, U.MALWARE_MISSILES, U.LOGIC_BOMBS, U.ARC_DISCHARGE, U.ENCRYPTION_ARMOR, U.PLASMA_BEAM)) s.engine.build.take(d)
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

    // World Kit (owner sheet, 2026-10-10): rooms at several depths, plus a boss room.
    @Test fun worldKit1() { assumeTrue(enabled); envShot("world_kit_l7", 7) }
    @Test fun worldKit2() { assumeTrue(enabled); envShot("world_kit_l46", 46) }
    @Test fun worldKit3() { assumeTrue(enabled); envShot("world_kit_l83", 83) }
    /** World Kit 05/07: a room with data-vault crates (first such level). */
    @Test fun worldKitCrates() {
        assumeTrue(enabled)
        gameplay("world_kit_crates", 0f) { s ->
            for (lvl in 2..90) {
                s.engine.debugJumpToLevel(lvl)
                if (s.engine.arena.obstacles.any { it.kind == com.cyberoperative.game.data.ObstacleKind.CRATES }) break
            }
            for (e in s.engine.enemies.items) e.active = false
            repeat(200) { s.engine.update(1f / 60f) }
            for (e in s.engine.enemies.items) e.active = false
        }
    }

    @Test fun worldKitBoss() {
        assumeTrue(enabled)
        gameplay("world_kit_boss", 0f) { s ->
            s.engine.debugStartPlan(com.cyberoperative.game.engine.LevelPlanner.bossPlan(90, kotlin.random.Random(1)).copy(boss = com.cyberoperative.game.data.Bosses.forLevel(90), glitchedBoss = false))
            repeat(260) { s.engine.update(1f / 60f) }
            for (e in s.engine.enemies.items) if (e.boss == null) e.active = false
            for (p in s.engine.projectiles.items) p.active = false
            for (h in s.engine.hazards.items) h.active = false
        }
    }

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

    private fun toShopCounter(s: GameSession) {
        val g = s.engine
        g.debugJumpToLevel(12)
        clearToPortal(g)
        g.offerShop()
        var guard = 0
        while (!g.inShop && guard++ < 1500) { g.setInput((g.shopGateX - g.px) / 200f, (g.shopGateY - g.py) / 200f); g.update(1f / 60f) }
        g.debugGrantEuros(3000)
        guard = 0
        while (!g.atShopCounter && guard++ < 1200) { g.setInput((g.arena.width / 2f - g.px) / 100f, -1f); g.update(1f / 60f) }
        g.setInput(0f, 0f)
        repeat(30) { g.update(1f / 60f) }
    }

    private fun openShop() {
        compose.onAllNodesWithText("X,.,.X  OPEN SHOP")[0].performClick()
        compose.mainClock.advanceTimeBy(200)
    }

    /** The shop as a small window in the middle with an X (owner, 2026-10-11). */
    @Test fun shopWindowPortrait() {
        assumeTrue(enabled)
        gameplay("shop_window_portrait", 0f, interact = { openShop() }) { s -> toShopCounter(s) }
    }

    @Test @Config(qualifiers = "w891dp-h411dp-xxhdpi") fun shopWindowLandscape() {
        assumeTrue(enabled)
        pcMode(landscape = true) {
            gameplay("shop_window_landscape", 0f, interact = { openShop() }) { s -> toShopCounter(s) }
        }
    }

    /** Android landscape HUD (owner's mock-up, 2026-10-12): slim strip, thin boss bar. */
    @Test @Config(qualifiers = "w891dp-h411dp-xxhdpi") fun landscapeHudBoss() {
        assumeTrue(enabled)
        pcMode(landscape = true) {
            gameplay("landscape_hud_boss", 6f) { s ->
                val g = s.engine
                g.debugEnterRoom(com.cyberoperative.game.engine.CampaignMap.BOSS_INDEX)
                for (u in listOf(com.cyberoperative.game.data.Upgrades.MALWARE_MISSILES, com.cyberoperative.game.data.Upgrades.MULTISHOT, com.cyberoperative.game.data.Upgrades.PAYLOAD_BOOST, com.cyberoperative.game.data.Upgrades.ENCRYPTION_ARMOR, com.cyberoperative.game.data.Upgrades.PENETRATION))
                    if (g.build.isEligible(u)) g.build.take(u)
            }
        }
    }

    @Test @Config(qualifiers = "w891dp-h411dp-xxhdpi") fun landscapeHudRoom() {
        assumeTrue(enabled)
        pcMode(landscape = true) {
            gameplay("landscape_hud_room", 3f) { s -> s.engine.debugJumpToLevel(6) }
        }
    }

    /** The world server after a world boss, with the MOVE TO NEXT WORLD button (owner, 2026-10-12). */
    @Test @Config(qualifiers = "w891dp-h411dp-xxhdpi") fun worldServer() {
        assumeTrue(enabled)
        pcMode(landscape = true) {
            gameplay("world_server", 0f) { s ->
                val g = s.engine
                g.debugEnterRoom(com.cyberoperative.game.engine.CampaignMap.BOSS_INDEX)
                g.killEnemy(g.boss!!)
                var guard = 0
                while (g.phase != com.cyberoperative.game.engine.Phase.PORTAL && guard++ < 60 * 30) g.update(1f / 60f)
                g.loot.clear()
                g.operatives[0].px = g.worldServerX - 60f; g.operatives[0].py = g.worldServerY + 80f
                repeat(5) { g.update(1f / 60f) }
            }
        }
    }

    /** INFERNO look (owner, 2026-10-13): hell rooms, broken towers, claw marks, wall fire, meaner monsters. */
    @Test @Config(qualifiers = "w891dp-h411dp-xxhdpi") fun infernoRoom() {
        assumeTrue(enabled)
        pcMode(landscape = true) {
            gameplay("inferno_room", 2.5f, difficulty = com.cyberoperative.game.engine.Difficulty.inferno(3)!!) { s -> s.engine.debugEnterRoom(2) }
        }
    }

    @Test @Config(qualifiers = "w891dp-h411dp-xxhdpi") fun infernoBoss() {
        assumeTrue(enabled)
        pcMode(landscape = true) {
            gameplay("inferno_boss", 5f, difficulty = com.cyberoperative.game.engine.Difficulty.inferno(7)!!) { s ->
                s.engine.debugEnterRoom(com.cyberoperative.game.engine.CampaignMap.BOSS_INDEX)
            }
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
            while (!g.inShop && guard++ < 1500) { g.setInput((g.shopGateX - g.px) / 200f, (g.shopGateY - g.py) / 200f); g.update(1f / 60f) }
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

    @Test fun heroLineup() {
        assumeTrue(enabled)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CyberOperativeTheme {
                androidx.compose.foundation.layout.Column(
                    androidx.compose.ui.Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF060A12))
                ) {
                    androidx.compose.foundation.layout.Row {
                        com.cyberoperative.game.ui.common.OperativeEmblem(
                            com.cyberoperative.game.data.OperativeSkins.DEFAULT,
                            androidx.compose.ui.Modifier.size(200.dp), animate = false
                        )
                        androidx.compose.foundation.Canvas(androidx.compose.ui.Modifier.size(200.dp)) {
                            with(com.cyberoperative.game.ui.game.OperativeFigures) {
                                drawFigure(com.cyberoperative.game.ui.game.BodyStyle.NEON, com.cyberoperative.game.data.OperativeSkins.DEFAULT,
                                    size.width / 2f, size.height * 0.92f, 5.2f, 0f, false, 1f)
                            }
                        }
                    }
                    androidx.compose.foundation.Canvas(androidx.compose.ui.Modifier.fillMaxWidth().size(220.dp)) {
                        val looks = listOf(
                            com.cyberoperative.game.ui.common.HeroPalette.KEEPER_GOLD,
                            com.cyberoperative.game.ui.common.HeroPalette.KEEPER_TITANIUM,
                            com.cyberoperative.game.ui.common.HeroPalette.KEEPER_BLACK,
                            com.cyberoperative.game.ui.common.HeroPalette.KEEPER_SPECTRUM
                        )
                        for ((i, p) in looks.withIndex()) with(com.cyberoperative.game.ui.game.OperativeFigures) {
                            drawShopkeeper(size.width * (i + 0.5f) / 4f, size.height * 0.92f, 3.6f, 1.3f, p)
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(100)
        save("hero_lineup")
    }

    private fun toSkipPrompt(g: com.cyberoperative.game.engine.GameEngine) {
        g.debugJumpToLevel(7)
        clearToPortal(g)
        g.offerShop()
        var guard = 0
        while (!g.skipShopPrompt && guard++ < 1500) {
            val a = g.arena
            if (a.lineOfSight(g.px, g.py, a.portalX, 0f, g.playerRadius)) g.setInput(a.portalX - g.px, -g.py)
            else { g.path.update(a, a.portalX, 30f, 1f); if (g.path.steer(g.px, g.py, g.playerRadius)) g.setInput(g.path.dir[0], g.path.dir[1]) }
            g.update(1f / 60f)
        }
        g.setInput(0f, 0f)
    }

    @Test fun skipShopPrompt() {
        assumeTrue(enabled)
        gameplay("shop_skip_prompt", 0f) { s -> toSkipPrompt(s.engine) }
    }

    @Test fun skipShopDeclined() {
        assumeTrue(enabled)
        gameplay("shop_skip_declined", 0f) { s ->
            toSkipPrompt(s.engine)
            s.engine.answerSkipShop(false)
            repeat(5) { s.engine.update(1f / 60f) }
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
