package com.cyberoperative.game.engine

import com.cyberoperative.game.core.MathUtil
import com.cyberoperative.game.core.Scaling
import com.cyberoperative.game.data.AiKind
import com.cyberoperative.game.data.BossDef
import com.cyberoperative.game.data.BossMove
import com.cyberoperative.game.data.Bosses
import com.cyberoperative.game.data.Enemies
import com.cyberoperative.game.data.EnemyDef
import com.cyberoperative.game.data.Pattern
import com.cyberoperative.game.data.ShapeKind
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Runtime state of the boss currently in the arena. */
class BossState(val def: BossDef, val cycle: Int) {
    var phaseIndex = 0
    var patternIndex = 0
    var rest = 1.6f
    var active: Pattern? = null
    var patternTime = 0f
    var counter = 0
    var subTimer = 0f
    var angleOffset = 0f
    var spiralAcc = 0f
    var chargeStage = 0
    var dirX = 0f
    var dirY = 0f
    var anchorX = 0f
    var anchorY = 0f
    var anchorTimer = 0f
    var driftAngle = 0f
    /** The entrance growl has played (see [BossBrain.INTRO_GROWL_AT]). */
    var growled = false
    /** *GLITCHED* boss: tougher, flickers, and fires extra random patterns. */
    var glitched = false
    var glitchTimer = 3f
    val displayName: String get() = if (glitched) "*GLITCHED* ${def.name}" else def.name

    val phase get() = def.phases[phaseIndex]
}

/**
 * Drives bosses (§22–25): phase selection by HP, movement style, and a
 * cycling list of telegraphed patterns per phase. Later roster cycles fire
 * patterns more often and hit harder, so boss difficulty scales endlessly.
 */
class BossBrain(private val g: GameEngine) {

    private val defCache = HashMap<String, EnemyDef>()

    private fun enemyDefFor(b: BossDef): EnemyDef = defCache.getOrPut(b.id) {
        EnemyDef(
            id = "boss_" + b.id, name = b.name, tag = b.tag, codex = b.codex,
            shape = ShapeKind.HEXAGON, color = b.color, radius = b.radius,
            baseHp = b.baseHp, baseSpeed = b.speed, contactDamage = b.contactDamage,
            ai = AiKind.CHASER, xp = 0f, euros = 0, score = 0, minLevel = 999, weight = 0f
        )
    }

    fun spawn(b: BossDef, x: Float, y: Float, glitched: Boolean = false) {
        val cycle = Bosses.cycleForLevel(g.level)
        val e = g.spawnEnemyAt(enemyDefFor(b), null, x, y, telegraph = true) ?: return
        e.stateTimer = INTRO_SECONDS
        val st = BossState(b, cycle)
        e.boss = st
        e.maxHp = b.baseHp * Scaling.bossHp(g.level) * (1f + 0.25f * cycle) * g.config.difficulty.enemyHp
        e.hp = e.maxHp
        e.radius = b.radius
        e.speed = b.speed * (1f + 0.05f * cycle)
        e.damageMul = Scaling.enemyDamage(g.level) * (1f + 0.1f * cycle) * g.config.difficulty.enemyDamage
        if (glitched) {
            st.glitched = true
            e.maxHp *= 1.35f
            e.hp = e.maxHp
            e.damageMul *= 1.1f
        }
        st.anchorX = x
        st.anchorY = y
        g.setBossRef(e)
        g.setBossPhaseLabel(b.phases[0].label)
    }

