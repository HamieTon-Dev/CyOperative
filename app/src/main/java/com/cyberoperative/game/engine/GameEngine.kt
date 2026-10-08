package com.cyberoperative.game.engine

import com.cyberoperative.game.core.MathUtil
import com.cyberoperative.game.core.Scaling
import com.cyberoperative.game.data.EliteModifier
import com.cyberoperative.game.data.Enemies
import com.cyberoperative.game.data.EnemyDef
import com.cyberoperative.game.data.EventRules
import com.cyberoperative.game.data.Upgrades
import com.cyberoperative.game.data.WeaponKind
import com.cyberoperative.game.data.WeaponSpec
import com.cyberoperative.game.data.Weapons
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Where the run is. The UI decides what to show from this. */
enum class Phase {
    /** Fighting. */
    COMBAT,
    /** Arena clear; short beat before upgrades / the access port. */
    CLEARED,
    /** Paused on the three-card upgrade screen. */
    UPGRADE,
    /** Access port open; walk into it to continue. */
    PORTAL,
    /** Brief fade between arenas. */
    TRANSITION,
    /** Operative terminated; waiting for revive / game-over decision. */
    DEAD
}

/** Everything a run starts with: the operative's base stats + permanent progression. */
data class RunConfig(
    val baseStats: RunStats = RunStats(),
    val seed: Long = System.nanoTime(),
    val freeRevives: Int = 1,
    val rerolls: Int = 0,
    val upgradeQuality: Float = 0f,
    val startingUpgrades: Int = 0,
    val startLevel: Int = 1,
    val mode: GameMode = GameMode.CAMPAIGN,
    val difficulty: Difficulty = Difficulty.MEDIUM,
    /** Account OP level (owner, 2026-10-08: veterans face tougher threats and bosses). */
    val opLevel: Int = 1
) {
    /** Threat HP from OP level: +2% per level above 1, up to ×3 (OP 101). */
    val opHpMul: Float get() = 1f + 0.02f * (opLevel - 1).coerceIn(0, 100)
    /** Threat damage from OP level: +1.2% per level above 1, up to ×2.2. */
    val opDamageMul: Float get() = 1f + 0.012f * (opLevel - 1).coerceIn(0, 100)
}

/**
 * The shopkeeper's look, matched to the rarest stock on the counter
 * (owner, 2026-10-08): GOLD → TITANIUM → menacing BLACK → SPECTRUM.
 */
enum class KeeperLook { GOLD, TITANIUM, BLACK, SPECTRUM;
    companion object {
        fun forStock(items: List<ShopItem>): KeeperLook {
            val ti = items.count { it.def.rarity == com.cyberoperative.game.data.Rarity.TITANIUM }
            return when {
                ti >= 4 -> SPECTRUM
                ti >= 2 -> BLACK
                ti == 1 -> TITANIUM
                else -> GOLD
            }
        }
    }
}

/** One mod on the shop counter. */
data class ShopItem(val def: com.cyberoperative.game.data.UpgradeDef, val nextLevel: Int, val price: Int, val sold: Boolean)

/** Final numbers of a run, for the game-over screen and the save. */
data class RunSummary(
    val levelReached: Int,
    val score: Long,
    val kills: Int,
    val bosses: Int,
    val elites: Int,
    val events: Int,
    val euros: Int,
    val diamonds: Int,
    val seconds: Float
)

/**
 * The simulation. Pure Kotlin with no Android dependency, so every rule can be
 * unit tested. The UI feeds it [setInput] and [update]; it never draws.
 *
 * Core loop (§8–10, §16):
 * - MOVE = SURVIVE: while the stick is held the operative moves and the
 *   primary weapon holds fire.
 * - STOP = SHOOT: when the stick is released it auto-targets and fires.
 * - ORBITS = CONTINUOUS DAMAGE: Packet Nodes and Encryption Blades never stop.
 */
class GameEngine(val config: RunConfig = RunConfig(), restore: RunSnapshot? = null) {

    val rng = Random(config.seed)

    // --- Run state --------------------------------------------------------
    var level = config.startLevel
        private set
    var phase = Phase.COMBAT
        private set
    var phaseTimer = 0f
        private set
    lateinit var plan: LevelPlan
        private set
    lateinit var arena: Arena
        private set
    val mode: GameMode get() = config.mode
    val build = RunBuild(config.baseStats).also {
        it.qualityBonus = config.upgradeQuality
        // Campaign picks come from clearing levels, not data, so data-only cards are pointless.
        if (config.mode == GameMode.CAMPAIGN) it.excluded = setOf(Upgrades.DATA_DUMP.id, Upgrades.DATA_COMPRESSION.id)
    }
    val stats: RunStats get() = build.stats

    var runLevel = 1
        private set
    var xp = 0f
        private set
    var pendingUpgrades = 0
        private set
    var offer: List<UpgradeOffer> = emptyList()
        private set(value) {
            field = value
            offerSerial++
        }
    /** Bumped whenever [offer] changes so the UI knows to redraw the cards. */
    var offerSerial = 0
        private set
    /** Rewards in the current pick streak (e.g. 3 after a boss) and how many are taken. */
    var rewardBatchTotal = 0
        private set
    var rewardBatchTaken = 0
        private set
    /** Rarity luck of the current offers (level + difficulty + boss bonus). */
    var offerLuck = 0f
        private set
    /** Adaptive threat multipliers for the current level (see [Scaling.adaptiveHp]). */
    var adaptiveHp = 1f
        private set
    var adaptiveDamage = 1f
        private set

    /** Weapons fighting for the operative besides the primary gun. */
    fun weaponCount(): Int {
        val s = stats
        return s.weapons.size + listOf(s.coneLevel, s.lanceLevel, s.beamLevel, s.empLevel, s.mineLevel, s.missileLevel,
            s.arcLevel, s.railLevel, s.strikeLevel, s.bladeCount).count { it > 0 } + (s.orbCount - 1).coerceAtLeast(0) / 2
    }

    private fun updateAdaptive() {
        val picks = build.owned().values.sum()
        val weapons = weaponCount()
        adaptiveHp = Scaling.adaptiveHp(level, picks, weapons)
        adaptiveDamage = Scaling.adaptiveDamage(level, picks, weapons)
    }

    /** Boss rewards roll with extra luck until they are all picked. */
    private var bossLuckPending = false

    // --- Upgrade shop (owner, 2026-10-08) --------------------------------------
    /** A side gate to the shop is open this PORTAL phase. */
    var shopGateOpen = false
        private set
    /** Bumped when the "receiving message… upgrade shop available" popup should play. */
    var shopMessageSerial = 0
        private set
    val inShop: Boolean get() = plan.kind == LevelKind.SHOP
    private var goingToShop = false
    var shopItems: List<ShopItem> = emptyList()
        private set
    /** True while the operative stands at the counter (the buy panel shows). */
    val atShopCounter: Boolean
        get() = inShop && arena.obstacles.firstOrNull { it.kind == com.cyberoperative.game.data.ObstacleKind.SHOP_COUNTER }?.let {
            px > it.rect.left - 40f && px < it.rect.right + 40f && py < it.rect.bottom + 150f
        } == true
    // --- Data Vault cache (owner, 2026-10-08) ------------------------------------
    /** The cache block still stands in the middle of a Data Vault room. */
    val vaultPresent: Boolean get() = plan.rules.vault && !vaultCracked
    private var vaultCracked = false
    /** Seconds left of the open-and-shake before the cache bursts (0 = not opening). */
    var vaultOpening = 0f
        private set
    /** True once the room is clear and the cache can be cracked by walking up to it. */
    val vaultReady: Boolean get() = vaultPresent && vaultOpening <= 0f && phase != Phase.COMBAT && phase != Phase.DEAD

    /** Side gate on the left wall, halfway down the room. */
    var shopGateY = 0f
        private set
    /** Which side wall the shop gate is on (chosen so it is never blocked). */
    var shopGateRight = false
        private set
    /** X of the gate's mouth: where the operative has to stand to go through. */
    val shopGateX: Float get() = if (shopGateRight) arena.width else 0f
    var rerollsLeft = config.rerolls
        private set
    var revivesLeft = config.freeRevives
        private set
    var revivesUsed = 0
        private set

    var score = 0L
        private set
    var kills = 0
        private set
    var bossesDefeated = 0
        private set
    var elitesDefeated = 0
        private set
    var eventsCompleted = 0
        private set
    /** Event ids completed this run (achievements: complete every event type). */
    val completedEventIds = HashSet<String>()
    var eurosEarned = 0
        private set
    var diamondsEarned = 0
        private set
    var runSeconds = 0f
        private set
    var levelSeconds = 0f
        private set
    private var levelDamageTaken = 0f
    private var levelEnemyTotal = 0
    /** Campaign HUD: threats destroyed / threats in this level (incl. splits and summons). */
    var levelKills = 0
        private set
    private var levelSpawned = 0
    val levelThreats: Int get() = max(plan.enemyCount, levelSpawned)
    /** Seconds left of the slide-in of a new room (gameplay waits for it). */
    var slideIn = 0f
        private set
    /** Endless mode: time into the current difficulty stage. */
    private var stageTimer = 0f
    /** Seed the current room/plan was built from, so a saved run rebuilds the same room. */
    private var levelSeed = 0L
    private var previousArenaId: String? = null
    private var previousEvent = false

    // --- Player -------------------------------------------------------------
    var px = 0f
        private set
    var py = 0f
        private set
    val playerRadius = 20f
    var hp = 100f
        private set
    var firewall = 0f
        private set
    var facing = -MathUtil.PI / 2f
        private set
    var moving = false
        private set
    var invuln = 0f
        private set
    var hurtFlash = 0f
        private set
    private var inputX = 0f
    private var inputY = 0f
    private var fireCooldown = 0f
    private var stillTime = 0f
    private var followUpLeft = 0
    private var followUpTimer = 0f
    private var lanceTimer = 0f
    private var empTimer = 0f
    private var sinceDamage = 99f
    private var firewallWasUp = false
    var orbAngle = 0f
        private set
    var bladeAngle = 0f
        private set
    private var orbBoltTimer = 0f
    var targetUid = -1
        private set

    /** Plasma Beam (upgrade weapon): live this frame, and where it ends. */
    var beamActive = false
        private set
    var beamX2 = 0f
        private set
    var beamY2 = 0f
        private set
    val beamWidth: Float get() = 14f + 5f * stats.beamLevel
    private var beamTick = 0f
    /**
     * Plasma Beam heat (owner, 2026-10-08: "too overpowered"): seconds of
     * damage dealt so far; at [BEAM_MAX_FIRE] it overheats into a
     * [BEAM_COOLDOWN] lockout. Heat bleeds off slowly while not firing.
     */
    var beamHeat = 0f
        private set
    var beamCooldown = 0f
        private set
    private var mineTimer = 0f
    private var missileTimer = 0f
    private var arcTimer = 0f
    private var railTimer = 0f
    private var strikeTimer = 0f

    // --- Entities -----------------------------------------------------------
    val enemies = Pool(96, { Enemy() }) { it.active }
    val projectiles = Pool(480, { Projectile() }) { it.active }
    val texts = Pool(56, { FloatText() }) { it.active }
    val particles = Pool(320, { Particle() }) { it.active }
    val hazards = Pool(64, { Hazard() }) { it.active }
    val pulses = Pool(24, { Pulse() }) { it.active }
    val zaps = Pool(40, { Zap() }) { it.active }
    private var nextUid = 1

    // --- Level flow ---------------------------------------------------------
    private var waveIndex = 0
    private var waveTimer = 0f
    private var eventTimer = 0f
    private var spawnTimer = 0f
    private var hazardTimer = 0f
    var timedRemaining = 0f
        private set
    var portalOpen = false
        private set
    var boss: Enemy? = null
        private set
    var bossPhaseLabel = ""
        private set

    /** Short centred announcement ("LEVEL 7", "PHASE 2"). */
    var banner = ""
        private set
    var bannerSub = ""
        private set
    var bannerTimer = 0f
        private set

    /** Sounds requested this frame; drained by the UI layer. */
    val sounds = ArrayList<GameSound>(16)

    /** Monotonic counter the renderer watches. */
    var frame = 0L
        private set

    /** True while the upgrade screen was opened before combat (starting upgrades). */
    private var upgradeReturnsToCombat = false

    /** Flow field toward the player for enemy navigation. */
    val path = Pathfinder()
    private val ai = EnemyAi(this)
    private val bossBrain = BossBrain(this)

    init {
        build.recompute()
        hp = stats.maxHp
        firewall = stats.firewallMax
        if (restore != null) applySnapshot(restore)
        else startLevel(level, null, false)
        if (restore == null && config.startingUpgrades > 0) {
            // Starting Weapon Power: pick free upgrades before the first fight.
            pendingUpgrades = config.startingUpgrades
            openUpgrades(resumeCombat = true)
        }
    }

    // ======================================================================
    // Input & main update
    // ======================================================================

