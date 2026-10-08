package com.cyberoperative.game.engine

import com.cyberoperative.game.core.MathUtil
import com.cyberoperative.game.data.AiKind
import com.cyberoperative.game.data.AttackKind
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Behaviour for regular enemies (§43). Each [AiKind] is a small state machine
 * over [AiState]; every attack has a visible windup so the player can read it.
 * Bosses are driven separately by [BossBrain] but share movement/contact code.
 */
class EnemyAi(private val g: GameEngine) {

    companion object {
        /** Seconds between path re-plans per enemy (staggered by spawn time). */
        const val NAV_REFRESH = 0.12f
    }

    fun update(dt: Float) {
        val phase = g.phase
        if (phase != Phase.COMBAT) return
        for (e in g.enemies.items) {
            if (!e.active) continue
            // Co-op: each threat goes after the closest operative still standing.
            g.focusNearest(e.x, e.y)
            if (e.hitFlash > 0f) e.hitFlash -= dt
            if (e.contactCooldown > 0f) e.contactCooldown -= dt
            if (e.state == AiState.SPAWNING) {
                e.stateTimer -= dt
                if (e.stateTimer <= 0f) e.state = AiState.MOVE
                continue
            }
            if (e.boss != null) {
                contact(e)
                continue
            }
            when (e.def.ai) {
                AiKind.CHASER -> chase(e, dt, 1f, 0f)
                AiKind.SWARMER -> chase(e, dt, 1f, 0.9f)
                AiKind.SHOOTER -> shooter(e, dt, sniper = false)
                AiKind.SNIPER -> shooter(e, dt, sniper = true)
                AiKind.CHARGER -> charger(e, dt)
                AiKind.TURRET -> turret(e, dt)
                AiKind.TELEPORTER -> teleporter(e, dt)
                AiKind.GLITCHED -> glitched(e, dt)
            }
            contact(e)
        }
        separate()
    }

    // --- Movement helpers ---------------------------------------------------

    /**
     * Move toward a point, sliding along obstacles. When cover blocks the
     * straight line to the player, follow the [Pathfinder] flow field around
     * it instead of pushing into the wall; a short sidestep remains as a last
     * resort if something still pins the enemy.
     */
    fun moveToward(e: Enemy, tx: Float, ty: Float, speed: Float, dt: Float) {
        var dx = tx - e.x
        var dy = ty - e.y
        e.navTimer -= dt
        if (e.navTimer <= 0f) {
            e.navTimer = NAV_REFRESH
            e.navValid = false
            // Only player-bound moves use the field (it leads to the player).
            val towardPlayer = MathUtil.dist2(tx, ty, g.px, g.py) < 170f * 170f
            if (towardPlayer && !g.arena.lineOfSight(e.x, e.y, tx, ty, e.radius * 0.9f) && g.path.steer(e.x, e.y, e.radius)) {
                e.navX = g.path.dir[0]
                e.navY = g.path.dir[1]
                e.navValid = true
            }
        }
        if (e.navValid) {
            dx = e.navX
            dy = e.navY
        }
        if (e.detourTimer > 0f) {
            e.detourTimer -= dt
            dx = e.detourX
            dy = e.detourY
        }
        val d = sqrt(dx * dx + dy * dy)
        if (d < 1f && !e.navValid && e.detourTimer <= 0f) return
        if (d < 1e-4f) return
        val nx = e.x + dx / d * speed * dt
        val ny = e.y + dy / d * speed * dt
        g.arena.pushOut(nx, ny, e.radius)
        e.x = g.arena.out[0]
        e.y = g.arena.out[1]

        // Stuck detection: barely moved for a while while trying to move.
        e.stuckTimer += dt
        if (e.stuckTimer >= 0.4f) {
            val moved = MathUtil.dist(e.x, e.y, e.lastX, e.lastY)
            if (moved < speed * 0.4f * 0.25f && e.detourTimer <= 0f) {
                // Slide perpendicular to the desired direction for a moment,
                // toward whichever side is closer to the player by path.
                val px1 = e.x - dy / d * 40f
                val py1 = e.y + dx / d * 40f
                val px2 = e.x + dy / d * 40f
                val py2 = e.y - dx / d * 40f
                val c1 = g.path.costAt(px1, py1)
                val c2 = g.path.costAt(px2, py2)
                val side = when {
                    c1 < c2 -> 1f
                    c2 < c1 -> -1f
                    else -> if (g.rng.nextBoolean()) 1f else -1f
                }
                e.detourX = -dy / d * side
                e.detourY = dx / d * side
                e.detourTimer = 0.45f
                e.navTimer = 0f
            }
            e.lastX = e.x
            e.lastY = e.y
            e.stuckTimer = 0f
        }
    }

