package com.cyberoperative.game.ui.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyberoperative.game.data.Gear
import com.cyberoperative.game.data.GearItem
import com.cyberoperative.game.data.GearSlot
import com.cyberoperative.game.data.OpProgress
import com.cyberoperative.game.data.Operatives
import com.cyberoperative.game.meta.Characters
import com.cyberoperative.game.meta.GearBank
import com.cyberoperative.game.save.PlayerProfile
import com.cyberoperative.game.save.SaveRepository
import com.cyberoperative.game.ui.common.CyberButton
import com.cyberoperative.game.ui.common.GearIconView
import com.cyberoperative.game.ui.common.LivingBackground
import com.cyberoperative.game.ui.theme.Palette

/** CHARACTER screen tabs (owner's mock-ups, 2026-10-13). */
enum class CharTab(val label: String) { OVERVIEW("OVERVIEW"), GEAR("GEAR"), SKILLS("SKILLS"), VAULT("DATA VAULT") }

/** Where an item sits right now. */
private enum class Where { EQUIPPED, BACKPACK, VAULT }

private fun whereOf(p: PlayerProfile, uid: Long): Where? = when {
    p.equipped.values.any { it.uid == uid } -> Where.EQUIPPED
    p.backpack.any { it.uid == uid } -> Where.BACKPACK
    p.vault.any { it.uid == uid } -> Where.VAULT
    else -> null
}

/** Drag-to-equip: the item under the finger / mouse, where it is, and the drop targets on screen. */
private class GearDrag {
    var item by mutableStateOf<GearItem?>(null)
    var pos by mutableStateOf(Offset.Zero)
    val targets = mutableStateMapOf<String, Rect>()
    fun targetAt(p: Offset): String? = targets.entries.firstOrNull { it.value.contains(p) }?.key
}

/**
 * The CHARACTER screen: OVERVIEW, GEAR (slots around the operative, backpack, item card),
 * SKILLS and the DATA VAULT. Also opened mid-run from the INVENTORY button ([inRun]): gear
 * swaps then apply at once in solo ([onGearChanged]); in co-op they apply on the next run.
 */
@Composable
fun CharacterScreen(
    save: SaveRepository,
    onBack: () -> Unit,
    startTab: CharTab = CharTab.OVERVIEW,
    inRun: Boolean = false,
    coopRun: Boolean = false,
    onDeploy: (() -> Unit)? = null,
    onOverclock: (() -> Unit)? = null,
    onEditLook: (() -> Unit)? = null,
    onNewCharacter: (() -> Unit)? = null,
    onGearChanged: () -> Unit = {},
    initialSelected: Long? = null
) {
    val p by save.profile.collectAsState()
    var tab by remember { mutableStateOf(startTab) }
    var selected by remember { mutableStateOf(initialSelected) }
    var picking by remember { mutableStateOf(false) }
    val drag = remember { GearDrag() }
    fun change(f: (PlayerProfile) -> PlayerProfile) {
        save.update(f)
        onGearChanged()
    }
    val actions = GearActions(
        equip = { uid -> change { GearBank.equip(it, uid) } },
        unequip = { slot -> change { GearBank.unequip(it, slot) } },
        toVault = { uid -> save.update { GearBank.toVault(it, uid) } },
        toBackpack = { uid -> save.update { GearBank.toBackpack(it, uid) } },
        lock = { uid -> save.update { GearBank.toggleLock(it, uid) } },
        discard = { uid -> save.update { GearBank.discard(it, uid) }; selected = null },
        select = { uid -> selected = uid; save.update { GearBank.markSeen(it, uid) } }
    )
    fun drop(item: GearItem, at: Offset) {
        val target = drag.targetAt(at) ?: return
        val from = whereOf(p, item.uid) ?: return
        when {
            target.startsWith("slot:") -> if (target == "slot:" + item.slot.name && from != Where.EQUIPPED) actions.equip(item.uid)
            target == "vault" -> when (from) {
                Where.BACKPACK -> actions.toVault(item.uid)
                Where.EQUIPPED -> { actions.unequip(item.slot); save.update { q -> q.backpack.lastOrNull()?.let { GearBank.toVault(q, it.uid) } ?: q } }
                else -> {}
            }
            target == "backpack" -> when (from) {
                Where.VAULT -> actions.toBackpack(item.uid)
                Where.EQUIPPED -> actions.unequip(item.slot)
                else -> {}
            }
        }
        selected = item.uid
    }

    Box(Modifier.fillMaxSize().background(Palette.Background)) {
        LivingBackground(Modifier.fillMaxSize(), intensity = 0.35f)
        BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp)) {
            val wide = maxWidth >= 760.dp
            Column(Modifier.fillMaxSize()) {
                Header(p, tab, wide, inRun, onBack) { tab = it; selected = null }
                Spacer(Modifier.height(8.dp))
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (tab) {
                        CharTab.OVERVIEW -> OverviewTab(p, wide, inRun, onEditLook, { picking = true }.takeIf { !inRun }, onDeploy)
                        CharTab.GEAR -> GearTab(p, wide, selected, actions, drag, coopRun, ::drop) { tab = CharTab.VAULT; selected = null }
                        CharTab.SKILLS -> SkillsTab(p, wide, onOverclock.takeIf { !inRun })
                        CharTab.VAULT -> VaultTab(p, wide, selected, actions, drag, ::drop, onDepositAll = { save.update { GearBank.depositAll(it) } }) { tab = CharTab.GEAR; selected = null }
                    }
                }
            }
        }
        // The dragged item follows the pointer above everything.
        val d = drag.item
        if (d != null) {
            val half = with(LocalDensity.current) { 32.dp.toPx() }
            GearIconView(d, Modifier.offset { IntOffset((drag.pos.x - half).toInt(), (drag.pos.y - half).toInt()) }.size(64.dp).alpha(0.9f))
        }
        if (picking) CharacterPicker(save, p, onClose = { picking = false }, onNew = onNewCharacter?.let { n -> { picking = false; n() } })
    }
}

