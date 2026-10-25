package com.cyberoperative.game.desktop

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.cyberoperative.game.audio.AudioManager
import com.cyberoperative.game.engine.Difficulty
import com.cyberoperative.game.engine.GameMode
import com.cyberoperative.game.save.SaveRepository
import com.cyberoperative.game.ui.game.GameScreen
import com.cyberoperative.game.ui.game.GameSession
import com.cyberoperative.game.ui.menu.MainMenuScreen
import com.cyberoperative.game.ui.menu.StoreScreen
import com.cyberoperative.game.ui.theme.CyberOperativeTheme
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

/**
 * Developer tool: renders key screens offscreen at a desktop resolution and writes PNGs
 * (`gradlew :desktop:shots`). Used to review the landscape layouts without a display.
 */
fun main(args: Array<String>) {
    val out = File(args.getOrNull(0) ?: "build/shots").apply { mkdirs() }
    val dir = kotlin.io.path.createTempDirectory("cyberop-shots").toFile()
    val save = SaveRepository(dir)
    com.cyberoperative.game.data.ArenaShape.wide = true
    com.cyberoperative.game.data.ArenaShape.pcRules = true
    com.cyberoperative.game.ui.common.PlayMode.pcRules = true
    com.cyberoperative.game.ui.common.PlayMode.landscape = true
    save.update { it.copy(diamonds = 1250, tutorialDone = true) }
    val audio = AudioManager()

    // 1080p desktop, then the Steam Deck screen (1280 × 800).
    for ((w, h, prefix) in listOf(Triple(1920, 1080, ""), Triple(1280, 800, "deck_"))) {
    fun shot(name0: String, frames: Int = 90, before: (Int) -> Unit = {}, content: @Composable () -> Unit) {
        val scene = ImageComposeScene(w, h, Density(w / 1280f)) {
            androidx.compose.runtime.CompositionLocalProvider(androidx.lifecycle.compose.LocalLifecycleOwner provides ShotLifecycle) {
                CyberOperativeTheme { content() }
            }
        }
        var t = 0L
        repeat(frames) { i -> before(i); scene.render(t); t += 16_666_667L }
        val img = scene.render(t)
        val name = prefix + name0
        File(out, "$name.png").writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        scene.close()
        println("wrote $name.png")
    }

    shot("splash", frames = 100) { com.cyberoperative.game.ui.splash.DeveloperSplash {} }
    shot("boot", frames = 150) { com.cyberoperative.game.ui.splash.BootTerminal(onSound = {}) {} }
    shot("menu") { MainMenuScreen(save.current) {} }
    run {
        // A cleared room with extra exit doors open.
        var s: GameSession? = null
        for (i in 0 until 40) {
            val c = GameSession(save, audio, GameMode.CAMPAIGN, Difficulty.MEDIUM)
            val e = c.engine
            e.debugJumpToLevel(4)
            repeat(60 * 40) {
                for (x in e.enemies.items) if (x.active && x.boss == null) e.killEnemy(x)
                e.update(1f / 60f)
                if (e.phase == com.cyberoperative.game.engine.Phase.UPGRADE && e.offer.isNotEmpty()) e.chooseUpgrade(0)
            }
            if (e.phase == com.cyberoperative.game.engine.Phase.PORTAL && e.exitDoors.size >= 2) { s = c; break }
        }
        s?.let { c -> shot("exits", frames = 30) { GameScreen(c, showDamageNumbers = true, onExitToMenu = {}, onNewOperation = {}) } }
    }
    run {
        val c = GameSession(save, audio, GameMode.CAMPAIGN, Difficulty.MEDIUM)
        val e = c.engine
        val ups = com.cyberoperative.game.data.Upgrades.all.filter { !com.cyberoperative.game.data.Upgrades.isWeapon(it) && !it.instant }
        com.cyberoperative.game.data.Rarity.entries.forEachIndexed { i, r ->
            ups.firstOrNull { it.rarity == r }?.let { d ->
                e.loot += com.cyberoperative.game.engine.Loot(com.cyberoperative.game.engine.LootKind.UPGRADE, e.px - 300f + i * 120f, e.py - 200f, upgradeId = d.id)
            }
        }
        for ((i, r) in listOf(com.cyberoperative.game.data.Rarity.RARE, com.cyberoperative.game.data.Rarity.EPIC, com.cyberoperative.game.data.Rarity.LEGENDARY).withIndex()) {
            com.cyberoperative.game.data.Upgrades.all.firstOrNull { com.cyberoperative.game.data.Upgrades.isWeapon(it) && it.rarity == r }?.let { d ->
                e.loot += com.cyberoperative.game.engine.Loot(com.cyberoperative.game.engine.LootKind.UPGRADE, e.px - 120f + i * 120f, e.py - 60f, upgradeId = d.id)
            }
        }
        shot("chips", frames = 20) { GameScreen(c, showDamageNumbers = true, onExitToMenu = {}, onNewOperation = {}) }
    }
    run {
        // 0.47.3 HUD review: many buffs in the compact three-row grid, with damage numbers flying.
        val c = GameSession(save, audio, GameMode.CAMPAIGN, Difficulty.MEDIUM)
        val e = c.engine
        e.debugJumpToLevel(14)
        for (u in com.cyberoperative.game.data.Upgrades.all.filter { !it.instant && e.build.isEligible(it) }.take(34)) e.build.take(u)
        repeat(60 * 3) { e.update(1f / 60f) }
        shot("hud_buffs", frames = 30) { GameScreen(c, showDamageNumbers = true, onExitToMenu = {}, onNewOperation = {}) }
    }
    // Campaign: a fresh room (doors shut), the room before the boss (skull door), the map overlay.
    run {
        val c = GameSession(save, audio, GameMode.CAMPAIGN, Difficulty.MEDIUM)
        shot("campaign_room1", frames = 60) { GameScreen(c, showDamageNumbers = true, onExitToMenu = {}, onNewOperation = {}) }
        val e = c.engine
        repeat(60 * 40) {
            for (x in e.enemies.items) if (x.active) e.killEnemy(x)
            e.update(1f / 60f)
            if (e.phase == com.cyberoperative.game.engine.Phase.PORTAL) return@repeat
        }
        shot("campaign_room1_clear", frames = 30) { GameScreen(c, showDamageNumbers = true, onExitToMenu = {}, onNewOperation = {}) }
        for (r in 1..8) e.clearedRooms += r
        e.debugEnterRoom(9)
        e.operatives[0].py = e.arena.height * 0.35f
        shot("campaign_boss_door", frames = 40) { GameScreen(c, showDamageNumbers = true, onExitToMenu = {}, onNewOperation = {}) }
        e.visitedRooms.addAll(0..9)
        shot("campaign_map", frames = 30) {
            androidx.compose.foundation.layout.Box {
                GameScreen(c, showDamageNumbers = true, onExitToMenu = {}, onNewOperation = {})
                com.cyberoperative.game.ui.game.WorldMapOverlay(c.hud, e.worldMap, 1f) {}
            }
        }
    }
    shot("settings") { com.cyberoperative.game.ui.menu.SettingsScreen(save, audio) {} }
    shot("store") { StoreScreen(save, audio, {}, {}) }
    run {
        val s = GameSession(save, audio, GameMode.CAMPAIGN, Difficulty.MEDIUM)
        s.showTutorial = true
        shot("briefing", frames = 60) { GameScreen(s, showDamageNumbers = true, onExitToMenu = {}, onNewOperation = {}) }
    }
    for ((name, level) in listOf("game_l3" to 3, "game_l14" to 14, "game_l42" to 42, "boss_l10" to 10, "boss_l30" to 30)) {
        val s = GameSession(save, audio, GameMode.CAMPAIGN, Difficulty.MEDIUM)
        s.engine.debugJumpToLevel(level)
        if (name == "game_l14") {
            // Floor pickups next to the operative, for review.
            val e = s.engine
            e.loot += com.cyberoperative.game.engine.Loot(com.cyberoperative.game.engine.LootKind.HP_CHARGE, e.px - 90f, e.py - 160f)
            e.loot += com.cyberoperative.game.engine.Loot(com.cyberoperative.game.engine.LootKind.PACKET_BOMB, e.px, e.py - 190f)
            e.loot += com.cyberoperative.game.engine.Loot(com.cyberoperative.game.engine.LootKind.CASH, e.px + 90f, e.py - 160f, 40)
            e.operatives[0].packetBombs = 1
            // One upgrade chip of each rarity in a row, and a GOLDEN one under the operative (picked up: HUD line).
            val ups = com.cyberoperative.game.data.Upgrades.all.filter { !com.cyberoperative.game.data.Upgrades.isWeapon(it) && !it.instant }
            com.cyberoperative.game.data.Rarity.entries.forEachIndexed { i, r ->
                ups.firstOrNull { it.rarity == r }?.let { d ->
                    e.loot += com.cyberoperative.game.engine.Loot(com.cyberoperative.game.engine.LootKind.UPGRADE, e.px - 330f + i * 130f, e.py - 260f, upgradeId = d.id)
                }
            }
        }
        if (name == "game_l3") {
            val e = s.engine
            val gold = com.cyberoperative.game.data.Upgrades.all.first { it.rarity == com.cyberoperative.game.data.Rarity.LEGENDARY && !com.cyberoperative.game.data.Upgrades.isWeapon(it) }
            e.loot += com.cyberoperative.game.engine.Loot(com.cyberoperative.game.engine.LootKind.UPGRADE, e.px, e.py - 400f, upgradeId = gold.id)
        }
        shot(name, frames = if (name == "game_l3") 200 else 240, before = { i -> if (name == "game_l3" && i == 170) { s.engine.operatives[0].px = s.engine.loot.last().x; s.engine.operatives[0].py = s.engine.loot.last().y }; if (name != "game_l3") s.engine.setInput(if (i in 60..120) 0.6f else 0f, if (i in 60..120) -0.8f else 0f) }) {
            GameScreen(s, showDamageNumbers = true, onExitToMenu = {}, onNewOperation = {})
        }
    }
    }
    System.exit(0)
}

internal object ShotLifecycle : androidx.lifecycle.LifecycleOwner {
    private val registry = androidx.lifecycle.LifecycleRegistry.createUnsafe(this).apply { currentState = androidx.lifecycle.Lifecycle.State.RESUMED }
    override val lifecycle: androidx.lifecycle.Lifecycle get() = registry
}