    private fun chase(e: Enemy, dt: Float, speedMul: Float, wobble: Float) {
        var tx = g.px
        var ty = g.py
        if (wobble > 0f) {
            e.wobble += dt * 6f
            val ang = atan2(g.py - e.y, g.px - e.x)
            val off = sin(e.wobble) * 60f * wobble
            tx += -sin(ang) * off
            ty += cos(ang) * off
        }
        moveToward(e, tx, ty, e.speed * speedMul, dt)
    }

    private fun contact(e: Enemy) {
        if (e.contactCooldown > 0f || !e.targetable) return
        val rr = e.radius + g.playerRadius * 0.85f
        if (MathUtil.dist2(e.x, e.y, g.px, g.py) < rr * rr) {
            val dmg = (e.boss?.def?.contactDamage ?: e.def.contactDamage) * e.damageMul
            g.damagePlayer(dmg, e.x, e.y)
            e.contactCooldown = 0.8f
        }
    }

    private fun separate() {
        val items = g.enemies.items
        for (i in items.indices) {
            val a = items[i]
            if (!a.active || a.state == AiState.SPAWNING || a.state == AiState.HIDDEN) continue
            for (j in i + 1 until items.size) {
                val b = items[j]
                if (!b.active || b.state == AiState.SPAWNING || b.state == AiState.HIDDEN) continue
                val dx = b.x - a.x
                val dy = b.y - a.y
                val min = (a.radius + b.radius) * 0.9f
                val d2 = dx * dx + dy * dy
                if (d2 >= min * min || d2 < 1e-4f) continue
                val d = sqrt(d2)
                val push = (min - d) * 0.5f
                val ux = dx / d
                val uy = dy / d
                val aHeavy = a.boss != null
                val bHeavy = b.boss != null
                if (!aHeavy) { a.x -= ux * push * (if (bHeavy) 2f else 1f); a.y -= uy * push * (if (bHeavy) 2f else 1f) }
                if (!bHeavy) { b.x += ux * push * (if (aHeavy) 2f else 1f); b.y += uy * push * (if (aHeavy) 2f else 1f) }
            }
        }
    }

    // --- Archetypes ---------------------------------------------------------

    private fun shooter(e: Enemy, dt: Float, sniper: Boolean) {
        val def = e.def
        val dist = MathUtil.dist(e.x, e.y, g.px, g.py)
        val los = g.arena.lineOfSight(e.x, e.y, g.px, g.py, 4f)
        when (e.state) {
            AiState.MOVE -> {
                val want = def.preferredRange
                if (!los || dist > want + 50f) {
                    moveToward(e, g.px, g.py, e.speed, dt)
                } else if (dist < want - 80f) {
                    // Back away from the player.
                    moveToward(e, e.x * 2f - g.px, e.y * 2f - g.py, e.speed * 0.8f, dt)
                } else {
                    // Strafe around the player at range.
                    val ang = atan2(e.y - g.py, e.x - g.px) + e.strafeDir * 0.6f
                    moveToward(e, g.px + cos(ang) * want, g.py + sin(ang) * want, e.speed * 0.6f, dt)
                    if (g.rng.nextFloat() < dt * 0.3f) e.strafeDir = -e.strafeDir
                }
                e.attackTimer -= dt * e.attackRateMul
                if (e.attackTimer <= 0f && los && dist < want + 160f) {
                    e.state = AiState.WINDUP
                    e.stateTimer = def.windup
                    e.aimX = g.px
                    e.aimY = g.py
                    if (sniper) g.addLine(e.x, e.y, g.px, g.py, def.windup, 0xFFFF2D55, e.uid)
                }
            }
            AiState.WINDUP -> {
                e.stateTimer -= dt
                // Snipers track for most of the windup, then lock (dodgeable).
                if (sniper && e.stateTimer > def.windup * 0.3f) {
                    e.aimX = g.px
                    e.aimY = g.py
                    updateOwnLine(e)
                } else if (!sniper) {
                    e.aimX = g.px
                    e.aimY = g.py
                }
                if (e.stateTimer <= 0f) {
                    fireAttack(e, atan2(e.aimY - e.y, e.aimX - e.x))
                    e.state = AiState.MOVE
                    e.attackTimer = def.attackCooldown * (0.85f + g.rng.nextFloat() * 0.3f)
                }
            }
            else -> e.state = AiState.MOVE
        }
    }

