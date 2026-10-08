package com.cyberoperative.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.ColorDrawable
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.cyberoperative.game.ui.common.CyberTitleLogo
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Exports the CYBER OPERATIVE wordmark as large PNGs (transparent and on dark)
 * for store art and marketing. The in-game title draws the same vectors live.
 * Run: ./gradlew testDebugUnitTest --tests '*LogoRender*' -PrenderPreviews
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w800dp-h400dp-xxxhdpi", sdk = [35])
class LogoRender {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val enabled = System.getProperty("cyberop.renderPreviews") == "true"
    private val outDir = File(System.getProperty("cyberop.previewDir") ?: "build/screens").resolveSibling("brand")

    private fun render(name: String, background: Color?) {
        assumeTrue(enabled)
        compose.runOnUiThread { compose.activity.window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT)) }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val m = Modifier.fillMaxWidth()
            Box(if (background != null) m.background(background) else m) { CyberTitleLogo(Modifier.fillMaxWidth()) }
        }
        compose.mainClock.advanceTimeBy(100)
        outDir.mkdirs()
        val view = compose.activity.window.decorView
        val content = view.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(content.width, content.height, Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { content.draw(Canvas(bmp)) }
        // Crop to the logo's own height (it keeps its aspect ratio at full width).
        val h = (bmp.width * 420f / 1000f).toInt().coerceAtMost(bmp.height)
        val out = Bitmap.createBitmap(bmp, 0, 0, bmp.width, h)
        File(outDir, "$name.png").outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun emblem() {
        assumeTrue(enabled)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Box(Modifier.background(Color(0xFF04080F))) {
                com.cyberoperative.game.ui.common.OperativeEmblem(
                    com.cyberoperative.game.data.OperativeSkins.byId("neon_operative_skin_unused"),
                    Modifier.size(400.dp), animate = false
                )
            }
        }
        compose.mainClock.advanceTimeBy(100)
        outDir.mkdirs()
        val content = compose.activity.window.decorView.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(content.width, content.height, Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { content.draw(Canvas(bmp)) }
        val side = minOf(bmp.width, bmp.height)
        val out = Bitmap.createBitmap(bmp, 0, 0, side, side)
        File(outDir, "menu_emblem.png").outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun transparent() = render("title_logo", null)
    @Test fun onDark() = render("title_logo_dark", Color(0xFF04080F))
}
