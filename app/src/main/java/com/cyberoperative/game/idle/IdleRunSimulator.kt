package com.cyberoperative.game.idle

import com.cyberoperative.game.core.Scaling
import com.cyberoperative.game.data.AiKind
import com.cyberoperative.game.data.AttackKind
import com.cyberoperative.game.data.Bosses
import com.cyberoperative.game.data.EliteModifier
import com.cyberoperative.game.data.Enemies
import com.cyberoperative.game.data.Gear
import com.cyberoperative.game.data.GearItem
import com.cyberoperative.game.data.OpProgress
import com.cyberoperative.game.data.Operatives
import com.cyberoperative.game.data.Upgrades
import com.cyberoperative.game.engine.Armor
import com.cyberoperative.game.engine.Difficulty
import com.cyberoperative.game.engine.GameEngine
import com.cyberoperative.game.engine.LevelPlanner
import com.cyberoperative.game.engine.PowerGovernor
import com.cyberoperative.game.engine.RunBuild
import com.cyberoperative.game.engine.RunConfig
import com.cyberoperative.game.engine.RunStats
import com.cyberoperative.game.save.PlayerProfile
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * IDLE RUN simulator (owner, 0.37.0). Advances an [IdleRunState] to a wall-clock time without
 * the game renderer: no physics, no projectiles, no pathfinding.
 *
 * It fights the same run a player would, using the game's own data and formulas:
 *  - the operator is [Operatives.buildConfig] of the character's class, permanent upgrades,
 *    OP level and equipped gear, plus the upgrade chips this run has picked ([RunBuild]);
 *  - each level's threats come from [LevelPlanner.plan] and are fought ONE AT A TIME; every
 *    10th level is a boss from [Bosses.randomForLevel] (INFERNO runs: the INFERNO packs);
 *  - threat HP and damage use the engine's curves (Scaling, difficulty, OP level, the hidden
 *    [PowerGovernor]); damage to the operator goes through armour, dodge and the threat-damage scale;
 *  - rewards are the normal run's (kill €, room €, boss bounty, [OpProgress] XP) and the
 *    operator keeps [IdleRunState.REWARD_SHARE] of them; gear drops use the normal drop rules.
 *
 * A real room throws several threats at once and a real player dodges most attacks; the idle
 * fight shows one threat, so its damage is the room's crowd pressure and the share a competent
 * player still takes ([HIT_TAKEN]). Output is the share of potential damage a competent player
 * lands ([SKILL_MEAN], rolled per level). Every random draw is seeded from the run seed and the
 * event, so a run doesn't depend on how often it is synced, and no two runs are the same.
 */
object IdleRunSimulator {
    /** Share of potential damage output a competent player lands (rolled ± [SKILL_SPREAD] per level). */
    const val SKILL_MEAN = 0.70f
    const val SKILL_SPREAD = 0.12f
    /** Bosses are big, focused targets: a competent player lands a bit more on them. */
    const val BOSS_SKILL = 1.1f
    /** Share of threat attacks a competent player still takes. */
    const val HIT_TAKEN = 0.058f
    const val BOSS_HIT_TAKEN = 0.029f
    /** Boss pattern damage per second, as a multiple of its contact damage. */
    const val BOSS_ATTACK_DPS = 0.9f
    /** Between rooms: HP charges, floor pickups and System Restores, as a share of max HP (× heal power). */
    const val ROOM_HEAL = 0.32f
    /** Walk to the next door, and a boss's intro. */
    const val ROOM_SECONDS = 6f
    const val BOSS_INTRO_SECONDS = 4f
    /** Most threats that press the operator at once (one fought, the rest closing in). */
    const val MAX_CROWD = 3f
    /** A run stops by itself after this long (COMPLETED). */
    const val MAX_RUN_SECONDS = 12L * 3600L
    /** Chip picks: a smart player mostly takes the best fit, but not always. */
    const val PICK_SHARPNESS = 60.0
    const val PICK_WILD = 0.12f

    /** Drink an HP charge under this share of max HP. */
    const val CHARGE_AT = 0.4f

    private const val BOSS_PREFIX = "boss:"

    // ------------------------------------------------------------------------------------------
    // Starting a run

    /** Why a run can't start, or null when it can. */
    fun cannotStart(p: PlayerProfile, difficulty: Difficulty): String? = when {
        p.characterId.isEmpty() -> "Make a character first."
        difficulty.inferno > 0 && p.infernoUnlocked <= 0 -> "INFERNO is locked: beat World 15 on HARD."
        difficulty.inferno > p.infernoUnlocked -> "INFERNO ${difficulty.inferno} is locked."
        else -> null
    }