    /** Joystick vector; magnitude 0..1. */
    fun setInput(x: Float, y: Float) {
        inputX = x
        inputY = y
    }

    fun update(delta: Float) {
        var remaining = delta.coerceIn(0f, MAX_FRAME)
        while (remaining > 0f) {
            val dt = min(STEP, remaining)
            step(dt)
            remaining -= dt
        }
        frame++
    }

    private fun step(dt: Float) {
        if (bannerTimer > 0f) bannerTimer -= dt
        updateEffects(dt)
        if (slideIn > 0f) {
            slideIn = max(0f, slideIn - dt)
            return
        }
        when (phase) {
            Phase.UPGRADE, Phase.DEAD -> return
            Phase.TRANSITION -> {
                phaseTimer += dt
                if (phaseTimer >= TRANSITION_TIME) {
                    if (goingToShop) enterShop()
                    else startLevel(level + 1, plan.arena.id, plan.kind == LevelKind.EVENT)
                    slideIn = TRANSITION_TIME
                }
                return
            }
            else -> {}
        }
        phaseTimer += dt
        runSeconds += dt
        if (phase == Phase.COMBAT) levelSeconds += dt

        updatePlayer(dt)
        updateOrbit(dt)
        if (phase == Phase.COMBAT) {
            if (mode == GameMode.ENDLESS) updateEndless(dt) else updateSpawning(dt)
            updateEventRules(dt)
        }
        if (phase == Phase.COMBAT) path.update(arena, px, py, dt)
        ai.update(dt)
        bossBrain.update(dt)
        updateProjectiles(dt)
        updateZaps(dt)
        updateHazards(dt)
        if (hp <= 0f) {
            die()
            return
        }

        updateVault(dt)
        when (phase) {
            Phase.COMBAT -> if (mode == GameMode.ENDLESS) {
                if (pendingUpgrades > 0) openUpgrades(resumeCombat = true)
            } else checkCleared()
            Phase.CLEARED -> if (phaseTimer >= CLEAR_BEAT) afterClear()
            Phase.PORTAL -> if (kotlin.math.abs(px - arena.portalX) < GATE_HALF_WIDTH && py < arena.portalY + PORTAL_RADIUS) {
                if (shopGateOpen) {
                    // A shop is on offer: ask before letting them skip it, and step them back off the gate.
                    if (!skipShopPrompt) {
                        skipShopPrompt = true
                        sound(GameSound.UI_CLICK)
                    }
                    py = arena.portalY + PORTAL_RADIUS + 46f
                } else {
                    phase = Phase.TRANSITION
                    phaseTimer = 0f
                    goingToShop = false
                }
            } else if (shopGateOpen && kotlin.math.abs(px - shopGateX) < playerRadius + 14f && kotlin.math.abs(py - shopGateY) < SHOP_GATE_HALF) {
                // Through the side gate into the upgrade shop.
                phase = Phase.TRANSITION
                phaseTimer = 0f
                goingToShop = true
            }
            else -> {}
        }
    }

    // ======================================================================
    // Level lifecycle
    // ======================================================================

    private fun startLevel(newLevel: Int, previousArena: String?, previousEvent: Boolean, forced: LevelPlan? = null) {
        level = newLevel
        updateAdaptive()
        skipShopPrompt = false
        topGateLocked = false
        shopArrowFlash = 0f
        vaultCracked = false
        vaultOpening = 0f
        shopGateOpen = false
        goingToShop = false
        shopItems = emptyList()
        previousArenaId = previousArena
        this.previousEvent = previousEvent
        levelSeed = rng.nextLong()
        plan = forced ?: LevelPlanner.plan(level, Random(levelSeed), previousArena, previousEvent, mode)
        val extra = if (plan.rules.vault) listOf(Arena.vaultObstacle(plan.arena)) else emptyList()
        arena = Arena(plan.arena, extra)
        clearAll()
        px = arena.spawnX
        py = arena.spawnY
        facing = -MathUtil.PI / 2f
        phase = Phase.COMBAT
        phaseTimer = 0f
        portalOpen = false
        waveIndex = 0
        waveTimer = 0f
        spawnTimer = 0f
        hazardTimer = 0f
        levelSeconds = 0f
        levelDamageTaken = 0f
        levelEnemyTotal = plan.enemyCount
        levelKills = 0
        levelSpawned = 0
        stageTimer = 0f
        timedRemaining = plan.rules.timedSeconds
        boss = null
        bossPhaseLabel = ""
        invuln = 1.0f
        when (plan.kind) {
            LevelKind.BOSS -> {
                val b = plan.boss!!
                sound(GameSound.BOSS_SPAWN)
                bossBrain.spawn(b, arena.width / 2f, 300f, plan.glitchedBoss)
            }
            LevelKind.EVENT -> {
                val e = plan.event!!
                val sub = if (plan.modifierNames.isNotEmpty()) plan.modifierNames.joinToString(" · ") else e.subtitle
                showBanner(e.name, sub, 2.6f)
                sound(GameSound.EVENT_START)
            }
            LevelKind.SHOP -> {}
            LevelKind.NORMAL -> if (mode == GameMode.ENDLESS) showBanner("ENDLESS", "Survive as long as you can", 2f)
            else showBanner(
                "LEVEL $level",
                // A new environment theme every 3 levels gets announced.
                if (com.cyberoperative.game.data.Environments.isNewTheme(level))
                    "${plan.enemyCount} THREATS · ENTERING ${com.cyberoperative.game.data.Environments.forLevel(level, config.seed).name}"
                else if (adaptiveHp >= 1.5f) "${plan.enemyCount} THREATS · ADAPTED TO YOUR ARSENAL ×${"%.1f".format(adaptiveHp)}"
                else "${plan.enemyCount} THREATS · ${plan.arena.name}",
                1.6f
            )
        }
    }

    private fun clearAll() {
        for (e in enemies.items) e.active = false
        for (p in projectiles.items) p.active = false
        for (h in hazards.items) h.active = false
        for (t in texts.items) t.active = false
        for (p in pulses.items) p.active = false
        for (z in zaps.items) z.active = false
    }

    private fun updateSpawning(dt: Float) {
        val rules = plan.rules
        if (rules.timedSeconds > 0f) {
            timedRemaining -= dt
            spawnTimer -= dt
            if (spawnTimer <= 0f && aliveCount() < Scaling.MAX_ALIVE) {
                spawnTimer = max(0.35f, rules.spawnInterval - level * 0.006f)
                val pool = Enemies.pool(level)
                spawnEnemy(LevelPlanner.weightedPick(pool, rng), null, telegraph = true)
            }
            return
        }
        if (waveIndex >= plan.waves.size) return
        waveTimer += dt
        val alive = aliveCount()
        val first = waveIndex == 0
        if (first || alive <= 2 || waveTimer > WAVE_TIMEOUT) {
            if (!first && alive > 0 && waveTimer < WAVE_MIN_GAP) return
            for (spec in plan.waves[waveIndex]) {
                if (aliveCount() >= Scaling.MAX_ALIVE) break
                spawnEnemy(spec.def, spec.elite, telegraph = true)
            }
            waveIndex++
            waveTimer = 0f
        }
    }

    private fun updateEventRules(dt: Float) {
        val interval = plan.rules.hazardInterval
        if (interval <= 0f) return
        hazardTimer += dt
        if (hazardTimer >= interval) {
            hazardTimer = 0f
            // Firewall Breach / unstable sectors: a corruption zone lands near
            // the player, always telegraphed first (a BLAST marker), so the
            // safe space shrinks without ever being unavoidable.
            val ang = rng.nextFloat() * MathUtil.TWO_PI
            val d = 60f + rng.nextFloat() * 160f
            val x = MathUtil.clamp(px + cos(ang) * d, 60f, arena.width - 60f)
            val y = MathUtil.clamp(py + sin(ang) * d, 60f, arena.height - 60f)
            addZone(x, y, 80f, 7f, 14f * Scaling.enemyDamage(level), 0xFFFF7A1A, telegraph = 1.0f)
        }
    }

    private fun checkCleared() {
        val timed = plan.rules.timedSeconds > 0f
        val done = if (timed) timedRemaining <= 0f
        else plan.kind != LevelKind.BOSS && waveIndex >= plan.waves.size && aliveCount() == 0
        if (plan.kind == LevelKind.BOSS && boss == null && phaseTimer > 1f && aliveCount() == 0) {
            levelCleared()
            return
        }
        if (done) {
            if (timed) purgeHostiles()
            levelCleared()
        }
    }

    private fun levelCleared() {
        phase = Phase.CLEARED
        phaseTimer = 0f
        for (p in projectiles.items) if (p.active && !p.friendly) p.active = false
        for (h in hazards.items) h.active = false
        val eventMul = if (plan.kind == LevelKind.EVENT) plan.rules.rewardMul else 1f
        score += (Scoring.levelClear(level, levelSeconds, levelEnemyTotal, levelDamageTaken, eventMul) * difficultyReward).toLong()
        val euros = ((5 + level) * eventMul * stats.euroMul * difficultyReward).toInt()
        eurosEarned += euros
        if (plan.kind == LevelKind.EVENT) {
            eventsCompleted++
            plan.event?.let { completedEventIds += it.id }
            if (plan.rules.diamondChance > 0f && rng.nextFloat() < plan.rules.diamondChance) diamondsEarned += 1
            pendingUpgrades++
        }
        // Campaign: every cleared level earns a power-up, and deeper levels
        // roll for bonus rewards (owner, 2026-10-08: "higher levels give more").
        pendingUpgrades++
        if (rng.nextFloat() < min(0.45f, (level - 1) * 0.012f)) pendingUpgrades++
        if (level >= 25 && rng.nextFloat() < min(0.3f, (level - 24) * 0.01f)) pendingUpgrades++
        addText(px, py - 40f, "+$euros €", TextKind.INFO)
        addPulse(px, py, 420f, 0.7f, 0xFF00FF9C)
        // Firewall fully restores between arenas.
        firewall = stats.firewallMax
        showBanner(if (plan.kind == LevelKind.EVENT) "EVENT COMPLETE" else "THREATS ELIMINATED", "", 1.2f)
        sound(GameSound.LEVEL_COMPLETE)
        sound(GameSound.CURRENCY)
    }

    private fun afterClear() {
        if (pendingUpgrades > 0) {
            openUpgrades()
        } else {
            openPortal()
        }
    }

    /** Rarity luck for the next offer: deeper levels, harder difficulty and boss rewards all help. */
    private fun currentLuck(): Float =
        level * 0.015f + config.difficulty.luck + (if (bossLuckPending) 1.2f else 0f)

    private fun openUpgrades(resumeCombat: Boolean = false) {
        rewardBatchTotal = pendingUpgrades
        rewardBatchTaken = 0
        offerLuck = currentLuck()
        offer = build.rollOffer(rng, luck = offerLuck)
        if (offer.isEmpty()) {
            pendingUpgrades = 0
            if (!resumeCombat) openPortal()
            return
        }
        upgradeReturnsToCombat = resumeCombat
        phase = Phase.UPGRADE
        phaseTimer = 0f
    }

    private fun finishUpgrades() {
        offer = emptyList()
        bossLuckPending = false
        rewardBatchTotal = 0
        rewardBatchTaken = 0
        // 1 in 20: the shopkeeper sends a message and a side gate opens.
        if (!upgradeReturnsToCombat && mode == GameMode.CAMPAIGN && !inShop && rng.nextFloat() < SHOP_CHANCE) offerShop()
        if (upgradeReturnsToCombat) {
            upgradeReturnsToCombat = false
            phase = Phase.COMBAT
            phaseTimer = 0f
        } else {
            openPortal()
        }
    }

    /** UI: the player picked card [index]. */
    fun chooseUpgrade(index: Int) {
        if (phase != Phase.UPGRADE) return
        val choice = offer.getOrNull(index) ?: return
        val wasMaxHp = stats.maxHp
        val instant = build.take(choice.def)
        if (instant) {
            when (choice.def.id) {
                Upgrades.CRYPTO_CACHE.id -> {
                    val euros = ((15 + 3 * level) * stats.euroMul * difficultyReward).toInt()
                    eurosEarned += euros
                    addText(px, py - 40f, "+$euros €", TextKind.INFO)
                    sound(GameSound.CURRENCY)
                }
                Upgrades.DATA_DUMP.id -> gainXp(Scaling.xpToNext(runLevel) * 0.6f)
                else -> heal(stats.maxHp * 0.4f, ignoreMul = true)
            }
        } else {
            // Max-HP upgrades heal by the amount gained so they feel immediate.
            val gained = stats.maxHp - wasMaxHp
            if (gained > 0f) hp += gained
            hp = min(hp, stats.maxHp)
            if (choice.def == Upgrades.FIREWALL || choice.def.evolvesFrom == Upgrades.FIREWALL.id ||
                choice.def.id in FIREWALL_IDS
            ) {
                firewall = stats.firewallMax
                sound(GameSound.FIREWALL_UP)
            }
        }
        pendingUpgrades--
        rewardBatchTaken++
        sound(GameSound.UPGRADE_SELECTED)
        if (pendingUpgrades > 0) {
            // Endless can earn more data mid-streak; keep the counter honest.
            rewardBatchTotal = max(rewardBatchTotal, rewardBatchTaken + pendingUpgrades)
            offerLuck = currentLuck()
            offer = build.rollOffer(rng, luck = offerLuck)
            if (offer.isEmpty()) { pendingUpgrades = 0; finishUpgrades() }
        } else {
            finishUpgrades()
        }
    }

