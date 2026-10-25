package com.cyberoperative.game.ui.menu

import com.cyberoperative.game.i18n.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cyberoperative.game.engine.RunSnapshot
import com.cyberoperative.game.save.PlayerProfile
import com.cyberoperative.game.ui.common.CurrencyChip
import com.cyberoperative.game.ui.common.CyberButton
import com.cyberoperative.game.ui.common.CyberTitleLogo
import com.cyberoperative.game.ui.common.LivingBackground
import com.cyberoperative.game.ui.common.OperativeEmblem
import com.cyberoperative.game.ui.theme.Palette
import kotlin.math.sin

enum class MenuTarget(val label: String) {
    PLAY(tr("PLAY")), CO_OP(tr("CO-OP")), OPERATIVE(tr("CHARACTER")), VAULT(tr("DATA VAULT")), UPGRADES(tr("OVERCLOCK")), ARMORY(tr("ARMORY")), CODEX(tr("BOSS CODEX")), PET_CODEX(tr("PET CODEX")),
    SKINS(tr("SKINS")), STORE(tr("STORE")), LEADERBOARD(tr("LEADERBOARD")), ACHIEVEMENTS(tr("ACHIEVEMENTS")),
    SETTINGS(tr("SETTINGS")), ABOUT(tr("ABOUT")), ENDLESS(tr("ENDLESS MODE")), CONTINUE(tr("CONTINUE")),
    /** IDLE RUNS (0.37.0): Android only (the PC build has no Idle Runs host). */
    IDLE_RUNS(tr("IDLE RUNS")),
    /** Settings › LANGUAGE (0.55.0): not on the menu itself. */
    LANGUAGE(tr("LANGUAGE"));

    companion object {
        /** The small grid buttons (the big buttons are drawn on their own). */
        val grid: List<MenuTarget> get() = entries.filter { it != PLAY && it != ENDLESS && it != CONTINUE && it != CO_OP && it != IDLE_RUNS && it != LANGUAGE }
    }
}

/** Main menu (§6). One big PLAY, the rest in a compact two-column grid. */
@Composable
fun MainMenuScreen(profile: PlayerProfile, onSelect: (MenuTarget) -> Unit) {
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val s = withFrameNanos { it }
        while (true) withFrameNanos { t = (it - s) / 1e9f }
    }
    if (com.cyberoperative.game.ui.common.PlayMode.landscape) {
        LandscapeMainMenu(profile, t, onSelect)
        return
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Palette.Background)
    ) {
        LivingBackground(Modifier.fillMaxSize())
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(operativeLabel(profile.operativeXp), color = Palette.Green, style = MaterialTheme.typography.labelLarge)
                Row {
                    CurrencyChip("€", profile.euros, Palette.Euro)
                    Spacer(Modifier.width(8.dp))
                    CurrencyChip("◇", profile.diamonds, Palette.Diamond)
                }
            }
            Spacer(Modifier.height(22.dp))
            OperativeEmblem(
                com.cyberoperative.game.meta.StoreManager.effectiveSkin(profile),
                Modifier
                    .size(150.dp)
                    .scale(1f + 0.02f * sin(t * 2f))
            )
            Spacer(Modifier.height(10.dp))
            // Neon wordmark, as in the key art (vector, scales to any width).
            CyberTitleLogo(Modifier.fillMaxWidth(), glow = 0.75f + 0.25f * sin(t * 2.4f))
            Spacer(Modifier.height(10.dp))
            if (profile.highestLevel > 0) {
                Text(
                    tr("BEST LEVEL {0}   ·   BEST SCORE {1}", profile.highestLevel, "%,d".format(profile.bestScore)),
                    color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall
                )
            }
            Spacer(Modifier.height(20.dp))
            // A saved operation turns PLAY into CONTINUE (owner, 2026-10-08).
            val saved = remember(profile.savedRun) { RunSnapshot.decodeOrNull(profile.savedRun) }
            if (saved != null) {
                CyberButton(
                    tr("CONTINUE"), primary = true, accent = Palette.Green,
                    subtitle = saved.label,
                    modifier = Modifier.fillMaxWidth()
                ) { onSelect(MenuTarget.CONTINUE) }
                Spacer(Modifier.height(10.dp))
                CyberButton(
                    tr("NEW OPERATION"), accent = Palette.Cyan,
                    subtitle = tr("START OVER"),
                    modifier = Modifier.fillMaxWidth()
                ) { onSelect(MenuTarget.PLAY) }
            } else {
                CyberButton(
                    tr("PLAY"), primary = true, accent = Palette.Green,
                    subtitle = if (profile.bestWorld > 0) tr("CAMPAIGN · BEST: WORLD {0}", profile.bestWorld) else tr("CAMPAIGN · 15 SERVER WORLDS"),
                    modifier = Modifier.fillMaxWidth()
                ) { onSelect(MenuTarget.PLAY) }
            }
            Spacer(Modifier.height(10.dp))
            CyberButton(
                tr("ENDLESS MODE"), accent = Palette.Purple,
                subtitle = if (profile.endlessBestStage > 0) tr("BEST LEVEL {0}", profile.endlessBestStage) else tr("NO MAP · DOORS ANYWHERE · NO END"),
                modifier = Modifier.fillMaxWidth()
            ) { onSelect(MenuTarget.ENDLESS) }
            Spacer(Modifier.height(10.dp))
            CyberButton(
                tr("CO-OP"), accent = Palette.Magenta,
                subtitle = tr("PLAY WITH A FRIEND"),
                modifier = Modifier.fillMaxWidth()
            ) { onSelect(MenuTarget.CO_OP) }
            if (com.cyberoperative.game.idle.IdleRuns.supported) {
                Spacer(Modifier.height(10.dp))
                CyberButton(
                    tr("IDLE RUNS"), accent = Palette.Orange,
                    subtitle = tr("YOUR OPERATOR FIGHTS ON · 40% € · 50% XP"),
                    modifier = Modifier.fillMaxWidth()
                ) { onSelect(MenuTarget.IDLE_RUNS) }
            }
            Spacer(Modifier.height(14.dp))
            val rest = MenuTarget.grid
            for (row in rest.chunked(2)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (item in row) {
                        CyberButton(item.label, modifier = Modifier.weight(1f)) { onSelect(item) }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text("HamieTon.dev", color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** Highest operative level (owner, 2026-10-08). */
/**
 * The level that drives monster level and unlocks (owner, 2026-10-13): OP Lv 1–60 plus a quarter
 * of OVERCLOCK. See [com.cyberoperative.game.data.OpProgress].
 */
fun operativeLevel(xp: Long): Int = com.cyberoperative.game.data.OpProgress.of(xp).effective

/** "OP LV 24" / "OP LV 60 · OC 12" for menus. */
fun operativeLabel(xp: Long): String = com.cyberoperative.game.data.OpProgress.of(xp).label.uppercase()