    /** The difficulties an idle run may use: STANDARD (Normal), and INFERNO once unlocked. */
    fun choices(p: PlayerProfile): List<Difficulty> =
        listOfNotNull(Difficulty.MEDIUM, if (p.infernoUnlocked > 0) Difficulty.inferno(p.infernoUnlocked) else null)

    /** A fresh run for the active character with everything it has equipped now. */
    fun start(p: PlayerProfile, difficulty: Difficulty, now: Long, seed: Long = now xor System.nanoTime()): IdleRunState {
        require(cannotStart(p, difficulty) == null) { cannotStart(p, difficulty)!! }
        val base = IdleRunState(
            runId = seed,
            characterId = p.characterId, characterName = p.characterName,
            classId = Operatives.byId(p.selectedOperative).id,
            skinId = com.cyberoperative.game.meta.StoreManager.effectiveSkin(p).id, neonColor = p.neonColor, look = p.look,
            opXp = p.operativeXp, permanent = p.permanentUpgrades, gear = p.equipped.values.toList(),
            difficulty = difficulty.name, seed = seed,
            startedAt = now, simulatedTo = now
        )
        val sim = Sim(base)
        sim.hp = sim.stats.maxHp
        sim.startLevel(1, first = true)
        return sim.snapshot()
    }

    /** The run's starting configuration (also used by the Idle Runs screen's stat line). */
    fun config(s: IdleRunState): RunConfig {
        val lv = OpProgress.of(s.opXp)
        return Operatives.buildConfig(s.classId, s.permanent, s.seed, lv.level, s.gear)
            .copy(difficulty = Difficulty.byName(s.difficulty), opLevel = lv.effective, gearLevel = lv.level)
    }

    // ------------------------------------------------------------------------------------------
    // Advancing

    /** [s] moved forward to [now] (a run never goes back in time; ended runs don't change). */
    fun advance(s: IdleRunState, now: Long): IdleRunState {
        if (!s.active || now <= s.simulatedTo) return s
        val sim = Sim(s)
        sim.runTo(now)
        return sim.snapshot()
    }

    // ------------------------------------------------------------------------------------------

    private class Sim(val s0: IdleRunState) {
        val difficulty = Difficulty.byName(s0.difficulty)
        val inferno = difficulty.inferno > 0
        val cfg = config(s0)
        val build = RunBuild(RunStats().also { it.copyFrom(cfg.baseStats) }).apply {
            weaponSlots = difficulty.weaponSlots(cfg.weaponSlots)
            qualityBonus = cfg.upgradeQuality
            restore(s0.chips)
        }
        val stats: RunStats get() = build.stats

        var status = s0.status
        var endReason = s0.endReason
        var endedAt = s0.endedAt
        var t = s0.simulatedTo
        var level = s0.level
        val queue = ArrayDeque(s0.queue)
        var roomSize = s0.roomSize
        var enemy = s0.enemy
        var pause = s0.pause
        var hp = s0.hp
        var charges = s0.charges
        var kills = s0.kills
        var elites = s0.elites
        var bosses = s0.bosses
        var euros = s0.normalEuros
        var xp = s0.normalXp
        val chipLog = ArrayList(s0.chipLog)
        val drops = ArrayList(s0.drops)
        var highest = s0.highestLevel

        /** Monster level, as in the engine: the OP level, or the run level / 2.5 when deeper. */
        val monsterLevel: Int get() = max(cfg.opLevel, ceil(level / 2.5f).toInt()).coerceAtLeast(1)
        val opHpNow: Float get() = 1f + (cfg.opHpMul - 1f) * difficulty.extraScaling
        val opDamageNow: Float get() = 1f + (cfg.opDamageMul - 1f) * difficulty.extraScaling
        fun pressureHp(l: Int) = 1f + (Scaling.hpPressure(l) - 1f) * difficulty.hpPressureShare
        fun pressureDamage(l: Int) = 1f + (Scaling.damagePressure(l) - 1f) * difficulty.pressure
        fun pressureCount(l: Int) = 1f + (Scaling.countPressure(l) - 1f) * difficulty.pressure

        fun rng(salt: Int, index: Int = 0) = Random(s0.seed * 31 + level * 1_000_003L + index * 7_919L + salt)

        /** Share of potential output that lands this level. */
        fun skill(): Float = SKILL_MEAN + SKILL_SPREAD * (rng(11).nextFloat() * 2f - 1f)

