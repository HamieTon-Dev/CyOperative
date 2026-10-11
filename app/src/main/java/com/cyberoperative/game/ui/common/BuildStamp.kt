package com.cyberoperative.game.ui.common

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyberoperative.game.platform.AppInfo

/** The build the player is on (owner, 2026-10-11): small, soft, bottom-right on every screen. */
fun buildStampText(): String = "v${AppInfo.versionName} (${AppInfo.versionCode})"

@Composable
fun BoxScope.BuildStamp() {
    Text(
        buildStampText(),
        color = Color.White.copy(alpha = 0.42f),
        fontSize = 9.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 6.dp, bottom = 3.dp)
    )
}
