package com.cyberoperative.game.ui.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cyberoperative.game.BuildConfig
import com.cyberoperative.game.audio.AudioManager
import com.cyberoperative.game.data.LivingBackground
import com.cyberoperative.game.data.OperativeSkins
import com.cyberoperative.game.data.StoreCatalog
import com.cyberoperative.game.engine.GameSound
import com.cyberoperative.game.meta.StoreManager
import com.cyberoperative.game.save.PlayerProfile
import com.cyberoperative.game.save.SaveRepository
import com.cyberoperative.game.ui.common.CurrencyChip
import com.cyberoperative.game.ui.common.CyberButton
import com.cyberoperative.game.ui.common.OperativeMarkIcon
import com.cyberoperative.game.ui.common.ScreenScaffold
import com.cyberoperative.game.ui.common.TerminalCard
import com.cyberoperative.game.ui.game.LivingBackgroundDrawer.drawLivingBackground
import com.cyberoperative.game.ui.theme.Palette

/** Try a ◇ purchase; plays a confirm or a refusal sound. */
private fun spend(save: SaveRepository, audio: AudioManager, op: (PlayerProfile) -> PlayerProfile?) {
    var ok = false
    save.update { p -> op(p)?.also { ok = true } ?: p }
    audio.play(if (ok) GameSound.CURRENCY else GameSound.UI_BACK)
}