        fun runTo(now: Long) {
            var guard = 0
            while (status == com.cyberoperative.game.idle.IdleStatus.ACTIVE && t < now && guard++ < 2_000_000) {
                if ((t - s0.startedAt) / 1000L >= MAX_RUN_SECONDS) { complete("UPLINK LIMIT · 12 H"); break }
                val left = (now - t) / 1000f
                if (pause > 0f) {
                    val dt = min(pause, left)
                    pause -= dt; t += Math.round(dt * 1000.0).coerceAtLeast(1L)
                    continue
                }
                val e = enemy ?: run { spawnNext(); null } ?: continue
                // An HP charge at the line (the same packet a player drinks on Q).
                val line = stats.maxHp * CHARGE_AT
                if (charges > 0 && hp <= line + 0.01f) { charges--; hp = min(stats.maxHp, hp + stats.maxHp * GameEngine.HP_CHARGE_HEAL) }
                val out = dpsAgainst(e)
                val toKill = e.hp / out
                // Stop where HP reaches the charge line, so the charge is drunk in time.
                val toDie = if (e.dps <= 0f) Float.MAX_VALUE else if (charges > 0 && hp > line) (hp - line) / e.dps else hp / e.dps
                val dt = min(min(toKill, toDie), left)
                hp -= e.dps * dt
                val ehp = e.hp - out * dt
                t += Math.round(dt * 1000.0).coerceAtLeast(1L)
                if (hp <= 0.001f && charges == 0) { hp = 0f; enemy = e.copy(hp = ehp.coerceAtLeast(1f)); defeat(); break }
                if (ehp <= 0f || dt >= toKill) killed(e) else enemy = e.copy(hp = ehp)
            }
        }

        fun dpsAgainst(e: IdleEnemy): Float {
            val s = stats
            val base = if (e.boss) PowerGovernor.potentialBossDps(s) * BOSS_SKILL else PowerGovernor.potentialDps(s)
            val elite = e.elite?.let { runCatching { EliteModifier.valueOf(it) }.getOrNull() }
            var dps = base * skill()
            if (elite != null) {
                dps *= s.eliteDamageMul * elite.damageTakenMul
                if (difficulty.eliteArmor) dps *= GameEngine.ELITE_DAMAGE_TAKEN
            }
            return dps.coerceAtLeast(0.5f)
        }

        /** Incoming damage after the operator's defences. */
        fun mitigate(raw: Float): Float {
            val s = stats
            return raw * PowerGovernor.threatDamage(difficulty) * (1f - Armor.reduction(s.armor, monsterLevel)) * (1f - s.dodge)
        }

        fun startLevel(l: Int, first: Boolean = false) {
            level = l
            highest = max(highest, l)
            queue.clear()
            if (Scaling.isBossLevel(l)) {
                val b = bossFor(s0, l)
                queue += BOSS_PREFIX + b.id
                pause = BOSS_INTRO_SECONDS
            } else {
                // previousWasEvent = true: no event rooms (their rules need the real arena).
                val plan = LevelPlanner.plan(l, rng(3), null, true, s0.seed, pressureCount(l), inferno)
                for (w in plan.waves) for (sp in w) queue += if (sp.elite != null) "${sp.def.id}|${sp.elite.name}" else sp.def.id
                if (queue.isEmpty()) queue += "malware"
                pause = if (first) 1f else ROOM_SECONDS
            }
            roomSize = queue.size
            enemy = null
        }

        fun spawnNext() {
            if (queue.isEmpty()) { levelCleared(); return }
            val index = roomSize - queue.size
            val key = queue.removeFirst()
            enemy = if (key.startsWith(BOSS_PREFIX)) spawnBoss(key.removePrefix(BOSS_PREFIX)) else spawnThreat(key, index)
        }

