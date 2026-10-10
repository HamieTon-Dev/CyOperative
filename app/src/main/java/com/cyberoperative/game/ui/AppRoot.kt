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
import com.cyberoperative.game.engine.Difficulty
import com.cyberoperative.game.engine.GameMode
import com.cyberoperative.game.engine.RunSnapshot
import com.cyberoperative.game.engine.GameSound
import com.cyberoperative.game.save.SaveRepository
import com.cyberoperative.game.ui.game.GameScreen
import com.cyberoperative.game.ui.game.GameSession
import com.cyberoperative.game.ui.menu.AboutScreen
import com.cyberoperative.game.ui.menu.AchievementsScreen
import com.cyberoperative.game.ui.menu.ArmoryScreen
import com.cyberoperative.game.ui.menu.DifficultyPicker
import com.cyberoperative.game.ui.menu.SkinsScreen
import com.cyberoperative.game.ui.menu.StoreScreen
import com.cyberoperative.game.ui.menu.LeaderboardScreen
import com.cyberoperative.game.ui.menu.MainMenuScreen
import com.cyberoperative.game.ui.menu.MenuTarget
import com.cyberoperative.game.ui.menu.OperativeScreen
import com.cyberoperative.game.ui.menu.PermanentUpgradesScreen
import com.cyberoperative.game.ui.menu.SettingsScreen
import com.cyberoperative.game.ui.splash.BootTerminal
import com.cyberoperative.game.ui.splash.DeveloperSplash
import com.cyberoperative.game.net.CoopBackend
import com.cyberoperative.game.net.CoopRoom
import com.cyberoperative.game.net.OfflineCoopBackend
import com.cyberoperative.game.ui.game.CoopLink
import com.cyberoperative.game.ui.menu.CoopLobbyScreen
import com.cyberoperative.game.ui.menu.CoopScreen
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

/** Destinations. A sealed hierarchy + one state value is the whole router. */
sealed interface Screen {
    data object DevSplash : Screen
    data object Boot : Screen
    data object Menu : Screen
    data object Game : Screen
    data class Sub(val target: MenuTarget) : Screen
    data object CoopLobby : Screen
}