    fun update(dt: Float) {
        val e = g.boss ?: return
        if (!e.active) return
        if (g.phase != Phase.COMBAT) return
        val st = e.boss ?: return
        if (e.state == AiState.SPAWNING) {
            // Entrance: the bar fills, the name appears, then the growl.
            if (!st.growled && INTRO_SECONDS - e.stateTimer >= INTRO_GROWL_AT) {
                st.growled = true
                g.sound(GameSound.BOSS_GROWL)
                g.addPulse(e.x, e.y, 160f, 0.5f, st.def.color)
            }
            return
        }

        // Phase by HP.
        val frac = e.hp / e.maxHp
        var idx = 0
        for (i in st.def.phases.indices) if (frac <= st.def.phases[i].below) idx = i
        if (idx != st.phaseIndex) {
            st.phaseIndex = idx
            st.patternIndex = 0
            st.active = null
            st.rest = 1.0f
            g.setBossPhaseLabel(st.phase.label)
            g.showBanner(st.phase.label, st.displayName, 1.4f)
            g.addPulse(e.x, e.y, 200f, 0.6f, st.def.color)
            g.sound(GameSound.BOSS_PHASE)
            if (e.state == AiState.HIDDEN) e.state = AiState.MOVE
        }

        if (st.glitched) glitchBurst(e, st, dt)

        val dashing = st.active is Pattern.Charge && st.chargeStage == 1
        val teleporting = st.active is Pattern.Teleport
        if (!dashing && !teleporting && st.active !is Pattern.Charge) move(e, st, dt)

        val p = st.active
        if (p == null) {
            st.rest -= dt
            if (st.rest <= 0f) {
                val list = st.phase.patterns
                startPattern(e, st, list[st.patternIndex % list.size])
                st.patternIndex++
            }
        } else {
            st.patternTime += dt
            if (runPattern(e, st, p, dt)) {
                st.active = null
                st.rest = max(0.45f, st.phase.gap / (1f + 0.12f * st.cycle))
            }
        }
    }

    /** *GLITCHED* boss: on top of its own patterns, a random corrupted volley every few seconds. */
    private fun glitchBurst(e: Enemy, st: BossState, dt: Float) {
        st.glitchTimer -= dt
        if (st.glitchTimer > 0f) return
        st.glitchTimer = 2.8f + g.rng.nextFloat() * 1.6f
        val dmg = 10f * e.damageMul
        val aim = kotlin.math.atan2(g.py - e.y, g.px - e.x)
        when (g.rng.nextInt(3)) {
            0 -> for (i in 0 until 14) g.fireEnemyProjectile(e.x, e.y, aim + MathUtil.TWO_PI * i / 14, 180f, dmg, 8f, ProjKind.BOSS)
            1 -> for (i in 0 until 7) g.fireEnemyProjectile(e.x, e.y, aim + (i / 6f - 0.5f) * 1.6f, 230f, dmg, 8f, ProjKind.BOSS)
            else -> for (i in 0 until 3) g.fireEnemyProjectile(e.x, e.y, aim + (i - 1) * 0.1f, 320f, dmg * 1.2f, 9f, ProjKind.BOSS)
        }
        g.addPulse(e.x, e.y, 120f, 0.35f, 0xFFFF2EC4)
    }

    private fun move(e: Enemy, st: BossState, dt: Float) {
        val speed = e.speed * st.phase.speedMul
        when (st.def.move) {
            BossMove.CHASE -> g.enemyAiMove(e, g.px, g.py, speed, dt)
            BossMove.HOVER -> {
                st.anchorTimer -= dt
                if (st.anchorTimer <= 0f || MathUtil.dist(e.x, e.y, st.anchorX, st.anchorY) < 20f) {
                    pickAnchor(e, st)
                    st.anchorTimer = 2.5f
                }
                g.enemyAiMove(e, st.anchorX, st.anchorY, speed, dt)
            }
            BossMove.DRIFT -> {
                st.driftAngle += dt * 0.35f * st.phase.speedMul
                val cx = g.arena.width / 2f
                val cy = g.arena.height * 0.42f
                g.enemyAiMove(e, cx + cos(st.driftAngle) * 190f, cy + sin(st.driftAngle) * 150f, max(speed, 60f), dt)
            }
            BossMove.TELEPORT -> {}
        }
    }

    private fun pickAnchor(e: Enemy, st: BossState) {
        for (attempt in 0 until 20) {
            val x = 90f + g.rng.nextFloat() * (g.arena.width - 180f)
            val y = 120f + g.rng.nextFloat() * (g.arena.height * 0.55f)
            if (!g.arena.isFree(x, y, e.radius + 6f)) continue
            if (MathUtil.dist(x, y, g.px, g.py) < 240f) continue
            st.anchorX = x
            st.anchorY = y
            return
        }
        st.anchorX = g.arena.width / 2f
        st.anchorY = 260f
    }

