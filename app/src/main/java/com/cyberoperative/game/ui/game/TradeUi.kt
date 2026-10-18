package com.cyberoperative.game.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cyberoperative.game.data.Gear
import com.cyberoperative.game.data.GearItem
import com.cyberoperative.game.engine.TradeAction
import com.cyberoperative.game.engine.TradeDesk
import com.cyberoperative.game.engine.TradeOffer
import com.cyberoperative.game.ui.common.CyberButton
import com.cyberoperative.game.ui.common.GearIconView
import com.cyberoperative.game.ui.theme.Palette

/*
 * Co-op trading UI (owner, 0.35.0): the small [TRADE] button, the request prompt and the trade
 * window. The desk lives in the engine (engine/Trade.kt); this only shows it and sends actions.
 */

/** The small see-through [TRADE] button: co-op only, and only while trading is possible (shown by the caller). */
@Composable
fun TradeButton(session: GameSession, @Suppress("UNUSED_PARAMETER") hud: HudSnapshot, modifier: Modifier) {
    val c = Palette.Gold
    Box(
        modifier
            .background(Color(0xCC05080F), RoundedCornerShape(8.dp))
            .border(1.5.dp, c, RoundedCornerShape(8.dp))
            .clickable { session.trade(TradeAction.REQUEST) }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) { Text("TRADE", color = c, style = MaterialTheme.typography.labelLarge) }
}

/** The request prompt or the trade window, whichever the desk is in. */
@Composable
fun TradeOverlay(session: GameSession, @Suppress("UNUSED_PARAMETER") hud: HudSnapshot, modifier: Modifier) {
    val desk = session.engine.trade
    val me = session.myIndex
    when {
        desk.open -> TradeWindow(session, desk, me, modifier)
        desk.requestedBy == 1 - me -> Prompt(modifier, "YOUR PARTNER WANTS TO TRADE") {
            CyberButton("ACCEPT", Modifier.widthIn(min = 120.dp), accent = Palette.Green, primary = true) { session.trade(TradeAction.AGREE) }
            CyberButton("DECLINE", Modifier.widthIn(min = 120.dp), accent = Palette.Red) { session.trade(TradeAction.CLOSE) }
        }
        desk.requestedBy == me -> Prompt(modifier, "TRADE REQUEST SENT · WAITING FOR YOUR PARTNER") {
            CyberButton("CANCEL", Modifier.widthIn(min = 120.dp), accent = Palette.TextSecondary) { session.trade(TradeAction.CLOSE) }
        }
    }
}

@Composable
private fun Prompt(modifier: Modifier, text: String, buttons: @Composable () -> Unit) {
    Column(
        modifier.background(Palette.Surface.copy(alpha = 0.95f), RoundedCornerShape(10.dp))
            .border(1.5.dp, Palette.Gold, RoundedCornerShape(10.dp)).padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text, color = Palette.Gold, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { buttons() }
    }
}

