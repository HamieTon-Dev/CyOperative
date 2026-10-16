package com.cyberoperative.game

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyberoperative.game.data.InfernoBosses
import com.cyberoperative.game.ui.game.BossBodyPreview
import com.cyberoperative.game.ui.theme.CyberOperativeTheme
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 0.35.0 INFERNO packs: one approval sheet per pack (each boss in phases 1–3); `-PrenderPreviews` only. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w900dp-h1100dp-xhdpi", sdk = [35])
class InfernoBossRenders {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun alpha() = sheet("alpha", InfernoBosses.alpha)
    @Test fun beta() = sheet("beta", InfernoBosses.beta)
    @Test fun gamma() = sheet("gamma", InfernoBosses.gamma)

    /** In the arena at gameplay scale (portrait), a few bosses side by side over several runs. */
    @Test fun arenaMaw() = arena("furnace_maw")
    @Test fun arenaSearfang() = arena("searfang_colossus")
    @Test fun arenaMonarch() = arena("combustion_monarch")
    @Test fun arenaWarden() = arena("ember_warden")

    private fun arena(id: String) {
        assumeTrue(System.getProperty("cyberop.renderPreviews") == "true")
        val out = File(System.getProperty("cyberop.previewDir") ?: "build/screens").also { it.mkdirs() }
        val def = com.cyberoperative.game.data.Bosses.byId(id)!!
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        val repo = com.cyberoperative.game.save.SaveRepository(ctx)
        repo.update { it.copy(tutorialDone = true) }
        val session = com.cyberoperative.game.ui.game.GameSession(repo, com.cyberoperative.game.audio.AudioManager(ctx))
        val g = session.engine
        g.debugStartPlan(com.cyberoperative.game.engine.LevelPlanner.bossPlan(40, kotlin.random.Random(3)).copy(boss = def, glitchedBoss = false))
        val me = g.operatives[0]
        var t = 0f
        while (t < 6f) {
            me.invuln = 99f; me.hp = me.build.stats.maxHp
            g.setInput(0f, 0f)
            g.update(1f / 60f); t += 1f / 60f
        }
        g.boss?.let { it.x = g.arena.width / 2f; it.y = g.arena.height * 0.45f }
        g.sounds.clear()
        compose.mainClock.autoAdvance = false
        compose.setContent { CyberOperativeTheme { com.cyberoperative.game.ui.game.GameScreen(session, false, {}, {}) } }
        compose.mainClock.advanceTimeBy(200)
        val view = compose.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { view.draw(Canvas(bmp)) }
        File(out, "inferno_arena_$id.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun sheet(name: String, pack: List<com.cyberoperative.game.data.BossDef>) {
        assumeTrue(System.getProperty("cyberop.renderPreviews") == "true")
        val out = File(System.getProperty("cyberop.previewDir") ?: "build/screens").also { it.mkdirs() }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CyberOperativeTheme {
                Column(Modifier.fillMaxSize().background(Color(0xFF0A0606)).padding(8.dp)) {
                    for (b in pack) Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Text(b.name, color = Color(b.color), fontSize = 13.sp, modifier = Modifier.weight(0.8f))
                        for (ph in 0..2) Box(Modifier.weight(1f).height(210.dp)) {
                            BossBodyPreview(b, ph, 1.3f + ph * 0.7f, Modifier.fillMaxSize(), winding = ph == 2)
                            // The hitbox, as the preview places it (cyan ring), to check alignment.
                            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                                val body = com.cyberoperative.game.ui.game.BossBodies.forId(b.id)
                                val sc = size.minDimension / (b.radius * (body?.previewSpan ?: 4.2f))
                                drawCircle(Color(0xFF00E5FF), b.radius * sc, androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f - 6f * sc),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
                            }
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(300)
        val view = compose.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { view.draw(Canvas(bmp)) }
        File(out, "inferno_pack_$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