    /** UI: spend a reroll (permanent progression) to redraw the cards. */
    fun reroll(): Boolean {
        if (phase != Phase.UPGRADE || rerollsLeft <= 0) return false
        rerollsLeft--
        offer = build.rollOffer(rng, luck = offerLuck)
        sound(GameSound.UI_CLICK)
        return true
    }

    /**
     * Data Vault: once the room is clear, walking up to the cache opens it — it
     * shakes for [VAULT_OPEN_SECONDS], then bursts and pays out [vaultPayout].
     */
    private fun updateVault(dt: Float) {
        if (shopArrowFlash > 0f) shopArrowFlash = max(0f, shopArrowFlash - dt)
        if (!vaultPresent) return
        if (vaultOpening > 0f) {
            vaultOpening -= dt
            if (rng.nextFloat() < dt * 30f) addParticle(arena.width / 2f, arena.height / 2f, 0xFFFFD426, 140f, 0.4f, 3f)
            if (vaultOpening <= 0f) crackVault()
            return
        }
        if (phase == Phase.COMBAT || phase == Phase.DEAD) return
        val r = Arena.vaultObstacle(plan.arena).rect
        val nx = MathUtil.clamp(px, r.left, r.right)
        val ny = MathUtil.clamp(py, r.top, r.bottom)
        if (MathUtil.dist(px, py, nx, ny) < playerRadius + 34f) {
            vaultOpening = VAULT_OPEN_SECONDS
            showBanner("DATA CACHE", "DECRYPTING…", VAULT_OPEN_SECONDS)
            sound(GameSound.ACCESS_GRANTED)
        }
    }

    private fun crackVault() {
        vaultOpening = 0f
        vaultCracked = true
        // The block is gone: rebuild the room without it.
        arena = Arena(plan.arena)
        val cx = arena.width / 2f
        val cy = arena.height / 2f
        val payout = vaultPayout(level)
        eurosEarned += payout
        addPulse(cx, cy, 260f, 0.7f, 0xFFFFD426)
        addPulse(cx, cy, 140f, 0.45f, 0xFFFFFFFF)
        repeat(70) { addParticle(cx, cy, if (it % 3 == 0) 0xFFFFFFFF else 0xFFFFD426, 420f, 1.1f, 4f) }
        addText(cx, cy - 30f, "+€$payout", TextKind.INFO)
        showBanner("DATA CACHE CRACKED", "+€${"%,d".format(payout)}", 2f)
        sound(GameSound.ELITE_DEATH)
        sound(GameSound.CURRENCY)
    }

    /** Opens the side gate to the shop for this room (also a test hook). */
    fun offerShop() {
        if (mode != GameMode.CAMPAIGN || inShop) return
        // Owner, 2026-10-08: the gate must never be blocked by a wall or block.
        if (!placeShopGate()) return
        shopGateOpen = true
        shopMessageSerial++
        sound(GameSound.ACCESS_GRANTED)
    }

    /**
     * Picks a spot on a side wall whose mouth is clear of obstacles and that the
     * operative can actually walk to from where they stand. Mid-height first,
     * then outward, left wall then right. Returns false if no wall spot works.
     */
    private fun placeShopGate(): Boolean {
        val reach = reachableCells()
        val mid = arena.height * 0.5f
        val offsets = (0..20).flatMap { k -> if (k == 0) listOf(0f) else listOf(k * 40f, -k * 40f) }
        for (right in listOf(false, true)) {
            for (off in offsets) {
                val y = mid + off
                if (y < 200f || y > arena.height - 200f) continue
                if (gateMouthClear(right, y) && reach(if (right) arena.width - 30f else 30f, y)) {
                    shopGateRight = right
                    shopGateY = y
                    return true
                }
            }
        }
        return false
    }

    /** The doorway strip (and a little room in front of it) has no obstacle in it. */
    private fun gateMouthClear(right: Boolean, y: Float): Boolean {
        var d = -SHOP_GATE_HALF
        while (d <= SHOP_GATE_HALF) {
            var depth = 20f
            while (depth <= 80f) {
                val x = if (right) arena.width - depth else depth
                if (arena.obstacleAt(x, y + d, 2f) >= 0) return false
                depth += 20f
            }
            d += 20f
        }
        return arena.isFree(if (right) arena.width - 30f else 30f, y, playerRadius + 4f)
    }

    /** Flood fill from the operative: returns a lookup "can I walk to (x, y)?". */
    private fun reachableCells(): (Float, Float) -> Boolean {
        val cell = 20f
        val cols = (arena.width / cell).toInt() + 1
        val rows = (arena.height / cell).toInt() + 1
        val seen = BooleanArray(cols * rows)
        val queue = IntArray(cols * rows)
        var head = 0
        var tail = 0
        val sc = (px / cell).toInt().coerceIn(0, cols - 1)
        val sr = (py / cell).toInt().coerceIn(0, rows - 1)
        seen[sr * cols + sc] = true
        queue[tail++] = sr * cols + sc
        while (head < tail) {
            val cur = queue[head++]
            val r = cur / cols
            val c = cur % cols
            for (k in 0 until 4) {
                val nr = r + (if (k == 0) 1 else if (k == 1) -1 else 0)
                val nc = c + (if (k == 2) 1 else if (k == 3) -1 else 0)
                if (nr < 0 || nc < 0 || nr >= rows || nc >= cols) continue
                val idx = nr * cols + nc
                if (seen[idx]) continue
                if (!arena.isFree(nc * cell + cell / 2f, nr * cell + cell / 2f, playerRadius)) continue
                seen[idx] = true
                queue[tail++] = idx
            }
        }
        return { x, y ->
            val c = (x / cell).toInt().coerceIn(0, cols - 1)
            val r = (y / cell).toInt().coerceIn(0, rows - 1)
            seen[r * cols + c]
        }
    }

    private fun enterShop() {
        goingToShop = false
        shopGateOpen = false
        skipShopPrompt = false
        topGateLocked = false
        shopArrowFlash = 0f
        val room = ArenaGenerator.shopRoom(Random(rng.nextLong()))
        plan = LevelPlan(level, LevelKind.SHOP, room, emptyList())
        arena = Arena(room)
        clearAll()
        px = arena.spawnX
        py = arena.spawnY
        facing = -MathUtil.PI / 2f
        phaseTimer = 0f
        shopItems = rollShopItems()
        keeperLook = KeeperLook.forStock(shopItems)
        phase = Phase.PORTAL
        portalOpen = true
        invuln = 1f
        showBanner("UPGRADE SHOP", "GOLDEN & TITANIUM MODS · PAY WITH RUN €", 2f)
    }

    /**
     * Four distinct GOLDEN-or-better mods the build can still take, priced by
     * rarity and depth. Drawn by rarity weight, so TITANIUM stays the rare find.
     */
    private fun rollShopItems(): List<ShopItem> {
        val pool = Upgrades.all.filter { !it.instant && it.rarity.ordinal >= com.cyberoperative.game.data.Rarity.LEGENDARY.ordinal && build.isEligible(it) }.toMutableList()
        val picked = ArrayList<com.cyberoperative.game.data.UpgradeDef>()
        while (picked.size < 4 && pool.isNotEmpty()) {
            val total = pool.sumOf { it.rarity.weight.toDouble() }.toFloat()
            var roll = rng.nextFloat() * total
            var chosen = pool.last()
            for (d in pool) { roll -= d.rarity.weight; if (roll <= 0f) { chosen = d; break } }
            pool.remove(chosen)
            picked += chosen
        }
        return picked.map { ShopItem(it, build.level(it.id) + 1, shopPrice(it.rarity, level), sold = false) }
    }

    /** How the shopkeeper looks this visit (fixed when the shop opens). */
    var keeperLook = KeeperLook.GOLD
        private set

    // --- "Skip the shop?" (owner, 2026-10-08) -----------------------------------
    /** The top gate was touched while a shop is on offer: ask before skipping it. */
    var skipShopPrompt = false
        private set
    /** The player said NO: the top gate shows locked (red) until they change their mind. */
    var topGateLocked = false
        private set
    /** Seconds left of the flashing arrow pointing at the shop gate. */
    var shopArrowFlash = 0f
        private set

    /** UI: answer the skip prompt. YES leaves for the next level; NO locks the gate and points at the shop. */
    fun answerSkipShop(skip: Boolean) {
        if (!skipShopPrompt) return
        skipShopPrompt = false
        if (skip) {
            shopGateOpen = false
            topGateLocked = false
            phase = Phase.TRANSITION
            phaseTimer = 0f
            goingToShop = false
        } else {
            topGateLocked = true
            shopArrowFlash = SHOP_ARROW_SECONDS
            sound(GameSound.UI_BACK)
        }
    }

    /** UI: buy table item [index] with this run's €. */
    fun buyShopItem(index: Int): Boolean {
        val item = shopItems.getOrNull(index) ?: return false
        if (!inShop || item.sold || eurosEarned < item.price || !build.isEligible(item.def)) return false
        eurosEarned -= item.price
        applyUpgrade(item.def)
        shopItems = shopItems.mapIndexed { i, it -> if (i == index) it.copy(sold = true) else it }
        addText(px, py - 40f, "-${item.price} €", TextKind.INFO)
        sound(GameSound.CURRENCY)
        sound(GameSound.UPGRADE_SELECTED)
        return true
    }

    /** Applies an upgrade's immediate side effects (HP gained, firewall refilled). Used by the shop. */
    private fun applyUpgrade(def: com.cyberoperative.game.data.UpgradeDef) {
        val wasMaxHp = stats.maxHp
        build.take(def)
        val gained = stats.maxHp - wasMaxHp
        if (gained > 0f) hp += gained
        hp = min(hp, stats.maxHp)
        if (def.id in FIREWALL_IDS) firewall = stats.firewallMax
    }

    private fun openPortal() {
        phase = Phase.PORTAL
        phaseTimer = 0f
        portalOpen = true
        sound(GameSound.PORTAL_OPEN)
    }

    private fun die() {
        hp = 0f
        phase = Phase.DEAD
        phaseTimer = 0f
        addPulse(px, py, 260f, 0.9f, 0xFFFF2D55)
        repeat(30) { addParticle(px, py, 0xFF00E5FF, 260f, 0.9f, 4f) }
        sound(GameSound.GAME_OVER)
    }

    val canRevive: Boolean get() = phase == Phase.DEAD && revivesLeft > 0

    /**
     * Revive in place (§34): half HP, full firewall, a long grace period and
     * every hostile projectile/hazard near the operative wiped. Limited per run.
     */
    fun revive(paid: Boolean = false): Boolean {
        if (phase != Phase.DEAD) return false
        if (!paid) {
            if (revivesLeft <= 0) return false
            revivesLeft--
        }
        revivesUsed++
        hp = stats.maxHp * 0.5f
        firewall = stats.firewallMax
        invuln = 2.5f
        for (p in projectiles.items) if (p.active && !p.friendly) p.active = false
        for (h in hazards.items) h.active = false
        // Push nearby enemies back so the revive is not an instant re-death.
        for (e in enemies.items) {
            if (!e.active || e.boss != null) continue
            val d = MathUtil.dist(px, py, e.x, e.y)
            if (d < 220f && d > 0.1f) {
                e.x += (e.x - px) / d * (220f - d)
                e.y += (e.y - py) / d * (220f - d)
                arena.pushOut(e.x, e.y, e.radius)
                e.x = arena.out[0]; e.y = arena.out[1]
            }
        }
        addPulse(px, py, 240f, 0.6f, 0xFF00FF9C)
        phase = if (aliveCount() == 0 && portalOpen) Phase.PORTAL else Phase.COMBAT
        phaseTimer = 0f
        sound(GameSound.REVIVE)
        return true
    }

    private val difficultyReward: Float get() = config.difficulty.rewardMul

    // ======================================================================
    // Save & continue (owner, 2026-10-08)
    // ======================================================================

    /** True while a boss is alive or its level is still being fought. */
    val bossActive: Boolean
        get() = boss != null || (plan.kind == LevelKind.BOSS && phase == Phase.COMBAT)

