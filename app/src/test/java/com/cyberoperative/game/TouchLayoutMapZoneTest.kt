package com.cyberoperative.game

import androidx.compose.ui.geometry.Offset
import com.cyberoperative.game.save.GameSettings
import com.cyberoperative.game.ui.game.TouchControl
import com.cyberoperative.game.ui.game.TouchLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** No touch control can sit on the MAP button's corner (owner, 2026-10-11). */
class TouchLayoutMapZoneTest {
    private fun overlapsMap(p: Offset, c: TouchControl, landscape: Boolean, w: Float, h: Float): Boolean {
        val half = c.sizeDp / 2f
        return p.x * w + half > w - TouchLayout.MAP_ZONE_W && p.y * h - half < TouchLayout.mapZoneH(landscape)
    }

    @Test fun controlsDraggedOntoTheMapCornerArePushedOut() {
        for (landscape in listOf(false, true)) {
            val (w, h) = if (landscape) 891f to 411f else 411f to 891f
            for (c in TouchControl.entries) for (x in listOf(0.7f, 0.85f, 0.99f)) for (y in listOf(0.01f, 0.1f, 0.2f)) {
                val out = TouchLayout.clearOfMap(Offset(x, y), c, landscape, w, h)
                assertTrue("$c at $x,$y landscape=$landscape → $out", !overlapsMap(out, c, landscape, w, h))
            }
        }
    }

    @Test fun defaultLayoutsAreUntouched() {
        val s = GameSettings()
        for (landscape in listOf(false, true)) {
            val (w, h) = if (landscape) 891f to 411f else 411f to 891f
            for (c in TouchControl.entries) {
                val a = TouchLayout.pos(s, c, landscape); val b = TouchLayout.place(s, c, landscape, w, h)
                assertEquals(a.x, b.x, 1e-6f); assertEquals(a.y, b.y, 1e-6f)
            }
        }
    }
}
