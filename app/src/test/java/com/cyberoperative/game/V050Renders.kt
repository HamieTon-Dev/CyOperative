package com.cyberoperative.game

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.cyberoperative.game.audio.AudioManager
import com.cyberoperative.game.data.GearRarity
import com.cyberoperative.game.data.Pet
import com.cyberoperative.game.data.Pets
import com.cyberoperative.game.save.SaveRepository
import com.cyberoperative.game.ui.theme.CyberOperativeTheme
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 0.50.0 store, skins and PET CODEX renders (docs/screenshots/v050_*). Opt-in. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xhdpi", sdk = [35])
class V050Renders {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val on get() = System.getProperty("cyberop.renderPreviews") == "true"
    private fun repo() = SaveRepository(ApplicationProvider.getApplicationContext<android.app.Application>())

    private fun shot(name: String) {
        val view = compose.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { view.draw(Canvas(bmp)) }
        File(System.getProperty("cyberop.previewDir") ?: "build/screens", "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun store() {
        assumeTrue(on)
        val r = repo()
        r.update { it.copy(diamonds = 5000) }
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        compose.mainClock.autoAdvance = false
        compose.setContent { CyberOperativeTheme { com.cyberoperative.game.ui.menu.StoreScreen(r, AudioManager(ctx), {}, {}) } }
        compose.mainClock.advanceTimeBy(1200)
        compose.mainClock.advanceTimeBy(800)
        shot("v050_store_top")
    }

    /** Frames of the store's moving eggs and the skin previews (docs/screenshots/v050_*.gif). */
    @Test fun clips() {
        assumeTrue(on)
        val r = repo()
        r.update { it.copy(diamonds = 5000) }
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val which = androidx.compose.runtime.mutableIntStateOf(0)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CyberOperativeTheme {
                if (which.intValue == 0) com.cyberoperative.game.ui.menu.StoreScreen(r, AudioManager(ctx), {}, {})
                else com.cyberoperative.game.ui.menu.SkinsScreen(r, AudioManager(ctx)) {}
            }
        }
        for ((k, dir) in listOf(0 to "store_frames", 1 to "skin_frames")) {
            which.intValue = k
            compose.mainClock.advanceTimeBy(500)
            val out = File(System.getProperty("cyberop.previewDir") ?: "build/screens", "../../app/build/$dir").canonicalFile
            out.deleteRecursively(); out.mkdirs()
            val view = compose.activity.window.decorView
            for (f in 0 until 60) {
                compose.mainClock.advanceTimeBy(50)
                val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                compose.runOnUiThread { view.draw(Canvas(bmp)) }
                File(out, "f_%03d.png".format(f)).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }

    /** 0.51.0: the in-run INVENTORY's PETS tab (no incubator panel). */
    @Test fun inRunPets() {
        assumeTrue(on)
        val r = repo()
        val arts = listOf(Pets.NEW_BASE + 1150, Pets.NEW_BASE + 1050, Pets.NEW_BASE + 1260, Pets.NEW_BASE + 600, 17)
        val tiers = listOf(GearRarity.GLITCHED_ENCRYPTED, GearRarity.GLITCHED, GearRarity.ENCRYPTED, GearRarity.ENCRYPTED, GearRarity.EPIC)
        r.update { p -> p.copy(characterId = "c", characterName = "ZERO",
            pets = arts.mapIndexed { i, a -> Pet(i + 1L, a, tiers[i], fresh = false, eggArt = a) }, activePet = 1L,
            eggs = listOf(com.cyberoperative.game.data.PetEgg(20, GearRarity.FORGED, Pets.NEW_BASE + 100, Pets.NEW_BASE + 100),
                com.cyberoperative.game.data.PetEgg(21, GearRarity.GLITCHED, Pets.NEW_BASE + 5, Pets.NEW_BASE + 5))) }
        compose.mainClock.autoAdvance = false
        compose.setContent { CyberOperativeTheme { com.cyberoperative.game.ui.menu.CharacterScreen(r, {}, startTab = com.cyberoperative.game.ui.menu.CharTab.PETS, inRun = true) } }
        compose.mainClock.advanceTimeBy(1500)
        shot("v051_in_run_pets")
    }

    @Test fun menu() {
        assumeTrue(on)
        val r = repo()
        compose.mainClock.autoAdvance = false
        compose.setContent { CyberOperativeTheme { com.cyberoperative.game.ui.menu.MainMenuScreen(r.current) {} } }
        compose.mainClock.advanceTimeBy(1500)
        shot("v050_main_menu")
    }

    @Test fun skins() {
        assumeTrue(on)
        val r = repo()
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        compose.mainClock.autoAdvance = false
        compose.setContent { CyberOperativeTheme { com.cyberoperative.game.ui.menu.SkinsScreen(r, AudioManager(ctx)) {} } }
        compose.mainClock.advanceTimeBy(1500)
        shot("v050_skins")
    }

    @Test fun codex() {
        assumeTrue(on)
        val r = repo()
        val arts = listOf(Pets.NEW_BASE + 1050, Pets.NEW_BASE + 1150, Pets.NEW_BASE + 1260, Pets.NEW_BASE + 1000, Pets.NEW_BASE + 600, 3, 17, 240)
        val tiers = listOf(GearRarity.GLITCHED, GearRarity.GLITCHED_ENCRYPTED, GearRarity.ENCRYPTED, GearRarity.GLITCHED_ENCRYPTED, GearRarity.ENCRYPTED, GearRarity.RARE, GearRarity.EPIC, GearRarity.LEGENDARY)
        r.update { p -> p.copy(pets = arts.mapIndexed { i, a -> Pet(i + 1L, a, tiers[i], fresh = false, eggArt = if (a >= Pets.NEW_BASE) a else 30 + i) }) }
        compose.mainClock.autoAdvance = false
        compose.setContent { CyberOperativeTheme { com.cyberoperative.game.ui.menu.PetCodexScreen(r.current) {} } }
        compose.mainClock.advanceTimeBy(1500)
        shot("v050_pet_codex")
    }

    @Test fun codexPage() {
        assumeTrue(on)
        val art = Pets.NEW_BASE + 1150
        compose.mainClock.autoAdvance = false
        compose.setContent { CyberOperativeTheme { com.cyberoperative.game.ui.menu.PetDexPage(art, com.cyberoperative.game.data.PetDexEntry(art, GearRarity.GLITCHED_ENCRYPTED), 1.3f) {} } }
        compose.mainClock.advanceTimeBy(600)
        shot("v050_pet_codex_page")
    }

    @Test fun codexPageOriginal() {
        assumeTrue(on)
        compose.mainClock.autoAdvance = false
        compose.setContent { CyberOperativeTheme { com.cyberoperative.game.ui.menu.PetDexPage(17, com.cyberoperative.game.data.PetDexEntry(31, GearRarity.EPIC), 1.3f) {} } }
        compose.mainClock.advanceTimeBy(600)
        shot("v050_pet_codex_page_original")
    }
}
