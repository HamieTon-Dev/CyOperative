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

/** The co-op partner's own permanent progression (their base stats, rerolls, card quality). */
data class AllyConfig(
    val baseStats: RunStats = RunStats(),
    val rerolls: Int = 0,
    val upgradeQuality: Float = 0f,
    val startingUpgrades: Int = 0,
    val opLevel: Int = 1,
    val weaponSlots: Int = Upgrades.MAX_WEAPONS
)

const val COOP_HP_MUL = 1.4f
const val COOP_BOSS_HP_MUL = 1.7f
const val COOP_EXTRA_THREATS = 0.35f

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
    val opLevel: Int = 1,
    /** How much permanent-upgrade mastery multiplied damage output / staying power (1 = none). */
    val masteryDpsRatio: Float = 1f,
    val masterySurvivalRatio: Float = 1f,
    /** Co-op partner (owner, 2026-10-08), or null for a solo run. */
    val ally: AllyConfig? = null,
    /** Weapons this operative can carry (7 + WEAPON SLOTS upgrade). */
    val weaponSlots: Int = Upgrades.MAX_WEAPONS
) {
    val coop: Boolean get() = ally != null
    /** Co-op threat HP ×1.4 (bosses ×1.7) and ~35% more threats. */
    val coopHpMul: Float get() = if (coop) COOP_HP_MUL else 1f
    val coopBossHpMul: Float get() = if (coop) COOP_BOSS_HP_MUL else 1f

    /**
     * Threat HP from OP level: +2% per level up to OP 101 (×3), then it keeps
     * climbing on a log curve (×7 at OP 1,000, ×11 at OP 9,999), times the
     * square root of the damage mastery added: mastery always pays off, but only
     * half of it (in multiplier terms) turns into easier fights.
     */
    val opHpMul: Float get() = opCurve(0.02f, 0.6f) * kotlin.math.sqrt(masteryDpsRatio)
    /** Threat damage from OP level: +1.2% per level up to ×2.2, then log (×4.2 at OP 1,000, ×6.2 at 9,999); × √ survival mastery. */
    val opDamageMul: Float get() = opCurve(0.012f, 0.4f) * kotlin.math.sqrt(masterySurvivalRatio)

    private fun opCurve(perLevel: Float, late: Float): Float {
        val base = 1f + perLevel * (opLevel - 1).coerceIn(0, 100)
        val past = (opLevel - 101).coerceAtLeast(0)
        return if (past == 0) base else base * (1f + late * kotlin.math.ln(1f + past / 100f))
    }
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
    /** The operatives on the field: [0] is the host / solo player, [1] the co-op partner. */
    private val ops = ArrayList<Operative>(2).apply {
        add(Operative(0, config.baseStats).also {
            it.rerollsLeft = config.rerolls; it.build.qualityBonus = config.upgradeQuality; it.build.weaponSlots = config.weaponSlots
        })
        config.ally?.let { a ->
            add(Operative(1, a.baseStats).also { it.rerollsLeft = a.rerolls; it.build.qualityBonus = a.upgradeQuality; it.build.weaponSlots = a.weaponSlots })
        }
        // Campaign picks come from clearing levels, not data, so data-only cards are pointless.
        if (config.mode == GameMode.CAMPAIGN) for (o in this) o.build.excluded = setOf(Upgrades.DATA_DUMP.id, Upgrades.DATA_COMPRESSION.id)
    }
    val operatives: List<Operative> get() = ops
    /** The operative the rules are being applied to right now (outside [update]: the host / solo player). */
    private var cur: Operative = ops[0]
    /** The operative this device plays: 0 on the host / solo, 1 on a co-op guest. */
    var primary = 0
        private set
    val coop: Boolean get() = ops.size > 1
    val build: RunBuild get() = cur.build
    val stats: RunStats get() = build.stats

    var runLevel = 1
        private set
    var xp = 0f
        private set
    var pendingUpgrades: Int
        get() = cur.pendingUpgrades
        private set(v) { cur.pendingUpgrades = v }
    var offer: List<UpgradeOffer>
        get() = cur.offer
        private set(v) { cur.offer = v }
    /** Bumped whenever [offer] changes so the UI knows to redraw the cards. */
    val offerSerial: Int get() = cur.offerSerial
    /** Rewards in the current pick streak (e.g. 3 after a boss) and how many are taken. */
    var rewardBatchTotal: Int
        get() = cur.rewardBatchTotal
        private set(v) { cur.rewardBatchTotal = v }
    var rewardBatchTaken: Int
        get() = cur.rewardBatchTaken
        private set(v) { cur.rewardBatchTaken = v }

    /** Rarity luck of the current offers (level + difficulty + boss bonus). */
    var offerLuck = 0f
        private set
    /** Former build-size threat multipliers (removed 2026-10-09; always ×1). */
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

    /** Build-size threat scaling was removed (owner, 2026-10-09): kept at ×1. */
    private fun updateAdaptive() {
        adaptiveHp = 1f
        adaptiveDamage = 1f
    }

    /**
     * OP-level (and mastery) threat bonus (owner, 2026-10-09: "it should scale
     * to OP level"): the one difficulty extra on top of the base curve, from
     * level 1, halved on EASY.
     */
    val opHpNow: Float get() = 1f + (config.opHpMul - 1f) * config.difficulty.extraScaling
    val opDamageNow: Float get() = 1f + (config.opDamageMul - 1f) * config.difficulty.extraScaling

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
    var rerollsLeft: Int
        get() = cur.rerollsLeft
        private set(v) { cur.rerollsLeft = v }
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

    // --- Player (the current operative; see [Operative]) ------------------------
    val playerRadius = 20f
    var px: Float
        get() = cur.px
        private set(v) { cur.px = v }
    var py: Float
        get() = cur.py
        private set(v) { cur.py = v }
    var hp: Float
        get() = cur.hp
        private set(v) { cur.hp = v }
    var firewall: Float
        get() = cur.firewall
        private set(v) { cur.firewall = v }
    var facing: Float
        get() = cur.facing
        private set(v) { cur.facing = v }
    var moving: Boolean
        get() = cur.moving
        private set(v) { cur.moving = v }
    var invuln: Float
        get() = cur.invuln
        private set(v) { cur.invuln = v }
    var hurtFlash: Float
        get() = cur.hurtFlash
        private set(v) { cur.hurtFlash = v }
    var orbAngle: Float
        get() = cur.orbAngle
        private set(v) { cur.orbAngle = v }
    var bladeAngle: Float
        get() = cur.bladeAngle
        private set(v) { cur.bladeAngle = v }
    var targetUid: Int
        get() = cur.targetUid
        private set(v) { cur.targetUid = v }
    var beamActive: Boolean
        get() = cur.beamActive
        private set(v) { cur.beamActive = v }
    var beamX2: Float
        get() = cur.beamX2
        private set(v) { cur.beamX2 = v }
    var beamY2: Float
        get() = cur.beamY2
        private set(v) { cur.beamY2 = v }
    var beamHeat: Float
        get() = cur.beamHeat
        private set(v) { cur.beamHeat = v }
    var beamCooldown: Float
        get() = cur.beamCooldown
        private set(v) { cur.beamCooldown = v }
    private var inputX: Float
        get() = cur.inputX
        set(v) { cur.inputX = v }
    private var inputY: Float
        get() = cur.inputY
        set(v) { cur.inputY = v }
    private var fireCooldown: Float
        get() = cur.fireCooldown
        set(v) { cur.fireCooldown = v }
    private var stillTime: Float
        get() = cur.stillTime
        set(v) { cur.stillTime = v }
    private var followUpLeft: Int
        get() = cur.followUpLeft
        set(v) { cur.followUpLeft = v }
    private var followUpTimer: Float
        get() = cur.followUpTimer
        set(v) { cur.followUpTimer = v }
    private var lanceTimer: Float
        get() = cur.lanceTimer
        set(v) { cur.lanceTimer = v }
    private var empTimer: Float
        get() = cur.empTimer
        set(v) { cur.empTimer = v }
    private var sinceDamage: Float
        get() = cur.sinceDamage
        set(v) { cur.sinceDamage = v }
    private var firewallWasUp: Boolean
        get() = cur.firewallWasUp
        set(v) { cur.firewallWasUp = v }
    private var orbBoltTimer: Float
        get() = cur.orbBoltTimer
        set(v) { cur.orbBoltTimer = v }
    private var beamTick: Float
        get() = cur.beamTick
        set(v) { cur.beamTick = v }
    private var mineTimer: Float
        get() = cur.mineTimer
        set(v) { cur.mineTimer = v }
    private var missileTimer: Float
        get() = cur.missileTimer
        set(v) { cur.missileTimer = v }
    private var arcTimer: Float
        get() = cur.arcTimer
        set(v) { cur.arcTimer = v }
    private var railTimer: Float
        get() = cur.railTimer
        set(v) { cur.railTimer = v }
    private var strikeTimer: Float
        get() = cur.strikeTimer
        set(v) { cur.strikeTimer = v }
    val beamWidth: Float get() = 14f + 5f * stats.beamLevel

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

    /** Flow field toward the current operative for enemy navigation. */
    val path: Pathfinder get() = cur.path
    private val ai = EnemyAi(this)
    private val bossBrain = BossBrain(this)
    /** Shake, flash, hit-stop and slow motion (Boss Expansion S12). */
    val fx = ScreenFx()

    /**
     * Room lights (Boss Expansion S11): 0 = normal, 1 = blacked out by an EMP.
     * [darknessTarget] is where it is heading (power restoring after a win).
     */
    var darkness = 0f
    var darknessTarget = 0f
    /** Seconds of a lights-flicker (grid reboot attempt): darkness eases off briefly. */
    var lightFlicker = 0f
    /** Stealth boss in the shadows: 1 = eyes closed (can't lock on), 0 = eyes open. */
    var bossVeil = 0f

    /** What the renderer shows right now. */
    val darknessNow: Float get() = if (lightFlicker > 0f) darkness * 0.35f else darkness

    /** Serpent body points (x, y pairs, head first) for Circuit Hydra; empty otherwise. */
    var bossTrail = FloatArray(0)

    /** Botnet Monarch's Sync Burst charge (0..1 while the drones link up), 0 otherwise. */
    var bossSync = 0f

    /** Ransom shield on the current boss: 1 = up (reduced damage), 0 = broken, -1 = no shield boss. */
    var bossShield = -1f

    /** Golden key zone (see [HazardKind.KEY_ZONE]). */
    fun addKeyZone(x: Float, y: Float, radius: Float, duration: Float, color: Long) {
        val h = hazards.obtain() ?: return
        h.active = true; h.kind = HazardKind.KEY_ZONE
        h.x = x; h.y = y; h.radius = radius
        h.timer = 0f; h.duration = duration; h.windup = 0f; h.damage = 0f; h.color = color
        h.hitMask = 0; h.ownerUid = -1
    }

    /** Ransom Pulse ring (see [HazardKind.RANSOM_RING]). */
    fun addRansomRing(x: Float, y: Float, maxRadius: Float, speed: Float, damage: Float, color: Long) {
        val h = hazards.obtain() ?: return
        h.active = true; h.kind = HazardKind.RANSOM_RING
        h.x = x; h.y = y; h.radius = 10f; h.maxRadius = maxRadius
        h.timer = 0f; h.duration = maxRadius / speed; h.damage = damage; h.color = color
        h.hitMask = 0; h.ownerUid = -1
    }

    /** Spectral Firewall's orbiting ring: angle, plates filled (of [FIREWALL_SLOTS]), launched (gone). -1 filled = none. */
    var bossRingAngle = 0f
    var bossRingFilled = -1
    var bossRingOut = false

    /** ON FIRE: the current operative burns for [seconds] at [dps]. */
    fun ignite(seconds: Float, dps: Float) {
        if (cur.burning <= 0f) addText(px, py - 44f, "ON FIRE", TextKind.PLAYER_HURT)
        cur.burning = max(cur.burning, seconds)
        cur.burnDps = max(cur.burnDps, dps)
    }

    /** Fire wall ring from radius [from] to [to] around ([x], [y]); see [HazardKind.FIRE_WALL]. */
    fun addFireWall(x: Float, y: Float, from: Float, to: Float, speed: Float, gaps: Int, gapAngle: Float, gapHalf: Float, damage: Float, color: Long, ownerUid: Int, kind: HazardKind = HazardKind.FIRE_WALL) {
        val h = hazards.obtain() ?: return
        h.active = true; h.kind = kind
        h.x = x; h.y = y; h.radius = from; h.maxRadius = to; h.angle = from
        h.x2 = gapAngle * 1000f; h.y2 = gaps.toFloat(); h.windup = gapHalf
        h.timer = 0f; h.duration = kotlin.math.abs(to - from) / speed
        h.damage = damage; h.color = color; h.hitMask = 0; h.ownerUid = ownerUid
        h.angVel = (if (rng.nextBoolean()) 1f else -1f) * 0.35f
    }

    /** Crescent slash; see [HazardKind.SLASH]. Damages anyone in the arc right away. */
    fun addSlash(x: Float, y: Float, angle: Float, reach: Float, damage: Float, color: Long) {
        val h = hazards.obtain() ?: return
        h.active = true; h.kind = HazardKind.SLASH
        h.x = x; h.y = y; h.angle = angle; h.radius = reach
        h.timer = 0f; h.duration = 0.3f; h.damage = damage; h.color = color; h.hitMask = 0; h.ownerUid = -1
        forEachAlive {
            val d = MathUtil.dist(px, py, x, y)
            if (d < reach + playerRadius * 0.6f && kotlin.math.abs(MathUtil.wrapAngle(atan2(py - y, px - x) - angle)) < SLASH_HALF_ARC) damagePlayer(damage, x, y)
        }
    }

    /** Packet scythe; see [HazardKind.SCYTHE]. */
    fun addScythe(x: Float, y: Float, angle: Float, range: Float, bulge: Float, flight: Float, damage: Float, color: Long) {
        val h = hazards.obtain() ?: return
        h.active = true; h.kind = HazardKind.SCYTHE
        h.x = x; h.y = y; h.x2 = x; h.y2 = y; h.angle = angle; h.maxRadius = range; h.windup = bulge
        h.radius = 30f; h.timer = 0f; h.duration = flight; h.damage = damage; h.color = color; h.hitMask = 0; h.ownerUid = -1
    }

    /** Corrupted floor tile; see [HazardKind.TILE]. */
    fun addTile(x: Float, y: Float, half: Float, warn: Float, burn: Float, dps: Float, color: Long) {
        if (x < half || y < half || x > arena.width - half || y > arena.height - half) return
        val h = hazards.obtain() ?: return
        h.active = true; h.kind = HazardKind.TILE
        h.x = x; h.y = y; h.radius = half; h.windup = warn; h.timer = 0f; h.duration = warn + burn
        h.damage = dps; h.color = color; h.tick = 0f; h.hitMask = 0; h.ownerUid = -1
    }

    /** Worm Queen egg; see [HazardKind.EGG]. */
    fun addEgg(x: Float, y: Float, hatch: Float, count: Int, color: Long) {
        if (!arena.isFree(x, y, 16f)) return
        val h = hazards.obtain() ?: return
        h.active = true; h.kind = HazardKind.EGG
        h.x = x; h.y = y; h.radius = 20f; h.timer = 0f; h.duration = hatch; h.tick = count.toFloat()
        h.damage = 0f; h.color = color; h.hitMask = 0; h.ownerUid = -1
    }

    /** Hostile mine; see [HazardKind.MINE]. */
    fun addMine(x: Float, y: Float, arm: Float, life: Float, radius: Float, damage: Float, color: Long) {
        if (!arena.isFree(x, y, 14f)) return
        val h = hazards.obtain() ?: return
        h.active = true; h.kind = HazardKind.MINE
        h.x = x; h.y = y; h.radius = radius; h.maxRadius = MINE_TRIGGER
        h.windup = arm; h.timer = 0f; h.duration = life; h.tick = -1f
        h.damage = damage; h.color = color; h.hitMask = 0; h.ownerUid = -1
    }

    /** PULLED: drag the current operative toward ([x], [y]) for [seconds]. */
    fun pull(x: Float, y: Float, seconds: Float, strength: Float) {
        cur.pulled = seconds; cur.pullX = x; cur.pullY = y; cur.pullStrength = strength
    }

    /** Burning pie slice; see [HazardKind.BURN_SECTOR]. */
    fun addBurnSector(x: Float, y: Float, aim: Float, halfWidth: Float, reach: Float, warn: Float, burn: Float, dps: Float, color: Long) {
        val h = hazards.obtain() ?: return
        h.active = true; h.kind = HazardKind.BURN_SECTOR
        h.x = x; h.y = y; h.x2 = MathUtil.wrapAngle(aim).let { if (it < 0f) it + MathUtil.TWO_PI else it } * 1000f
        h.maxRadius = halfWidth * 1000f; h.radius = reach
        h.windup = warn; h.timer = 0f; h.duration = warn + burn
        h.damage = dps; h.color = color; h.tick = 0f; h.hitMask = 0; h.ownerUid = -1
    }

    /** True if angle [a] (radians) falls inside one of a fire wall's gaps. */
    internal fun inFireGap(h: Hazard, a: Float): Boolean {
        val gaps = h.y2.toInt()
        if (gaps <= 0) return false
        val base = h.x2 / 1000f
        for (k in 0 until gaps) {
            val g = base + MathUtil.TWO_PI * k / gaps
            if (kotlin.math.abs(MathUtil.wrapAngle(a - g)) < h.windup) return true
        }
        return false
    }

    /** Adds [stacks] CHILL to the current operative; at [CHILL_MAX] it FREEZES. */
    fun chill(stacks: Float) {
        if (cur.frozen > 0f) return
        cur.chill = min(CHILL_MAX, cur.chill + stacks)
        if (cur.chill >= CHILL_MAX) {
            cur.frozen = FREEZE_SECONDS
            cur.chill = 0f
            addText(px, py - 44f, "FROZEN", TextKind.PLAYER_HURT)
            repeat(14) { addParticle(px, py - 20f, 0xFF9AE6FF, 160f, 0.5f, 3f) }
        }
    }

    /** Movement multiplier from CHILL (each stack slows 9%). */
    internal fun chillSlow(o: Operative): Float = if (o.frozen > 0f) 0f else 1f - 0.09f * o.chill

    /** Black Ice Overlord's shell: fraction of its HP left (0..1), or -1 when there's no shell. */
    var bossIceShell = -1f

    /** Circuit Hydra: bit k set = head k alive (core shielded while non-zero); -1 = no hydra. */
    var bossHeadMask = -1

    /** SEIZED: the current operative can't move for [seconds]. */
    fun root(seconds: Float) {
        cur.rooted = max(cur.rooted, seconds)
        addText(px, py - 44f, "SEIZED", TextKind.PLAYER_HURT)
    }

    /** ENCRYPTED: for [ENCRYPT_SECONDS], moving charges a burst of [burst] damage. */
    fun encrypt(burst: Float) {
        cur.encrypted = ENCRYPT_SECONDS
        cur.encryptCharge = 0f
        cur.encryptDamage = burst
        addText(px, py - 44f, "ENCRYPTED", TextKind.PLAYER_HURT)
    }

    /** Ticks SEIZED / ENCRYPTED on the current operative (host). */
    private fun updateStatuses(dt: Float) {
        if (cur.rooted > 0f) cur.rooted = max(0f, cur.rooted - dt)
        if (cur.encrypted > 0f) {
            cur.encrypted = max(0f, cur.encrypted - dt)
            if (moving) cur.encryptCharge += dt / ENCRYPT_MOVE_SECONDS
            if (cur.encryptCharge >= 1f) {
                // Moved too much while encrypted: the ransom bursts.
                cur.encrypted = 0f; cur.encryptCharge = 0f
                damagePlayer(cur.encryptDamage, px, py, ignoreInvuln = true)
                addPulse(px, py, 120f, 0.4f, 0xFFFF3B3B)
                repeat(14) { addParticle(px, py, 0xFFFFC233, 240f, 0.5f, 3f) }
                if (cur === ops[primary]) fx.shake(6f, 0.3f)
            }
            if (cur.encrypted <= 0f) cur.encryptCharge = 0f
        }
        if (cur.frozen > 0f) cur.frozen = max(0f, cur.frozen - dt)
        if (cur.chill > 0f) cur.chill = max(0f, cur.chill - dt * CHILL_DECAY)
        if (cur.pulled > 0f) {
            cur.pulled = max(0f, cur.pulled - dt)
            if (!cur.remote) {
                val dx = cur.pullX - px; val dy = cur.pullY - py
                val d = sqrt(dx * dx + dy * dy)
                if (d > 60f) {
                    arena.pushOut(px + dx / d * cur.pullStrength * dt, py + dy / d * cur.pullStrength * dt, playerRadius)
                    px = arena.out[0]; py = arena.out[1]
                }
            }
        }
        if (cur.burning > 0f) {
            cur.burning = max(0f, cur.burning - dt)
            cur.burnTick -= dt
            if (cur.burnTick <= 0f) {
                cur.burnTick = BURN_TICK
                damagePlayer(cur.burnDps * BURN_TICK, px, py, ignoreInvuln = true)
                repeat(3) { addParticle(px, py - 20f, 0xFFFF7A1A, 90f, 0.4f, 3f) }
            }
            if (cur.burning <= 0f) cur.burnDps = 0f
        }
    }

    /** Calls [hit] with the operative's bit for each live operative within [radius] not yet in [mask]. */
    internal inline fun forEachOperativeHit(x: Float, y: Float, radius: Float, mask: Int, hit: (Int) -> Unit) {
        for (o in ops) {
            if (!o.alive) continue
            val bit = 1 shl o.index
            if (mask and bit != 0) continue
            if (MathUtil.dist2(o.px, o.py, x, y) < radius * radius) {
                val prev = cur
                cur = o
                hit(bit)
                cur = prev
            }
        }
    }

    /** EMP Blackout: the room goes dark at once. */
    fun blackout(x: Float, y: Float, color: Long) {
        darkness = 1f; darknessTarget = 1f
        addPulse(x, y, 900f, 1.0f, color)
        addPulse(x, y, 420f, 0.6f, 0xFFFFFFFF)
        fx.flash(0xFFFFFFFF, 0.45f, 0.35f)
        fx.shake(9f, 0.5f)
        sound(GameSound.FIREWALL_BREAK)
    }

    private fun updateDarkness(dt: Float) {
        if (lightFlicker > 0f) lightFlicker = max(0f, lightFlicker - dt)
        if (darkness < darknessTarget) darkness = min(darknessTarget, darkness + dt * 3f)
        else if (darkness > darknessTarget) darkness = max(darknessTarget, darkness - dt * 0.8f)
        // In the dark, the server blocks spit red sparks now and then.
        if (darkness > 0.5f && arena.obstacles.isNotEmpty() && rng.nextFloat() < dt * 5f) {
            val o = arena.obstacles[rng.nextInt(arena.obstacles.size)].rect
            val sx = o.left + rng.nextFloat() * o.width
            repeat(3) { addParticle(sx, o.top, 0xFFFF2A3A, 140f, 0.45f, 2.5f) }
        }
    }

    /** Boss-raised cubes: rising, solid or sinking. */
    val barriers = ArrayList<Barrier>()
    private var barrierSolidCount = 0


    init {
        for (o in ops) {
            o.build.recompute()
            o.hp = o.build.stats.maxHp
            o.firewall = o.build.stats.firewallMax
        }
        if (restore != null) applySnapshot(restore)
        else startLevel(level, null, false)
        if (restore == null) {
            // Starting Weapon Power: pick free upgrades before the first fight.
            ops[0].pendingUpgrades = config.startingUpgrades
            if (ops.size > 1) ops[1].pendingUpgrades = config.ally?.startingUpgrades ?: 0
            if (ops.any { it.pendingUpgrades > 0 }) openUpgrades(resumeCombat = true)
        }
    }

    // ======================================================================
    // Co-op (owner, 2026-10-08)
    // ======================================================================

    /** Runs [block] with each operative still on the field as the current one. */
    private inline fun forEachAlive(block: (Operative) -> Unit) {
        for (o in ops) {
            if (!o.alive) continue
            cur = o
            block(o)
        }
        cur = ops[primary]
    }

    /** Points the rules at the living operative closest to (x, y): who a threat chases and shoots at. */
    internal fun focusNearest(x: Float, y: Float) {
        if (ops.size == 1) return
        var best: Operative? = null
        var bd = Float.MAX_VALUE
        for (o in ops) {
            if (!o.alive) continue
            val d = MathUtil.dist2(x, y, o.px, o.py)
            if (d < bd) { bd = d; best = o }
        }
        if (best != null) cur = best
    }

    internal fun focusHost() { cur = ops[primary] }

    /** Co-op partner's stick. */
    fun setInputFor(index: Int, x: Float, y: Float) {
        val o = ops.getOrNull(index) ?: return
        o.inputX = x
        o.inputY = y
    }

    /** The partner left the game: the run carries on with whoever is still here. */
    fun removeOperative(index: Int) {
        val o = ops.getOrNull(index) ?: return
        if (index == primary || o.gone) return
        o.gone = true
        o.downed = false
        o.pendingUpgrades = 0
        o.offer = emptyList()
        o.beamActive = false
        showBanner("PARTNER DISCONNECTED", "Continuing solo", 2f)
        if (phase == Phase.UPGRADE && ops.all { it.gone || it.pendingUpgrades <= 0 }) finishUpgrades()
    }

    /** Downed operatives, revives by standing close, and game over when nobody is left up. True = run ended. */
    private fun updateCoop(dt: Float): Boolean {
        for (o in ops) {
            if (o.gone || o.downed || o.hp > 0f) continue
            o.downed = true
            o.hp = 0f
            o.reviveProgress = 0f
            o.beamActive = false
            o.moving = false
            addPulse(o.px, o.py, 200f, 0.7f, 0xFFFF2D55)
            showBanner("OPERATIVE DOWN", "Stand next to your partner to revive", 2f)
            sound(GameSound.GAME_OVER)
        }
        if (ops.none { it.alive }) {
            die()
            return true
        }
        for (o in ops) {
            if (!o.downed || o.gone) continue
            val helped = ops.any { it.alive && MathUtil.dist(it.px, it.py, o.px, o.py) < REVIVE_RADIUS }
            o.reviveProgress = if (helped) o.reviveProgress + dt else max(0f, o.reviveProgress - dt * 0.5f)
            if (o.reviveProgress >= REVIVE_SECONDS) reviveOperative(o)
        }
        return false
    }

    private fun reviveOperative(o: Operative) {
        o.downed = false
        o.reviveProgress = 0f
        o.hp = o.build.stats.maxHp * 0.5f
        o.firewall = o.build.stats.firewallMax
        o.invuln = 2f
        addPulse(o.px, o.py, 200f, 0.6f, 0xFF00FF9C)
        addText(o.px, o.py - 40f, "REVIVED", TextKind.HEAL)
        sound(GameSound.REVIVE)
    }

    /** Every operative still in the run gets a level-up pick. */
    private fun grantPick(n: Int = 1) {
        for (o in ops) if (!o.gone) o.pendingUpgrades += n
    }

    // ======================================================================
    // Input & main update
    // ======================================================================

    /** Joystick vector; magnitude 0..1. */
    fun setInput(x: Float, y: Float) {
        ops[primary].inputX = x
        ops[primary].inputY = y
    }

    fun update(delta: Float) {
        var remaining = fx.tick(delta.coerceIn(0f, MAX_FRAME))
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
        // Runs on menus too, so the power-restore fade plays over the reward screen.
        updateDarkness(dt)
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

        forEachAlive { updatePlayer(dt); updateOrbit(dt) }
        if (phase == Phase.COMBAT) {
            if (mode == GameMode.ENDLESS) updateEndless(dt) else updateSpawning(dt)
            updateEventRules(dt)
        }
        if (phase == Phase.COMBAT) forEachAlive { path.update(arena, px, py, dt) }
        ai.update(dt)
        bossBrain.update(dt)
        cur = ops[primary]
        updateProjectiles(dt)
        updateZaps(dt)
        updateHazards(dt)
        updateBarriers(dt)
        if (phase == Phase.COMBAT) updateDwell(dt)
        cur = ops[primary]
        if (coop) {
            if (updateCoop(dt)) return
        } else if (hp <= 0f) {
            die()
            return
        }

        forEachAlive { updateVault(dt) }
        when (phase) {
            Phase.COMBAT -> if (mode == GameMode.ENDLESS) {
                if (ops.any { it.pendingUpgrades > 0 }) openUpgrades(resumeCombat = true)
            } else checkCleared()
            Phase.CLEARED -> if (phaseTimer >= CLEAR_BEAT) afterClear()
            Phase.PORTAL -> forEachAlive { portalCheck() }
            else -> {}
        }
    }

    /** The current operative walking into the top gate or the shop's side gate. */
    private fun portalCheck() {
        if (phase != Phase.PORTAL) return
        if (kotlin.math.abs(px - arena.portalX) < GATE_HALF_WIDTH && py < arena.portalY + PORTAL_RADIUS) {
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
    }

    // ======================================================================
    // Level lifecycle
    // ======================================================================

    private fun startLevel(newLevel: Int, previousArena: String?, previousEvent: Boolean, forced: LevelPlan? = null) {
        level = newLevel
        fx.clear()
        darkness = 0f; darknessTarget = 0f; lightFlicker = 0f; bossVeil = 0f; bossShield = -1f; bossSync = 0f; bossTrail = FloatArray(0)
        for (o in ops) { o.rooted = 0f; o.encrypted = 0f; o.encryptCharge = 0f; o.burning = 0f; o.burnDps = 0f; o.pulled = 0f; o.chill = 0f; o.frozen = 0f }
        bossIceShell = -1f
        bossHeadMask = -1
        bossRingFilled = -1; bossRingOut = false
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
        for (o in ops) {
            if (o.gone) continue
            // Partners start side by side; anyone downed is back up at half HP.
            arena.pushOut(arena.spawnX + if (coop) (o.index * 2 - 1) * 42f else 0f, arena.spawnY, playerRadius)
            o.px = arena.out[0]
            o.py = arena.out[1]
            o.facing = -MathUtil.PI / 2f
            o.invuln = 1.0f
            if (o.downed) {
                o.downed = false
                o.reviveProgress = 0f
                o.hp = o.build.stats.maxHp * 0.5f
                o.firewall = o.build.stats.firewallMax
            }
        }
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
        barriers.clear(); barrierSolidCount = 0
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
                spawnTimer = max(0.35f, rules.spawnInterval - level * 0.006f) / (if (coop) 1f + COOP_EXTRA_THREATS else 1f)
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
                // Co-op: about a third more threats per wave.
                if (coop && aliveCount() < Scaling.MAX_ALIVE && rng.nextFloat() < COOP_EXTRA_THREATS) spawnEnemy(spec.def, spec.elite, telegraph = true)
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
            grantPick()
        }
        // Campaign: every cleared level earns a power-up, and deeper levels
        // roll for bonus rewards (owner, 2026-10-08: "higher levels give more").
        grantPick()
        if (rng.nextFloat() < min(0.45f, (level - 1) * 0.012f)) grantPick()
        if (level >= 25 && rng.nextFloat() < min(0.3f, (level - 24) * 0.01f)) grantPick()
        addText(px, py - 40f, "+$euros €", TextKind.INFO)
        addPulse(px, py, 420f, 0.7f, 0xFF00FF9C)
        // Firewall fully restores between arenas.
        for (o in ops) if (o.alive) o.firewall = o.build.stats.firewallMax
        showBanner(if (plan.kind == LevelKind.EVENT) "EVENT COMPLETE" else "THREATS ELIMINATED", "", 1.2f)
        sound(GameSound.LEVEL_COMPLETE)
        sound(GameSound.CURRENCY)
    }

    private fun afterClear() {
        if (ops.any { it.pendingUpgrades > 0 }) {
            openUpgrades()
        } else {
            openPortal()
        }
    }

    /** Rarity luck for the next offer: deeper levels, harder difficulty and boss rewards all help. */
    private fun currentLuck(): Float =
        level * 0.015f + config.difficulty.luck + (if (bossLuckPending) 1.2f else 0f)

    private fun openUpgrades(resumeCombat: Boolean = false) {
        offerLuck = currentLuck()
        // Each operative rolls and picks its own cards (owner, 2026-10-08).
        var any = false
        for (o in ops) {
            cur = o
            rewardBatchTotal = if (o.gone) 0 else pendingUpgrades
            rewardBatchTaken = 0
            offer = if (o.gone || pendingUpgrades <= 0) emptyList() else build.rollOffer(rng, luck = offerLuck)
            if (offer.isEmpty()) pendingUpgrades = 0 else any = true
        }
        cur = ops[primary]
        if (!any) {
            if (!resumeCombat) openPortal()
            return
        }
        upgradeReturnsToCombat = resumeCombat
        phase = Phase.UPGRADE
        phaseTimer = 0f
    }

    private fun finishUpgrades() {
        for (o in ops) {
            o.offer = emptyList()
            o.rewardBatchTotal = 0
            o.rewardBatchTaken = 0
            o.pendingUpgrades = 0
        }
        bossLuckPending = false
        // 1 in 20: the shopkeeper sends a message and a side gate opens (solo only).
        if (!coop && !upgradeReturnsToCombat && mode == GameMode.CAMPAIGN && !inShop && rng.nextFloat() < SHOP_CHANCE) offerShop()
        if (upgradeReturnsToCombat) {
            upgradeReturnsToCombat = false
            phase = Phase.COMBAT
            phaseTimer = 0f
        } else {
            openPortal()
        }
    }

    /**
     * UI: the player picked card [index]. A new weapon with all
     * [com.cyberoperative.game.data.Upgrades.MAX_WEAPONS] slots full needs
     * [replace]: the equipped weapon it takes the place of.
     */
    fun chooseUpgrade(index: Int, replace: String? = null) = chooseUpgradeFor(primary, index, replace)

    /** True when card [index] is a new weapon and every weapon slot is taken (UI shows the swap grid). */
    fun offerNeedsSlot(index: Int): Boolean = offer.getOrNull(index)?.let { build.needsSlot(it.def) } == true

    /** Operative [opIndex] picked card [index] (co-op: each picks their own). */
    fun chooseUpgradeFor(opIndex: Int, index: Int, replace: String? = null) {
        if (phase != Phase.UPGRADE) return
        val o = ops.getOrNull(opIndex) ?: return
        if (o.gone) return
        cur = o
        try { takeOffered(index, replace) } finally { cur = ops[primary] }
        if (ops.all { it.gone || it.pendingUpgrades <= 0 }) finishUpgrades()
    }

    private fun takeOffered(index: Int, replace: String?) {
        val choice = offer.getOrNull(index) ?: return
        if (build.needsSlot(choice.def)) {
            // Weapon slots full: only with a valid weapon to swap out.
            if (replace == null || replace !in build.weapons()) return
            dropWeapon(replace)
        }
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
            if (offer.isEmpty()) pendingUpgrades = 0
        } else {
            offer = emptyList()
        }
    }

    /** UI: spend a reroll (permanent progression) to redraw the cards. */
    fun reroll(): Boolean = rerollFor(primary)

    fun rerollFor(opIndex: Int): Boolean {
        val o = ops.getOrNull(opIndex) ?: return false
        if (phase != Phase.UPGRADE || o.rerollsLeft <= 0 || o.pendingUpgrades <= 0) return false
        o.rerollsLeft--
        o.offer = o.build.rollOffer(rng, luck = offerLuck)
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
        rebuildArena()
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
        // New weapons are offered even with full slots: buying one swaps out an equipped weapon.
        val pool = Upgrades.all.filter {
            !it.instant && it.rarity.ordinal >= com.cyberoperative.game.data.Rarity.LEGENDARY.ordinal && build.isEligible(it)
        }.toMutableList()
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
    /** True when shop item [index] is a new weapon and every slot is taken (UI shows the swap grid). */
    fun shopNeedsSlot(index: Int): Boolean = shopItems.getOrNull(index)?.let { build.needsSlot(it.def) } == true

    /** Buys shop item [index]; a new weapon with full slots needs [replace] (the equipped weapon it swaps out). */
    fun buyShopItem(index: Int, replace: String? = null): Boolean {
        val item = shopItems.getOrNull(index) ?: return false
        if (!inShop || item.sold || eurosEarned < item.price || !build.isEligible(item.def)) return false
        if (build.needsSlot(item.def)) {
            if (replace == null || replace !in build.weapons()) return false
            dropWeapon(replace)
        }
        eurosEarned -= item.price
        applyUpgrade(item.def)
        shopItems = shopItems.mapIndexed { i, it -> if (i == index) it.copy(sold = true) else it }
        addText(px, py - 40f, "-${item.price} €", TextKind.INFO)
        sound(GameSound.CURRENCY)
        sound(GameSound.UPGRADE_SELECTED)
        return true
    }

    /** Frees a weapon slot: the weapon, its timers and anything it left on the field. */
    private fun dropWeapon(id: String) {
        build.remove(id)
        weaponTimers.remove(id)
        if (id == Upgrades.PLASMA_BEAM.id) { beamActive = false; beamHeat = 0f; beamCooldown = 0f }
        hp = min(hp, stats.maxHp)
        firewall = min(firewall, stats.firewallMax)
        sound(GameSound.UI_BACK)
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

    val canRevive: Boolean get() = !coop && phase == Phase.DEAD && revivesLeft > 0

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
            coop -> "CO-OP RUNS CAN'T BE SAVED"
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

        // Movement. A co-op guest moves its own operative and reports where it is.
        val mag = sqrt(inputX * inputX + inputY * inputY)
        moving = if (cur.remote) cur.netMoving else mag > MOVE_DEADZONE
        updateStatuses(dt)
        if (cur.remote && (cur.rooted > 0f || cur.frozen > 0f)) {
            // SEIZED: the guest's reported position is ignored until it breaks free.
            stillTime += dt
        } else if (cur.remote) {
            arena.pushOut(cur.netX, cur.netY, playerRadius)
            px = arena.out[0]
            py = arena.out[1]
            if (moving) {
                facing = cur.netFacing
                stillTime = 0f
                followUpLeft = 0
            } else stillTime += dt
        } else if (moving && cur.rooted <= 0f && cur.frozen <= 0f) {
            val m = min(1f, mag)
            val speed = s.moveSpeed * m * chillSlow(cur)
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
        p.owner = cur.index
        p.tint = 0L
        p.returning = false
        p.armTimer = 0f
        p.splash = 0f
        return p
    }

    // ======================================================================
    // Arsenal: data-driven auto-weapons (data/Weapons.kt)
    // ======================================================================

    private val weaponTimers: HashMap<String, Float> get() = cur.weaponTimers

    private class SpiralBurst(val spec: WeaponSpec, val damage: Float, var left: Int, var angle: Float, val step: Float) {
        var timer = 0f
    }
    @Suppress("UNCHECKED_CAST")
    private val spirals: ArrayList<SpiralBurst> get() = cur.spirals as ArrayList<SpiralBurst>

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
            // Hit cooldowns tick once per step, not once per operative.
            if (cur === firstAlive()) {
                if (e.orbHitCooldown > 0f) e.orbHitCooldown -= dt
                if (e.bladeHitCooldown > 0f) e.bladeHitCooldown -= dt
            }
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

    private fun firstAlive(): Operative? = ops.firstOrNull { it.alive }

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
        if (!cur.alive) return
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
            if (cur === ops[primary] && dmg >= s.maxHp * 0.12f) fx.shake(5f, 0.25f)
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
        // Circuit Hydra: the core is shielded while any of its heads lives.
        if (e.boss?.def?.headShield == true && bossHeadMask > 0) {
            bossBrain.headShieldSpark(e)
            return
        }
        // Black Ice Overlord's Permafrost Shell: hits chip the shell; the boss takes a quarter.
        if (e.boss != null && bossIceShell > 0f && bossBrain.shellAbsorb(e, raw)) return
        // Spectral Firewall: its ring plates soak up fire from outside unless you're lined up with a gap.
        if (e.boss != null && bossBrain.ringBlocks(e, px, py)) {
            bossBrain.ringSpark(e, px, py)
            return
        }
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
            grantPick()
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
        val hpMul = Scaling.enemyHp(level) * rules.enemyHpMul * config.difficulty.enemyHp * opHpNow * adaptiveHp * config.coopHpMul *
            (if (elite != null) EliteModifier.BASE_HP_MUL * elite.hpMul else 1f)
        e.maxHp = def.baseHp * hpMul
        e.hp = e.maxHp
        e.radius = def.radius * (if (elite != null) EliteModifier.SIZE_MUL else 1f)
        e.speed = def.baseSpeed * Scaling.enemySpeed(level) * rules.enemySpeedMul * (elite?.speedMul ?: 1f)
        e.damageMul = Scaling.enemyDamage(level) * rules.enemyDamageMul * config.difficulty.enemyDamage * opDamageNow * adaptiveDamage
        e.attackRateMul = Scaling.attackRate(level) * (elite?.attackRateMul ?: 1f)
        e.damageTakenMul = elite?.damageTakenMul ?: 1f
        e.hasteTimer = 0f; e.hasteMul = 1f; e.orbitSlot = -1
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
        for (p in projectiles.items) {
            if (!p.active) continue
            // Friendly shots act for whoever fired them; hostile ones chase the closest operative.
            if (p.friendly) cur = ops.getOrNull(p.owner)?.takeIf { !it.gone } ?: ops[primary]
            else focusNearest(p.x, p.y)
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
                for (o in ops) {
                    if (!o.alive || !p.active) continue
                    cur = o
                    hostileShotVsOperative(p)
                }
            }
        }
        cur = ops[primary]
    }

    /** A hostile packet against the current operative: blocked by its blades, or a hit. */
    private fun hostileShotVsOperative(p: Projectile) {
        val s = stats
        // Encryption Blades block hostile packets.
        if (s.bladeCount > 0) {
            for (i in 0 until s.bladeCount) {
                val a = bladeAngle + MathUtil.TWO_PI * i / s.bladeCount
                val bx = px + cos(a) * s.bladeRadius
                val by = py + sin(a) * s.bladeRadius
                val rr = 16f + p.radius
                if (MathUtil.dist2(bx, by, p.x, p.y) < rr * rr) {
                    p.active = false
                    addParticle(p.x, p.y, 0xFF00E5FF, 120f, 0.25f, 2f)
                    sound(GameSound.SHIELD_BLOCK)
                    return
                }
            }
        }
        val rr = playerRadius * 0.8f + p.radius
        if (MathUtil.dist2(p.x, p.y, px, py) < rr * rr) {
            p.active = false
            damagePlayer(p.damage, p.x, p.y)
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
        h.radius = 3f; h.damage = 0f; h.hitMask = 0
        return h
    }

    fun addBlast(x: Float, y: Float, radius: Float, delay: Float, damage: Float, color: Long) {
        val h = hazards.obtain() ?: return
        h.active = true; h.kind = HazardKind.BLAST
        h.x = x; h.y = y; h.radius = radius
        h.timer = 0f; h.duration = delay; h.damage = damage; h.color = color
        h.hitMask = 0; h.ownerUid = -1
    }

    fun addZone(x: Float, y: Float, radius: Float, duration: Float, dps: Float, color: Long, telegraph: Float, kind: HazardKind = HazardKind.ZONE) {
        val h = hazards.obtain() ?: return
        h.active = true; h.kind = kind
        h.x = x; h.y = y; h.radius = radius
        h.timer = 0f; h.windup = telegraph; h.duration = telegraph + duration
        h.damage = dps; h.color = color; h.tick = 0f; h.hitMask = 0; h.ownerUid = -1
    }

    fun addShockRing(x: Float, y: Float, maxRadius: Float, speed: Float, damage: Float, color: Long) {
        val h = hazards.obtain() ?: return
        h.active = true; h.kind = HazardKind.SHOCK_RING
        h.x = x; h.y = y; h.radius = 10f; h.maxRadius = maxRadius
        h.timer = 0f; h.duration = maxRadius / speed; h.damage = damage; h.color = color
        h.hitMask = 0; h.ownerUid = -1
    }

    fun addBeam(x: Float, y: Float, angle: Float, length: Float, width: Float, windup: Float, active: Float, damage: Float, color: Long) {
        val h = hazards.obtain() ?: return
        h.active = true; h.kind = HazardKind.BEAM
        h.x = x; h.y = y
        h.x2 = x + cos(angle) * length; h.y2 = y + sin(angle) * length
        h.radius = width; h.windup = windup
        h.timer = 0f; h.duration = windup + active; h.damage = damage; h.color = color
        h.hitMask = 0; h.ownerUid = -1
    }

    /** Telegraphed sweeping laser (see [HazardKind.SWEEP]). */
    fun addSweep(
        x: Float, y: Float, startAngle: Float, sweep: Float, width: Float, windup: Float, active: Float,
        damage: Float, color: Long, ownerUid: Int
    ): Hazard? {
        val h = hazards.obtain() ?: return null
        h.active = true; h.kind = HazardKind.SWEEP
        h.x = x; h.y = y; h.angle = startAngle; h.angVel = sweep / active; h.maxRadius = sweep
        h.radius = width; h.windup = windup
        h.timer = 0f; h.duration = windup + active; h.damage = damage; h.color = color
        h.hitMask = 0; h.ownerUid = ownerUid; h.tick = 0f; h.head = -1
        clipRay(h)
        return h
    }

    /** Lobbed shell from ([fromX], [fromY]) landing on ([x], [y]) after [flight]. */
    fun addMortar(fromX: Float, fromY: Float, x: Float, y: Float, radius: Float, flight: Float, damage: Float, color: Long): Hazard? {
        val h = hazards.obtain() ?: return null
        h.active = true; h.kind = HazardKind.MORTAR
        h.x = x; h.y = y; h.x2 = fromX; h.y2 = fromY; h.radius = radius
        h.timer = 0f; h.duration = flight; h.damage = damage; h.color = color
        h.hitMask = 0; h.ownerUid = -1; h.tick = 0f
        return h
    }

    /** Crystal spike cluster at ([x], [y]) bursting after [delay] (see [HazardKind.SPIKE]). */
    fun addSpike(x: Float, y: Float, radius: Float, delay: Float, damage: Float, color: Long) {
        if (x < 16f || y < 16f || x > arena.width - 16f || y > arena.height - 16f) return
        val h = hazards.obtain() ?: return
        h.active = true; h.kind = HazardKind.SPIKE
        h.x = x; h.y = y; h.radius = radius; h.maxRadius = SPIKE_LINGER
        h.timer = 0f; h.duration = delay; h.damage = damage; h.color = color
        h.hitMask = 0; h.ownerUid = -1; h.tick = 0f
    }

    // --- Where the operatives have been standing (Infected Zone) ----------

    private var dwell = FloatArray(0)
    private var dwellCols = 0
    private var dwellRows = 0

    private fun updateDwell(dt: Float) {
        val cols = (arena.width / DWELL_CELL).toInt() + 1
        val rows = (arena.height / DWELL_CELL).toInt() + 1
        if (cols != dwellCols || rows != dwellRows) { dwellCols = cols; dwellRows = rows; dwell = FloatArray(cols * rows) }
        // Old standing time fades over ~10 s.
        val keep = 1f - dt / 10f
        for (i in dwell.indices) dwell[i] *= keep
        forEachAlive {
            val c = (px / DWELL_CELL).toInt().coerceIn(0, cols - 1)
            val r = (py / DWELL_CELL).toInt().coerceIn(0, rows - 1)
            dwell[r * cols + c] += dt
        }
    }

    /** Centres of the [n] cells the operatives have stood in longest lately, at least [minSep] apart. */
    fun dwellHotspots(n: Int, minSep: Float, avoidX: Float, avoidY: Float): List<Pair<Float, Float>> {
        val order = dwell.indices.filter { dwell[it] > 0.3f }.sortedByDescending { dwell[it] }
        val out = ArrayList<Pair<Float, Float>>()
        for (i in order) {
            if (out.size >= n) break
            val x = (i % dwellCols + 0.5f) * DWELL_CELL
            val y = (i / dwellCols + 0.5f) * DWELL_CELL
            if (MathUtil.dist(x, y, avoidX, avoidY) < minSep) continue
            if (out.any { MathUtil.dist(x, y, it.first, it.second) < minSep }) continue
            out += x to y
        }
        return out
    }

    /** Sets a sweep's end to where its ray first meets an obstacle or wall. */
    private fun clipRay(h: Hazard) {
        val dx = cos(h.angle)
        val dy = sin(h.angle)
        var d = 0f
        val step = 8f
        while (d < SWEEP_LENGTH) {
            val nx = h.x + dx * (d + step)
            val ny = h.y + dy * (d + step)
            if (nx < 0f || ny < 0f || nx > arena.width || ny > arena.height) break
            // Start past the boss's own footprint so a block it stands against doesn't swallow the beam.
            if (d > 30f && arena.obstacleAt(nx, ny, 1f) >= 0) break
            d += step
        }
        h.x2 = h.x + dx * d
        h.y2 = h.y + dy * d
    }

    // --- Barrier cubes (Boss Expansion S6) --------------------------------


    /**
     * Raises a cube centred on ([x], [y]) if the spot is inside the room, clear of
     * walls, other cubes, the boss and every operative. Returns whether it rose.
     */
    fun addBarrier(x: Float, y: Float, half: Float, rise: Float, life: Float, clearance: Float = 26f, style: Int = Barrier.STYLE_VAULT): Boolean {
        if (x - half < 24f || y - half < 24f || x + half > arena.width - 24f || y + half > arena.height - 24f) return false
        val r = com.cyberoperative.game.core.Rect(x - half, y - half, x + half, y + half)
        for (i in 0 until arena.obstacles.size) if (arena.rect(i).let { it.left < r.right + 4f && it.right > r.left - 4f && it.top < r.bottom + 4f && it.bottom > r.top - 4f }) return false
        // Cubes may sit flush against each other (walls), just not overlap.
        for (b in barriers) if (b.left < r.right - 0.5f && b.right > r.left + 0.5f && b.top < r.bottom - 0.5f && b.bottom > r.top + 0.5f) return false
        for (o in ops) if (!o.gone && r.intersectsCircle(o.px, o.py, playerRadius + clearance)) return false
        val b = boss
        if (b != null && r.intersectsCircle(b.x, b.y, b.radius + 12f)) return false
        barriers += Barrier(x, y, half, rise, life, style)
        return true
    }

    /** Ends every cube early (boss down, room over). */
    fun sinkBarriers() {
        for (b in barriers) if (b.timer < b.rise + b.life) b.timer = b.rise + b.life
    }

    private fun updateBarriers(dt: Float) {
        if (barriers.isEmpty() && barrierSolidCount == 0) return
        for (b in barriers) b.timer += dt
        barriers.removeAll { it.done }
        val solid = barriers.count { it.solid }
        // Rebuild only when a cube changes state (rose or started sinking).
        if (solid != barrierSolidCount || barriers.any { it.solid && it.timer - dt < it.rise }) {
            barrierSolidCount = solid
            rebuildArena()
            // Anyone the cube rose under is pushed out to its nearest side.
            forEachAlive {
                if (arena.pushOut(px, py, playerRadius)) { px = arena.out[0]; py = arena.out[1] }
            }
            for (e in enemies.items) if (e.active && e.boss == null && arena.pushOut(e.x, e.y, e.radius)) { e.x = arena.out[0]; e.y = arena.out[1] }
        }
    }

    /** The room's obstacles plus the data cache and any solid barrier cubes (cache stays last). */
    private fun rebuildArena() {
        val extra = ArrayList<com.cyberoperative.game.data.ObstacleSpec>()
        for (b in barriers) if (b.solid) extra += com.cyberoperative.game.data.ObstacleSpec(
            com.cyberoperative.game.core.Rect(b.left, b.top, b.right, b.bottom),
            if (b.style == Barrier.STYLE_LOCK) com.cyberoperative.game.data.ObstacleKind.LOCK_CUBE else com.cyberoperative.game.data.ObstacleKind.BARRIER_CUBE
        )
        if (vaultPresent) extra += Arena.vaultObstacle(plan.arena)
        arena = Arena(plan.arena, extra)
    }

    private fun updateHazards(dt: Float) {
        for (h in hazards.items) {
            if (!h.active) continue
            h.timer += dt
            when (h.kind) {
                HazardKind.LINE -> if (h.timer >= h.duration) h.active = false
                HazardKind.BLAST, HazardKind.MORTAR, HazardKind.ORBITAL -> if (h.timer >= h.duration) {
                    h.active = false
                    forEachAlive {
                        if (MathUtil.dist2(px, py, h.x, h.y) < (h.radius + playerRadius * 0.6f).let { it * it }) {
                            damagePlayer(h.damage, h.x, h.y)
                        }
                    }
                    addPulse(h.x, h.y, h.radius, 0.3f, h.color)
                    if (h.kind == HazardKind.MORTAR && h.tick == 2f) {
                        // Ice crystal: a hit also chills.
                        forEachAlive { if (MathUtil.dist2(px, py, h.x, h.y) < (h.radius + playerRadius * 0.6f).let { it * it }) chill(1.5f) }
                    }
                    if (h.kind == HazardKind.MORTAR) {
                        repeat(10) { addParticle(h.x, h.y, h.color, 220f, 0.45f, 3f) }
                        if (MathUtil.dist2(ops[primary].px, ops[primary].py, h.x, h.y) < 260f * 260f) fx.shake(3.5f, 0.2f)
                    }
                }
                HazardKind.ZONE, HazardKind.INFECTED -> {
                    if (h.timer >= h.duration) { h.active = false; continue }
                    if (h.timer >= h.windup) {
                        h.tick -= dt
                        if (h.tick <= 0f) {
                            var hit = false
                            forEachAlive {
                                if (MathUtil.dist2(px, py, h.x, h.y) < h.radius * h.radius) {
                                    hit = true
                                    damagePlayer(h.damage * 0.5f, h.x, h.y, ignoreInvuln = true)
                                }
                            }
                            if (hit) h.tick = 0.5f
                        }
                    }
                }
                HazardKind.SHOCK_RING -> {
                    h.radius = 10f + (h.maxRadius - 10f) * (h.timer / h.duration)
                    if (h.timer >= h.duration) { h.active = false; continue }
                    forEachAlive { o ->
                        val bit = 1 shl o.index
                        if (h.hitMask and bit == 0) {
                            val d = MathUtil.dist(px, py, h.x, h.y)
                            if (kotlin.math.abs(d - h.radius) < RING_THICKNESS + playerRadius * 0.5f) {
                                h.hitMask = h.hitMask or bit
                                damagePlayer(h.damage, h.x, h.y)
                            }
                        }
                    }
                }
                HazardKind.SLASH -> if (h.timer >= h.duration) h.active = false
                HazardKind.TILE -> {
                    if (h.timer >= h.duration) { h.active = false; continue }
                    if (h.timer >= h.windup) {
                        h.tick -= dt
                        if (h.tick <= 0f) {
                            var hit = false
                            forEachAlive {
                                if (kotlin.math.abs(px - h.x) < h.radius && kotlin.math.abs(py - h.y) < h.radius) {
                                    hit = true
                                    damagePlayer(h.damage * 0.5f, h.x, h.y, ignoreInvuln = true)
                                }
                            }
                            if (hit) h.tick = 0.5f
                        }
                    }
                }
                HazardKind.EGG -> if (h.timer >= h.duration) {
                    h.active = false
                    val def = com.cyberoperative.game.data.Enemies.SWARMLING
                    for (k in 0 until h.tick.toInt()) {
                        if (aliveCount() >= com.cyberoperative.game.core.Scaling.MAX_ALIVE_SWARM) break
                        val a = MathUtil.TWO_PI * k / h.tick
                        spawnEnemyAt(def, null, h.x + cos(a) * 18f, h.y + sin(a) * 18f, telegraph = false)?.isChild = true
                    }
                    addPulse(h.x, h.y, 50f, 0.3f, h.color)
                    repeat(8) { addParticle(h.x, h.y, h.color, 180f, 0.4f, 3f) }
                }
                HazardKind.SCYTHE -> {
                    if (h.timer >= h.duration) { h.active = false; continue }
                    val u = h.timer / h.duration
                    val out = sin(u * MathUtil.PI) * h.maxRadius
                    val side = (1f - cos(u * MathUtil.TWO_PI)) / 2f * h.windup
                    val dx = cos(h.angle); val dy = sin(h.angle)
                    h.x2 = h.x + dx * out - dy * side
                    h.y2 = h.y + dy * out + dx * side
                    forEachAlive { o ->
                        val bit = 1 shl o.index
                        if (h.hitMask and bit == 0 && MathUtil.dist2(px, py, h.x2, h.y2) < (h.radius + playerRadius * 0.6f).let { it * it }) {
                            h.hitMask = h.hitMask or bit
                            damagePlayer(h.damage, h.x2, h.y2)
                        }
                    }
                }
                HazardKind.MINE -> {
                    if (h.tick < 0f) {
                        if (h.timer >= h.duration) h.tick = 0f
                        else if (h.timer >= h.windup) forEachAlive {
                            if (MathUtil.dist2(px, py, h.x, h.y) < h.maxRadius * h.maxRadius) h.tick = 0f
                        }
                    } else {
                        h.tick += dt
                        if (h.tick >= MINE_FUSE) {
                            h.active = false
                            forEachAlive {
                                if (MathUtil.dist2(px, py, h.x, h.y) < (h.radius + playerRadius * 0.6f).let { it * it }) damagePlayer(h.damage, h.x, h.y)
                            }
                            addPulse(h.x, h.y, h.radius, 0.35f, h.color)
                            repeat(12) { addParticle(h.x, h.y, h.color, 260f, 0.45f, 3f) }
                        }
                    }
                }
                HazardKind.ICE -> {
                    if (h.timer >= h.duration) { h.active = false; continue }
                    if (h.timer >= h.windup) {
                        h.tick -= dt
                        if (h.tick <= 0f) {
                            h.tick = 0.5f
                            forEachAlive { if (MathUtil.dist2(px, py, h.x, h.y) < h.radius * h.radius) chill(1f) }
                        }
                    }
                }
                HazardKind.FIRE_WALL, HazardKind.COIL -> {
                    if (h.timer >= h.duration) { h.active = false; continue }
                    val r0 = h.angle
                    h.radius = r0 + (h.maxRadius - r0) * (h.timer / h.duration)
                    // The gaps drift, so you have to move to stay lined up.
                    h.x2 = MathUtil.wrapAngle(h.x2 / 1000f + h.angVel * dt) * 1000f
                    forEachAlive { o ->
                        val bit = 1 shl o.index
                        if (h.hitMask and bit != 0) return@forEachAlive
                        val d = MathUtil.dist(px, py, h.x, h.y)
                        if (kotlin.math.abs(d - h.radius) < FIRE_WALL_THICKNESS + playerRadius * 0.5f && !inFireGap(h, atan2(py - h.y, px - h.x))) {
                            h.hitMask = h.hitMask or bit
                            damagePlayer(h.damage, h.x, h.y)
                            if (h.kind == HazardKind.FIRE_WALL) ignite(BURN_SECONDS, h.damage * 0.35f)
                        }
                    }
                }
                HazardKind.BURN_SECTOR -> {
                    if (h.timer >= h.duration) { h.active = false; continue }
                    if (h.timer >= h.windup) {
                        h.tick -= dt
                        if (h.tick <= 0f) {
                            h.tick = 0.4f
                            val aim = h.x2 / 1000f
                            val half = h.maxRadius / 1000f
                            forEachAlive {
                                val d = MathUtil.dist(px, py, h.x, h.y)
                                if (d < h.radius && kotlin.math.abs(MathUtil.wrapAngle(atan2(py - h.y, px - h.x) - aim)) < half) {
                                    damagePlayer(h.damage * 0.4f, h.x, h.y, ignoreInvuln = true)
                                    ignite(BURN_SECONDS, h.damage * 0.3f)
                                }
                            }
                        }
                    }
                }
                HazardKind.KEY_ZONE -> {
                    if (h.timer >= h.duration) { h.active = false; continue }
                    var inside = false
                    forEachAlive { if (MathUtil.dist2(px, py, h.x, h.y) < h.radius * h.radius) inside = true }
                    h.windup = if (inside) h.windup + dt / KEY_CAPTURE_SECONDS else max(0f, h.windup - dt * 0.25f)
                    if (h.windup >= 1f) {
                        h.active = false
                        addPulse(h.x, h.y, h.radius * 1.8f, 0.5f, 0xFFFFC233)
                        repeat(18) { addParticle(h.x, h.y, 0xFFFFC233, 220f, 0.6f, 3f) }
                        addText(h.x, h.y - 30f, "UNLOCKED", TextKind.INFO)
                        sound(GameSound.ACCESS_GRANTED)
                        bossBrain.keyCaptured()
                    }
                }
                HazardKind.RANSOM_RING -> {
                    h.radius = 10f + (h.maxRadius - 10f) * (h.timer / h.duration)
                    if (h.timer >= h.duration) { h.active = false; continue }
                    forEachAlive { o ->
                        val bit = 1 shl o.index
                        if (h.hitMask and bit == 0 && kotlin.math.abs(MathUtil.dist(px, py, h.x, h.y) - h.radius) < RING_THICKNESS + playerRadius * 0.5f) {
                            h.hitMask = h.hitMask or bit
                            damagePlayer(h.damage, h.x, h.y)
                            encrypt(h.damage * 1.8f)
                        }
                    }
                }
                HazardKind.SPIKE -> {
                    if (h.timer >= h.duration + h.maxRadius) { h.active = false; continue }
                    if (h.tick == 0f && h.timer >= h.duration) {
                        h.tick = 1f
                        forEachAlive {
                            if (MathUtil.dist2(px, py, h.x, h.y) < (h.radius + playerRadius * 0.6f).let { it * it }) damagePlayer(h.damage, h.x, h.y)
                        }
                        repeat(4) { addParticle(h.x, h.y, h.color, 160f, 0.35f, 2.5f) }
                    }
                }
                HazardKind.SWEEP -> {
                    if (h.timer >= h.duration) { h.active = false; continue }
                    // The laser stays on its emitter while the boss shifts.
                    val owner = boss
                    if (owner != null && owner.uid == h.ownerUid) {
                        val rig = bossTrail
                        if (h.head >= 0 && bossHeadMask >= 0 && (bossHeadMask shr h.head) and 1 == 0) {
                            // Its head was destroyed: the beam dies with it.
                            h.active = false; continue
                        }
                        if (h.head >= 0 && rig.size >= (h.head + 1) * 4) {
                            // Hydra beams stay in the mouth of the head that breathed them.
                            val (mx, my) = HydraRig.mouth(rig, h.head, owner.radius, HydraRig.facing(rig, h.head, px, py))
                            h.x = mx; h.y = my
                        } else { h.x = owner.x; h.y = owner.y }
                    }
                    if (h.timer >= h.windup) h.angle += h.angVel * dt
                    clipRay(h)
                    if (h.timer >= h.windup) forEachAlive { o ->
                        val bit = 1 shl o.index
                        if (h.hitMask and bit == 0 && distToSegment(px, py, h.x, h.y, h.x2, h.y2) < h.radius * 0.5f + playerRadius * 0.6f) {
                            h.hitMask = h.hitMask or bit
                            damagePlayer(h.damage, h.x, h.y)
                            // Purge Spin flame jets set you on fire; Ice Laser beams (tick 2) chill.
                            if (h.tick == 1f) ignite(BURN_SECONDS, h.damage * 0.3f)
                            if (h.tick == 2f) chill(2f)
                        }
                    }
                }
                HazardKind.BEAM -> {
                    if (h.timer >= h.duration) { h.active = false; continue }
                    if (h.timer >= h.windup) forEachAlive { o ->
                        val bit = 1 shl o.index
                        if (h.hitMask and bit == 0 && distToSegment(px, py, h.x, h.y, h.x2, h.y2) < h.radius * 0.5f + playerRadius * 0.6f) {
                            h.hitMask = h.hitMask or bit
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
    /** What the current boss pays out when it falls (shown on the dossier card). */
    fun bossBounty(): Int {
        val st = boss?.boss ?: return 0
        return (bossBrain.baseBounty(st) * stats.euroMul * difficultyReward).toInt()
    }

    internal fun onBossDefeated(eurosReward: Int, scoreReward: Int, bonusPicks: Int = 0) {
        bossesDefeated++
        eurosEarned += (eurosReward * stats.euroMul * difficultyReward).toInt()
        score += (scoreReward * difficultyReward).toLong()
        // Boss mods (owner, 2026-10-08): more picks, and rolled with extra luck.
        grantPick(2 + level / 20 + bonusPicks)
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
        sinkBarriers()
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
            spawnTimer = max(0.35f, 1.5f - level * 0.03f) / (if (coop) 1f + COOP_EXTRA_THREATS else 1f)
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
    /** Test hook: put the card screen up with exactly these cards. */
    internal fun debugOffer(cards: List<UpgradeOffer>) {
        pendingUpgrades = max(1, pendingUpgrades)
        offer = cards
        phase = Phase.UPGRADE
    }

        fun debugJumpToLevel(target: Int) {
        startLevel(target, null, true)
    }

    /** Bot/test hook: a spot lined up with one of Spectral Firewall's ring gaps. */
    fun firewallGapPoint(scale: Float): Pair<Float, Float>? = boss?.let { bossBrain.ringGapPoint(it, scale) }

    /** Test hook: make the boss start [p] right now. */
    fun debugBossPattern(p: com.cyberoperative.game.data.Pattern) = bossBrain.forcePattern(p)

    /** Test hook: start a specific plan (e.g. a given event or boss). */
    fun debugStartPlan(forced: LevelPlan) {
        startLevel(forced.level, null, true, forced)
    }

    // ======================================================================
    // Co-op networking: host snapshot, guest mirror (see CoopNet.kt)
    // ======================================================================

    /** True on a co-op guest: this engine only shows what the host sends. */
    var mirror = false
        private set
    /** Host: last input seen from the guest. */
    private var lastPickSerial = 0
    private var lastRerollSerial = 0

    /** Host: the guest's latest movement and picks. */
    fun applyInput(index: Int, c: CoopInput) {
        val o = ops.getOrNull(index) ?: return
        if (o.gone) return
        o.remote = true
        if (o.alive && c.level == level && phase != Phase.TRANSITION && slideIn <= 0f) {
            o.netX = c.x; o.netY = c.y; o.netFacing = c.facing; o.netMoving = c.moving
        } else {
            // Still in the previous room on the guest's side: hold the spawn spot.
            o.netX = o.px; o.netY = o.py; o.netMoving = false
        }
        if (c.pickSerial > lastPickSerial) {
            lastPickSerial = c.pickSerial
            chooseUpgradeFor(index, c.pickIndex, Upgrades.all.getOrNull(c.replaceIndex)?.id)
        }
        if (c.rerollSerial > lastRerollSerial) {
            lastRerollSerial = c.rerollSerial
            rerollFor(index)
        }
    }

    private val upgradeIndex: Map<String, Int> by lazy { Upgrades.all.withIndex().associate { it.value.id to it.index } }
    private val enemyIndex: Map<String, Int> by lazy { Enemies.all.withIndex().associate { it.value.id to it.index } }

    /** Host: everything the guest needs to draw this moment of the run. [sounds] = sounds since the last snapshot. */
    fun captureWorld(seq: Int, sounds: List<GameSound>): CoopWorld {
        val w = CoopWorld()
        w.seq = seq
        w.level = level; w.levelSeed = levelSeed; w.previousArenaId = previousArenaId; w.previousEvent = previousEvent
        w.vaultCracked = vaultCracked; w.vaultOpening = vaultOpening
        w.phase = phase; w.phaseTimer = phaseTimer; w.slideIn = slideIn
        w.score = score; w.kills = kills; w.euros = eurosEarned; w.diamonds = diamondsEarned
        w.bosses = bossesDefeated; w.elites = elitesDefeated; w.events = eventsCompleted
        w.levelKills = levelKills; w.levelSpawned = levelSpawned; w.waveIndex = waveIndex
        w.runLevel = runLevel; w.xp = xp; w.runSeconds = runSeconds; w.timedRemaining = timedRemaining
        w.portalOpen = portalOpen
        w.banner = banner; w.bannerSub = bannerSub; w.bannerTimer = bannerTimer
        w.bossPhaseLabel = bossPhaseLabel; w.bossUid = boss?.uid ?: -1
        for (o in ops) {
            val n = NetOp()
            n.x = o.px; n.y = o.py; n.hp = o.hp; n.firewall = o.firewall; n.facing = o.facing
            n.moving = o.moving; n.invuln = o.invuln; n.hurtFlash = o.hurtFlash
            n.rooted = o.rooted; n.encrypted = o.encrypted; n.encryptCharge = o.encryptCharge; n.burning = o.burning
            n.pulled = o.pulled; n.pullX = o.pullX; n.pullY = o.pullY; n.pullStrength = o.pullStrength
            n.chill = o.chill; n.frozen = o.frozen
            n.orbAngle = o.orbAngle; n.bladeAngle = o.bladeAngle; n.targetUid = o.targetUid
            n.beamActive = o.beamActive; n.beamX2 = o.beamX2; n.beamY2 = o.beamY2; n.beamHeat = o.beamHeat; n.beamCooldown = o.beamCooldown
            n.downed = o.downed; n.reviveProgress = o.reviveProgress; n.gone = o.gone
            n.pending = o.pendingUpgrades; n.rerolls = o.rerollsLeft; n.batchTotal = o.rewardBatchTotal; n.batchTaken = o.rewardBatchTaken
            for ((id, l) in o.build.owned()) upgradeIndex[id]?.let { n.owned[it] = l }
            for (c in o.offer) upgradeIndex[c.def.id]?.let { n.offer += it to c.nextLevel }
            w.ops += n
        }
        for (e in enemies.items) {
            if (!e.active) continue
            val n = NetEnemy()
            n.uid = e.uid
            val b = e.boss
            if (b != null) {
                n.bossIndex = com.cyberoperative.game.data.Bosses.roster.indexOf(b.def)
                n.bossCycle = b.cycle; n.bossPhase = b.phaseIndex; n.bossGlitched = b.glitched
            } else {
                n.def = enemyIndex[e.def.id] ?: continue
                n.elite = e.elite?.ordinal ?: -1
            }
            n.x = e.x; n.y = e.y; n.hp = e.hp; n.maxHp = e.maxHp; n.radius = e.radius
            n.state = e.state; n.stateTimer = e.stateTimer; n.hitFlash = e.hitFlash
            w.enemies += n
        }
        var hostile = 0
        var friendly = 0
        for (p in projectiles.items) {
            if (!p.active) continue
            if (p.friendly) { if (friendly++ >= CoopCodec.MAX_FRIENDLY_SHOTS) continue } else if (hostile++ >= CoopCodec.MAX_HOSTILE_SHOTS) continue
            val n = NetShot()
            n.kind = p.kind; n.friendly = p.friendly; n.x = p.x; n.y = p.y; n.vx = p.vx; n.vy = p.vy
            n.radius = p.radius; n.crit = p.crit; n.homing = p.homing > 0f; n.tint = p.tint; n.armTimer = p.armTimer
            n.life = p.life; n.owner = p.owner
            w.shots += n
        }
        for (h in hazards.items) {
            if (!h.active) continue
            val n = NetHazard()
            n.kind = h.kind; n.x = h.x; n.y = h.y; n.x2 = h.x2; n.y2 = h.y2; n.radius = h.radius; n.maxRadius = h.maxRadius
            n.timer = h.timer; n.duration = h.duration; n.windup = h.windup; n.color = h.color
            w.hazards += n
        }
        for (z in zaps.items) {
            if (!z.active) continue
            val n = NetZap()
            n.kind = z.kind; n.x = z.x; n.y = z.y; n.x2 = z.x2; n.y2 = z.y2; n.radius = z.radius
            n.timer = z.timer; n.duration = z.duration; n.landed = z.landed; n.seed = z.seed; n.color = z.color
            w.zaps += n
        }
        for (t in texts.items) {
            if (!t.active || w.texts.size >= CoopCodec.MAX_TEXTS) continue
            val n = NetText()
            n.x = t.x; n.y = t.y; n.text = t.text; n.kind = t.kind; n.life = t.life
            w.texts += n
        }
        for (p in pulses.items) {
            if (!p.active) continue
            val n = NetPulse()
            n.x = p.x; n.y = p.y; n.radius = p.radius; n.maxRadius = p.maxRadius; n.life = p.life; n.maxLife = p.maxLife; n.color = p.color
            w.pulses += n
        }
        w.sounds += sounds.take(16)
        for (b in barriers) w.barriers += floatArrayOf(b.x, b.y, b.half, b.rise, b.life, b.timer, b.style.toFloat())
        w.darkness = darkness; w.lightFlicker = lightFlicker; w.bossVeil = bossVeil; w.bossShield = bossShield
        w.bossRingAngle = bossRingAngle; w.bossRingFilled = bossRingFilled; w.bossRingOut = bossRingOut; w.bossSync = bossSync; w.bossTrail = bossTrail.copyOf(); w.bossIceShell = bossIceShell; w.bossHeadMask = bossHeadMask
        return w
    }

    /** Guest: from now on this engine only mirrors the host's snapshots for operative [localIndex]. */
    fun enterMirror(localIndex: Int) {
        mirror = true
        primary = localIndex.coerceIn(0, ops.size - 1)
        cur = ops[primary]
        for (o in ops) { o.pendingUpgrades = 0; o.offer = emptyList() }
        clearAll()
        boss = null
    }

    private var mirrorSeq = -1
    /** Where each mirrored enemy should be (smoothed toward between snapshots). */
    private val netTargets = HashMap<Int, FloatArray>()

    /** Guest: apply one host snapshot. Older or duplicate snapshots are ignored. */
    fun applyWorld(w: CoopWorld) {
        if (!mirror || w.seq <= mirrorSeq) return
        mirrorSeq = w.seq
        val newRoom = w.level != level || w.levelSeed != levelSeed
        if (newRoom) {
            level = w.level
            levelSeed = w.levelSeed
            previousArenaId = w.previousArenaId
            previousEvent = w.previousEvent
            plan = LevelPlanner.plan(level, Random(levelSeed), previousArenaId, previousEvent, mode)
            vaultCracked = false
            val extra = if (plan.rules.vault) listOf(Arena.vaultObstacle(plan.arena)) else emptyList()
            arena = Arena(plan.arena, extra)
            netTargets.clear()
            for (e in enemies.items) e.active = false
        }
        if (w.vaultCracked && !vaultCracked) {
            vaultCracked = true
            rebuildArena()
        }
        barriers.clear()
        for (n in w.barriers) barriers += Barrier(n[0], n[1], n[2], n[3], n[4], n.getOrElse(6) { 0f }.toInt()).also { it.timer = n[5] }
        darkness = w.darkness; darknessTarget = w.darkness; lightFlicker = w.lightFlicker; bossVeil = w.bossVeil; bossShield = w.bossShield
        bossRingAngle = w.bossRingAngle; bossRingFilled = w.bossRingFilled; bossRingOut = w.bossRingOut; bossSync = w.bossSync; bossTrail = w.bossTrail; bossIceShell = w.bossIceShell; bossHeadMask = w.bossHeadMask
        val solidNow = barriers.count { it.solid }
        if (solidNow != barrierSolidCount) { barrierSolidCount = solidNow; rebuildArena() }
        vaultOpening = w.vaultOpening
        phase = w.phase; phaseTimer = w.phaseTimer; slideIn = w.slideIn
        score = w.score; kills = w.kills; eurosEarned = w.euros; diamondsEarned = w.diamonds
        bossesDefeated = w.bosses; elitesDefeated = w.elites; eventsCompleted = w.events
        levelKills = w.levelKills; levelSpawned = w.levelSpawned; waveIndex = w.waveIndex
        runLevel = w.runLevel; xp = w.xp; runSeconds = w.runSeconds; timedRemaining = w.timedRemaining
        portalOpen = w.portalOpen
        if (w.banner != banner || w.bannerTimer > bannerTimer + 0.2f) { banner = w.banner; bannerSub = w.bannerSub }
        bannerTimer = w.bannerTimer
        bossPhaseLabel = w.bossPhaseLabel

        for ((i, n) in w.ops.withIndex()) {
            val o = ops.getOrNull(i) ?: continue
            val local = i == primary
            // The guest drives its own position (except on a new room, while down, or between rooms);
            // everything else comes from the host.
            val hostPlaces = !local || newRoom || n.downed || n.gone || w.phase == Phase.TRANSITION || w.slideIn > 0f
            if (hostPlaces) { o.px = n.x; o.py = n.y; o.facing = n.facing; o.moving = n.moving }
            o.hp = n.hp; o.firewall = n.firewall; o.invuln = n.invuln; o.hurtFlash = n.hurtFlash
            o.rooted = n.rooted; o.encrypted = n.encrypted; o.encryptCharge = n.encryptCharge; o.burning = n.burning
            o.pulled = n.pulled; o.pullX = n.pullX; o.pullY = n.pullY; o.pullStrength = n.pullStrength
            o.chill = n.chill; o.frozen = n.frozen
            if (!local) { o.orbAngle = n.orbAngle; o.bladeAngle = n.bladeAngle }
            o.targetUid = n.targetUid
            o.beamActive = n.beamActive; o.beamX2 = n.beamX2; o.beamY2 = n.beamY2; o.beamHeat = n.beamHeat; o.beamCooldown = n.beamCooldown
            o.downed = n.downed; o.reviveProgress = n.reviveProgress; o.gone = n.gone
            o.pendingUpgrades = n.pending; o.rerollsLeft = n.rerolls; o.rewardBatchTotal = n.batchTotal; o.rewardBatchTaken = n.batchTaken
            val owned = HashMap<String, Int>()
            for ((k, l) in n.owned) Upgrades.all.getOrNull(k)?.let { owned[it.id] = l }
            if (owned != o.build.owned()) o.build.restore(owned)
            val offer = n.offer.mapNotNull { (k, l) -> Upgrades.all.getOrNull(k)?.let { UpgradeOffer(it, l) } }
            if (offer.map { it.def.id to it.nextLevel } != o.offer.map { it.def.id to it.nextLevel }) o.offer = offer
        }

        // Enemies by uid: keep the ones still alive (smoothly moved), add new, burst the vanished.
        val seen = HashSet<Int>(w.enemies.size * 2)
        var bossRef: Enemy? = null
        for (n in w.enemies) {
            seen += n.uid
            var e = enemies.items.firstOrNull { it.active && it.uid == n.uid }
            if (e == null) {
                e = enemies.obtain() ?: continue
                e.active = true
                e.uid = n.uid
                e.x = n.x; e.y = n.y
                if (n.bossIndex >= 0) {
                    val bd = com.cyberoperative.game.data.Bosses.roster.getOrNull(n.bossIndex) ?: continue
                    e.def = bossBrain.defFor(bd)
                    e.boss = BossState(bd, n.bossCycle).also { it.glitched = n.bossGlitched; it.growled = true }
                    e.elite = null
                } else {
                    e.def = Enemies.all.getOrNull(n.def) ?: continue
                    e.boss = null
                    e.elite = if (n.elite >= 0) EliteModifier.entries.getOrNull(n.elite) else null
                }
            }
            e.boss?.phaseIndex = n.bossPhase.coerceIn(0, (e.boss?.def?.phases?.size ?: 1) - 1)
            e.hp = n.hp; e.maxHp = n.maxHp; e.radius = n.radius
            e.state = n.state; e.stateTimer = n.stateTimer; e.hitFlash = n.hitFlash
            netTargets.getOrPut(n.uid) { FloatArray(2) }.let { it[0] = n.x; it[1] = n.y }
            if (n.uid == w.bossUid) bossRef = e
        }
        for (e in enemies.items) {
            if (!e.active || e.uid in seen) continue
            e.active = false
            netTargets.remove(e.uid)
            // Killed on the host: the same burst the host shows.
            repeat(if (e.isElite) 14 else 8) { addParticle(e.x, e.y, e.def.color, 200f, 0.5f, 3f) }
        }
        boss = bossRef

        for (p in projectiles.items) p.active = false
        for (n in w.shots) {
            val p = projectiles.obtain() ?: break
            p.active = true; p.kind = n.kind; p.friendly = n.friendly; p.x = n.x; p.y = n.y; p.vx = n.vx; p.vy = n.vy
            p.radius = n.radius; p.crit = n.crit; p.homing = if (n.homing) 1f else 0f; p.tint = n.tint; p.armTimer = n.armTimer
            p.life = n.life; p.owner = n.owner; p.returning = false; p.splash = 0f
        }
        for (h in hazards.items) h.active = false
        for (n in w.hazards) {
            val h = hazards.obtain() ?: break
            h.active = true; h.kind = n.kind; h.x = n.x; h.y = n.y; h.x2 = n.x2; h.y2 = n.y2; h.radius = n.radius; h.maxRadius = n.maxRadius
            h.timer = n.timer; h.duration = n.duration; h.windup = n.windup; h.color = n.color; h.hitMask = 0; h.ownerUid = -1
        }
        for (z in zaps.items) z.active = false
        for (n in w.zaps) {
            val z = zaps.obtain() ?: break
            z.active = true; z.kind = n.kind; z.x = n.x; z.y = n.y; z.x2 = n.x2; z.y2 = n.y2; z.radius = n.radius
            z.timer = n.timer; z.duration = n.duration; z.landed = n.landed; z.seed = n.seed; z.color = n.color
        }
        for (t in texts.items) t.active = false
        for (n in w.texts) {
            val t = texts.obtain() ?: break
            t.active = true; t.x = n.x; t.y = n.y; t.text = n.text; t.kind = n.kind; t.life = n.life
        }
        for (p in pulses.items) p.active = false
        for (n in w.pulses) {
            val p = pulses.obtain() ?: break
            p.active = true; p.x = n.x; p.y = n.y; p.radius = n.radius; p.maxRadius = n.maxRadius; p.life = n.life; p.maxLife = n.maxLife; p.color = n.color
        }
        sounds += w.sounds
        frame++
    }

    /**
     * Guest frame: move its own operative from the stick (no waiting on the
     * network), glide enemies toward their last reported spot, fly shots on
     * their velocity, and age the effects.
     */
    fun mirrorTick(delta: Float) {
        if (!mirror) return
        val dt = delta.coerceIn(0f, MAX_FRAME)
        cur = ops[primary]
        if (bannerTimer > 0f) bannerTimer -= dt
        updateEffects(dt)
        if (slideIn > 0f) slideIn = max(0f, slideIn - dt)
        val me = ops[primary]
        if (me.alive && phase != Phase.UPGRADE && phase != Phase.DEAD && phase != Phase.TRANSITION && slideIn <= 0f) {
            val mag = sqrt(me.inputX * me.inputX + me.inputY * me.inputY)
            if (me.rooted > 0f) me.rooted = max(0f, me.rooted - dt)
            if (me.pulled > 0f) {
                // Convergence Flash drags the guest too (host sends the pull; the guest moves itself).
                me.pulled = max(0f, me.pulled - dt)
                val dx = me.pullX - me.px; val dy = me.pullY - me.py
                val d = sqrt(dx * dx + dy * dy)
                if (d > 60f) {
                    arena.pushOut(me.px + dx / d * me.pullStrength * dt, me.py + dy / d * me.pullStrength * dt, playerRadius)
                    me.px = arena.out[0]; me.py = arena.out[1]
                }
            }
            if (me.frozen > 0f) me.frozen = max(0f, me.frozen - dt)
            me.moving = mag > MOVE_DEADZONE && me.rooted <= 0f && me.frozen <= 0f
            if (me.moving) {
                val speed = stats.moveSpeed * min(1f, mag) * chillSlow(me)
                arena.pushOut(me.px + me.inputX / mag * speed * dt, me.py + me.inputY / mag * speed * dt, playerRadius)
                me.px = arena.out[0]
                me.py = arena.out[1]
                me.facing = atan2(me.inputY, me.inputX)
            }
        } else me.moving = false
        me.orbAngle = (me.orbAngle + stats.orbAngularSpeed * dt) % MathUtil.TWO_PI
        me.bladeAngle = (me.bladeAngle - 3.4f * dt) % MathUtil.TWO_PI
        val k = min(1f, dt * 14f)
        for (e in enemies.items) {
            if (!e.active) continue
            val t = netTargets[e.uid] ?: continue
            e.x += (t[0] - e.x) * k
            e.y += (t[1] - e.y) * k
            if (e.hitFlash > 0f) e.hitFlash -= dt
        }
        for (p in projectiles.items) {
            if (!p.active) continue
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.life -= dt
            if (p.life <= 0f) p.active = false
        }
        for (h in hazards.items) {
            if (!h.active) continue
            h.timer += dt
            if (h.kind == HazardKind.SHOCK_RING && h.duration > 0f) h.radius = 10f + (h.maxRadius - 10f) * (h.timer / h.duration).coerceAtMost(1f)
        }
        for (z in zaps.items) if (z.active) z.timer += dt
    }

    /** Renderer: run [block] as if operative [index] were the current one (draws a partner with the same code). */
    fun <T> viewAs(index: Int, block: () -> T): T {
        val prev = cur
        cur = ops.getOrNull(index) ?: return block()
        try {
            return block()
        } finally {
            cur = prev
        }
    }

    /** Guest: this device's movement, for [CoopInput]. */
    val localX: Float get() = ops[primary].px
    val localY: Float get() = ops[primary].py
    val localFacing: Float get() = ops[primary].facing
    val localMoving: Boolean get() = ops[primary].moving
    val localLevel: Int get() = level

    /**
     * Guest: the host left. This mirror becomes a real solo run from the last
     * snapshot: the host's operative leaves the field and the threats pick up
     * from where they were.
     */
    fun promoteToSolo() {
        if (!mirror) return
        mirror = false
        for (o in ops) if (o.index != primary) { o.gone = true; o.downed = false; o.pendingUpgrades = 0; o.offer = emptyList() }
        val me = ops[primary]
        me.remote = false
        if (me.downed || me.hp <= 0f) { me.downed = false; me.hp = me.build.stats.maxHp * 0.5f }
        cur = me
        for (e in enemies.items) {
            if (!e.active) continue
            val d = e.def
            e.vx = 0f; e.vy = 0f
            if (e.boss == null) {
                e.speed = d.baseSpeed * Scaling.enemySpeed(level)
                e.damageMul = Scaling.enemyDamage(level) * config.difficulty.enemyDamage * opDamageNow * adaptiveDamage
            } else {
                e.speed = (e.boss!!.def.speed) * (1f + 0.05f * e.boss!!.cycle)
                e.damageMul = Scaling.enemyDamage(level) * (1f + 0.1f * e.boss!!.cycle) * config.difficulty.enemyDamage * opDamageNow * adaptiveDamage
                e.boss!!.anchorX = e.x; e.boss!!.anchorY = e.y
            }
            e.attackRateMul = 1f; e.damageTakenMul = 1f; e.rewardMul = 1f
            if (e.state != AiState.SPAWNING) e.state = AiState.MOVE
            e.attackTimer = d.attackCooldown * (0.6f + rng.nextFloat() * 0.6f)
            e.strafeDir = if (rng.nextBoolean()) 1f else -1f
            e.navValid = false; e.lastX = e.x; e.lastY = e.y
        }
        for (p in projectiles.items) if (p.active && p.friendly) p.active = false
        if (phase == Phase.UPGRADE && me.pendingUpgrades > 0 && me.offer.isEmpty()) me.offer = build.rollOffer(rng, luck = currentLuck())
        if (phase == Phase.UPGRADE && me.pendingUpgrades <= 0) finishUpgrades()
        me.invuln = 2f
        showBanner("HOST DISCONNECTED", "Continuing solo", 2f)
    }

    companion object {
        /** Co-op: how close the partner must stand, and for how long, to revive. */
        const val REVIVE_RADIUS = 70f
        const val REVIVE_SECONDS = 3f
        const val STEP = 1f / 120f
        const val MAX_FRAME = 0.1f
        /** How far a sweeping laser reaches when nothing stops it. */
        const val SWEEP_LENGTH = 1500f
        /** ON FIRE: how long it lasts and how often it ticks. */
        const val BURN_SECONDS = 2.5f
        /** CHILL: max stacks (= FROZEN), decay per second, freeze length. */
        const val CHILL_MAX = 5f
        const val CHILL_DECAY = 0.8f
        const val FREEZE_SECONDS = 1.1f
        /** Hostile mines: how close trips one, and its fuse once tripped. */
        const val MINE_TRIGGER = 70f
        const val MINE_FUSE = 0.45f
        /** Half the arc a Dash Slash crescent covers (radians). */
        const val SLASH_HALF_ARC = 1.15f
        const val BURN_TICK = 0.5f
        /** Half-thickness of a fire wall ring. */
        const val FIRE_WALL_THICKNESS = 16f
        /** Spectral Firewall's orbiting ring: plate slots and radius (× boss radius). */
        const val FIREWALL_SLOTS = 9
        const val FIREWALL_RING_SCALE = 1.6f
        /** Seconds standing in a key zone to unlock it. */
        const val KEY_CAPTURE_SECONDS = 1.3f
        /** How long ENCRYPTED lasts, and how much moving fills its burst meter. */
        const val ENCRYPT_SECONDS = 3.5f
        const val ENCRYPT_MOVE_SECONDS = 1.1f
        /** How long burst crystal spikes stay on screen (no damage after the burst). */
        const val SPIKE_LINGER = 0.7f
        /** Grid size for tracking where operatives stand (Infected Zone). */
        const val DWELL_CELL = 100f
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
