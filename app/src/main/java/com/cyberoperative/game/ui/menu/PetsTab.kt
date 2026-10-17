package com.cyberoperative.game.ui.menu

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
fun PetsTab(save: SaveRepository, p: PlayerProfile, wide: Boolean) {
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
    LaunchedEffect(p.incubator.current?.uid, p.incubator.stones, p.incubator.waitingForTime) {
        com.cyberoperative.game.meta.PetReminders.update(p)
        if (p.incubator.current != null) host?.requestNotifications()
    }
    val inc = p.incubator
    val sameBoot = inc.clock.boot == PlatformClock.bootId()
    val elapsed = if (sameBoot && !inc.waitingForTime) (monoNow - inc.clock.mono).coerceAtLeast(0) else 0L
    val remaining = (inc.remainingMs - elapsed).coerceAtLeast(0)
    val hatchDue = inc.current != null && remaining == 0L && !inc.waitingForTime
    LaunchedEffect(hatchDue) { if (hatchDue) save.update { Incubators.tick(it) } }

    val incubator: @Composable (Modifier) -> Unit = { m -> IncubatorPanel(p, remaining, m, { n -> save.update { Incubators.setStones(it, n) } },
        onInstant = { if (Incubators.instantHatch(p) == null) note = "Not enough ◇." else { save.update { Incubators.instantHatch(it) ?: it }; note = "" } },
        onInstantEuros = { if (Incubators.instantHatchWithEuros(p) == null) note = "Not enough €." else { save.update { Incubators.instantHatchWithEuros(it) ?: it }; note = "" } }) { uid ->
        val r = Incubators.takeBack(p, uid)
        if (r == null) note = "Egg inventory is full." else save.update { Incubators.takeBack(it, uid) ?: it }
    } }
    val eggs: @Composable (Modifier) -> Unit = { m -> EggsPanel(p, selEgg, m, { selEgg = it }) { uid ->
        val r = Incubators.send(p, uid)
        if (r == null) note = "The incubator and its queue are full."
        else { save.update { Incubators.send(it, uid) ?: it }; selEgg = null; note = "" }
    } }
    val pets: @Composable (Modifier) -> Unit = { m -> PetsPanel(p, selPet, m, { selPet = it }, { uid -> save.update { Incubators.equip(it, uid) } }) { uid ->
        save.update { Incubators.release(it, uid) }; selPet = null
    } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        if (wide) {
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
    val fresh = p.pets.firstOrNull { it.fresh }
    if (fresh != null) HatchReveal(fresh) { save.update { Incubators.seen(it) } }
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
private fun IncubatorPanel(p: PlayerProfile, remaining: Long, modifier: Modifier, onStones: (Int) -> Unit, onInstant: () -> Unit = {}, onInstantEuros: () -> Unit = {}, onTakeBack: (Long) -> Unit) {
    val inc = p.incubator
    val egg = inc.current
    Card(modifier, accent = Color(0xFFFF4A1A)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("INCUBATOR", color = Color(0xFFFF6A2A), style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
            Text("Equip Inferno Stones to reduce hatching time", color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            // The owner's incubator picture (it glows hotter with more stones) with the egg inside.
            Box(Modifier.fillMaxWidth().aspectRatio(1.29f), contentAlignment = Alignment.Center) {
                PetPicture("incubator_${inc.stones.coerceIn(1, Pets.MAX_STONES)}", Modifier.fillMaxSize(), alpha = if (inc.stones == 0) 0.75f else 1f)
                if (egg != null) {
                    var t by remember { mutableStateOf(0f) }
                    LaunchedEffect(egg.uid) { while (true) { delay(50); t += 0.05f } }
                    // The egg trembles more as it gets closer to hatching.
                    val shake = inc.fraction * 2.5f * kotlin.math.sin(t * (6f + 10f * inc.fraction))
                    Canvas(Modifier.fillMaxWidth(0.34f).aspectRatio(0.9f)) {
                        drawCircle(Color(egg.rarity.color).copy(alpha = 0.18f + 0.25f * inc.fraction), size.minDimension * 0.55f, center)
                        with(PetArt) { drawEgg(egg.art, size.width / 2f + shake, size.height * 0.98f, size.minDimension) }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            if (egg == null) {
                Text("EMPTY", color = Palette.TextSecondary, style = MaterialTheme.typography.titleMedium)
                Text("Send an egg from your eggs below.", color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
            } else {
                Text(egg.name, color = Color(egg.rarity.color), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                Text(egg.rarity.label, color = Color(egg.rarity.color), style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(4.dp))
                if (inc.waitingForTime) {
                    Text("VERIFYING TIME…", color = Palette.Gold, style = MaterialTheme.typography.titleLarge)
                    Text("After a restart the incubator checks the real time online before it counts on. Connect to the internet.", color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                } else {
                    Text("Hatches in", color = Palette.TextSecondary, style = MaterialTheme.typography.labelMedium)
                    Text(hatchClock(remaining), color = Palette.Cyan, style = MaterialTheme.typography.headlineMedium)
                }
                Box(Modifier.fillMaxWidth().height(8.dp).background(Palette.SurfaceSunken, RoundedCornerShape(4.dp))) {
                    Box(Modifier.fillMaxWidth(inc.fraction).fillMaxHeight().background(Color(0xFFFF6A1A), RoundedCornerShape(4.dp)))
                }
                // INSTANT HATCH (owner, 0.42.0): ◇ only, the price falls as the egg hatches. Two taps.
                val price = Pets.instantHatchPrice(egg.rarity, if (inc.waitingForTime) inc.remainingMs else remaining)
                if (price > 0) {
                    Spacer(Modifier.height(8.dp))
                    var confirm by remember(egg.uid) { mutableStateOf(false) }
                    val afford = p.diamonds >= price
                    CyberButton(
                        if (confirm) "CONFIRM · ◇$price" else "INSTANT HATCH · ◇$price", Modifier.fillMaxWidth(),
                        accent = Palette.Gold, primary = confirm, enabled = afford,
                        subtitle = if (afford) "You have ◇${p.diamonds}" else "Not enough ◇ (you have ${p.diamonds})"
                    ) { if (confirm) { confirm = false; onInstant() } else confirm = true }
                    // Or € (owner, 0.43.0): highest operator level on the account × ◇ price × 100.
                    Spacer(Modifier.height(6.dp))
                    val euros = Pets.instantHatchEuros(price, Incubators.accountOpLevel(p))
                    var confirmE by remember(egg.uid) { mutableStateOf(false) }
                    val affordE = p.euros >= euros
                    CyberButton(
                        if (confirmE) "CONFIRM · €${"%,d".format(euros)}" else "HATCH WITH € · €${"%,d".format(euros)}", Modifier.fillMaxWidth(),
                        accent = Palette.Green, primary = confirmE, enabled = affordE,
                        subtitle = if (affordE) "You have €${"%,d".format(p.euros)}" else "Not enough € (you have €${"%,d".format(p.euros)})"
                    ) { if (confirmE) { confirmE = false; onInstantEuros() } else confirmE = true }
                }
            }
            Spacer(Modifier.height(8.dp))
            // Queue.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("QUEUE", color = Palette.TextSecondary, style = MaterialTheme.typography.labelMedium)
                for (i in 0 until Pets.QUEUE) {
                    val q = inc.queue.getOrNull(i)
                    Box(
                        Modifier.weight(1f).aspectRatio(1f).border(1.dp, Palette.Divider, RoundedCornerShape(6.dp))
                            .clickable(enabled = q != null) { q?.let { onTakeBack(it.uid) } },
                        contentAlignment = Alignment.Center
                    ) { if (q != null) EggIconView(q, Modifier.fillMaxSize().padding(3.dp)) else Text("+", color = Palette.TextMuted) }
                }
            }
            if (inc.queue.isNotEmpty() || egg != null) Text("Tap a queued egg to take it back.", color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            // Inferno Stones: six sockets, −15% each, −90% with all six.
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                PetPicture("stone_icon", Modifier.size(28.dp))
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) {
                    Text("INFERNO STONES", color = Color(0xFFFF6A2A), style = MaterialTheme.typography.labelLarge)
                    Text("${inc.stones} socketed = −${inc.stones * 15}% hatch time · ${p.infernoStones} owned", color = Palette.TextSecondary, style = MaterialTheme.typography.labelSmall)
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
                if (p.infernoStones == 0) "Inferno Stones are ultra rare (ABYSSAL tier): INFERNO bosses can drop them."
                else "Tap + to socket a stone, tap a stone to remove it.",
                color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun EggsPanel(p: PlayerProfile, sel: Long?, modifier: Modifier, onSelect: (Long?) -> Unit, onSend: (Long) -> Unit) {
    val eggs = remember(p.eggs) { p.eggs.sortedWith(compareByDescending<PetEgg> { it.rarity.ordinal }.thenBy { it.uid }) }
    Card(modifier) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("EGGS", color = Palette.TextPrimary, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text("${p.eggs.size}/${Pets.MAX_EGGS}", color = Palette.TextSecondary, style = MaterialTheme.typography.labelMedium)
            }
            if (eggs.isEmpty()) Text("Bosses (often) and elites (rarely) drop eggs. The STORE sells them for ◇.", color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall)
            Grid(eggs.size, 6) { i ->
                val e = eggs[i]
                Box(
                    Modifier.fillMaxWidth().aspectRatio(1f)
                        .border(if (e.uid == sel) 2.dp else 1.dp, if (e.uid == sel) Color.White else Color(e.rarity.color).copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .clickable { onSelect(if (e.uid == sel) null else e.uid) }
                ) { EggIconView(e, Modifier.fillMaxSize().padding(3.dp)) }
            }
            val e = eggs.firstOrNull { it.uid == sel }
            if (e != null) {
                Spacer(Modifier.height(8.dp))
                Text(e.name, color = Color(e.rarity.color), style = MaterialTheme.typography.titleSmall)
                Text("${e.rarity.label} · Hatches in ${hatchClock((e.baseMs * Pets.timeFactor(p.incubator.stones)).toLong())}" +
                    if (p.incubator.stones > 0) " (with ${p.incubator.stones} stones)" else "", color = Palette.TextSecondary, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(6.dp))
                val full = p.incubator.current != null && p.incubator.queue.size >= Pets.QUEUE
                CyberButton("SEND TO INCUBATOR", Modifier.fillMaxWidth(), accent = Color(0xFFFF6A1A), primary = true, enabled = !full,
                    subtitle = if (full) "INCUBATOR AND QUEUE FULL" else if (p.incubator.current == null) "STARTS HATCHING NOW" else "JOINS THE QUEUE") { onSend(e.uid) }
            }
        }
    }
}

@Composable
private fun PetsPanel(p: PlayerProfile, sel: Long?, modifier: Modifier, onSelect: (Long?) -> Unit, onEquip: (Long) -> Unit, onRelease: (Long) -> Unit) {
    val pets = remember(p.pets) { p.pets.sortedWith(compareByDescending<Pet> { it.rarity.ordinal }.thenByDescending { it.uid }) }
    Card(modifier, accent = Palette.Cyan.copy(alpha = 0.6f)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("PETS", color = Palette.TextPrimary, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text("${p.pets.size}", color = Palette.TextSecondary, style = MaterialTheme.typography.labelMedium)
            }
            val active = p.pets.firstOrNull { it.uid == p.activePet }
            Text(if (active != null) "Following you: ${active.name}" else "No pet equipped: pick one to follow you and fight.", color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
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
                Text("${pet.rarity.label} · shots deal ${(Pets.damageMul(pet.rarity) * 100).toInt()}% of your payload every ${"%.2f".format(Pets.fireInterval(pet.rarity))}s",
                    color = Palette.TextSecondary, style = MaterialTheme.typography.labelMedium)
                PetAbilities(pet)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val on = pet.uid == p.activePet
                    CyberButton(if (on) "UNEQUIP" else "EQUIP", Modifier.weight(1f), accent = Palette.Cyan, primary = !on) { onEquip(pet.uid) }
                    var confirm by remember(pet.uid) { mutableStateOf(false) }
                    CyberButton(if (confirm) "CONFIRM RELEASE" else "RELEASE", Modifier.weight(1f), accent = Palette.Red) {
                        if (confirm) onRelease(pet.uid) else confirm = true
                    }
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

@Composable
private fun HatchReveal(pet: Pet, onContinue: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.78f)).clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 360.dp).fillMaxWidth(0.86f)
                .background(Palette.Surface, RoundedCornerShape(12.dp))
                .border(2.dp, Color(pet.rarity.color), RoundedCornerShape(12.dp))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("NEW PET HATCHED!", color = Palette.Gold, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            PetIconView(pet.art, pet.rarity, Modifier.size(160.dp))
            Spacer(Modifier.height(8.dp))
            Text(pet.name, color = Color(pet.rarity.color), style = MaterialTheme.typography.titleMedium)
            Text(pet.rarity.label, color = Color(pet.rarity.color), style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.border(1.dp, Color(pet.rarity.color), RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 2.dp))
            PetAbilities(pet, center = true)
            Spacer(Modifier.height(6.dp))
            Text("Equip it in PETS and it follows you into runs, firing at threats.", color = Palette.TextSecondary, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            CyberButton("CONTINUE", Modifier.fillMaxWidth(), accent = Palette.Cyan, primary = true) { onContinue() }
        }
    }
}
