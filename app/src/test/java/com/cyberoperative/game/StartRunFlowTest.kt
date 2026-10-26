package com.cyberoperative.game

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import com.cyberoperative.game.audio.AudioManager
import com.cyberoperative.game.save.SaveRepository
import com.cyberoperative.game.ui.AppRoot
import com.cyberoperative.game.ui.theme.CyberOperativeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The whole first-launch tap flow: language → character → PLAY (or ENDLESS) → difficulty → START. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class StartRunFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun flow(landscape: Boolean, menuLabel: String) {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repo = SaveRepository(ctx)
        repo.update { it.copy(tutorialDone = true, savedRun = null, settings = it.settings.copy(landscape = landscape)) }
        compose.mainClock.autoAdvance = false
        compose.setContent { CyberOperativeTheme { AppRoot(repo, AudioManager(ctx)) } }
        try {
            // Splash, the first-launch language picker (0.55.0), boot terminal, then CREATE CHARACTER (0.30.0).
            var guard = 0
            while (compose.onAllNodesWithText("English").fetchSemanticsNodes().isEmpty() && guard++ < 1200) compose.mainClock.advanceTimeBy(50)
            compose.onAllNodesWithText("English")[0].performClick()
            assert(!com.cyberoperative.game.i18n.Languages.needsPicker())
            guard = 0
            while (compose.onAllNodesWithText("CREATE »").fetchSemanticsNodes().isEmpty() && guard++ < 1200) compose.mainClock.advanceTimeBy(50)
            compose.onAllNodesWithText("CREATE »")[0].performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
            repeat(10) { compose.mainClock.advanceTimeBy(50) }
            assert(com.cyberoperative.game.meta.Characters.hasCharacter(repo.current))
            guard = 0
            while (compose.onAllNodesWithText(menuLabel).fetchSemanticsNodes().isEmpty() && guard++ < 1200) compose.mainClock.advanceTimeBy(50)
            compose.onAllNodesWithText(menuLabel)[0].performClick()
            repeat(20) { compose.mainClock.advanceTimeBy(50) }
            compose.onAllNodesWithText("START")[0].performClick()
            repeat(300) { compose.mainClock.advanceTimeBy(16) }
        } finally {
            com.cyberoperative.game.ui.common.PlayMode.landscape = false
            com.cyberoperative.game.data.ArenaShape.wide = false
            com.cyberoperative.game.data.ArenaShape.pcRules = false
        }
    }

    @Test fun portraitPlay() = flow(false, "PLAY")
    @Test @Config(qualifiers = "w800dp-h384dp-xxhdpi") fun landscapePlay() = flow(true, "PLAY")
    @Test @Config(qualifiers = "w800dp-h384dp-xxhdpi") fun landscapeEndless() = flow(true, "ENDLESS")
}
