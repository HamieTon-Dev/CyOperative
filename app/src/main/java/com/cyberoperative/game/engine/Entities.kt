package com.cyberoperative.game.engine

import com.cyberoperative.game.data.EliteModifier
import com.cyberoperative.game.data.EnemyDef

/**
 * Runtime entities. Mutable classes (not data classes) living in fixed-size
 * pools, so a busy arena allocates nothing per frame (§61).
 */

enum class AiState { SPAWNING, MOVE, WINDUP, DASH, RECOVER, HIDDEN }

class Enemy {
    var active = false
    var uid = 0
    lateinit var def: EnemyDef
    var elite: EliteModifier? = null
    var boss: BossState? = null

    var x = 0f
    var y = 0f
    var vx = 0f
    var vy = 0f
    var radius = 16f
    var hp = 1f
    var maxHp = 1f
    var speed = 0f
    var damageMul = 1f
    var attackRateMul = 1f
    var damageTakenMul = 1f
    var rewardMul = 1f

    var state = AiState.SPAWNING
    var stateTimer = 0f
    var attackTimer = 0f
    var aimX = 0f
    var aimY = 0f
    var strafeDir = 1f
    var wobble = 0f
    var hitFlash = 0f
    var orbHitCooldown = 0f
    var bladeHitCooldown = 0f
    var contactCooldown = 0f
    var stuckTimer = 0f
    var detourTimer = 0f
    var detourX = 0f
    var detourY = 0f
    /** Cached path-following direction, refreshed every [navTimer] seconds. */
    var navX = 0f
    var navY = 0f
    var navTimer = 0f
    var navValid = false
    var lastX = 0f
    var lastY = 0f
    /** *GLITCHED*: which attack the current windup will release (0..3). */
    var glitchMode = 0
    /** Split children give reduced score so splitting can't be farmed. */
    var isChild = false

    val targetable: Boolean get() = active && state != AiState.SPAWNING && state != AiState.HIDDEN
    val isElite: Boolean get() = elite != null
}

enum class ProjKind { BOLT, CONE, LANCE, NODE_BOLT, COUNTER, ENEMY, BOSS, MISSILE, MINE, RAIL, BOOMERANG }

class Projectile {
    var active = false
    var friendly = true
    var kind = ProjKind.BOLT
    var x = 0f
    var y = 0f
    var vx = 0f
    var vy = 0f
    var radius = 6f
    var damage = 0f
    var life = 0f
    var pierceLeft = 0
    var bounceLeft = 0
    var chainLeft = 0
    var crit = false
    var lastHitUid = -1
    /** Phase through obstacles (some boss patterns). */
    var ghost = false
    var homing = 0f
    /** MINE: seconds until armed. */
    var armTimer = 0f
    /** MISSILE / MINE: blast radius on detonation (0 = no blast). */
    var splash = 0f
    /** Friendly colour override for arsenal weapons (0 = default cyan). */
    var tint = 0L
    /** BOOMERANG: on its way back. */
    var returning = false
    /** Operative index that fired it (co-op credit and stats). */
    var owner = 0
}

enum class ZapKind {
    /** Arc Discharge bolt from (x, y) to (x2, y2); damage already applied. */
    ARC,
    /** Orbital Strike: marked at (x, y), lands for [Zap.damage] when the timer runs out. */
    STRIKE,
    /** Laser line from (x, y) to (x2, y2); damage already applied. */
    LASER,
    /** Lingering zone: [Zap.damage] per second to threats inside. */
    FIELD
}

/** Friendly weapon effects that are neither projectiles nor hostile hazards. */
class Zap {
    var active = false
    var kind = ZapKind.ARC
    var x = 0f
    var y = 0f
    var x2 = 0f
    var y2 = 0f
    var radius = 0f
    var timer = 0f
    var duration = 0f
    var damage = 0f
    var landed = false
    var seed = 0
    var color = 0L
    var tick = 0f
}

