package com.cyberoperative.game.ui.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cyberoperative.game.engine.Difficulty
import com.cyberoperative.game.engine.GameMode
import com.cyberoperative.game.ui.common.CyberButton
import com.cyberoperative.game.ui.theme.Palette

/**
 * Shown when a NEW run starts (owner, 2026-10-08): EASY / MEDIUM / HARD.
 * If a saved operation exists, it says plainly that starting will replace it.
 */
@Composable
fun DifficultyPicker(
    mode: GameMode,
    initial: Difficulty,
    savedRunLabel: String?,
    opLevel: Int = 1,
    permanent: Map<String, Int> = emptyMap(),
    /** Highest INFERNO tier unlocked (0 = none). */
    infernoUnlocked: Int = 0,
    onStart: (Difficulty) -> Unit,
    onCancel: () -> Unit
) {
    var picked by remember { mutableStateOf(initial) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xE6040810))
            .clickable(enabled = true, onClick = {}),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
                .background(Palette.Surface, RoundedCornerShape(10.dp))
                .border(1.dp, Palette.Cyan, RoundedCornerShape(10.dp))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                if (mode == GameMode.ENDLESS) "NEW ENDLESS RUN" else "NEW CAMPAIGN",
                color = Palette.Cyan, style = MaterialTheme.typography.titleLarge
            )
            Text("SELECT DIFFICULTY", color = Palette.TextSecondary, style = MaterialTheme.typography.labelMedium)
            if (opLevel > 1) {
                val cfg = remember(opLevel, permanent) {
                    com.cyberoperative.game.data.Operatives.buildConfig("operative", permanent, 0L).copy(opLevel = opLevel)
                }
                Text(
                    "OP LVL $opLevel: threats +${((cfg.opHpMul - 1f) * 100).toInt()}% HP, +${((cfg.opDamageMul - 1f) * 100).toInt()}% damage",
                    color = Palette.Red, style = MaterialTheme.typography.labelSmall
                )
            }
            Spacer(Modifier.height(14.dp))
            // Easy/Normal/Hard, every unlocked INFERNO tier, and the next one shown locked.
            val nextLocked = Difficulty.inferno(infernoUnlocked + 1)
            // INFERNO is one row: the picked level (the highest unlocked by default), set with the stepper.
            val infernoRow = if (picked.inferno > 0) picked else Difficulty.inferno(infernoUnlocked)
            for (d in Difficulty.entries + listOfNotNull(infernoRow, nextLocked)) {
                val locked = d == nextLocked
                val c = if (locked) Palette.TextMuted else Color(d.color)
                val selected = d == picked
                val shape = RoundedCornerShape(8.dp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (selected) c.copy(alpha = 0.14f) else Palette.Background, shape)
                        .border(if (selected) 2.dp else 1.dp, if (selected) c else c.copy(alpha = 0.35f), shape)
                        .clickable(enabled = !locked) { picked = d }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(16.dp)
                            .border(2.dp, c, CircleShape)
                            .padding(3.dp)
                            .background(if (selected) c else Color.Transparent, CircleShape)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text((if (locked) "🔒 " else "") + d.label, color = c, style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (locked) "Beat World 15's boss on ${if (d.inferno == 1) "HARD" else Difficulty.inferno(d.inferno - 1)?.label} to unlock"
                            else d.blurb,
                            color = Palette.TextSecondary, style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                if (d == picked && d.inferno > 0 && !locked) {
                    Spacer(Modifier.height(6.dp))
                    InfernoStepper(d.inferno, infernoUnlocked) { lv -> Difficulty.inferno(lv)?.let { picked = it } }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (savedRunLabel != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Starting a new run replaces your saved operation ($savedRunLabel).",
                    color = Palette.Red, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(6.dp))
            CyberButton("START", Modifier.fillMaxWidth(), accent = Color(picked.color), primary = true) { onStart(picked) }
            Spacer(Modifier.height(8.dp))
            CyberButton("CANCEL", Modifier.fillMaxWidth(), accent = Palette.TextSecondary) { onCancel() }
        }
    }
}

/** INFERNO level stepper (owner, 0.34.0: levels 1–500): −10 / −1 / LEVEL n of max / +1 / +10. */
@Composable
fun InfernoStepper(level: Int, max: Int, onLevel: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
        for ((label, step) in listOf("−10" to -10, "−1" to -1)) {
            CyberButton(label, Modifier.weight(1f), accent = Palette.TextSecondary, enabled = level > 1) { onLevel((level + step).coerceIn(1, max)) }
        }
        Text("LV $level / $max", color = Palette.TextPrimary, style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center, modifier = Modifier.weight(1.4f))
        for ((label, step) in listOf("+1" to 1, "+10" to 10)) {
            CyberButton(label, Modifier.weight(1f), accent = Palette.TextSecondary, enabled = level < max) { onLevel((level + step).coerceIn(1, max)) }
        }
    }
}