        fun spawnThreat(key: String, @Suppress("UNUSED_PARAMETER") index: Int): IdleEnemy {
            val def = Enemies.byId(key.substringBefore('|'))
            val elite = key.substringAfter('|', "").takeIf { it.isNotEmpty() }?.let { runCatching { EliteModifier.valueOf(it) }.getOrNull() }
            val l = level
            val d = difficulty
            val hpCurve = Scaling.enemyHp(l) * pressureHp(l) * d.enemyHp * opHpNow
            val lift = PowerGovernor.enemyHpLift(d, PowerGovernor.potentialDps(stats), hpCurve)
            val hp = def.baseHp * hpCurve * lift * (if (elite != null) EliteModifier.BASE_HP_MUL * elite.hpMul * d.eliteHpMul else 1f)
            val dmgCurve = Scaling.enemyDamage(l) * pressureDamage(l) * d.enemyDamage * opDamageNow
            val damageMul = dmgCurve * PowerGovernor.damageLift(d, lift, PowerGovernor.survival(stats), dmgCurve)
            // Its attacks per second at this level, then what still lands on a competent player.
            val volley = when (def.attack) {
                AttackKind.NONE -> 0f
                AttackKind.AIMED -> min(def.projectileCount, 3).toFloat()
                AttackKind.SPREAD -> min(def.projectileCount, 2).toFloat()
                AttackKind.RADIAL -> 1f
            }
            val shots = if (def.attackCooldown > 0f) def.projectileDamage * volley / def.attackCooldown else 0f
            val melee = def.contactDamage * (if (def.ai == AiKind.CHASER || def.ai == AiKind.SWARMER || def.ai == AiKind.CHARGER) 0.8f else 0.2f)
            val raw = (shots + melee) * damageMul * Scaling.attackRate(l) * (elite?.attackRateMul ?: 1f)
            // The rest of the wave is closing in while this one is fought.
            val waves = Scaling.campaignWaves(roomSize).coerceAtLeast(1)
            val crowd = ((roomSize.toFloat() / waves + 1f) / 2f).coerceIn(1f, MAX_CROWD)
            val rewardMul = if (elite != null) EliteModifier.REWARD_MUL else 1f
            val s = stats
            return IdleEnemy(
                id = def.id, elite = elite?.name, hp = hp, maxHp = hp,
                dps = mitigate(raw) * HIT_TAKEN * crowd,
                euros = max(1f, (def.euros * rewardMul * s.euroMul * d.rewardMul).toInt().toFloat()),
                xp = OpProgress.killXp(monsterLevel, elite != null, false, s.xpMul).toFloat()
            )
        }

        fun spawnBoss(id: String): IdleEnemy {
            val b = Bosses.byId(id) ?: Bosses.forLevel(level)
            val l = level
            val d = difficulty
            val cycle = Bosses.cycleForLevel(l)
            val ref = Bosses.forLevel(l)
            val baseHp = if (b == ref) b.baseHp else ref.baseHp * Math.pow((b.baseHp / ref.baseHp).toDouble(), 0.25).toFloat()
            val dmgScale = if (b == ref) 1f else (ref.contactDamage / b.contactDamage).coerceIn(0.6f, 1.6f)
            val pressure = 1f + (pressureHp(l) - 1f) * 0.5f
            var hp = baseHp * d.bossHpMul * pressure * Scaling.bossHp(l) * (1f + 0.25f * cycle) * d.enemyHp * opHpNow
            val lift = PowerGovernor.bossHpLift(d, PowerGovernor.potentialBossDps(stats), hp)
            hp *= lift
            val curve = pressureDamage(l) * Scaling.enemyDamage(l) * d.enemyDamage * opDamageNow
            val govern = PowerGovernor.damageLift(d, lift, PowerGovernor.survival(stats), curve) * PowerGovernor.lateBossDamage(d, l)
            val damageMul = dmgScale * curve * (1f + 0.1f * cycle) * govern
            val s = stats
            return IdleEnemy(
                id = b.id, boss = true, hp = hp, maxHp = hp,
                dps = mitigate(b.contactDamage * BOSS_ATTACK_DPS * damageMul * Scaling.attackRate(l)) * BOSS_HIT_TAKEN,
                euros = (b.euros * (1f + 0.04f * l) * s.euroMul * d.rewardMul).toInt().toFloat(),
                xp = OpProgress.killXp(monsterLevel, false, true, s.xpMul).toFloat()
            )
        }

        fun killed(e: IdleEnemy) {
            enemy = null
            kills++
            euros += e.euros
            xp += e.xp
            val s = stats
            if (s.healOnKill > 0f) hp = min(s.maxHp, hp + s.healOnKill)
            val r = rng(7, kills)
            val inf = difficulty.inferno
            // HP charge packets drop from elites and bosses, as on the floor in a normal run.
            val chargeChance = if (e.boss) GameEngine.BOSS_HP_DROP else if (e.elite != null) GameEngine.ELITE_HP_DROP else 0f
            if (r.nextFloat() < chargeChance) charges = min(GameEngine.MAX_HP_CHARGES, charges + 1)
            when {
                e.boss -> {
                    bosses++
                    drop(r, boss = true, count = GameEngine.BOSS_GEAR_DROPS - 1 + inf.coerceAtMost(10) / 3)
                }
                e.elite != null -> {
                    elites++
                    if (r.nextFloat() < GameEngine.ELITE_GEAR_CHANCE + 0.02f * inf.coerceAtMost(10)) drop(r, boss = false, count = 1)
                }
            }
            if (queue.isEmpty()) levelCleared()
        }