    /**
     * Why the run cannot be saved right now, or null when it can. A boss
     * fight blocks saving until the boss is defeated.
     */
    val saveBlockReason: String?
        get() = when {
            phase == Phase.DEAD -> "OPERATIVE DOWN"
            bossActive -> "[BOSS] SAVE BLOCKED"
            phase == Phase.TRANSITION || slideIn > 0f -> "ENTERING NEXT ROOM"
            inShop -> "UPGRADE SHOP · SAVE AFTER LEAVING"
            else -> null
        }

    /** The run as it stands, for "save & exit". Null when [saveBlockReason] is set. */
    fun snapshot(): RunSnapshot? {
        if (saveBlockReason != null) return null
        val saved = ArrayList<SavedEnemy>()
        for (e in enemies.items) {
            if (!e.active || e.boss != null) continue
            saved += SavedEnemy(
                def = e.def.id, elite = e.elite?.name, x = e.x, y = e.y, hp = e.hp, maxHp = e.maxHp,
                radius = e.radius, speed = e.speed, damageMul = e.damageMul, attackRateMul = e.attackRateMul,
                damageTakenMul = e.damageTakenMul, rewardMul = e.rewardMul, isChild = e.isChild
            )
        }
        return RunSnapshot(
            mode = mode.name, difficulty = config.difficulty.name, seed = config.seed,
            level = level, levelSeed = levelSeed, previousArenaId = previousArenaId, previousEvent = previousEvent,
            phase = phase.name, phaseTimer = phaseTimer,
            px = px, py = py, hp = hp, firewall = firewall, facing = facing,
            upgrades = HashMap(build.owned()), runLevel = runLevel, xp = xp,
            pendingUpgrades = pendingUpgrades, rewardBatchTotal = rewardBatchTotal, rewardBatchTaken = rewardBatchTaken,
            offerLuck = offerLuck, offer = offer.map { it.def.id }, upgradeReturnsToCombat = upgradeReturnsToCombat,
            rerollsLeft = rerollsLeft, revivesLeft = revivesLeft, revivesUsed = revivesUsed,
            score = score, kills = kills, bosses = bossesDefeated, elites = elitesDefeated, events = eventsCompleted,
            completedEventIds = HashSet(completedEventIds), euros = eurosEarned, diamonds = diamondsEarned,
            runSeconds = runSeconds, levelSeconds = levelSeconds, levelDamageTaken = levelDamageTaken,
            levelEnemyTotal = levelEnemyTotal, levelKills = levelKills, levelSpawned = levelSpawned,
            stageTimer = stageTimer, timedRemaining = timedRemaining, portalOpen = portalOpen,
            waveIndex = waveIndex, waveTimer = waveTimer, spawnTimer = spawnTimer, hazardTimer = hazardTimer,
            enemies = saved
        )
    }

    private fun applySnapshot(r: RunSnapshot) {
        level = r.level
        levelSeed = r.levelSeed
        previousArenaId = r.previousArenaId
        previousEvent = r.previousEvent
        // The same seed rebuilds the same room, waves and event rules.
        plan = LevelPlanner.plan(level, Random(levelSeed), previousArenaId, previousEvent, mode)
        val extra = if (plan.rules.vault) listOf(Arena.vaultObstacle(plan.arena)) else emptyList()
        arena = Arena(plan.arena, extra)
        clearAll()
        build.restore(r.upgrades)
        updateAdaptive()
        runLevel = r.runLevel; xp = r.xp
        pendingUpgrades = r.pendingUpgrades
        rewardBatchTotal = r.rewardBatchTotal; rewardBatchTaken = r.rewardBatchTaken
        offerLuck = r.offerLuck
        upgradeReturnsToCombat = r.upgradeReturnsToCombat
        rerollsLeft = r.rerollsLeft; revivesLeft = r.revivesLeft; revivesUsed = r.revivesUsed
        score = r.score; kills = r.kills; bossesDefeated = r.bosses; elitesDefeated = r.elites
        eventsCompleted = r.events; completedEventIds += r.completedEventIds
        eurosEarned = r.euros; diamondsEarned = r.diamonds; runSeconds = r.runSeconds
        levelSeconds = r.levelSeconds; levelDamageTaken = r.levelDamageTaken; levelEnemyTotal = r.levelEnemyTotal
        levelKills = r.levelKills; levelSpawned = r.levelSpawned; stageTimer = r.stageTimer
        timedRemaining = r.timedRemaining; portalOpen = r.portalOpen
        waveIndex = r.waveIndex; waveTimer = r.waveTimer; spawnTimer = r.spawnTimer; hazardTimer = r.hazardTimer
        px = r.px; py = r.py; facing = r.facing
        hp = r.hp.coerceIn(1f, stats.maxHp)
        firewall = r.firewall.coerceIn(0f, stats.firewallMax)
        for (se in r.enemies) {
            val def = try { Enemies.byId(se.def) } catch (_: Exception) { continue }
            val e = enemies.obtain() ?: break
            e.active = true; e.uid = nextUid++; e.def = def
            e.elite = se.elite?.let { n -> EliteModifier.entries.firstOrNull { it.name == n } }
            e.boss = null; e.isChild = se.isChild
            e.x = se.x; e.y = se.y; e.vx = 0f; e.vy = 0f
            e.hp = se.hp; e.maxHp = se.maxHp; e.radius = se.radius; e.speed = se.speed
            e.damageMul = se.damageMul; e.attackRateMul = se.attackRateMul
            e.damageTakenMul = se.damageTakenMul; e.rewardMul = se.rewardMul
            // A short spawn-in so nothing hits the moment the run resumes.
            e.state = AiState.SPAWNING; e.stateTimer = SPAWN_TELEGRAPH
            e.attackTimer = def.attackCooldown * (0.6f + rng.nextFloat() * 0.6f)
            e.strafeDir = if (rng.nextBoolean()) 1f else -1f
            e.wobble = rng.nextFloat() * MathUtil.TWO_PI
            e.hitFlash = 0f; e.orbHitCooldown = 0f; e.bladeHitCooldown = 0f; e.contactCooldown = 0f
            e.stuckTimer = 0f; e.detourTimer = 0f; e.navTimer = 0f; e.navValid = false; e.lastX = e.x; e.lastY = e.y
        }
        phase = Phase.entries.firstOrNull { it.name == r.phase } ?: Phase.COMBAT
        phaseTimer = r.phaseTimer
        if (phase == Phase.UPGRADE) {
            val restored = r.offer.mapNotNull { id ->
                Upgrades.all.firstOrNull { it.id == id }?.let { UpgradeOffer(it, if (it.instant) 1 else build.level(it.id) + 1) }
            }
            offer = restored.ifEmpty { build.rollOffer(rng, luck = offerLuck) }
            if (offer.isEmpty()) { pendingUpgrades = 0; finishUpgrades() }
        }
        invuln = 2f
        showBanner("OPERATION RESUMED", if (mode == GameMode.ENDLESS) "STAGE $level" else "LEVEL $level", 1.6f)
    }

    fun summary(): RunSummary = RunSummary(
        levelReached = level, score = score, kills = kills, bosses = bossesDefeated,
        elites = elitesDefeated, events = eventsCompleted, euros = eurosEarned,
        diamonds = diamondsEarned, seconds = runSeconds
    )

    // ======================================================================
    // Player
    // ======================================================================

    private fun updatePlayer(dt: Float) {
        val s = stats
        val wasBeaming = beamActive
        beamActive = false
        if (invuln > 0f) invuln -= dt
        if (hurtFlash > 0f) hurtFlash -= dt
        sinceDamage += dt

        // Regeneration and firewall recharge.
        if (s.regenPerSec > 0f && hp < s.maxHp) heal(s.maxHp * s.regenPerSec * dt, quiet = true)
        if (s.firewallMax > 0f && sinceDamage >= s.firewallDelay && firewall < s.firewallMax) {
            firewall = min(s.firewallMax, firewall + s.firewallMax * s.firewallRate * dt)
            if (firewall >= s.firewallMax && !firewallWasUp) {
                firewallWasUp = true
                sound(GameSound.FIREWALL_UP)
            }
        }

        // Movement.
        val mag = sqrt(inputX * inputX + inputY * inputY)
        moving = mag > MOVE_DEADZONE
        if (moving) {
            val m = min(1f, mag)
            val speed = s.moveSpeed * m
            val nx = px + inputX / mag * speed * dt
            val ny = py + inputY / mag * speed * dt
            arena.pushOut(nx, ny, playerRadius)
            px = arena.out[0]
            py = arena.out[1]
            facing = atan2(inputY, inputX)
            stillTime = 0f
            followUpLeft = 0
        } else {
            stillTime += dt
        }

        fireCooldown -= dt
        lanceTimer -= dt
        if (beamCooldown > 0f) beamCooldown = max(0f, beamCooldown - dt)
        else if (!wasBeaming) beamHeat = max(0f, beamHeat - dt * 0.5f)
        if (phase != Phase.COMBAT) {
            targetUid = -1
            return
        }

        // EMP Burst is an always-on ability.
        if (s.empLevel > 0) {
            empTimer -= dt
            if (empTimer <= 0f) {
                empTimer = 7f - s.empLevel
                firePulseDamage(px, py, 150f + 25f * s.empLevel, s.damage * (0.9f + 0.3f * s.empLevel), clearsProjectiles = true)
                sound(GameSound.EMP)
            }
        }

        updateAutoWeapons(dt)
        updateArsenal(dt)

        // STOP = SHOOT.
        if (moving || stillTime < STOP_TO_FIRE_DELAY) {
            targetUid = -1
            return
        }
        val target = acquireTarget()
        if (target == null) {
            targetUid = -1
            return
        }
        targetUid = target.uid
        facing = atan2(target.y - py, target.x - px)

        if (fireCooldown <= 0f) {
            fireVolley(facing)
            fireCooldown = 1f / s.fireRate
            followUpLeft = s.followUpShots
            followUpTimer = FOLLOW_UP_GAP
            if (s.coneLevel > 0) fireCone(facing)
        } else if (followUpLeft > 0) {
            followUpTimer -= dt
            if (followUpTimer <= 0f) {
                fireVolley(facing)
                followUpLeft--
                followUpTimer = FOLLOW_UP_GAP
            }
        }
        if (s.lanceLevel > 0 && lanceTimer <= 0f) {
            lanceTimer = 2.8f - 0.4f * s.lanceLevel
            fireLance(facing)
        }
        if (s.beamLevel > 0 && beamCooldown <= 0f) {
            if (updateBeam(dt, target)) beamHeat += dt
            if (!wasBeaming) sound(GameSound.BEAM)
            if (beamHeat >= BEAM_MAX_FIRE) {
                beamHeat = 0f
                beamCooldown = BEAM_COOLDOWN
                addText(px, py - 44f, "BEAM OVERHEAT", TextKind.INFO)
            }
        }
        if (s.railLevel > 0) {
            railTimer -= dt
            if (railTimer <= 0f) {
                railTimer = 3.6f - 0.8f * s.railLevel
                fireRail(facing)
            }
        }
    }

    // ======================================================================
    // Extra weapons (owner, 2026-10-08)
    // ======================================================================

    /** Weapons that work whether or not the operative is moving. */
    private fun updateAutoWeapons(dt: Float) {
        val s = stats
        if (s.mineLevel > 0 && moving) {
            mineTimer -= dt
            if (mineTimer <= 0f) {
                mineTimer = 0.9f
                dropMine()
            }
        }
        if (s.missileLevel > 0) {
            missileTimer -= dt
            if (missileTimer <= 0f && nearestEnemy(px, py, 520f) != null) {
                missileTimer = 2.8f - 0.4f * s.missileLevel
                val n = 1 + s.missileLevel
                for (i in 0 until n) {
                    val a = facing + MathUtil.PI + (i - (n - 1) / 2f) * 0.55f
                    val p = spawnBolt(px, py, a, ProjKind.MISSILE, s.damage * 1.1f) ?: break
                    p.vx *= 0.45f; p.vy *= 0.45f
                    p.homing = 7f
                    p.radius = 7f
                    p.life = 3f
                    p.pierceLeft = 0; p.bounceLeft = 0; p.chainLeft = 0
                    p.splash = 60f
                }
                sound(GameSound.LANCE)
            }
        }
        if (s.arcLevel > 0) {
            arcTimer -= dt
            if (arcTimer <= 0f) {
                if (fireArcs(2 + s.arcLevel, s.damage * (1.3f + 0.3f * s.arcLevel))) arcTimer = 2.4f - 0.4f * s.arcLevel
            }
        }
        if (s.strikeLevel > 0) {
            strikeTimer -= dt
            if (strikeTimer <= 0f) {
                if (callOrbitalStrikes(3, s.damage * 8f)) strikeTimer = 5f
            }
        }
    }

    /** Every projectile comes from here so pooled fields never leak between uses. */
    private fun newProjectile(): Projectile? {
        val p = projectiles.obtain() ?: return null
        p.tint = 0L
        p.returning = false
        p.armTimer = 0f
        p.splash = 0f
        return p
    }

    // ======================================================================
    // Arsenal: data-driven auto-weapons (data/Weapons.kt)
    // ======================================================================