    private fun startPattern(e: Enemy, st: BossState, p: Pattern) {
        st.active = p
        st.patternTime = 0f
        st.counter = 0
        st.subTimer = 0f
        st.spiralAcc = 0f
        st.chargeStage = 0
        when (p) {
            is Pattern.Summon -> {
                val def = Enemies.byId(p.enemyId)
                val n = p.count + st.cycle
                for (i in 0 until n) {
                    if (g.aliveCount() >= com.cyberoperative.game.core.Scaling.MAX_ALIVE) break
                    val a = MathUtil.TWO_PI * i / n
                    val c = g.spawnEnemyAt(def, null, e.x + cos(a) * (e.radius + 40f), e.y + sin(a) * (e.radius + 40f), telegraph = true)
                    c?.isChild = true
                }
            }
            is Pattern.ShockRing -> {
                g.addShockRing(e.x, e.y, p.maxRadius, p.speed, p.damage * e.damageMul, st.def.color)
                g.addPulse(e.x, e.y, 70f, 0.3f, st.def.color)
            }
            is Pattern.Blasts -> {
                for (i in 0 until p.count) {
                    val x: Float
                    val y: Float
                    if (p.targeted && i == 0) {
                        x = g.px; y = g.py
                    } else {
                        val a = g.rng.nextFloat() * MathUtil.TWO_PI
                        val d = 60f + g.rng.nextFloat() * 260f
                        x = MathUtil.clamp(g.px + cos(a) * d, 40f, g.arena.width - 40f)
                        y = MathUtil.clamp(g.py + sin(a) * d, 40f, g.arena.height - 40f)
                    }
                    g.addBlast(x, y, p.radius, p.delay, p.damage * e.damageMul, st.def.color)
                }
            }
            is Pattern.Zones -> {
                for (i in 0 until p.count) {
                    val x: Float
                    val y: Float
                    if (i == 0) { x = e.x; y = e.y } else {
                        x = 60f + g.rng.nextFloat() * (g.arena.width - 120f)
                        y = 60f + g.rng.nextFloat() * (g.arena.height - 120f)
                    }
                    g.addZone(x, y, p.radius, p.duration, p.dps * e.damageMul, 0xFF9B4DFF, telegraph = 0.9f)
                }
            }
            is Pattern.Beam -> {
                val base = atan2(g.py - e.y, g.px - e.x)
                val spread = Math.toRadians(p.spreadDeg.toDouble()).toFloat()
                for (i in 0 until p.count) {
                    val t = if (p.count == 1) 0f else i / (p.count - 1f) - 0.5f
                    g.addBeam(e.x, e.y, base + t * spread, 1600f, p.width, p.windup, p.duration, p.damage * e.damageMul, st.def.color)
                }
            }
            is Pattern.Homing -> {
                for (i in 0 until p.count) {
                    val a = atan2(g.py - e.y, g.px - e.x) + (i - (p.count - 1) / 2f) * 0.5f
                    val pr = g.fireEnemyProjectile(e.x, e.y, a, p.speed, p.damage * e.damageMul, 9f, ProjKind.BOSS)
                    if (pr != null) { pr.homing = p.turn; pr.life = p.life }
                }
            }
            is Pattern.Teleport -> {
                e.state = AiState.HIDDEN
                g.addPulse(e.x, e.y, 80f, 0.35f, st.def.color)
            }
            is Pattern.Charge -> {}
            is Pattern.Aimed, is Pattern.Radial, is Pattern.Spiral -> {}
        }
    }

