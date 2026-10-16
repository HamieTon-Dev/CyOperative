package com.cyberoperative.game

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.cyberoperative.game.data.InfernoBosses
import com.cyberoperative.game.ui.game.BossBodyPreview
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** INFERNO bosses in motion: frame sequences per pack (idle, then a wind-up); `-PrenderPreviews` only. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1000dp-h240dp-mdpi", sdk = [35])
class InfernoBossAnim {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun alpha() = run("alpha", InfernoBosses.alpha)
    @Test fun beta() = run("beta", InfernoBosses.beta)
    @Test fun gamma() = run("gamma", InfernoBosses.gamma)

    private fun run(name: String, pack: List<com.cyberoperative.game.data.BossDef>) {
        assumeTrue(System.getProperty("cyberop.renderPreviews") == "true")
        val out = File(System.getProperty("cyberop.previewDir") ?: "build/screens", "anim_$name").also { it.mkdirs() }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            var t by remember { mutableFloatStateOf(0f) }
            LaunchedEffect(Unit) { val s0 = withFrameNanos { it }; while (true) withFrameNanos { t = (it - s0) / 1e9f } }
            Row(Modifier.fillMaxSize().background(Color(0xFF140A0A))) {
                for (b in pack) Box(Modifier.weight(1f).fillMaxHeight()) {
                    BossBodyPreview(b, 1, t, Modifier.fillMaxSize(), winding = t % 4f > 2.8f)
                }
            }
        }
        for (f in 0 until 40) {
            compose.mainClock.advanceTimeBy(100)
            val view = compose.activity.window.decorView
            val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            compose.runOnUiThread { view.draw(Canvas(bmp)) }
            File(out, "f%02d.png".format(f)).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