    private val weaponTimers = HashMap<String, Float>()

    private class SpiralBurst(val spec: WeaponSpec, val damage: Float, var left: Int, var angle: Float, val step: Float) {
        var timer = 0f
    }
    private val spirals = ArrayList<SpiralBurst>()

    private fun updateArsenal(dt: Float) {
        val s = stats
        if (spirals.isNotEmpty()) {
            val it = spirals.iterator()
            while (it.hasNext()) {
                val b = it.next()
                b.timer -= dt
                while (b.timer <= 0f && b.left > 0) {
                    arsenalBolt(b.spec, b.angle, b.damage, ProjKind.BOLT)
                    b.angle += b.step
                    b.left--
                    b.timer += 0.045f
                }
                if (b.left <= 0) it.remove()
            }
        }
        if (s.weapons.isEmpty()) return
        for ((id, level) in s.weapons) {
            val w = Weapons.byId(id) ?: continue
            if (w.stillOnly && moving) continue
            val t = (weaponTimers[id] ?: (0.4f + rng.nextFloat() * 0.8f)) - dt
            if (t > 0f) { weaponTimers[id] = t; continue }
            weaponTimers[id] = if (fireWeapon(w, level)) w.cooldownAt(level) else 0.25f
        }
    }

    /** Fires one use of an arsenal weapon. False when it had nothing to shoot at. */
    private fun fireWeapon(w: WeaponSpec, level: Int): Boolean {
        val s = stats
        val dmg = s.damage * w.damageAt(level)
        val n = w.countAt(level)
        val target = nearestEnemy(px, py, 680f)
        val spread = Math.toRadians(w.spreadDegrees.toDouble()).toFloat()
        fun fanAngle(base: Float, i: Int) = if (n <= 1) base else base + spread * (i / (n - 1f) - 0.5f)
        when (w.kind) {
            WeaponKind.VOLLEY -> {
                val t = target ?: return false
                val base = atan2(t.y - py, t.x - px)
                val kind = if (w.splash > 0f) ProjKind.MISSILE else ProjKind.BOLT
                for (i in 0 until n) arsenalBolt(w, fanAngle(base, i) + if (spread == 0f && n > 1) (i - (n - 1) / 2f) * 0.08f else 0f, dmg, kind)
            }
            WeaponKind.RING -> {
                if (target == null) return false
                val off = rng.nextFloat() * MathUtil.TWO_PI
                for (i in 0 until n) arsenalBolt(w, off + MathUtil.TWO_PI * i / n, dmg, ProjKind.BOLT)
            }
            WeaponKind.SPIRAL -> {
                val t = target ?: return false
                spirals += SpiralBurst(w, dmg, n, atan2(t.y - py, t.x - px), MathUtil.TWO_PI * 1.5f / n)
            }
            WeaponKind.NOVA -> {
                var hit = false
                for (e in enemies.items) {
                    if (!e.targetable) continue
                    val r = w.radius + e.radius
                    if (MathUtil.dist2(px, py, e.x, e.y) < r * r) {
                        damageEnemy(e, dmg * plan.rules.playerDamageMul, false, ProjKind.BOLT, quiet = true)
                        hit = true
                    }
                }
                if (!hit) return false
                addPulse(px, py, w.radius, 0.45f, w.color)
                addPulse(px, py, w.radius * 0.6f, 0.3f, 0xFFFFFFFF)
                sound(GameSound.EMP)
            }
            WeaponKind.LASER -> {
                val t = target ?: return false
                val base = atan2(t.y - py, t.x - px)
                for (i in 0 until n) fireLaser(fanAngle(base, i), w, dmg)
                sound(GameSound.LANCE)
            }
            WeaponKind.ARC -> if (!fireArcs(n, dmg, w.color)) return false
            WeaponKind.STRIKE -> if (!callOrbitalStrikes(n, dmg, w.radius, w.color)) return false
            WeaponKind.MINES -> {
                if (target == null) return false
                repeat(n) { dropMine(n + 2, dmg * plan.rules.playerDamageMul, w.splash, w.color, jitter = it > 0) }
            }
            WeaponKind.FIELD -> {
                if (nearestEnemy(px, py, w.radius + 260f) == null) return false
                val z = zaps.obtain() ?: return false
                z.active = true; z.kind = ZapKind.FIELD
                z.x = px; z.y = py; z.radius = w.radius
                z.timer = 0f; z.duration = 3f; z.damage = dmg * plan.rules.playerDamageMul
                z.color = w.color; z.tick = 0f; z.seed = rng.nextInt()
            }
            WeaponKind.BOOMERANG -> {
                val t = target ?: return false
                val base = atan2(t.y - py, t.x - px)
                for (i in 0 until n) {
                    val p = arsenalBolt(w, fanAngle(base, i), dmg, ProjKind.BOOMERANG) ?: break
                    p.pierceLeft = 999
                    p.ghost = true
                    p.radius = 11f
                    p.armTimer = 0.6f
                    p.life = 3.5f
                }
            }
        }
        return true
    }

    private fun arsenalBolt(w: WeaponSpec, angle: Float, damage: Float, kind: ProjKind): Projectile? {
        val p = spawnBolt(px, py, angle, kind, damage) ?: return null
        p.vx *= w.speed; p.vy *= w.speed
        p.pierceLeft = w.pierce
        p.bounceLeft = 0
        p.chainLeft = 0
        p.homing = w.homing
        p.splash = w.splash
        p.tint = w.color
        if (kind == ProjKind.MISSILE) { p.radius = 9f; p.life = 3f }
        return p
    }

    /** Instant beam: damages every threat along it; stops at cover unless [WeaponSpec.throughWalls]. */
    private fun fireLaser(angle: Float, w: WeaponSpec, damage: Float) {
        val dx = cos(angle)
        val dy = sin(angle)
        var len = 0f
        while (len < w.length) {
            val nx = px + dx * (len + 10f)
            val ny = py + dy * (len + 10f)
            if (nx < 0f || ny < 0f || nx > arena.width || ny > arena.height) break
            if (!w.throughWalls && arena.obstacleAt(nx, ny, 2f) >= 0) break
            len += 10f
        }
        val x2 = px + dx * len
        val y2 = py + dy * len
        for (e in enemies.items) {
            if (!e.targetable) continue
            if (distToSegment(e.x, e.y, px, py, x2, y2) < e.radius + 8f) {
                damageEnemy(e, damage * plan.rules.playerDamageMul, false, ProjKind.LANCE, quiet = true)
            }
        }
        val z = zaps.obtain() ?: return
        z.active = true; z.kind = ZapKind.LASER
        z.x = px; z.y = py; z.x2 = x2; z.y2 = y2
        z.timer = 0f; z.duration = 0.25f; z.color = w.color; z.radius = if (w.rarity.highTier >= 2) 9f else 6f
    }

    private fun dropMine() {
        val s = stats
        dropMine(2 + 2 * s.mineLevel, s.damage * (1.6f + 0.4f * s.mineLevel) * plan.rules.playerDamageMul, 85f, 0L, jitter = false)
    }

    private fun dropMine(maxLive: Int, damage: Float, splash: Float, tint: Long, jitter: Boolean) {
        var live = 0
        for (p in projectiles.items) if (p.active && p.kind == ProjKind.MINE && p.tint == tint) live++
        if (live >= maxLive) return
        val p = newProjectile() ?: return
        p.active = true; p.friendly = true; p.kind = ProjKind.MINE
        p.x = px + (if (jitter) (rng.nextFloat() - 0.5f) * 60f else 0f)
        p.y = py + (if (jitter) (rng.nextFloat() - 0.5f) * 60f else 0f)
        p.vx = 0f; p.vy = 0f
        p.radius = 9f; p.crit = false
        p.damage = damage
        p.life = 25f; p.pierceLeft = 0; p.bounceLeft = 0; p.chainLeft = 0
        p.lastHitUid = -1; p.ghost = true; p.homing = 0f
        p.armTimer = 0.5f; p.splash = splash; p.tint = tint
    }

    /** Arc Discharge: instant lightning to the [count] nearest threats. Returns false if none. */
    private fun fireArcs(count: Int, damage: Float, color: Long = 0xFFA259FF): Boolean {
        var hit = 0
        var fromX = px
        var fromY = py
        val used = HashSet<Int>()
        while (hit < count) {
            var best: Enemy? = null
            var bd = 340f * 340f
            for (e in enemies.items) {
                if (!e.targetable || e.uid in used) continue
                val d = MathUtil.dist2(px, py, e.x, e.y)
                if (d < bd) { bd = d; best = e }
            }
            val e = best ?: break
            used += e.uid
            val z = zaps.obtain()
            if (z != null) {
                z.active = true; z.kind = ZapKind.ARC
                z.x = fromX; z.y = fromY; z.x2 = e.x; z.y2 = e.y
                z.timer = 0f; z.duration = 0.22f; z.seed = rng.nextInt(); z.color = color
            }
            damageEnemy(e, damage * plan.rules.playerDamageMul, false, ProjKind.NODE_BOLT, quiet = true)
            fromX = e.x; fromY = e.y
            hit++
        }
        if (hit > 0) sound(GameSound.EMP)
        return hit > 0
    }

    /** Orbital Strike: marks up to [count] random threats; each strike lands after a telegraph. */
    private fun callOrbitalStrikes(count: Int, damage: Float, radius: Float = 90f, color: Long = 0xFFDCE8F2): Boolean {
        val targets = enemies.items.filter { it.targetable }.shuffled(rng).take(count)
        if (targets.isEmpty()) return false
        for (e in targets) {
            val z = zaps.obtain() ?: break
            z.active = true; z.kind = ZapKind.STRIKE
            z.x = e.x; z.y = e.y; z.radius = radius; z.color = color
            z.timer = 0f; z.duration = 0.75f; z.damage = damage * plan.rules.playerDamageMul
            z.landed = false; z.seed = rng.nextInt()
        }
        return true
    }

    private fun fireRail(angle: Float) {
        val s = stats
        val p = spawnBolt(px, py, angle, ProjKind.RAIL, s.damage * (6f + 2f * s.railLevel)) ?: return
        p.pierceLeft = 999
        p.ghost = true
        p.radius = 12f
        p.vx *= 2.2f; p.vy *= 2.2f
        p.life = 1.2f
        p.bounceLeft = 0; p.chainLeft = 0
        addPulse(px, py, 60f, 0.25f, 0xFFE7D4FF)
        sound(GameSound.LANCE)
    }

    /** Missile / mine detonation: damages every threat in [radius]. */
    private fun detonate(x: Float, y: Float, radius: Float, damage: Float) {
        for (e in enemies.items) {
            if (!e.targetable) continue
            val r = radius + e.radius
            if (MathUtil.dist2(x, y, e.x, e.y) < r * r) damageEnemy(e, damage, false, ProjKind.BOLT, quiet = true)
        }
        addPulse(x, y, radius, 0.35f, 0xFFFF9A1A)
        repeat(8) { addParticle(x, y, 0xFFFFC14D, 220f, 0.35f, 3f) }
        sound(GameSound.ELITE_DEATH)
    }

    private fun updateZaps(dt: Float) {
        for (z in zaps.items) {
            if (!z.active) continue
            z.timer += dt
            if (z.kind == ZapKind.FIELD) {
                z.tick -= dt
                if (z.tick <= 0f) {
                    z.tick = 0.25f
                    for (e in enemies.items) {
                        if (!e.targetable) continue
                        val r = z.radius + e.radius * 0.5f
                        if (MathUtil.dist2(z.x, z.y, e.x, e.y) < r * r) damageEnemy(e, z.damage * 0.25f, false, ProjKind.BOLT, quiet = true, showText = false)
                    }
                }
            }
            if (z.kind == ZapKind.STRIKE && !z.landed && z.timer >= z.duration) {
                z.landed = true
                detonate(z.x, z.y, z.radius, z.damage)
                addPulse(z.x, z.y, z.radius * 1.4f, 0.5f, if (z.color != 0L) z.color else 0xFFDCE8F2)
            }
            val end = if (z.kind == ZapKind.STRIKE) z.duration + 0.3f else z.duration
            if (z.timer >= end) z.active = false
        }
    }