    /** Returns true when the pattern has finished. */
    private fun runPattern(e: Enemy, st: BossState, p: Pattern, dt: Float): Boolean {
        val mul = g.plan.rules.enemyProjectileMul
        when (p) {
            is Pattern.Aimed -> {
                st.subTimer -= dt
                if (st.subTimer <= 0f) {
                    val base = atan2(g.py - e.y, g.px - e.x)
                    val n = p.count * mul
                    val spread = Math.toRadians(p.spreadDeg.toDouble()).toFloat()
                    for (i in 0 until n) {
                        val t = if (n == 1) 0f else i / (n - 1f) - 0.5f
                        g.fireEnemyProjectile(e.x, e.y, base + t * spread, p.speed, p.damage * e.damageMul, 8f, ProjKind.BOSS)
                    }
                    st.counter++
                    st.subTimer = p.burstGap
                }
                return st.counter >= p.bursts
            }
            is Pattern.Radial -> {
                st.subTimer -= dt
                if (st.subTimer <= 0f) {
                    val n = p.count * mul
                    val off = st.angleOffset
                    for (i in 0 until n) {
                        g.fireEnemyProjectile(e.x, e.y, off + MathUtil.TWO_PI * i / n, p.speed, p.damage * e.damageMul, 8f, ProjKind.BOSS)
                    }
                    st.angleOffset += Math.toRadians(p.rotateDeg.toDouble()).toFloat() + 0.07f
                    st.counter++
                    st.subTimer = p.waveGap
                }
                return st.counter >= p.waves
            }
            is Pattern.Spiral -> {
                st.spiralAcc += dt * p.shotsPerSecond
                val rot = Math.toRadians(p.degPerSecond.toDouble()).toFloat()
                while (st.spiralAcc >= 1f) {
                    st.spiralAcc -= 1f
                    for (a in 0 until p.arms) {
                        val ang = st.angleOffset + MathUtil.TWO_PI * a / p.arms
                        g.fireEnemyProjectile(e.x, e.y, ang, p.speed, p.damage * e.damageMul, 7f, ProjKind.BOSS)
                    }
                    st.angleOffset += rot / p.shotsPerSecond
                }
                return st.patternTime >= p.duration
            }
            is Pattern.Charge -> {
                when (st.chargeStage) {
                    0 -> {
                        if (st.subTimer == 0f) {
                            val ang = atan2(g.py - e.y, g.px - e.x)
                            st.dirX = cos(ang); st.dirY = sin(ang)
                            g.addLine(e.x, e.y, e.x + st.dirX * p.distance, e.y + st.dirY * p.distance, p.windup, 0xFFFFD426, e.uid)
                        }
                        st.subTimer += dt
                        if (st.subTimer >= p.windup) { st.chargeStage = 1; st.subTimer = 0f }
                    }
                    1 -> {
                        st.subTimer += dt
                        val nx = e.x + st.dirX * p.speed * dt
                        val ny = e.y + st.dirY * p.speed * dt
                        val blocked = g.arena.pushOut(nx, ny, e.radius)
                        e.x = g.arena.out[0]; e.y = g.arena.out[1]
                        if (blocked || st.subTimer * p.speed >= p.distance) {
                            st.chargeStage = 2; st.subTimer = 0f
                            g.addPulse(e.x, e.y, 90f, 0.3f, st.def.color)
                        }
                    }
                    else -> {
                        st.subTimer += dt
                        if (st.subTimer >= 0.45f) {
                            st.counter++
                            if (st.counter >= p.repeats) return true
                            st.chargeStage = 0; st.subTimer = 0f
                        }
                    }
                }
                return false
            }
            is Pattern.Teleport -> {
                if (st.patternTime >= 0.55f) {
                    pickAnchor(e, st)
                    e.x = st.anchorX; e.y = st.anchorY
                    e.state = AiState.MOVE
                    g.addPulse(e.x, e.y, 90f, 0.35f, st.def.color)
                    return true
                }
                return false
            }
            is Pattern.Beam -> return st.patternTime >= p.windup + p.duration
            is Pattern.Blasts -> return st.patternTime >= p.delay * 0.6f
            is Pattern.Summon, is Pattern.ShockRing, is Pattern.Zones, is Pattern.Homing -> return st.patternTime >= 0.4f
        }
    }

    fun onBossKilled(e: Enemy, st: BossState) {
        g.addPulse(e.x, e.y, 480f, 1.1f, st.def.color)
        g.addPulse(e.x, e.y, 260f, 0.7f, 0xFFFFFFFF)
        repeat(60) { g.addParticle(e.x, e.y, st.def.color, 360f, 1.2f, 4f) }
        g.sound(GameSound.BOSS_DEATH)
        g.showBanner("BOSS ELIMINATED", st.displayName, 2f)
        val glitchMul = if (st.glitched) 1.5f else 1f
        val euros = (st.def.euros * (1f + 0.04f * g.level) * glitchMul).toInt()
        g.onBossDefeated(euros, (Scoring.boss(st.def.score, g.level, st.cycle) * glitchMul).toInt(), bonusPicks = if (st.glitched) 1 else 0)
        g.setBossRef(null)
    }

    companion object {
        /**
         * Boss entrance timeline (owner, 2026-10-07): 0–1.4 s the health bar
         * grows from the centre and fills, 1.4–2.0 s the name glitches in,
         * 2.0 s the growl, then the fight starts at [INTRO_SECONDS]. The boss
         * cannot act or be hit until then.
         */
        const val INTRO_SECONDS = 3.2f
        const val INTRO_BAR_END = 1.4f
        const val INTRO_NAME_END = 2.0f
        const val INTRO_GROWL_AT = 2.0f
    }
}