        fun drop(r: Random, boss: Boolean, count: Int) {
            val inf = difficulty.inferno
            val luck = (difficulty.luck * 0.3f).coerceAtLeast(0f)
            repeat(count) {
                val rarity = Gear.rollRarity(r, boss, inf, luck, boss && level >= 120)
                drops += Gear.roll(r, (drops.size + 1).toLong(), Gear.itemLevel(cfg.gearLevel, r), rarity)
            }
        }

        fun levelCleared() {
            val s = stats
            val boss = Scaling.isBossLevel(level)
            euros += ((5 + level) * s.euroMul * difficulty.rewardMul).toInt()
            xp += OpProgress.roomXp(monsterLevel, s.xpMul)
            hp = min(s.maxHp, hp + s.maxHp * ROOM_HEAL * s.healMul)
            // Upgrade chips: one from a normal room, two from a boss (picked with boss luck).
            val luck = level * 0.015f + difficulty.luck + (if (boss) 1.2f else 0f)
            repeat(if (boss) 2 else 1) { k -> pickChip(rng(13, k), luck) }
            startLevel(level + 1)
        }

        /** Rolls a normal offer and takes the card that fits this build best, with some randomness. */
        fun pickChip(r: Random, luck: Float) {
            val offers = build.rollOffer(r, 3, luck).filter { !build.needsSlot(it.def) }
            if (offers.isEmpty()) return
            val s = stats
            val now = value(s, hp / s.maxHp)
            val scores = offers.map { o ->
                if (o.def.instant) {
                    when (o.def.id) {
                        Upgrades.CRYPTO_CACHE.id, Upgrades.DATA_DUMP.id -> 1.01
                        else -> 1.0 + 0.4 * (1f - hp / s.maxHp)
                    }
                } else value(build.preview(o.def), hp / s.maxHp) / now
            }
            val pick = if (r.nextFloat() < PICK_WILD) r.nextInt(offers.size) else {
                val w = scores.map { Math.pow(it.coerceAtLeast(0.01), PICK_SHARPNESS) }
                var roll = r.nextDouble() * w.sum()
                var i = 0
                while (i < w.lastIndex && roll > w[i]) { roll -= w[i]; i++ }
                i
            }
            val o = offers[pick]
            val wasMax = s.maxHp
            if (build.take(o.def)) {
                when (o.def.id) {
                    Upgrades.CRYPTO_CACHE.id -> euros += ((15 + 3 * level) * stats.euroMul * difficulty.rewardMul).toInt()
                    Upgrades.DATA_DUMP.id -> {}
                    else -> hp = min(stats.maxHp, hp + stats.maxHp * 0.4f)
                }
            } else {
                val gained = stats.maxHp - wasMax
                if (gained > 0f) hp += gained
                hp = min(hp, stats.maxHp)
            }
            chipLog += o.title
        }

        /** How strong a build is: output × staying power, evenly weighted. */
        fun value(s: RunStats, hpFrac: Float): Double =
            Math.sqrt(PowerGovernor.potentialDps(s).toDouble().coerceAtLeast(1.0) * PowerGovernor.survival(s).toDouble().coerceAtLeast(1.0)) *
                (1.0 + 0.1 * (1f - hpFrac))

        fun defeat() {
            status = IdleStatus.DEFEATED
            endedAt = t
            endReason = "OPERATOR TERMINATED"
        }

        fun complete(reason: String) {
            status = IdleStatus.COMPLETED
            endedAt = t
            endReason = reason
        }

        fun snapshot(): IdleRunState = s0.copy(
            status = status, endReason = endReason, endedAt = endedAt, simulatedTo = t,
            level = level, queue = queue.toList(), roomSize = roomSize, enemy = enemy, pause = pause,
            hp = hp, maxHp = stats.maxHp, charges = charges, kills = kills, elites = elites, bosses = bosses,
            normalEuros = euros, normalXp = xp,
            chips = LinkedHashMap(build.owned()), chipLog = chipLog, drops = drops,
            highestLevel = highest
        )
    }

    /** The boss guarding boss level [level] of run [s] (INFERNO runs: the INFERNO packs from level 30). */
    fun bossFor(s: IdleRunState, level: Int): com.cyberoperative.game.data.BossDef =
        Bosses.randomForLevel(level, s.seed, Difficulty.byName(s.difficulty).inferno > 0)

    /** The gear a run found, given fresh save ids starting at [firstUid]. */
    fun dropsWithIds(s: IdleRunState, firstUid: Long): List<GearItem> = s.drops.mapIndexed { i, g -> g.copy(uid = firstUid + i) }
}
