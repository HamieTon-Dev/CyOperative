package com.cyberoperative.game.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyberoperative.game.data.UpgradeDef
import com.cyberoperative.game.data.Upgrades
import com.cyberoperative.game.engine.GameEngine
import com.cyberoperative.game.ui.theme.Palette

/**
 * Owned-upgrade strip along the bottom (owner, 2026-10-08): one small icon
 * per mod with its level. Press and hold an icon to see what it does. The
 * Plasma Beam icon doubles as its heat / overheat-cooldown meter.
 */
@Composable
fun UpgradeBar(h: HudSnapshot, modifier: Modifier = Modifier) {
    if (h.owned.isEmpty()) return
    var held by remember { mutableStateOf<String?>(null) }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        val heldDef = held?.let { id -> Upgrades.all.firstOrNull { it.id == id } }
        if (heldDef != null) {
            UpgradeTooltip(heldDef, h.owned.firstOrNull { it.first == heldDef.id }?.second ?: 1)
            Spacer(Modifier.height(6.dp))
        }
        if (h.beamCooldown > 0f) {
            Text(
                "PLASMA BEAM COOLING · ${"%.1f".format(h.beamCooldown)}s",
                color = Palette.Red, style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .background(Palette.Background.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )
            Spacer(Modifier.height(4.dp))
        }
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for ((id, level) in h.owned) {
                val def = Upgrades.all.firstOrNull { it.id == id } ?: continue
                UpgradeIcon(
                    def, level,
                    beamHeat = if (id == Upgrades.PLASMA_BEAM.id) h.beamHeat else 0f,
                    beamCooldown = if (id == Upgrades.PLASMA_BEAM.id) h.beamCooldown else 0f,
                    modifier = Modifier.pointerInput(id) {
                        detectTapGestures(onPress = {
                            held = id
                            tryAwaitRelease()
                            held = null
                        })
                    }
                )
            }
        }
    }
}

@Composable
private fun UpgradeIcon(def: UpgradeDef, level: Int, beamHeat: Float, beamCooldown: Float, modifier: Modifier) {
    val c = Color(def.rarity.color)
    val shape = RoundedCornerShape(5.dp)
    Box(
        modifier
            .size(32.dp)
            .background(Palette.Background.copy(alpha = 0.85f), shape)
            .border(1.dp, c.copy(alpha = 0.8f), shape),
        contentAlignment = Alignment.Center
    ) {
        // Beam: heat fills from the bottom; during overheat the icon dims with a countdown.
        if (beamCooldown > 0f) {
            val f = (beamCooldown / GameEngine.BEAM_COOLDOWN).coerceIn(0f, 1f)
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(f)
                    .background(Palette.Red.copy(alpha = 0.45f), shape)
            )
        } else if (beamHeat > 0f) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(beamHeat.coerceIn(0f, 1f))
                    .background(Palette.Gold.copy(alpha = 0.3f), shape)
            )
        }
        Text(
            if (beamCooldown > 0f) "%.0f".format(kotlin.math.ceil(beamCooldown)) else def.glyph,
            color = if (beamCooldown > 0f) Palette.Red else c,
            fontSize = if (beamCooldown > 0f) 13.sp else 8.sp,
            maxLines = 1, overflow = TextOverflow.Clip, textAlign = TextAlign.Center
        )
        if (def.maxLevel > 1) {
            Text(
                "$level", color = Palette.TextPrimary, fontSize = 8.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(1.dp)
                    .background(c.copy(alpha = 0.35f), RoundedCornerShape(3.dp))
                    .padding(horizontal = 2.dp)
            )
        }
    }
}

@Composable
private fun UpgradeTooltip(def: UpgradeDef, level: Int) {
    val c = Color(def.rarity.color)
    Column(
        Modifier
            .widthIn(max = 300.dp)
            .padding(horizontal = 16.dp)
            .background(Palette.Surface, RoundedCornerShape(8.dp))
            .border(1.dp, c, RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(def.name, color = Palette.TextPrimary, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(8.dp))
            Text(def.rarity.label, color = c, style = MaterialTheme.typography.labelSmall)
        }
        if (def.maxLevel > 1) Text("LEVEL $level / ${def.maxLevel}", color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
        Text(def.effect(level), color = Palette.Green, style = MaterialTheme.typography.bodySmall)
        Text(def.description, color = Palette.TextSecondary, style = MaterialTheme.typography.bodySmall)
    }
}
