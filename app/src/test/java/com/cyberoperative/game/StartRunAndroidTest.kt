package com.cyberoperative.game

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.cyberoperative.game.audio.AudioManager
import com.cyberoperative.game.engine.Difficulty
import com.cyberoperative.game.engine.GameMode
import com.cyberoperative.game.save.SaveRepository
import com.cyberoperative.game.ui.game.GameScreen
import com.cyberoperative.game.ui.game.GameSession
import com.cyberoperative.game.ui.theme.CyberOperativeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Android: PLAY → difficulty → START opens the game screen without crashing. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [26, 35])
class StartRunAndroidTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun start(difficulty: Difficulty, tutorialDone: Boolean, landscape: Boolean = false, veteran: Boolean = false, skin: String = "default") {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repo = SaveRepository(ctx)
        repo.update { it.copy(tutorialDone = tutorialDone, savedRun = null) }
        if (veteran) repo.update {
            it.copy(
                operativeXp = 50_000_000L, diamonds = 5000, euros = 2_000_000,
                permanentUpgrades = com.cyberoperative.game.data.PermanentUpgrades.all.associate { u -> u.id to u.maxLevel },
                ownedSkins = com.cyberoperative.game.data.OperativeSkins.all.map { k -> k.id }.toSet(), selectedSkin = skin,
                highestLevel = 120, bestWorld = 6, totalRuns = 80
            )
        }
        com.cyberoperative.game.ui.common.PlayMode.landscape = landscape
        try {
            val s = GameSession(repo, AudioManager(ctx), GameMode.CAMPAIGN, difficulty)
            compose.mainClock.autoAdvance = false
            compose.setContent { CyberOperativeTheme { GameScreen(s, true, {}, {}) } }
            repeat(120) { compose.mainClock.advanceTimeBy(16) }
            s.showTutorial = false
            repeat(240) { compose.mainClock.advanceTimeBy(16) }
        } finally {
            com.cyberoperative.game.ui.common.PlayMode.landscape = false
            com.cyberoperative.game.data.ArenaShape.wide = false
            com.cyberoperative.game.data.ArenaShape.pcRules = false
        }
    }

    @Test fun veteranNormal() = start(Difficulty.MEDIUM, tutorialDone = true, veteran = true, skin = "neon_custom")
    @Test fun veteranHard() = start(Difficulty.HARD, tutorialDone = true, veteran = true, skin = "glitched")
    @Test fun normalFirstRun() = start(Difficulty.MEDIUM, tutorialDone = false)
    @Test fun easy() = start(Difficulty.EASY, tutorialDone = true)
    @Test fun hard() = start(Difficulty.HARD, tutorialDone = true)
    @Test @Config(qualifiers = "w740dp-h360dp-xxhdpi") fun landscapePhone360() = start(Difficulty.MEDIUM, tutorialDone = false, landscape = true)
    @Test @Config(qualifiers = "w640dp-h320dp-xhdpi") fun landscapeSmall() = start(Difficulty.HARD, tutorialDone = true, landscape = true, veteran = true)
    @Test @Config(qualifiers = "w800dp-h384dp-xxhdpi") fun landscapeVeteranFirstRun() = start(Difficulty.EASY, tutorialDone = false, landscape = true, veteran = true)
    @Test @Config(qualifiers = "w891dp-h411dp-xxhdpi") fun landscape() = start(Difficulty.MEDIUM, tutorialDone = true, landscape = true)
}