private class GearActions(
    val equip: (Long) -> Unit,
    val unequip: (GearSlot) -> Unit,
    val toVault: (Long) -> Unit,
    val toBackpack: (Long) -> Unit,
    val lock: (Long) -> Unit,
    val discard: (Long) -> Unit,
    val select: (Long) -> Unit
)

@Composable
private fun Header(p: PlayerProfile, tab: CharTab, wide: Boolean, inRun: Boolean, onBack: () -> Unit, onTab: (CharTab) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (inRun) "< RESUME" else "< BACK", color = Palette.Cyan, style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.clickable(onClick = onBack).padding(vertical = 6.dp, horizontal = 4.dp))
            Spacer(Modifier.width(10.dp))
            Text(if (tab == CharTab.VAULT) "DATA VAULT" else "CHARACTER", color = Palette.TextPrimary,
                style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 1)
            if (wide) TabRow(tab, onTab)
            Spacer(Modifier.width(10.dp))
            Text("▣ ${"%,d".format(p.vault.size)} / ${"%,d".format(Gear.VAULT_SIZE)}", color = Palette.Cyan, style = MaterialTheme.typography.labelLarge)
        }
        if (!wide) { Spacer(Modifier.height(6.dp)); TabRow(tab, onTab) }
    }
}

@Composable
private fun TabRow(tab: CharTab, onTab: (CharTab) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (t in CharTab.entries) {
            val on = t == tab
            Text(
                t.label, color = if (on) Palette.Cyan else Palette.TextSecondary, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .background(if (on) Palette.Cyan.copy(alpha = 0.16f) else Palette.Surface.copy(alpha = 0.8f), RoundedCornerShape(6.dp))
                    .border(if (on) 2.dp else 1.dp, if (on) Palette.Cyan else Palette.Divider, RoundedCornerShape(6.dp))
                    .clickable { onTab(t) }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------------------
// OVERVIEW
// ---------------------------------------------------------------------------------------

/** The operative's figure in its skin and look, on the glowing stand. */
@Composable
fun CharacterFigure(p: PlayerProfile, modifier: Modifier, classId: String = p.selectedOperative, look: com.cyberoperative.game.save.CharacterLook = p.look, turn: Float = 0f) {
    val cls = Operatives.byId(classId)
    com.cyberoperative.game.ui.game.FigurePreview(
        com.cyberoperative.game.ui.game.BodyStyle.byId(cls.bodyId),
        com.cyberoperative.game.meta.StoreManager.effectiveSkin(p, cls.id), modifier, look = look, turn = turn
    )
}

/** The class's traits as shown on the character cards (Firewall is gone: Juggernaut shows armor). */
fun classTraits(classId: String): List<String> = Operatives.byId(classId).traits

@Composable
private fun OverviewTab(p: PlayerProfile, wide: Boolean, inRun: Boolean, onEditLook: (() -> Unit)?, onChange: (() -> Unit)?, onDeploy: (() -> Unit)?) {
    val st = OpProgress.of(p.operativeXp)
    val cls = Operatives.byId(p.selectedOperative)
    val stats = remember(p.equipped, p.selectedOperative, p.permanentUpgrades, st.level) {
        Operatives.buildConfig(cls.id, p.permanentUpgrades, 0L, st.level, p.equipped.values).baseStats
    }
    val info: @Composable () -> Unit = {
        Panel {
            Column {
                Text(p.characterName.ifBlank { "Operative" }, color = Palette.TextPrimary, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${cls.name}  •  ${st.label.uppercase()}", color = Palette.Cyan, style = MaterialTheme.typography.titleMedium)
                XpBar(st.fraction)
                Spacer(Modifier.height(8.dp))
                Text("CLASS TRAITS", color = Palette.TextPrimary, style = MaterialTheme.typography.labelLarge)
                for (t in classTraits(cls.id)) Text("· $t", color = Palette.Cyan, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Text("STATS (WITH GEAR)", color = Palette.TextPrimary, style = MaterialTheme.typography.labelLarge)
                StatLine("Max HP", "%,.0f".format(stats.maxHp))
                StatLine("Damage", "%,.0f".format(stats.damage))
                StatLine("Attack speed", "%.2f/s".format(stats.fireRate))
                StatLine("Critical", "%.0f%% · ×%.2f".format(stats.critChance * 100f, stats.critMul))
                StatLine("Armor", "%,.0f (%.0f%% at your level)".format(stats.armor, com.cyberoperative.game.engine.Armor.reduction(stats.armor, st.level) * 100f))
                StatLine("Move speed", "%.0f".format(stats.moveSpeed))
            }
        }
    }
    val look: @Composable () -> Unit = {
        Panel {
            Column {
                Text("APPEARANCE", color = Palette.Cyan, style = MaterialTheme.typography.labelLarge)
                for ((part, name) in com.cyberoperative.game.data.Appearance.names(cls.id, p.look)) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(part, color = Palette.TextPrimary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(56.dp))
                        Text(name, color = Palette.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (onEditLook != null) {
                    Spacer(Modifier.height(6.dp))
                    CyberButton("✎ EDIT APPEARANCE", Modifier.fillMaxWidth(), onClick = onEditLook)
                }
            }
        }
    }
    val buttons: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (onChange != null) CyberButton("⇄ CHANGE CHARACTER", Modifier.weight(1f), onClick = onChange)
            if (onDeploy != null && !inRun) CyberButton("DEPLOY »", Modifier.weight(1f), accent = Palette.Green, primary = true, onClick = onDeploy)
        }
    }
    if (wide) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { info() }
                CharacterFigure(p, Modifier.weight(1f).fillMaxHeight())
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { look() }
            }
            Spacer(Modifier.height(8.dp))
            buttons()
        }
    } else {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            CharacterFigure(p, Modifier.fillMaxWidth().height(220.dp))
            info()
            Spacer(Modifier.height(8.dp))
            look()
            Spacer(Modifier.height(10.dp))
            buttons()
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun XpBar(f: Float) {
    Box(Modifier.fillMaxWidth().padding(top = 4.dp).height(6.dp).background(Palette.SurfaceSunken, RoundedCornerShape(3.dp))) {
        Box(Modifier.fillMaxWidth(f.coerceIn(0f, 1f)).fillMaxHeight().background(Palette.Cyan, RoundedCornerShape(3.dp)))
    }
}

@Composable
private fun StatLine(k: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(k, color = Palette.TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(v, color = Palette.TextPrimary, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun Panel(modifier: Modifier = Modifier, accent: Color = Palette.Divider, content: @Composable () -> Unit) {
    Box(
        modifier.fillMaxWidth()
            .background(Palette.Surface.copy(alpha = 0.92f), RoundedCornerShape(10.dp))
            .border(1.dp, accent, RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) { content() }
}

// ---------------------------------------------------------------------------------------
// GEAR
// ---------------------------------------------------------------------------------------

private val leftSlots = listOf(GearSlot.HELMET, GearSlot.PRIMARY, GearSlot.IMPLANT, GearSlot.CORE)
private val rightSlots = listOf(GearSlot.CHEST, GearSlot.SECONDARY, GearSlot.GLOVES, GearSlot.BOOTS)

@Composable
private fun GearTab(
    p: PlayerProfile, wide: Boolean, selected: Long?, actions: GearActions, drag: GearDrag, coopRun: Boolean,
    onDrop: (GearItem, Offset) -> Unit, onOpenVault: () -> Unit
) {
    val sel = selected?.let { GearBank.find(p, it) }
    val doll: @Composable (Modifier) -> Unit = { m -> PaperDoll(p, m, selected, actions, drag, onDrop) }
    val pack: @Composable (Modifier) -> Unit = { m -> BackpackPanel(p, m, selected, actions, drag, onDrop, cols = 5) }
    val card: @Composable (Modifier) -> Unit = { m -> ItemPanel(p, sel, m, actions) }
    val hint = if (coopRun) "Co-op: gear changes apply on your next run." else if (com.cyberoperative.game.platform.AppInfo.desktop) "Drag an item to a matching gear slot" else "Hold and drag an item onto a matching gear slot"
    if (wide) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                doll(Modifier.weight(1.15f).fillMaxHeight())
                pack(Modifier.weight(1f).fillMaxHeight())
                card(Modifier.weight(0.85f).fillMaxHeight())
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("ⓘ $hint", color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                CyberButton("▤ OPEN DATA VAULT", accent = Palette.Cyan, onClick = onOpenVault)
            }
        }
    } else {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            doll(Modifier.fillMaxWidth().height(330.dp))
            Spacer(Modifier.height(8.dp))
            if (sel != null) { card(Modifier.fillMaxWidth().heightIn(max = 460.dp)); Spacer(Modifier.height(8.dp)) }
            pack(Modifier.fillMaxWidth().height(420.dp))
            Spacer(Modifier.height(6.dp))
            Text("ⓘ $hint", color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            CyberButton("▤ OPEN DATA VAULT", Modifier.fillMaxWidth(), onClick = onOpenVault)
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** The operative between two columns of gear slots (helmet, primary, implant, core | chest, secondary, gloves, boots). */
@Composable
private fun PaperDoll(p: PlayerProfile, modifier: Modifier, selected: Long?, actions: GearActions, drag: GearDrag, onDrop: (GearItem, Offset) -> Unit) {
    val st = OpProgress.of(p.operativeXp)
    Panel(modifier) {
        Column(Modifier.fillMaxSize()) {
            Text(p.characterName.ifBlank { "Operative" }, color = Palette.TextPrimary, style = MaterialTheme.typography.titleLarge, maxLines = 1)
            Text("${Operatives.byId(p.selectedOperative).name}  •  LEVEL ${st.level}", color = Palette.Cyan, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                SlotColumn(p, leftSlots, selected, actions, drag, onDrop)
                CharacterFigure(p, Modifier.weight(1f).fillMaxHeight())
                SlotColumn(p, rightSlots, selected, actions, drag, onDrop)
            }
        }
    }
}

@Composable
private fun SlotColumn(p: PlayerProfile, slots: List<GearSlot>, selected: Long?, actions: GearActions, drag: GearDrag, onDrop: (GearItem, Offset) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxHeight().width(70.dp)) {
    // Four slots and their labels share the height; tiles shrink on short screens.
    val tile = minOf(62.dp, (maxHeight - 64.dp) / 4)
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.CenterHorizontally) {
        for (s in slots) {
            val item = p.equipped[s]
            val hot = drag.item?.slot == s
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(s.label, color = if (hot) Palette.Green else Palette.TextSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                Box(
                    Modifier.size(tile)
                        .onGloballyPositioned { drag.targets["slot:" + s.name] = it.boundsInRoot() }
                        .border(if (hot) 2.dp else 0.dp, if (hot) Palette.Green else Color.Transparent, RoundedCornerShape(6.dp))
                ) {
                    GearCell(item, item != null && item.uid == selected, Modifier.fillMaxSize(), drag, onDrop) { item?.let { actions.select(it.uid) } }
                }
            }
        }
    }
    }
}

@Composable
private fun BackpackPanel(p: PlayerProfile, modifier: Modifier, selected: Long?, actions: GearActions, drag: GearDrag, onDrop: (GearItem, Offset) -> Unit, cols: Int) {
    var sort by remember { mutableStateOf(GearSort.RARITY) }
    val items = remember(p.backpack, sort) { sort.apply(p.backpack) }
    Panel(modifier.onGloballyPositioned { drag.targets["backpack"] = it.boundsInRoot() }, accent = if (drag.item != null) Palette.Cyan else Palette.Divider) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("BACKPACK", color = Palette.TextPrimary, style = MaterialTheme.typography.titleSmall, maxLines = 1, softWrap = false)
                Spacer(Modifier.width(6.dp))
                Text("${p.backpack.size}/${Gear.BACKPACK_SIZE}", color = Palette.TextSecondary, style = MaterialTheme.typography.labelMedium, maxLines = 1, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                SortChip(sort) { sort = it }
            }
            Spacer(Modifier.height(6.dp))
            if (items.isEmpty()) Text("Elites and bosses drop gear. It lands here.", color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall)
            LazyVerticalGrid(GridCells.Fixed(cols), Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(items, key = { it.uid }) { item ->
                    GearCell(item, item.uid == selected, Modifier.aspectRatio(1f), drag, onDrop) { actions.select(item.uid) }
                }
            }
        }
    }
}

enum class GearSort(val label: String) {
    RARITY("RARITY"), NEWEST("NEWEST"), LEVEL("LEVEL"), SLOT("SLOT");

    fun apply(list: List<GearItem>): List<GearItem> = when (this) {
        RARITY -> list.sortedWith(compareByDescending<GearItem> { it.rarity.ordinal }.thenByDescending { it.level }.thenByDescending { it.uid })
        NEWEST -> list.sortedByDescending { it.uid }
        LEVEL -> list.sortedWith(compareByDescending<GearItem> { it.level }.thenByDescending { it.rarity.ordinal })
        SLOT -> list.sortedWith(compareBy<GearItem> { it.slot.ordinal }.thenByDescending { it.rarity.ordinal })
    }
}

@Composable
private fun SortChip(sort: GearSort, onSort: (GearSort) -> Unit) {
    Text(
        "Sort: ${sort.label} ▾", color = Palette.TextPrimary, style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.background(Palette.SurfaceRaised, RoundedCornerShape(6.dp)).border(1.dp, Palette.Divider, RoundedCornerShape(6.dp))
            .clickable { onSort(GearSort.entries[(sort.ordinal + 1) % GearSort.entries.size]) }.padding(horizontal = 8.dp, vertical = 5.dp)
    )
}

/** One gear tile: tap to inspect, drag (hold first on phones) to move or equip. */
@Composable
private fun GearCell(item: GearItem?, selected: Boolean, modifier: Modifier, drag: GearDrag, onDrop: (GearItem, Offset) -> Unit, onClick: () -> Unit) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val desktop = com.cyberoperative.game.platform.AppInfo.desktop
    val dragMod = if (item == null) Modifier else Modifier.pointerInput(item.uid) {
        val start = { o: Offset -> drag.item = item; drag.pos = origin + o }
        val move = { d: Offset -> drag.pos += d }
        val end = { drag.item?.let { onDrop(it, drag.pos) }; drag.item = null }
        val cancel = { drag.item = null }
        if (desktop) detectDragGestures(onDragStart = start, onDragEnd = end, onDragCancel = cancel) { c, d -> c.consume(); move(d) }
        else detectDragGesturesAfterLongPress(onDragStart = start, onDragEnd = end, onDragCancel = cancel) { c, d -> c.consume(); move(d) }
    }
    Box(
        modifier
            .onGloballyPositioned { origin = it.positionInRoot() }
            .then(if (selected) Modifier.border(2.dp, Color.White, RoundedCornerShape(6.dp)) else Modifier)
            .clickable(enabled = item != null, onClick = onClick)
            .then(dragMod)
    ) {
        GearIconView(item, Modifier.fillMaxSize(), dim = drag.item?.uid == item?.uid && item != null)
        if (item != null) {
            if (item.fresh) Text("NEW", color = Palette.Cyan, fontSize = 8.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopStart).padding(2.dp).background(Palette.Background.copy(alpha = 0.8f), RoundedCornerShape(3.dp)).padding(horizontal = 2.dp))
            if (item.locked) Text("🔒", fontSize = 9.sp, modifier = Modifier.align(Alignment.BottomStart).padding(2.dp))
            Text("${item.level}", color = Palette.TextPrimary, fontSize = 8.sp, modifier = Modifier.align(Alignment.BottomEnd).padding(3.dp))
            if (!item.usableBy(com.cyberoperative.game.ui.menu.currentClass)) Text("✕", color = Palette.Red, fontSize = 10.sp, modifier = Modifier.align(Alignment.TopEnd).padding(2.dp))
        }
    }
}

/** Set while the character screen is up so tiles can grey out other classes' weapons. */
internal var currentClass: String = "operative"

/** The selected item's card: name, rarity and level, big art, main stat vs equipped, affixes, actions. */
@Composable
private fun ItemPanel(p: PlayerProfile, item: GearItem?, modifier: Modifier, actions: GearActions) {
    currentClass = p.selectedOperative
    Panel(modifier, accent = item?.let { Color(it.rarity.color).copy(alpha = 0.7f) } ?: Palette.Divider) {
        if (item == null) {
            Column {
                Text("SELECT AN ITEM", color = Palette.TextSecondary, style = MaterialTheme.typography.titleMedium)
                Text("Tap any piece to see its stats. Gear drops from elites and bosses, from 2 levels under yours to 2 over (never past ${OpProgress.MAX_LEVEL}).",
                    color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                RarityLegend()
            }
            return@Panel
        }
        var confirmDiscard by remember(item.uid) { mutableStateOf(false) }
        val where = whereOf(p, item.uid)
        val c = Color(item.rarity.color)
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.name.uppercase(), color = c, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text(if (item.locked) "🔒" else "🔓", fontSize = 16.sp, modifier = Modifier.clickable { actions.lock(item.uid) }.padding(4.dp))
            }
            Text("${item.rarity.label}  •  LEVEL ${item.level}  •  ${item.slot.label}", color = c.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge)
            GearIconView(item, Modifier.fillMaxWidth().height(120.dp).padding(vertical = 6.dp), frame = false)
            Text(item.mainLabel, color = Palette.TextSecondary, style = MaterialTheme.typography.labelLarge)
            Text(item.mainValue, color = Palette.TextPrimary, style = MaterialTheme.typography.headlineMedium)
            GearBank.versus(p, item)?.let { dv ->
                val up = dv >= 0f
                Text("${if (up) "+" else "−"}${"%.0f".format(kotlin.math.abs(dv))} vs equipped ${if (up) "▲" else "▼"}",
                    color = if (up) Palette.Green else Palette.Red, style = MaterialTheme.typography.labelLarge)
            }
            item.style?.let { Text(it.noun.uppercase() + if (it.rateMul != 1f) " · ×%.2f fire rate".format(it.rateMul) else "", color = Palette.Cyan, style = MaterialTheme.typography.labelMedium) }
            Spacer(Modifier.height(4.dp))
            for (a in item.affixes) Text("◆ " + a.stat.format(a.value), color = Palette.TextPrimary, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(4.dp))
            Text(item.description, color = Palette.TextSecondary, style = MaterialTheme.typography.bodySmall)
            item.classOnly?.let { cid ->
                if (cid != p.selectedOperative) Text("Only a ${Operatives.byId(cid).name} can equip this.", color = Palette.Red, style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.height(8.dp))
            when (where) {
                Where.EQUIPPED -> CyberButton("UNEQUIP", Modifier.fillMaxWidth()) { actions.unequip(item.slot) }
                Where.BACKPACK -> {
                    CyberButton("EQUIP", Modifier.fillMaxWidth(), accent = Palette.Cyan, primary = false, enabled = item.usableBy(p.selectedOperative)) { actions.equip(item.uid) }
                    Spacer(Modifier.height(6.dp))
                    CyberButton("STORE IN VAULT", Modifier.fillMaxWidth(), enabled = p.vault.size < Gear.VAULT_SIZE) { actions.toVault(item.uid) }
                }
                Where.VAULT -> {
                    CyberButton("EQUIP", Modifier.fillMaxWidth(), accent = Palette.Cyan, enabled = item.usableBy(p.selectedOperative)) { actions.equip(item.uid) }
                    Spacer(Modifier.height(6.dp))
                    CyberButton("MOVE TO BACKPACK", Modifier.fillMaxWidth(), enabled = p.backpack.size < Gear.BACKPACK_SIZE) { actions.toBackpack(item.uid) }
                }
                null -> {}
            }
            if (where != Where.EQUIPPED && !item.locked) {
                Spacer(Modifier.height(6.dp))
                CyberButton(if (confirmDiscard) "TAP AGAIN TO DESTROY" else "DISCARD", Modifier.fillMaxWidth(), accent = Palette.Red) {
                    if (confirmDiscard) actions.discard(item.uid) else confirmDiscard = true
                }
            }
        }
    }
}

@Composable
private fun RarityLegend() {
    Column {
        for (r in com.cyberoperative.game.data.GearRarity.entries) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 1.dp)) {
                Box(Modifier.size(10.dp).background(Color(r.color), RoundedCornerShape(2.dp)))
                Spacer(Modifier.width(6.dp))
                Text(r.label + if (r.minInferno > 0) "  · INFERNO ${roman(r.minInferno)}+" else "", color = Color(r.color), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun roman(n: Int): String = listOf("", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X").getOrElse(n) { n.toString() }

// ---------------------------------------------------------------------------------------
// DATA VAULT
// ---------------------------------------------------------------------------------------

private enum class VaultFilter(val label: String, val slots: Set<GearSlot>?) {
    ALL("ALL", null),
    WEAPONS("WEAPONS", setOf(GearSlot.PRIMARY, GearSlot.SECONDARY)),
    ARMOR("ARMOR", setOf(GearSlot.HELMET, GearSlot.CHEST, GearSlot.GLOVES, GearSlot.BOOTS)),
    IMPLANTS("IMPLANTS", setOf(GearSlot.IMPLANT)),
    CORES("CORES", setOf(GearSlot.CORE))
}

@Composable
private fun VaultTab(
    p: PlayerProfile, wide: Boolean, selected: Long?, actions: GearActions, drag: GearDrag,
    onDrop: (GearItem, Offset) -> Unit, onDepositAll: () -> Unit, onBackToGear: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(VaultFilter.ALL) }
    var sort by remember { mutableStateOf(GearSort.RARITY) }
    val shown = remember(p.vault, query, filter, sort) {
        val slots = filter.slots
        sort.apply(p.vault.filter { (slots == null || it.slot in slots) && (query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) || it.rarity.label.contains(query.trim(), ignoreCase = true)) })
    }
    val sel = selected?.let { GearBank.find(p, it) }
    val grid: @Composable (Modifier) -> Unit = { m ->
        Panel(m.onGloballyPositioned { drag.targets["vault"] = it.boundsInRoot() }, accent = if (drag.item != null) Palette.Cyan else Palette.Divider) {
            Column(Modifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("STORED ITEMS", color = Palette.TextPrimary, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(8.dp))
                    Text("${"%,d".format(p.vault.size)} / ${"%,d".format(Gear.VAULT_SIZE)}", color = Palette.TextSecondary, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f).height(6.dp).background(Palette.SurfaceSunken, RoundedCornerShape(3.dp))) {
                        Box(Modifier.fillMaxWidth(p.vault.size / Gear.VAULT_SIZE.toFloat()).fillMaxHeight().background(Palette.Cyan, RoundedCornerShape(3.dp)))
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        query, { query = it.take(24) }, singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Palette.TextPrimary),
                        cursorBrush = SolidColor(Palette.Cyan),
                        modifier = Modifier.weight(1f).background(Palette.SurfaceSunken, RoundedCornerShape(6.dp)).border(1.dp, Palette.Divider, RoundedCornerShape(6.dp)).padding(8.dp),
                        decorationBox = { inner -> if (query.isEmpty()) Text("⌕ Search gear…", color = Palette.TextMuted, style = MaterialTheme.typography.bodyMedium); inner() }
                    )
                    Spacer(Modifier.width(6.dp))
                    SortChip(sort) { sort = it }
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (f in VaultFilter.entries) {
                        val on = f == filter
                        Text(f.label, color = if (on) Palette.Cyan else Palette.TextSecondary, style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.background(if (on) Palette.Cyan.copy(alpha = 0.16f) else Palette.SurfaceRaised, RoundedCornerShape(6.dp))
                                .border(1.dp, if (on) Palette.Cyan else Palette.Divider, RoundedCornerShape(6.dp)).clickable { filter = f }.padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                }
                Spacer(Modifier.height(6.dp))
                if (shown.isEmpty()) Text(if (p.vault.isEmpty()) "The vault is empty. Store gear here from your backpack (1,500 slots)." else "Nothing matches.",
                    color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall)
                LazyVerticalGrid(GridCells.Adaptive(64.dp), Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(shown, key = { it.uid }) { item ->
                        GearCell(item, item.uid == selected, Modifier.aspectRatio(1f), drag, onDrop) { actions.select(item.uid) }
                    }
                }
            }
        }
    }
    val side: @Composable (Modifier) -> Unit = { m ->
        Column(m) {
            if (sel != null) { ItemPanel(p, sel, Modifier.fillMaxWidth().heightIn(max = 380.dp), actions); Spacer(Modifier.height(8.dp)) }
            BackpackPanel(p, Modifier.fillMaxWidth().weight(1f), selected, actions, drag, onDrop, cols = 3)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallButton("⇩ DEPOSIT ALL", Modifier.weight(1f), onClick = onDepositAll)
                SmallButton("↩ BACK TO GEAR", Modifier.weight(1f), onClick = onBackToGear)
            }
        }
    }
    if (wide) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            grid(Modifier.weight(2f).fillMaxHeight())
            side(Modifier.weight(1f).fillMaxHeight())
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            if (sel != null) { ItemPanel(p, sel, Modifier.fillMaxWidth().heightIn(max = 260.dp), actions); Spacer(Modifier.height(6.dp)) }
            grid(Modifier.fillMaxWidth().weight(1f))
            Spacer(Modifier.height(6.dp))
            BackpackStrip(p, selected, actions, drag, onDrop)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallButton("⇩ DEPOSIT ALL", Modifier.weight(1f), onClick = onDepositAll)
                SmallButton("↩ BACK TO GEAR", Modifier.weight(1f), onClick = onBackToGear)
            }
        }
    }
}

