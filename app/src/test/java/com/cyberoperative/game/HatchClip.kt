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

    @Test fun frames() {
        assumeTrue(System.getProperty("cyberop.renderPreviews") == "true")
        val out = File(System.getProperty("cyberop.previewDir") ?: "build/screens", "../../app/build/hatch_frames").canonicalFile
        out.deleteRecursively(); out.mkdirs()
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repo = SaveRepository(ctx)
        val art = Pets.NEW_BASE + 500 + 3
        repo.update { it.copy(characterId = "c", characterName = "ZERO", pets = listOf(Pet(1L, art, GearRarity.ENCRYPTED, fresh = true, eggArt = art))) }
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