enum class TextKind { NORMAL, CRIT, BOSS, HEAL, SHIELD, PLAYER_HURT, INFO }

class FloatText {
    var active = false
    var x = 0f
    var y = 0f
    var text = ""
    var kind = TextKind.NORMAL
    var life = 0f
}

class Particle {
    var active = false
    var x = 0f
    var y = 0f
    var vx = 0f
    var vy = 0f
    var life = 0f
    var maxLife = 1f
    var color = 0L
    var size = 3f
}

enum class HazardKind {
    /** Telegraphed line (charge / sniper sight). Visual only. */
    LINE,
    /** Telegraphed circle that damages once when the timer completes. */
    BLAST,
    /** Lingering damaging zone (corruption). */
    ZONE,
    /** Expanding ring that damages on contact with its edge. */
    SHOCK_RING,
    /** Telegraphed line that becomes a damaging beam after [Hazard.windup]. */
    BEAM,
    /**
     * Laser that sweeps an arc after its windup (Boss Expansion S2). [Hazard.angle]
     * turns by [Hazard.angVel]; obstacles stop it ([Hazard.x2]/[Hazard.y2] is the
     * clipped end), so cover blocks save you. [Hazard.maxRadius] = total sweep.
     */
    SWEEP,
    /**
     * Lobbed shell (Boss Expansion S4): a BLAST whose shell arcs from
     * [Hazard.x2]/[Hazard.y2] (the launcher) to the marked spot. Flies over cover.
     */
    MORTAR,
    /**
     * Crystal spike cluster bursting from the floor (Rootkit Apostle): telegraphed
     * for [Hazard.duration], hits once as it bursts, then the crystals linger
     * (visual only) for [GameEngine.SPIKE_LINGER].
     */
    SPIKE,
    /** A ZONE drawn as rootkit infection (Rootkit Apostle's Infected Zone); same damage rules. */
    INFECTED
}

class Hazard {
    var active = false
    var kind = HazardKind.LINE
    var x = 0f
    var y = 0f
    var x2 = 0f
    var y2 = 0f
    var radius = 0f
    var maxRadius = 0f
    var timer = 0f
    var duration = 0f
    var damage = 0f
    var color = 0L
    var ownerUid = -1
    /** Which operatives this hazard has already hit (bit per operative index). */
    var hitMask = 0
    var windup = 0f
    var tick = 0f
    /** SWEEP: current angle and turn rate (radians, radians/s). */
    var angle = 0f
    var angVel = 0f
}

/**
 * Barrier cube raised out of the floor by a boss (Boss Expansion S6). Telegraphed
 * for [rise] seconds, solid (a real obstacle) for [life], then sinks.
 */
class Barrier(val x: Float, val y: Float, val half: Float, val rise: Float, val life: Float) {
    var timer = 0f
    val solid: Boolean get() = timer >= rise && timer < rise + life
    val done: Boolean get() = timer >= rise + life + SINK_SECONDS
    val left get() = x - half
    val top get() = y - half
    val right get() = x + half
    val bottom get() = y + half

    companion object { const val SINK_SECONDS = 0.35f }
}

/** Purely visual expanding ring (EMP, explosions, level clear). */
class Pulse {
    var active = false
    var x = 0f
    var y = 0f
    var radius = 0f
    var maxRadius = 0f
    var life = 0f
    var maxLife = 0f
    var color = 0L
}

/** Fixed-capacity pool. `obtain()` returns null when full (caller skips the spawn). */
class Pool<T>(capacity: Int, factory: () -> T, private val isActive: (T) -> Boolean) {
    val items: List<T> = List(capacity) { factory() }
    private var cursor = 0

    fun obtain(): T? {
        val n = items.size
        for (i in 0 until n) {
            val idx = (cursor + i) % n
            val item = items[idx]
            if (!isActive(item)) {
                cursor = (idx + 1) % n
                return item
            }
        }
        return null
    }

    fun countActive(): Int {
        var c = 0
        for (i in items.indices) if (isActive(items[i])) c++
        return c
    }
}