@Composable
private fun TradeWindow(session: GameSession, desk: TradeDesk, me: Int, modifier: Modifier) {
    val p by session.saveRepo.profile.collectAsState()
    val mine = desk.offers[me]
    val theirs = desk.offers[1 - me]
    val myCharges = session.engine.operatives.getOrNull(me)?.hpCharges ?: 0
    // Your € are the same everywhere (owner): this run's wallet is your saved total plus the run.
    val myWallet = session.engine.operatives.getOrNull(me)?.let { session.engine.walletOf(it) }?.coerceAtLeast(0) ?: 0
    // The local draft, seeded from what the desk already has for me.
    var euros by remember { mutableIntStateOf(mine.euros) }
    var potions by remember { mutableIntStateOf(mine.potions) }
    var picked by remember { mutableStateOf(mine.itemList.map { it.uid }.toSet()) }
    var pickedEggs by remember { mutableStateOf(mine.eggList.map { it.uid }.toSet()) }
    var pickedPets by remember { mutableStateOf(mine.petList.map { it.uid }.toSet()) }
    val tradable = p.backpack.filter { !it.locked }
    fun send(e: Int = euros, po: Int = potions, ids: Set<Long> = picked, eggIds: Set<Long> = pickedEggs, petIds: Set<Long> = pickedPets) {
        euros = e.coerceIn(0, myWallet); potions = po.coerceIn(0, myCharges); picked = ids; pickedEggs = eggIds; pickedPets = petIds
        val items = tradable.filter { it.uid in picked }.take(TradeDesk.MAX_ITEMS)
        val eggs = p.eggs.filter { it.uid in pickedEggs }.take(TradeDesk.MAX_ITEMS)
        val pets = p.pets.filter { it.uid in pickedPets }.take(TradeDesk.MAX_ITEMS)
        session.trade(TradeAction.OFFER, TradeOffer(euros, potions, TradeOffer.pack(items, eggs, pets)))
    }
    fun toggle(set: Set<Long>, uid: Long) = if (uid in set) set - uid else if (set.size < TradeDesk.MAX_ITEMS) set + uid else set
    Column(
        modifier.widthIn(max = 640.dp).fillMaxWidth()
            .background(Palette.Surface.copy(alpha = 0.97f), RoundedCornerShape(10.dp))
            .border(1.5.dp, Palette.Gold, RoundedCornerShape(10.dp))
            .verticalScroll(rememberScrollState())
            .padding(14.dp)
    ) {
        Text("TRADE", color = Palette.Gold, style = MaterialTheme.typography.titleLarge)
        Text("Both players must ACCEPT. Changing either offer clears both accepts.", color = Palette.TextSecondary, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(10.dp))
        // --- My offer ---
        SideHeader("YOUR OFFER", mine.accepted)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            com.cyberoperative.game.ui.common.FitNumber(euros.toLong(), Palette.Gold, MaterialTheme.typography.titleMedium, Modifier.widthIn(min = 96.dp), prefix = "€ ", maxFull = 9)
            for ((label, d) in listOf("−1k" to -1000, "−100" to -100, "+100" to 100, "+1k" to 1000)) Step(label) { send(e = euros + d) }
        }
        com.cyberoperative.game.ui.common.FitNumber(myWallet.toLong(), Palette.TextMuted, MaterialTheme.typography.labelSmall, prefix = "You have € ")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("✚ $potions potions", color = Palette.Green, style = MaterialTheme.typography.titleSmall, modifier = Modifier.widthIn(min = 96.dp))
            Step("−1") { send(po = potions - 1) }
            Step("+1") { send(po = potions + 1) }
            Text("of $myCharges", color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
        }
        Text("Tap backpack items to offer them (up to ${TradeDesk.MAX_ITEMS}):", color = Palette.TextSecondary, style = MaterialTheme.typography.labelSmall)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (tradable.isEmpty()) Text("Backpack empty", color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
            for (item in tradable) ItemTile(item, item.uid in picked) {
                val ids = if (item.uid in picked) picked - item.uid else if (picked.size < TradeDesk.MAX_ITEMS) picked + item.uid else picked
                send(ids = ids)
            }
        }
        // Eggs and pets (0.52.0).
        if (p.eggs.isNotEmpty()) {
            Text("Eggs:", color = Palette.TextSecondary, style = MaterialTheme.typography.labelSmall)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (e in p.eggs.take(200)) EggTile(e, e.uid in pickedEggs) { send(eggIds = toggle(pickedEggs, e.uid)) }
            }
        }
        if (p.pets.isNotEmpty()) {
            Text("Pets:", color = Palette.TextSecondary, style = MaterialTheme.typography.labelSmall)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (pet in p.pets.take(200)) PetTile(pet.art, pet.rarity, pet.uid in pickedPets) { send(petIds = toggle(pickedPets, pet.uid)) }
            }
        }
        Spacer(Modifier.height(10.dp))
        // --- Partner's offer ---
        SideHeader("PARTNER OFFERS", theirs.accepted)
        Text("€ ${com.cyberoperative.game.ui.common.formatAmount(theirs.euros.toLong())}   ·   ✚ ${theirs.potions} potions", color = Palette.TextPrimary, style = MaterialTheme.typography.titleSmall)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val list = theirs.itemList
            if (list.isEmpty()) Text("No items", color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
            for (item in list) ItemTile(item, false) {}
        }
        for (item in theirs.itemList.take(TradeDesk.MAX_ITEMS)) Text("◆ ${item.name} · ${item.rarity.label} · LV ${item.level}", color = com.cyberoperative.game.ui.common.rarityColor(item.rarity), style = MaterialTheme.typography.labelSmall)
        if (theirs.eggList.isNotEmpty() || theirs.petList.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (e in theirs.eggList) EggTile(e, false) {}
            for (pet in theirs.petList) PetTile(pet.art, pet.rarity, false) {}
        }
        for (e in theirs.eggList) Text("◆ ${e.name} · ${e.rarity.label}", color = com.cyberoperative.game.ui.common.rarityColor(e.rarity), style = MaterialTheme.typography.labelSmall)
        for (pet in theirs.petList) Text("◆ ${pet.name} · ${pet.rarity.label}", color = com.cyberoperative.game.ui.common.rarityColor(pet.rarity), style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CyberButton(if (mine.accepted) "ACCEPTED ✓" else "ACCEPT", Modifier.weight(1f), accent = Palette.Green, primary = !mine.accepted, enabled = !mine.accepted) {
                session.trade(TradeAction.ACCEPT)
            }
            CyberButton("CANCEL TRADE", Modifier.weight(1f), accent = Palette.Red) { session.trade(TradeAction.CLOSE) }
        }
    }
}

@Composable
private fun SideHeader(title: String, accepted: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Palette.Cyan, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        Text(if (accepted) "✓ ACCEPTED" else "NOT ACCEPTED", color = if (accepted) Palette.Green else Palette.TextMuted, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun Step(label: String, onClick: () -> Unit) {
    Box(
        Modifier.border(1.dp, Palette.Divider, RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp)
    ) { Text(label, color = Palette.TextPrimary, style = MaterialTheme.typography.labelMedium) }
}

@Composable
private fun ItemTile(item: GearItem, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp)
            .then(if (selected) Modifier.border(2.dp, Color.White, RoundedCornerShape(6.dp)) else Modifier)
            .clickable(onClick = onClick)
    ) { GearIconView(item, Modifier.size(48.dp)) }
}

@Composable
private fun EggTile(e: com.cyberoperative.game.data.PetEgg, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).background(Palette.Surface, RoundedCornerShape(6.dp))
            .border(if (selected) 2.dp else 1.dp, if (selected) Color.White else com.cyberoperative.game.ui.common.rarityColor(e.rarity), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
    ) {
        androidx.compose.foundation.Canvas(Modifier.size(48.dp)) {
            with(com.cyberoperative.game.ui.common.PetArt) { drawEgg(e.art, size.width / 2f, size.height * 0.92f, size.minDimension * 0.8f) }
        }
    }
}

@Composable
private fun PetTile(art: Int, rarity: com.cyberoperative.game.data.GearRarity, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp)
            .then(if (selected) Modifier.border(2.dp, Color.White, RoundedCornerShape(6.dp)) else Modifier)
            .clickable(onClick = onClick)
    ) { com.cyberoperative.game.ui.common.PetIconView(art, rarity, Modifier.size(48.dp)) }
}
