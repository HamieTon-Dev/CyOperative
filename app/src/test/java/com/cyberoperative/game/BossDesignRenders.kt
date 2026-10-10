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

    /** The level the boss appears at (130 for one not in the roster yet). */
    private fun levelOf(def: BossDef): Int = com.cyberoperative.game.data.Bosses.firstLevelOf(def) ?: 130

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
            shot == "anim" -> anim(def)
            else -> attack(def, ATTACKS.getValue(def.id).first { it.name == shot })
        }
    }

    /** One attack moment: the patterns to fire in order (with a wait after each). */
    class AttackShot(
        val name: String, val steps: List<Pair<Pattern, Float>>,
        /** Spots (arena fractions) the operative stands in for 1.5 s each first. */
        val camp: List<Pair<Float, Float>> = emptyList(),
        /** Final touch before the capture (e.g. force a stealth boss's eyes open or shut). */
        val after: (com.cyberoperative.game.engine.GameEngine) -> Unit = {},
        /** Seconds to keep running after [after] before the capture. */
        val afterHold: Float = 0f
    )

    private fun attack(def: BossDef, a: AttackShot) {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repo = SaveRepository(ctx)
        repo.update { it.copy(tutorialDone = true) }
        val session = GameSession(repo, AudioManager(ctx))
        val g = session.engine
        g.debugStartPlan(LevelPlanner.bossPlan(levelOf(def), Random(3)).copy(boss = def, glitchedBoss = false))
        val me = g.operatives[0]
        var pin = true
        var standX = g.arena.width / 2f
        var standY = g.arena.height * 0.64f
        fun hold(seconds: Float) {
            var t = 0f
            while (t < seconds) {
                me.invuln = 99f; me.hp = me.build.stats.maxHp
                g.setInput(0f, 0f)
                g.boss?.let { b ->
                    b.boss?.let { st -> if (st.active == null) st.rest = 99f }
                    if (b.state != AiState.SPAWNING) {
                        b.hp = b.maxHp * 0.9f
                        // Stealth bosses sit lower so their eyes aren't hidden under the HUD in the shots.
                        if (pin) { b.x = g.arena.width / 2f; b.y = g.arena.height * (if (def.stealth) 0.4f else 0.27f) }
                    }
                }
                me.px = standX; me.py = standY
                g.update(1f / 60f); t += 1f / 60f
            }
        }
        hold(BossBrainIntro + 0.2f)
        // Screenshots only: a fresh operative can't take level-140 hits, so keep it alive.
        g.boss?.damageMul = 0.001f
        for (p in g.projectiles.items) p.active = false
        for ((fx, fy) in a.camp) {
            standX = g.arena.width * fx; standY = g.arena.height * fy
            hold(1.5f)
        }
        standX = g.arena.width / 2f; standY = g.arena.height * 0.64f
        hold(0.1f)
        for (p in g.projectiles.items) p.active = false
        // From here the boss moves on its own (burrowing, dashing).
        pin = false
        for ((pattern, wait) in a.steps) {
            g.debugBossPattern(pattern)
            hold(wait)
        }
        a.after(g)
        if (a.afterHold > 0f) hold(a.afterHold)
        // Don't catch the boss mid hit-flash in a screenshot.
        g.boss?.hitFlash = 0f
        if (a.name != "atk1_emp_blackout") g.showBanner("", "", 0f)
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
                    val cells = listOf("PHASE 1" to (0 to false), "PHASE 2" to (1 to false), "PHASE 3" to (2 to false), "CHARGING ATTACK" to (0 to true)) +
                        (if (def.stealth) listOf("IN THE SHADOWS" to (0 to false), "SHADOWS · PHASE 3" to (2 to false)) else emptyList())
                    for (row in cells.chunked(2)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            for ((label, cfg) in row) {
                                val veiled = if (label.contains("SHADOWS")) 1f else 0f
                                Column(
                                    Modifier.weight(1f)
                                        .background(Color(0xFF0C0610), RoundedCornerShape(8.dp))
                                        .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                        .padding(6.dp)
                                ) {
                                    Text(label, color = accent, style = mono.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold))
                                    BossBodyPreview(def, cfg.first, 1.3f, Modifier.fillMaxWidth().aspectRatio(if (def.stealth) 1.25f else 0.95f), winding = cfg.second, veiled = veiled)
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

    /**
     * Idle loop then a charge, as numbered frames in build/boss_anim/<id>/
     * (stitched into docs/bosses/designs/<id>_anim.gif by the caller).
     */
    private fun anim(def: BossDef) {
        compose.mainClock.autoAdvance = false
        val time = androidx.compose.runtime.mutableFloatStateOf(0f)
        val winding = androidx.compose.runtime.mutableStateOf(false)
        val veil = androidx.compose.runtime.mutableFloatStateOf(0f)
        compose.setContent {
            CyberOperativeTheme {
                androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(Color(0xFF05070D))) {
                    BossBodyPreview(def, 0, time.floatValue, Modifier.fillMaxWidth().aspectRatio(1f), winding = winding.value, veiled = veil.floatValue)
                    Text(
                        when { winding.value -> "CHARGING ATTACK"; veil.floatValue > 0.5f -> "IN THE SHADOWS"; else -> "IDLE" }, color = Color(def.color),
                        style = TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
        val dir = File("build/boss_anim/${def.id}").apply { deleteRecursively(); mkdirs() }
        val frames = 48
        for (i in 0 until frames) {
            time.floatValue = i * 0.1f
            winding.value = i >= 36
            // Stealth bosses fade into the shadows and back during the idle part.
            if (def.stealth) veil.floatValue = when (i) { in 10..15 -> (i - 9) / 6f; in 16..23 -> 1f; in 24..29 -> 1f - (i - 23) / 6f; else -> 0f }
            compose.mainClock.advanceTimeBy(50)
            val view = compose.activity.window.decorView
            val w = view.width.coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(w, w, Bitmap.Config.ARGB_8888)
            compose.runOnUiThread { view.draw(Canvas(bmp)) }
            File(dir, "f%03d.png".format(i)).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun fight(def: BossDef) {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repo = SaveRepository(ctx)
        repo.update { it.copy(tutorialDone = true) }
        val session = GameSession(repo, AudioManager(ctx))
        val g = session.engine
        g.debugStartPlan(LevelPlanner.bossPlan(levelOf(def), Random(3)).copy(boss = def, glitchedBoss = false))
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
            ),
            "rootkit_apostle" to listOf(
                AttackShot("atk1_burrow_drift", listOf(Pattern.Burrow(3.0f, 120f, 0.8f, 100f, 30f) to 1.6f)),
                AttackShot("atk2_eruption_marked", listOf(Pattern.Burrow(1.2f, 400f, 1.2f, 100f, 30f) to 1.95f)),
                AttackShot("atk3_erupt_bloom", listOf(Pattern.Burrow(1.0f, 400f, 0.6f, 100f, 30f, bloomRings = 2) to 2.35f)),
                AttackShot("atk4_spike_eruption", listOf(Pattern.SpikeEruption(3, 8, 50f, 26f) to 0.95f)),
                AttackShot("atk5_infected_zones", listOf(Pattern.Infect(4, 110f, 7f, 20f) to 1.6f), camp = listOf(0.2f to 0.5f, 0.8f to 0.78f, 0.25f to 0.85f)),
                AttackShot("atk6_rootkit_bloom", listOf(Pattern.Bloom(3, 28f) to 1.05f))
            ),
            "nullshade" to listOf(
                AttackShot("atk1_emp_blackout", emptyList()),
                AttackShot("atk2_shadows_static_needles", listOf(Pattern.Needles(7, 70f, 340f, 16f, bursts = 2) to 0.45f), after = { g -> eyes(g, false) }),
                AttackShot("atk3_eye_glint_lock_window", listOf(Pattern.Needles(5, 40f, 360f, 16f) to 0.25f), after = { g -> eyes(g, true) }),
                AttackShot("atk4_ghost_dash", listOf(Pattern.GhostDash(0.6f, 520f, 520f, 34f) to 1.15f), after = { g -> eyes(g, true) }),
                AttackShot("atk5_spark_ambush_marked", listOf(Pattern.SparkAmbush(0.9f, 90f, 30f, 12) to 0.55f)),
                AttackShot("atk6_spark_ambush_burst", listOf(Pattern.SparkAmbush(0.9f, 90f, 30f, 12) to 1.0f), after = { g -> eyes(g, true) }),
                AttackShot("atk7_grid_reboot_surge", listOf(Pattern.GridSurge(3, 0.55f, 420f, 270f, 26f) to 0.62f), after = { g -> g.lightFlicker = 0.18f })
            ),
            "ransom_king" to listOf(
                AttackShot("atk1_key_zones_shielded", listOf(Pattern.KeyZone(3, 58f, 11f) to 0.8f)),
                AttackShot("atk2_key_zone_unlocking", listOf(Pattern.KeyZone(3, 58f, 11f) to 0.3f), after = { g -> zoneUnder(g, 1) }, afterHold = 0.8f),
                AttackShot("atk3_decrypted_shield_down", listOf(Pattern.KeyZone(1, 58f, 11f) to 0.3f), after = { g -> zoneUnder(g, 1) }, afterHold = 1.7f),
                AttackShot("atk4_lock_grid", listOf(Pattern.LockGrid(2, 7f) to 1.4f)),
                AttackShot("atk5_royal_seizure_marked", listOf(Pattern.RoyalSeizure(110f, 1.0f, 28f, 1.1f, 3.5f) to 0.6f)),
                AttackShot("atk6_seized_and_caged", listOf(Pattern.RoyalSeizure(110f, 0.8f, 28f, 1.5f, 3.5f) to 1.25f)),
                AttackShot("atk7_ransom_pulse_encrypted", listOf(Pattern.RansomPulse(2, 0.6f, 440f, 240f, 22f) to 1.7f), after = { g -> g.operatives[0].encryptCharge = 0.6f })
            ),
            "spectral_firewall" to listOf(
                AttackShot("atk1_ring_shield", listOf(Pattern.Spiral(3, 0.1f, 1f, 0f, 1f, 0f) to 0.6f)),
                AttackShot("atk2_firewall_ring_launch", listOf(Pattern.FirewallRing(1, 3, 170f, 26f) to 1.5f)),
                AttackShot("atk3_firewall_ring_waves", listOf(Pattern.FirewallRing(2, 2, 180f, 27f) to 2.7f)),
                AttackShot("atk4_burn_sector_warning", listOf(Pattern.BurnSector(2, 70f, 1.2f, 2.5f, 24f) to 0.7f)),
                AttackShot("atk5_burn_sector_burning", listOf(Pattern.BurnSector(2, 70f, 1.2f, 2.5f, 24f) to 1.7f)),
                AttackShot("atk6_heat_collapse", listOf(Pattern.HeatCollapse(2, 150f, 28f) to 2.2f)),
                AttackShot("atk7_purge_spin", listOf(Pattern.PurgeSpin(4, 0.85f, 3.2f, 300f, 25f) to 1.7f)),
                AttackShot("atk9_phase3_four_rings", listOf(Pattern.FirewallRing(4, 2, 190f, 28f, waveGap = 1.05f) to 4.0f)),
                AttackShot("atk8_on_fire", listOf(Pattern.FirewallRing(1, 3, 170f, 26f) to 1.2f), after = { g -> g.operatives[0].burning = 2f })
            )
        )

        /** Moves [n] key zones under the operative so they get unlocked during the hold. */
        private fun zoneUnder(g: com.cyberoperative.game.engine.GameEngine, n: Int) {
            val me = g.operatives[0]
            g.hazards.items.filter { it.active && it.kind == com.cyberoperative.game.engine.HazardKind.KEY_ZONE }.take(n).forEach { it.x = me.px; it.y = me.py }
        }

        /** Force a stealth boss's eyes open (lockable) or shut (in the shadows). */
        private fun eyes(g: com.cyberoperative.game.engine.GameEngine, open: Boolean) {
            g.boss?.boss?.let { it.eyesOpen = open; it.eyeTimer = 9f }
            g.boss?.untargetable = !open
            g.bossVeil = if (open) 0f else 1f
        }

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}_{1}")
        fun params(): List<Array<Any>> = BossExpansion.designed.flatMap { b ->
            listOf(arrayOf<Any>(b.id, "sheet"), arrayOf<Any>(b.id, "fight"), arrayOf<Any>(b.id, "anim")) + ATTACKS[b.id].orEmpty().map { arrayOf<Any>(b.id, it.name) }
        }
    }
}
