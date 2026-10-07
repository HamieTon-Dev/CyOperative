package com.cyberoperative.game.engine

import com.cyberoperative.game.core.Scaling
import com.cyberoperative.game.data.Arenas
import com.cyberoperative.game.data.ArenaTemplate
import com.cyberoperative.game.data.BossDef
import com.cyberoperative.game.data.Bosses
import com.cyberoperative.game.data.EliteModifier
import com.cyberoperative.game.data.Enemies
import com.cyberoperative.game.data.EnemyDef
import com.cyberoperative.game.data.EventDef
import com.cyberoperative.game.data.EventRules
import com.cyberoperative.game.data.Events
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

enum class LevelKind { NORMAL, BOSS, EVENT }

data class SpawnSpec(val def: EnemyDef, val elite: EliteModifier?)

data class LevelPlan(
    val level: Int,
    val kind: LevelKind,
    val arena: ArenaTemplate,
    val waves: List<List<SpawnSpec>>,
    val boss: BossDef? = null,
    val event: EventDef? = null,
    val rules: EventRules = EventRules(),
    /** Names of Zero-Day modifiers rolled for this level (for the banner). */
    val modifierNames: List<String> = emptyList()
) {
    val enemyCount: Int get() = waves.sumOf { it.size }
}

/**
 * Decides what each level contains (§14–16, §22, §26). Pure function of
 * (level, rng, previous arena) so it can be tested deterministically.
 */
object LevelPlanner {

    fun plan(level: Int, rng: Random, previousArenaId: String?, previousWasEvent: Boolean): LevelPlan {
        if (Scaling.isBossLevel(level)) return bossPlan(level, rng)

        val event = pickEvent(level, rng, previousWasEvent)
        if (event != null) return eventPlan(level, rng, event, previousArenaId)

        val arena = pickArena(level, rng, previousArenaId)
        val waves = buildWaves(level, rng, Scaling.enemyBudget(level), Scaling.waveCount(level), EventRules())
        return LevelPlan(level, LevelKind.NORMAL, arena, waves)
    }

    fun pickEvent(level: Int, rng: Random, previousWasEvent: Boolean): EventDef? {
        if (previousWasEvent) return null
        // Never the level right before a boss: let the player breathe.
        if (level % 10 == 9) return null
        val eligible = Events.all.filter { level >= it.minLevel }
        if (eligible.isEmpty()) return null
        if (rng.nextFloat() >= Events.EVENT_CHANCE) return null
        val total = eligible.sumOf { it.weight.toDouble() }.toFloat()
        var roll = rng.nextFloat() * total
        for (e in eligible) {
            roll -= e.weight
            if (roll <= 0f) return e
        }
        return eligible.last()
    }

    fun eventPlan(level: Int, rng: Random, event: EventDef, previousArenaId: String?): LevelPlan {
        var rules = event.rules
        val names = ArrayList<String>()
        if (event.randomModifiers.isNotEmpty()) {
            val picks = event.randomModifiers.shuffled(rng).take(2 + rng.nextInt(2))
            for ((name, r) in picks) {
                rules = Events.merge(rules, r)
                names += name
            }
        }
        val arena = if (rules.vault) Arenas.templates.first { it.id == "open_grid" }
        else pickArena(level, rng, previousArenaId)
        val budget = max(4, (Scaling.enemyBudget(level) * rules.enemyCountMul).roundToInt())
        val waves = if (rules.timedSeconds > 0f) emptyList()
        else buildWaves(level, rng, budget, Scaling.waveCount(level) + rules.extraWaves, rules)
        return LevelPlan(level, LevelKind.EVENT, arena, waves, event = event, rules = rules, modifierNames = names)
    }

    fun bossPlan(level: Int, rng: Random): LevelPlan {
        val boss = Bosses.forLevel(level)
        val arena = if (rng.nextBoolean()) Arenas.bossArena else Arenas.mirrored(Arenas.bossArena)
        return LevelPlan(level, LevelKind.BOSS, arena, emptyList(), boss = boss)
    }

    fun pickArena(level: Int, rng: Random, previousArenaId: String?): ArenaTemplate {
        val pool = Arenas.eligible(level)
        var t = pool[rng.nextInt(pool.size)]
        // Avoid repeating the base layout twice in a row.
        if (pool.size > 1) {
            var guard = 0
            while (previousArenaId != null && previousArenaId.removeSuffix("_m") == t.id && guard++ < 8) {
                t = pool[rng.nextInt(pool.size)]
            }
        }
        return if (rng.nextBoolean()) Arenas.mirrored(t) else t
    }

    fun buildWaves(level: Int, rng: Random, budget: Int, waveCount: Int, rules: EventRules): List<List<SpawnSpec>> {
        val pool: List<EnemyDef> = rules.forcedPool?.map { Enemies.byId(it) } ?: Enemies.pool(level)
        val eliteChance = (Scaling.eliteChance(level) + rules.eliteChanceBonus).coerceAtMost(0.6f)
        val waves = ArrayList<List<SpawnSpec>>(waveCount)
        var remaining = budget
        for (w in 0 until waveCount) {
            val wavesLeft = waveCount - w
            // First wave a little lighter so a level never opens on a wall of threats.
            var size = remaining / wavesLeft
            if (w == 0 && waveCount > 1) size = (size * 0.8f).roundToInt().coerceAtLeast(2)
            size = size.coerceAtLeast(1)
            val wave = ArrayList<SpawnSpec>(size)
            while (wave.size < size) {
                val def = weightedPick(pool, rng)
                val elite = if (rng.nextFloat() < eliteChance) EliteModifier.entries[rng.nextInt(EliteModifier.entries.size)] else null
                val pack = if (elite == null) def.packSize else 1
                repeat(pack) { if (wave.size < size + 2) wave += SpawnSpec(def, if (it == 0) elite else null) }
            }
            remaining -= wave.size
            waves += wave
            if (remaining <= 0 && w < waveCount - 1) break
        }
        return waves
    }

    fun weightedPick(pool: List<EnemyDef>, rng: Random): EnemyDef {
        val total = pool.sumOf { it.weight.toDouble() }.toFloat()
        var roll = rng.nextFloat() * total
        for (d in pool) {
            roll -= d.weight
            if (roll <= 0f) return d
        }
        return pool.last()
    }
}
