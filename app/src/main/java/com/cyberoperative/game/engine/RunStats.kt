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
    /** Armor (owner, 2026-10-13): damage cut = armor / (armor + 50 × monster level), see [Armor]. */
    var armor = 0f
    var dodge = 0f              // chance to ignore a hit, 0..0.4
    var healOnKill = 0f         // flat HP per kill
    var healMul = 1f
    var counterRing = 0         // projectiles released when hit
    // Owner, 0.39.0: the GLITCHED / ENCRYPTED buffs.
    var healOnHit = 0f          // HP per primary shot that hits
    var dodgeMul = 1f           // ENCRYPTED: dodge ×2 (cap raised to 0.6)
    var potionFullHeal = false  // HP potions heal to full
    var potionMul = 1f          // HP potion heal multiplier
    var autoPotion = false      // HP potions drink themselves at 20% HP or on a lethal hit
    var dashCooldownMul = 1f
    var petRateMul = 1f         // companion fire rate

    // Primary weapon (fires while stationary; [moveFireRate] > 0 lets it fire on the move)

    var damage = 20f
    var fireRate = 1.6f         // volleys per second
    var projectileSpeed = 640f
    var range = 560f
    var critChance = 0.05f
    var critMul = 1.8f
    var parallelShots = 1
    var followUpShots = 0
    var diagonalPairs = 0
    var pierce = 0
    var bounce = 0
    var chain = 0
    var chainDamageMul = 0.6f
    var instantDeleteChance = 0f
    var eliteDamageMul = 1f
    /** Share of the fire rate kept while moving (0 = stop to shoot, 1 = full rate). */
    var moveFireRate = 0f

    // Movement
    // Owner, 2026-10-08: base speed +15% (was 240) — runs started a little slow.
    var moveSpeed = 276f

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
    var mineLevel = 0
    var missileLevel = 0
    var arcLevel = 0
    var railLevel = 0
    var strikeLevel = 0
    /** Auto-weapon levels by [com.cyberoperative.game.data.Weapons] id. */
    val weapons = HashMap<String, Int>()

    /** PHANTOM: cloaks every [GameEngine.CLOAK_CYCLE] seconds. */
    var cloak = false

    // Economy
    var xpMul = 1f
    var euroMul = 1f

    fun copyFrom(o: RunStats) {
        maxHp = o.maxHp; armor = o.armor; dodge = o.dodge
        healOnKill = o.healOnKill; healMul = o.healMul
        counterRing = o.counterRing
        healOnHit = o.healOnHit; dodgeMul = o.dodgeMul; potionFullHeal = o.potionFullHeal; potionMul = o.potionMul
        autoPotion = o.autoPotion; dashCooldownMul = o.dashCooldownMul; petRateMul = o.petRateMul
        moveFireRate = o.moveFireRate
        damage = o.damage; fireRate = o.fireRate; projectileSpeed = o.projectileSpeed
        range = o.range; critChance = o.critChance; critMul = o.critMul
        parallelShots = o.parallelShots; followUpShots = o.followUpShots
        diagonalPairs = o.diagonalPairs; pierce = o.pierce
        bounce = o.bounce; chain = o.chain; chainDamageMul = o.chainDamageMul
        instantDeleteChance = o.instantDeleteChance; eliteDamageMul = o.eliteDamageMul
        moveSpeed = o.moveSpeed
        orbCount = o.orbCount; orbDamage = o.orbDamage; orbAngularSpeed = o.orbAngularSpeed
        orbRadius = o.orbRadius; orbSize = o.orbSize; orbBoltInterval = o.orbBoltInterval
        bladeCount = o.bladeCount; bladeDamage = o.bladeDamage; bladeRadius = o.bladeRadius
        coneLevel = o.coneLevel; lanceLevel = o.lanceLevel; beamLevel = o.beamLevel; empLevel = o.empLevel
        mineLevel = o.mineLevel; missileLevel = o.missileLevel; arcLevel = o.arcLevel
        railLevel = o.railLevel; strikeLevel = o.strikeLevel
        weapons.clear(); weapons.putAll(o.weapons)
        xpMul = o.xpMul; euroMul = o.euroMul
        cloak = o.cloak
    }

    /** Clamp the stats that would break the game if stacked without limit. */
    fun clampLimits() {
        armor = armor.coerceAtLeast(0f)
        dodge = (dodge * dodgeMul).coerceIn(0f, if (dodgeMul > 1f) 0.6f else 0.4f)
        dashCooldownMul = dashCooldownMul.coerceIn(0.3f, 1f)
        moveFireRate = moveFireRate.coerceIn(0f, 1f)
        critChance = critChance.coerceIn(0f, 0.85f)
        instantDeleteChance = instantDeleteChance.coerceIn(0f, 0.15f)
        fireRate = fireRate.coerceAtMost(12f)
        orbCount = orbCount.coerceAtMost(10)
        bladeCount = bladeCount.coerceAtMost(6)
    }
}
