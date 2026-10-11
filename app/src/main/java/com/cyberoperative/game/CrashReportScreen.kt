package com.cyberoperative.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.cyberoperative.game.ui.common.CyberButton
import com.cyberoperative.game.ui.theme.Palette

/** Shown on the launch after a crash: the error report, COPY it, then CONTINUE into the game. */
@Composable
fun CrashReportScreen(report: String, onContinue: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    Column(
        Modifier.fillMaxSize().background(Palette.SurfaceSunken).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("THE GAME CRASHED LAST TIME", color = Palette.Red, style = MaterialTheme.typography.titleMedium)
        Text("Tap COPY and send this report to the developer.", color = Palette.TextSecondary, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CyberButton("COPY", Modifier.weight(1f), accent = Palette.Cyan) { clipboard.setText(AnnotatedString(report)) }
            CyberButton("CONTINUE", Modifier.weight(1f), accent = Palette.Green) { onContinue() }
        }
        Text(
            report, color = Palette.TextPrimary, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
        )
    }
}
