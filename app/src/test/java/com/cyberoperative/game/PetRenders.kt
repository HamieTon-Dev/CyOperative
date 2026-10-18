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
        val pets = (0 until 12).map { Pet(500L + it, r.nextInt(Pets.PET_COUNT), GearRarity.entries[it % GearRarity.entries.size], fresh = false) } +
            // 0.40.0 pets (GLITCHED … ENCRYPTED, pages 4–5).
            (0 until 10).map { Pet(600L + it, Pets.NEW_BASE + it * 50, Pets.designedTier(Pets.NEW_BASE + it * 50)!!, fresh = false) } +
            // 0.41.0 animated ENCRYPTED pets.
            (0 until 10).map { Pet(700L + it, Pets.NEW_BASE + 500 + it * 15, GearRarity.ENCRYPTED, fresh = false) }
        repo.update { it.copy(characterId = "c", characterName = "ZERO", eggs = eggs, pets = pets, activePet = 703L, infernoStones = 4, petSeq = 1000) }
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
        // An animated ENCRYPTED pet (0.41.0) hatching: frames through the 0.45.0 animation.
        repo.update { it.copy(pets = it.pets + Pet(777L, Pets.NEW_BASE + 500 + 3, GearRarity.ENCRYPTED, fresh = true, eggArt = Pets.NEW_BASE + 500 + 3)) }
        compose.mainClock.autoAdvance = false
        compose.setContent { CyberOperativeTheme { CharacterScreen(repo, {}, startTab = CharTab.PETS) } }
        var at = 0L
        for ((ms, name) in listOf(1200L to "pets_hatch_1_cracking", 1820L to "pets_hatch_2_burst", 2300L to "pets_hatch_3_popout", 3400L to "pets_hatch_reveal")) {
            while (at < ms) { compose.mainClock.advanceTimeBy(16); at += 16 }
            save(name)
        }
    }

    /** The hatch at full burst for one rarity (0.45.0: bigger glow the rarer it is). */
    private fun hatchAt(rarity: GearRarity, art: Int, name: String) {
        assumeTrue(System.getProperty("cyberop.renderPreviews") == "true")
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repo = SaveRepository(ctx)
        repo.update { it.copy(characterId = "c", characterName = "ZERO", pets = listOf(Pet(1L, art, rarity, fresh = true))) }
        compose.mainClock.autoAdvance = false
        compose.setContent { CyberOperativeTheme { CharacterScreen(repo, {}, startTab = CharTab.PETS) } }
        var at = 0L
        while (at < 2600L) { compose.mainClock.advanceTimeBy(16); at += 16 }
        save(name)
    }

    @Test fun hatchCommon() = hatchAt(GearRarity.COMMON, 12, "pets_hatch_rarity_common")
    @Test fun hatchEpic() = hatchAt(GearRarity.EPIC, 40, "pets_hatch_rarity_epic")
    @Test fun hatchEncrypted() = hatchAt(GearRarity.ENCRYPTED, Pets.NEW_BASE + 500 + 3, "pets_hatch_rarity_encrypted")

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
