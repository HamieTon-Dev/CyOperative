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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyberoperative.game.data.BossDef
import com.cyberoperative.game.data.Bosses
import com.cyberoperative.game.data.EliteModifier
import com.cyberoperative.game.data.Enemies
import com.cyberoperative.game.data.InfernoBosses
import com.cyberoperative.game.data.ObstacleKind
import com.cyberoperative.game.data.ObstacleSpec
import com.cyberoperative.game.data.Operatives
import com.cyberoperative.game.data.OperativeSkins
import com.cyberoperative.game.save.CharacterLook
import com.cyberoperative.game.ui.game.BodyStyle
import com.cyberoperative.game.ui.game.BossBodyPreview
import com.cyberoperative.game.ui.game.EnemySprites
import com.cyberoperative.game.ui.game.FigurePreview
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Asset packs (owner, 0.36.4): labelled still sheets of every enemy (normal and INFERNO look),
 * the elite modifiers, every operative class (turns, looks, skins), every boss in its three
 * phases (classic, Boss Expansion, INFERNO packs) and the arena props, plus prop animation frames.
 * Drawn by the game's own renderers. `-PrenderPreviews` only; output in docs/asset_packs.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1200dp-h2400dp-mdpi", sdk = [35])
class AssetPackRenders {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val out = File(System.getProperty("cyberop.previewDir") ?: "build/screens").resolveSibling("asset_packs")
    private val bg = Color(0xFF0B0F18)

    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(300)
        out.mkdirs()
        val view = compose.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { view.draw(Canvas(bmp)) }
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun sheet(name: String, title: String, content: @Composable () -> Unit) {
        assumeTrue(System.getProperty("cyberop.renderPreviews") == "true")
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Column(Modifier.fillMaxSize().background(bg).padding(16.dp)) {
                Text(title, color = Color(0xFF00E5FF), fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text("CYBER OPERATIVE · ASSET SHEET · drawn by the game", color = Color(0xFF7A8AA8), fontSize = 13.sp)
                androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))
                content()
            }
        }
        capture(name)
    }

    @Composable
    private fun Label(text: String, color: Color = Color(0xFFE6EEFA), sub: String? = null) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.fillMaxWidth())
            if (sub != null) Text(sub, color = Color(0xFF7A8AA8), fontSize = 10.sp, textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.fillMaxWidth())
        }
    }

    // ---- Enemies ---------------------------------------------------------------------------

    private val pages = Enemies.all.chunked(64)

    private fun enemies(name: String, title: String, hell: Boolean, page: Int) {
        assumeTrue(page < pages.size)
        sheet("${name}_p${page + 1}", "$title · PAGE ${page + 1}/${pages.size}") {
            EnemySprites.hell = hell
            for (row in pages[page].chunked(8)) Row(Modifier.fillMaxWidth()) {
                for (d in row) Column(Modifier.weight(1f).padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(120.dp)) {
                        with(EnemySprites) { draw(d, size.width / 2f, size.height / 2f, size.height * 0.3f, 1.2f, d.id.hashCode() and 0xFF, Math.PI.toFloat() / 2f, false, false) }
                    }
                    Label(d.name, Color(d.color), d.id)
                }
                repeat(8 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }

    @Test fun enemiesNormal1() = enemies("enemies_normal", "THREATS · ALL ENEMIES", false, 0)
    @Test fun enemiesNormal2() = enemies("enemies_normal", "THREATS · ALL ENEMIES", false, 1)
    @Test fun enemiesNormal3() = enemies("enemies_normal", "THREATS · ALL ENEMIES", false, 2)
    @Test fun enemiesNormal4() = enemies("enemies_normal", "THREATS · ALL ENEMIES", false, 3)
    @Test fun enemiesNormal5() = enemies("enemies_normal", "THREATS · ALL ENEMIES", false, 4)
    @Test fun enemiesNormal6() = enemies("enemies_normal", "THREATS · ALL ENEMIES", false, 5)
    @Test fun enemiesNormal7() = enemies("enemies_normal", "THREATS · ALL ENEMIES", false, 6)
    @Test fun enemiesInferno1() = try { enemies("enemies_inferno", "THREATS · INFERNO LOOK", true, 0) } finally { EnemySprites.hell = false }
    @Test fun enemiesInferno2() = try { enemies("enemies_inferno", "THREATS · INFERNO LOOK", true, 1) } finally { EnemySprites.hell = false }
    @Test fun enemiesInferno3() = try { enemies("enemies_inferno", "THREATS · INFERNO LOOK", true, 2) } finally { EnemySprites.hell = false }
    @Test fun enemiesInferno4() = try { enemies("enemies_inferno", "THREATS · INFERNO LOOK", true, 3) } finally { EnemySprites.hell = false }
    @Test fun enemiesInferno5() = try { enemies("enemies_inferno", "THREATS · INFERNO LOOK", true, 4) } finally { EnemySprites.hell = false }
    @Test fun enemiesInferno6() = try { enemies("enemies_inferno", "THREATS · INFERNO LOOK", true, 5) } finally { EnemySprites.hell = false }
    @Test fun enemiesInferno7() = try { enemies("enemies_inferno", "THREATS · INFERNO LOOK", true, 6) } finally { EnemySprites.hell = false }

    @Test fun elites() = sheet("enemies_elites", "ELITE MODIFIERS") {
        val bases = Enemies.all.take(6)
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.width(150.dp))
            for (b in bases) Box(Modifier.weight(1f)) { Label(b.name, Color(b.color)) }
        }
        for (m in EliteModifier.entries) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(150.dp)) { Label(m.label, Color(m.color)) }
            for (b in bases) androidx.compose.foundation.Canvas(Modifier.weight(1f).height(130.dp)) {
                val c = Offset(size.width / 2f, size.height / 2f); val r = size.height * 0.28f
                with(EnemySprites) {
                    eliteBack(m, c, r, 1.2f, 7)
                    draw(b, c.x, c.y, r, 1.2f, 7, Math.PI.toFloat() / 2f, false, false, elite = m)
                    eliteFront(m, c, r, 1.2f)
                }
            }
        }
    }

    // ---- Operatives ------------------------------------------------------------------------

    @Test fun operatives() = sheet("operatives_classes", "OPERATIVES · CLASSES (standing / running)") {
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.width(220.dp))
            for (lbl in listOf("STANDING", "RUNNING", "STANDING · TITANIUM", "RUNNING · TITANIUM")) Box(Modifier.weight(1f)) { Label(lbl) }
        }
        for (c in Operatives.all) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(220.dp)) { Label(c.name, sub = c.traits.firstOrNull()) }
            for ((moving, skin) in listOf(false to OperativeSkins.DEFAULT, true to OperativeSkins.DEFAULT, false to OperativeSkins.TITANIUM, true to OperativeSkins.TITANIUM))
                FigurePreview(BodyStyle.byId(c.bodyId), skin, Modifier.weight(1f).height(190.dp), moving = moving)
        }
    }

    @Test fun operativeLooks() = sheet("operatives_looks", "OPERATIVES · CREATOR PARTS (head / face / body / legs)") {
        val looks = listOf(CharacterLook(0, 0, 0, 0) to "A A A A", CharacterLook(1, 1, 1, 1) to "B B B B", CharacterLook(2, 2, 2, 2) to "C C C C",
            CharacterLook(0, 1, 2, 0) to "A B C A", CharacterLook(2, 0, 1, 2) to "C A B C", CharacterLook(1, 2, 0, 1) to "B C A B")
        for (c in Operatives.all) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(170.dp)) { Label(c.name) }
            for ((l, lbl) in looks) Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                FigurePreview(BodyStyle.byId(c.bodyId), OperativeSkins.DEFAULT, Modifier.fillMaxWidth().height(170.dp), look = l)
                Label(lbl)
            }
        }
    }

    @Test fun operativeSkins() = sheet("operatives_skins", "OPERATIVES · SKINS") {
        for (row in OperativeSkins.all.chunked(6)) Row(Modifier.fillMaxWidth()) {
            for (s in row) Column(Modifier.weight(1f).padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                FigurePreview(BodyStyle.AGENT, s, Modifier.fillMaxWidth().height(170.dp))
                Label(s.name, Color(s.edge))
            }
            repeat(6 - row.size) { Box(Modifier.weight(1f)) }
        }
    }

    // ---- Bosses ----------------------------------------------------------------------------

    private fun bosses(name: String, title: String, list: List<BossDef>) = sheet(name, title) {
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.width(200.dp))
            for (ph in listOf("PHASE 1", "PHASE 2", "PHASE 3 · WIND-UP")) Box(Modifier.weight(1f)) { Label(ph) }
        }
        for (b in list) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(200.dp)) { Label(b.name, Color(b.color), "${b.title} · ${b.role.label}") }
            for (ph in 0..2) Box(Modifier.weight(1f).height(if (list.size > 6) 180.dp else 260.dp)) {
                BossBodyPreview(b, ph, 1.3f + ph * 0.7f, Modifier.fillMaxSize(), winding = ph == 2)
            }
        }
    }

    @Test fun bossesClassic() = bosses("bosses_classic", "BOSSES · CLASSIC THREATS", Bosses.classics)
    @Test fun bossesExpansion() = bosses("bosses_expansion", "BOSSES · BOSS EXPANSION VOL. 1", Bosses.expansion)
    @Test fun bossesInfernoAlpha() = bosses("bosses_inferno_alpha", "INFERNO BOSSES · PACK ALPHA", InfernoBosses.alpha)
    @Test fun bossesInfernoBeta() = bosses("bosses_inferno_beta", "INFERNO BOSSES · PACK BETA", InfernoBosses.beta)
    @Test fun bossesInfernoGamma() = bosses("bosses_inferno_gamma", "INFERNO BOSSES · PACK GAMMA", InfernoBosses.gamma)

    // ---- Arena props (barriers, server towers) ---------------------------------------------

    private fun props(name: String, title: String, inferno: Boolean, frames: Int) {
        assumeTrue(System.getProperty("cyberop.renderPreviews") == "true")
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        val repo = com.cyberoperative.game.save.SaveRepository(ctx)
        repo.update { it.copy(tutorialDone = true) }
        val diff = if (inferno) com.cyberoperative.game.engine.Difficulty.inferno(3)!! else com.cyberoperative.game.engine.Difficulty.MEDIUM
        val session = com.cyberoperative.game.ui.game.GameSession(repo, com.cyberoperative.game.audio.AudioManager(ctx), com.cyberoperative.game.engine.GameMode.CAMPAIGN, diff)
        if (inferno) session.engine.debugEnterRoom(2)
        val renderer = com.cyberoperative.game.ui.game.ArenaRenderer()
        val kinds = ObstacleKind.entries.filter { it != ObstacleKind.SHOP_COUNTER }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            var t by remember { mutableFloatStateOf(0f) }
            LaunchedEffect(Unit) { val s0 = withFrameNanos { it }; while (true) withFrameNanos { t = (it - s0) / 1e9f } }
            Column(Modifier.fillMaxSize().background(bg).padding(16.dp)) {
                Text(title, color = Color(0xFF00E5FF), fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text("CYBER OPERATIVE · ASSET SHEET · drawn by the game", color = Color(0xFF7A8AA8), fontSize = 13.sp)
                androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))
                for (row in kinds.chunked(5)) Row(Modifier.fillMaxWidth()) {
                    for (k in row) Column(Modifier.weight(1f).padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(200.dp)) {
                            val w = 120f; val h = 70f
                            val sc = minOf(size.width / 200f, size.height / 230f)
                            scale(sc, sc, Offset.Zero) {
                                translate((size.width / sc - w) / 2f, (size.height / sc) * 0.62f) {
                                    with(renderer) { drawPropForSheet(session.engine, ObstacleSpec(com.cyberoperative.game.core.Rect(0f, 0f, w, h), k), t) }
                                }
                            }
                        }
                        Label(k.label, sub = k.name.lowercase())
                    }
                    repeat(5 - row.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
        for (f in 0 until frames) {
            compose.mainClock.advanceTimeBy(if (f == 0) 300 else 100)
            out.mkdirs()
            val view = compose.activity.window.decorView
            val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            compose.runOnUiThread { view.draw(Canvas(bmp)) }
            val file = if (f == 0) File(out, "$name.png") else File(out, "frames_$name/f%02d.png".format(f)).also { it.parentFile?.mkdirs() }
            file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun propsNormal() = props("props_barriers_servers", "ARENA PROPS · BARRIERS & SERVER TOWERS", inferno = false, frames = 25)
    @Test fun propsInferno() = props("props_inferno", "ARENA PROPS · INFERNO ROOMS", inferno = true, frames = 25)
}
