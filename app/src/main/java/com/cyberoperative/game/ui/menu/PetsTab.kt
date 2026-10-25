package com.cyberoperative.game.ui.menu

import com.cyberoperative.game.i18n.tr
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.key
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import com.cyberoperative.game.ui.common.rarityCard
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cyberoperative.game.data.Pet
import com.cyberoperative.game.data.PetEgg
import com.cyberoperative.game.data.Pets
import com.cyberoperative.game.meta.Incubators
import com.cyberoperative.game.meta.TrustedClock
import com.cyberoperative.game.platform.PlatformClock
import com.cyberoperative.game.save.PlayerProfile
import com.cyberoperative.game.save.SaveRepository
import com.cyberoperative.game.ui.common.CyberButton
import com.cyberoperative.game.ui.common.EggIconView
import com.cyberoperative.game.ui.common.PetArt
import com.cyberoperative.game.ui.common.PetIconView
import com.cyberoperative.game.ui.common.PetPicture
import com.cyberoperative.game.ui.theme.Palette
import kotlinx.coroutines.delay

/** "2(H):40(M):59(S)" as the owner wrote it, shown as 2:40:59. */
fun hatchClock(ms: Long): String {
    val s = (ms + 999) / 1000
    return "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
}

/**
 * COMPANIONS (owner, 0.38.0): the incubator (one egg hatching on a real-time countdown, three
 * waiting, six Inferno Stone sockets), the eggs from your inventory with SEND TO INCUBATOR,
 * and your pets (equip one to follow you into runs).
 */
@Composable
fun PetsTab(
    save: SaveRepository, p: PlayerProfile, wide: Boolean, inRun: Boolean = false, onPetChanged: () -> Unit = {},
    /** In a solo run a sale goes into the run's wallet (like gear); otherwise to the saved €. */
    onSold: ((Int) -> Unit)? = null
) {
    val host = com.cyberoperative.game.idle.IdleRuns.host
    var selEgg by remember { mutableStateOf<Long?>(null) }
    // Opens on the equipped pet, so its stats and abilities show right away.
    var selPet by remember { mutableStateOf(p.activePet.takeIf { it != 0L }) }
    var note by remember { mutableStateOf("") }
    // Catch up when the page opens; read network time if the clock is waiting on it.
    LaunchedEffect(Unit) {
        PetArt.preload()
        TrustedClock.refresh { }
        save.update { Incubators.tick(it) }
    }
    LaunchedEffect(p.incubator.waitingForTime) {
        while (p.incubator.waitingForTime) {
            TrustedClock.refresh()
            delay(4000)
            save.update { Incubators.tick(it) }
        }
    }
    // The countdown runs on the since-boot clock between saves (changing the phone's time doesn't move it).
    var monoNow by remember { mutableLongStateOf(PlatformClock.mono()) }
    LaunchedEffect(Unit) { while (true) { delay(250); monoNow = PlatformClock.mono() } }
    // Keep the hatch reminder in step with what's in the incubator.
    LaunchedEffect(p.incubator.eggsIn.map { it.uid }, p.incubator.stones, p.incubator.waitingForTime) {
        com.cyberoperative.game.meta.PetReminders.update(p)
        if (p.incubator.busy) host?.requestNotifications()
    }
    val inc = p.incubator
    val sameBoot = inc.clock.boot == PlatformClock.bootId()
    val elapsed = if (sameBoot && !inc.waitingForTime) (monoNow - inc.clock.mono).coerceAtLeast(0) else 0L
    val remaining = List(Pets.SLOTS) { i -> (inc.remainingMs(i) - elapsed).coerceAtLeast(0) }
    val hatchDue = !inc.waitingForTime && (0 until Pets.SLOTS).any { inc.slot(it) != null && remaining[it] == 0L }
    LaunchedEffect(hatchDue) { if (hatchDue) save.update { Incubators.tick(it) } }

    val incubator: @Composable (Modifier) -> Unit = { m -> IncubatorPanel(p, remaining, m, { n -> save.update { Incubators.setStones(it, n) } },
        onInstant = { slot -> if (Incubators.instantHatch(p, slot) == null) note = tr("Not enough ◇.") else { save.update { Incubators.instantHatch(it, slot) ?: it }; note = "" } },
        onInstantEuros = { slot -> if (Incubators.instantHatchWithEuros(p, slot) == null) note = tr("Not enough €.") else { save.update { Incubators.instantHatchWithEuros(it, slot) ?: it }; note = "" } }) { uid ->
        val r = Incubators.takeBack(p, uid)
        if (r == null) note = tr("Egg inventory is full.") else save.update { Incubators.takeBack(it, uid) ?: it }
    } }
    val eggs: @Composable (Modifier) -> Unit = { m -> EggsPanel(p, selEgg, m, { selEgg = it }, { uid ->
        val r = Incubators.send(p, uid)
        if (r == null) note = tr("All three incubators are busy.")
        else { save.update { Incubators.send(it, uid) ?: it }; selEgg = null; note = "" }
    }, onVault = { uid ->
        if (Incubators.eggToVault(p, uid) == null) note = tr("The DATA VAULT has no room for pets and eggs.")
        else { save.update { Incubators.eggToVault(it, uid) ?: it }; selEgg = null; note = tr("Egg stored in the DATA VAULT.") }
    }, onSell = { uid ->
        var got = 0
        save.update { val (q, n) = Incubators.sellEgg(it, uid, bank = onSold == null); got = n; q }
        if (got > 0) onSold?.invoke(got)
        selEgg = null; note = tr("Sold for € {0}.", "%,d".format(got))
    }) }
    val pets: @Composable (Modifier) -> Unit = { m -> PetsPanel(p, selPet, m, { selPet = it }, { uid -> save.update { Incubators.equip(it, uid) }; onPetChanged() }, { uid ->
        val wasActive = p.activePet == uid
        if (Incubators.petToVault(p, uid) == null) note = tr("The DATA VAULT has no room for pets and eggs.")
        else { save.update { Incubators.petToVault(it, uid) ?: it }; selPet = null; note = tr("Pet stored in the DATA VAULT."); if (wasActive) onPetChanged() }
    }) { uid ->
        val wasActive = p.activePet == uid
        var got = 0
        save.update { val (q, n) = Incubators.sellPet(it, uid, bank = onSold == null); got = n; q }
        if (got > 0) onSold?.invoke(got)
        selPet = null; note = tr("Sold for € {0}.", "%,d".format(got))
        if (wasActive) onPetChanged()
    } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        if (inRun) {
            // Mid-run (owner, 0.51.0): no incubator screen, just your pets and eggs. Equipping swaps
            // the companion in the run at once; an egg sent to a free incubator starts hatching now.
            CompactIncubators(p, remaining)
            Spacer(Modifier.height(8.dp)); pets(Modifier)
            Spacer(Modifier.height(10.dp)); eggs(Modifier)
        } else if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                incubator(Modifier.weight(1f))
                Column(Modifier.weight(1f)) { eggs(Modifier); Spacer(Modifier.height(10.dp)); pets(Modifier) }
            }
        } else {
            incubator(Modifier)
            Spacer(Modifier.height(10.dp)); eggs(Modifier)
            Spacer(Modifier.height(10.dp)); pets(Modifier)
        }
        if (note.isNotEmpty()) Text(note, color = Palette.Red, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(16.dp))
    }
    // A pet just hatched: the reveal.
    val fresh = p.pets.filter { it.fresh }
    fresh.firstOrNull()?.let { f -> key(f.uid) { HatchReveal(f, fresh.size) { save.update { Incubators.seenOne(it, f.uid) } } } }
}

