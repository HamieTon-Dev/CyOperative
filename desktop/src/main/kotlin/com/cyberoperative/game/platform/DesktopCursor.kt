package com.cyberoperative.game.platform

import com.cyberoperative.game.ui.common.CursorArt
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Cursor
import java.awt.Point
import java.awt.RenderingHints
import java.awt.Toolkit
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import java.awt.image.BufferedImage

/** The owner's neon arrow as the system cursor (menus and gameplay); the tip is the hotspot. */
object DesktopCursor {
    val cursor: Cursor by lazy { build() }

    private fun build(): Cursor = try {
        val tk = Toolkit.getDefaultToolkit()
        val best = tk.getBestCursorSize(48, 48)
        val side = if (best.width > 0) best.width else 32
        val img = BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        val pad = side * 0.06f
        val k = (side - pad * 2f) / 1050f
        fun poly(p: FloatArray) = Path2D.Float().apply {
            moveTo(pad + p[0] * k, pad + p[1] * k)
            var i = 2
            while (i < p.size) { lineTo(pad + p[i] * k, pad + p[i + 1] * k); i += 2 }
            closePath()
        }
        fun col(c: androidx.compose.ui.graphics.Color, a: Float = 1f) = Color(c.red, c.green, c.blue, a)
        val outer = poly(CursorArt.OUTER)
        g.color = col(CursorArt.NEON_BLUE, 0.45f); g.stroke = BasicStroke(side * 0.09f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND); g.draw(outer)
        g.color = col(CursorArt.NAVY); g.fill(outer)
        g.color = col(CursorArt.CYAN); g.stroke = BasicStroke(side * 0.05f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND); g.draw(outer)
        val inner = poly(CursorArt.INNER)
        g.color = Color(0x14, 0x25, 0x4A); g.fill(inner)
        g.color = col(CursorArt.GREEN); g.stroke = BasicStroke(side * 0.03f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        with(CursorArt.ACCENT_LEFT) { g.draw(Line2D.Float(pad + this[0] * k, pad + this[1] * k, pad + this[2] * k, pad + this[3] * k)) }
        with(CursorArt.ACCENT_RIGHT) { g.draw(Line2D.Float(pad + this[0] * k, pad + this[1] * k, pad + this[2] * k, pad + this[3] * k)) }
        val ex = pad + CursorArt.EYE_X * k
        val ey = pad + CursorArt.EYE_Y * k
        g.color = col(CursorArt.CYAN); g.stroke = BasicStroke(side * 0.03f)
        val rr = CursorArt.RING_R * k
        g.draw(Ellipse2D.Float(ex - rr, ey - rr, rr * 2, rr * 2))
        g.color = col(CursorArt.GREEN)
        val er = CursorArt.EYE_R * k
        g.fill(Ellipse2D.Float(ex - er, ey - er, er * 2, er * 2))
        g.dispose()
        tk.createCustomCursor(img, Point(pad.toInt(), pad.toInt()), "cyberop-arrow")
    } catch (_: Throwable) {
        Cursor.getDefaultCursor()
    }
}
