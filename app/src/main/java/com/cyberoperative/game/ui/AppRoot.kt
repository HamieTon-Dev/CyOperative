package com.cyberoperative.game.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.cyberoperative.game.audio.AudioManager
import com.cyberoperative.game.audio.MusicState
import com.cyberoperative.game.engine.GameSound
import com.cyberoperative.game.save.SaveRepository
import com.cyberoperative.game.ui.game.GameScreen
import com.cyberoperative.game.ui.game.GameSession
import com.cyberoperative.game.ui.menu.AboutScreen
import com.cyberoperative.game.ui.menu.AchievementsScreen
import com.cyberoperative.game.ui.menu.ArmoryScreen
import com.cyberoperative.game.ui.menu.ComingSoonScreen
import com.cyberoperative.game.ui.menu.LeaderboardScreen
import com.cyberoperative.game.ui.menu.MainMenuScreen
import com.cyberoperative.game.ui.menu.MenuTarget
import com.cyberoperative.game.ui.menu.OperativeScreen
import com.cyberoperative.game.ui.menu.PermanentUpgradesScreen
import com.cyberoperative.game.ui.menu.SettingsScreen
import com.cyberoperative.game.ui.splash.BootTerminal
import com.cyberoperative.game.ui.splash.DeveloperSplash

/** Destinations. A sealed hierarchy + one state value is the whole router. */
sealed interface Screen {
    data object DevSplash : Screen
    data object Boot : Screen
    data object Menu : Screen
    data object Game : Screen
    data class Sub(val target: MenuTarget) : Screen
}

@Composable
fun AppRoot(save: SaveRepository, audio: AudioManager) {
    var screen by remember { mutableStateOf<Screen>(Screen.DevSplash) }
    var session by remember { mutableStateOf<GameSession?>(null) }
    val profile by save.profile.collectAsState()

    fun click() = audio.play(GameSound.UI_CLICK)
    fun back() {
        audio.play(GameSound.UI_BACK)
        screen = Screen.Menu
    }
    fun startRun() {
        session = GameSession(save, audio)
        screen = Screen.Game
    }

    LaunchedEffect(screen) {
        when (screen) {
            Screen.DevSplash -> {}
            Screen.Boot -> audio.playBootChime()
            Screen.Menu, is Screen.Sub -> audio.setMusic(MusicState.MENU)
            Screen.Game -> {}
        }
    }

    when (val s = screen) {
        Screen.DevSplash -> DeveloperSplash { screen = Screen.Boot }
        Screen.Boot -> BootTerminal(onSound = { audio.play(it) }) { screen = Screen.Menu }
        Screen.Menu -> MainMenuScreen(profile) { target ->
            click()
            if (target == MenuTarget.PLAY) startRun() else screen = Screen.Sub(target)
        }
        Screen.Game -> {
            val sess = session
            if (sess == null) {
                LaunchedEffect(Unit) { screen = Screen.Menu }
            } else {
                GameScreen(
                    session = sess,
                    showDamageNumbers = profile.settings.damageNumbers,
                    onExitToMenu = {
                        session = null
                        screen = Screen.Menu
                    },
                    onNewOperation = { startRun() }
                )
            }
        }
        is Screen.Sub -> {
            BackHandler { back() }
            when (s.target) {
                MenuTarget.UPGRADES -> PermanentUpgradesScreen(save, audio, ::back)
                MenuTarget.OPERATIVE -> OperativeScreen(profile, ::back)
                MenuTarget.ARMORY -> ArmoryScreen(::back)
                MenuTarget.ACHIEVEMENTS -> AchievementsScreen(profile, ::back)
                MenuTarget.LEADERBOARD -> LeaderboardScreen(profile, ::back)
                MenuTarget.SETTINGS -> SettingsScreen(save, audio, ::back)
                MenuTarget.ABOUT -> AboutScreen(::back)
                MenuTarget.SKINS -> ComingSoonScreen("SKINS", SKINS_PLAN, ::back)
                MenuTarget.STORE -> ComingSoonScreen("STORE", STORE_PLAN, ::back)
                MenuTarget.PLAY -> LaunchedEffect(Unit) { startRun() }
            }
        }
    }
}

private val SKINS_PLAN = listOf(
    "Operative armour, glow and energy colours",
    "Weapon, orb and projectile effects",
    "Cosmetic only — no gameplay advantage"
)

private val STORE_PLAN = listOf(
    "◇ PACKS (Google Play Billing)",
    "SKINS · OPERATIVES · THEMES",
    "REVIVES · LEVEL-UP PACKS",
    "SPECIAL OFFERS",
    "Nothing in the store is required to progress."
)
