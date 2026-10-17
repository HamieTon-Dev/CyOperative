package com.cyberoperative.game.ui.menu

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cyberoperative.game.data.Bosses
import com.cyberoperative.game.data.Enemies
import com.cyberoperative.game.data.OpProgress
import com.cyberoperative.game.data.Operatives
import com.cyberoperative.game.engine.Difficulty
import com.cyberoperative.game.idle.IdleRunSimulator
import com.cyberoperative.game.idle.IdleRunState
import com.cyberoperative.game.idle.IdleRuns
import com.cyberoperative.game.idle.IdleStatus
import com.cyberoperative.game.save.PlayerProfile
import com.cyberoperative.game.save.SaveRepository
import com.cyberoperative.game.ui.common.CyberButton
import com.cyberoperative.game.ui.common.GearIconView
import com.cyberoperative.game.ui.common.ScreenScaffold
import com.cyberoperative.game.ui.common.TerminalCard
import com.cyberoperative.game.ui.common.formatAmount
import com.cyberoperative.game.ui.common.formatFull
import com.cyberoperative.game.ui.game.IdleBattleArt
import com.cyberoperative.game.ui.theme.Palette
import kotlinx.coroutines.delay

/**
 * IDLE RUNS (owner, 0.37.0): start a run for the active character (STANDARD, or INFERNO once
 * unlocked), watch it fight one threat at a time, and collect what an ended run earned. The run
 * keeps going with the game closed; the home-screen widget shows the same run.
 */