    /**
     * Plasma Beam: a continuous ray toward the target, stopped by the first
     * obstacle it meets, damaging every threat along it ten times a second.
     */
    /** Returns true while the beam is actually touching a threat (that is what builds heat). */
    private fun updateBeam(dt: Float, target: Enemy): Boolean {
        val s = stats
        val ang = atan2(target.y - py, target.x - px)
        val dx = cos(ang)
        val dy = sin(ang)
        val sx = px + dx * (playerRadius + 6f)
        val sy = py + dy * (playerRadius + 6f)
        var len = 0f
        val maxLen = s.range
        while (len < maxLen) {
            val nx = sx + dx * (len + 8f)
            val ny = sy + dy * (len + 8f)
            if (nx < 0f || ny < 0f || nx > arena.width || ny > arena.height || arena.obstacleAt(nx, ny, 2f) >= 0) break
            len += 8f
        }
        beamActive = true
        beamX2 = sx + dx * len
        beamY2 = sy + dy * len
        val touching = enemies.items.any { it.targetable && distToSegment(it.x, it.y, sx, sy, beamX2, beamY2) < it.radius + beamWidth * 0.5f }
        beamTick -= dt
        if (beamTick > 0f) return touching
        beamTick = 0.1f
        val dps = s.damage * (1.4f + 0.6f * s.beamLevel) * plan.rules.playerDamageMul
        val half = beamWidth * 0.5f
        for (e in enemies.items) {
            if (!e.targetable) continue
            if (distToSegment(e.x, e.y, sx, sy, beamX2, beamY2) < e.radius + half) {
                // No number per beam tick (ten a second would bury the screen).
                damageEnemy(e, dps * 0.1f, false, ProjKind.LANCE, quiet = true, showText = false)
                if (rng.nextFloat() < 0.3f) addParticle(e.x, e.y, 0xFF7DF9FF, 160f, 0.3f, 3f)
            }
        }
        return touching
    }

    /**
     * Target priority (§8): the nearest visible threat, with enemies that are
     * actively threatening the player (very close) always first, and a boss
     * preferred when nothing is pressing.
     */
    fun acquireTarget(): Enemy? {
        val range2 = stats.range * stats.range
        var best: Enemy? = null
        var bestScore = Float.MAX_VALUE
        var fallback: Enemy? = null
        var fallbackD = Float.MAX_VALUE
        for (e in enemies.items) {
            if (!e.targetable) continue
            val d2 = MathUtil.dist2(px, py, e.x, e.y)
            if (d2 > range2) continue
            if (d2 < fallbackD) { fallbackD = d2; fallback = e }
            // Same margin a bolt collides with (radius 6 × 0.6), so a "visible"
            // target is never one whose shots clip a corner forever.
            if (!arena.lineOfSight(px, py, e.x, e.y, SHOT_CLEARANCE)) continue
            var score = d2
            if (d2 < THREAT_RADIUS * THREAT_RADIUS) score *= 0.25f
            else if (e.boss != null) score *= 0.6f
            if (e.uid == targetUid) score *= 0.85f // slight stickiness: predictable targeting
            if (score < bestScore) { bestScore = score; best = e }
        }
        return best ?: fallback
    }

    private fun fireVolley(angle: Float) {
        val s = stats
        val n = s.parallelShots
        val perpX = -sin(angle)
        val perpY = cos(angle)
        for (i in 0 until n) {
            val off = (i - (n - 1) / 2f) * 15f
            spawnBolt(px + perpX * off, py + perpY * off, angle, ProjKind.BOLT, s.damage)
        }
        for (k in 1..s.diagonalPairs) {
            val a = 0.32f * k
            spawnBolt(px, py, angle + a, ProjKind.BOLT, s.damage * 0.8f)
            spawnBolt(px, py, angle - a, ProjKind.BOLT, s.damage * 0.8f)
        }
        if (s.rearShot) spawnBolt(px, py, angle + MathUtil.PI, ProjKind.BOLT, s.damage * 0.8f)
        sound(GameSound.PLAYER_SHOT)
    }

    private fun fireCone(angle: Float) {
        val s = stats
        val pellets = 1 + 2 * s.coneLevel
        val spread = 0.9f
        for (i in 0 until pellets) {
            val t = if (pellets == 1) 0f else i / (pellets - 1f) - 0.5f
            val p = spawnBolt(px, py, angle + t * spread, ProjKind.CONE, s.damage * 0.45f) ?: continue
            p.life = 0.42f
            p.radius = 5f
        }
    }

    private fun fireLance(angle: Float) {
        val s = stats
        val p = spawnBolt(px, py, angle, ProjKind.LANCE, s.damage * (2.5f + 0.5f * s.lanceLevel)) ?: return
        p.pierceLeft = 999
        p.radius = 10f
        p.vx *= 0.8f
        p.vy *= 0.8f
        p.bounceLeft = 0
        p.chainLeft = 0
        sound(GameSound.LANCE)
    }

    private fun spawnBolt(x: Float, y: Float, angle: Float, kind: ProjKind, damage: Float): Projectile? {
        val p = newProjectile() ?: return null
        val s = stats
        p.active = true
        p.friendly = true
        p.kind = kind
        p.x = x + cos(angle) * (playerRadius + 4f)
        p.y = y + sin(angle) * (playerRadius + 4f)
        p.vx = cos(angle) * s.projectileSpeed
        p.vy = sin(angle) * s.projectileSpeed
        p.radius = 6f
        val crit = rng.nextFloat() < s.critChance
        p.crit = crit
        p.damage = damage * plan.rules.playerDamageMul * (if (crit) s.critMul else 1f)
        p.life = s.range / s.projectileSpeed * 1.25f
        p.pierceLeft = s.pierce
        p.bounceLeft = s.bounce
        p.chainLeft = s.chain
        p.lastHitUid = -1
        p.ghost = false
        p.homing = 0f
        p.armTimer = 0f
        p.splash = 0f
        return p
    }

    private fun updateOrbit(dt: Float) {
        val s = stats
        orbAngle = (orbAngle + s.orbAngularSpeed * dt) % MathUtil.TWO_PI
        bladeAngle = (bladeAngle - 3.4f * dt) % MathUtil.TWO_PI
        if (phase != Phase.COMBAT) return

        // Packet Nodes: contact damage with a per-enemy cooldown.
        val orbs = s.orbCount
        for (e in enemies.items) {
            if (!e.targetable) continue
            if (e.orbHitCooldown > 0f) e.orbHitCooldown -= dt
            if (e.bladeHitCooldown > 0f) e.bladeHitCooldown -= dt
            if (e.orbHitCooldown <= 0f) {
                for (i in 0 until orbs) {
                    val a = orbAngle + MathUtil.TWO_PI * i / orbs
                    val ox = px + cos(a) * s.orbRadius
                    val oy = py + sin(a) * s.orbRadius
                    val rr = s.orbSize + e.radius
                    if (MathUtil.dist2(ox, oy, e.x, e.y) < rr * rr) {
                        e.orbHitCooldown = ORB_HIT_COOLDOWN
                        damageEnemy(e, s.orbDamage * plan.rules.playerDamageMul, false, ProjKind.BOLT, quiet = true)
                        sound(GameSound.ORB_HIT)
                        break
                    }
                }
            }
            if (s.bladeCount > 0 && e.active && e.bladeHitCooldown <= 0f) {
                for (i in 0 until s.bladeCount) {
                    val a = bladeAngle + MathUtil.TWO_PI * i / s.bladeCount
                    val bx = px + cos(a) * s.bladeRadius
                    val by = py + sin(a) * s.bladeRadius
                    val rr = 14f + e.radius
                    if (MathUtil.dist2(bx, by, e.x, e.y) < rr * rr) {
                        e.bladeHitCooldown = 0.5f
                        damageEnemy(e, s.bladeDamage * plan.rules.playerDamageMul, false, ProjKind.BOLT, quiet = true)
                        break
                    }
                }
            }
        }

        // Autonomous Defense Node: each node fires at the nearest threat.
        if (s.orbBoltInterval > 0f) {
            orbBoltTimer -= dt
            if (orbBoltTimer <= 0f) {
                orbBoltTimer = s.orbBoltInterval
                for (i in 0 until orbs) {
                    val a = orbAngle + MathUtil.TWO_PI * i / orbs
                    val ox = px + cos(a) * s.orbRadius
                    val oy = py + sin(a) * s.orbRadius
                    val t = nearestEnemy(ox, oy, 420f) ?: continue
                    val ang = atan2(t.y - oy, t.x - ox)
                    val p = newProjectile() ?: break
                    p.active = true; p.friendly = true; p.kind = ProjKind.NODE_BOLT
                    p.x = ox; p.y = oy
                    p.vx = cos(ang) * 520f; p.vy = sin(ang) * 520f
                    p.radius = 5f; p.damage = s.orbDamage * 0.8f; p.crit = false
                    p.life = 1f; p.pierceLeft = 0; p.bounceLeft = 0; p.chainLeft = 0
                    p.lastHitUid = -1; p.ghost = false; p.homing = 0f
                }
            }
        }
    }

    fun nearestEnemy(x: Float, y: Float, maxDist: Float, exceptUid: Int = -1): Enemy? {
        var best: Enemy? = null
        var bd = maxDist * maxDist
        for (e in enemies.items) {
            if (!e.targetable || e.uid == exceptUid) continue
            val d = MathUtil.dist2(x, y, e.x, e.y)
            if (d < bd) { bd = d; best = e }
        }
        return best
    }

    // ======================================================================
    // Damage
    // ======================================================================

    fun damagePlayer(amount: Float, sourceX: Float, sourceY: Float, ignoreInvuln: Boolean = false) {
        if (phase == Phase.DEAD || phase == Phase.TRANSITION) return
        if (!ignoreInvuln && invuln > 0f) return
        val s = stats
        if (s.dodge > 0f && rng.nextFloat() < s.dodge) {
            addText(px, py - 30f, "DODGE", TextKind.INFO)
            invuln = 0.2f
            return
        }
        var dmg = amount * (1f - s.armor)
        sinceDamage = 0f
        if (firewall > 0f) {
            val absorbed = min(firewall, dmg)
            firewall -= absorbed
            dmg -= absorbed
            addText(px, py - 30f, "-${absorbed.toInt()}", TextKind.SHIELD)
            sound(GameSound.SHIELD_BLOCK)
            if (firewall <= 0f) {
                firewallWasUp = false
                sound(GameSound.FIREWALL_BREAK)
                if (s.firewallBreakPulse > 0f) {
                    firePulseDamage(px, py, 200f, s.damage * s.firewallBreakPulse, clearsProjectiles = true)
                }
            }
        }
        if (dmg > 0f) {
            hp -= dmg
            levelDamageTaken += dmg
            addText(px, py - 30f, "-${dmg.toInt().coerceAtLeast(1)}", TextKind.PLAYER_HURT)
            hurtFlash = 0.25f
            sound(GameSound.PLAYER_HURT)
        }
        invuln = if (ignoreInvuln) max(invuln, 0.1f) else HIT_INVULN
        if (s.counterRing > 0) {
            for (i in 0 until s.counterRing) {
                val a = MathUtil.TWO_PI * i / s.counterRing
                val p = spawnBolt(px, py, a, ProjKind.COUNTER, s.damage * 0.6f) ?: break
                p.life = 0.6f
            }
        }
    }

    fun heal(amount: Float, quiet: Boolean = false, ignoreMul: Boolean = false) {
        if (hp <= 0f) return
        val mul = if (ignoreMul) 1f else stats.healMul * plan.rules.healingMul
        val real = min(stats.maxHp - hp, amount * mul)
        if (real <= 0f) return
        hp += real
        if (!quiet && real >= 1f) addText(px, py - 34f, "+${real.toInt()}", TextKind.HEAL)
    }

    fun damageEnemy(e: Enemy, raw: Float, crit: Boolean, kind: ProjKind, quiet: Boolean = false, showText: Boolean = true) {
        if (!e.targetable) return
        var d = raw
        if (e.isElite || e.boss != null) d *= stats.eliteDamageMul
        val armor = e.def.armor * sqrt(Scaling.enemyHp(level))
        d = max(d * 0.2f, d - armor)
        d *= e.damageTakenMul
        e.hp -= d
        e.hitFlash = 0.1f
        val textKind = when {
            e.boss != null -> TextKind.BOSS
            crit -> TextKind.CRIT
            else -> TextKind.NORMAL
        }
        if (showText) addText(e.x + (rng.nextFloat() - 0.5f) * 16f, e.y - e.radius, d.toInt().coerceAtLeast(1).toString(), textKind)
        if (crit) sound(GameSound.CRIT) else if (!quiet) sound(GameSound.ENEMY_HIT)
        if (e.hp <= 0f) killEnemy(e)
    }