    private fun updateOwnLine(e: Enemy) {
        for (h in g.hazards.items) {
            if (h.active && h.kind == HazardKind.LINE && h.ownerUid == e.uid) {
                h.x = e.x; h.y = e.y
                // Extend the sight line past the player so it reads as a lane.
                val ang = atan2(e.aimY - e.y, e.aimX - e.x)
                h.x2 = e.x + cos(ang) * 900f
                h.y2 = e.y + sin(ang) * 900f
            }
        }
    }

    private fun charger(e: Enemy, dt: Float) {
        val def = e.def
        val dist = MathUtil.dist(e.x, e.y, g.px, g.py)
        when (e.state) {
            AiState.MOVE -> {
                moveToward(e, g.px, g.py, e.speed, dt)
                e.attackTimer -= dt * e.attackRateMul
                if (e.attackTimer <= 0f && dist < def.preferredRange && g.arena.lineOfSight(e.x, e.y, g.px, g.py, e.radius * 0.5f)) {
                    e.state = AiState.WINDUP
                    e.stateTimer = def.windup
                    val ang = atan2(g.py - e.y, g.px - e.x)
                    val len = def.preferredRange * 1.5f
                    e.aimX = cos(ang)
                    e.aimY = sin(ang)
                    g.addLine(e.x, e.y, e.x + e.aimX * len, e.y + e.aimY * len, def.windup, 0xFFFFD426, e.uid)
                }
            }
            AiState.WINDUP -> {
                e.stateTimer -= dt
                if (e.stateTimer <= 0f) {
                    e.state = AiState.DASH
                    e.stateTimer = def.preferredRange * 1.5f / def.projectileSpeed
                }
            }
            AiState.DASH -> {
                e.stateTimer -= dt
                val sp = def.projectileSpeed
                val nx = e.x + e.aimX * sp * dt
                val ny = e.y + e.aimY * sp * dt
                val blocked = g.arena.pushOut(nx, ny, e.radius)
                e.x = g.arena.out[0]
                e.y = g.arena.out[1]
                if (e.stateTimer <= 0f || blocked) {
                    e.state = AiState.RECOVER
                    e.stateTimer = 0.6f
                }
            }
            AiState.RECOVER -> {
                e.stateTimer -= dt
                if (e.stateTimer <= 0f) {
                    e.state = AiState.MOVE
                    e.attackTimer = def.attackCooldown
                }
            }
            else -> e.state = AiState.MOVE
        }
    }

    private fun turret(e: Enemy, dt: Float) {
        val def = e.def
        when (e.state) {
            AiState.MOVE -> {
                e.attackTimer -= dt * e.attackRateMul
                if (e.attackTimer <= 0f) {
                    e.state = AiState.WINDUP
                    e.stateTimer = def.windup
                }
            }
            AiState.WINDUP -> {
                e.stateTimer -= dt
                if (e.stateTimer <= 0f) {
                    e.wobble += 0.3f
                    // Ring turrets rotate their pattern; aimed / spread turrets track the player.
                    val aim = if (def.attack == AttackKind.RADIAL) e.wobble else atan2(g.py - e.y, g.px - e.x)
                    fireAttack(e, aim)
                    e.state = AiState.MOVE
                    e.attackTimer = def.attackCooldown
                }
            }
            else -> e.state = AiState.MOVE
        }
    }

    private fun teleporter(e: Enemy, dt: Float) {
        val def = e.def
        when (e.state) {
            AiState.MOVE -> {
                e.attackTimer -= dt * e.attackRateMul
                if (e.attackTimer <= 0f) {
                    e.state = AiState.HIDDEN
                    e.stateTimer = 0.5f
                }
            }
            AiState.HIDDEN -> {
                e.stateTimer -= dt
                if (e.stateTimer <= 0f) {
                    // Reappear at range, somewhere with a clear shot.
                    for (attempt in 0 until 16) {
                        val a = g.rng.nextFloat() * MathUtil.TWO_PI
                        val x = g.px + cos(a) * def.preferredRange
                        val y = g.py + sin(a) * def.preferredRange
                        if (g.arena.isFree(x, y, e.radius + 4f) && g.arena.lineOfSight(x, y, g.px, g.py)) {
                            e.x = x; e.y = y
                            break
                        }
                    }
                    e.state = AiState.WINDUP
                    e.stateTimer = def.windup
                    g.addPulse(e.x, e.y, 50f, 0.3f, def.color)
                }
            }
            AiState.WINDUP -> {
                e.stateTimer -= dt
                e.aimX = g.px; e.aimY = g.py
                if (e.stateTimer <= 0f) {
                    fireAttack(e, atan2(e.aimY - e.y, e.aimX - e.x))
                    e.state = AiState.MOVE
                    e.attackTimer = def.attackCooldown
                }
            }
            else -> e.state = AiState.MOVE
        }
    }

