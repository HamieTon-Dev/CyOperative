package com.cyberoperative.game

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.sp
import com.cyberoperative.game.data.Bosses
import com.cyberoperative.game.ui.game.BossBodyPreview
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** The 12 classic bodies on a 0.1 grid of their preview square (rig placement); `-PrenderPreviews` only. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1200dp-h900dp-mdpi", sdk = [35])
class ClassicBossRenders {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun grid() {
        assumeTrue(System.getProperty("cyberop.renderPreviews") == "true")
        val out = File(System.getProperty("cyberop.previewDir") ?: "build/screens").also { it.mkdirs() }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Column(Modifier.fillMaxSize().background(Color(0xFF101418))) {
                for (row in Bosses.classics.chunked(4)) Row(Modifier.fillMaxWidth()) {
                    for (b in row) Box(Modifier.weight(1f).aspectRatio(1f)) {
                        BossBodyPreview(b, 0, 1.3f, Modifier.fillMaxSize())
                        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                            val side = size.minDimension
                            val x0 = (size.width - side) / 2f; val y0 = (size.height - side) / 2f
                            for (k in 0..10) {
                                val c = if (k == 5) Color(0x8800E5FF) else Color(0x3300E5FF)
                                drawLine(c, androidx.compose.ui.geometry.Offset(x0 + side * k / 10f, y0), androidx.compose.ui.geometry.Offset(x0 + side * k / 10f, y0 + side))
                                drawLine(c, androidx.compose.ui.geometry.Offset(x0, y0 + side * k / 10f), androidx.compose.ui.geometry.Offset(x0 + side, y0 + side * k / 10f))
                            }
                        }
                        Text(b.id, color = Color.White, fontSize = 11.sp)
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(200)
        val view = compose.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { view.draw(Canvas(bmp)) }
        File(out, "classic_grid.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
