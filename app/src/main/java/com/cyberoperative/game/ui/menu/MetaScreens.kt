package com.cyberoperative.game.ui.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cyberoperative.game.BuildConfig
import com.cyberoperative.game.audio.AudioManager
import com.cyberoperative.game.data.Bosses
import com.cyberoperative.game.data.Enemies
import com.cyberoperative.game.data.Operatives
import com.cyberoperative.game.data.PermanentUpgrades
import com.cyberoperative.game.data.Upgrades
import com.cyberoperative.game.engine.GameSound
import com.cyberoperative.game.meta.Achievements
import com.cyberoperative.game.save.GameSettings
import com.cyberoperative.game.save.PlayerProfile
import com.cyberoperative.game.save.SaveRepository
import com.cyberoperative.game.ui.common.CurrencyChip
import com.cyberoperative.game.ui.common.CyberButton
import com.cyberoperative.game.ui.common.ScreenScaffold
import com.cyberoperative.game.ui.common.TerminalCard
import com.cyberoperative.game.ui.theme.Palette

// ---------------------------------------------------------------------------
// UPGRADES — permanent € progression (§31)
// ---------------------------------------------------------------------------

@Composable
fun PermanentUpgradesScreen(save: SaveRepository, audio: AudioManager, onBack: () -> Unit) {
    val profile by save.profile.collectAsState()
    val opLevel = operativeLevel(profile.operativeXp)
    ScreenScaffold("UPGRADES", onBack, trailing = { CurrencyChip("€", profile.euros, Palette.Euro) }) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text(
                    "Past each upgrade's max, MASTERY levels keep going (up to ${"%,d".format(com.cyberoperative.game.data.PermanentUpgradeDef.MAX_LEVEL)}). " +
                        "Each mastery level needs one OP level and gives a little less than the last. Threats grow with your mastery too.",
                    color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall
                )
            }
            items(PermanentUpgrades.all, key = { it.id }) { def ->
                val level = profile.permanentUpgrades[def.id] ?: 0
                val maxed = level >= def.levelCap
                val cost = def.costFor(level)
                // Locked until the OP level the next level needs (WEAPON SLOTS: OP 10/20/30/50/80).
                val needOp = if (def.levelUnlocks != null) def.unlockFor(level) else if (level < def.maxLevel) def.unlockAt else level - def.maxLevel + 1
                val locked = !maxed && (opLevel < def.unlockAt || level >= def.capAt(opLevel))
                val mastered = level >= def.maxLevel
                val affordable = profile.euros >= cost
                TerminalCard(accent = if (mastered) Palette.Gold else Palette.Divider) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.alpha(if (locked && level == 0) 0.55f else 1f)) {
                        Column(Modifier.weight(1f)) {
                            Text(def.name, color = Palette.TextPrimary, style = MaterialTheme.typography.titleSmall)
                            Text(def.description, color = Palette.TextSecondary, style = MaterialTheme.typography.bodySmall)
                            Text(
                                (if (level > def.maxLevel) "LV ${"%,d".format(level)} · MASTERY ${"%,d".format(level - def.maxLevel)}" else "LV $level/${def.maxLevel}") +
                                    (if (level > 0) "  ·  ${def.effectAt(level)}" else "") +
                                    (if (level > def.maxLevel && def.mastery == com.cyberoperative.game.data.Mastery.SOFT) "  ·  soft cap" else ""),
                                color = Palette.Green, style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        when {
                            maxed -> Text("MAX", color = Palette.Gold, style = MaterialTheme.typography.titleMedium)
                            locked -> LockedBadge(needOp)
                            else -> CyberButton("€ ${"%,d".format(cost)}", enabled = affordable, accent = Palette.Euro) {
                                var bought = false
                                save.update { p ->
                                    val cur = p.permanentUpgrades[def.id] ?: 0
                                    val c = def.costFor(cur)
                                    if (cur >= def.capAt(operativeLevel(p.operativeXp)) || p.euros < c) p
                                    else {
                                        bought = true
                                        val next = p.copy(euros = p.euros - c, permanentUpgrades = p.permanentUpgrades + (def.id to cur + 1))
                                        val ach = Achievements.evaluateProfileOnly(next)
                                        next.copy(
                                            achievements = next.achievements + ach.map { it.id },
                                            euros = next.euros + ach.sumOf { it.rewardEuros.toLong() }
                                        )
                                    }
                                }
                                audio.play(if (bought) GameSound.UPGRADE_SELECTED else GameSound.UI_BACK)
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

// ---------------------------------------------------------------------------
// OPERATIVE — account level and records (§39)
// ---------------------------------------------------------------------------

@Composable
fun OperativeScreen(save: SaveRepository, onBack: () -> Unit) {
    val profile by save.profile.collectAsState()
    val op = Operatives.byId(profile.selectedOperative)
    val level = operativeLevel(profile.operativeXp)
    ScreenScaffold("OPERATIVE", onBack) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("BODY DESIGN", color = Palette.Cyan, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (style in com.cyberoperative.game.ui.game.BodyStyle.entries) {
                    val selected = profile.operativeBody == style.id
                    val owned = !style.premium || style.id in profile.ownedOperatives
                    Column(
                        Modifier
                            .weight(1f)
                            .background(Palette.Surface, RoundedCornerShape(8.dp))
                            .border(if (selected) 2.dp else 1.dp, if (selected) Palette.Green else if (style.premium) Palette.Cyan else Palette.Divider, RoundedCornerShape(8.dp))
                            .clickable {
                                save.update { p ->
                                    com.cyberoperative.game.meta.StoreManager.equipBody(p, style.id, style.premium)
                                        ?: com.cyberoperative.game.meta.StoreManager.buyNeonOperative(p)
                                        ?: p
                                }
                            }
                            .padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        com.cyberoperative.game.ui.game.FigurePreview(style, com.cyberoperative.game.data.OperativeSkins.byId(profile.selectedSkin), Modifier.fillMaxWidth().height(110.dp))
                        Text(style.label, color = if (selected) Palette.Green else Palette.TextPrimary, style = MaterialTheme.typography.labelSmall, maxLines = 2)
                        if (!owned) Text("◇${com.cyberoperative.game.data.StoreCatalog.NEON_OPERATIVE_PRICE}", color = Palette.Diamond, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            TerminalCard(accent = Palette.Green) {
                Column {
                    Text(op.name, color = Palette.Green, style = MaterialTheme.typography.titleLarge)
                    Text(op.description, color = Palette.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(6.dp))
                    Text("PASSIVE: ${op.passive}", color = Palette.Cyan, style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(10.dp))
            TerminalCard {
                Column {
                    Text("OPERATIVE LEVEL $level", color = Palette.Cyan, style = MaterialTheme.typography.titleMedium)
                    Text("Operative XP: ${"%,d".format(profile.operativeXp)}", color = Palette.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    Text("Higher levels unlock more permanent upgrades.", color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(10.dp))
            RecordsCard(profile)
            Spacer(Modifier.height(10.dp))
            Text("More operatives will join the roster in a future update.", color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun RecordsCard(p: PlayerProfile) {
    TerminalCard {
        Column {
            Text("SERVICE RECORD", color = Palette.TextPrimary, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            KV("Highest level", p.highestLevel.toString())
            KV("Best score", "%,d".format(p.bestScore))
            KV("Threats eliminated", "%,d".format(p.totalKills))
            KV("Bosses defeated", "%,d".format(p.totalBosses))
            KV("Events completed", p.totalEvents.toString())
            KV("Operations", p.totalRuns.toString())
            KV("Longest run", formatSeconds(p.longestRunSeconds))
        }
    }
}

fun formatSeconds(s: Float): String {
    val t = s.toInt()
    return "%d:%02d".format(t / 60, t % 60)
}

@Composable
private fun KV(k: String, v: String, color: Color = Palette.TextPrimary) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(k, color = Palette.TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(v, color = color, style = MaterialTheme.typography.labelMedium)
    }
}

// ---------------------------------------------------------------------------
// LEADERBOARD — local records now; Google Play Games later (§40)
// ---------------------------------------------------------------------------

@Composable
fun LeaderboardScreen(profile: PlayerProfile, onBack: () -> Unit) {
    ScreenScaffold("LEADERBOARD", onBack) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("PERSONAL BESTS (THIS DEVICE)", color = Palette.Cyan, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            TerminalCard {
                Column {
                    KV("HIGHEST LEVEL", profile.highestLevel.toString(), Palette.Green)
                    KV("HIGHEST SCORE", "%,d".format(profile.bestScore), Palette.Green)
                    KV("MOST ENEMIES ELIMINATED (TOTAL)", "%,d".format(profile.totalKills), Palette.Green)
                    KV("MOST BOSSES DEFEATED (TOTAL)", "%,d".format(profile.totalBosses), Palette.Green)
                    KV("LONGEST RUN", formatSeconds(profile.longestRunSeconds), Palette.Green)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Global, weekly and friends leaderboards arrive with Google Play Games Services.",
                color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

// ---------------------------------------------------------------------------
// ACHIEVEMENTS (§59)
// ---------------------------------------------------------------------------

@Composable
fun AchievementsScreen(profile: PlayerProfile, onBack: () -> Unit) {
    val done = Achievements.all.count { it.id in profile.achievements }
    ScreenScaffold("ACHIEVEMENTS", onBack, trailing = {
        Text("$done/${Achievements.all.size}", color = Palette.Gold, style = MaterialTheme.typography.labelLarge)
    }) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(Achievements.all, key = { it.id }) { a ->
                val got = a.id in profile.achievements
                TerminalCard(accent = if (got) Palette.Gold else Palette.Divider) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (got) "★" else "☆", color = if (got) Palette.Gold else Palette.TextMuted, style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(a.name, color = if (got) Palette.TextPrimary else Palette.TextSecondary, style = MaterialTheme.typography.titleSmall)
                            Text(a.description, color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall)
                        }
                        Text("€ ${a.rewardEuros}", color = Palette.Euro, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

// ---------------------------------------------------------------------------
// ARMORY — catalogue of run modules, threats and bosses (codex)
// ---------------------------------------------------------------------------

@Composable
fun ArmoryScreen(onBack: () -> Unit) {
    ScreenScaffold("ARMORY", onBack) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item { Section("RUN MODULES") }
            items(Upgrades.all.filter { !it.instant }, key = { "u_" + it.id }) { u ->
                TerminalCard(accent = Color(u.rarity.color).copy(alpha = 0.5f)) {
                    Column {
                        Text("${u.glyph}  ${u.name}", color = Color(u.rarity.color), style = MaterialTheme.typography.titleSmall)
                        Text(u.description, color = Palette.TextSecondary, style = MaterialTheme.typography.bodySmall)
                        u.evolvesFrom?.let {
                            Text("Evolves from ${Upgrades.byId(it).name} (max level)" + (u.alsoRequires?.let { r -> " + ${Upgrades.byId(r).name}" } ?: ""),
                                color = Palette.Purple, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            item { Section("KNOWN THREATS") }
            items(Enemies.all, key = { "e_" + it.id }) { e ->
                TerminalCard {
                    Column {
                        Text("[${e.tag}] ${e.name}", color = Color(e.color), style = MaterialTheme.typography.titleSmall)
                        Text(e.codex, color = Palette.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item { Section("BOSS DOSSIERS") }
            items(Bosses.roster, key = { "b_" + it.id }) { b ->
                TerminalCard(accent = Color(b.color).copy(alpha = 0.5f)) {
                    Column {
                        Text("${b.tag} ${b.name} — ${b.title}", color = Color(b.color), style = MaterialTheme.typography.titleSmall)
                        Text(b.codex, color = Palette.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun Section(t: String) {
    Text(t, color = Palette.Cyan, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
}

// ---------------------------------------------------------------------------
// SETTINGS
// ---------------------------------------------------------------------------

@Composable
fun SettingsScreen(save: SaveRepository, audio: AudioManager, onBack: () -> Unit) {
    val profile by save.profile.collectAsState()
    val s = profile.settings
    fun set(next: GameSettings) {
        save.update { it.copy(settings = next) }
        audio.setVolumes(next.musicVolume, next.sfxVolume)
        audio.hapticsEnabled = next.haptics
    }
    ScreenScaffold("SETTINGS", onBack) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            TerminalCard {
                Column {
                    Text("MUSIC VOLUME ${(s.musicVolume * 100).toInt()}%", color = Palette.TextPrimary, style = MaterialTheme.typography.labelLarge)
                    Slider(s.musicVolume, { set(s.copy(musicVolume = it)) }, colors = sliderColors())
                    Text("EFFECTS VOLUME ${(s.sfxVolume * 100).toInt()}%", color = Palette.TextPrimary, style = MaterialTheme.typography.labelLarge)
                    Slider(s.sfxVolume, { set(s.copy(sfxVolume = it)) }, onValueChangeFinished = { audio.play(GameSound.UI_CLICK) }, colors = sliderColors())
                }
            }
            Spacer(Modifier.height(10.dp))
            TerminalCard {
                Column {
                    Toggle("HAPTICS", s.haptics) { set(s.copy(haptics = it)) }
                    Toggle("DAMAGE NUMBERS", s.damageNumbers) { set(s.copy(damageNumbers = it)) }
                }
            }
            Spacer(Modifier.height(10.dp))
            CyberButton("REPLAY TUTORIAL", Modifier.fillMaxWidth()) {
                save.update { it.copy(tutorialDone = false) }
                audio.play(GameSound.UI_CLICK)
            }
        }
    }
}

@Composable
private fun sliderColors() = SliderDefaults.colors(thumbColor = Palette.Cyan, activeTrackColor = Palette.Cyan, inactiveTrackColor = Palette.Divider)

@Composable
private fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Palette.TextPrimary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        Switch(value, onChange, colors = SwitchDefaults.colors(checkedThumbColor = Palette.Background, checkedTrackColor = Palette.Green))
    }
}

// ---------------------------------------------------------------------------
// ABOUT
// ---------------------------------------------------------------------------

@Composable
fun AboutScreen(onBack: () -> Unit) {
    ScreenScaffold("ABOUT", onBack) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            TerminalCard(accent = Palette.Cyan) {
                Column {
                    Text("CYBER OPERATIVE", color = Palette.Cyan, style = MaterialTheme.typography.titleLarge)
                    Text("Version ${BuildConfig.VERSION_NAME}", color = Palette.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "An endless cyber-themed roguelike action RPG. Enter the network, eliminate " +
                            "digital threats, strengthen the operative and survive as deep into the " +
                            "compromised system as possible.",
                        color = Palette.TextPrimary, style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            TerminalCard {
                Column {
                    Text("PART OF THE CYOPS UNIVERSE", color = Palette.Green, style = MaterialTheme.typography.titleSmall)
                    Text("Shares its world, operative and threats with CyOps TD.", color = Palette.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Text("No real hacking takes place. All terminal output is fictional.", color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall)
                    Text("€ and ◇ are fictional in-game currencies.", color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().background(Palette.Surface, RoundedCornerShape(8.dp)).border(1.dp, Palette.Divider, RoundedCornerShape(8.dp)).padding(14.dp)) {
                Text("© HamieTon.dev. All rights reserved.", color = Palette.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Placeholder for not-yet-built sections (no fake purchases, ever)
// ---------------------------------------------------------------------------

@Composable
fun ComingSoonScreen(title: String, plan: List<String>, onBack: () -> Unit) {
    ScreenScaffold(title, onBack) {
        Column {
            TerminalCard(accent = Palette.Purple) {
                Column {
                    Text("MODULE NOT YET DEPLOYED", color = Palette.Purple, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    for (line in plan) Text("> $line", color = Palette.TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}


/** Grey padlock + the OP level that unlocks the next level of an upgrade. */
@Composable
private fun LockedBadge(opLevel: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp)) {
        androidx.compose.foundation.Canvas(Modifier.size(width = 18.dp, height = 22.dp)) {
            val c = Palette.TextMuted
            val stroke = 2.dp.toPx()
            // Shackle
            drawArc(
                c, 180f, 180f, false,
                topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.2f, 0f),
                size = androidx.compose.ui.geometry.Size(size.width * 0.6f, size.height * 0.6f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(stroke)
            )
            drawLine(c, androidx.compose.ui.geometry.Offset(size.width * 0.2f, size.height * 0.3f), androidx.compose.ui.geometry.Offset(size.width * 0.2f, size.height * 0.45f), stroke)
            drawLine(c, androidx.compose.ui.geometry.Offset(size.width * 0.8f, size.height * 0.3f), androidx.compose.ui.geometry.Offset(size.width * 0.8f, size.height * 0.45f), stroke)
            // Body + keyhole
            drawRoundRect(
                c, topLeft = androidx.compose.ui.geometry.Offset(0f, size.height * 0.45f),
                size = androidx.compose.ui.geometry.Size(size.width, size.height * 0.55f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
            )
            drawCircle(Palette.Surface, size.width * 0.1f, androidx.compose.ui.geometry.Offset(size.width / 2f, size.height * 0.68f))
        }
        Text("OP LVL $opLevel", color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
    }
}