@Composable
fun StoreScreen(save: SaveRepository, audio: AudioManager, onBack: () -> Unit, onOpenSkins: () -> Unit) {
    val p by save.profile.collectAsState()
    var notice by remember { mutableStateOf<String?>(null) }
    ScreenScaffold("STORE", onBack, trailing = { CurrencyChip("◇", p.diamonds, Palette.Diamond) }) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Header("◇ GOLD") }
            items(StoreCatalog.diamondPacks, key = { it.productId }) { pack ->
                StoreRow(title = "◇ ${"%,d".format(pack.diamonds)}", subtitle = "Premium currency", price = pack.usdPrice, accent = Palette.Diamond) {
                    if (BuildConfig.DEBUG) {
                        // Google Play Billing is not wired yet; debug builds grant
                        // test ◇ so the store can be exercised. Release builds never do.
                        save.update { StoreManager.grantDiamonds(it, pack.diamonds) }
                        audio.play(GameSound.CURRENCY)
                        notice = "DEBUG BUILD: granted ◇${pack.diamonds} for testing (no charge)."
                    } else {
                        notice = "Purchases open when Google Play Billing is connected."
                    }
                }
            }
            notice?.let { n -> item { Text(n, color = Palette.Orange, style = MaterialTheme.typography.labelSmall) } }

            item { Header("REVIVES  ·  OWNED: ${p.reviveTokens}") }
            items(StoreCatalog.revivePacks, key = { it.id }) { pack ->
                StoreRow(
                    title = if (pack.revives == 1) "1 REVIVE" else "${pack.revives} REVIVE PACK",
                    subtitle = "Revives the operative where it fell (max ${StoreCatalog.MAX_PAID_REVIVES_PER_RUN} per run)",
                    price = "◇${"%,d".format(pack.priceDiamonds)}", accent = Palette.Green,
                    enabled = p.diamonds >= pack.priceDiamonds
                ) { spend(save, audio) { StoreManager.buyRevives(it, pack) } }
            }

            item { Header("OPERATIVES") }
            item {
                val owned = com.cyberoperative.game.meta.StoreManager.NEON_OPERATIVE_ID in p.ownedOperatives
                StoreRow(
                    title = "NEON OPERATIVE", subtitle = "The app icon come to life — faceted neon hood, antenna, glowing >_<",
                    price = if (owned) "OWNED" else "◇${StoreCatalog.NEON_OPERATIVE_PRICE}", accent = Palette.Cyan,
                    enabled = !owned && p.diamonds >= StoreCatalog.NEON_OPERATIVE_PRICE
                ) { spend(save, audio) { com.cyberoperative.game.meta.StoreManager.buyNeonOperative(it) } }
            }

            item { Header("OPERATIVE SKINS") }
            item {
                StoreRow(
                    title = "OPERATIVE SKINS (${OperativeSkins.paid.size})", subtitle = "◇${StoreCatalog.SKIN_PRICE} each — browse & equip",
                    price = "OPEN", accent = Palette.Cyan
                ) { onOpenSkins() }
            }
            item {
                val owned = StoreManager.allSkinsOwned(p)
                StoreRow(
                    title = "ALL OPERATIVE SKINS", subtitle = "Unlock all ${OperativeSkins.paid.size} skins",
                    price = if (owned) "OWNED" else "◇${StoreCatalog.ALL_SKINS_PRICE}", accent = Palette.Gold,
                    enabled = !owned && p.diamonds >= StoreCatalog.ALL_SKINS_PRICE
                ) { spend(save, audio) { StoreManager.buyAllSkins(it) } }
            }

            item { Header("LIVING BACKGROUNDS") }
            item {
                StoreRow(
                    title = "LIVING BACKGROUNDS (${LivingBackground.purchasable.size})", subtitle = "◇${StoreCatalog.BACKGROUND_PRICE} each — animated arena floors from CyOps TD",
                    price = "OPEN", accent = Palette.Cyan
                ) { onOpenSkins() }
            }
            item {
                val owned = StoreManager.allBackgroundsOwned(p)
                StoreRow(
                    title = "ALL LIVING BACKGROUNDS", subtitle = "Unlock all ${LivingBackground.purchasable.size} backgrounds",
                    price = if (owned) "OWNED" else "◇${StoreCatalog.ALL_BACKGROUNDS_PRICE}", accent = Palette.Gold,
                    enabled = !owned && p.diamonds >= StoreCatalog.ALL_BACKGROUNDS_PRICE
                ) { spend(save, audio) { StoreManager.buyAllBackgrounds(it) } }
            }
            item {
                Text(
                    "Nothing in the store is required to progress. € and ◇ are fictional in-game currencies.",
                    color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun Header(t: String) {
    Text(t, color = Palette.Cyan, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun StoreRow(title: String, subtitle: String, price: String, accent: Color, enabled: Boolean = true, onClick: () -> Unit) {
    TerminalCard(accent = accent.copy(alpha = 0.5f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Palette.TextPrimary, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, color = Palette.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.width(8.dp))
            CyberButton(price, accent = accent, enabled = enabled, onClick = onClick)
        }
    }
}

/** SKINS: buy / equip operative skins and living backgrounds. */
@Composable
fun SkinsScreen(save: SaveRepository, audio: AudioManager, onBack: () -> Unit) {
    val p by save.profile.collectAsState()
    var tab by remember { mutableStateOf(0) }
    ScreenScaffold("SKINS", onBack, trailing = { CurrencyChip("◇", p.diamonds, Palette.Diamond) }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CyberButton("OPERATIVE", Modifier.weight(1f), accent = if (tab == 0) Palette.Green else Palette.Divider) { tab = 0 }
            CyberButton("BACKGROUNDS", Modifier.weight(1f), accent = if (tab == 1) Palette.Green else Palette.Divider) { tab = 1 }
        }
        Spacer(Modifier.height(8.dp))
        if (tab == 0) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!StoreManager.allSkinsOwned(p)) item {
                    CyberButton("UNLOCK ALL ${OperativeSkins.paid.size} SKINS  ·  ◇${StoreCatalog.ALL_SKINS_PRICE}", Modifier.fillMaxWidth(), accent = Palette.Gold,
                        enabled = p.diamonds >= StoreCatalog.ALL_SKINS_PRICE) { spend(save, audio) { StoreManager.buyAllSkins(it) } }
                }
                items(OperativeSkins.all, key = { it.id }) { skin ->
                    val owned = skin.free || skin.id in p.ownedSkins
                    val equipped = p.selectedSkin == skin.id
                    TerminalCard(accent = if (equipped) Palette.Green else Palette.Divider) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OperativeMarkIcon(skin, Modifier.size(56.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(skin.name, color = Color(skin.edge), style = MaterialTheme.typography.titleSmall)
                                Text(skin.origin, color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall)
                            }
                            when {
                                equipped -> Text("EQUIPPED", color = Palette.Green, style = MaterialTheme.typography.labelMedium)
                                owned -> CyberButton("EQUIP", accent = Palette.Green) { spend(save, audio) { StoreManager.equipSkin(it, skin.id) } }
                                else -> CyberButton("◇${StoreCatalog.SKIN_PRICE}", accent = Palette.Diamond, enabled = p.diamonds >= StoreCatalog.SKIN_PRICE) {
                                    spend(save, audio) { StoreManager.buySkin(it, skin.id) }
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!StoreManager.allBackgroundsOwned(p)) item {
                    CyberButton("UNLOCK ALL BACKGROUNDS  ·  ◇${StoreCatalog.ALL_BACKGROUNDS_PRICE}", Modifier.fillMaxWidth(), accent = Palette.Gold,
                        enabled = p.diamonds >= StoreCatalog.ALL_BACKGROUNDS_PRICE) { spend(save, audio) { StoreManager.buyAllBackgrounds(it) } }
                }
                items(LivingBackground.entries, key = { it.id }) { bg ->
                    val owned = bg == LivingBackground.NONE || bg.id in p.ownedBackgrounds
                    val equipped = p.selectedBackground == bg.id
                    TerminalCard(accent = if (equipped) Palette.Green else Palette.Divider) {
                        Column {
                            BackgroundPreview(bg)
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(bg.displayName, color = Palette.TextPrimary, style = MaterialTheme.typography.titleSmall)
                                    Text(bg.description, color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall)
                                }
                                when {
                                    equipped -> Text("EQUIPPED", color = Palette.Green, style = MaterialTheme.typography.labelMedium)
                                    owned -> CyberButton("EQUIP", accent = Palette.Green) { spend(save, audio) { StoreManager.equipBackground(it, bg.id) } }
                                    else -> CyberButton("◇${StoreCatalog.BACKGROUND_PRICE}", accent = Palette.Diamond, enabled = p.diamonds >= StoreCatalog.BACKGROUND_PRICE) {
                                        spend(save, audio) { StoreManager.buyBackground(it, bg.id) }
                                    }
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun BackgroundPreview(bg: LivingBackground) {
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(bg) {
        val s = withFrameNanos { it }
        while (true) withFrameNanos { time = (it - s) / 1e9f }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(90.dp)
            .background(Palette.Background, RoundedCornerShape(6.dp))
            .border(1.dp, Palette.Divider, RoundedCornerShape(6.dp))
    ) {
        Canvas(Modifier.fillMaxWidth().height(90.dp)) {
            // Preview at arena scale: 720 world units across.
            val k = size.width / 720f
            drawContext.transform.scale(k, k, androidx.compose.ui.geometry.Offset.Zero)
            // Background intensity is tuned for a dark arena; boost the preview a little.
            drawLivingBackground(bg, 720f, size.height / k, time, 0.5f, 360f, size.height / k / 2f)
        }
    }
}
