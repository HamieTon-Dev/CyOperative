package com.cyberoperative.game.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cyberoperative.game.audio.AudioManager
import com.cyberoperative.game.audio.MusicLibrary
import com.cyberoperative.game.ui.theme.Palette

/**
 * Pause-screen music player: now playing, previous / play-pause / next,
 * SHUFFLE on/off, AUTO (back to the game's automatic music), and the full
 * CyOps TD soundtrack — tap a track to play it.
 */
@Composable
fun MusicPlayerPanel(audio: AudioManager, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Palette.SurfaceSunken, RoundedCornerShape(8.dp))
            .border(1.dp, Palette.Divider, RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Text("MUSIC", color = Palette.Purple, style = MaterialTheme.typography.labelLarge)
        Text(
            audio.currentTrack?.title ?: "—",
            color = Palette.TextPrimary, style = MaterialTheme.typography.bodyMedium,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        Text(
            if (audio.pinned != null) "YOUR PICK" else "AUTO · shuffles with the action",
            color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall
        )
        Spacer(Modifier.size(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Control("|<<", Modifier.weight(1f)) { audio.previousTrack() }
            Control(if (audio.userPaused) "PLAY" else "II", Modifier.weight(1f), active = true) { audio.pauseByUser(!audio.userPaused) }
            Control(">>|", Modifier.weight(1f)) { audio.nextTrack() }
            Control("SHUFFLE", Modifier.weight(1.6f), active = audio.shuffle) { audio.toggleShuffle() }
            Control("AUTO", Modifier.weight(1.2f), active = audio.pinned == null) { audio.clearPin() }
        }
        Spacer(Modifier.size(8.dp))
        LazyColumn(Modifier.heightIn(max = 220.dp)) {
            items(MusicLibrary.all, key = { it.id }) { t ->
                val playing = audio.currentTrack?.id == t.id
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { audio.selectTrack(t) }
                        .padding(vertical = 7.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (playing) "▶" else " ", color = Palette.Green, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(18.dp))
                    Text(
                        t.title.removePrefix("CyOps TD - "),
                        color = if (playing) Palette.Green else Palette.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun Control(label: String, modifier: Modifier, active: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier
            .background(if (active) Palette.Purple.copy(alpha = 0.2f) else Palette.Surface, RoundedCornerShape(6.dp))
            .border(1.dp, if (active) Palette.Purple else Palette.Divider, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (active) Palette.Purple else Palette.TextPrimary, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}
