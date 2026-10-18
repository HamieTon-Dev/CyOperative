package com.cyberoperative.game

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.cyberoperative.game.data.GearItem
import com.cyberoperative.game.data.GearRarity
import com.cyberoperative.game.data.GearSlot
import com.cyberoperative.game.ui.common.GearIconView
import com.cyberoperative.game.ui.common.GearIcons
import com.cyberoperative.game.ui.common.rarityCard
import com.cyberoperative.game.ui.common.rarityColor
import com.cyberoperative.game.ui.theme.CyberOperativeTheme
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** GLITCHED ENCRYPTED (0.47.0): gear tiles, name, badge and the drop beam, as frames for docs/gear_gx/gx_animation.gif. Opt-in. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h600dp-xhdpi", sdk = [35])
class GxRenders {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun frames() {
        assumeTrue(System.getProperty("cyberop.renderPreviews") == "true")
        val out = File(System.getProperty("cyberop.previewDir") ?: "build/screens", "../../app/build/gx_frames").canonicalFile
        out.deleteRecursively(); out.mkdirs()
        val gx = GearRarity.GLITCHED_ENCRYPTED
        val pieces = GearSlot.entries.mapIndexed { i, s -> GearItem(i.toLong(), s, gx, i * 7 % 50, 60, 100f) } +
            (0 until 5).map { k -> GearItem(100L + k, GearSlot.PRIMARY, gx, 50 + k * 50 + 3, 60, 100f) }
        val t = mutableFloatStateOf(0f)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CyberOperativeTheme {
                Column(Modifier.fillMaxSize().background(Color(0xFF05080F)).padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val c = rarityColor(gx)
                    Text(pieces[8].name, color = c, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.rarityCard(gx, corner = 4.dp).border(1.dp, c, RoundedCornerShape(4.dp)).padding(horizontal = 10.dp, vertical = 3.dp)) {
                        Text(gx.label, color = c, style = MaterialTheme.typography.labelLarge)
                    }
                    Spacer(Modifier.height(10.dp))
                    for (row in pieces.chunked(5)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            for (p in row) GearIconView(p, Modifier.size(64.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Spacer(Modifier.height(30.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        for (r in listOf(GearRarity.GLITCHED, GearRarity.ENCRYPTED, gx)) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Canvas(Modifier.size(110.dp, 150.dp)) {
                            val tt = t.floatValue
                            with(GearIcons) {
                                drawLootBeam(r, size.width / 2f, size.height * 0.9f, tt, 1f)
                                drawGear(GearSlot.CORE, r, 2, size.width / 2f, size.height * 0.78f, 60.dp.toPx())
                            }
                        }
                        Text(r.label, color = rarityColor(r), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        GearIcons.atlas(GearSlot.CORE); for (s in GearSlot.entries) GearIcons.gxAtlas(s)
        val view = compose.activity.window.decorView
        for (f in 0 until 90) {          // 3 s at 30 fps
            t.floatValue = f / 30f
            compose.mainClock.advanceTimeBy(33)
            val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            compose.runOnUiThread { view.draw(android.graphics.Canvas(bmp)) }
            File(out, "f_%03d.png".format(f)).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
