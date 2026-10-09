package com.cyberoperative.game.engine

import com.cyberoperative.game.data.Rarity
import com.cyberoperative.game.data.UpgradeDef
import com.cyberoperative.game.data.Upgrades
import kotlin.random.Random

/** One card on the level-up screen. */
data class UpgradeOffer(val def: UpgradeDef, val nextLevel: Int) {
    val title: String get() = if (def.instant || nextLevel <= 1) def.name else "${def.name} ${Upgrades.roman(nextLevel)}"
    val effect: String get() = def.effectAt(nextLevel)
    val mastery: Boolean get() = !def.instant && nextLevel > def.maxLevel
    val isEvolution: Boolean get() = def.evolvesFrom != null
}

/**
 * The upgrades owned in the current run, and the rules for offering more.
 * [base] comes from the operative + permanent progression; [stats] is always
 * `base + every owned upgrade`, rebuilt by [recompute].
 */
class RunBuild(private val base: RunStats) {

    private val levels = LinkedHashMap<String, Int>()
    val stats = RunStats()

    /** Upgrade ids never offered in this run (mode-specific). */
    var excluded: Set<String> = emptySet()

    /** Weapons this operative may carry (7 + permanent WEAPON SLOTS). */
    var weaponSlots = Upgrades.MAX_WEAPONS

    /** Extra rarity bias from permanent "Upgrade Quality" (0 = none). */
    var qualityBonus = 0f

    init { recompute() }

    fun level(id: String): Int = levels[id] ?: 0

    fun owned(): Map<String, Int> = levels

    fun isEligible(def: UpgradeDef): Boolean {
        if (def.id in excluded) return false
        if (def.instant) return true
        if (level(def.id) >= def.levelCap) return false
        val from = def.evolvesFrom
        if (from != null) {
            val parent = Upgrades.byId(from)
            if (level(from) < parent.maxLevel) return false
        }
        val also = def.alsoRequires
        if (also != null && level(also) < 1) return false
        return true
    }

    /** Weapons equipped, in the order they were picked up. */
    fun weapons(): List<String> = levels.keys.filter { Upgrades.isWeapon(it) }

    /** True when taking [def] would need a free weapon slot and none is left. */
    fun needsSlot(def: UpgradeDef): Boolean =
        Upgrades.isWeapon(def) && level(def.id) == 0 && weapons().size >= weaponSlots

    /** Drops an equipped weapon to free its slot (weapon replacement). */
    fun remove(id: String) {
        if (levels.remove(id) != null) recompute()
    }

    /**
     * Stats as they would be after taking [add] (one more level) and/or dropping
     * [remove], without changing the build: drives the before/after comparison.
     */
    fun preview(add: UpgradeDef?, remove: String? = null): RunStats {
        val lv = LinkedHashMap(levels)
        if (remove != null) lv.remove(remove)
        if (add != null && !add.instant) lv[add.id] = (lv[add.id] ?: 0) + 1
        val out = RunStats()
        out.copyFrom(base)
        for ((id, l) in lv) Upgrades.byId(id).applyLevel(out, l)
        out.clampLimits()
        return out
    }

    /** Applies the choice. Returns true if it was an instant effect (e.g. heal). */
    fun take(def: UpgradeDef): Boolean {
        if (def.instant) return true
        levels[def.id] = level(def.id) + 1
        recompute()
        return false
    }

    fun recompute() {
        stats.copyFrom(base)
        for ((id, lvl) in levels) Upgrades.byId(id).applyLevel(stats, lvl)
        stats.clampLimits()
    }

    private fun weightOf(def: UpgradeDef, luck: Float): Float {
        var w = def.rarity.weight
        if (qualityBonus > 0f && def.rarity.ordinal >= Rarity.RARE.ordinal) w *= 1f + qualityBonus
        // Luck (level, difficulty, boss rewards) makes each tier above BLUE
        // compound more likely: luck 1 doubles PURPLE, x4 GOLDEN, x8 TITANIUM.
        val tier = def.rarity.highTier
        if (tier > 0 && luck > 0f) w *= Math.pow(1.0 + luck, tier.toDouble()).toFloat()
        // An unlocked evolution is the payoff for committing to a build:
        // make it very likely to be seen.
        if (def.evolvesFrom != null) w = maxOf(w, 60f)
        // Filler heal is rare unless nothing else remains.
        if (def.instant) w = 8f
        return w
    }

    /** Odds of [def] appearing in one card slot of a fresh offer (tests, tuning). */
    fun chanceOf(def: UpgradeDef, luck: Float = 0f): Float {
        val pool = Upgrades.all.filter { isEligible(it) }
        if (def !in pool) return 0f
        return weightOf(def, luck) / pool.sumOf { weightOf(it, luck).toDouble() }.toFloat()
    }

    /**
     * The card set (owner, 2026-10-09): power-up, power-up, then one weapon.
     * If either kind runs out the other fills the gap, so there are always
     * three cards while anything is left to offer.
     */
    fun rollOffer(rng: Random, count: Int = 3, luck: Float = 0f): List<UpgradeOffer> {
        val eligible = Upgrades.all.filter { isEligible(it) }
        val powers = eligible.filter { !Upgrades.isWeapon(it) }.toMutableList()
        val weapons = eligible.filter { Upgrades.isWeapon(it) }.toMutableList()
        val result = ArrayList<UpgradeOffer>(count)
        fun draw(pool: MutableList<UpgradeDef>): Boolean {
            if (pool.isEmpty()) return false
            val total = pool.sumOf { weightOf(it, luck).toDouble() }.toFloat()
            var roll = rng.nextFloat() * total
            var chosen = pool.last()
            for (d in pool) {
                roll -= weightOf(d, luck)
                if (roll <= 0f) { chosen = d; break }
            }
            pool.remove(chosen)
            result += UpgradeOffer(chosen, if (chosen.instant) 1 else level(chosen.id) + 1)
            return true
        }
        val weaponCards = if (count >= 3) 1 else 0
        while (result.size < count - weaponCards && draw(powers)) {}
        repeat(weaponCards) { if (!draw(weapons)) draw(powers) }
        // Not enough power-ups left: top up with weapons.
        while (result.size < count && (draw(powers) || draw(weapons))) {}
        return result
    }

    /** Restores owned levels from a saved run. Unknown ids (removed mods) are skipped. */
    fun restore(owned: Map<String, Int>) {
        levels.clear()
        for ((id, l) in owned) if (Upgrades.all.any { it.id == id }) levels[id] = l
        recompute()
    }
}
