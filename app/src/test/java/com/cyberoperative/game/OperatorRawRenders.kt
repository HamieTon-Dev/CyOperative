package com.cyberoperative.game

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.cyberoperative.game.data.OperativeSkins
import com.cyberoperative.game.save.CharacterLook
import com.cyberoperative.game.ui.game.BodyStyle
import com.cyberoperative.game.ui.game.OperativeFigures
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The real operator figures, big and on a transparent background, for the redesign concept
 * (tools/operators/shade_operators.py adds the pets' shading on top). Opt-in: -PrenderPreviews.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class OperatorRawRenders {
    private val out = File(System.getProperty("cyberop.previewDir") ?: "build/screens", "../operators_concept/raw")

    private var dir = out

    private fun render(name: String, style: BodyStyle, skin: com.cyberoperative.game.data.OperativeSkin, look: CharacterLook? = null) {
        val px = 480
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp.asImageBitmap())
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, Size(px.toFloat(), px.toFloat())) {
            val u = px / 80f * 0.92f
            with(OperativeFigures) { drawFigure(style, skin, px / 2f, px * 0.9f, u, -0.4f, false, 0f, look = look) }
        }
        dir.mkdirs()
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun raw() {
        assumeTrue(System.getProperty("cyberop.renderPreviews") == "true")
        // raw/: the flat originals (input for tools/operators/shade_operators.py); ingame/: the shipped look.
        for (shaded in listOf(false, true)) {
            com.cyberoperative.game.ui.common.FigureShade.enabled = shaded
            dir = if (shaded) File(out.parentFile, "ingame") else out
            renderAll()
        }
        com.cyberoperative.game.ui.common.FigureShade.enabled = true
    }

    private fun renderAll() {
        val styles = listOf("agent" to BodyStyle.AGENT, "technician" to BodyStyle.NEON, "juggernaut" to BodyStyle.MECH, "phantom" to BodyStyle.RUNNER, "mage" to BodyStyle.MAGE)
        val skins = listOf("default" to OperativeSkins.DEFAULT, "titanium" to OperativeSkins.TITANIUM, "purple" to OperativeSkins.PURPLE, "gold" to OperativeSkins.GOLD) +
            OperativeSkins.all.filter { it.id in setOf("red_hat", "ids", "blue_hat") }.map { it.id to it }
        for ((n, st) in styles) for ((sn, sk) in skins) render("${n}_$sn", st, sk)
        for (k in 0..2) {
            render("agent_head$k", BodyStyle.AGENT, OperativeSkins.DEFAULT, CharacterLook(head = k))
            render("agent_face$k", BodyStyle.AGENT, OperativeSkins.DEFAULT, CharacterLook(face = k))
            render("agent_legs$k", BodyStyle.AGENT, OperativeSkins.DEFAULT, CharacterLook(legs = k))
        }
    }
}
