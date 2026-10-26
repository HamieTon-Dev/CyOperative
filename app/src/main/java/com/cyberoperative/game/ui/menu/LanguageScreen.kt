package com.cyberoperative.game.ui.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cyberoperative.game.i18n.Language
import com.cyberoperative.game.i18n.Languages
import com.cyberoperative.game.i18n.tr
import com.cyberoperative.game.ui.common.LivingBackground
import com.cyberoperative.game.ui.common.PlayMode
import com.cyberoperative.game.ui.common.ScreenScaffold
import com.cyberoperative.game.ui.theme.Palette
import kotlinx.coroutines.delay

/**
 * First launch (owner, 0.55.0): right after the HamieTon.dev ident, before the boot terminal, the
 * player picks the game's language. Only on a fresh install: an update over an existing one never
 * shows it ([Languages.start]). The title cycles through every language so anyone can read it; the
 * device's language is highlighted.
 */
@Composable
fun FirstLaunchLanguageScreen(onDone: () -> Unit) {
    val device = remember { Languages.match(java.util.Locale.getDefault()) }
    var titleIndex by remember { mutableIntStateOf(0) }
    val titles = remember(device) {
        val all = Languages.all.map { it.tag }
        (listOf(device) + all.filter { it != device }).mapNotNull { Languages.chooseTitle[it] }.distinct()
    }
    LaunchedEffect(titles) {
        while (true) {
            delay(1600)
            titleIndex = (titleIndex + 1) % titles.size
        }
    }
    Box(Modifier.fillMaxSize().background(Palette.Background)) {
        LivingBackground(Modifier.fillMaxSize(), intensity = 0.45f)
        Column(
            (if (PlayMode.landscape) Modifier.align(Alignment.TopCenter).widthIn(max = 1000.dp).fillMaxHeight().padding(top = 12.dp)
            else Modifier.fillMaxSize())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(18.dp))
            Text(
                titles[titleIndex.coerceIn(titles.indices)],
                color = Palette.Green,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            Text("> LANGUAGE_", color = Palette.GreenDim, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(14.dp))
            LanguageGrid(highlight = device) { lang ->
                Languages.pickFirstLaunch(lang)
                onDone()
            }
        }
    }
}

/** Settings › LANGUAGE: the same list, with the current language highlighted. */
@Composable
fun LanguageSettingsScreen(onBack: () -> Unit) {
    var restartNote by remember { mutableStateOf(false) }
    ScreenScaffold(tr("LANGUAGE"), onBack) {
        Column {
            if (restartNote) {
                Text(
                    tr("Saved. Close and reopen the game to switch language."),
                    color = Palette.Gold, style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
            }
            LanguageGrid(highlight = Languages.current().tag) { lang ->
                if (Languages.apply(lang)) onBack() else restartNote = true
            }
        }
    }
}

@Composable
private fun LanguageGrid(highlight: String, onPick: (Language) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(if (PlayMode.landscape) 3 else 2),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(Languages.all, key = { it.tag }) { lang ->
            val on = lang.tag == highlight
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(if (on) Palette.Surface else Palette.Background.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                    .border(1.dp, if (on) Palette.Cyan else Palette.Divider, RoundedCornerShape(6.dp))
                    .clickable { onPick(lang) }
                    .padding(horizontal = 10.dp, vertical = 10.dp)
            ) {
                Text(lang.nativeName, color = if (on) Palette.Cyan else Palette.TextPrimary, style = MaterialTheme.typography.titleMedium)
                if (lang.englishName != lang.nativeName) {
                    Text(lang.englishName, color = Palette.TextSecondary, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
