package com.cyberoperative.game

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.cyberoperative.game.data.GearRarity
import com.cyberoperative.game.data.Pet
import com.cyberoperative.game.data.Pets
import com.cyberoperative.game.save.SaveRepository
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

/** Frames of a full egg hatch (0.45.2) for docs/pets_new/hatch_animation.mp4 (tools/sfx/hatch_clip.sh). Opt-in. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xhdpi", sdk = [35])
class HatchClip {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun frames() = clip("hatch_frames", Pets.NEW_BASE + 500 + 3, GearRarity.ENCRYPTED)

    /** 0.49.0: a GLITCHED ENCRYPTED pet hatching (docs/pets_new/glitched_encrypted_hatch.gif). */
    @Test fun gxFrames() = clip("hatch_frames_gx", Pets.NEW_BASE + 1000 + 7, GearRarity.GLITCHED_ENCRYPTED)

    /** 0.49.1: the PETS list with some of the 300 new pets (docs/screenshots/pets_0491_in_game.png). */
    @Test fun newPetsInTheList() {
        assumeTrue(System.getProperty("cyberop.renderPreviews") == "true")
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repo = SaveRepository(ctx)
        val ks = listOf(1050, 1063, 1077, 1091, 1150, 1166, 1182, 1199, 1250, 1268, 1283, 1297)
        val tiers = listOf(GearRarity.GLITCHED, GearRarity.GLITCHED_ENCRYPTED, GearRarity.ENCRYPTED)
        repo.update { p -> p.copy(characterId = "c", characterName = "ZERO",
            pets = ks.mapIndexed { i, k -> Pet(i + 1L, Pets.NEW_BASE + k, tiers[i / 4], fresh = false, eggArt = Pets.NEW_BASE + k) }) }
        compose.mainClock.autoAdvance = false
        compose.setContent { CyberOperativeTheme { CharacterScreen(repo, {}, startTab = CharTab.PETS) } }
        compose.mainClock.advanceTimeBy(1400)
        val view = compose.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { view.draw(Canvas(bmp)) }
        val out = File(System.getProperty("cyberop.previewDir") ?: "build/screens", "pets_0491_in_game.png")
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun clip(dir: String, art: Int, rarity: GearRarity) {
        assumeTrue(System.getProperty("cyberop.renderPreviews") == "true")
        val out = File(System.getProperty("cyberop.previewDir") ?: "build/screens", "../../app/build/$dir").canonicalFile
        out.deleteRecursively(); out.mkdirs()
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repo = SaveRepository(ctx)
        repo.update { it.copy(characterId = "c", characterName = "ZERO", pets = listOf(Pet(1L, art, rarity, fresh = true, eggArt = art))) }
        compose.mainClock.autoAdvance = false
        compose.setContent { CyberOperativeTheme { CharacterScreen(repo, {}, startTab = CharTab.PETS) } }
        val view = compose.activity.window.decorView
        for (f in 0 until 150) {           // 5 s at 30 fps
            compose.mainClock.advanceTimeBy(33)
            val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            compose.runOnUiThread { view.draw(Canvas(bmp)) }
            File(out, "f_%03d.png".format(f)).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
