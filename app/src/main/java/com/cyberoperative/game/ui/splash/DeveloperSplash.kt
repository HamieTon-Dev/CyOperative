package com.cyberoperative.game.ui.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.cyberoperative.game.R
import com.cyberoperative.game.ui.theme.Palette

/**
 * Stage 1 (§5): the HamieTon.dev ident, reusing CyOps TD's banner vector and
 * its fade-hold-fade timing. Tap to skip.
 */
@Composable
fun DeveloperSplash(onFinished: () -> Unit) {
    var elapsed by remember { mutableFloatStateOf(0f) }
    var done by remember { mutableStateOf(false) }
    fun finish() {
        if (!done) { done = true; onFinished() }
    }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (elapsed < IDENT_SECONDS) withFrameNanos { elapsed = (it - start) / 1_000_000_000f }
        finish()
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Palette.Background)
            .clickable(remember { MutableInteractionSource() }, indication = null) { finish() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .alpha(identAlpha(elapsed))
        ) {
            Image(
                painter = painterResource(R.drawable.hamieton_banner),
                contentDescription = "HamieTon.dev",
                colorFilter = ColorFilter.tint(Palette.Green),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth(0.78f)
                    .aspectRatio(70f / 8f)
            )
            Spacer(Modifier.height(18.dp))
            Text("P R E S E N T S", color = Palette.GreenDim, style = MaterialTheme.typography.titleSmall)
        }
    }
}

fun identAlpha(t: Float): Float = when {
    t <= 0f -> 0f
    t < FADE -> t / FADE
    t < FADE + HOLD -> 1f
    t < IDENT_SECONDS -> 1f - (t - FADE - HOLD) / FADE
    else -> 0f
}

private const val FADE = 0.5f
private const val HOLD = 0.9f
const val IDENT_SECONDS = FADE + HOLD + FADE
