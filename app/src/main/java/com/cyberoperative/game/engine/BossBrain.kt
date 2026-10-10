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
    /** Stealth bosses: eyes open (lockable) and the time left in this window. */
    var eyesOpen = false
    var eyeTimer = 2.5f
    /** The fight-opening EMP blackout has fired. */
    var blackedOut = false
    /** Operatives already hit by the current dash (bit per operative). */
    var dashHits = 0
    /** Ransom shield (Ransom King): up, key zones still to capture, seconds until it reforms. */
    var shieldUp = true
    var keysLeft = 0
    var shieldTimer = 0f
    /** Spectral Firewall: orbiting ring angle and the slab-touch burn cooldown. */
    var ringAngle = 0f
    var ringTouch = 0f
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

    /** The enemy body a boss uses (also for co-op mirrors). */
    internal fun defFor(b: BossDef): EnemyDef = enemyDefFor(b)

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
        e.maxHp = b.baseHp * Scaling.bossHp(g.level) * (1f + 0.25f * cycle) * g.config.difficulty.enemyHp * g.opHpNow * g.adaptiveHp * g.config.coopBossHpMul
        e.hp = e.maxHp
        e.radius = b.radius
        e.speed = b.speed * (1f + 0.05f * cycle)
        e.damageMul = Scaling.enemyDamage(g.level) * (1f + 0.1f * cycle) * g.config.difficulty.enemyDamage * g.opDamageNow * g.adaptiveDamage
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
        g.focusNearest(e.x, e.y)
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
            g.fx.hitStop(0.08f)
            g.fx.flash(st.def.color, 0.28f, 0.35f)
            g.fx.shake(6f, 0.4f)
            if (e.state == AiState.HIDDEN) e.state = AiState.MOVE
        }

        if (st.glitched) glitchBurst(e, st, dt)
        if (st.def.stealth) stealth(e, st, dt)
        if (st.def.keyShield) ransomShield(e, st, dt)
        if (st.def.firewallRing) firewallRing(e, st, dt)

        val dashing = (st.active is Pattern.Charge || st.active is Pattern.GhostDash || st.active is Pattern.DashSlash || st.active is Pattern.BacklineDive) && st.chargeStage >= 1
        val teleporting = st.active is Pattern.Teleport
        if (!dashing && !teleporting && st.active !is Pattern.Charge && st.active !is Pattern.Burrow &&
            st.active !is Pattern.GhostDash && st.active !is Pattern.SparkAmbush && st.active !is Pattern.DashSlash &&
            st.active !is Pattern.BacklineDive) move(e, st, dt)

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

    /**
     * Nullshade's room rules: an EMP blackout as the fight opens, then eye
     * windows — it can only be locked on (and hit) while its eyes are open.
     * Windows get shorter but more frequent each phase.
     */
    private fun stealth(e: Enemy, st: BossState, dt: Float) {
        if (!st.blackedOut) {
            st.blackedOut = true
            g.blackout(e.x, e.y, st.def.color)
            g.showBanner("EMP BLACKOUT", "Lock on only while its eyes are open", 1.8f)
            st.eyesOpen = false
            st.eyeTimer = 2.0f
        }
        st.eyeTimer -= dt
        if (st.eyeTimer <= 0f) {
            st.eyesOpen = !st.eyesOpen
            st.eyeTimer = when (st.phaseIndex) {
                0 -> if (st.eyesOpen) 2.8f else 2.6f
                1 -> if (st.eyesOpen) 2.0f else 1.9f
                else -> if (st.eyesOpen) 1.7f else 1.5f
            }
            if (st.eyesOpen) g.addPulse(e.x, e.y - e.radius, 60f, 0.3f, 0xFFFF2A3A)
        }
        e.untargetable = !st.eyesOpen
        val target = if (st.eyesOpen) 0f else 1f
        g.bossVeil += (target - g.bossVeil) * kotlin.math.min(1f, dt * 6f)
    }

    /**
     * Ransom shield: while up he takes a quarter damage. Capturing every key
     * zone of a Key Zone wave breaks it for [DECRYPT_SECONDS] (he takes extra
     * damage), then it reforms.
     */
    private fun ransomShield(e: Enemy, st: BossState, dt: Float) {
        if (!st.shieldUp) {
            st.shieldTimer -= dt
            if (st.shieldTimer <= 0f) {
                st.shieldUp = true
                g.addPulse(e.x, e.y, e.radius * 2.2f, 0.5f, st.def.color)
                g.showBanner("RE-ENCRYPTED", "Capture the key zones to break his shield", 1.3f)
            }
        }
        e.damageTakenMul = if (st.shieldUp) SHIELD_DAMAGE_MUL else DECRYPTED_DAMAGE_MUL
        g.bossShield = if (st.shieldUp) 1f else 0f
    }

    /** Plates in the orbiting ring this phase: 3 gaps, then 2, then 1. */
    private fun ringFilled(st: BossState) = (GameEngine.FIREWALL_SLOTS - 3 + st.phaseIndex).coerceAtMost(GameEngine.FIREWALL_SLOTS - 1)

    /**
     * Spectral Firewall's orbiting ring: turns a little faster each phase, is gone
     * while its Firewall Ring is out (exposed window), and burns anyone touching a plate.
     */
    private fun firewallRing(e: Enemy, st: BossState, dt: Float) {
        st.ringAngle = MathUtil.wrapAngle(st.ringAngle + dt * (0.5f + 0.15f * st.phaseIndex))
        g.bossRingAngle = st.ringAngle
        g.bossRingFilled = ringFilled(st)
        g.bossRingOut = g.hazards.items.any { it.active && it.kind == HazardKind.FIRE_WALL && it.ownerUid == e.uid && it.maxRadius > it.angle }
        if (st.ringTouch > 0f) st.ringTouch -= dt
        if (g.bossRingOut || st.ringTouch > 0f || e.state == AiState.SPAWNING) return
        val ringR = e.radius * GameEngine.FIREWALL_RING_SCALE
        var touched = false
        g.forEachOperativeHit(e.x, ringCenterY(e), ringR * 1.3f, 0) { _ ->
            val nd = ringDist(e, g.px, g.py)
            if (kotlin.math.abs(nd - ringR) < g.playerRadius * 1.2f && onPlate(st, ringParam(e, g.px, g.py))) {
                g.damagePlayer(st.def.contactDamage * 0.5f * e.damageMul, e.x, e.y)
                g.ignite(GameEngine.BURN_SECONDS, st.def.contactDamage * 0.3f * e.damageMul)
                touched = true
            }
        }
        if (touched) st.ringTouch = 0.6f
    }

    // The ring is drawn in perspective (an ellipse squashed by RING_SQUASH around a point just
    // below the boss), so every gameplay check uses that same ellipse: what you see is what blocks.

    private fun ringCenterY(e: Enemy) = e.y + e.radius * 0.8f - 18f

    /** Angle parameter on the ring's ellipse for a world point. */
    private fun ringParam(e: Enemy, x: Float, y: Float) = atan2((y - ringCenterY(e)) / RING_SQUASH, x - e.x)

    /** Distance from the ring centre with the squash undone (compare with the ring radius). */
    private fun ringDist(e: Enemy, x: Float, y: Float): Float {
        val dx = x - e.x
        val dy = (y - ringCenterY(e)) / RING_SQUASH
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    /** True if ring angle [a] is covered by a plate (not a gap). */
    private fun onPlate(st: BossState, a: Float): Boolean {
        val slot = MathUtil.TWO_PI / GameEngine.FIREWALL_SLOTS
        var rel = MathUtil.wrapAngle(a - st.ringAngle)
        if (rel < 0f) rel += MathUtil.TWO_PI
        val i = (rel / slot).toInt()
        val frac = rel / slot - i
        return i < ringFilled(st) && frac < PLATE_SPAN
    }

    /** Would a hit from ([x], [y]) be soaked up by the orbiting ring? (Not from inside it, nor while it's launched.) */
    internal fun ringBlocks(e: Enemy, x: Float, y: Float): Boolean {
        val st = e.boss ?: return false
        if (!st.def.firewallRing || g.bossRingOut) return false
        if (ringDist(e, x, y) <= e.radius * GameEngine.FIREWALL_RING_SCALE) return false
        return onPlate(st, ringParam(e, x, y))
    }

    /** Sparks where a blocked shot hit the ring. */
    internal fun ringSpark(e: Enemy, x: Float, y: Float) {
        val a = ringParam(e, x, y)
        val ringR = e.radius * GameEngine.FIREWALL_RING_SCALE
        if (g.rng.nextFloat() < 0.5f) g.addParticle(e.x + cos(a) * ringR, ringCenterY(e) + sin(a) * ringR * RING_SQUASH - 20f, 0xFFFFD45A, 160f, 0.3f, 2.5f)
    }

    /** A world point lined up with the middle of a ring gap, [scale] × the ring radius out (bot, tests). */
    internal fun ringGapPoint(e: Enemy, scale: Float): Pair<Float, Float>? {
        val st = e.boss ?: return null
        val slot = MathUtil.TWO_PI / GameEngine.FIREWALL_SLOTS
        val filled = ringFilled(st)
        if (filled >= GameEngine.FIREWALL_SLOTS) return null
        val ringR = e.radius * GameEngine.FIREWALL_RING_SCALE * scale
        // Pick the gap whose point is nearest the operative.
        return (filled until GameEngine.FIREWALL_SLOTS).map { i ->
            val a = st.ringAngle + (i + 0.5f) * slot
            (e.x + cos(a) * ringR) to (ringCenterY(e) + sin(a) * ringR * RING_SQUASH)
        }.minByOrNull { MathUtil.dist2(it.first, it.second, g.px, g.py) }
    }

    /** A key zone was unlocked; the last one of the wave breaks the shield. */
    internal fun keyCaptured() {
        val e = g.boss ?: return
        val st = e.boss ?: return
        if (!st.def.keyShield || !st.shieldUp) return
        st.keysLeft--
        if (st.keysLeft > 0) return
        st.shieldUp = false
        st.shieldTimer = DECRYPT_SECONDS
        g.addPulse(e.x, e.y, e.radius * 3f, 0.6f, 0xFFFFC233)
        repeat(40) { g.addParticle(e.x, e.y - e.radius, 0xFFFFC233, 360f, 0.8f, 3.5f) }
        g.fx.hitStop(0.08f)
        g.fx.flash(0xFFFFC233, 0.3f, 0.35f)
        g.fx.shake(8f, 0.4f)
        g.showBanner("DECRYPTED", "Shield down — hit him now", 1.4f)
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
            is Pattern.CoverDeploy -> deployCover(e, st, p)
            is Pattern.Lockdown -> lockdown(e, p)
            is Pattern.SweepBeam -> {
                val aim = atan2(g.py - e.y, g.px - e.x)
                val sweep = Math.toRadians(p.sweepDeg.toDouble()).toFloat() * (if (g.rng.nextBoolean()) 1f else -1f)
                for (i in 0 until p.count) {
                    // One beam starts to one side of you and crosses you; more beams spread evenly.
                    val start = aim - sweep / 2f + MathUtil.TWO_PI * i / p.count
                    g.addSweep(e.x, e.y, start, sweep, p.width, p.windup, p.duration, p.damage * e.damageMul, st.def.color, e.uid)
                }
            }
            is Pattern.Mortar -> {}
            is Pattern.Burrow -> {
                g.addPulse(e.x, e.y, e.radius * 1.6f, 0.45f, st.def.color)
                repeat(24) { g.addParticle(e.x, e.y, st.def.color, 260f, 0.6f, 3f) }
            }
            is Pattern.SpikeEruption -> {
                val aim = atan2(g.py - e.y, g.px - e.x)
                val spread = Math.toRadians(p.spreadDeg.toDouble()).toFloat()
                for (l in 0 until p.lines) {
                    val a = if (p.lines >= 8) aim + MathUtil.TWO_PI * l / p.lines
                    else aim + (if (p.lines == 1) 0f else (l / (p.lines - 1f) - 0.5f) * spread)
                    for (k in 0 until p.spikes) {
                        val d = e.radius + 30f + k * p.spacing
                        g.addSpike(e.x + cos(a) * d, e.y + sin(a) * d, p.radius, p.delay + k * p.step, p.damage * e.damageMul, st.def.color)
                    }
                }
            }
            is Pattern.Infect -> {
                g.addZone(g.px, g.py, p.radius, p.duration, p.dps * e.damageMul, st.def.color, telegraph = p.telegraph, kind = HazardKind.INFECTED)
                for ((x, y) in g.dwellHotspots(p.count - 1, p.radius * 2f, g.px, g.py)) {
                    g.addZone(x, y, p.radius, p.duration, p.dps * e.damageMul, st.def.color, telegraph = p.telegraph, kind = HazardKind.INFECTED)
                }
            }
            is Pattern.GhostDash, is Pattern.Needles, is Pattern.SparkAmbush, is Pattern.GridSurge -> {}
            is Pattern.KeyZone -> keyZones(e, st, p)
            is Pattern.LockGrid -> lockGrid(e, p)
            is Pattern.RoyalSeizure -> {
                st.anchorX = g.px; st.anchorY = g.py
                g.addBlast(g.px, g.py, p.radius, p.delay, p.damage * e.damageMul, 0xFFFFC233)
                g.addPulse(e.x, e.y - e.radius, 70f, 0.3f, 0xFFFFC233)
            }
            is Pattern.RansomPulse -> {}
            is Pattern.FirewallRing -> {
                g.addPulse(e.x, e.y, e.radius * 2f, p.windup, st.def.color)
                g.fx.shake(3f, p.windup)
            }
            is Pattern.BurnSector -> {
                val aim = atan2(g.py - e.y, g.px - e.x)
                val half = Math.toRadians(p.widthDeg / 2.0).toFloat()
                val reach = kotlin.math.hypot(g.arena.width, g.arena.height)
                for (k in 0 until p.count) {
                    val a = aim + MathUtil.TWO_PI * k / p.count
                    g.addBurnSector(e.x, e.y, a, half, reach, p.warn, p.burn, p.dps * e.damageMul, st.def.color)
                }
            }
            is Pattern.HeatCollapse -> {
                val far = kotlin.math.hypot(g.arena.width, g.arena.height)
                val gapHalf = 0.32f
                g.addFireWall(e.x, e.y, far, e.radius * 1.2f, p.speed, p.gaps, atan2(g.py - e.y, g.px - e.x) + 1.2f, gapHalf, p.damage * e.damageMul, st.def.color, e.uid)
                g.showBanner("HEAT COLLAPSE", "Slip through a gap", 1.0f)
            }
            is Pattern.LineWarp -> lineWarp(e, st, p)
            is Pattern.DashSlash, is Pattern.BacklineDive -> {}
            is Pattern.SwarmHatch -> {
                for (k in 0 until p.eggs) {
                    val a = MathUtil.TWO_PI * k / p.eggs + g.rng.nextFloat() * 0.5f
                    val d = e.radius + 70f + g.rng.nextFloat() * 120f
                    g.addEgg(MathUtil.clamp(e.x + cos(a) * d, 40f, g.arena.width - 40f), MathUtil.clamp(e.y + sin(a) * d, 40f, g.arena.height - 40f), p.hatch, p.perEgg + st.cycle, st.def.color)
                }
                g.addPulse(e.x, e.y, e.radius * 2f, 0.4f, st.def.color)
            }
            is Pattern.CorruptionTrail -> { st.spiralAcc = 0f }
            is Pattern.QueenRoar -> {
                g.addShockRing(e.x, e.y, p.maxRadius, p.speed, p.damage * e.damageMul, st.def.color)
                g.addPulse(e.x, e.y, e.radius * 2.5f, 0.5f, 0xFFFFFFFF)
                g.fx.shake(8f, 0.45f)
                for (m in g.enemies.items) {
                    if (!m.active || m.boss != null) continue
                    if (m.hasteTimer <= 0f) { m.hasteMul = p.haste; m.speed *= p.haste }
                    m.hasteTimer = p.seconds
                }
                g.showBanner("QUEEN ROAR", "Her swarm surges", 1.0f)
            }
            is Pattern.Scythes -> {
                val aim = atan2(g.py - e.y, g.px - e.x)
                val spread = Math.toRadians(p.spreadDeg.toDouble()).toFloat()
                for (k in 0 until p.count) {
                    val t = if (p.count == 1) 0f else k / (p.count - 1f) - 0.5f
                    // Alternate the side each scythe curls to, so they cross.
                    val bulge = (if (k % 2 == 0) 1f else -1f) * p.range * 0.45f
                    g.addScythe(e.x, e.y, aim + t * spread, p.range, bulge, p.flight, p.damage * e.damageMul, st.def.color)
                }
            }
            is Pattern.CrossBeam -> {
                val rot = Math.toRadians(p.rotateDeg.toDouble()).toFloat() * (if (g.rng.nextBoolean()) 1f else -1f)
                val n = if (p.diagonal) 8 else 4
                for (k in 0 until n) g.addSweep(e.x, e.y, MathUtil.TWO_PI * k / n, rot, p.width, p.windup, p.duration, p.damage * e.damageMul, st.def.color, e.uid)
                g.addPulse(e.x, e.y, 90f, p.windup, st.def.color)
            }
            is Pattern.BishopMines -> {
                for (k in 0 until p.count) {
                    // A ring around where you are, one right under you; they arm before you can blink.
                    val a = MathUtil.TWO_PI * k / p.count + g.rng.nextFloat() * 0.4f
                    val d = if (k == 0) 0f else 90f + g.rng.nextFloat() * 160f
                    val x = MathUtil.clamp(g.px + cos(a) * d, 40f, g.arena.width - 40f)
                    val y = MathUtil.clamp(g.py + sin(a) * d, 40f, g.arena.height - 40f)
                    g.addMine(x, y, p.arm, p.life, p.radius, p.damage * e.damageMul, st.def.color)
                }
            }
            is Pattern.ConvergenceFlash -> {
                g.forEachOperativeHit(e.x, e.y, 9999f, 0) { _ -> g.pull(e.x, e.y, p.pull, p.strength) }
                g.addPulse(e.x, e.y, p.radius * 1.4f, p.pull, 0xFFFFFFFF)
                g.showBanner("CONVERGENCE", "Pull away — get out of the blast", 1.0f)
            }
            is Pattern.PurgeSpin -> {
                val sweep = Math.toRadians(p.sweepDeg.toDouble()).toFloat() * (if (g.rng.nextBoolean()) 1f else -1f)
                val aim = atan2(g.py - e.y, g.px - e.x) + 0.6f
                for (k in 0 until p.arms) {
                    // tick = 1 flags the jet as fire: it sets you burning.
                    g.addSweep(e.x, e.y, aim + MathUtil.TWO_PI * k / p.arms, sweep, 34f, p.windup, p.duration, p.damage * e.damageMul, st.def.color, e.uid)?.tick = 1f
                }
            }
            is Pattern.Bloom -> bloom(e.x, e.y, p.rings, p.lanes, p.delay, p.ringGap, p.radius, p.damage * e.damageMul, st.def.color)
        }
    }

    /**
     * Line Warp: jumps onto the operative's row or column, 260–420 units away,
     * leaving a light streak, then fires a beam back down the lane.
     */
    private fun lineWarp(e: Enemy, st: BossState, p: Pattern.LineWarp) {
        var bx = e.x; var by = e.y
        for (attempt in 0 until 16) {
            val vertical = g.rng.nextBoolean()
            val d = (260f + g.rng.nextFloat() * 160f) * (if (g.rng.nextBoolean()) 1f else -1f)
            val x = if (vertical) g.px else g.px + d
            val y = if (vertical) g.py + d else g.py
            if (x < e.radius + 20f || x > g.arena.width - e.radius - 20f || y < e.radius + 60f || y > g.arena.height - e.radius - 20f) continue
            if (!g.arena.isFree(x, y, e.radius)) continue
            bx = x; by = y; break
        }
        g.addLine(e.x, e.y, bx, by, 0.45f, 0xFFFFFFFF, e.uid)
        repeat(20) { k -> val q = k / 20f; g.addParticle(e.x + (bx - e.x) * q, e.y + (by - e.y) * q, st.def.color, 60f, 0.5f, 3f) }
        g.addPulse(e.x, e.y, 80f, 0.3f, st.def.color)
        e.x = bx; e.y = by
        st.anchorX = bx; st.anchorY = by
        g.addPulse(bx, by, 110f, 0.35f, 0xFFFFFFFF)
        g.addBeam(bx, by, atan2(g.py - by, g.px - bx), 1600f, 30f, p.beamWindup, 0.3f, p.damage * e.damageMul, st.def.color)
    }

    /** Golden key zones spread around the arena, away from walls and each other. */
    private fun keyZones(e: Enemy, st: BossState, p: Pattern.KeyZone) {
        if (!st.shieldUp) return
        for (h in g.hazards.items) if (h.active && h.kind == HazardKind.KEY_ZONE) h.active = false
        var placed = 0
        var tries = 0
        val spots = ArrayList<Pair<Float, Float>>()
        while (placed < p.count && tries++ < 60) {
            val x = 80f + g.rng.nextFloat() * (g.arena.width - 160f)
            val y = 140f + g.rng.nextFloat() * (g.arena.height - 260f)
            if (!g.arena.isFree(x, y, p.radius * 0.6f)) continue
            if (MathUtil.dist(x, y, e.x, e.y) < e.radius + p.radius + 60f) continue
            if (spots.any { MathUtil.dist(x, y, it.first, it.second) < p.radius * 3f }) continue
            spots += x to y
            g.addKeyZone(x, y, p.radius, p.duration, 0xFFFFC233)
            placed++
        }
        st.keysLeft = placed
        if (placed > 0) g.showBanner("KEY ZONES", "Stand in them to break his shield", 1.2f)
    }

    /**
     * Walls of padlocked cubes across the arena near the operative, alternating
     * horizontal and vertical, each with [Pattern.LockGrid.gaps] openings three
     * cubes wide, never through the operative.
     */
    private fun lockGrid(e: Enemy, p: Pattern.LockGrid) {
        val size = CUBE * 2f
        for (line in 0 until p.lines) {
            val horizontal = line % 2 == 0
            val side = if (g.rng.nextBoolean()) 1f else -1f
            var off = side * (95f + g.rng.nextFloat() * 90f + (line / 2) * 120f)
            val length = if (horizontal) g.arena.width else g.arena.height
            // Keep the wall inside the room: if it would land past a wall, put it on the other side of you.
            val span = if (horizontal) g.arena.height else g.arena.width
            val at = (if (horizontal) g.py else g.px) + off
            if (at < 90f || at > span - 90f) off = -off
            val n = (length / size).toInt()
            val gapAt = IntArray(p.gaps) { 1 + g.rng.nextInt((n - 4).coerceAtLeast(1)) }
            for (k in 0 until n) {
                if (gapAt.any { k >= it && k < it + 3 }) continue
                val along = (k + 0.5f) * size
                val x = if (horizontal) along else g.px + off
                val y = if (horizontal) g.py + off else along
                g.addBarrier(x, y, CUBE, p.rise, p.life, clearance = 6f, style = Barrier.STYLE_LOCK)
            }
        }
        g.addPulse(e.x, e.y, 160f, 0.4f, 0xFFFF3B3B)
    }

    /** Padlock cage around a Royal Seizure impact, open on the side away from the king. */
    private fun seizureCage(e: Enemy, cx: Float, cy: Float, life: Float) {
        val size = CUBE * 2f
        val inner = 95f
        val reach = inner + CUBE
        val perSide = ((reach * 2f) / size).toInt() + 1
        val away = atan2(cy - e.y, cx - e.x)
        val preferred = (((away / (MathUtil.TWO_PI / 4f)).let { kotlin.math.round(it).toInt() } % 4) + 4) % 4
        val order = intArrayOf(preferred, (preferred + 1) % 4, (preferred + 3) % 4, (preferred + 2) % 4)
        val openSide = order.firstOrNull { side ->
            val ex = cx + SIDE_X[side] * (reach + 70f)
            val ey = cy + SIDE_Y[side] * (reach + 70f)
            g.arena.isFree(ex, ey, g.playerRadius + 4f) && g.arena.lineOfSight(cx, cy, ex, ey, g.playerRadius)
        } ?: return
        for (side in 0 until 4) for (k in 0 until perSide) {
            val t = -reach + k * (reach * 2f / (perSide - 1))
            if (side == openSide && kotlin.math.abs(t) < size * 1.1f) continue
            val (x, y) = when (side) {
                0 -> (cx + reach) to (cy + t)
                1 -> (cx + t) to (cy + reach)
                2 -> (cx - reach) to (cy + t)
                else -> (cx + t) to (cy - reach)
            }
            g.addBarrier(x, y, CUBE, 0.35f, life, clearance = 4f, style = Barrier.STYLE_LOCK)
        }
    }

    /**
     * Rings of spikes around ([cx], [cy]), each ring a little later than the one
     * inside it, with [lanes] straight safe lanes cut through all of them.
     */
    private fun bloom(cx: Float, cy: Float, rings: Int, lanes: Int, delay: Float, ringGap: Float, radius: Float, damage: Float, color: Long) {
        val laneAngles = FloatArray(lanes) { g.rng.nextFloat() * MathUtil.TWO_PI }
        for (ring in 1..rings) {
            val rr = 60f + ring * 88f
            val n = kotlin.math.ceil(MathUtil.TWO_PI * rr / (radius * 1.9f)).toInt()
            val laneHalf = (g.playerRadius * 2.6f) / rr
            for (k in 0 until n) {
                val a = MathUtil.TWO_PI * k / n
                if (laneAngles.any { kotlin.math.abs(angleDiff(a, it)) < laneHalf }) continue
                g.addSpike(cx + cos(a) * rr, cy + sin(a) * rr, radius, delay + (ring - 1) * ringGap, damage, color)
            }
        }
    }

    private fun angleDiff(a: Float, b: Float): Float {
        var d = (a - b) % MathUtil.TWO_PI
        if (d > Math.PI) d -= MathUtil.TWO_PI
        if (d < -Math.PI) d += MathUtil.TWO_PI
        return d
    }

    /** Ghost Dash: 0 telegraph line, 1 dash (hits once per operative, trail of corruption), 2 recover. */
    private fun runGhostDash(e: Enemy, st: BossState, p: Pattern.GhostDash, dt: Float): Boolean {
        when (st.chargeStage) {
            0 -> {
                if (st.subTimer == 0f) {
                    val ang = atan2(g.py - e.y, g.px - e.x)
                    st.dirX = cos(ang); st.dirY = sin(ang)
                    g.addLine(e.x, e.y, e.x + st.dirX * p.distance, e.y + st.dirY * p.distance, p.windup, st.def.color, e.uid)
                    st.dashHits = 0
                    st.spiralAcc = 0f
                }
                st.subTimer += dt
                if (st.subTimer >= p.windup) { st.chargeStage = 1; st.subTimer = 0f }
            }
            1 -> {
                st.subTimer += dt
                val step = p.speed * dt
                val nx = e.x + st.dirX * step
                val ny = e.y + st.dirY * step
                val blocked = g.arena.pushOut(nx, ny, e.radius)
                e.x = g.arena.out[0]; e.y = g.arena.out[1]
                // Faint afterimage and a short corruption trail.
                st.spiralAcc += step
                if (st.spiralAcc >= 55f) {
                    st.spiralAcc = 0f
                    g.addZone(e.x, e.y, 30f, 2.4f, 10f * e.damageMul, st.def.color, telegraph = 0.15f, kind = HazardKind.INFECTED)
                    repeat(4) { g.addParticle(e.x, e.y - e.radius * 0.5f, st.def.color, 50f, 0.7f, 5f) }
                }
                g.forEachOperativeHit(e.x, e.y, e.radius + g.playerRadius * 0.6f, st.dashHits) { bit ->
                    st.dashHits = st.dashHits or bit
                    g.damagePlayer(p.damage * e.damageMul, e.x, e.y)
                }
                if (blocked || st.subTimer * p.speed >= p.distance) {
                    st.chargeStage = 2; st.subTimer = 0f
                    g.addPulse(e.x, e.y, 90f, 0.3f, st.def.color)
                }
            }
            else -> {
                st.subTimer += dt
                if (st.subTimer >= 0.35f) {
                    st.counter++
                    if (st.counter >= p.repeats) return true
                    st.chargeStage = 0; st.subTimer = 0f
                }
            }
        }
        return false
    }

    /** Dash Slash: 0 telegraph, 1 dash (hits once; with trail, drops delayed bursts), 2 crescent slash + recover. */
    private fun runDashSlash(e: Enemy, st: BossState, p: Pattern.DashSlash, dt: Float): Boolean {
        when (st.chargeStage) {
            0 -> {
                if (st.subTimer == 0f) {
                    val ang = atan2(g.py - e.y, g.px - e.x)
                    st.dirX = cos(ang); st.dirY = sin(ang)
                    g.addLine(e.x, e.y, e.x + st.dirX * p.distance, e.y + st.dirY * p.distance, p.windup, st.def.color, e.uid)
                    st.dashHits = 0; st.spiralAcc = 0f
                }
                st.subTimer += dt
                if (st.subTimer >= p.windup) { st.chargeStage = 1; st.subTimer = 0f }
            }
            1 -> {
                st.subTimer += dt
                val step = p.speed * dt
                val blocked = g.arena.pushOut(e.x + st.dirX * step, e.y + st.dirY * step, e.radius)
                e.x = g.arena.out[0]; e.y = g.arena.out[1]
                st.spiralAcc += step
                if (st.spiralAcc >= 70f) {
                    st.spiralAcc = 0f
                    repeat(3) { g.addParticle(e.x, e.y, st.def.color, 60f, 0.6f, 4f) }
                    if (p.trail) g.addBlast(e.x, e.y, 58f, 0.75f, p.damage * 0.8f * e.damageMul, st.def.color)
                }
                g.forEachOperativeHit(e.x, e.y, e.radius + g.playerRadius * 0.6f, st.dashHits) { bit ->
                    st.dashHits = st.dashHits or bit
                    g.damagePlayer(p.damage * e.damageMul, e.x, e.y)
                }
                if (blocked || st.subTimer * p.speed >= p.distance) {
                    st.chargeStage = 2; st.subTimer = 0f
                    g.addSlash(e.x, e.y, atan2(st.dirY, st.dirX), e.radius + 95f, p.damage * e.damageMul, st.def.color)
                }
            }
            else -> {
                st.subTimer += dt
                if (st.subTimer >= 0.35f) {
                    st.counter++
                    if (st.counter >= p.repeats) return true
                    st.chargeStage = 0; st.subTimer = 0f
                }
            }
        }
        return false
    }

    /** Backline Dive: 0 vanish + mark behind the operative, 1 appear and dive through them, 2 recover. */
    private fun runBacklineDive(e: Enemy, st: BossState, p: Pattern.BacklineDive, dt: Float): Boolean {
        st.subTimer += dt
        when (st.chargeStage) {
            0 -> if (st.counter == 0) {
                st.counter = 1
                // Behind = the side of the operative away from where it is facing.
                val f = g.operatives[0].facing
                var bx = g.px - cos(f) * 200f
                var by = g.py - sin(f) * 200f
                g.arena.pushOut(MathUtil.clamp(bx, e.radius, g.arena.width - e.radius), MathUtil.clamp(by, e.radius, g.arena.height - e.radius), e.radius)
                bx = g.arena.out[0]; by = g.arena.out[1]
                st.anchorX = bx; st.anchorY = by
                g.addPulse(e.x, e.y, 90f, 0.3f, st.def.color)
                e.state = AiState.HIDDEN
                g.addBlast(bx, by, e.radius * 0.9f, p.warn, 0f, st.def.color)
            } else if (st.subTimer >= p.warn) {
                e.x = st.anchorX; e.y = st.anchorY
                e.state = AiState.MOVE
                val ang = atan2(g.py - e.y, g.px - e.x)
                st.dirX = cos(ang); st.dirY = sin(ang)
                st.dashHits = 0
                g.addPulse(e.x, e.y, 110f, 0.3f, 0xFFFFFFFF)
                st.chargeStage = 1; st.subTimer = 0f
            }
            1 -> {
                val step = p.speed * dt
                val blocked = g.arena.pushOut(e.x + st.dirX * step, e.y + st.dirY * step, e.radius)
                e.x = g.arena.out[0]; e.y = g.arena.out[1]
                g.forEachOperativeHit(e.x, e.y, e.radius + g.playerRadius * 0.6f, st.dashHits) { bit ->
                    st.dashHits = st.dashHits or bit
                    g.damagePlayer(p.damage * e.damageMul, e.x, e.y)
                }
                if (blocked || st.subTimer * p.speed >= 420f) {
                    g.addSlash(e.x, e.y, atan2(st.dirY, st.dirX), e.radius + 90f, p.damage * 0.8f * e.damageMul, st.def.color)
                    st.chargeStage = 2; st.subTimer = 0f
                }
            }
            else -> return st.subTimer >= 0.35f
        }
        return false
    }

    /** Spark Ambush: 0 melt into shadow + mark a server block, 1 burst out beside it. */
    private fun runSparkAmbush(e: Enemy, st: BossState, p: Pattern.SparkAmbush, dt: Float): Boolean {
        st.subTimer += dt
        when (st.chargeStage) {
            0 -> if (st.subTimer <= dt) {
                // Pick a block 150–520 units from the operative (any block if none fits).
                val obs = g.arena.obstacles.indices.map { g.arena.rect(it) }
                val pick = obs.filter { MathUtil.dist(it.centerX, it.centerY, g.px, g.py) in 150f..520f }.ifEmpty { obs }
                if (pick.isEmpty()) return true
                val r = pick[g.rng.nextInt(pick.size)]
                st.anchorX = r.centerX; st.anchorY = r.centerY
                st.dirX = r.width / 2f; st.dirY = r.height / 2f
                g.addBlast(r.centerX, r.centerY, p.radius, p.delay, p.damage * e.damageMul, 0xFFFF2A3A)
                e.state = AiState.HIDDEN
                g.addPulse(e.x, e.y, 80f, 0.35f, st.def.color)
            } else if (st.subTimer >= p.delay) {
                // Sparks spray from the block; it steps out on the side facing you.
                for (i in 0 until p.shards) {
                    val a = MathUtil.TWO_PI * i / p.shards + g.rng.nextFloat() * 0.2f
                    // Out of the block's faces, not its middle (the block would swallow them).
                    val ca = kotlin.math.abs(cos(a)).coerceAtLeast(0.001f)
                    val sa = kotlin.math.abs(sin(a)).coerceAtLeast(0.001f)
                    val edge = kotlin.math.min(st.dirX / ca, st.dirY / sa) + 10f
                    g.fireEnemyProjectile(st.anchorX + cos(a) * edge, st.anchorY + sin(a) * edge, a, 230f, p.damage * 0.6f * e.damageMul, 5f, ProjKind.NEEDLE)
                }
                repeat(26) { g.addParticle(st.anchorX, st.anchorY, 0xFFFF2A3A, 320f, 0.6f, 3f) }
                val ang = atan2(g.py - st.anchorY, g.px - st.anchorX)
                g.arena.pushOut(st.anchorX + cos(ang) * 110f, st.anchorY + sin(ang) * 110f, e.radius)
                e.x = g.arena.out[0]; e.y = g.arena.out[1]
                e.state = AiState.MOVE
                g.addPulse(e.x, e.y, 110f, 0.4f, st.def.color)
                st.chargeStage = 1; st.subTimer = 0f
            }
            else -> return st.subTimer >= 0.3f
        }
        return false
    }

    /** Burrow Drift: 0 dive, 1 tunnel toward the operative, 2 exit marked, 3 erupt. */
    private fun runBurrow(e: Enemy, st: BossState, p: Pattern.Burrow, dt: Float): Boolean {
        st.subTimer += dt
        when (st.chargeStage) {
            0 -> if (st.subTimer >= 0.45f) {
                e.state = AiState.HIDDEN
                st.chargeStage = 1; st.subTimer = 0f
            }
            1 -> {
                val dx = g.px - e.x
                val dy = g.py - e.y
                val d = kotlin.math.sqrt(dx * dx + dy * dy)
                if (d > 1f) {
                    val step = kotlin.math.min(d, p.speed * dt)
                    e.x = MathUtil.clamp(e.x + dx / d * step, e.radius, g.arena.width - e.radius)
                    e.y = MathUtil.clamp(e.y + dy / d * step, e.radius, g.arena.height - e.radius)
                }
                // Glowing crack trail behind it.
                st.spiralAcc += dt
                if (st.spiralAcc >= 0.035f) {
                    st.spiralAcc = 0f
                    g.addParticle(e.x + (g.rng.nextFloat() - 0.5f) * 34f, e.y + (g.rng.nextFloat() - 0.5f) * 16f, st.def.color, 22f, 1.8f, 5f)
                    g.addParticle(e.x, e.y, 0xFFFFFFFF, 30f, 0.5f, 2.5f)
                }
                if (st.subTimer >= p.travel || d < 30f) {
                    // It can't surface inside a wall.
                    g.arena.pushOut(e.x, e.y, e.radius)
                    e.x = g.arena.out[0]; e.y = g.arena.out[1]
                    g.addBlast(e.x, e.y, p.eruptRadius, p.telegraph, p.damage * e.damageMul, st.def.color)
                    st.chargeStage = 2; st.subTimer = 0f
                }
            }
            2 -> if (st.subTimer >= p.telegraph) {
                e.state = AiState.MOVE
                g.addPulse(e.x, e.y, p.eruptRadius * 1.5f, 0.5f, st.def.color)
                repeat(40) { g.addParticle(e.x, e.y, st.def.color, 320f, 0.8f, 3.5f) }
                g.fx.shake(8f, 0.4f)
                if (p.bloomRings > 0) bloom(e.x, e.y, p.bloomRings, 3, 0.55f, 0.28f, 32f, p.damage * 0.8f * e.damageMul, st.def.color)
                st.chargeStage = 3; st.subTimer = 0f
            }
            else -> return st.subTimer >= 0.4f
        }
        return false
    }

    /** Wall segments of 1–3 cubes in a ring 110–330 units around the player. */
    private fun deployCover(e: Enemy, st: BossState, p: Pattern.CoverDeploy) {
        val size = CUBE * 2f
        var placed = 0
        var attempts = 0
        while (placed < p.segments + st.cycle && attempts++ < 40) {
            val a = g.rng.nextFloat() * MathUtil.TWO_PI
            val d = 110f + g.rng.nextFloat() * 220f
            val x = g.px + cos(a) * d
            val y = g.py + sin(a) * d
            if (MathUtil.dist(x, y, e.x, e.y) < e.radius + 90f) continue
            val n = 1 + g.rng.nextInt(3)
            val horizontal = g.rng.nextBoolean()
            var any = false
            for (k in 0 until n) {
                val off = (k - (n - 1) / 2f) * size
                val cx = if (horizontal) x + off else x
                val cy = if (horizontal) y else y + off
                if (g.addBarrier(cx, cy, CUBE, p.rise, p.life)) any = true
            }
            if (any) placed++
        }
        g.addPulse(e.x, e.y, 120f, 0.4f, st.def.color)
    }

    /** A square of cubes around the player, a [Pattern.Lockdown.gapCubes]-wide gap on the side away from the boss. */
    private fun lockdown(e: Enemy, p: Pattern.Lockdown) {
        val size = CUBE * 2f
        val cx = g.px
        val cy = g.py
        val reach = p.inner + CUBE
        val perSide = ((reach * 2f) / size).toInt() + 1
        val away = atan2(cy - e.y, cx - e.x)
        // 0 right, 1 bottom, 2 left, 3 top. The open side faces away from the boss
        // unless a room wall or block is right behind it — then the next best side,
        // so the box can never seal you in.
        val preferred = (((away / (MathUtil.TWO_PI / 4f)).let { kotlin.math.round(it).toInt() } % 4) + 4) % 4
        val order = intArrayOf(preferred, (preferred + 1) % 4, (preferred + 3) % 4, (preferred + 2) % 4)
        val openSide = order.firstOrNull { side ->
            val ex = cx + SIDE_X[side] * (reach + 70f)
            val ey = cy + SIDE_Y[side] * (reach + 70f)
            g.arena.isFree(ex, ey, g.playerRadius + 4f) && g.arena.lineOfSight(cx, cy, ex, ey, g.playerRadius)
        } ?: return
        for (side in 0 until 4) {
            for (k in 0 until perSide) {
                val t = -reach + k * (reach * 2f / (perSide - 1))
                if (side == openSide && kotlin.math.abs(t) < (p.gapCubes * size) / 2f) continue
                val (x, y) = when (side) {
                    0 -> (cx + reach) to (cy + t)
                    1 -> (cx + t) to (cy + reach)
                    2 -> (cx - reach) to (cy + t)
                    else -> (cx + t) to (cy - reach)
                }
                g.addBarrier(x, y, CUBE, p.rise, p.life, clearance = 6f)
            }
        }
        g.addPulse(cx, cy, reach * 1.3f, 0.6f, e.boss?.def?.color ?: 0xFFFF2D55)
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
            is Pattern.SweepBeam -> return st.patternTime >= p.windup + p.duration
            is Pattern.Burrow -> return runBurrow(e, st, p, dt)
            is Pattern.KeyZone -> return st.patternTime >= 0.5f
            is Pattern.LockGrid -> return st.patternTime >= p.rise
            is Pattern.RoyalSeizure -> {
                if (st.counter == 0 && st.patternTime >= p.delay) {
                    st.counter = 1
                    // The crown comes down: anyone in the circle is seized and caged.
                    var caught = false
                    g.forEachOperativeHit(st.anchorX, st.anchorY, p.radius + g.playerRadius * 0.6f, 0) { _ ->
                        g.root(p.root)
                        caught = true
                    }
                    g.addPulse(st.anchorX, st.anchorY, p.radius * 1.6f, 0.45f, 0xFFFFC233)
                    repeat(24) { g.addParticle(st.anchorX, st.anchorY, 0xFFFFC233, 280f, 0.6f, 3f) }
                    g.fx.shake(7f, 0.35f)
                    if (caught) seizureCage(e, st.anchorX, st.anchorY, p.cageLife)
                }
                return st.patternTime >= p.delay + 0.4f
            }
            is Pattern.LineWarp -> return st.patternTime >= 0.4f + p.beamWindup + 0.3f
            is Pattern.DashSlash -> return runDashSlash(e, st, p, dt)
            is Pattern.SwarmHatch -> return st.patternTime >= 0.6f
            is Pattern.QueenRoar -> return st.patternTime >= 0.6f
            is Pattern.CorruptionTrail -> {
                // Surges after you (on top of her normal movement) and drops pools as she goes.
                val dx = g.px - e.x; val dy = g.py - e.y
                val d = kotlin.math.sqrt(dx * dx + dy * dy)
                if (d > e.radius) {
                    val step = e.speed * (p.speedMul - 1f) * dt
                    g.arena.pushOut(e.x + dx / d * step, e.y + dy / d * step, e.radius)
                    e.x = g.arena.out[0]; e.y = g.arena.out[1]
                }
                st.spiralAcc += dt
                if (st.spiralAcc >= 0.3f) {
                    st.spiralAcc = 0f
                    g.addZone(e.x, e.y, p.poolRadius, p.poolLife, p.dps * e.damageMul, st.def.color, telegraph = 0.35f, kind = HazardKind.INFECTED)
                }
                return st.patternTime >= p.duration
            }
            is Pattern.Scythes -> return st.patternTime >= p.flight * 0.6f
            is Pattern.BacklineDive -> return runBacklineDive(e, st, p, dt)
            is Pattern.CrossBeam -> return st.patternTime >= p.windup + p.duration
            is Pattern.BishopMines -> return st.patternTime >= 0.5f
            is Pattern.ConvergenceFlash -> {
                if (st.counter == 0 && st.patternTime >= p.pull) {
                    st.counter = 1
                    // The blinding flash: a blast around the bishop. Threats stay outlined, so it's readable.
                    g.forEachOperativeHit(e.x, e.y, p.radius + g.playerRadius * 0.6f, 0) { _ -> g.damagePlayer(p.damage * e.damageMul, e.x, e.y) }
                    g.addPulse(e.x, e.y, p.radius, 0.45f, 0xFFFFFFFF)
                    g.addPulse(e.x, e.y, p.radius * 0.6f, 0.35f, st.def.color)
                    g.fx.flash(0xFFFFFFFF, 0.6f, 0.55f)
                    g.fx.shake(8f, 0.35f)
                }
                return st.patternTime >= p.pull + 0.4f
            }
            is Pattern.FirewallRing -> {
                if (st.patternTime < p.windup) return false
                st.subTimer -= dt
                if (st.subTimer <= 0f && st.counter < p.waves) {
                    // Out from the boss all the way past the far corner of the room.
                    val far = kotlin.math.hypot(g.arena.width, g.arena.height)
                    val gapHalf = 0.3f
                    g.addFireWall(e.x, e.y, e.radius * 1.2f, far, p.speed, p.gaps, atan2(g.py - e.y, g.px - e.x) + 0.9f + st.counter * 0.7f, gapHalf, p.damage * e.damageMul, st.def.color, e.uid)
                    g.addPulse(e.x, e.y, e.radius * 2.4f, 0.4f, 0xFFFFD45A)
                    st.counter++
                    st.subTimer = p.waveGap
                }
                return st.counter >= p.waves && st.subTimer <= 0f
            }
            is Pattern.BurnSector -> return st.patternTime >= p.warn + 0.3f
            is Pattern.HeatCollapse -> return st.patternTime >= 0.6f
            is Pattern.PurgeSpin -> return st.patternTime >= p.windup + p.duration
            is Pattern.RansomPulse -> {
                st.subTimer -= dt
                if (st.subTimer <= 0f) {
                    g.addRansomRing(e.x, e.y, p.maxRadius, p.speed, p.damage * e.damageMul, st.def.color)
                    g.addPulse(e.x, e.y, 80f, 0.3f, 0xFFFFC233)
                    st.counter++
                    st.subTimer = p.gap
                }
                return st.counter >= p.rings && st.subTimer <= p.gap * 0.5f
            }
            is Pattern.GhostDash -> return runGhostDash(e, st, p, dt)
            is Pattern.Needles -> {
                st.subTimer -= dt
                if (st.subTimer <= 0f) {
                    val base = atan2(g.py - e.y, g.px - e.x)
                    val spread = Math.toRadians(p.spreadDeg.toDouble()).toFloat()
                    val n = p.count * mul
                    for (i in 0 until n) {
                        val t = if (n == 1) 0f else i / (n - 1f) - 0.5f
                        g.fireEnemyProjectile(e.x, e.y - e.radius * 0.5f, base + t * spread, p.speed, p.damage * e.damageMul, 5f, ProjKind.NEEDLE)
                    }
                    st.counter++
                    st.subTimer = p.burstGap
                }
                return st.counter >= p.bursts
            }
            is Pattern.SparkAmbush -> return runSparkAmbush(e, st, p, dt)
            is Pattern.GridSurge -> {
                st.subTimer -= dt
                if (st.subTimer <= 0f) {
                    g.addShockRing(e.x, e.y, p.maxRadius, p.speed, p.damage * e.damageMul, st.def.color)
                    g.addPulse(e.x, e.y, 90f, 0.3f, 0xFFFFFFFF)
                    // The grid tries to reboot: the lights stutter on with each wave.
                    g.lightFlicker = 0.18f
                    g.fx.shake(5f, 0.2f)
                    st.counter++
                    st.subTimer = p.gap
                }
                return st.counter >= p.rings && st.subTimer <= p.gap * 0.5f
            }
            is Pattern.SpikeEruption -> return st.patternTime >= p.delay + p.spikes * p.step
            is Pattern.Infect -> return st.patternTime >= p.telegraph
            is Pattern.Bloom -> return st.patternTime >= p.delay + p.rings * p.ringGap
            is Pattern.CoverDeploy -> return st.patternTime >= p.rise
            is Pattern.Lockdown -> return st.patternTime >= p.rise
            is Pattern.Mortar -> {
                st.subTimer -= dt
                if (st.subTimer <= 0f) {
                    for (i in 0 until p.count) {
                        val x: Float
                        val y: Float
                        if (i == 0) { x = g.px; y = g.py } else {
                            val a = g.rng.nextFloat() * MathUtil.TWO_PI
                            val d = 50f + g.rng.nextFloat() * 200f
                            x = MathUtil.clamp(g.px + cos(a) * d, 40f, g.arena.width - 40f)
                            y = MathUtil.clamp(g.py + sin(a) * d, 40f, g.arena.height - 40f)
                        }
                        g.addMortar(e.x, e.y - e.radius, x, y, p.radius, p.flight, p.damage * e.damageMul, st.def.color)
                    }
                    g.addPulse(e.x, e.y - e.radius, 50f, 0.25f, st.def.color)
                    st.counter++
                    st.subTimer = p.volleyGap
                }
                return st.counter >= p.volleys
            }
            is Pattern.Blasts -> return st.patternTime >= p.delay * 0.6f
            is Pattern.Summon, is Pattern.ShockRing, is Pattern.Zones, is Pattern.Homing -> return st.patternTime >= 0.4f
        }
    }

    /** Test/render hook: start [p] now and keep the boss from picking its own patterns. */
    internal fun forcePattern(p: Pattern) {
        val e = g.boss ?: return
        val st = e.boss ?: return
        startPattern(e, st, p)
    }

    /** Euros for the kill before the operative's own multipliers. */
    fun baseBounty(st: BossState): Int =
        (st.def.euros * (1f + 0.04f * g.level) * (if (st.glitched) 1.5f else 1f)).toInt()

    fun onBossKilled(e: Enemy, st: BossState) {
        // Power restores after a blackout fight.
        if (g.darkness > 0f) { g.darknessTarget = 0f; g.bossVeil = 0f }
        g.addPulse(e.x, e.y, 480f, 1.1f, st.def.color)
        g.addPulse(e.x, e.y, 260f, 0.7f, 0xFFFFFFFF)
        repeat(60) { g.addParticle(e.x, e.y, st.def.color, 360f, 1.2f, 4f) }
        g.sound(GameSound.BOSS_DEATH)
        // The kill beat: freeze, white flash, heavy shake, then slow motion.
        g.fx.hitStop(0.14f)
        g.fx.flash(0xFFFFFFFF, 0.55f, 0.5f)
        g.fx.shake(14f, 0.9f)
        g.fx.slowMotion(1.4f, 0.3f)
        g.showBanner("BOSS ELIMINATED", st.displayName, 2f)
        val glitchMul = if (st.glitched) 1.5f else 1f
        val euros = baseBounty(st)
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
        /** Half the size of a barrier cube. */
        const val CUBE = 24f
        /** Damage the Ransom King takes with his shield up / broken. */
        const val SHIELD_DAMAGE_MUL = 0.25f
        const val DECRYPTED_DAMAGE_MUL = 1.5f
        const val DECRYPT_SECONDS = 6f
        /** Share of each ring slot a plate covers (the rest is a sliver gap). Matches the body drawing. */
        const val PLATE_SPAN = 0.78f
        /** Vertical squash of the ring's perspective ellipse (matches BossBodySpectralFirewall). */
        const val RING_SQUASH = 0.36f
        private val SIDE_X = floatArrayOf(1f, 0f, -1f, 0f)
        private val SIDE_Y = floatArrayOf(0f, 1f, 0f, -1f)
        const val INTRO_BAR_END = 1.4f
        const val INTRO_NAME_END = 2.0f
        const val INTRO_GROWL_AT = 2.0f
    }
}
