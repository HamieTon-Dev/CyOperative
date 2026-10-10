package com.cyberoperative.game

import com.cyberoperative.game.core.MathUtil
import com.cyberoperative.game.engine.GameEngine
import com.cyberoperative.game.engine.Phase
import kotlin.math.sqrt

/**
 * A simple automated player used to exercise the real game loop in tests:
 * dodge when something is close, otherwise stand still and shoot, walk toward
 * threats it can't see, and path to the access port when it opens.
 */
class Bot(private val g: GameEngine, private val dodge: Boolean = true) {
    private var repath = 0f
    private var picks = 0
    private var wpX = 0f
    private var wpY = 0f
    private var hasWp = false
    private var blindTime = 0f
    private var dodgeTimer = 0f
    private var dodgeX = 0f
    private var dodgeY = 0f

    fun step(dt: Float) {
        when (g.phase) {
            Phase.UPGRADE -> {
                // Like a player: alternate the weapon card (while a slot is free) with the first power-up.
                val pickWeapon = g.offer.size == 3 && !g.offerNeedsSlot(2) && (picks++ % 2 == 0)
                g.chooseUpgrade(if (pickWeapon) 2 else 0)
                g.setInput(0f, 0f)
            }
            Phase.PORTAL -> goTo(g.arena.portalX, g.arena.portalY, dt)
            Phase.COMBAT -> combat(dt)
            else -> g.setInput(0f, 0f)
        }
        g.update(dt)
    }

    private fun combat(dt: Float) {
        // Like a player: line up with a gap in Spectral Firewall's ring, then stand still and shoot.
        if (g.bossRingFilled >= 0 && !g.bossRingOut) {
            val gap = g.firewallGapPoint(2.2f)
            if (gap != null) {
                if (MathUtil.dist2(gap.first, gap.second, g.px, g.py) > 40f * 40f) goTo(gap.first, gap.second, dt) else g.setInput(0f, 0f)
                return
            }
        }
        // Like a player: go and unlock any key zone (Ransom King's shield).
        val key = g.hazards.items.filter { it.active && it.kind == com.cyberoperative.game.engine.HazardKind.KEY_ZONE }
            .minByOrNull { MathUtil.dist2(it.x, it.y, g.px, g.py) }
        if (key != null && g.operatives[0].rooted <= 0f) {
            if (MathUtil.dist2(key.x, key.y, g.px, g.py) > (key.radius * 0.5f) * (key.radius * 0.5f)) goTo(key.x, key.y, dt) else g.setInput(0f, 0f)
            return
        }
        if (dodge) {
            if (dodgeTimer > 0f) {
                dodgeTimer -= dt
                g.setInput(dodgeX, dodgeY)
                return
            }
            var tx = 0f
            var ty = 0f
            var threat = false
            for (p in g.projectiles.items) {
                if (!p.active || p.friendly) continue
                val d2 = MathUtil.dist2(p.x, p.y, g.px, g.py)
                if (d2 < 110f * 110f) {
                    // step perpendicular to the projectile's travel
                    tx += -p.vy; ty += p.vx
                    if ((g.px - p.x) * -p.vy + (g.py - p.y) * p.vx < 0f) { tx += 2 * p.vy; ty -= 2 * p.vx }
                    threat = true
                }
            }
            for (e in g.enemies.items) {
                if (!e.targetable) continue
                val d2 = MathUtil.dist2(e.x, e.y, g.px, g.py)
                if (d2 < 95f * 95f) { tx += g.px - e.x; ty += g.py - e.y; threat = true }
            }
            if (threat) {
                val l = sqrt(tx * tx + ty * ty).coerceAtLeast(0.001f)
                dodgeX = tx / l; dodgeY = ty / l
                dodgeTimer = 0.18f
                g.setInput(dodgeX, dodgeY)
                return
            }
        }
        if (g.acquireTarget()?.let { g.arena.lineOfSight(g.px, g.py, it.x, it.y, GameEngine.SHOT_CLEARANCE) } == true) {
            blindTime = 0f
            g.setInput(0f, 0f)
            return
        }
        blindTime += dt
        val nearest = g.nearestEnemy(g.px, g.py, 5000f)
        if (nearest != null && blindTime > 0.3f) goTo(nearest.x, nearest.y, dt) else g.setInput(0f, 0f)
    }

    private fun goTo(x: Float, y: Float, dt: Float) {
        repath -= dt
        if (repath <= 0f || !hasWp) {
            repath = 0.3f
            hasWp = nextWaypoint(x, y)
        }
        val dx = (if (hasWp) wpX else x) - g.px
        val dy = (if (hasWp) wpY else y) - g.py
        val l = sqrt(dx * dx + dy * dy)
        if (l < 4f) { g.setInput(0f, 0f); repath = 0f; return }
        g.setInput(dx / l, dy / l)
    }

    /** BFS on a coarse grid; sets the waypoint a few cells along the path. */
    private fun nextWaypoint(tx: Float, ty: Float): Boolean {
        val a = g.arena
        val cell = 24f
        val cols = (a.width / cell).toInt()
        val rows = (a.height / cell).toInt()
        fun free(r: Int, c: Int) = a.isFree(c * cell + cell / 2, r * cell + cell / 2, g.playerRadius)
        val sr = (g.py / cell).toInt().coerceIn(0, rows - 1)
        val sc = (g.px / cell).toInt().coerceIn(0, cols - 1)
        val gr = (ty / cell).toInt().coerceIn(0, rows - 1)
        val gc = (tx / cell).toInt().coerceIn(0, cols - 1)
        val prev = IntArray(rows * cols) { -1 }
        val seen = BooleanArray(rows * cols)
        val q = ArrayDeque<Int>()
        q.add(sr * cols + sc); seen[sr * cols + sc] = true
        var found = -1
        while (q.isNotEmpty()) {
            val cur = q.removeFirst()
            val r = cur / cols
            val c = cur % cols
            if (r == gr && c == gc) { found = cur; break }
            for (k in 0 until 4) {
                val nr = r + intArrayOf(1, -1, 0, 0)[k]
                val nc = c + intArrayOf(0, 0, 1, -1)[k]
                if (nr !in 0 until rows || nc !in 0 until cols) continue
                val idx = nr * cols + nc
                if (seen[idx]) continue
                if (!(nr == gr && nc == gc) && !free(nr, nc)) continue
                seen[idx] = true; prev[idx] = cur; q.add(idx)
            }
        }
        if (found < 0) return false
        val path = ArrayList<Int>()
        var cur = found
        while (cur != -1) { path.add(cur); cur = prev[cur] }
        path.reverse()
        val pick = path.getOrNull(3) ?: path.last()
        wpX = (pick % cols) * cell + cell / 2
        wpY = (pick / cols) * cell + cell / 2
        return true
    }
}
