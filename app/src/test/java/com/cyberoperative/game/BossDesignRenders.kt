package com.cyberoperative.game

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.cyberoperative.game.audio.AudioManager
import com.cyberoperative.game.data.BossDef
import com.cyberoperative.game.data.BossExpansion
import com.cyberoperative.game.data.Pattern
import com.cyberoperative.game.data.ThreatTier
import com.cyberoperative.game.engine.AiState
import com.cyberoperative.game.engine.LevelPlanner
import com.cyberoperative.game.save.SaveRepository
import com.cyberoperative.game.ui.game.BossBodyPreview
import com.cyberoperative.game.ui.game.GameScreen
import com.cyberoperative.game.ui.game.GameSession
import com.cyberoperative.game.ui.theme.CyberOperativeTheme
import com.cyberoperative.game.ui.theme.Palette
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
 * Boss body approval sheets (owner, 2026-10-10: "I want to see and approve,
 * or request changes to the physical look of each boss as you make them").
 * Per boss: a close-up sheet (phases 1–3 + charging) and an in-fight shot.
 * ./gradlew testDebugUnitTest -PrenderPreviews --tests '*BossDesignRenders*'
 * (-Dcyberop.boss=<id> is not needed; every designed boss renders.)
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [35])
class BossDesignRenders(private val bossId: String, private val shot: String) {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val enabled = System.getProperty("cyberop.renderPreviews") == "true"
    private val outDir = File(System.getProperty("cyberop.previewDir") ?: "build/screens").resolveSibling("bosses").resolve("designs")

    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(100)
        outDir.mkdirs()
        val view = compose.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { view.draw(Canvas(bmp)) }
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun render() {
        assumeTrue(enabled)
        val def = BossExpansion.designed.first { it.id == bossId }
        when {
            shot == "sheet" -> sheet(def)
            shot == "fight" -> fight(def)
            else -> attack(def, ATTACKS.getValue(def.id).first { it.name == shot })
        }
    }

    /** One attack moment: the patterns to fire in order (with a wait after each). */
    class AttackShot(val name: String, val steps: List<Pair<Pattern, Float>>)

    private fun attack(def: BossDef, a: AttackShot) {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repo = SaveRepository(ctx)
        repo.update { it.copy(tutorialDone = true) }
        val session = GameSession(repo, AudioManager(ctx))
        val g = session.engine
        g.debugStartPlan(LevelPlanner.bossPlan(130, Random(3)).copy(boss = def, glitchedBoss = false))
        val me = g.operatives[0]
        fun hold(seconds: Float) {
            var t = 0f
            while (t < seconds) {
                me.invuln = 99f; me.hp = me.build.stats.maxHp
                g.setInput(0f, 0f)
                g.boss?.let { b ->
                    b.boss?.let { st -> if (st.active == null) st.rest = 99f }
                    if (b.state != AiState.SPAWNING) { b.hp = b.maxHp * 0.9f; b.x = g.arena.width / 2f; b.y = g.arena.height * 0.27f }
                }
                me.px = g.arena.width / 2f; me.py = g.arena.height * 0.64f
                g.update(1f / 60f); t += 1f / 60f
            }
        }
        hold(BossBrainIntro + 0.2f)
        for (p in g.projectiles.items) p.active = false
        for ((pattern, wait) in a.steps) {
            g.debugBossPattern(pattern)
            hold(wait)
        }
        g.sounds.clear()
        compose.mainClock.autoAdvance = false
        compose.setContent { CyberOperativeTheme { GameScreen(session, false, {}, {}) } }
        capture("${def.id}_${a.name}")
    }

