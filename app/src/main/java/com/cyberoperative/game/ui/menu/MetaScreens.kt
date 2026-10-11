package com.cyberoperative.game.ui.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cyberoperative.game.platform.AppInfo
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
            Text("CLASS", color = Palette.Cyan, style = MaterialTheme.typography.labelLarge)
            Text("Pick who you deploy as. Every class can wear the paid skins and earns its own TITANIUM and PURPLE skins.",
                color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            for (cls in Operatives.all) {
                ClassCard(profile, cls, selected = cls.id == op.id) {
                    save.update { p -> com.cyberoperative.game.meta.StoreManager.selectClass(p, cls.id) }
                }
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(2.dp))
            TerminalCard {
                Column {
                    Text("OPERATIVE LEVEL $level", color = Palette.Cyan, style = MaterialTheme.typography.titleMedium)
                    Text("Operative XP: ${"%,d".format(profile.operativeXp)}", color = Palette.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    Text("Higher levels unlock more permanent upgrades.", color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(10.dp))
            RecordsCard(profile)
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** One class in the picker: its figure in its skin, what it does, its records and class skins. */
@Composable
private fun ClassCard(p: PlayerProfile, cls: com.cyberoperative.game.data.OperativeDef, selected: Boolean, onSelect: () -> Unit) {
    val skin = com.cyberoperative.game.meta.StoreManager.effectiveSkin(p, cls.id)
    val rec = p.classRecords[cls.id]
    Row(
        Modifier
            .fillMaxWidth()
            .background(Palette.Surface, RoundedCornerShape(10.dp))
            .border(if (selected) 2.dp else 1.dp, if (selected) Palette.Green else Palette.Divider, RoundedCornerShape(10.dp))
            .clickable(onClick = onSelect)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        com.cyberoperative.game.ui.game.FigurePreview(
            com.cyberoperative.game.ui.game.BodyStyle.byId(cls.bodyId), skin, Modifier.width(84.dp).height(112.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(cls.name, color = if (selected) Palette.Green else Palette.TextPrimary, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (selected) Text("SELECTED", color = Palette.Green, style = MaterialTheme.typography.labelSmall)
            }
            Text(cls.description, color = Palette.TextSecondary, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(4.dp))
            for (t in cls.traits) Text("· $t", color = Palette.Cyan, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "BEST · NORMAL ${rec?.bestNormal ?: 0} · HARD ${rec?.bestHard ?: 0}",
                color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall
            )
            Column {
                for (cs in com.cyberoperative.game.data.OperativeSkins.classSkins) {
                    val got = com.cyberoperative.game.meta.StoreManager.skinUnlocked(p, cs, cls.id)
                    val diff = if (cs.unlock?.difficulty == "HARD") "HARD" else "NORMAL"
                    Text(
                        if (got) "${cs.name} SKIN ✓" else if (cs.unlock?.campaign == true) "${cs.name} SKIN · beat World 15's boss" else "${cs.name} SKIN · reach L${cs.unlock?.level} on $diff",
                        color = if (got) Color(cs.edge) else Palette.TextMuted,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
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
    var editingTouch by remember { mutableStateOf(false) }
    if (editingTouch) {
        com.cyberoperative.game.platform.PlatformBackHandler { editingTouch = false }
        TouchControlsEditor(s, onChange = { set(it) }, onDone = { editingTouch = false })
        return
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
                    if (!com.cyberoperative.game.platform.GameKeys.active) Toggle("HAPTICS", s.haptics) { set(s.copy(haptics = it)) }
                    Toggle("DAMAGE NUMBERS", s.damageNumbers) { set(s.copy(damageNumbers = it)) }
                    Toggle("SCREEN SHAKE", s.screenShake) { set(s.copy(screenShake = it)) }
                }
            }
            if (!com.cyberoperative.game.platform.GameKeys.active) {
                Spacer(Modifier.height(10.dp))
                ScreenCard(s, set = { set(it) }, onEditTouch = { editingTouch = true })
            }
            if (com.cyberoperative.game.platform.GameKeys.active) {
                Spacer(Modifier.height(10.dp))
                KeybindsCard(s.keyBinds) { next ->
                    com.cyberoperative.game.ui.common.KeyBinds.map = next
                    set(s.copy(keyBinds = next))
                    audio.play(GameSound.UI_CLICK)
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
                    Text("Version ${AppInfo.versionName}", color = Palette.TextSecondary, style = MaterialTheme.typography.bodySmall)
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

/** PC: rebind the game's keys. Click an action, then press the new key (Esc cancels). */
@Composable
private fun KeybindsCard(binds: Map<String, Long>, onChange: (Map<String, Long>) -> Unit) {
    val keys = com.cyberoperative.game.platform.GameKeys
    var waiting by remember { mutableStateOf<com.cyberoperative.game.ui.common.KeyAction?>(null) }
    TerminalCard {
        Column {
            Text("KEYBINDS", color = Palette.Cyan, style = MaterialTheme.typography.labelLarge)
            Text(
                "Click an action, then press its new key (Esc cancels). Arrow keys always move and Esc always pauses.",
                color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall
            )
            Spacer(Modifier.height(8.dp))
            for (a in com.cyberoperative.game.ui.common.KeyAction.entries) {
                val code = binds[a.name] ?: a.default.keyCode
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(a.label, color = Palette.TextPrimary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    CyberButton(
                        if (waiting == a) "PRESS A KEY…" else keys.keyName(code).uppercase(),
                        accent = if (waiting == a) Palette.Orange else Palette.Cyan,
                        modifier = Modifier.width(170.dp)
                    ) {
                        waiting = a
                        keys.captureKey { pressed ->
                            waiting = null
                            if (pressed != null) onChange(com.cyberoperative.game.ui.common.KeyBinds.rebind(binds, a, pressed))
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            CyberButton("RESET TO DEFAULTS", Modifier.fillMaxWidth()) { onChange(emptyMap()) }
            Spacer(Modifier.height(6.dp))
            Text(
                "Controller: left stick move · right stick aim & fire · LB HP charge · RB Packet Bomb · START pause · B back",
                color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

/**
 * Android: SCREEN (PORTRAIT or LANDSCAPE), the joystick and the touch layout. LANDSCAPE turns
 * the app sideways: wide rooms, the HUD strip above the arena, joystick bottom-left and the
 * buttons bottom-right. A run in progress keeps the room shape it started with.
 */
@Composable
private fun ScreenCard(s: GameSettings, set: (GameSettings) -> Unit, onEditTouch: () -> Unit) {
    TerminalCard(accent = Palette.Purple) {
        Column {
            Text("SCREEN", color = Palette.Purple, style = MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CyberButton("PORTRAIT", Modifier.weight(1f), accent = if (!s.landscape) Palette.Green else Palette.Cyan, primary = !s.landscape) { set(s.copy(landscape = false)) }
                CyberButton("LANDSCAPE", Modifier.weight(1f), accent = if (s.landscape) Palette.Green else Palette.Cyan, primary = s.landscape) { set(s.copy(landscape = true)) }
            }
            Text(
                "LANDSCAPE: wide rooms, HUD above the arena, joystick left and buttons right. Rooms change shape from your next run.",
                color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall
            )
            Spacer(Modifier.height(10.dp))
            Text("JOYSTICK", color = Palette.TextPrimary, style = MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((mode, label) in listOf("AUTO" to "AUTO", "FLOAT" to "FREE FLOAT", "LOCKED" to "LOCKED")) {
                    CyberButton(label, Modifier.weight(1f), accent = if (s.joystickMode == mode) Palette.Green else Palette.Cyan, primary = s.joystickMode == mode) {
                        set(s.copy(joystickMode = mode))
                    }
                }
            }
            Text(
                "AUTO: locked bottom-left in landscape, follows your thumb in portrait.",
                color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall
            )
            Spacer(Modifier.height(8.dp))
            CyberButton("TOUCH CONTROLS LAYOUT", Modifier.fillMaxWidth()) { onEditTouch() }
        }
    }
}

/** Full-screen editor: drag the joystick and buttons; RESET restores this orientation's defaults. */
@Composable
private fun TouchControlsEditor(s: GameSettings, onChange: (GameSettings) -> Unit, onDone: () -> Unit) {
    val landscape = com.cyberoperative.game.ui.common.PlayMode.landscape
    Box(Modifier.fillMaxSize().background(Palette.Background)) {
        com.cyberoperative.game.ui.common.LivingBackground(Modifier.fillMaxSize(), intensity = 0.5f)
        Column(Modifier.align(Alignment.TopCenter).padding(top = 18.dp, start = 16.dp, end = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("TOUCH CONTROLS · ${if (landscape) "LANDSCAPE" else "PORTRAIT"}", color = Palette.Cyan, style = MaterialTheme.typography.titleMedium)
            Text("Drag the joystick and the buttons where you want them.", color = Palette.TextSecondary, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val next = when (s.joystickMode) { "AUTO" -> "FLOAT"; "FLOAT" -> "LOCKED"; else -> "AUTO" }
                CyberButton("JOYSTICK: ${if (s.joystickMode == "FLOAT") "FREE FLOAT" else s.joystickMode}") { onChange(s.copy(joystickMode = next)) }
                CyberButton("RESET") { onChange(s.copy(touchLayout = com.cyberoperative.game.ui.game.TouchLayout.reset(s, landscape))) }
                CyberButton("DONE", accent = Palette.Green, primary = true) { onDone() }
            }
        }
        com.cyberoperative.game.ui.game.TouchLayoutEditor(s, landscape) { onChange(s.copy(touchLayout = it)) }
    }
}
