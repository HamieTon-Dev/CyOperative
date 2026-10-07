package com.cyberoperative.game.ui.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyberoperative.game.engine.GameSound
import com.cyberoperative.game.ui.theme.Palette
import kotlin.random.Random

/**
 * Stage 2 (§5): the animated INITIALIZING terminal. Lines type out one after
 * another with a progress bar, blinking cursor and occasional glitch, then
 * "SYSTEM INITIALIZED / CYBER OPERATIVE ONLINE". Tap to skip.
 */
@Composable
fun BootTerminal(onSound: (GameSound) -> Unit, onFinished: () -> Unit) {
    val script = remember { BootScript.build(Random(System.nanoTime())) }
    var shown by remember { mutableIntStateOf(0) }
    var time by remember { mutableFloatStateOf(0f) }
    var finalBeat by remember { mutableFloatStateOf(-1f) }
    var done by remember { mutableStateOf(false) }
    fun finish() {
        if (!done) { done = true; onFinished() }
    }

    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        var next = 0.35f
        while (!done) {
            withFrameNanos { now ->
                time = (now - start) / 1_000_000_000f
                if (shown < script.size && time >= next) {
                    val line = script[shown]
                    shown++
                    next = time + line.delay
                    if (line.style == LineStyle.FINAL) {
                        onSound(GameSound.ACCESS_GRANTED)
                        finalBeat = time
                    } else if (line.text.isNotEmpty()) onSound(GameSound.BOOT_TICK)
                }
            }
            if (finalBeat >= 0f && time - finalBeat > 1.5f) finish()
        }
    }

    val progress = shown.toFloat() / script.size
    val glitch = (time * 7f).toInt() % 23 == 0
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF02060A))
            .clickable(remember { MutableInteractionSource() }, indication = null) { finish() }
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .offset(x = if (glitch) 3.dp else 0.dp)
        ) {
            Text("INITIALIZING...", color = Palette.Cyan, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            val cells = 28
            val filled = (progress * cells).toInt().coerceIn(0, cells)
            Text(
                "[" + "#".repeat(filled) + ".".repeat(cells - filled) + "] ${(progress * 100).toInt()}%",
                color = Palette.Green, style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(10.dp))
            // Only the most recent lines are visible: the log scrolls.
            val visible = 26
            val from = (shown - visible).coerceAtLeast(0)
            Column(Modifier.weight(1f)) {
                for (i in from until shown) {
                    val l = script[i]
                    if (l.style == LineStyle.FINAL) continue
                    Text(
                        l.text,
                        color = colorFor(l.style),
                        fontSize = 11.5.sp,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1
                    )
                }
                val cursorOn = (time * 2.4f).toInt() % 2 == 0
                Text(if (cursorOn) "_" else " ", color = Palette.Green, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (finalBeat >= 0f) {
            val t = time - finalBeat
            Column(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .background(Color(0xE602060A))
                    .padding(vertical = 28.dp)
                    .alpha((t * 3f).coerceIn(0f, 1f)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("ACCESS GRANTED", color = Palette.Green, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(10.dp))
                Text("SYSTEM INITIALIZED", color = Palette.TextSecondary, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                Text(
                    "CYBER OPERATIVE ONLINE",
                    color = Palette.Cyan, textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.alpha(if ((t * 5f).toInt() % 7 == 3) 0.5f else 1f)
                )
            }
        }
    }
}

private fun colorFor(style: LineStyle): Color = when (style) {
    LineStyle.HEADER -> Palette.Cyan
    LineStyle.OK -> Palette.Green
    LineStyle.WARN -> Palette.Orange
    LineStyle.CMD -> Palette.Blue
    LineStyle.STATUS -> Palette.Green
    LineStyle.FINAL -> Palette.Green
    LineStyle.NORMAL -> Palette.TextSecondary
}