@Composable
fun AppRoot(save: SaveRepository, audio: AudioManager, coop: CoopBackend = remember { OfflineCoopBackend() }) {
    var screen by remember { mutableStateOf<Screen>(Screen.DevSplash) }
    /** Lives as long as the app UI: co-op links outlast the game screen they start on. */
    val appScope = rememberCoroutineScope()
    var coopRoom by remember { mutableStateOf<CoopRoom?>(null) }
    var session by remember { mutableStateOf<GameSession?>(null) }
    val profile by save.profile.collectAsState()

    fun click() = audio.play(GameSound.UI_CLICK)
    fun back() {
        audio.play(GameSound.UI_BACK)
        screen = Screen.Menu
    }
    var lastMode by remember { mutableStateOf(GameMode.CAMPAIGN) }
    var lastDifficulty by remember { mutableStateOf(Difficulty.byName(save.current.lastDifficulty)) }
    /** Mode waiting on the difficulty picker, or null when it is closed. */
    var picking by remember { mutableStateOf<GameMode?>(null) }
    fun startRun(mode: GameMode = lastMode, difficulty: Difficulty = lastDifficulty) {
        lastMode = mode
        lastDifficulty = difficulty
        // A new run replaces any saved operation.
        save.update { it.copy(savedRun = null, lastDifficulty = difficulty.name) }
        session = GameSession(save, audio, mode, difficulty)
        screen = Screen.Game
    }
    fun continueRun() {
        val snap = RunSnapshot.decodeOrNull(save.current.savedRun)
        if (snap == null) {
            save.update { it.copy(savedRun = null) }
            picking = GameMode.CAMPAIGN
            return
        }
        val s = GameSession(save, audio, restore = snap)
        lastMode = s.mode
        lastDifficulty = s.difficulty
        session = s
        screen = Screen.Game
    }

    LaunchedEffect(screen) {
        when (screen) {
            Screen.DevSplash -> {}
            Screen.Boot -> audio.playBootChime()
            Screen.Menu, is Screen.Sub, Screen.CoopLobby -> audio.setMusic(MusicState.MENU)
            Screen.Game -> {}
        }
    }

    when (val s = screen) {
        Screen.DevSplash -> DeveloperSplash { screen = Screen.Boot }
        Screen.Boot -> BootTerminal(onSound = { audio.play(it) }) { screen = Screen.Menu }
        Screen.Menu -> {
            MainMenuScreen(profile) { target ->
                click()
                when (target) {
                    MenuTarget.PLAY -> picking = GameMode.CAMPAIGN
                    MenuTarget.ENDLESS -> picking = GameMode.ENDLESS
                    MenuTarget.CONTINUE -> continueRun()
                    else -> screen = Screen.Sub(target)
                }
            }
            val mode = picking
            if (mode != null) {
                BackHandler { picking = null }
                DifficultyPicker(
                    mode = mode,
                    initial = lastDifficulty,
                    savedRunLabel = RunSnapshot.decodeOrNull(profile.savedRun)?.label,
                    opLevel = com.cyberoperative.game.ui.menu.operativeLevel(profile.operativeXp),
                    permanent = profile.permanentUpgrades,
                    onStart = { d -> click(); picking = null; startRun(mode, d) },
                    onCancel = { audio.play(GameSound.UI_BACK); picking = null }
                )
            }
        }
        Screen.Game -> {
            val sess = session
            if (sess == null) {
                LaunchedEffect(Unit) { screen = Screen.Menu }
            } else {
                GameScreen(
                    session = sess,
                    showDamageNumbers = profile.settings.damageNumbers,
                    screenShake = profile.settings.screenShake,
                    onExitToMenu = {
                        val wasCoop = sess.isCoop
                        session = null
                        coopRoom = null
                        screen = if (wasCoop) Screen.Sub(MenuTarget.CO_OP) else Screen.Menu
                    },
                    // A new co-op run goes back through the lobby with a fresh invite.
                    onNewOperation = {
                        if (sess.isCoop) { session = null; coopRoom = null; screen = Screen.Sub(MenuTarget.CO_OP) } else startRun()
                    }
                )
            }
        }
        is Screen.Sub -> {
            BackHandler { back() }
            when (s.target) {
                MenuTarget.UPGRADES -> PermanentUpgradesScreen(save, audio, ::back)
                MenuTarget.OPERATIVE -> OperativeScreen(save, ::back)
                MenuTarget.ARMORY -> ArmoryScreen(::back)
                MenuTarget.ACHIEVEMENTS -> AchievementsScreen(profile, ::back)
                MenuTarget.LEADERBOARD -> LeaderboardScreen(profile, ::back)
                MenuTarget.SETTINGS -> SettingsScreen(save, audio, ::back)
                MenuTarget.ABOUT -> AboutScreen(::back)
                MenuTarget.SKINS -> SkinsScreen(save, audio, ::back)
                MenuTarget.STORE -> StoreScreen(save, audio, ::back) { screen = Screen.Sub(MenuTarget.SKINS) }
                MenuTarget.CO_OP -> CoopScreen(coop, profile, ::back) { room ->
                    click()
                    coopRoom = room
                    screen = Screen.CoopLobby
                }
                MenuTarget.PLAY, MenuTarget.ENDLESS, MenuTarget.CONTINUE -> LaunchedEffect(Unit) { screen = Screen.Menu }
            }
        }
        Screen.CoopLobby -> {
            val room = coopRoom
            fun leaveLobby() {
                audio.play(GameSound.UI_BACK)
                room?.let { r -> appScope.launch { r.leave() } }
                coopRoom = null
                screen = Screen.Sub(MenuTarget.CO_OP)
            }
            if (room == null) {
                LaunchedEffect(Unit) { screen = Screen.Sub(MenuTarget.CO_OP) }
            } else {
                BackHandler { leaveLobby() }
                CoopLobbyScreen(
                    room = room,
                    initialDifficulty = lastDifficulty,
                    onStart = { st ->
                        if (st.host != null && st.guest != null) {
                            session = GameSession(save, audio, coop = CoopLink(room, st, appScope))
                            screen = Screen.Game
                        }
                    },
                    onLeave = ::leaveLobby
                )
            }
        }
    }
}
