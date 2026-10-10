package com.cyberoperative.game.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyberoperative.game.data.ThreatTier
import com.cyberoperative.game.engine.BossBrain
import com.cyberoperative.game.ui.theme.Palette

/**
 * Boss dossier card (Boss Expansion A3, from the owner's concept sheets):
 * slides in under the boss bar during the entrance with the role badge,
 * threat-tier skulls, signature abilities and the bounty, then fades as the
 * fight starts.
 */
@Composable
internal fun BossDossierCard(h: HudSnapshot, modifier: Modifier = Modifier) {
    val t = h.bossIntro
    if (t < 0f || h.bossTier <= 0) return
    val start = 0.2f
    val end = BossBrain.INTRO_SECONDS
    if (t < start) return
    val inP = ((t - start) / 0.3f).coerceIn(0f, 1f)
    val outP = ((end - t) / 0.35f).coerceIn(0f, 1f)
    val ease = inP * inP * (3f - 2f * inP)
    val accent = if (h.bossColor != 0L) Color(h.bossColor) else Palette.Red
    val roleColor = if (h.bossRoleColor != 0L) Color(h.bossRoleColor) else accent
    Column(
        // Mid-screen, clear of the boss materialising at the top.
        modifier
            .padding(top = 80.dp)
            .fillMaxWidth()
            .offset(x = ((1f - ease) * 240f).dp)
            .alpha(ease * outP)
            .background(Color(0xEE0A0610), RoundedCornerShape(8.dp))
            .border(1.dp, accent.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("THREAT DOSSIER", color = Palette.TextSecondary, style = mono(10.sp, FontWeight.Bold), modifier = Modifier.weight(1f))
            RoleBadge(h.bossRole, roleColor)
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ThreatSkulls(h.bossTier, accent, 16.dp)
            Spacer(Modifier.width(8.dp))
            Text(ThreatTier.label(h.bossTier), color = accent, style = mono(13.sp, FontWeight.Bold))
            Spacer(Modifier.width(6.dp))
            Text(
                ThreatTier.blurb(h.bossTier).uppercase(), color = Palette.TextMuted, style = mono(9.sp),
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
            )
        }
        if (h.bossAbilities.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (a in h.bossAbilities) {
                    Text(
                        a.uppercase(), color = Palette.TextPrimary, style = mono(9.sp, FontWeight.Bold),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .background(accent.copy(alpha = 0.14f), RoundedCornerShape(4.dp))
                            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 3.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("BOUNTY", color = Palette.TextSecondary, style = mono(10.sp, FontWeight.Bold))
            Spacer(Modifier.width(8.dp))
            Text("€" + "%,d".format(h.bossBounty), color = Palette.Euro, style = mono(15.sp, FontWeight.Bold))
        }
    }
}

/** Role chip: coloured outline and label (TANK, ASSASSIN, …). */
@Composable
internal fun RoleBadge(label: String, color: Color) {
    Text(
        label, color = color, style = mono(10.sp, FontWeight.Bold),
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            .border(1.dp, color, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

/** Four skulls, the first [tier] lit (LOW … EXTREME). */
@Composable
internal fun ThreatSkulls(tier: Int, color: Color, size: Dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (i in 1..4) {
            Canvas(Modifier.size(size)) {
                val c = if (i <= tier) color else Palette.TextMuted.copy(alpha = 0.35f)
                val w = this.size.width
                val hgt = this.size.height
                // Cranium, jaw, then eye and nose holes cut in the background colour.
                drawCircle(c, w * 0.42f, Offset(w / 2f, hgt * 0.42f))
                drawRoundRect(c, Offset(w * 0.27f, hgt * 0.6f), Size(w * 0.46f, hgt * 0.32f), CornerRadius(w * 0.08f))
                val hole = Color(0xFF0A0610)
                drawCircle(hole, w * 0.12f, Offset(w * 0.34f, hgt * 0.45f))
                drawCircle(hole, w * 0.12f, Offset(w * 0.66f, hgt * 0.45f))
                drawRect(hole, Offset(w * 0.47f, hgt * 0.6f), Size(w * 0.06f, hgt * 0.1f))
                for (k in 0..2) drawRect(hole, Offset(w * (0.35f + k * 0.12f), hgt * 0.8f), Size(w * 0.04f, hgt * 0.12f))
            }
        }
    }
}
