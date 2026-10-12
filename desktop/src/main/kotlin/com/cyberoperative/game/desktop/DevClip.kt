package com.cyberoperative.game.desktop

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.cyberoperative.game.audio.AudioManager
import com.cyberoperative.game.core.MathUtil
import com.cyberoperative.game.data.Upgrades
import com.cyberoperative.game.engine.Difficulty
import com.cyberoperative.game.engine.GameEngine
import com.cyberoperative.game.engine.GameMode
import com.cyberoperative.game.engine.Phase
import com.cyberoperative.game.save.SaveRepository
import com.cyberoperative.game.ui.game.GameScreen
import com.cyberoperative.game.ui.game.GameSession
import com.cyberoperative.game.ui.theme.CyberOperativeTheme
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Developer tool: records gameplay clips offscreen as PNG frames (`gradlew :desktop:clip`),
 * a small bot playing: it aims at the nearest threat, circles it at range, sidesteps shots and
 * dashes now and then. ffmpeg turns the frames into video.
 */
fun main(args: Array<String>) {
    val out = File(args.getOrNull(0) ?: "build/clip").apply { mkdirs() }
    val dir = kotlin.io.path.createTempDirectory("cyberop-clip").toFile()
    val save = SaveRepository(dir)
    com.cyberoperative.game.data.ArenaShape.wide = true
    com.cyberoperative.game.data.ArenaShape.pcRules = true
    com.cyberoperative.game.ui.common.PlayMode.pcRules = true
    com.cyberoperative.game.ui.common.PlayMode.landscape = true
    save.update { it.copy(tutorialDone = true) }
    val audio = AudioManager()
    val w = 1280
    val h = 720
    val fps = 30

    fun record(name: String, seconds: Int, setup: (GameEngine) -> Unit) {
        val frames = File(out, name).apply { deleteRecursively(); mkdirs() }
        val s = GameSession(save, audio, GameMode.CAMPAIGN, Difficulty.MEDIUM)
        val g = s.engine
        setup(g)
        val scene = ImageComposeScene(w, h, Density(w / 1280f)) {
            androidx.compose.runtime.CompositionLocalProvider(androidx.lifecycle.compose.LocalLifecycleOwner provides ShotLifecycle) {
                CyberOperativeTheme { GameScreen(s, showDamageNumbers = true, onExitToMenu = {}, onNewOperation = {}) }
            }
        }
        val bot = ClipBot(g)
        var t = 0L
        // A short warm-up so the first frame isn't the very first tick.
        repeat(10) { bot.step(); scene.render(t); t += 1_000_000_000L / fps }
        for (i in 0 until seconds * fps) {
            bot.step()
            val img = scene.render(t)
            t += 1_000_000_000L / fps
            File(frames, "f%05d.png".format(i)).writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        }
        scene.close()
        println("recorded $name: ${seconds * fps} frames")
    }

    // A strong mid-campaign build so the fights look like a real run.
    fun kit(g: GameEngine) {
        for (u in listOf(Upgrades.MALWARE_MISSILES, Upgrades.ARC_DISCHARGE, Upgrades.MULTISHOT, Upgrades.PAYLOAD_BOOST, Upgrades.PAYLOAD_BOOST, Upgrades.KERNEL_OVERCLOCK, Upgrades.PENETRATION))
            if (g.build.isEligible(u)) g.build.take(u)
        g.operatives[0].hp = g.operatives[0].build.stats.maxHp
    }

    record("room", 22) { g -> g.debugJumpToLevel(14); kit(g) }
    record("boss", 32) { g -> g.debugJumpToLevel(20); kit(g) }
}

/** Plays like a person for the camera: aim, circle at range, sidestep shots, dash, heal. */
private class ClipBot(val g: GameEngine) {
    private var t = 0f
    private var orbit = 1f

    fun step() {
        t += 1f / 30f
        if (g.phase == Phase.UPGRADE && g.offer.isNotEmpty()) { g.chooseUpgrade(0); return }
        val o = g.operatives[0]
        // Keep the show going: a charge when low, and never let the take end early.
        if (o.hp < o.build.stats.maxHp * 0.45f) { if (!g.useHpCharge()) o.hp = o.build.stats.maxHp * 0.6f }
        val target = g.boss?.takeIf { it.active } ?: g.enemies.items.filter { it.active && it.targetable }.minByOrNull { MathUtil.dist2(it.x, it.y, g.px, g.py) }
        if (target == null) {
            g.setAim(false, 0f, 0f)
            // Room clear: wander toward the middle and pick up drops.
            val l = g.loot.minByOrNull { MathUtil.dist2(it.x, it.y, g.px, g.py) }
            val (tx, ty) = l?.let { it.x to it.y } ?: (g.arena.width / 2f to g.arena.height / 2f)
            val d = MathUtil.dist(tx, ty, g.px, g.py)
            if (d > 30f) g.setInput((tx - g.px) / d, (ty - g.py) / d) else g.setInput(0f, 0f)
            return
        }
        g.setAim(true, target.x, target.y)
        val dx = g.px - target.x
        val dy = g.py - target.y
        val d = MathUtil.dist(target.x, target.y, g.px, g.py).coerceAtLeast(1f)
        val want = if (target.boss != null) 430f else 330f
        // Circle the target, closing or opening to the range we want; flip direction now and then.
        if ((t * 10).toInt() % 70 == 0) orbit = -orbit
        val a = atan2(dy, dx) + orbit * 1.2f
        var mx = cos(a) + (dx / d) * (want - d) / 120f
        var my = sin(a) + (dy / d) * (want - d) / 120f
        // Sidestep the nearest incoming shot.
        g.projectiles.items.filter { it.active && !it.friendly }.minByOrNull { MathUtil.dist2(it.x, it.y, g.px, g.py) }?.let { p ->
            val pd = MathUtil.dist(p.x, p.y, g.px, g.py)
            if (pd < 140f) { mx += (g.px - p.x) / pd * 1.5f; my += (g.py - p.y) / pd * 1.5f }
        }
        // Stay off the walls.
        if (g.px < 120f) mx += 1f
        if (g.px > g.arena.width - 120f) mx -= 1f
        if (g.py < 160f) my += 1f
        if (g.py > g.arena.height - 120f) my -= 1f
        val m = kotlin.math.sqrt(mx * mx + my * my).coerceAtLeast(1e-3f)
        g.setInput(mx / m, my / m)
        if ((t * 10).toInt() % 45 == 0) g.dash()
    }
}
