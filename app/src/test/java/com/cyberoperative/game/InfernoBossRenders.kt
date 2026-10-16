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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyberoperative.game.data.InfernoBosses
import com.cyberoperative.game.ui.game.BossBodyPreview
import com.cyberoperative.game.ui.theme.CyberOperativeTheme
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 0.35.0 INFERNO packs: one approval sheet per pack (each boss in phases 1–3); `-PrenderPreviews` only. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w900dp-h1100dp-xhdpi", sdk = [35])
class InfernoBossRenders {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun alpha() = sheet("alpha", InfernoBosses.alpha)
    @Test fun beta() = sheet("beta", InfernoBosses.beta)
    @Test fun gamma() = sheet("gamma", InfernoBosses.gamma)

    private fun sheet(name: String, pack: List<com.cyberoperative.game.data.BossDef>) {
        assumeTrue(System.getProperty("cyberop.renderPreviews") == "true")
        val out = File(System.getProperty("cyberop.previewDir") ?: "build/screens").also { it.mkdirs() }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CyberOperativeTheme {
                Column(Modifier.fillMaxSize().background(Color(0xFF0A0606)).padding(8.dp)) {
                    for (b in pack) Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Text(b.name, color = Color(b.color), fontSize = 13.sp, modifier = Modifier.weight(0.8f))
                        for (ph in 0..2) Box(Modifier.weight(1f).height(210.dp)) {
                            BossBodyPreview(b, ph, 1.3f + ph * 0.7f, Modifier.fillMaxSize(), winding = ph == 2)
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(300)
        val view = compose.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { view.draw(Canvas(bmp)) }
        File(out, "inferno_pack_$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