@Composable
fun IdleRunScreen(save: SaveRepository, onBack: () -> Unit, onClick: () -> Unit) {
    val host = IdleRuns.host
    ScreenScaffold("IDLE RUNS", onBack) {
        if (host == null) {
            Text("Idle Runs are on the Android version.", color = Palette.TextSecondary, style = MaterialTheme.typography.bodyMedium)
            return@ScreenScaffold
        }
        val run by host.state.collectAsState()
        val profile by save.profile.collectAsState()
        // The run catches up to now while this page is open (the widget refreshes when the app leaves).
        LaunchedEffect(Unit) {
            while (true) { host.sync(refreshWidget = false); delay(1000) }
        }
        // An ended run pays out once: € to the account, XP and gear to the character that ran it.
        LaunchedEffect(run?.status, run?.collected) {
            val r = run
            if (r != null && r.ended && !r.collected) {
                save.update { IdleRuns.collect(it, r) }
                host.store(r.copy(collected = true))
            }
        }
        var time by remember { mutableFloatStateOf(0f) }
        LaunchedEffect(Unit) {
            val start = withFrameNanos { it }
            while (true) withFrameNanos { time = (it - start) / 1e9f }
        }
        Column(Modifier.verticalScroll(rememberScrollState())) {
            val r = run
            if (r != null && r.active) {
                ActiveRun(r, time) { onClick(); host.store(IdleRuns.recall(r, System.currentTimeMillis())) }
            } else {
                if (r != null) Summary(r, time)
                NewRun(profile) { d ->
                    onClick()
                    host.requestNotifications()
                    host.begin(IdleRunSimulator.start(profile, d, System.currentTimeMillis()))
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** "1:04:09" / "4:09". */
internal fun clock(seconds: Long): String {
    val h = seconds / 3600; val m = (seconds % 3600) / 60; val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

internal fun idleDifficultyLabel(s: IdleRunState): String {
    val d = Difficulty.byName(s.difficulty)
    return if (d.inferno > 0) "INFERNO ${d.inferno}" else "STANDARD"
}

internal fun idleEnemyName(s: IdleRunState): String {
    val e = s.enemy ?: return if (s.pause > 0f) "MOVING TO THE NEXT ROOM" else "—"
    if (e.boss) return "BOSS · " + (Bosses.byId(e.id)?.name ?: e.id.uppercase())
    val name = Enemies.byId(e.id).name
    return if (e.elite != null) "ELITE $name" else name
}

@Composable
private fun Battle(s: IdleRunState, time: Float, defeated: Boolean = false) {
    // Slow cycle, like the widget: idle, attack, hit, recover.
    val frame = IdleBattleArt.Frame.entries[((time / 1.1f).toInt()) % 4]
    Canvas(
        Modifier.fillMaxWidth().aspectRatio(2f)
            .background(Palette.SurfaceSunken, RoundedCornerShape(8.dp))
    ) {
        with(IdleBattleArt) { if (defeated) drawDefeated(s, time) else drawBattle(s, frame, time) }
    }
}

@Composable
private fun Stat(label: String, value: String, color: Color = Palette.TextPrimary, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
        Text(value, color = color, style = MaterialTheme.typography.titleMedium, maxLines = 1)
    }
}

@Composable
private fun StatRow(vararg cells: Triple<String, String, Color>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for ((l, v, c) in cells) Stat(l, v, c, Modifier.weight(1f))
    }
    Spacer(Modifier.height(8.dp))
}

/** Full number when short enough, else 398k / 3.7m (owner's number rule). */
private fun amount(v: Long): String = formatFull(v).let { if (it.length <= 9) it else formatAmount(v) }

@Composable
private fun ActiveRun(s: IdleRunState, time: Float, onRecall: () -> Unit) {
    val inferno = Difficulty.byName(s.difficulty).inferno > 0
    TerminalCard(accent = if (inferno) Palette.Red else Palette.Cyan) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("AUTO-FIGHT · 1 THREAT", color = Palette.TextPrimary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Text(idleDifficultyLabel(s), color = if (inferno) Palette.Red else Palette.Cyan, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(8.dp))
            Battle(s, time)
            Spacer(Modifier.height(8.dp))
            Text(idleEnemyName(s), color = if (s.enemy?.boss == true) Palette.Orange else Palette.TextSecondary, style = MaterialTheme.typography.labelLarge)
            // Operator HP.
            val frac = (s.hp / s.maxHp.coerceAtLeast(1f)).coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth().height(8.dp).background(Palette.SurfaceSunken, RoundedCornerShape(4.dp))) {
                Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(if (frac < 0.35f) Palette.Red else Palette.Green, RoundedCornerShape(4.dp)))
            }
            Text("HP ${s.hp.toInt()} / ${s.maxHp.toInt()}   ·   ✚ ${s.charges} HP charges", color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(10.dp))
            BossStrip(s)
            Spacer(Modifier.height(10.dp))
            StatRow(
                Triple("OPERATOR", s.characterName.ifEmpty { Operatives.byId(s.classId).name }, Palette.TextPrimary),
                Triple("OP LV", OpProgress.of(s.opXp).label.removePrefix("OP Lv ").uppercase(), Palette.Cyan)
            )
            StatRow(
                Triple("LEVEL", s.level.toString(), Palette.TextPrimary),
                Triple("NEXT BOSS", if (s.bossLevel) "NOW" else s.nextBoss.toString(), Palette.Orange)
            )
            StatRow(
                Triple("€ EARNED", amount(s.eurosEarned), Palette.Euro),
                Triple("XP EARNED", amount(s.xpEarned), Palette.Cyan)
            )
            StatRow(
                Triple("RUN TIME", clock(s.durationSeconds(System.currentTimeMillis())), Palette.TextPrimary),
                Triple("KILLS", "${s.kills} · ${s.bosses} BOSS", Palette.TextPrimary)
            )
            Text("IDLE RATE: 40% € · 50% XP OF A NORMAL RUN", color = Palette.Cyan, style = MaterialTheme.typography.labelMedium)
            Text("Uses your operator's level, gear, Overclock and class. Upgrade chips are picked for your build.", color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
        }
    }
    Spacer(Modifier.height(10.dp))
    Loot(s)
    Spacer(Modifier.height(10.dp))
    CyberButton("RECALL OPERATOR", Modifier.fillMaxWidth(), accent = Palette.Red, subtitle = "END THE RUN NOW · KEEP WHAT IT EARNED") { onRecall() }
}

/** Ten dots for the current block of levels: cleared, current, the boss. */
@Composable
private fun BossStrip(s: IdleRunState) {
    val first = (s.nextBoss - 9).coerceAtLeast(1)
    Text("RUN PROGRESS · LV ${s.level} · NEXT BOSS ${s.nextBoss}", color = Palette.TextSecondary, style = MaterialTheme.typography.labelSmall)
    Spacer(Modifier.height(4.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        for (l in first..s.nextBoss) {
            val c = when {
                l < s.level -> Palette.Red
                l == s.level -> Palette.TextPrimary
                l == s.nextBoss -> Palette.Orange
                else -> Palette.Divider
            }
            Box(Modifier.size(if (l == s.nextBoss) 14.dp else 10.dp).background(c, RoundedCornerShape(50)))
        }
    }
}

@Composable
private fun Loot(s: IdleRunState) {
    TerminalCard {
        Column {
            Text("UPGRADE CHIPS (${s.chipLog.size})", color = Palette.Green, style = MaterialTheme.typography.labelLarge)
            if (s.chipLog.isEmpty()) Text("None yet: one per cleared room, two per boss.", color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
            else Text(s.chipLog.takeLast(12).reversed().joinToString(" · "), color = Palette.TextSecondary, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(8.dp))
            Text("GEAR DROPS (${s.drops.size})", color = Palette.Gold, style = MaterialTheme.typography.labelLarge)
            if (s.drops.isEmpty()) Text("Elites and bosses drop gear (Idle Runs keep EPIC and better).", color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
            else Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (g in s.drops) GearIconView(g, Modifier.size(44.dp))
            }
        }
    }
}

@Composable
private fun Summary(s: IdleRunState, time: Float) {
    val defeated = s.status == IdleStatus.DEFEATED
    TerminalCard(accent = if (defeated) Palette.Red else Palette.Gold) {
        Column {
            Text(if (defeated) "OPERATOR DEFEATED" else "IDLE RUN COMPLETE", color = if (defeated) Palette.Red else Palette.Gold, style = MaterialTheme.typography.titleLarge)
            Text(s.endReason.ifEmpty { "Run ended" } + " · " + idleDifficultyLabel(s), color = Palette.TextSecondary, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(8.dp))
            Battle(s, time, defeated = defeated)
            Spacer(Modifier.height(10.dp))
            StatRow(
                Triple("HIGHEST LEVEL", s.highestLevel.toString(), Palette.TextPrimary),
                Triple("RUN TIME", clock(s.durationSeconds(0)), Palette.TextPrimary)
            )
            StatRow(
                Triple("€ EARNED", amount(s.eurosEarned), Palette.Euro),
                Triple("XP EARNED", amount(s.xpEarned), Palette.Cyan)
            )
            Text(
                if (s.collected) "Rewards added: € to your account, XP and ${s.drops.size} gear to ${s.characterName.ifEmpty { "your character" }}."
                else "Collecting rewards…",
                color = Palette.Green, style = MaterialTheme.typography.labelSmall
            )
        }
    }
    Spacer(Modifier.height(10.dp))
    if (s.drops.isNotEmpty() || s.chipLog.isNotEmpty()) { Loot(s); Spacer(Modifier.height(10.dp)) }
}

@Composable
private fun NewRun(p: PlayerProfile, onStart: (Difficulty) -> Unit) {
    var inferno by remember { mutableStateOf(false) }
    var tier by remember { mutableIntStateOf(p.infernoUnlocked.coerceAtLeast(1)) }
    TerminalCard(accent = Palette.Cyan) {
        Column {
            Text("START NEW IDLE RUN?", color = Palette.Cyan, style = MaterialTheme.typography.titleMedium)
            val cls = Operatives.byId(p.selectedOperative)
            Text("${p.characterName.ifEmpty { cls.name }} · ${cls.name} · ${operativeLabel(p.operativeXp)} · ${p.equipped.size} gear equipped",
                color = Palette.TextSecondary, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CyberButton("STANDARD", Modifier.weight(1f), accent = Palette.Cyan, primary = !inferno) { inferno = false }
                // INFERNO only once it is unlocked (beat World 15 on HARD).
                if (p.infernoUnlocked > 0) CyberButton("INFERNO", Modifier.weight(1f), accent = Palette.Red, primary = inferno) { inferno = true }
            }
            if (inferno && p.infernoUnlocked > 0) {
                Spacer(Modifier.height(8.dp))
                InfernoStepper(tier, p.infernoUnlocked) { tier = it }
            }
            Spacer(Modifier.height(10.dp))
            Text("• One threat at a time, a boss every 10 levels.\n• Your equipped operator's real stats, gear and class.\n• Upgrade chips picked for your build.\n• Earns 40% of the € and 50% of the XP a normal run would.\n• Keeps going with the game closed. Add the IDLE RUN widget to your home screen.",
                color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(10.dp))
            val d = if (inferno) Difficulty.inferno(tier) ?: Difficulty.MEDIUM else Difficulty.MEDIUM
            val why = IdleRunSimulator.cannotStart(p, d)
            CyberButton("START IDLE RUN", Modifier.fillMaxWidth(), accent = Palette.Green, primary = true, enabled = why == null,
                subtitle = why ?: if (inferno) "INFERNO $tier" else "STANDARD") { onStart(d) }
        }
    }
}