/**
 * Mid-run (owner, 0.52.0: "compact incubators"): the three incubators as small tiles — the egg in
 * each, its countdown and progress — so a hatch can be watched and started without leaving the run.
 */
@Composable
private fun CompactIncubators(p: PlayerProfile, remaining: List<Long>) {
    Card(Modifier) {
        Column {
            Text(tr("INCUBATORS"), color = Palette.TextPrimary, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (i in 0 until Pets.SLOTS) {
                    val s = p.incubator.slot(i)
                    val c = s?.let { Color(it.egg.rarity.color) } ?: Palette.Divider
                    Column(
                        Modifier.weight(1f).background(Palette.Surface, RoundedCornerShape(6.dp)).border(1.dp, c.copy(alpha = 0.7f), RoundedCornerShape(6.dp)).padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                            if (s != null) EggIconView(s.egg, Modifier.fillMaxSize())
                            else Text(tr("EMPTY"), color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
                        }
                        if (s != null) {
                            val total = (s.egg.baseMs * Pets.timeFactor(p.incubator.stones)).toLong().coerceAtLeast(1L)
                            val f = (1f - remaining[i].toFloat() / total).coerceIn(0f, 1f)
                            Text(if (remaining[i] <= 0L) tr("HATCHING") else hatchClock(remaining[i]), color = Palette.TextSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            Box(Modifier.fillMaxWidth().height(4.dp).background(Palette.SurfaceRaised, RoundedCornerShape(2.dp))) {
                                Box(Modifier.fillMaxWidth(f).height(4.dp).background(c, RoundedCornerShape(2.dp)))
                            }
                        } else Text(tr("pick an egg below"), color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun Card(modifier: Modifier, accent: Color = Palette.Divider, content: @Composable () -> Unit) {
    Box(
        modifier.fillMaxWidth()
            .background(Palette.Surface.copy(alpha = 0.92f), RoundedCornerShape(10.dp))
            .border(1.dp, accent, RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) { content() }
}

@Composable
private fun IncubatorPanel(
    p: PlayerProfile, remaining: List<Long>, modifier: Modifier, onStones: (Int) -> Unit,
    onInstant: (Int) -> Unit = {}, onInstantEuros: (Int) -> Unit = {}, onTakeBack: (Long) -> Unit
) {
    val inc = p.incubator
    // The incubator shown below the three (the first busy one until the player taps another).
    var sel by remember { mutableIntStateOf(-1) }
    val shown = if (sel in 0 until Pets.SLOTS) sel else (0 until Pets.SLOTS).firstOrNull { inc.slot(it) != null } ?: 0
    var t by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) { while (true) { delay(50); t += 0.05f } }
    Card(modifier, accent = Color(0xFFFF4A1A)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(tr("INCUBATORS"), color = Color(0xFFFF6A2A), style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
            Text(tr("Three eggs hatch at once · Inferno Stones speed up all three"), color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            // Three incubators (owner, 0.45.0): empty glass, or the egg being hatched in it.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (i in 0 until Pets.SLOTS) {
                    val slot = inc.slot(i)
                    Column(
                        Modifier.weight(1f)
                            .border(if (i == shown) 2.dp else 1.dp, if (i == shown) Color(0xFFFF6A1A) else Palette.Divider, RoundedCornerShape(8.dp))
                            .clickable { sel = i }
                            .padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(Modifier.fillMaxWidth().aspectRatio(1.29f), contentAlignment = Alignment.Center) {
                            PetPicture("incubator_empty_${inc.stones.coerceIn(1, Pets.MAX_STONES)}", Modifier.fillMaxSize(), alpha = if (inc.stones == 0) 0.75f else 1f)
                            if (slot != null) {
                                val f = slot.fraction
                                // The egg trembles more as it gets closer to hatching.
                                val shake = f * 1.6f * kotlin.math.sin(t * (6f + 10f * f) + i)
                                Canvas(Modifier.fillMaxSize()) {
                                    val ew = size.width * 0.26f
                                    val cx = size.width * 0.5f + shake
                                    val bottom = size.height * 0.75f
                                    drawCircle(Color(slot.egg.rarity.color).copy(alpha = 0.16f + 0.24f * f), ew * 0.62f, Offset(cx, bottom - ew * 0.5f))
                                    with(PetArt) { drawEgg(slot.egg.art, cx, bottom, ew * 1.05f) }
                                }
                            }
                        }
                        if (slot == null) {
                            Text(tr("EMPTY"), color = Palette.TextMuted, style = MaterialTheme.typography.labelMedium)
                        } else {
                            Text(if (inc.waitingForTime) tr("VERIFYING…") else hatchClock(remaining[i]), color = Palette.Cyan, style = MaterialTheme.typography.labelLarge)
                            Box(Modifier.fillMaxWidth().padding(top = 2.dp).height(5.dp).background(Palette.SurfaceSunken, RoundedCornerShape(3.dp))) {
                                Box(Modifier.fillMaxWidth(slot.fraction).fillMaxHeight().background(Color(slot.egg.rarity.color), RoundedCornerShape(3.dp)))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            // The selected incubator.
            val slot = inc.slot(shown)
            if (slot == null) {
                Text(tr("INCUBATOR {0} · EMPTY", shown + 1), color = Palette.TextSecondary, style = MaterialTheme.typography.titleSmall)
                Text(tr("Send an egg from your eggs below."), color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
            } else {
                val egg = slot.egg
                Text(egg.name, color = Color(egg.rarity.color), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                Text(tr("{0} · INCUBATOR {1}", egg.rarity.label, shown + 1), color = Color(egg.rarity.color), style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(4.dp))
                if (inc.waitingForTime) {
                    Text(tr("VERIFYING TIME…"), color = Palette.Gold, style = MaterialTheme.typography.titleLarge)
                    Text(tr("After a restart the incubators check the real time online before they count on. Connect to the internet."), color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                } else {
                    Text(tr("Hatches in"), color = Palette.TextSecondary, style = MaterialTheme.typography.labelMedium)
                    Text(hatchClock(remaining[shown]), color = Palette.Cyan, style = MaterialTheme.typography.headlineMedium)
                }
                Box(Modifier.fillMaxWidth().height(8.dp).background(Palette.SurfaceSunken, RoundedCornerShape(4.dp))) {
                    Box(Modifier.fillMaxWidth(slot.fraction).fillMaxHeight().background(Color(0xFFFF6A1A), RoundedCornerShape(4.dp)))
                }
                // INSTANT HATCH (owner, 0.42.0 ◇ / 0.43.0 €): the price falls as the egg hatches. Two taps.
                val price = Pets.instantHatchPrice(egg.rarity, if (inc.waitingForTime) inc.remainingMs(shown) else remaining[shown])
                if (price > 0) {
                    Spacer(Modifier.height(8.dp))
                    var confirm by remember(egg.uid) { mutableStateOf(false) }
                    val afford = p.diamonds >= price
                    CyberButton(
                        if (confirm) tr("CONFIRM · ◇{0}", price) else tr("INSTANT HATCH · ◇{0}", price), Modifier.fillMaxWidth(),
                        accent = Palette.Gold, primary = confirm, enabled = afford,
                        subtitle = if (afford) tr("You have ◇{0}", p.diamonds) else tr("Not enough ◇ (you have {0})", p.diamonds)
                    ) { if (confirm) { confirm = false; onInstant(shown) } else confirm = true }
                    Spacer(Modifier.height(6.dp))
                    val euros = Pets.instantHatchEuros(price, Incubators.accountOpLevel(p))
                    var confirmE by remember(egg.uid) { mutableStateOf(false) }
                    val affordE = p.euros >= euros
                    CyberButton(
                        if (confirmE) tr("CONFIRM · €{0}", "%,d".format(euros)) else tr("HATCH WITH € · €{0}", "%,d".format(euros)), Modifier.fillMaxWidth(),
                        accent = Palette.Green, primary = confirmE, enabled = affordE,
                        subtitle = if (affordE) tr("You have €{0}", "%,d".format(p.euros)) else tr("Not enough € (you have €{0})", "%,d".format(p.euros))
                    ) { if (confirmE) { confirmE = false; onInstantEuros(shown) } else confirmE = true }
                }
                Spacer(Modifier.height(6.dp))
                var confirmT by remember(egg.uid) { mutableStateOf(false) }
                CyberButton(if (confirmT) tr("CONFIRM: PROGRESS IS LOST") else tr("TAKE OUT"), Modifier.fillMaxWidth(), accent = Palette.TextSecondary) {
                    if (confirmT) { confirmT = false; onTakeBack(egg.uid) } else confirmT = true
                }
            }
            Spacer(Modifier.height(10.dp))
            // Inferno Stones: six sockets, −15% each, −90% with all six.
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                PetPicture("stone_icon", Modifier.size(28.dp))
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) {
                    Text(tr("INFERNO STONES"), color = Color(0xFFFF6A2A), style = MaterialTheme.typography.labelLarge)
                    Text(tr("{0} socketed = −{1}% hatch time · {2} owned", inc.stones, inc.stones * 15, p.infernoStones), color = Palette.TextSecondary, style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (i in 0 until Pets.MAX_STONES) {
                    val on = i < inc.stones
                    val canAdd = i == inc.stones && inc.stones < p.infernoStones
                    Box(
                        Modifier.weight(1f).aspectRatio(1f)
                            .border(1.dp, if (on) Color(0xFFFF6A1A) else Palette.Divider, RoundedCornerShape(6.dp))
                            .clickable(enabled = on || canAdd) { onStones(if (on) i else i + 1) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (on) PetPicture("stone_slot", Modifier.fillMaxSize())
                        else Text("+", color = if (canAdd) Color(0xFFFF6A1A) else Palette.TextMuted)
                    }
                }
            }
            Text(
                if (p.infernoStones == 0) tr("Inferno Stones are ultra rare (ABYSSAL tier): INFERNO bosses can drop them.")
                else tr("Tap + to socket a stone, tap a stone to remove it."),
                color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun EggsPanel(
    p: PlayerProfile, sel: Long?, modifier: Modifier, onSelect: (Long?) -> Unit, onSend: (Long) -> Unit,
    onVault: (Long) -> Unit = {}, onSell: (Long) -> Unit = {}
) {
    val all = remember(p.eggs) { p.eggs.sortedWith(compareByDescending<PetEgg> { it.rarity.ordinal }.thenBy { it.uid }) }
    // Up to 1,000 eggs (0.52.0): show them a page at a time.
    var shown by remember { mutableStateOf(120) }
    val eggs = all.take(shown)
    Card(modifier) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("EGGS"), color = Palette.TextPrimary, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text("${p.eggs.size}/${Pets.MAX_EGGS}", color = Palette.TextSecondary, style = MaterialTheme.typography.labelMedium)
            }
            if (eggs.isEmpty()) Text(tr("Bosses (often) and elites (rarely) drop eggs. The STORE sells them for ◇."), color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall)
            Grid(eggs.size, 6) { i ->
                val e = eggs[i]
                Box(
                    Modifier.fillMaxWidth().aspectRatio(1f)
                        .border(if (e.uid == sel) 2.dp else 1.dp, if (e.uid == sel) Color.White else Color(e.rarity.color).copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .clickable { onSelect(if (e.uid == sel) null else e.uid) }
                ) { EggIconView(e, Modifier.fillMaxSize().padding(3.dp)) }
            }
            if (all.size > shown) CyberButton(tr("SHOW MORE ({0})", all.size - shown), Modifier.fillMaxWidth().padding(top = 6.dp), accent = Palette.Divider) { shown += 120 }
            val e = all.firstOrNull { it.uid == sel }
            if (e != null) {
                Spacer(Modifier.height(8.dp))
                Text(e.name, color = Color(e.rarity.color), style = MaterialTheme.typography.titleSmall)
                Text(tr("{0} · Hatches in {1}", e.rarity.label, hatchClock((e.baseMs * Pets.timeFactor(p.incubator.stones)).toLong())) +
                    if (p.incubator.stones > 0) tr(" (with {0} stones)", p.incubator.stones) else "", color = Palette.TextSecondary, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(6.dp))
                val full = p.incubator.full
                val free = p.incubator.slots.indexOfFirst { it == null }
                CyberButton(tr("SEND TO INCUBATOR"), Modifier.fillMaxWidth(), accent = Color(0xFFFF6A1A), primary = true, enabled = !full,
                    subtitle = if (full) tr("ALL THREE INCUBATORS BUSY") else tr("STARTS HATCHING NOW IN INCUBATOR {0}", free + 1)) { onSend(e.uid) }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CyberButton(tr("TO VAULT"), Modifier.weight(1f), accent = Palette.Gold) { onVault(e.uid) }
                    var confirm by remember(e.uid) { mutableStateOf(false) }
                    val worth = Incubators.eggValue(p, e.uid)
                    CyberButton(if (confirm) tr("CONFIRM · € {0}", "%,d".format(worth)) else tr("SELL · € {0}", "%,d".format(worth)), Modifier.weight(1f), accent = Palette.Euro) {
                        if (confirm) onSell(e.uid) else confirm = true
                    }
                }
            }
        }
    }
}

@Composable
private fun PetsPanel(p: PlayerProfile, sel: Long?, modifier: Modifier, onSelect: (Long?) -> Unit, onEquip: (Long) -> Unit, onVault: (Long) -> Unit, onSell: (Long) -> Unit) {
    val pets = remember(p.pets) { p.pets.sortedWith(compareByDescending<Pet> { it.rarity.ordinal }.thenByDescending { it.uid }) }
    Card(modifier, accent = Palette.Cyan.copy(alpha = 0.6f)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("PETS"), color = Palette.TextPrimary, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text("${p.pets.size}", color = Palette.TextSecondary, style = MaterialTheme.typography.labelMedium)
            }
            val active = p.pets.firstOrNull { it.uid == p.activePet }
            Text(if (active != null) tr("Following you: {0}", active.name) else tr("No pet equipped: pick one to follow you and fight."), color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(6.dp))
            Grid(pets.size, 5) { i ->
                val pet = pets[i]
                val on = pet.uid == p.activePet
                Box(
                    Modifier.fillMaxWidth().aspectRatio(1f)
                        .background(if (on) Palette.Cyan.copy(alpha = 0.12f) else Color.Transparent, RoundedCornerShape(6.dp))
                        .border(if (pet.uid == sel || on) 2.dp else 1.dp, if (pet.uid == sel) Color.White else Color(pet.rarity.color).copy(alpha = if (on) 1f else 0.5f), RoundedCornerShape(6.dp))
                        .clickable { onSelect(if (pet.uid == sel) null else pet.uid) }
                ) { PetIconView(pet.art, pet.rarity, Modifier.fillMaxSize().padding(2.dp)) }
            }
            val pet = pets.firstOrNull { it.uid == sel }
            if (pet != null) {
                Spacer(Modifier.height(8.dp))
                Text(pet.name, color = Color(pet.rarity.color), style = MaterialTheme.typography.titleSmall)
                Text(tr("{0} · shots deal {1}% of your payload every {2}s", pet.rarity.label, (Pets.damageMul(pet.rarity) * 100).toInt(), "%.2f".format(Pets.fireInterval(pet.rarity))),
                    color = Palette.TextSecondary, style = MaterialTheme.typography.labelMedium)
                PetAbilities(pet)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val on = pet.uid == p.activePet
                    CyberButton(if (on) tr("UNEQUIP") else tr("EQUIP"), Modifier.weight(1f), accent = Palette.Cyan, primary = !on) { onEquip(pet.uid) }
                    CyberButton(tr("TO VAULT"), Modifier.weight(1f), accent = Palette.Gold) { onVault(pet.uid) }
                }
                // SELL replaces RELEASE (owner, 0.52.0): 10× a same-rarity weapon's value, confirmed.
                var confirm by remember(pet.uid) { mutableStateOf(false) }
                val worth = Incubators.petValue(p, pet.uid)
                Spacer(Modifier.height(6.dp))
                CyberButton(if (confirm) tr("CONFIRM SELL · € {0}", "%,d".format(worth)) else tr("SELL · € {0}", "%,d".format(worth)), Modifier.fillMaxWidth(), accent = Palette.Euro) {
                    if (confirm) onSell(pet.uid) else confirm = true
                }
            }
        }
    }
}

/** The pet's abilities (owner, 0.39.0): one for most, up to [Pets.MAX_ABILITIES] for the rarest. */
@Composable
private fun PetAbilities(pet: Pet, center: Boolean = false) {
    Spacer(Modifier.height(4.dp))
    for (a in pet.abilities) {
        Text("◆ ${a.label} · ${"%.0f".format(Pets.abilityCooldown(a, pet.rarity))}s", color = Color(pet.rarity.color),
            style = MaterialTheme.typography.labelMedium, textAlign = if (center) TextAlign.Center else TextAlign.Start)
        Text(a.text, color = Palette.TextSecondary, style = MaterialTheme.typography.labelSmall,
            textAlign = if (center) TextAlign.Center else TextAlign.Start)
    }
}

/** A simple non-lazy grid (the panels sit in a scrolling column). */
@Composable
private fun Grid(count: Int, cols: Int, cell: @Composable (Int) -> Unit) {
    for (row in 0 until (count + cols - 1) / cols) {
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (c in 0 until cols) {
                val i = row * cols + c
                Box(Modifier.weight(1f)) { if (i < count) cell(i) }
            }
        }
    }
}

/**
 * The hatch (owner, 0.45.0: "an animation showing your new pet fully animated and its stats,
 * rarity etc"): the egg shakes and cracks, bursts in a flash with shell pieces flying, and the
 * pet pops out in front of rays in its rarity colour; then its card fades in. Tap to skip ahead.
 */
@Composable
private fun HatchReveal(pet: Pet, left: Int, onContinue: () -> Unit) {
    var t by remember(pet.uid) { mutableStateOf(0f) }
    var skip by remember(pet.uid) { mutableStateOf(0f) }
    LaunchedEffect(pet.uid) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { t = (it - start) / 1e9f + skip }
    }
    val crack = 1.7f      // the egg bursts
    val card = 2.4f       // the stats fade in
    // The hatch sound as the egg bursts (also when the player taps ahead past it).
    val burst = t >= crack
    LaunchedEffect(pet.uid, burst) { if (burst) com.cyberoperative.game.engine.UiSound.play(com.cyberoperative.game.engine.GameSound.PET_HATCH) }
    // GLITCHED ENCRYPTED shifts cyan ↔ red (0.49.0); every other rarity keeps its colour.
    val c = com.cyberoperative.game.ui.common.rarityColor(pet.rarity)
    // Rarer eggs hatch bigger (owner, 0.45.1): the glow is the base size for COMMON and grows
    // evenly to twice that for the top pet rarity (GLITCHED ENCRYPTED since 0.49.0).
    val scale = 1f + pet.rarity.ordinal / com.cyberoperative.game.data.Pets.TOP.ordinal.toFloat()
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.86f))
            .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {
                if (t < card) skip += card - t
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            // No scroll here: a scroll container clips, and the ENCRYPTED glow reaches past the column.
            Modifier.widthIn(max = 380.dp).fillMaxWidth(0.9f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(if (t < crack) tr("SOMETHING IS HATCHING…") else tr("NEW PET HATCHED!"), color = if (t < crack) Palette.TextSecondary else Palette.Gold,
                style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            if (left > 1) Text(tr("{0} new pets", left), color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(6.dp))
            Canvas(Modifier.size(260.dp)) {
                val w = size.width; val h = size.height
                val cx = w / 2f; val cy = h * 0.56f
                val after = (t - crack).coerceAtLeast(0f)
                // Rays in the rarity colour, turning slowly once it has hatched.
                if (t >= crack) {
                    // Rays and halo in the rarity colour: the base size for COMMON, twice it for ENCRYPTED.
                    val a = (after / 0.6f).coerceIn(0f, 1f)
                    val len = h * 0.5f * scale
                    val half = w * 0.07f * scale
                    rotate(after * 18f, Offset(cx, cy)) {
                        for (k in 0 until 12) {
                            rotate(k * 30f, Offset(cx, cy)) {
                                val path = androidx.compose.ui.graphics.Path().apply {
                                    moveTo(cx, cy); lineTo(cx - half, cy - len); lineTo(cx + half, cy - len); close()
                                }
                                drawPath(path, Brush.verticalGradient(listOf(Color.Transparent, c.copy(alpha = 0.32f * a)), startY = cy - len, endY = cy))
                            }
                        }
                    }
                    val halo = w * 0.42f * scale
                    drawCircle(com.cyberoperative.game.ui.common.SafeBrush.radialGradient(listOf(c.copy(alpha = 0.45f * a), Color.Transparent), Offset(cx, cy), halo), halo, Offset(cx, cy))
                }
                if (t < crack) {
                    // The egg: shaking harder, glowing cracks spreading over it.
                    val k = (t / crack).coerceIn(0f, 1f)
                    val shake = k * k * w * 0.03f * kotlin.math.sin(t * (14f + 30f * k))
                    val ew = w * 0.42f
                    val ex = cx + shake
                    val bottom = cy + ew * 0.5f
                    val eg = ew * 0.9f * (1f + (scale - 1f) * k)
                    drawCircle(com.cyberoperative.game.ui.common.SafeBrush.radialGradient(listOf(c.copy(alpha = 0.15f + 0.4f * k), Color.Transparent), Offset(ex, cy), eg), eg, Offset(ex, cy))
                    with(PetArt) { drawEgg(pet.hatchedFrom, ex, bottom, ew) }
                    val lines = (k * 6f).toInt()
                    for (i in 0 until lines) {
                        val sx = ex + (((i * 37) % 9) - 4) / 4f * ew * 0.18f
                        val sy = cy - ew * 0.3f + i * ew * 0.08f
                        var px = sx; var py = sy
                        for (j in 0 until 3) {
                            val nx = px + ((((i + j) * 53) % 7) - 3) / 3f * ew * 0.09f
                            val ny = py + ew * 0.08f
                            drawLine(c.copy(alpha = 0.5f), Offset(px, py), Offset(nx, ny), 5f)
                            drawLine(Color.White, Offset(px, py), Offset(nx, ny), 2f)
                            px = nx; py = ny
                        }
                    }
                } else {
                    // Shell pieces flying out and falling.
                    for (i in 0 until 8) {
                        val ang = i * 0.785f + 0.3f
                        val v = w * (0.55f + (i % 3) * 0.15f)
                        val px = cx + kotlin.math.cos(ang) * v * after
                        val py = cy + kotlin.math.sin(ang) * v * after * 0.7f + 0.5f * h * 2.2f * after * after
                        val alpha = (1f - after / 1.2f).coerceIn(0f, 1f)
                        if (alpha > 0f) rotate(after * 400f * (if (i % 2 == 0) 1f else -1f), Offset(px, py)) {
                            drawRect(c.copy(alpha = alpha), Offset(px - 6f, py - 4f), androidx.compose.ui.geometry.Size(12f, 8f))
                        }
                    }
                    // The pet pops out with a bounce, then floats.
                    val p = (after / 0.45f).coerceIn(0f, 1f)
                    val back = 1.70158f
                    val ease = 1f + (back + 1f) * (p - 1f).let { it * it * it } + back * (p - 1f).let { it * it }
                    val size = w * 0.62f * ease
                    val bob = if (p >= 1f) kotlin.math.sin(after * 2.4f) * h * 0.015f else 0f
                    if (size > 1f) with(PetArt) { drawPet(pet.art, cx, cy + w * 0.3f + bob, size, time = t) }
                    // Rising sparkles.
                    for (i in 0 until 10) {
                        val ph = ((after * 0.45f + i * 0.1f) % 1f)
                        val sx = cx + kotlin.math.sin(i * 2.3f + after) * w * 0.36f * scale
                        val sy = cy + h * 0.25f - ph * h * 0.6f * scale
                        drawCircle(c.copy(alpha = (1f - ph) * 0.9f), (3f + (i % 3)) * scale, Offset(sx, sy))
                    }
                    // The flash.
                    val f = (1f - after / 0.35f).coerceIn(0f, 1f)
                    if (f > 0f) drawCircle(Color.White.copy(alpha = f * 0.9f), w * (0.2f + 0.6f * (1f - f)) * scale, Offset(cx, cy))
                }
            }
            // The card: name, rarity, stats, abilities.
            val a = ((t - card) / 0.5f).coerceIn(0f, 1f)
            Column(
                Modifier.fillMaxWidth().graphicsLayer { alpha = a; translationY = (1f - a) * 40f }
                    .background(Palette.Surface, RoundedCornerShape(12.dp))
                    .border(2.dp, c, RoundedCornerShape(12.dp))
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(pet.name, color = c, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                Spacer(Modifier.height(4.dp))
                Box(Modifier.rarityCard(pet.rarity, corner = 4.dp).border(1.dp, c, RoundedCornerShape(4.dp)).padding(horizontal = 10.dp, vertical = 3.dp)) {
                    Text(pet.rarity.label, color = c, style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatBox(tr("DAMAGE"), "${(Pets.damageMul(pet.rarity) * 100).toInt()}%", tr("of your payload"))
                    StatBox(tr("FIRE RATE"), "${"%.2f".format(Pets.fireInterval(pet.rarity))}s", tr("between shots"))
                    StatBox(tr("ABILITIES"), "${pet.abilities.size}", tr("of {0} max", Pets.MAX_ABILITIES))
                }
                PetAbilities(pet, center = true)
                Spacer(Modifier.height(6.dp))
                Text(tr("Equip it in PETS and it follows you into runs, firing at threats."), color = Palette.TextSecondary, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                CyberButton(if (left > 1) tr("NEXT PET ({0} more)", left - 1) else tr("CONTINUE"), Modifier.fillMaxWidth(), accent = Palette.Cyan, primary = true, enabled = a >= 1f) { onContinue() }
            }
        }
    }
}

@Composable
private fun StatBox(label: String, value: String, sub: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
        Text(value, color = Palette.TextPrimary, style = MaterialTheme.typography.titleMedium)
        Text(sub, color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
    }
}

/**
 * DATA VAULT sections (owner, 0.52.0: "eggs and pets can be stored in data vault"): GEAR (the
 * gear vault, [gear]) or the stored PETS / EGGS, with TAKE OUT and SELL.
 */
@Composable
fun VaultSections(save: SaveRepository, p: PlayerProfile, wide: Boolean, gear: @Composable () -> Unit) {
    var section by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
            for ((i, label) in listOf(tr("GEAR"), tr("PETS ({0})", p.vaultPets.size), tr("EGGS ({0})", p.vaultEggs.size)).withIndex()) {
                val on = i == section
                Text(label, color = if (on) Palette.Gold else Palette.TextSecondary, style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .background(if (on) Palette.Gold.copy(alpha = 0.14f) else Palette.Surface.copy(alpha = 0.8f), RoundedCornerShape(6.dp))
                        .border(if (on) 2.dp else 1.dp, if (on) Palette.Gold else Palette.Divider, RoundedCornerShape(6.dp))
                        .clickable { section = i }
                        .padding(horizontal = 12.dp, vertical = 6.dp))
            }
        }
        Box(Modifier.weight(1f)) {
            if (section == 0) gear() else PetVault(save, p, eggs = section == 2)
        }
    }
}

@Composable
private fun PetVault(save: SaveRepository, p: PlayerProfile, eggs: Boolean) {
    var sel by remember(eggs) { mutableStateOf<Long?>(null) }
    var note by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Card(Modifier) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (eggs) tr("STORED EGGS") else tr("STORED PETS"), color = Palette.TextPrimary, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text("${p.vaultPets.size + p.vaultEggs.size} / ${"%,d".format(Pets.VAULT_SIZE)}", color = Palette.TextSecondary, style = MaterialTheme.typography.labelMedium)
                }
                Text(tr("Pets and eggs share the vault's {0} pet slots. Store them from CHARACTER › PETS (TO VAULT).", "%,d".format(Pets.VAULT_SIZE)),
                    color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(6.dp))
                if (eggs) {
                    val list = remember(p.vaultEggs) { p.vaultEggs.sortedByDescending { it.rarity.ordinal } }
                    if (list.isEmpty()) Text(tr("No eggs stored."), color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall)
                    Grid(list.size, 6) { i ->
                        val e = list[i]
                        Box(Modifier.fillMaxWidth().aspectRatio(1f)
                            .border(if (e.uid == sel) 2.dp else 1.dp, if (e.uid == sel) Color.White else Color(e.rarity.color).copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .clickable { sel = if (e.uid == sel) null else e.uid }) { EggIconView(e, Modifier.fillMaxSize().padding(3.dp)) }
                    }
                    list.firstOrNull { it.uid == sel }?.let { e ->
                        Spacer(Modifier.height(8.dp))
                        Text(e.name, color = Color(e.rarity.color), style = MaterialTheme.typography.titleSmall)
                        Text(e.rarity.label, color = Palette.TextSecondary, style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(6.dp))
                        CyberButton(tr("TAKE OUT"), Modifier.fillMaxWidth(), accent = Palette.Cyan, primary = true) {
                            if (Incubators.eggFromVault(p, e.uid) == null) note = tr("Your eggs are full ({0}).", Pets.MAX_EGGS)
                            else { save.update { Incubators.eggFromVault(it, e.uid) ?: it }; sel = null; note = "" }
                        }
                    }
                } else {
                    val list = remember(p.vaultPets) { p.vaultPets.sortedByDescending { it.rarity.ordinal } }
                    if (list.isEmpty()) Text(tr("No pets stored."), color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall)
                    Grid(list.size, 5) { i ->
                        val pet = list[i]
                        Box(Modifier.fillMaxWidth().aspectRatio(1f)
                            .then(if (pet.uid == sel) Modifier.border(2.dp, Color.White, RoundedCornerShape(8.dp)) else Modifier)
                            .clickable { sel = if (pet.uid == sel) null else pet.uid }) { PetIconView(pet.art, pet.rarity, Modifier.fillMaxSize()) }
                    }
                    list.firstOrNull { it.uid == sel }?.let { pet ->
                        Spacer(Modifier.height(8.dp))
                        Text(pet.name, color = Color(pet.rarity.color), style = MaterialTheme.typography.titleSmall)
                        Text(pet.rarity.label, color = Palette.TextSecondary, style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(6.dp))
                        CyberButton(tr("TAKE OUT"), Modifier.fillMaxWidth(), accent = Palette.Cyan, primary = true) {
                            save.update { Incubators.petFromVault(it, pet.uid) ?: it }; sel = null
                        }
                    }
                }
                if (note.isNotEmpty()) Text(note, color = Palette.Red, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}
