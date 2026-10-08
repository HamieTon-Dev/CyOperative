package com.cyberoperative.game.engine

/**
 * Every number that describes the operative during one run.
 *
 * Never mutated incrementally: [com.cyberoperative.game.engine.RunBuild.recompute]
 * rebuilds it from the base stats plus every owned upgrade level, so the
 * result is deterministic and order-independent (and easy to unit test).
 */
class RunStats {
    // Survivability
    var maxHp = 100f
    var armor = 0f              // fraction of incoming damage removed, 0..0.6
    var dodge = 0f              // chance to ignore a hit, 0..0.4
    var regenPerSec = 0f        // fraction of max HP per second
    var healOnKill = 0f         // flat HP per kill
    var healMul = 1f
    var firewallMax = 0f        // absorb shield
    var firewallDelay = 3.0f    // seconds without damage before recharge
    var firewallRate = 0.25f    // fraction of firewallMax per second
    var firewallOnKill = 0f     // fraction restored per kill (adaptive firewall)
    var firewallBreakPulse = 0f // damage of the shockwave when the firewall breaks
    var counterRing = 0         // projectiles released when hit

    // Primary weapon (fires only while stationary)
    var damage = 20f
    var fireRate = 1.6f         // volleys per second
    var projectileSpeed = 640f
    var range = 560f
    var critChance = 0.05f
    var critMul = 1.8f
    var parallelShots = 1
    var followUpShots = 0
    var diagonalPairs = 0
    var rearShot = false
    var pierce = 0
    var bounce = 0
    var chain = 0
    var chainDamageMul = 0.6f
    var instantDeleteChance = 0f
    var eliteDamageMul = 1f

    // Movement
    var moveSpeed = 240f

    // Orbiting Packet Nodes (always active)
    var orbCount = 1
    var orbDamage = 10f
    var orbAngularSpeed = 2.6f  // radians per second
    var orbRadius = 80f
    var orbSize = 11f
    var orbBoltInterval = 0f    // >0: nodes fire autonomous bolts (final evolution)

    // Encryption Blades (always active; block enemy projectiles)
    var bladeCount = 0
    var bladeDamage = 14f
    var bladeRadius = 56f

    // Abilities
    var coneLevel = 0
    var lanceLevel = 0
    var beamLevel = 0
    var empLevel = 0

    // Economy
    var xpMul = 1f
    var euroMul = 1f

    fun copyFrom(o: RunStats) {
        maxHp = o.maxHp; armor = o.armor; dodge = o.dodge; regenPerSec = o.regenPerSec
        healOnKill = o.healOnKill; healMul = o.healMul; firewallMax = o.firewallMax
        firewallDelay = o.firewallDelay; firewallRate = o.firewallRate
        firewallOnKill = o.firewallOnKill; firewallBreakPulse = o.firewallBreakPulse
        counterRing = o.counterRing
        damage = o.damage; fireRate = o.fireRate; projectileSpeed = o.projectileSpeed
        range = o.range; critChance = o.critChance; critMul = o.critMul
        parallelShots = o.parallelShots; followUpShots = o.followUpShots
        diagonalPairs = o.diagonalPairs; rearShot = o.rearShot; pierce = o.pierce
        bounce = o.bounce; chain = o.chain; chainDamageMul = o.chainDamageMul
        instantDeleteChance = o.instantDeleteChance; eliteDamageMul = o.eliteDamageMul
        moveSpeed = o.moveSpeed
        orbCount = o.orbCount; orbDamage = o.orbDamage; orbAngularSpeed = o.orbAngularSpeed
        orbRadius = o.orbRadius; orbSize = o.orbSize; orbBoltInterval = o.orbBoltInterval
        bladeCount = o.bladeCount; bladeDamage = o.bladeDamage; bladeRadius = o.bladeRadius
        coneLevel = o.coneLevel; lanceLevel = o.lanceLevel; beamLevel = o.beamLevel; empLevel = o.empLevel
        xpMul = o.xpMul; euroMul = o.euroMul
    }

    /** Clamp the stats that would break the game if stacked without limit. */
    fun clampLimits() {
        armor = armor.coerceIn(0f, 0.6f)
        dodge = dodge.coerceIn(0f, 0.4f)
        critChance = critChance.coerceIn(0f, 0.85f)
        instantDeleteChance = instantDeleteChance.coerceIn(0f, 0.15f)
        fireRate = fireRate.coerceAtMost(8f)
        orbCount = orbCount.coerceAtMost(10)
        bladeCount = bladeCount.coerceAtMost(6)
    }
}
