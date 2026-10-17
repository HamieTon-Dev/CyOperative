package com.cyberoperative.game

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.cyberoperative.game.audio.AudioManager
import com.cyberoperative.game.data.GearRarity
import com.cyberoperative.game.data.Pet
import com.cyberoperative.game.data.Pets
import com.cyberoperative.game.meta.Incubators
import com.cyberoperative.game.save.SaveRepository
import com.cyberoperative.game.ui.game.GameScreen
import com.cyberoperative.game.ui.game.GameSession
import com.cyberoperative.game.ui.menu.CharTab
import com.cyberoperative.game.ui.menu.CharacterScreen
import com.cyberoperative.game.ui.theme.CyberOperativeTheme
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.random.Random

/** Companion renders (0.38.0): the PETS tab with an egg hatching, and a pet in a run. `-PrenderPreviews`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1500dp-xxhdpi", sdk = [35])
class PetRenders {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val out = File(System.getProperty("cyberop.previewDir") ?: "build/screens")

    private fun save(name: String) {
        out.mkdirs()
        val view = compose.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { view.draw(Canvas(bmp)) }
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun stocked(repo: SaveRepository) {
        val r = Random(5)
        val eggs = GearRarity.entries.flatMap { rar -> (0..1).map { Pets.rollEgg(r, (rar.ordinal * 10 + it + 1).toLong(), rar) } }
        val pets = (0 until 12).map { Pet(500L + it, r.nextInt(Pets.PET_COUNT), GearRarity.entries[it % GearRarity.entries.size], fresh = false) }
        repo.update { it.copy(characterId = "c", characterName = "ZERO", eggs = eggs, pets = pets, activePet = 503L, infernoStones = 4, petSeq = 1000) }
        repo.update { p -> listOf(42L, 31L, 12L, 5L).fold(p) { q, u -> Incubators.send(q, u) ?: q } }
        repo.update { Incubators.setStones(it, 3) }
    }

    @Test fun petsTab() {
        assumeTrue(System.getProperty("cyberop.renderPreviews") == "true")
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repo = SaveRepository(ctx)
        stocked(repo)
        compose.mainClock.autoAdvance = false
        compose.setContent { CyberOperativeTheme { CharacterScreen(repo, {}, startTab = CharTab.PETS) } }
        compose.mainClock.advanceTimeBy(600)
        save("pets_tab")
    }

    @Test fun hatchReveal() {
        assumeTrue(System.getProperty("cyberop.renderPreviews") == "true")
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repo = SaveRepository(ctx)
        stocked(repo)
        repo.update { it.copy(pets = it.pets + Pet(777L, 40, GearRarity.ENCRYPTED, fresh = true)) }
        compose.mainClock.autoAdvance = false
        compose.setContent { CyberOperativeTheme { CharacterScreen(repo, {}, startTab = CharTab.PETS) } }
        compose.mainClock.advanceTimeBy(600)
        save("pets_hatch_reveal")
    }

    @Test @Config(qualifiers = "w1280dp-h720dp-mdpi", sdk = [35]) fun petsTabWide() {
        assumeTrue(System.getProperty("cyberop.renderPreviews") == "true")
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repo = SaveRepository(ctx)
        stocked(repo)
        compose.mainClock.autoAdvance = false
        compose.setContent { CyberOperativeTheme { CharacterScreen(repo, {}, startTab = CharTab.PETS) } }
        compose.mainClock.advanceTimeBy(600)
        save("pets_tab_wide")
    }

    @Test fun petInRun() {
        assumeTrue(System.getProperty("cyberop.renderPreviews") == "true")
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repo = SaveRepository(ctx)
        stocked(repo)
        repo.update { it.copy(tutorialDone = true) }
        val s = GameSession(repo, AudioManager(ctx), com.cyberoperative.game.engine.GameMode.CAMPAIGN, com.cyberoperative.game.engine.Difficulty.MEDIUM)
        val bot = Bot(s.engine, dodge = true)
        var t = 0f
        while (t < 6f) { bot.step(1f / 30f); t += 1f / 30f }
        s.engine.setInput(0f, 0f)
        compose.mainClock.autoAdvance = false
        compose.setContent { CyberOperativeTheme { GameScreen(s, true, {}, {}) } }
        repeat(30) { compose.mainClock.advanceTimeBy(16) }
        save("pet_in_run")
    }
}
