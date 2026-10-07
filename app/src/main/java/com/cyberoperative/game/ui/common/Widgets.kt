package com.cyberoperative.game.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cyberoperative.game.ui.theme.Palette

/** Terminal-style button: dark fill, glowing border, monospace label. */
@Composable
fun CyberButton(
    text: String,
    modifier: Modifier = Modifier,
    accent: Color = Palette.Cyan,
    enabled: Boolean = true,
    primary: Boolean = false,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.4f)
            .background(
                Brush.horizontalGradient(
                    listOf(accent.copy(alpha = if (primary) 0.28f else 0.10f), Palette.Surface.copy(alpha = 0.85f))
                ),
                shape
            )
            .border(BorderStroke(if (primary) 2.dp else 1.dp, accent.copy(alpha = 0.85f)), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = if (primary) 14.dp else 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = text,
                color = if (primary) accent else Palette.TextPrimary,
                style = if (primary) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            if (subtitle != null) {
                Text(subtitle, color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** € / ◇ balance chip. */
@Composable
fun CurrencyChip(symbol: String, amount: Long, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(Palette.Surface.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(symbol, color = color, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(6.dp))
        Text(formatAmount(amount), color = Palette.TextPrimary, style = MaterialTheme.typography.titleSmall)
    }
}

fun formatAmount(v: Long): String = when {
    v >= 10_000_000 -> "${v / 1_000_000}M"
    v >= 100_000 -> "${v / 1_000}K"
    else -> "%,d".format(v)
}

/** Standard sub-screen frame: header with BACK, scrollable body supplied by caller. */
@Composable
fun ScreenScaffold(
    title: String,
    onBack: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Palette.Background)
    ) {
        LivingBackground(Modifier.fillMaxSize(), intensity = 0.45f)
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "< BACK",
                    color = Palette.Cyan,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .clickable(onClick = onBack)
                        .padding(vertical = 8.dp, horizontal = 4.dp)
                )
                Text(title, color = Palette.TextPrimary, style = MaterialTheme.typography.titleLarge)
                Box(Modifier.width(72.dp), contentAlignment = Alignment.CenterEnd) { trailing?.invoke() }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Palette.Divider)
            )
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

/** A titled section card used in Settings / About / etc. */
@Composable
fun TerminalCard(modifier: Modifier = Modifier, accent: Color = Palette.Divider, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .background(Palette.Surface.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
            .border(1.dp, accent, RoundedCornerShape(8.dp))
            .padding(14.dp)
    ) { content() }
}
