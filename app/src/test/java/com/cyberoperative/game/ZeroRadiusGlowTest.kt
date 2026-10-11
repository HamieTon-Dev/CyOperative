package com.cyberoperative.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import com.cyberoperative.game.ui.common.SafeBrush
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 0.25.2 crash on every run start (owner's Galaxy S24+, Android 16): a just-dropped chip's glow
 * had radius 0 and Android threw "ending radius must be > 0". Every glow now goes through SafeBrush.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ZeroRadiusGlowTest {
    private val colors = listOf(Color.Green, Color.Transparent)
    private fun shader(b: Brush) = (b as ShaderBrush).createShader(Size(100f, 100f))

    @Test fun androidRejectsAZeroRadiusGradient() {
        assertThrows(IllegalArgumentException::class.java) { shader(Brush.radialGradient(colors, Offset(5f, 5f), 0f)) }
    }

    @Test fun safeGlowsNeverCrash() {
        for (r in listOf(0f, -3f, Float.NaN, 0.001f, 12f)) {
            assertNotNull(shader(SafeBrush.radialGradient(colors, Offset(5f, 5f), r)))
            assertNotNull(shader(SafeBrush.radialGradient(0f to Color.Red, 1f to Color.Transparent, center = Offset(5f, 5f), radius = r)))
        }
    }

    @Test fun noRawRadialGradientsLeftInTheGame() {
        val src = java.io.File("src/main/java")
        val offenders = src.walkTopDown().filter { it.extension == "kt" && it.name != "SafeBrush.kt" }
            .filter { "Brush.radialGradient(" in it.readText().replace("SafeBrush.radialGradient(", "") }
            .map { it.name }.toList()
        assert(offenders.isEmpty()) { "use SafeBrush.radialGradient in: $offenders" }
    }
}