    private fun sheet(def: BossDef) {
        compose.mainClock.autoAdvance = false
        val accent = Color(def.color)
        val mono = TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
        compose.setContent {
            CyberOperativeTheme {
                Column(Modifier.fillMaxSize().background(Color(0xFF05070D)).padding(14.dp)) {
                    Text(def.name, color = Palette.TextPrimary, style = mono.copy(fontSize = 26.sp, fontWeight = FontWeight.Bold))
                    Text("${def.title.uppercase()} · ${def.role.label} · ${ThreatTier.label(def.tier)}", color = accent, style = mono.copy(fontSize = 12.sp))
                    Spacer(Modifier.height(10.dp))
                    val cells = listOf("PHASE 1" to (0 to false), "PHASE 2" to (1 to false), "PHASE 3" to (2 to false), "CHARGING ATTACK" to (0 to true))
                    for (row in cells.chunked(2)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            for ((label, cfg) in row) {
                                Column(
                                    Modifier.weight(1f)
                                        .background(Color(0xFF0C0610), RoundedCornerShape(8.dp))
                                        .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                        .padding(6.dp)
                                ) {
                                    Text(label, color = accent, style = mono.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold))
                                    BossBodyPreview(def, cfg.first, 1.3f, Modifier.fillMaxWidth().aspectRatio(0.95f), winding = cfg.second)
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                    Text("Abilities: " + def.abilityNames().joinToString(" · "), color = Palette.TextSecondary, style = mono.copy(fontSize = 11.sp))
                }
            }
        }
        capture("${def.id}_sheet")
    }

    private fun fight(def: BossDef) {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repo = SaveRepository(ctx)
        repo.update { it.copy(tutorialDone = true) }
        val session = GameSession(repo, AudioManager(ctx))
        val g = session.engine
        g.debugStartPlan(LevelPlanner.bossPlan(130, Random(3)).copy(boss = def, glitchedBoss = false))
        val me = g.operatives[0]
        var t = 0f
        while (t < 6.5f) {
            me.invuln = 99f; me.hp = me.build.stats.maxHp
            g.setInput(0f, 0f)
            g.boss?.let { b -> if (b.state != AiState.SPAWNING) b.hp = b.maxHp * 0.9f }
            g.update(1f / 60f); t += 1f / 60f
        }
        // Bring it down into clear view for the shot.
        g.boss?.let { it.x = g.arena.width / 2f; it.y = g.arena.height * 0.32f }
        g.sounds.clear()
        compose.mainClock.autoAdvance = false
        compose.setContent { CyberOperativeTheme { GameScreen(session, false, {}, {}) } }
        capture("${def.id}_fight")
    }

    companion object {
        private val BossBrainIntro = com.cyberoperative.game.engine.BossBrain.INTRO_SECONDS

        /** Attack moments shown to the owner per boss. */
        val ATTACKS: Map<String, List<AttackShot>> = mapOf(
            "vault_sentinel" to listOf(
                AttackShot("atk1_cover_rising", listOf(Pattern.CoverDeploy(5, 8f) to 0.45f)),
                AttackShot("atk2_cover_up", listOf(Pattern.CoverDeploy(5, 8f) to 1.6f)),
                AttackShot("atk3_sweep_telegraph", listOf(Pattern.CoverDeploy(5, 8f) to 1.3f, Pattern.SweepBeam(1.1f, 2.4f, 110f, 26f, 26f) to 0.7f)),
                AttackShot("atk4_sweep_firing", listOf(Pattern.CoverDeploy(5, 8f) to 1.3f, Pattern.SweepBeam(1.1f, 2.4f, 110f, 26f, 26f, count = 2) to 2.0f)),
                AttackShot("atk5_lockdown", listOf(Pattern.Lockdown(110f, 6f) to 1.5f)),
                AttackShot("atk6_mortar_on_box", listOf(Pattern.Lockdown(110f, 6f) to 1.3f, Pattern.Mortar(4, 80f, 1.3f, 24f) to 0.75f))
            )
        )

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}_{1}")
        fun params(): List<Array<Any>> = BossExpansion.designed.flatMap { b ->
            listOf(arrayOf<Any>(b.id, "sheet"), arrayOf<Any>(b.id, "fight")) + ATTACKS[b.id].orEmpty().map { arrayOf<Any>(b.id, it.name) }
        }
    }
}