    fun killEnemy(e: Enemy) {
        if (!e.active) return
        e.active = false
        val bossState = e.boss
        if (bossState != null) {
            bossBrain.onBossKilled(e, bossState)
            return
        }
        kills++
        levelKills++
        if (e.isElite) elitesDefeated++
        val s = stats
        val rewardMul = e.rewardMul * (if (plan.kind == LevelKind.EVENT) plan.rules.rewardMul else 1f)
        gainXp(e.def.xp * (if (e.isElite) EliteModifier.REWARD_MUL else 1f) * s.xpMul)
        eurosEarned += max(1, (e.def.euros * rewardMul * s.euroMul * difficultyReward).toInt())
        score += (Scoring.kill(e.def.score, level, e.isElite, e.isChild) * difficultyReward).toLong()
        if (s.healOnKill > 0f) heal(s.healOnKill, quiet = true)
        if (s.firewallOnKill > 0f && s.firewallMax > 0f) firewall = min(s.firewallMax, firewall + s.firewallMax * s.firewallOnKill)
        repeat(if (e.isElite) 18 else 10) { addParticle(e.x, e.y, e.def.color, 200f, 0.5f, 3f) }
        sound(if (e.isElite) GameSound.ELITE_DEATH else GameSound.ENEMY_DEATH)

        // Split / elite death effects.
        val split = e.def.splitInto
        if (split != null) spawnChildren(Enemies.byId(split), e.def.splitCount, e.x, e.y)
        when (e.elite) {
            EliteModifier.REPLICATING -> spawnChildren(e.def, 2, e.x, e.y)
            EliteModifier.VOLATILE -> {
                for (i in 0 until 10) {
                    fireEnemyProjectile(e.x, e.y, MathUtil.TWO_PI * i / 10f, 170f, 9f * Scaling.enemyDamage(level), 7f, ProjKind.ENEMY)
                }
                addPulse(e.x, e.y, 90f, 0.4f, 0xFFFF9A1A)
            }
            EliteModifier.CORRUPTED -> addZone(e.x, e.y, 70f, 4f, 10f * Scaling.enemyDamage(level), 0xFF9B4DFF, telegraph = 0f)
            else -> {}
        }
    }

    private fun spawnChildren(def: EnemyDef, count: Int, x: Float, y: Float) {
        for (i in 0 until count) {
            val a = MathUtil.TWO_PI * i / count + rng.nextFloat()
            val child = spawnEnemyAt(def, null, x + cos(a) * 20f, y + sin(a) * 20f, telegraph = false) ?: return
            child.isChild = true
        }
    }

    private fun gainXp(amount: Float) {
        // Campaign power-ups come from level clears; data only feeds endless mode.
        if (mode == GameMode.CAMPAIGN) return
        xp += amount
        var need = Scaling.xpToNext(runLevel)
        while (xp >= need) {
            xp -= need
            runLevel++
            pendingUpgrades++
            need = Scaling.xpToNext(runLevel)
        }
    }

    val xpFraction: Float get() = (xp / Scaling.xpToNext(runLevel)).coerceIn(0f, 1f)

    /** Radial damage around a point (EMP, firewall break). */
    fun firePulseDamage(x: Float, y: Float, radius: Float, damage: Float, clearsProjectiles: Boolean) {
        for (e in enemies.items) {
            if (!e.targetable) continue
            val r = radius + e.radius
            if (MathUtil.dist2(x, y, e.x, e.y) < r * r) damageEnemy(e, damage, false, ProjKind.BOLT, quiet = true)
        }
        if (clearsProjectiles) {
            for (p in projectiles.items) {
                if (p.active && !p.friendly && MathUtil.dist2(x, y, p.x, p.y) < radius * radius) p.active = false
            }
        }
        addPulse(x, y, radius, 0.45f, 0xFF2E9BFF)
    }

    // ======================================================================
    // Spawning (used by planner waves, AI and bosses)
    // ======================================================================

    fun aliveCount(): Int {
        var c = 0
        for (e in enemies.items) if (e.active) c++
        return c
    }

    /** Spawn at a random free point away from the player. */
    fun spawnEnemy(def: EnemyDef, elite: EliteModifier?, telegraph: Boolean): Enemy? {
        var x = 0f
        var y = 0f
        var found = false
        for (attempt in 0 until 30) {
            x = 50f + rng.nextFloat() * (arena.width - 100f)
            y = 60f + rng.nextFloat() * (arena.height * 0.72f)
            val minDist = if (attempt < 20) SPAWN_MIN_DIST else SPAWN_MIN_DIST * 0.6f
            if (MathUtil.dist2(x, y, px, py) < minDist * minDist) continue
            if (!arena.isFree(x, y, def.radius * 1.4f + 6f)) continue
            found = true
            break
        }
        if (!found) return null
        return spawnEnemyAt(def, elite, x, y, telegraph)
    }

    fun spawnEnemyAt(def: EnemyDef, elite: EliteModifier?, x: Float, y: Float, telegraph: Boolean): Enemy? {
        val e = enemies.obtain() ?: return null
        levelSpawned++
        val rules: EventRules = plan.rules
        e.active = true
        e.uid = nextUid++
        e.def = def
        e.elite = elite
        e.boss = null
        e.isChild = false
        arena.pushOut(x, y, def.radius)
        e.x = arena.out[0]
        e.y = arena.out[1]
        e.vx = 0f; e.vy = 0f
        val hpMul = Scaling.enemyHp(level) * rules.enemyHpMul * config.difficulty.enemyHp * config.opHpMul * adaptiveHp *
            (if (elite != null) EliteModifier.BASE_HP_MUL * elite.hpMul else 1f)
        e.maxHp = def.baseHp * hpMul
        e.hp = e.maxHp
        e.radius = def.radius * (if (elite != null) EliteModifier.SIZE_MUL else 1f)
        e.speed = def.baseSpeed * Scaling.enemySpeed(level) * rules.enemySpeedMul * (elite?.speedMul ?: 1f)
        e.damageMul = Scaling.enemyDamage(level) * rules.enemyDamageMul * config.difficulty.enemyDamage * config.opDamageMul * adaptiveDamage
        e.attackRateMul = Scaling.attackRate(level) * (elite?.attackRateMul ?: 1f)
        e.damageTakenMul = elite?.damageTakenMul ?: 1f
        e.rewardMul = if (elite != null) EliteModifier.REWARD_MUL else 1f
        e.state = if (telegraph) AiState.SPAWNING else AiState.MOVE
        e.stateTimer = if (telegraph) SPAWN_TELEGRAPH else 0f
        e.attackTimer = def.attackCooldown * (0.5f + rng.nextFloat() * 0.6f)
        e.strafeDir = if (rng.nextBoolean()) 1f else -1f
        e.wobble = rng.nextFloat() * MathUtil.TWO_PI
        e.hitFlash = 0f
        e.orbHitCooldown = 0f
        e.bladeHitCooldown = 0f
        e.contactCooldown = 0f
        e.stuckTimer = 0f
        e.detourTimer = 0f
        e.navTimer = rng.nextFloat() * EnemyAi.NAV_REFRESH
        e.navValid = false
        e.lastX = e.x; e.lastY = e.y
        return e
    }

    fun fireEnemyProjectile(x: Float, y: Float, angle: Float, speed: Float, damage: Float, radius: Float, kind: ProjKind): Projectile? {
        val p = newProjectile() ?: return null
        p.active = true
        p.friendly = false
        p.kind = kind
        p.x = x; p.y = y
        val sp = speed * Scaling.projectileSpeed(level)
        p.vx = cos(angle) * sp
        p.vy = sin(angle) * sp
        p.radius = radius
        p.damage = damage
        p.life = 6f
        p.pierceLeft = 0; p.bounceLeft = 0; p.chainLeft = 0
        p.crit = false
        p.lastHitUid = -1
        p.ghost = false
        p.homing = 0f
        p.armTimer = 0f
        p.splash = 0f
        return p
    }

    // ======================================================================
    // Projectiles
    // ======================================================================

    private fun updateProjectiles(dt: Float) {
        val s = stats
        for (p in projectiles.items) {
            if (!p.active) continue
            p.life -= dt
            if (p.life <= 0f) { p.active = false; continue }
            if (p.kind == ProjKind.MINE) {
                if (p.armTimer > 0f) { p.armTimer -= dt; continue }
                for (e in enemies.items) {
                    if (!e.targetable) continue
                    val rr = e.radius + 34f
                    if (MathUtil.dist2(p.x, p.y, e.x, e.y) < rr * rr) {
                        p.active = false
                        detonate(p.x, p.y, p.splash, p.damage)
                        break
                    }
                }
                continue
            }
            if (p.kind == ProjKind.BOOMERANG) {
                if (!p.returning) {
                    p.armTimer -= dt
                    if (p.armTimer <= 0f) { p.returning = true; p.lastHitUid = -1 }
                } else {
                    val dx = px - p.x
                    val dy = py - p.y
                    val d = sqrt(dx * dx + dy * dy)
                    if (d < playerRadius + 12f) { p.active = false; continue }
                    val sp = sqrt(p.vx * p.vx + p.vy * p.vy).coerceAtLeast(300f)
                    p.vx = dx / d * sp
                    p.vy = dy / d * sp
                }
            } else if (p.homing > 0f && p.friendly) {
                val t = nearestEnemy(p.x, p.y, 600f)
                if (t != null) {
                    val want = atan2(t.y - p.y, t.x - p.x)
                    val cur = atan2(p.vy, p.vx)
                    val turn = MathUtil.clamp(MathUtil.wrapAngle(want - cur), -p.homing * dt, p.homing * dt)
                    // Missiles accelerate after launch.
                    val sp = min(760f, sqrt(p.vx * p.vx + p.vy * p.vy) + 900f * dt)
                    p.vx = cos(cur + turn) * sp
                    p.vy = sin(cur + turn) * sp
                }
            } else if (p.homing > 0f && !p.friendly) {
                val want = atan2(py - p.y, px - p.x)
                val cur = atan2(p.vy, p.vx)
                val diff = MathUtil.wrapAngle(want - cur)
                val turn = MathUtil.clamp(diff, -p.homing * dt, p.homing * dt)
                val sp = sqrt(p.vx * p.vx + p.vy * p.vy)
                p.vx = cos(cur + turn) * sp
                p.vy = sin(cur + turn) * sp
            }
            val ox = p.x
            val oy = p.y
            p.x += p.vx * dt
            p.y += p.vy * dt

            // Walls and obstacles.
            val outside = p.x < 0f || p.y < 0f || p.x > arena.width || p.y > arena.height
            val hitIdx = if (p.ghost) -1 else arena.obstacleAt(p.x, p.y, p.radius * 0.6f)
            if (outside || hitIdx >= 0) {
                if (p.friendly && p.bounceLeft > 0) {
                    p.bounceLeft--
                    if (outside) {
                        if (p.x < 0f || p.x > arena.width) p.vx = -p.vx
                        if (p.y < 0f || p.y > arena.height) p.vy = -p.vy
                    } else {
                        val r = arena.rect(hitIdx)
                        if (ox < r.left || ox > r.right) p.vx = -p.vx else p.vy = -p.vy
                    }
                    p.x = ox; p.y = oy
                    p.lastHitUid = -1
                } else {
                    p.active = false
                    if (p.kind == ProjKind.MISSILE) detonate(ox, oy, p.splash, p.damage * 0.6f)
                    else addParticle(p.x, p.y, if (p.friendly) 0xFF00E5FF else 0xFFFF7A1A, 90f, 0.2f, 2f)
                }
                continue
            }

            if (p.friendly) {
                for (e in enemies.items) {
                    if (!e.targetable || e.uid == p.lastHitUid) continue
                    val rr = e.radius + p.radius
                    if (MathUtil.dist2(p.x, p.y, e.x, e.y) >= rr * rr) continue
                    onBoltHit(p, e)
                    break
                }
            } else {
                // Encryption Blades block hostile packets.
                if (s.bladeCount > 0) {
                    var blocked = false
                    for (i in 0 until s.bladeCount) {
                        val a = bladeAngle + MathUtil.TWO_PI * i / s.bladeCount
                        val bx = px + cos(a) * s.bladeRadius
                        val by = py + sin(a) * s.bladeRadius
                        val rr = 16f + p.radius
                        if (MathUtil.dist2(bx, by, p.x, p.y) < rr * rr) { blocked = true; break }
                    }
                    if (blocked) {
                        p.active = false
                        addParticle(p.x, p.y, 0xFF00E5FF, 120f, 0.25f, 2f)
                        sound(GameSound.SHIELD_BLOCK)
                        continue
                    }
                }
                val rr = playerRadius * 0.8f + p.radius
                if (MathUtil.dist2(p.x, p.y, px, py) < rr * rr) {
                    p.active = false
                    damagePlayer(p.damage, p.x, p.y)
                }
            }
        }
    }

    private fun onBoltHit(p: Projectile, e: Enemy) {
        val s = stats
        if (p.kind == ProjKind.BOLT && e.boss == null && s.instantDeleteChance > 0f && rng.nextFloat() < s.instantDeleteChance) {
            addText(e.x, e.y - e.radius - 10f, "DELETED", TextKind.CRIT)
            damageEnemy(e, e.hp / e.damageTakenMul + 9999f, true, p.kind)
        } else {
            damageEnemy(e, p.damage, p.crit, p.kind)
        }
        p.lastHitUid = e.uid
        if (p.kind == ProjKind.MISSILE) {
            p.active = false
            detonate(p.x, p.y, p.splash, p.damage * 0.6f)
            return
        }
        if (p.chainLeft > 0) {
            val next = nearestEnemy(e.x, e.y, 220f, exceptUid = e.uid)
            if (next != null) {
                val c = newProjectile()
                if (c != null) {
                    val ang = atan2(next.y - e.y, next.x - e.x)
                    c.active = true; c.friendly = true; c.kind = ProjKind.NODE_BOLT
                    c.x = e.x; c.y = e.y
                    c.vx = cos(ang) * 760f; c.vy = sin(ang) * 760f
                    c.radius = 5f; c.damage = p.damage * s.chainDamageMul; c.crit = false
                    c.life = 0.5f; c.pierceLeft = 0; c.bounceLeft = 0; c.chainLeft = p.chainLeft - 1
                    c.lastHitUid = e.uid; c.ghost = true; c.homing = 0f
                }
            }
            p.chainLeft = 0
        }
        if (p.pierceLeft > 0) p.pierceLeft-- else p.active = false
    }