/** Portrait vault: the backpack as one scrolling strip under the grid (a drop target too). */
@Composable
private fun BackpackStrip(p: PlayerProfile, selected: Long?, actions: GearActions, drag: GearDrag, onDrop: (GearItem, Offset) -> Unit) {
    Column(Modifier.fillMaxWidth().onGloballyPositioned { drag.targets["backpack"] = it.boundsInRoot() }
        .background(Palette.Surface.copy(alpha = 0.92f), RoundedCornerShape(10.dp)).border(1.dp, if (drag.item != null) Palette.Cyan else Palette.Divider, RoundedCornerShape(10.dp)).padding(8.dp)) {
        Text("▦ BACKPACK  ${p.backpack.size} / ${Gear.BACKPACK_SIZE}", color = Palette.TextPrimary, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (item in GearSort.RARITY.apply(p.backpack)) GearCell(item, item.uid == selected, Modifier.size(56.dp), drag, onDrop) { actions.select(item.uid) }
        }
    }
}

// ---------------------------------------------------------------------------------------
// SKILLS
// ---------------------------------------------------------------------------------------

@Composable
private fun SkillsTab(p: PlayerProfile, wide: Boolean, onOverclock: (() -> Unit)?) {
    val cls = Operatives.byId(p.selectedOperative)
    val st = OpProgress.of(p.operativeXp)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Panel {
            Column {
                Text("CLASS SKILLS · ${cls.name}", color = Palette.Cyan, style = MaterialTheme.typography.titleMedium)
                Text(cls.passive, color = Palette.TextSecondary, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                for (i in 0..1) com.cyberoperative.game.data.Spells.of(cls.id, i)?.let { s ->
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(s.icon, color = Palette.Cyan, style = MaterialTheme.typography.titleLarge, modifier = Modifier.width(40.dp), textAlign = TextAlign.Center)
                        Column(Modifier.weight(1f)) {
                            Text("${s.label}  ·  ${if (i == 0) "R" else "F"}  ·  ${"%.0f".format(s.cooldown)}s", color = Palette.TextPrimary, style = MaterialTheme.typography.labelLarge)
                            Text(s.text, color = Palette.TextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("»", color = Palette.Cyan, style = MaterialTheme.typography.titleLarge, modifier = Modifier.width(40.dp), textAlign = TextAlign.Center)
                    Column(Modifier.weight(1f)) {
                        Text("DASH  ·  SPACE", color = Palette.TextPrimary, style = MaterialTheme.typography.labelLarge)
                        Text("A quick burst of speed out of trouble.", color = Palette.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Panel {
            Column {
                Text("OVERCLOCK", color = Palette.Cyan, style = MaterialTheme.typography.titleMedium)
                Text(if (st.level < OpProgress.MAX_LEVEL) "Reach OP Lv ${OpProgress.MAX_LEVEL} to start earning Overclock points (OP Lv ${st.level} now)."
                    else "Overclock ${st.overclock}: ${OpProgress.boardPoints(st, boardSpent(p.permanentUpgrades))} point(s) to spend on the board.",
                    color = Palette.TextSecondary, style = MaterialTheme.typography.bodySmall)
                if (onOverclock != null) { Spacer(Modifier.height(6.dp)); CyberButton("OPEN OVERCLOCK BOARD", Modifier.fillMaxWidth(), onClick = onOverclock) }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

// ---------------------------------------------------------------------------------------
// CHANGE CHARACTER
// ---------------------------------------------------------------------------------------

@Composable
private fun CharacterPicker(save: SaveRepository, p: PlayerProfile, onClose: () -> Unit, onNew: (() -> Unit)?) {
    var confirmDelete by remember { mutableStateOf<String?>(null) }
    Box(Modifier.fillMaxSize().background(Color(0xCC000000)).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(20.dp).widthIn(max = 560.dp).background(Palette.Surface, RoundedCornerShape(12.dp))
                .border(2.dp, Palette.Cyan, RoundedCornerShape(12.dp)).clickable(enabled = false) {}.padding(16.dp)
        ) {
            Text("CHANGE CHARACTER", color = Palette.Cyan, style = MaterialTheme.typography.titleLarge)
            Text("Each character keeps its own level, Overclock, gear, backpack and Data Vault.", color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(10.dp))
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                for (c in Characters.all(p)) {
                    val active = c.id == p.characterId
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            .background(if (active) Palette.Green.copy(alpha = 0.12f) else Palette.SurfaceRaised, RoundedCornerShape(8.dp))
                            .border(1.dp, if (active) Palette.Green else Palette.Divider, RoundedCornerShape(8.dp))
                            .clickable { if (!active) save.update { Characters.switchTo(it, c.id) }; onClose() }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CharacterFigure(p, Modifier.size(54.dp, 64.dp), classId = c.classId, look = c.look)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.name, color = Palette.TextPrimary, style = MaterialTheme.typography.titleMedium)
                            Text("${Operatives.byId(c.classId).name} · ${OpProgress.of(c.operativeXp).label}", color = Palette.Cyan, style = MaterialTheme.typography.labelMedium)
                        }
                        if (active) Text("ACTIVE", color = Palette.Green, style = MaterialTheme.typography.labelSmall)
                        else Text(if (confirmDelete == c.id) "DELETE?" else "✕", color = Palette.Red, style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.clickable { if (confirmDelete == c.id) { save.update { Characters.delete(it, c.id) }; confirmDelete = null } else confirmDelete = c.id }.padding(8.dp))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            if (onNew != null && Characters.all(p).size < Characters.MAX_CHARACTERS) CyberButton("+ NEW CHARACTER", Modifier.fillMaxWidth(), accent = Palette.Green, onClick = onNew)
            Spacer(Modifier.height(6.dp))
            CyberButton("CLOSE", Modifier.fillMaxWidth(), onClick = onClose)
        }
    }
}

// ---------------------------------------------------------------------------------------
// CREATE CHARACTER (owner's mock-up, 2026-10-13)
// ---------------------------------------------------------------------------------------

/**
 * The character creator: class, name and HEAD / FACE / BODY / LEGS (three options each), with a
 * turning preview. [editing] re-opens it for the active character (class fixed, SAVE CHANGES).
 */
@Composable
fun CharacterCreateScreen(save: SaveRepository, editing: Boolean, onBack: (() -> Unit)?, onDone: () -> Unit) {
    val p by save.profile.collectAsState()
    var classId by remember { mutableStateOf(if (editing) p.selectedOperative else Operatives.all.first().id) }
    var name by remember { mutableStateOf(if (editing) p.characterName else com.cyberoperative.game.data.Appearance.randomName()) }
    var look by remember { mutableStateOf(if (editing) p.look else com.cyberoperative.game.save.CharacterLook()) }
    var turn by remember { mutableStateOf(0f) }
    val cls = Operatives.byId(classId)
    val valid = Characters.cleanName(name) != null
    fun finish() {
        if (!valid) return
        save.update { q ->
            if (editing) Characters.setLook(Characters.rename(q, name), look)
            else Characters.create(q, name, classId, look)
        }
        onDone()
    }
    Box(Modifier.fillMaxSize().background(Palette.Background)) {
        LivingBackground(Modifier.fillMaxSize(), intensity = 0.35f)
        BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp)) {
            val wide = maxWidth >= 760.dp
            val classes: @Composable (Boolean) -> Unit = { vertical ->
                val list = if (editing) listOf(cls) else Operatives.all
                val item: @Composable (com.cyberoperative.game.data.OperativeDef) -> Unit = { c ->
                    val on = c.id == classId
                    Row(
                        Modifier.then(if (vertical) Modifier.fillMaxWidth() else Modifier.width(150.dp)).padding(vertical = 3.dp, horizontal = if (vertical) 0.dp else 3.dp)
                            .background(if (on) Palette.Green.copy(alpha = 0.12f) else Palette.Surface.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                            .border(if (on) 2.dp else 1.dp, if (on) Palette.Green else Palette.Divider, RoundedCornerShape(8.dp))
                            .clickable { classId = c.id }.padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CharacterFigure(p, Modifier.size(40.dp, 48.dp), classId = c.id, look = com.cyberoperative.game.save.CharacterLook())
                        Spacer(Modifier.width(6.dp))
                        Text(c.name, color = if (on) Palette.Green else Palette.TextPrimary, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                }
                if (vertical) Column { for (c in list) item(c) }
                else Row(Modifier.horizontalScroll(rememberScrollState())) { for (c in list) item(c) }
            }
            val about: @Composable () -> Unit = {
                Column {
                    Text(cls.name, color = Palette.TextPrimary, style = MaterialTheme.typography.headlineSmall)
                    Text(cls.description, color = Palette.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                    for (t in classTraits(cls.id)) Text("· $t", color = Palette.Cyan, style = MaterialTheme.typography.bodySmall)
                }
            }
            val preview: @Composable (Modifier) -> Unit = { m ->
                Box(m) {
                    CharacterFigure(p, Modifier.fillMaxSize(), classId = classId, look = look, turn = turn)
                    Text("↺", color = Palette.Cyan, style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.align(Alignment.CenterStart).clickable { turn = if (turn == 0f) kotlin.math.PI.toFloat() + 0.8f else 0f }.padding(10.dp))
                    Text("↻", color = Palette.Cyan, style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.align(Alignment.CenterEnd).clickable { turn = if (turn == 0f) kotlin.math.PI.toFloat() + 0.8f else 0f }.padding(10.dp))
                }
            }
            val parts: @Composable () -> Unit = {
                Column {
                    Text("CHARACTER NAME", color = Palette.Cyan, style = MaterialTheme.typography.labelLarge)
                    BasicTextField(
                        name, { name = it.take(Characters.NAME_MAX) }, singleLine = true,
                        textStyle = MaterialTheme.typography.titleMedium.copy(color = Palette.TextPrimary),
                        cursorBrush = SolidColor(Palette.Cyan),
                        modifier = Modifier.fillMaxWidth().background(Palette.SurfaceSunken, RoundedCornerShape(6.dp))
                            .border(1.dp, if (valid) Palette.Divider else Palette.Red, RoundedCornerShape(6.dp)).padding(10.dp),
                        decorationBox = { inner -> Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.weight(1f)) { inner() }; Text("✎", color = Palette.Cyan) } }
                    )
                    if (!valid) Text("2–16 letters, digits, spaces, - or _", color = Palette.Red, style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(6.dp))
                    for ((pi, part) in com.cyberoperative.game.data.Appearance.parts.withIndex()) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(part, color = Palette.TextPrimary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(52.dp))
                            for (o in 0 until com.cyberoperative.game.data.Appearance.OPTIONS) {
                                val on = com.cyberoperative.game.data.Appearance.option(look, pi) == o
                                PartOption(p, classId, com.cyberoperative.game.data.Appearance.with(look, pi, o), pi, on,
                                    Modifier.weight(1f).padding(horizontal = 3.dp).height(if (wide) 48.dp else 58.dp)) {
                                    look = com.cyberoperative.game.data.Appearance.with(look, pi, o)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallButton("⚄ RANDOMIZE", Modifier.weight(1f)) {
                            look = com.cyberoperative.game.data.Appearance.random()
                            if (!editing) name = com.cyberoperative.game.data.Appearance.randomName()
                        }
                        SmallButton(if (editing) "SAVE CHANGES »" else "CREATE »", Modifier.weight(1.4f), accent = Palette.Green, filled = true, enabled = valid, onClick = ::finish)
                    }
                }
            }
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (onBack != null) Text("< BACK", color = Palette.Cyan, style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.clickable(onClick = onBack).padding(vertical = 6.dp, horizontal = 4.dp))
                    Text(if (editing) "EDIT APPEARANCE" else "CREATE CHARACTER", color = Palette.TextPrimary, style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    if (onBack != null) Spacer(Modifier.width(70.dp))
                }
                Spacer(Modifier.height(6.dp))
                if (wide) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.width(190.dp).fillMaxHeight().verticalScroll(rememberScrollState())) { classes(true) }
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            about()
                            preview(Modifier.fillMaxWidth().weight(1f))
                        }
                        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) { Panel { parts() } }
                    }
                } else {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        classes(false)
                        Spacer(Modifier.height(6.dp))
                        preview(Modifier.fillMaxWidth().height(230.dp))
                        about()
                        Spacer(Modifier.height(8.dp))
                        Panel { parts() }
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

/** One part option: the figure zoomed onto that part, wearing the option. */
@Composable
private fun PartOption(p: PlayerProfile, classId: String, look: com.cyberoperative.game.save.CharacterLook, part: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val (oy, k) = when (part) {
        0 -> 0.22f to 2.4f
        1 -> 0.36f to 2.8f
        2 -> 0.58f to 1.9f
        else -> 0.86f to 2.1f
    }
    Box(
        modifier.background(Palette.SurfaceSunken, RoundedCornerShape(6.dp))
            .border(if (selected) 2.dp else 1.dp, if (selected) Palette.Green else Palette.Divider, RoundedCornerShape(6.dp))
            .clipToBounds()
            .clickable(onClick = onClick)
    ) {
        CharacterFigure(p, Modifier.fillMaxSize().graphicsLayer { scaleX = k; scaleY = k; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, oy) }, classId = classId, look = look)
    }
}

/** A one-line button for tight rows (labels never wrap). */
@Composable
private fun SmallButton(text: String, modifier: Modifier = Modifier, accent: Color = Palette.Cyan, filled: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier.alpha(if (enabled) 1f else 0.4f)
            .background(if (filled) accent.copy(alpha = 0.85f) else accent.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
            .border(1.5.dp, accent, RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (filled) Palette.Background else Palette.TextPrimary, style = MaterialTheme.typography.labelLarge,
            maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
    }
}