    /**
     * *GLITCHED*: strafes at range like a shooter, but each attack rolls a
     * different pattern (aimed burst, wide spread, ring, or blink + spread).
     */
    private fun glitched(e: Enemy, dt: Float) {
        val def = e.def
        val dist = MathUtil.dist(e.x, e.y, g.px, g.py)
        val los = g.arena.lineOfSight(e.x, e.y, g.px, g.py, 4f)
        when (e.state) {
            AiState.MOVE -> {
                val want = def.preferredRange
                if (!los || dist > want + 60f) moveToward(e, g.px, g.py, e.speed, dt)
                else {
                    val ang = atan2(e.y - g.py, e.x - g.px) + e.strafeDir * 0.8f
                    moveToward(e, g.px + cos(ang) * want, g.py + sin(ang) * want, e.speed * 0.8f, dt)
                    if (g.rng.nextFloat() < dt * 0.6f) e.strafeDir = -e.strafeDir
                }
                e.attackTimer -= dt * e.attackRateMul
                if (e.attackTimer <= 0f && (los || g.rng.nextFloat() < 0.3f)) {
                    e.glitchMode = g.rng.nextInt(4)
                    if (e.glitchMode == 3) {
                        e.state = AiState.HIDDEN
                        e.stateTimer = 0.35f
                    } else {
                        e.state = AiState.WINDUP
                        e.stateTimer = def.windup
                    }
                }
            }
            AiState.HIDDEN -> {
                e.stateTimer -= dt
                if (e.stateTimer <= 0f) {
                    for (attempt in 0 until 16) {
                        val a = g.rng.nextFloat() * MathUtil.TWO_PI
                        val x = g.px + cos(a) * def.preferredRange
                        val y = g.py + sin(a) * def.preferredRange
                        if (g.arena.isFree(x, y, e.radius + 4f) && g.arena.lineOfSight(x, y, g.px, g.py)) {
                            e.x = x; e.y = y
                            break
                        }
                    }
                    g.addPulse(e.x, e.y, 50f, 0.3f, 0xFFFF2EC4)
                    e.state = AiState.WINDUP
                    e.stateTimer = def.windup
                }
            }
            AiState.WINDUP -> {
                e.stateTimer -= dt
                if (e.stateTimer <= 0f) {
                    val angle = atan2(g.py - e.y, g.px - e.x)
                    when (e.glitchMode) {
                        0 -> firePattern(e, angle, AttackKind.AIMED, 3, 0f)
                        1 -> firePattern(e, angle, AttackKind.SPREAD, 6, 80f)
                        2 -> firePattern(e, angle, AttackKind.RADIAL, 10, 0f)
                        else -> firePattern(e, angle, AttackKind.SPREAD, 4, 45f)
                    }
                    e.state = AiState.MOVE
                    e.attackTimer = def.attackCooldown * (0.7f + g.rng.nextFloat() * 0.6f)
                }
            }
            else -> e.state = AiState.MOVE
        }
    }

    private fun fireAttack(e: Enemy, angle: Float) =
        firePattern(e, angle, e.def.attack, e.def.projectileCount, e.def.spreadDegrees)

    private fun firePattern(e: Enemy, angle: Float, kind: AttackKind, count: Int, spreadDegrees: Float) {
        val def = e.def
        val dmg = def.projectileDamage * e.damageMul
        val mul = g.plan.rules.enemyProjectileMul
        when (kind) {
            AttackKind.NONE -> {}
            AttackKind.AIMED -> {
                val n = count * mul
                for (i in 0 until n) {
                    val off = if (n == 1) 0f else (i - (n - 1) / 2f) * 0.12f
                    g.fireEnemyProjectile(e.x, e.y, angle + off, def.projectileSpeed, dmg, def.projectileRadius, ProjKind.ENEMY)
                }
            }
            AttackKind.SPREAD -> {
                val n = count * mul
                val spread = Math.toRadians(spreadDegrees.toDouble()).toFloat() * (if (mul > 1) 1.4f else 1f)
                for (i in 0 until n) {
                    val t = if (n == 1) 0f else i / (n - 1f) - 0.5f
                    g.fireEnemyProjectile(e.x, e.y, angle + t * spread, def.projectileSpeed, dmg, def.projectileRadius, ProjKind.ENEMY)
                }
            }
            AttackKind.RADIAL -> {
                val n = count * mul
                for (i in 0 until n) {
                    g.fireEnemyProjectile(e.x, e.y, angle + MathUtil.TWO_PI * i / n, def.projectileSpeed, dmg, def.projectileRadius, ProjKind.ENEMY)
                }
            }
        }
    }
}