    // ======================================================================
    // Hazards & visual effects
    // ======================================================================

    fun addLine(x: Float, y: Float, x2: Float, y2: Float, duration: Float, color: Long, ownerUid: Int): Hazard? {
        val h = hazards.obtain() ?: return null
        h.active = true; h.kind = HazardKind.LINE
        h.x = x; h.y = y; h.x2 = x2; h.y2 = y2
        h.timer = 0f; h.duration = duration; h.color = color; h.ownerUid = ownerUid
        h.radius = 3f; h.damage = 0f; h.hitPlayer = false
        return h
    }

    fun addBlast(x: Float, y: Float, radius: Float, delay: Float, damage: Float, color: Long) {
        val h = hazards.obtain() ?: return
        h.active = true; h.kind = HazardKind.BLAST
        h.x = x; h.y = y; h.radius = radius
        h.timer = 0f; h.duration = delay; h.damage = damage; h.color = color
        h.hitPlayer = false; h.ownerUid = -1
    }

    fun addZone(x: Float, y: Float, radius: Float, duration: Float, dps: Float, color: Long, telegraph: Float) {
        val h = hazards.obtain() ?: return
        h.active = true; h.kind = HazardKind.ZONE
        h.x = x; h.y = y; h.radius = radius
        h.timer = 0f; h.windup = telegraph; h.duration = telegraph + duration
        h.damage = dps; h.color = color; h.tick = 0f; h.hitPlayer = false; h.ownerUid = -1
    }

    fun addShockRing(x: Float, y: Float, maxRadius: Float, speed: Float, damage: Float, color: Long) {
        val h = hazards.obtain() ?: return
        h.active = true; h.kind = HazardKind.SHOCK_RING
        h.x = x; h.y = y; h.radius = 10f; h.maxRadius = maxRadius
        h.timer = 0f; h.duration = maxRadius / speed; h.damage = damage; h.color = color
        h.hitPlayer = false; h.ownerUid = -1
    }

    fun addBeam(x: Float, y: Float, angle: Float, length: Float, width: Float, windup: Float, active: Float, damage: Float, color: Long) {
        val h = hazards.obtain() ?: return
        h.active = true; h.kind = HazardKind.BEAM
        h.x = x; h.y = y
        h.x2 = x + cos(angle) * length; h.y2 = y + sin(angle) * length
        h.radius = width; h.windup = windup
        h.timer = 0f; h.duration = windup + active; h.damage = damage; h.color = color
        h.hitPlayer = false; h.ownerUid = -1
    }

    private fun updateHazards(dt: Float) {
        for (h in hazards.items) {
            if (!h.active) continue
            h.timer += dt
            when (h.kind) {
                HazardKind.LINE -> if (h.timer >= h.duration) h.active = false
                HazardKind.BLAST -> if (h.timer >= h.duration) {
                    h.active = false
                    if (MathUtil.dist2(px, py, h.x, h.y) < (h.radius + playerRadius * 0.6f).let { it * it }) {
                        damagePlayer(h.damage, h.x, h.y)
                    }
                    addPulse(h.x, h.y, h.radius, 0.3f, h.color)
                }
                HazardKind.ZONE -> {
                    if (h.timer >= h.duration) { h.active = false; continue }
                    if (h.timer >= h.windup) {
                        h.tick -= dt
                        if (h.tick <= 0f && MathUtil.dist2(px, py, h.x, h.y) < h.radius * h.radius) {
                            h.tick = 0.5f
                            damagePlayer(h.damage * 0.5f, h.x, h.y, ignoreInvuln = true)
                        }
                    }
                }
                HazardKind.SHOCK_RING -> {
                    h.radius = 10f + (h.maxRadius - 10f) * (h.timer / h.duration)
                    if (h.timer >= h.duration) { h.active = false; continue }
                    if (!h.hitPlayer) {
                        val d = MathUtil.dist(px, py, h.x, h.y)
                        if (kotlin.math.abs(d - h.radius) < RING_THICKNESS + playerRadius * 0.5f) {
                            h.hitPlayer = true
                            damagePlayer(h.damage, h.x, h.y)
                        }
                    }
                }
                HazardKind.BEAM -> {
                    if (h.timer >= h.duration) { h.active = false; continue }
                    if (h.timer >= h.windup && !h.hitPlayer) {
                        if (distToSegment(px, py, h.x, h.y, h.x2, h.y2) < h.radius * 0.5f + playerRadius * 0.6f) {
                            h.hitPlayer = true
                            damagePlayer(h.damage, h.x, h.y)
                        }
                    }
                }
            }
        }
    }

    private fun updateEffects(dt: Float) {
        for (t in texts.items) {
            if (!t.active) continue
            t.life -= dt
            t.y -= 38f * dt
            if (t.life <= 0f) t.active = false
        }
        for (p in particles.items) {
            if (!p.active) continue
            p.life -= dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.vx *= 0.92f
            p.vy *= 0.92f
            if (p.life <= 0f) p.active = false
        }
        for (p in pulses.items) {
            if (!p.active) continue
            p.life -= dt
            p.radius = p.maxRadius * (1f - p.life / p.maxLife)
            if (p.life <= 0f) p.active = false
        }
    }

    fun addText(x: Float, y: Float, text: String, kind: TextKind) {
        val t = texts.obtain() ?: return
        t.active = true; t.x = x; t.y = y; t.text = text; t.kind = kind
        t.life = if (kind == TextKind.INFO) 1.1f else 0.7f
    }

    fun addParticle(x: Float, y: Float, color: Long, speed: Float, life: Float, size: Float) {
        val p = particles.obtain() ?: return
        val a = rng.nextFloat() * MathUtil.TWO_PI
        val sp = speed * (0.3f + rng.nextFloat() * 0.7f)
        p.active = true; p.x = x; p.y = y
        p.vx = cos(a) * sp; p.vy = sin(a) * sp
        p.life = life * (0.6f + rng.nextFloat() * 0.4f); p.maxLife = p.life
        p.color = color; p.size = size
    }

    fun addPulse(x: Float, y: Float, radius: Float, life: Float, color: Long) {
        val p = pulses.obtain() ?: return
        p.active = true; p.x = x; p.y = y; p.radius = 0f; p.maxRadius = radius
        p.life = life; p.maxLife = life; p.color = color
    }

    fun showBanner(text: String, sub: String, seconds: Float) {
        banner = text
        bannerSub = sub
        bannerTimer = seconds
    }

    fun sound(s: GameSound) {
        if (sounds.size < 32) sounds += s
    }

    internal fun enemyAiMove(e: Enemy, tx: Float, ty: Float, speed: Float, dt: Float) = ai.moveToward(e, tx, ty, speed, dt)

    internal fun setBossRef(e: Enemy?) { boss = e }
    internal fun setBossPhaseLabel(label: String) { bossPhaseLabel = label }
    internal fun onBossDefeated(eurosReward: Int, scoreReward: Int, bonusPicks: Int = 0) {
        bossesDefeated++
        eurosEarned += (eurosReward * stats.euroMul * difficultyReward).toInt()
        score += (scoreReward * difficultyReward).toLong()
        // Boss mods (owner, 2026-10-08): more picks, and rolled with extra luck.
        pendingUpgrades += 2 + level / 20 + bonusPicks
        bossLuckPending = true
        purgeHostiles()
    }

    /** Removes every hostile (boss death, timed event end). */
    fun purgeHostiles() {
        for (e in enemies.items) {
            if (!e.active) continue
            e.active = false
            repeat(6) { addParticle(e.x, e.y, e.def.color, 160f, 0.4f, 3f) }
        }
        for (p in projectiles.items) if (p.active && !p.friendly) p.active = false
        for (h in hazards.items) h.active = false
    }

    /**
     * Endless: one room, never cleared. Difficulty (the "level" used by every
     * scaling curve) rises one stage every [Scaling.ENDLESS_STAGE_SECONDS];
     * every 10th stage brings that stage's boss, and normal spawns pause
     * until it falls.
     */
    private fun updateEndless(dt: Float) {
        stageTimer += dt
        if (stageTimer >= Scaling.ENDLESS_STAGE_SECONDS) {
            stageTimer = 0f
            level++
            updateAdaptive()
            if (Scaling.isBossLevel(level) && boss == null) {
                val b = com.cyberoperative.game.data.Bosses.forLevel(level)
                sound(GameSound.BOSS_SPAWN)
                bossBrain.spawn(b, arena.width / 2f, 260f, LevelPlanner.rollGlitchedBoss(level, rng))
            } else {
                showBanner("STAGE $level", "Threat level rising", 1.2f)
            }
        }
        if (boss != null) return
        spawnTimer -= dt
        if (spawnTimer <= 0f && aliveCount() < Scaling.MAX_ALIVE) {
            spawnTimer = max(0.35f, 1.5f - level * 0.03f)
            val pool = Enemies.pool(level)
            val elite = if (rng.nextFloat() < Scaling.eliteChance(level)) EliteModifier.entries[rng.nextInt(EliteModifier.entries.size)] else null
            spawnEnemy(LevelPlanner.weightedPick(pool, rng), elite, telegraph = true)
        }
    }

    /** Seconds into the current boss's entrance, or -1 when no entrance is running. */
    val bossIntroElapsed: Float
        get() {
            val b = boss ?: return -1f
            return if (b.active && b.state == AiState.SPAWNING) BossBrain.INTRO_SECONDS - b.stateTimer else -1f
        }

    /** Test hook: add run €. */
    fun debugGrantEuros(amount: Int) { eurosEarned += amount }

    /** Test hook: jump to a level (used by unit tests and debug). */
    fun debugJumpToLevel(target: Int) {
        startLevel(target, null, true)
    }

    /** Test hook: start a specific plan (e.g. a given event or boss). */
    fun debugStartPlan(forced: LevelPlan) {
        startLevel(forced.level, null, true, forced)
    }

    companion object {
        const val STEP = 1f / 120f
        const val MAX_FRAME = 0.1f
        const val MOVE_DEADZONE = 0.12f
        /** How long the stick must be released before the first shot. */
        const val STOP_TO_FIRE_DELAY = 0.04f
        const val FOLLOW_UP_GAP = 0.11f
        const val THREAT_RADIUS = 150f
        const val ORB_HIT_COOLDOWN = 0.4f
        const val HIT_INVULN = 0.45f
        const val SPAWN_TELEGRAPH = 0.85f
        const val SPAWN_MIN_DIST = 300f
        const val WAVE_TIMEOUT = 14f
        const val WAVE_MIN_GAP = 1.2f
        const val CLEAR_BEAT = 0.9f
        const val TRANSITION_TIME = 0.45f
        const val PORTAL_RADIUS = 46f
        const val GATE_HALF_WIDTH = 80f
        const val RING_THICKNESS = 12f
        const val BEAM_MAX_FIRE = 5f
        const val SHOT_CLEARANCE = 4f
        const val SHOP_CHANCE = 1f / 20f
        const val VAULT_OPEN_SECONDS = 1.3f

        /** Owner: €1,000 and up, €100 per level (level 70 → €7,000). */
        fun vaultPayout(level: Int): Int = maxOf(1000, 100 * level)
        const val SHOP_GATE_HALF = 70f
        const val SHOP_ARROW_SECONDS = 2.4f

        /** GOLDEN from €1,000, TITANIUM from €2,500, a little more each level. */
        fun shopPrice(rarity: com.cyberoperative.game.data.Rarity, level: Int): Int = when (rarity) {
            com.cyberoperative.game.data.Rarity.TITANIUM -> 2500 + 50 * level
            else -> 1000 + 25 * level
        }
        const val BEAM_COOLDOWN = 3f

        private val FIREWALL_IDS = setOf("firewall", "reinforced_firewall", "adaptive_firewall", "zero_trust")

        fun distToSegment(px: Float, py: Float, x0: Float, y0: Float, x1: Float, y1: Float): Float {
            val dx = x1 - x0
            val dy = y1 - y0
            val len2 = dx * dx + dy * dy
            if (len2 < 1e-6f) return MathUtil.dist(px, py, x0, y0)
            val t = MathUtil.clamp(((px - x0) * dx + (py - y0) * dy) / len2, 0f, 1f)
            return MathUtil.dist(px, py, x0 + dx * t, y0 + dy * t)
        }
    }
}
