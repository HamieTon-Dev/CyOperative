package com.cyberoperative.game.engine

import com.cyberoperative.game.data.OpProgress

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
    val weaponSlots: Int = Upgrades.MAX_WEAPONS,
    /** The partner's class (its PC spells). */
    val classId: String = "operative"
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
    /** Hidden [PowerGovernor] (always on in play; mechanics tests switch it off). */
    val powerGovernor: Boolean = true,
    val mode: GameMode = GameMode.ENDLESS,
    val difficulty: Difficulty = Difficulty.MEDIUM,
    /** Account OP level (owner, 2026-10-08: veterans face tougher threats and bosses). */
    val opLevel: Int = 1,
    /** How much permanent-upgrade mastery multiplied damage output / staying power (1 = none). */
    val masteryDpsRatio: Float = 1f,
    val masterySurvivalRatio: Float = 1f,
    /** € the player already has when the run starts (owner, 0.32.0: € carries over between runs). */
    val startEuros: Int = 0,
    /** Co-op partner (owner, 2026-10-08), or null for a solo run. */
    val ally: AllyConfig? = null,
    /** Weapons this operative can carry (7 + WEAPON SLOTS upgrade). */
    val weaponSlots: Int = Upgrades.MAX_WEAPONS,
    /** PC consumables (HP charges on Q, Packet Bombs on E, floor drops). On for the landscape PC build. */
    val consumables: Boolean = com.cyberoperative.game.data.ArenaShape.pcRules,
    /** This player's class (its PC spells). */
    val classId: String = "operative",
    /** This player's own OP level for gear drops (co-op runs scale threats to the higher one); 0 = [opLevel]. */
    val gearLevel: Int = 0
) {
    val coop: Boolean get() = ally != null
    /** Co-op threat HP ×1.4 (bosses ×1.7) and ~35% more threats. */
    val coopHpMul: Float get() = if (coop) COOP_HP_MUL else 1f
    val coopBossHpMul: Float get() = if (coop) COOP_BOSS_HP_MUL else 1f

    /**
     * Threat HP from OP level (owner, 2026-10-13: enemies scale to the player, much harder than
     * before): +6% per level (damage +4.5%) up to OP 101, then it keeps
     * climbing on a log curve (×7 at OP 1,000, ×11 at OP 9,999), times the
     * square root of the damage mastery added: mastery always pays off, but only
     * half of it (in multiplier terms) turns into easier fights.
     */
    val opHpMul: Float get() = opCurve(0.06f, 0.6f) * kotlin.math.sqrt(masteryDpsRatio)
    /** Threat damage from OP level: +1.2% per level up to ×2.2, then log (×4.2 at OP 1,000, ×6.2 at 9,999); × √ survival mastery. */
    val opDamageMul: Float get() = opCurve(0.045f, 0.4f) * kotlin.math.sqrt(masterySurvivalRatio)

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

/** One piece of equipment on the shop counter (owner, 0.32.0: PURPLE and up). */
data class GearShopItem(val item: com.cyberoperative.game.data.GearItem, val price: Int, val sold: Boolean)

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
    /** CAMPAIGN (the world map) or ENDLESS. A finished campaign can carry on as ENDLESS. */
    var mode: GameMode = config.mode
        private set
    val campaign: Boolean get() = mode == GameMode.CAMPAIGN
    /** The operatives on the field: [0] is the host / solo player, [1] the co-op partner. */
    private val ops = ArrayList<Operative>(2).apply {
        add(Operative(0, config.baseStats).also {
            it.rerollsLeft = config.rerolls; it.build.qualityBonus = config.upgradeQuality
            it.build.weaponSlots = config.difficulty.weaponSlots(config.weaponSlots)
        })
        config.ally?.let { a ->
            add(Operative(1, a.baseStats).also { it.rerollsLeft = a.rerolls; it.build.qualityBonus = a.upgradeQuality; it.build.weaponSlots = config.difficulty.weaponSlots(a.weaponSlots) })
        }
        // Campaign picks come from clearing levels, not data, so data-only cards are pointless.
        // Power-ups come from rooms, not data, so the data cards are pointless.
        for (o in this) o.build.excluded = setOf(Upgrades.DATA_DUMP.id, Upgrades.DATA_COMPRESSION.id)
        if (config.consumables) for (o in this) o.hpCharges = START_HP_CHARGES
        this[0].classId = config.classId
        config.ally?.let { a -> getOrNull(1)?.classId = a.classId }
        this[0].gearLevel = if (config.gearLevel > 0) config.gearLevel else config.opLevel
        config.ally?.let { a -> getOrNull(1)?.gearLevel = a.opLevel }
    }
    val operatives: List<Operative> get() = ops
    /** Floor drops; only on builds with consumables. Own random so seeds and balance stay as they were. */
    val loot = ArrayList<Loot>()
    /** Boss loot sprays out of the dying boss one piece every [SPEW_INTERVAL] (owner, 2026-10-11). */
    private val spewQueue = ArrayDeque<Loot>()
    private var spewing = false
    private var spewTimer = 0f
    private var spewX = 0f
    private var spewY = 0f
    private val lootRng = Random(config.seed xor 0x10075EEDL)
    // --- Campaign world map (owner, 2026-10-11) ---------------------------------------
    /** Server world (1..15) and room on its map (0..9, 10 = the boss room). */
    var world = 1
        private set
    var room = 0
        private set
    var worldMap: WorldMap = CampaignMap.generate(config.seed, 1)
        private set
    /** Rooms of this world already cleared (they stay empty, doors open) and ones visited. */
    val clearedRooms = HashSet<Int>()
    val visitedRooms = HashSet<Int>()
    /** World 15's boss is down: the UI asks whether to carry on in Endless. */
    var campaignComplete = false
        private set
    /** This run finished the whole campaign (records). */
    var campaignFinished = false
        private set
    /** Endless after the campaign (or chosen from the menu): the HUD hides where you are. */
    val worldUnknown: Boolean get() = !campaign
    /** True when the current room was already cleared when entered (no threats, doors open). */
    var revisit = false
        private set
    private var pendingRoom = -1
    private var pendingEntry = 3
    /** The room the shop's exit leads back to, and where its doorway was. */
    private var shopReturnRoom = -1
    private var shopReturnRight = false
    private var shopReturnY = 0f
    /** Extra exit doors this room (PC); the top-centre gate is always there too. */
    val exitDoors = ArrayList<ExitDoor>(3)
    private val doorRng = Random(config.seed xor 0xD0012EL)
    /** PC rules: power-ups drop as upgrade chips instead of the card screen. */
    val chipRules: Boolean get() = config.consumables
    /** Chips this room still plans to drop from kills, and chips dropped not yet counted. */
    private var chipBudget = 0
    private var chipsDropped = 0
    private var lastKillX = 0f
    private var lastKillY = 0f
    /** A weapon chip waiting on the swap prompt (slots full, room clear). */
    var swapChip: Loot? = null
        private set
    /** The last upgrade picked up (name, rarity) for the HUD line, and a serial so the UI can animate it. */
    var pickupName = ""
        private set
    var pickupRarity = com.cyberoperative.game.data.Rarity.COMMON
        private set
    var pickupSerial = 0
        private set
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
    /** Hidden [PowerGovernor] lifts for regular threats and elites (never shown to the player). */
    var adaptiveHp = 1f
        private set
    var adaptiveDamage = 1f
        private set
    /** The current boss's governor damage lift (kept for co-op handover). */
    private var bossDamageLift = 1f

    /** Weapons fighting for the operative besides the primary gun. */
    fun weaponCount(): Int {
        val s = stats
        return s.weapons.size + listOf(s.coneLevel, s.lanceLevel, s.beamLevel, s.empLevel, s.mineLevel, s.missileLevel,
            s.arcLevel, s.railLevel, s.strikeLevel, s.bladeCount).count { it > 0 } + (s.orbCount - 1).coerceAtLeast(0) / 2
    }

    /** Potential damage per second of every operative still in the run. */
    internal fun teamPotentialDps(boss: Boolean = false): Float = if (!config.powerGovernor) 0f else ops.filter { !it.gone }.sumOf {
        (if (boss) PowerGovernor.potentialBossDps(it.build.stats) else PowerGovernor.potentialDps(it.build.stats)).toDouble()
    }.toFloat()

    private fun bestSurvival(): Float = if (!config.powerGovernor) 0f else ops.filter { !it.gone }.maxOfOrNull { PowerGovernor.survival(it.build.stats) } ?: 0f

    /** Boss damage from the governor: its HP lift, the team's staying power, and the late-level climb. */
    internal fun bossGovernorDamage(hpLift: Float): Float {
        val d = config.difficulty
        val curve = pressureDamage() * Scaling.enemyDamage(level) * d.enemyDamage * opDamageNow
        bossDamageLift = maxOf(adaptiveDamage, PowerGovernor.damageLift(d, hpLift, bestSurvival(), curve)) * PowerGovernor.lateBossDamage(d, level)
        return bossDamageLift
    }

    /**
     * Hidden power governor (owner, 2026-10-11; see [PowerGovernor]): regular threats and
     * elites keep pace with the team's potential output. Off on Easy.
     */
    private fun updateAdaptive() {
        val d = config.difficulty
        val hpCurve = Scaling.enemyHp(level) * pressureHp() * d.enemyHp * opHpNow * config.coopHpMul
        adaptiveHp = PowerGovernor.enemyHpLift(d, teamPotentialDps(), hpCurve)
        val dmgCurve = Scaling.enemyDamage(level) * pressureDamage() * d.enemyDamage * opDamageNow
        adaptiveDamage = PowerGovernor.damageLift(d, adaptiveHp, bestSurvival(), dmgCurve)
    }

    /**
     * OP-level (and mastery) threat bonus (owner, 2026-10-09: "it should scale
     * to OP level"): the one difficulty extra on top of the base curve, from
     * level 1, halved on EASY.
     */
    val opHpNow: Float get() = 1f + (config.opHpMul - 1f) * config.difficulty.extraScaling

    /** Post-level-10 pressure for this difficulty (owner, 2026-10-11): extra HP, damage and threat count. */
    internal fun pressureHp(lvl: Int = level): Float = 1f + (Scaling.hpPressure(lvl) - 1f) * config.difficulty.hpPressureShare
    internal fun pressureDamage(lvl: Int = level): Float = 1f + (Scaling.damagePressure(lvl) - 1f) * config.difficulty.pressure
    internal fun pressureCount(lvl: Int = level): Float = 1f + (Scaling.countPressure(lvl) - 1f) * config.difficulty.pressure
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
    /** The current operative's own shop stock. */
    val shopItems: List<ShopItem> get() = cur.shopItems
    /** Seed of the shop room (so a co-op guest builds the same room). */
    var shopSeed = 0L
        private set
    /** Co-op shop: how many players stand in the exit gate, of how many are in the run. */
    val shopReadyCount: Int get() = ops.count { !it.gone && it.shopReady }
    val shopReadyTotal: Int get() = ops.count { !it.gone }
    val meShopReady: Boolean get() = ops[primary].shopReady
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
    /** € on hand: what the player brought into the run plus what it has earned (minus the shop). */
    val wallet: Int get() = config.startEuros + eurosEarned

    /** € from outside the fight (a backpack sale mid-run, owner 0.35.0): into the wallet, banked as usual. */
    fun grantEuros(n: Int) { if (n > 0) eurosEarned += n }
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
    var levelSpawned = 0
        private set
    val levelThreats: Int get() = max(plan.enemyCount, levelSpawned)
    /** Seconds left of the slide-in of a new room (gameplay waits for it). */
    var slideIn = 0f
    /**
     * Which wall the room was left through (0 top / north, 1 left / west, 2 right / east,
     * 3 bottom / south): the room-change slide follows the direction of travel (owner, 2026-10-11).
     */
    var slideSide = 0
    private var skipPromptSide = 0
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
    /** Wave threats waiting for room under the on-screen cap. */
    private val waveCarry = ArrayList<SpawnSpec>()
    /** Seconds the last few threats of a cleared wave plan have gone without a kill. */
    private var stragglerTimer = 0f
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
    /** GOOD GAME siege cannon charge, 0..1 (renderer; synced to the co-op guest). */
    var bossCharge = 0f

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

    /**
     * Victory sequence (owner, 2026-10-10): set when a boss falls. The body shatters in
     * the arena, then a THREAT NEUTRALIZED card shows the fight time and bounty. The
     * boss room only clears once it has played ([VICTORY_SECONDS], skippable).
     */
    class BossVictory(
        val def: com.cyberoperative.game.data.BossDef, val x: Float, val y: Float, val phase: Int,
        val seconds: Float, val bounty: Int, val serial: Int
    ) { var t = 0f }

    var victory: BossVictory? = null
        internal set
    private var victorySerial = 0

    internal fun startVictory(def: com.cyberoperative.game.data.BossDef, x: Float, y: Float, phase: Int, seconds: Float, bounty: Int) {
        victory = BossVictory(def, x, y, phase, seconds, bounty, ++victorySerial)
    }

    /** Tap to skip the victory card. */
    fun skipVictory() {
        victory?.let { if (it.t < VICTORY_SECONDS) it.t = VICTORY_SECONDS }
    }

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

    /** Like [forEachOperativeHit], for anyone within [radius] of the segment (x0, y0)–(x1, y1). */
    internal inline fun forEachOperativeOnSegment(x0: Float, y0: Float, x1: Float, y1: Float, radius: Float, mask: Int, hit: (Int) -> Unit) {
        for (o in ops) {
            if (!o.alive) continue
            val bit = 1 shl o.index
            if (mask and bit != 0) continue
            if (distToSegment(o.px, o.py, x0, y0, x1, y1) < radius) {
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
        }
        if (restore != null) applySnapshot(restore)
        else if (campaign) enterRoom(0, 3)
        else startLevel(level, null, false)
        if (restore == null) {
            // Starting Weapon Power: free upgrades before the first fight. With upgrade chips they
            // drop in a ring around the operative instead of opening a card screen.
            if (chipRules) {
                // Each operative's own Starting Weapon Power, as chips only they can take.
                for (o in ops) {
                    val n = if (o.index == 0) config.startingUpgrades else config.ally?.startingUpgrades ?: 0
                    for (i in 0 until n) {
                        val a = -MathUtil.PI / 2f + (i - (n - 1) / 2f) * 0.6f
                        dropChip(o.px + cos(a) * 110f, o.py + sin(a) * 110f, only = o)
                    }
                }
            } else {
                ops[0].pendingUpgrades = config.startingUpgrades
                if (ops.size > 1) ops[1].pendingUpgrades = config.ally?.startingUpgrades ?: 0
                if (ops.any { it.pendingUpgrades > 0 }) openUpgrades(resumeCombat = true)
            }
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

    /**
     * Points the rules at the living operative closest to (x, y): who a threat chases and shoots at.
     * A cloaked PHANTOM is passed over while the partner is still visible.
     */
    internal fun focusNearest(x: Float, y: Float) {
        if (ops.size == 1) return
        var best: Operative? = null
        var bd = Float.MAX_VALUE
        for (pass in 0..1) {
            for (o in ops) {
                if (!o.alive || (pass == 0 && o.cloaked)) continue
                val d = MathUtil.dist2(x, y, o.px, o.py)
                if (d < bd) { bd = d; best = o }
            }
            if (best != null) break
        }
        if (best != null) cur = best
    }

    // ======================================================================
    // PHANTOM cloak (owner, 2026-10-11): "Every 10 seconds goes invisible (enemies cannot see
    // except the Nullshade specter)"
    // ======================================================================

    /** True for the focused operative while it is cloaked, unless [e] is the Nullshade Specter. */
    internal fun hiddenFrom(e: Enemy): Boolean = cur.cloaked && e.boss?.def?.id != NULLSHADE_ID

    /** Where threats that lost the focused operative go looking. */
    internal val searchX: Float get() = cur.cloakX
    internal val searchY: Float get() = cur.cloakY

    /** Is operative [index] cloaked right now (renderer, HUD). */
    fun isCloaked(index: Int = primary): Boolean = ops.getOrNull(index)?.cloaked == true

    /** Seconds until the focused operative's next cloak, or the cloak time left (HUD). */
    fun cloakLeft(index: Int = primary): Float {
        val o = ops.getOrNull(index) ?: return 0f
        val t = o.cloakTime % CLOAK_CYCLE
        return if (o.cloaked) CLOAK_CYCLE - t else CLOAK_CYCLE - CLOAK_SECONDS - t
    }

    private fun updateCloak(dt: Float) {
        val o = cur
        if (!stats.cloak || phase != Phase.COMBAT) {
            o.cloaked = false
            return
        }
        o.cloakTime += dt
        val now = o.cloakTime % CLOAK_CYCLE >= CLOAK_CYCLE - CLOAK_SECONDS || o.veil > 0f
        if (now && !o.cloaked) {
            o.cloakX = px; o.cloakY = py
            addText(px, py - 40f, "CLOAKED", TextKind.INFO)
            addPulse(px, py, 90f, 0.4f, 0xFF8FA7FF)
        } else if (!now && o.cloaked) {
            addPulse(px, py, 70f, 0.3f, 0xFF8FA7FF)
        }
        o.cloaked = now
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
        o.invuln = 2f
        addPulse(o.px, o.py, 200f, 0.6f, 0xFF00FF9C)
        addText(o.px, o.py - 40f, "REVIVED", TextKind.HEAL)
        sound(GameSound.REVIVE)
    }

    /** Every operative still in the run gets a level-up pick. */
    private var chipSlot = 0

    /**
     * Every cleared level earns a power-up, and deeper levels roll for bonus rewards
     * (owner, 2026-10-08: "higher levels give more"). Balance pass (2026-10-11, "maybe we are
     * upgrading too often?"): the bonus rolls are smaller.
     */
    private fun clearPicks() {
        grantPick()
        if (rng.nextFloat() < min(BONUS_PICK_CAP, (level - 1) * 0.01f)) grantPick()
        if (level >= 25 && rng.nextFloat() < min(DEEP_PICK_CAP, (level - 24) * 0.005f)) grantPick()
    }

    private fun grantPick(n: Int = 1) {
        if (chipRules) {
            // Upgrade chips (owner, 2026-10-11): picks become chips on the floor, no card screen.
            // Chips already dropped from kills this room count toward it.
            val credit = min(n, chipsDropped)
            chipsDropped -= credit
            repeat(n - credit) {
                // A rolling slot so back-to-back grants don't land in one pile.
                val k = chipSlot++ % 9
                dropChip(lastKillX + (k % 3 - 1) * 46f, lastKillY + (k / 3) * 46f)
            }
            return
        }
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

    /** PC mouse aim: while [active], the primary gun fires at world point ([x], [y]), moving or not. */
    fun setAim(active: Boolean, x: Float, y: Float) {
        val o = ops[primary]
        o.aiming = active
        o.aimX = x
        o.aimY = y
    }

    fun update(delta: Float) {
        var remaining = fx.tick(delta.coerceIn(0f, MAX_FRAME))
        while (remaining > 0f) {
            val dt = min(STEP, remaining)
            netClock += dt
            step(dt)
            remaining -= dt
        }
        frame++
    }

    private fun step(dt: Float) {
        if (bannerTimer > 0f) bannerTimer -= dt
        victory?.let { it.t += dt }
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
                    else if (campaign) campaignAdvance()
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

        forEachAlive { updatePlayer(dt); updateOrbit(dt); updateCloak(dt) }
        if (config.consumables) updateLoot(dt)
        if (phase == Phase.COMBAT) {
            updateSpawning(dt)
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
            Phase.COMBAT -> checkCleared()
            Phase.CLEARED -> if (phaseTimer >= CLEAR_BEAT) afterClear()
            Phase.PORTAL -> {
                forEachAlive { portalCheck() }
                // Walking away from the world server cancels that player's "ready".
                for (o in ops) if (o.worldReady && !nearWorldServer(o)) o.worldReady = false
            }
            else -> {}
        }
    }

    /** The current operative walking into an exit gate (top centre or an extra door) or the shop's side gate. */
    private fun portalCheck() {
        if (phase != Phase.PORTAL) return
        val mainGate = !campaign || inShop
        val door = if (mainGate && kotlin.math.abs(px - arena.portalX) < GATE_HALF_WIDTH && py < arena.portalY + PORTAL_RADIUS) MAIN_DOOR
        else if (campaign && inShop) null
        else exitDoors.firstOrNull { inDoor(it) }
        if (coop && inShop) {
            // Co-op shop (owner, 2026-10-11): both leave together. Standing in the gate marks you
            // ready; the way out opens once every player still in the run is standing in it.
            cur.shopReady = door != null
            if (door == null || ops.any { !it.gone && !it.shopReady }) return
        }
        if (door != null) {
                if (shopGateOpen && !coop) {
                    // A shop is on offer: ask before letting them skip it, and step them back off the gate.
                    if (!skipShopPrompt) {
                        skipShopPrompt = true
                        skipPromptSide = door.side
                        sound(GameSound.UI_CLICK)
                    }
                    when (door.side) {
                        1 -> px = 30f + playerRadius + 46f
                        2 -> px = arena.width - 30f - playerRadius - 46f
                        3 -> py = arena.height - BOTTOM_DOOR_INSET - PORTAL_RADIUS - 46f
                        else -> py = arena.portalY + PORTAL_RADIUS + 46f
                    }
                } else {
                    phase = Phase.TRANSITION
                    phaseTimer = 0f
                    goingToShop = false
                    slideSide = if (door.target == NEXT_WORLD) 0 else door.side
                    // Campaign: where this door leads, and you walk in on the matching side.
                    pendingRoom = door.target
                    pendingEntry = if (door.target == NEXT_WORLD) 3 else when (door.side) { 0 -> 3; 3 -> 0; 1 -> 2; else -> 1 }
                }
            } else if (shopGateOpen && kotlin.math.abs(px - shopGateX) < playerRadius + 14f && kotlin.math.abs(py - shopGateY) < SHOP_GATE_HALF) {
                // Through the side gate into the upgrade shop.
                phase = Phase.TRANSITION
                phaseTimer = 0f
                goingToShop = true
                slideSide = if (shopGateRight) 2 else 1
            }
    }

    // ======================================================================
    // Level lifecycle
    // ======================================================================

    /**
     * Room shape for the NEXT room (wide landscape or tall portrait), or null to keep the run's.
     * Android solo runs follow the screen setting (0.30.3, owner: a run started in landscape kept
     * wide rooms, the boss room included, after switching to portrait, too small to read).
     */
    var nextRoomWide: Boolean? = null

    private fun applyRoomShape() { nextRoomWide?.let { com.cyberoperative.game.data.ArenaShape.wide = it } }

    private fun startLevel(newLevel: Int, previousArena: String?, previousEvent: Boolean, forced: LevelPlan? = null) {
        applyRoomShape()
        level = newLevel
        fx.clear()
        if (!mirror) sweepLoot() else loot.clear()
        exitDoors.clear()
        swapChip = null
        chipsDropped = 0
        chipBudget = 0
        darkness = 0f; darknessTarget = 0f; lightFlicker = 0f; bossVeil = 0f; bossShield = -1f; bossSync = 0f; bossTrail = FloatArray(0); bossCharge = 0f
        for (o in ops) { o.cloakTime = 0f; o.cloaked = false }
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
        victory = null
        shopGateOpen = false
        goingToShop = false
        for (o in ops) { o.shopItems = emptyList(); o.shopGear = emptyList(); o.shopReady = false; o.worldReady = false }
        worldServerOn = false
        previousArenaId = previousArena
        this.previousEvent = previousEvent
        levelSeed = rng.nextLong()
        plan = forced ?: LevelPlanner.plan(level, Random(levelSeed), previousArena, previousEvent, config.seed, pressureCount(level), inferno = config.difficulty.inferno > 0)
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
            }
        }
        phase = Phase.COMBAT
        phaseTimer = 0f
        portalOpen = false
        waveIndex = 0
        waveCarry.clear()
        stragglerTimer = 0f
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
            LevelKind.NORMAL -> showBanner(
                "LEVEL $level",
                // A new environment theme every 3 levels gets announced.
                if (com.cyberoperative.game.data.Environments.isNewTheme(level))
                    "${plan.enemyCount} THREATS · ENTERING ${com.cyberoperative.game.data.Environments.forLevel(level, config.seed).name}"
                else "${plan.enemyCount} THREATS · ${plan.arena.name}",
                1.6f
            )
        }
        // Upgrade chips: a normal room drops the pick its clear would have given from its kills.
        if (chipRules && plan.kind != LevelKind.BOSS && plan.kind != LevelKind.SHOP) {
            chipBudget = 1 + (if (plan.kind == LevelKind.EVENT) 1 else 0)
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
            // A swarm may fill the room past the normal cap; Normal/Hard add their post-10 threat count.
            if (spawnTimer <= 0f && aliveCount() < Scaling.MAX_ALIVE_SWARM) {
                spawnTimer = max(0.35f * rules.spawnInterval, rules.spawnInterval * (1f - level * 0.006f)) /
                    pressureCount() / (if (coop) 1f + COOP_EXTRA_THREATS else 1f)
                val pool = Enemies.pool(level)
                spawnEnemy(LevelPlanner.weightedPick(pool, rng), null, telegraph = true)
            }
            return
        }
        val cap = if (rules.swarm) Scaling.MAX_ALIVE_SWARM else Scaling.MAX_ALIVE
        // Threats a full room could not take yet come in as space frees up (they used to be dropped).
        while (waveCarry.isNotEmpty() && aliveCount() < cap) {
            val spec = waveCarry.removeAt(0)
            spawnEnemy(spec.def, spec.elite, telegraph = true)
        }
        if (waveIndex >= plan.waves.size) {
            // The last 1–3 threats wedged somewhere unreachable used to hold the room forever:
            // after STRAGGLER_SECONDS without a kill the network purges them.
            val left = aliveCount()
            if (waveCarry.isEmpty() && plan.kind != LevelKind.BOSS && left in 1..3) {
                stragglerTimer += dt
                if (stragglerTimer >= STRAGGLER_SECONDS) {
                    stragglerTimer = 0f
                    purgeHostiles()
                    addText(px, py - 40f, "STRAGGLERS PURGED", TextKind.INFO)
                }
            } else stragglerTimer = 0f
            return
        }
        waveTimer += dt
        val alive = aliveCount()
        val first = waveIndex == 0
        // A flood's next wave rolls in while the last one's stragglers are still up.
        val lowWater = if (rules.swarm) 10 else 2
        if (first || (alive <= lowWater && waveCarry.isEmpty()) || waveTimer > WAVE_TIMEOUT) {
            if (!first && alive > 0 && waveTimer < WAVE_MIN_GAP) return
            for (spec in plan.waves[waveIndex]) {
                if (aliveCount() >= cap) { waveCarry += spec; continue }
                spawnEnemy(spec.def, spec.elite, telegraph = true)
                // Co-op: about a third more threats per wave.
                if (coop && aliveCount() < cap && rng.nextFloat() < COOP_EXTRA_THREATS) spawnEnemy(spec.def, spec.elite, telegraph = true)
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
        else plan.kind != LevelKind.BOSS && waveIndex >= plan.waves.size && waveCarry.isEmpty() && aliveCount() == 0
        if (plan.kind == LevelKind.BOSS && boss == null && phaseTimer > 1f && aliveCount() == 0 && (victory?.let { it.t >= VICTORY_SECONDS } ?: true)) {
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
        // A boss room's clear picks were already sprayed out with the boss's loot.
        if (plan.kind != LevelKind.BOSS) clearPicks()
        addText(px, py - 40f, "+$euros €", TextKind.INFO)
        addPulse(px, py, 420f, 0.7f, 0xFF00FF9C)
        // OP XP for clearing the room.
        if (!mirror) opXpEarned += OpProgress.roomXp(monsterLevel, stats.xpMul)
        showBanner(if (plan.kind == LevelKind.EVENT) "EVENT COMPLETE" else "THREATS ELIMINATED", "", 1.2f)
        sound(GameSound.LEVEL_COMPLETE)
        sound(GameSound.CURRENCY)
    }

    private fun afterClear() {
        if (ops.any { it.pendingUpgrades > 0 }) {
            openUpgrades()
        } else {
            // No card screen (upgrade chips): the shop still gets its 1-in-20 roll here.
            if (chipRules) rollShop()
            openPortal()
        }
    }

    /** 1 in 20: the shopkeeper sends a message and a side gate opens (solo campaign only). */
    private fun rollShop() {
        if (!inShop && rng.nextFloat() < SHOP_CHANCE) offerShop()
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
        if (!upgradeReturnsToCombat) rollShop()
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

    private fun inDoor(d: ExitDoor): Boolean = when (d.side) {
        0 -> kotlin.math.abs(px - d.x) < (if (d.target == CampaignMap.BOSS_INDEX) 120f else GATE_HALF_WIDTH) && py < arena.portalY + PORTAL_RADIUS
        3 -> kotlin.math.abs(px - d.x) < GATE_HALF_WIDTH && py > arena.height - BOTTOM_DOOR_INSET - PORTAL_RADIUS
        1 -> px < playerRadius + 14f && kotlin.math.abs(py - d.y) < SHOP_GATE_HALF
        else -> px > arena.width - playerRadius - 14f && kotlin.math.abs(py - d.y) < SHOP_GATE_HALF
    }

    /**
     * PC rooms (owner, 2026-10-11): up to four exit doors open when the room is clear, so the
     * player picks a way out. The top-centre gate is always one; up to three more go on the far
     * wall (left / right of it) or the side walls (never the shop door's wall). Every door has a
     * clear mouth and can be walked to. Whichever door is taken, the next level is rolled the
     * same way as before: the choice is in the player's head.
     */
    private fun openExtraDoors() {
        // Endless only: campaign rooms have the world map's doors.
        if (mode != GameMode.ENDLESS) return
        exitDoors.clear()
        if (!config.consumables || inShop || mirror) return
        val roll = doorRng.nextFloat()
        val extra = when {
            roll < EXTRA_DOOR_ODDS[0] -> 0
            roll < EXTRA_DOOR_ODDS[1] -> 1
            roll < EXTRA_DOOR_ODDS[2] -> 2
            else -> 3
        }
        if (extra == 0) return
        val reach = reachableCells()
        val options = ArrayList<ExitDoor>(4)
        for (f in floatArrayOf(0.22f, 0.78f)) {
            val x = arena.width * f
            if (topMouthClear(x) && reach(x, arena.portalY + 40f)) options += ExitDoor(x, arena.portalY, 0)
        }
        val offsets = (0..12).flatMap { k -> if (k == 0) listOf(0f) else listOf(k * 40f, -k * 40f) }
        for (right in listOf(false, true)) {
            if (shopGateOpen && shopGateRight == right) continue
            for (off in offsets) {
                val y = arena.height * 0.42f + off
                if (y < 200f || y > arena.height - 260f) continue
                if (gateMouthClear(right, y) && reach(if (right) arena.width - 30f else 30f, y)) {
                    options += ExitDoor(if (right) arena.width else 0f, y, if (right) 2 else 1)
                    break
                }
            }
        }
        options.shuffle(doorRng)
        exitDoors += options.take(extra)
        if (exitDoors.isNotEmpty()) showBanner("${exitDoors.size + 1} EXITS OPEN", "CHOOSE YOUR ROUTE", 1.6f)
    }

    /** The strip in front of a far-wall door is free of obstacles. */
    private fun topMouthClear(x: Float): Boolean {
        var dx = -GATE_HALF_WIDTH
        while (dx <= GATE_HALF_WIDTH) {
            var y = 10f
            while (y <= 170f) {
                if (arena.obstacleAt(x + dx, y, 2f) >= 0) return false
                y += 20f
            }
            dx += 20f
        }
        return arena.isFree(x, arena.portalY + 40f, playerRadius + 4f)
    }

    /** Opens the side gate to the shop for this room (also a test hook). */
    fun offerShop() {
        if (inShop) return
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
                // Never on top of a world-map door on the same wall.
                val side = if (right) 2 else 1
                if (exitDoors.any { it.side == side && kotlin.math.abs(it.y - y) < SHOP_GATE_HALF * 2f + 40f }) continue
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
        // Campaign: the shop's exit leads back into this room, at the shop's doorway.
        shopReturnRoom = room; shopReturnRight = shopGateRight; shopReturnY = shopGateY
        if (!mirror) sweepLoot() else loot.clear()
        exitDoors.clear()
        goingToShop = false
        shopGateOpen = false
        skipShopPrompt = false
        topGateLocked = false
        shopArrowFlash = 0f
        shopSeed = rng.nextLong()
        val room = ArenaGenerator.shopRoom(Random(shopSeed))
        plan = LevelPlan(level, LevelKind.SHOP, room, emptyList())
        arena = Arena(room)
        clearAll()
        // Everyone comes in together, side by side; each player gets their own stock.
        for (o in ops) {
            if (o.gone) continue
            arena.pushOut(arena.spawnX + if (coop) (o.index * 2 - 1) * 42f else 0f, arena.spawnY, playerRadius)
            o.px = arena.out[0]; o.py = arena.out[1]
            o.facing = -MathUtil.PI / 2f
            o.shopReady = false
            o.worldReady = false
            cur = o
            try { o.shopItems = rollShopItems(); o.shopGear = rollShopGear(o) } finally { cur = ops[primary] }
        }
        phaseTimer = 0f
        keeperLook = KeeperLook.forStock(ops[primary].shopItems)
        phase = Phase.PORTAL
        portalOpen = true
        invuln = 1f
        showBanner("UPGRADE SHOP", "GOLDEN & TITANIUM MODS · PAY WITH RUN €", 2f)
    }

    /**
     * Four distinct GOLDEN-or-better mods the build can still take, priced by
     * rarity and depth. Drawn by rarity weight, so TITANIUM stays the rare find.
     */
    /** The counter's equipment (owner, 0.32.0): always [SHOP_GEAR] pieces, PURPLE and up, at the buyer's level. */
    private fun rollShopGear(o: Operative): List<GearShopItem> = List(SHOP_GEAR) {
        val r = com.cyberoperative.game.data.Gear.rollShopRarity(gearRng, config.difficulty.inferno)
        val item = com.cyberoperative.game.data.Gear.roll(gearRng, ++gearSerial, com.cyberoperative.game.data.Gear.itemLevel(o.gearLevel, gearRng), r)
        GearShopItem(item, com.cyberoperative.game.data.Gear.shopPrice(item), sold = false)
    }

    /** Buys equipment piece [index] from the current operative's counter: it goes with the run's finds. */
    fun buyShopGear(index: Int): Boolean {
        val o = cur
        val g = o.shopGear.getOrNull(index) ?: return false
        if (!inShop || g.sold || wallet < g.price) return false
        eurosEarned -= g.price
        o.gearFound += g.item
        o.shopGear = o.shopGear.mapIndexed { i, it -> if (i == index) it.copy(sold = true) else it }
        addText(o.px, o.py - 40f, "-${g.price} €", TextKind.INFO)
        sound(GameSound.CURRENCY)
        sound(if (g.item.rarity >= com.cyberoperative.game.data.GearRarity.LEGENDARY) GameSound.GEAR_LEGENDARY else GameSound.GEAR_SHEEN)
        return true
    }

    /** Host: operative [opIndex] buys equipment from their own counter (a co-op guest's purchase). */
    fun buyShopGearFor(opIndex: Int, index: Int): Boolean {
        val o = ops.getOrNull(opIndex) ?: return false
        if (o.gone) return false
        cur = o
        try { return buyShopGear(index) } finally { cur = ops[primary] }
    }

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
            slideSide = skipPromptSide
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
        if (!inShop || item.sold || wallet < item.price || !build.isEligible(item.def)) return false
        if (build.needsSlot(item.def)) {
            if (replace == null || replace !in build.weapons()) return false
            dropWeapon(replace)
        }
        eurosEarned -= item.price
        applyUpgrade(item.def)
        cur.shopItems = shopItems.mapIndexed { i, it -> if (i == index) it.copy(sold = true) else it }
        addText(px, py - 40f, "-${item.price} €", TextKind.INFO)
        sound(GameSound.CURRENCY)
        sound(GameSound.UPGRADE_SELECTED)
        return true
    }

    /** Host: operative [opIndex] buys from their own shop (a co-op guest's purchase). */
    fun buyShopItemFor(opIndex: Int, index: Int, replace: String? = null): Boolean {
        val o = ops.getOrNull(opIndex) ?: return false
        if (o.gone) return false
        cur = o
        try { return buyShopItem(index, replace) } finally { cur = ops[primary] }
    }

    /** Frees a weapon slot: the weapon, its timers and anything it left on the field. */
    private fun dropWeapon(id: String) {
        build.remove(id)
        weaponTimers.remove(id)
        if (id == Upgrades.PLASMA_BEAM.id) { beamActive = false; beamHeat = 0f; beamCooldown = 0f }
        hp = min(hp, stats.maxHp)
        sound(GameSound.UI_BACK)
    }

    /** Applies an upgrade's immediate side effects (HP gained). Used by the shop. */
    private fun applyUpgrade(def: com.cyberoperative.game.data.UpgradeDef) {
        val wasMaxHp = stats.maxHp
        build.take(def)
        val gained = stats.maxHp - wasMaxHp
        if (gained > 0f) hp += gained
        hp = min(hp, stats.maxHp)
    }

    private fun openPortal() {
        phase = Phase.PORTAL
        phaseTimer = 0f
        portalOpen = true
        if (campaign && !inShop && !mirror) campaignCleared()
        openExtraDoors()
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
     * Revive in place (§34): half HP, a long grace period and
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
            format = 2, world = world, room = room, clearedRooms = clearedRooms.toList(), visitedRooms = visitedRooms.toList(),
            phase = phase.name, phaseTimer = phaseTimer,
            px = px, py = py, hp = hp, facing = facing,
            upgrades = HashMap(build.owned()), runLevel = runLevel, xp = xp,
            pendingUpgrades = pendingUpgrades, rewardBatchTotal = rewardBatchTotal, rewardBatchTaken = rewardBatchTaken,
            offerLuck = offerLuck, offer = offer.map { it.def.id }, upgradeReturnsToCombat = upgradeReturnsToCombat,
            rerollsLeft = rerollsLeft, revivesLeft = revivesLeft, revivesUsed = revivesUsed,
            hpCharges = cur.hpCharges, packetBombs = cur.packetBombs,
            wide = com.cyberoperative.game.data.ArenaShape.wide, pcRules = config.consumables,
            score = score, kills = kills, bosses = bossesDefeated, elites = elitesDefeated, events = eventsCompleted,
            completedEventIds = HashSet(completedEventIds), euros = eurosEarned, diamonds = diamondsEarned,
            runSeconds = runSeconds, levelSeconds = levelSeconds, levelDamageTaken = levelDamageTaken,
            levelEnemyTotal = levelEnemyTotal, levelKills = levelKills, levelSpawned = levelSpawned,
            stageTimer = stageTimer, timedRemaining = timedRemaining, portalOpen = portalOpen,
            waveIndex = waveIndex, waveTimer = waveTimer, spawnTimer = spawnTimer, hazardTimer = hazardTimer,
            enemies = saved,
            shopGateOpen = shopGateOpen, shopGateRight = shopGateRight, shopGateY = shopGateY,
            vaultCracked = vaultCracked
        )
    }

    private fun applySnapshot(r: RunSnapshot) {
        mode = GameMode.entries.firstOrNull { it.name == r.mode } ?: mode
        if (campaign) {
            world = r.world.coerceIn(1, CampaignMap.WORLDS)
            room = r.room.coerceIn(0, CampaignMap.BOSS_INDEX)
            worldMap = CampaignMap.generate(config.seed, world)
            clearedRooms.clear(); clearedRooms += r.clearedRooms
            visitedRooms.clear(); visitedRooms += r.visitedRooms
            revisit = room in clearedRooms
        }
        level = r.level
        levelSeed = r.levelSeed
        previousArenaId = r.previousArenaId
        previousEvent = r.previousEvent
        // The same seed rebuilds the same room, waves and event rules.
        plan = if (campaign) campaignPlan(world, room, room in clearedRooms)
        else LevelPlanner.plan(level, Random(levelSeed), previousArenaId, previousEvent, config.seed, pressureCount(level), inferno = config.difficulty.inferno > 0)
        vaultCracked = r.vaultCracked
        val extra = if (plan.rules.vault && !vaultCracked) listOf(Arena.vaultObstacle(plan.arena)) else emptyList()
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
        if (config.consumables) {
            if (r.hpCharges >= 0) cur.hpCharges = r.hpCharges
            if (r.packetBombs >= 0) cur.packetBombs = r.packetBombs
        }
        score = r.score; kills = r.kills; bossesDefeated = r.bosses; elitesDefeated = r.elites
        eventsCompleted = r.events; completedEventIds += r.completedEventIds
        eurosEarned = r.euros; diamondsEarned = r.diamonds; runSeconds = r.runSeconds
        levelSeconds = r.levelSeconds; levelDamageTaken = r.levelDamageTaken; levelEnemyTotal = r.levelEnemyTotal
        levelKills = r.levelKills; levelSpawned = r.levelSpawned; stageTimer = r.stageTimer
        timedRemaining = r.timedRemaining; portalOpen = r.portalOpen
        waveCarry.clear()
        waveIndex = r.waveIndex; waveTimer = r.waveTimer; spawnTimer = r.spawnTimer; hazardTimer = r.hazardTimer
        px = r.px; py = r.py; facing = r.facing
        hp = r.hp.coerceIn(1f, stats.maxHp)
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
        // The shop offered before the save is still waiting (owner: don't punish busy players).
        if (r.shopGateOpen) {
            shopGateRight = r.shopGateRight
            shopGateY = r.shopGateY
            if (shopGateY <= 0f && !placeShopGate()) shopGateY = arena.height * 0.5f
            shopGateOpen = true
            // Remind them it's there.
            shopMessageSerial++
        }
        if (chipRules && (phase == Phase.UPGRADE || ops.any { it.pendingUpgrades > 0 })) {
            // A save from before upgrade chips, made mid-pick: its picks drop as chips instead.
            for (o in ops) {
                val n = o.pendingUpgrades
                o.pendingUpgrades = 0; o.offer = emptyList()
                for (i in 0 until n) dropChip(o.px + (i % 3 - 1) * 60f, o.py - 90f - (i / 3) * 60f, only = o)
            }
            if (phase == Phase.UPGRADE) {
                if (upgradeReturnsToCombat) { upgradeReturnsToCombat = false; phase = Phase.COMBAT; phaseTimer = 0f } else openPortal()
            }
        }
        if (phase == Phase.UPGRADE) {
            val restored = r.offer.mapNotNull { id ->
                Upgrades.all.firstOrNull { it.id == id }?.let { UpgradeOffer(it, if (it.instant) 1 else build.level(it.id) + 1) }
            }
            offer = restored.ifEmpty { build.rollOffer(rng, luck = offerLuck) }
            if (offer.isEmpty()) { pendingUpgrades = 0; finishUpgrades() }
        }
        if (campaign) {
            exitDoors.clear()
            exitDoors += campaignDoors(room)
            if (room >= CampaignMap.BOSS_INDEX && room in clearedRooms) {
                if (world >= CampaignMap.WORLDS) campaignComplete = true else placeWorldServer()
            }
        }
        invuln = 2f
        showBanner("OPERATION RESUMED", roomLabel(), 1.6f)
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

        // No HP regeneration over time (owner, 2026-10-11: "way too easy" with HP charges in the
        // game). Firewall is gone entirely (2026-10-13).

        tickAbilities(cur, dt)
        // Movement. A co-op guest moves its own operative and reports where it is.
        val mag = sqrt(inputX * inputX + inputY * inputY)
        moving = if (cur.remote) cur.netMoving else mag > MOVE_DEADZONE || cur.dashTime > 0f
        updateStatuses(dt)
        if (cur.remote && (cur.rooted > 0f || cur.frozen > 0f)) {
            // SEIZED: the guest's reported position is ignored until it breaks free.
            stillTime += dt
        } else if (cur.remote) {
            // Glide toward where the guest should be by now (its reports arrive in steps).
            cur.netAge += dt
            val tx = if (cur.netValid) cur.netPredictX() else cur.netX
            val ty = if (cur.netValid) cur.netPredictY() else cur.netY
            val far = (tx - px) * (tx - px) + (ty - py) * (ty - py) > NET_SNAP * NET_SNAP
            val k = if (far) 1f else 1f - kotlin.math.exp(-Operative.NET_FOLLOW * dt)
            arena.pushOut(px + (tx - px) * k, py + (ty - py) * k, playerRadius)
            px = arena.out[0]
            py = arena.out[1]
            if (moving) {
                facing = cur.netFacing
                stillTime = 0f
                followUpLeft = 0
            } else stillTime += dt
        } else if (cur.dashTime > 0f) {
            dashStep(cur, dt)
            stillTime = 0f
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

        // STOP = SHOOT. On PC the mouse aims instead: holding the button fires at the
        // cursor whether or not the operative is moving.
        if (cur.aiming) {
            targetUid = -1
            val dx = cur.aimX - px
            val dy = cur.aimY - py
            if (dx * dx + dy * dy > 1f) facing = atan2(dy, dx)
        } else {
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
        }

        if (fireCooldown <= 0f) {
            fireVolley(facing)
            fireCooldown = 1f / (s.fireRate * (if (cur.overclock > 0f) OVERCLOCK_RATE else 1f))
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
            if (updateBeam(dt, facing)) beamHeat += dt
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
    // Campaign world map (owner, 2026-10-11)
    // ======================================================================

    /** The room plan for world [w] room [r]: always the same room for the run (seeded by world and room). */
    internal fun campaignPlan(w: Int, r: Int, revisit: Boolean): LevelPlan {
        val lvl = CampaignMap.levelFor(w, r)
        val rng = Random(config.seed xor (w * 1_000_003L + r * 7_919L) xor 0x77C0FFEE)
        var p = if (r >= CampaignMap.BOSS_INDEX) LevelPlanner.campaignBossPlan(w, lvl, rng, config.seed, inferno = config.difficulty.inferno > 0)
        else LevelPlanner.campaignRoomPlan(lvl, rng, config.seed, pressureCount(lvl))
        // A cleared room you walk back into is empty and calm.
        if (revisit) p = p.copy(kind = LevelKind.NORMAL, waves = emptyList(), boss = null, event = null, rules = com.cyberoperative.game.data.EventRules(), modifierNames = emptyList())
        return p
    }

    /** "WORLD 3 · ROOM 7", "WORLD 3 · BOSS", or (Endless) "WORLD ??? · ROOM ???". */
    fun roomLabel(): String = when {
        !campaign -> "WORLD ??? · ROOM ???"
        room >= CampaignMap.BOSS_INDEX -> "WORLD $world · BOSS"
        else -> "WORLD $world · ROOM ${room + 1}"
    }

    // --- OP level (owner, 2026-10-13) --------------------------------------------------------
    /** OP XP earned this run (banked into the profile by the session). */
    var opXpEarned = 0L
        private set

    /**
     * Monster level: the operative's level (OP Lv plus a quarter of Overclock), or how deep the
     * run is (level ÷ 2.5: World 15's boss is 60, Endless keeps climbing) when that's higher.
     */
    val monsterLevel: Int get() = max(config.opLevel, kotlin.math.ceil(level / 2.5f).toInt()).coerceAtLeast(1)

    // --- World server (owner, 2026-10-12) ------------------------------------------------
    // After a world boss falls, a glowing server rises in the middle of the room. Walk up to it
    // and press MOVE TO NEXT WORLD; in co-op everyone presses (1/2 READY), and walking away or
    // going into the shop cancels it.
    var worldServerOn = false
        private set
    var worldServerX = 0f
        private set
    var worldServerY = 0f
        private set
    /** The server is drawn and usable (not while the shop room is up). */
    val worldServerShown: Boolean get() = worldServerOn && !inShop

    private fun placeWorldServer() {
        arena.pushOut(arena.width / 2f, arena.height / 2f, WORLD_SERVER_RADIUS)
        worldServerX = arena.out[0]; worldServerY = arena.out[1]
        worldServerOn = true
        for (o in ops) o.worldReady = false
    }

    fun nearWorldServer(o: Operative = ops[primary]): Boolean =
        worldServerShown && phase == Phase.PORTAL && o.alive && !o.gone && MathUtil.dist(o.px, o.py, worldServerX, worldServerY) < WORLD_SERVER_REACH

    val worldReadyCount: Int get() = ops.count { !it.gone && it.worldReady }
    val worldReadyTotal: Int get() = ops.count { !it.gone }
    val meWorldReady: Boolean get() = ops[primary].worldReady

    /** MOVE TO NEXT WORLD for operative [index]: solo goes now, co-op once everyone pressed. */
    fun pressNextWorld(index: Int = primary): Boolean {
        val o = ops.getOrNull(index) ?: return false
        if (!nearWorldServer(o)) return false
        o.worldReady = true
        sound(GameSound.UI_CLICK)
        if (ops.any { !it.gone && !it.worldReady }) return true
        for (p in ops) p.worldReady = false
        phase = Phase.TRANSITION
        phaseTimer = 0f
        goingToShop = false
        pendingRoom = NEXT_WORLD
        pendingEntry = 3
        slideSide = 0
        sound(GameSound.ACCESS_GRANTED)
        return true
    }

    /** Campaign: which wall the way onward is on, for the "door open" hint. */
    fun forwardHint(): String {
        if (!campaign || inShop) return ""
        if (worldServerOn) return "WORLD SECURED · WALK TO THE SERVER IN THE CENTRE FOR WORLD ${world + 1}"
        val d = exitDoors.firstOrNull { it.target == room + 1 || it.target == NEXT_WORLD } ?: return ""
        val where = when (d.side) { 0 -> "▲ FAR WALL"; 1 -> "◀ LEFT WALL"; 2 -> "RIGHT WALL ▶"; else -> "▼ NEAR WALL" }
        val to = when {
            d.target == NEXT_WORLD -> "WORLD ${world + 1}"
            d.target >= CampaignMap.BOSS_INDEX -> "BOSS ROOM"
            else -> "ROOM ${d.target + 1}"
        }
        return "DOOR OPEN · $to · $where"
    }

    /** Where a door on [side] sits in the current arena. */
    private fun doorAt(side: Int, target: Int): ExitDoor = when (side) {
        0 -> ExitDoor(arena.portalX, arena.portalY, 0, target)
        1 -> ExitDoor(0f, arena.height * 0.5f, 1, target)
        2 -> ExitDoor(arena.width, arena.height * 0.5f, 2, target)
        else -> ExitDoor(arena.width / 2f, arena.height - BOTTOM_DOOR_INSET, 3, target)
    }

    /** Where an operative stands after walking in through the door on [side]. */
    private fun entryPoint(side: Int): Pair<Float, Float> = when (side) {
        0 -> arena.portalX to (arena.portalY + 110f)
        1 -> 110f to arena.height * 0.5f
        2 -> (arena.width - 110f) to arena.height * 0.5f
        else -> arena.spawnX to arena.spawnY
    }

    /** The doors of map room [r]: back, forward, and (after its boss) on to the next world. */
    private fun campaignDoors(r: Int): List<ExitDoor> {
        val out = ArrayList<ExitDoor>(3)
        for (n in worldMap.neighbours(r)) out += doorAt(worldMap.dir(r, n).side, n)
        return out
    }

    /**
     * Walk into map room [r] through the door on [entrySide]. A room already cleared is empty
     * with its doors open; a new one fills with threats and its doors stay shut until it's clear.
     * ([atY]: coming back from the shop, appear at the shop's doorway height.)
     */
    private fun enterRoom(r: Int, entrySide: Int, atY: Float = -1f) {
        room = r
        revisit = r in clearedRooms
        visitedRooms += r
        // The campaign plan builds the room's layout, so the shape must be set before it.
        applyRoomShape()
        startLevel(CampaignMap.levelFor(world, r), null, false, campaignPlan(world, r, revisit))
        exitDoors.clear()
        exitDoors += campaignDoors(r)
        // A beaten world boss's room keeps its world server (back from the shop, or walking
        // back in): the way on used to appear only when the boss fell, stranding the player.
        if (revisit && r >= CampaignMap.BOSS_INDEX && world < CampaignMap.WORLDS) placeWorldServer()
        var (ex, ey) = entryPoint(entrySide)
        if (atY >= 0f) ey = atY
        for (o in ops) {
            if (o.gone) continue
            arena.pushOut(ex + if (coop) (o.index * 2 - 1) * 42f else 0f, ey, playerRadius)
            o.px = arena.out[0]; o.py = arena.out[1]
            o.facing = when (entrySide) { 0 -> MathUtil.PI / 2f; 1 -> 0f; 2 -> MathUtil.PI; else -> -MathUtil.PI / 2f }
        }
        if (revisit) {
            // Nothing left here: doors open straight away.
            chipBudget = 0
            levelEnemyTotal = 0
            phase = Phase.PORTAL
            phaseTimer = 0f
            portalOpen = true
            showBanner(roomLabel(), "CLEARED", 1.2f)
        } else when {
            r == 0 -> showBanner("SERVER WORLD $world", "ROOM 1 · ${plan.enemyCount} THREATS", 2.2f)
            r >= CampaignMap.BOSS_INDEX -> showBanner("WORLD $world · BOSS", plan.boss?.name ?: "", 2f)
            plan.kind == LevelKind.EVENT -> {}
            else -> showBanner("ROOM ${r + 1}", "WORLD $world · ${plan.enemyCount} THREATS", 1.4f)
        }
    }

    /** The walk through a door has finished: arrive wherever it led. */
    private fun campaignAdvance() {
        if (inShop) {
            // Out of the shop: back into the room it was entered from, at its doorway.
            shopGateOpen = false
            enterRoom(shopReturnRoom.coerceAtLeast(0), if (shopReturnRight) 2 else 1, shopReturnY)
            return
        }
        if (pendingRoom == NEXT_WORLD) {
            world++
            worldMap = CampaignMap.generate(config.seed, world)
            clearedRooms.clear()
            visitedRooms.clear()
            enterRoom(0, 3)
            return
        }
        enterRoom(pendingRoom.coerceIn(0, CampaignMap.BOSS_INDEX), pendingEntry)
    }

    /** Campaign room cleared: its doors open; a world boss down opens the way to the next world. */
    private fun campaignCleared() {
        clearedRooms += room
        if (room < CampaignMap.BOSS_INDEX) return
        if (world >= CampaignMap.WORLDS) {
            campaignComplete = true
            campaignFinished = true
            showBanner("CAMPAIGN COMPLETE", "ALL ${CampaignMap.WORLDS} SERVER WORLDS SECURED", 3f)
        } else {
            placeWorldServer()
            showBanner("WORLD $world SECURED", "THE SERVER TO WORLD ${world + 1} IS ONLINE", 2.2f)
        }
    }

    /**
     * UI: after the campaign, carry on in Endless (no map; WORLD ??? · ROOM ???, doors anywhere),
     * or stop here. Returns true when the run carries on.
     */
    fun answerCampaignComplete(continueEndless: Boolean): Boolean {
        if (!campaignComplete) return false
        campaignComplete = false
        if (!continueEndless) return false
        mode = GameMode.ENDLESS
        exitDoors.clear()
        portalOpen = true
        phase = Phase.PORTAL
        phaseTimer = 0f
        openExtraDoors()
        showBanner("ENDLESS", "WORLD ??? · ROOM ???", 2.4f)
        return true
    }

    /** Test hook: start the campaign at world [w] (room 1). */
    fun debugEnterWorld(w: Int) {
        world = w
        worldMap = CampaignMap.generate(config.seed, w)
        clearedRooms.clear(); visitedRooms.clear()
        enterRoom(0, 3)
    }

    /** Test hook: jump to room [r] of this world, entering from [side]. */
    fun debugEnterRoom(r: Int, side: Int = 3) = enterRoom(r, side)

    // ======================================================================
    // PC consumables (owner, 2026-10-11): HP charge packets (Q) and Packet Bombs (E)
    // ======================================================================


    /** HP charge / Packet Bomb rolls: one set per operative in co-op (each player's own drops). */
    private fun dropLoot(x: Float, y: Float, hpChance: Float, bombChance: Float) {
        if (!config.consumables || mirror) return
        for (o in ops) {
            if (o.gone) continue
            val ox = (o.index * 2 - (ops.size - 1)) * 18f
            if (lootRng.nextFloat() < hpChance) placeLoot(Loot(LootKind.HP_CHARGE, x - 12f + ox, y).also { it.owner = o.index })
            if (lootRng.nextFloat() < bombChance) placeLoot(Loot(LootKind.PACKET_BOMB, x + 12f + ox, y).also { it.owner = o.index })
        }
    }

    /** Onto the floor, or (a boss dying) into the queue it sprays out of. */
    private fun placeLoot(l: Loot) {
        if (spewing) spewQueue += l else { loot += l; gearLanded(l) }
    }

    private fun updateSpew(dt: Float) {
        if (spewQueue.isEmpty()) return
        spewTimer -= dt
        if (spewTimer > 0f) return
        spewTimer = SPEW_INTERVAL
        val l = spewQueue.removeFirst()
        val a = lootRng.nextFloat() * MathUtil.TWO_PI
        // Slides SPEW_MIN..SPEW_MAX units before stopping (velocity decays at SPEW_DRAG per second).
        val dist = SPEW_MIN + lootRng.nextFloat() * (SPEW_MAX - SPEW_MIN)
        l.x = spewX; l.y = spewY
        l.vx = cos(a) * dist * SPEW_DRAG
        l.vy = sin(a) * dist * SPEW_DRAG
        l.age = 0f
        loot += l
        gearLanded(l)
        addParticle(spewX, spewY, 0xFFFFD426, 160f, 0.35f, 3f)
        sound(GameSound.CURRENCY)
    }

    private fun full(o: Operative, l: Loot): Boolean = when (l.kind) {
        LootKind.HP_CHARGE -> o.hpCharges >= MAX_HP_CHARGES
        LootKind.PACKET_BOMB -> o.packetBombs >= MAX_PACKET_BOMBS
        LootKind.CASH, LootKind.GEAR -> false
        // A new weapon with every slot taken waits for the swap prompt (or is walked over).
        LootKind.UPGRADE -> l.declined || chipDef(l)?.let { o.build.needsSlot(it) } != false
    }

    private fun chipDef(l: Loot): com.cyberoperative.game.data.UpgradeDef? = l.upgradeId?.let { Upgrades.byIdOrNull(it) }

    /** Drops one upgrade chip, its power-up rolled now (same odds as a card). */
    private fun dropChip(x: Float, y: Float, only: Operative? = null) {
        if (mirror) return
        // One chip per operative (co-op: each player's own pick, rolled from their own build).
        for (o in ops) {
            if (o.gone || (only != null && o !== only)) continue
            val pick = o.build.rollOffer(lootRng, count = 1, luck = currentLuck()).firstOrNull() ?: continue
            arena.pushOut(x + (o.index * 2 - (ops.size - 1)) * 26f, y, 14f)
            placeLoot(Loot(LootKind.UPGRADE, arena.out[0], arena.out[1], upgradeId = pick.def.id).also { it.owner = o.index })
        }
    }

    // --- Gear (Data Vault update, owner 2026-10-13) ------------------------------------
    /** Gear swapped in the INVENTORY mid-run: operative [index] gets new base stats (HP keeps its share). */
    fun reequip(index: Int, base: RunStats) {
        val o = ops.getOrNull(index) ?: return
        val frac = (o.hp / o.build.stats.maxHp.coerceAtLeast(1f)).coerceIn(0f, 1f)
        o.build.replaceBase(base)
        o.hp = (frac * o.build.stats.maxHp).coerceAtLeast(if (o.alive) 1f else 0f)
    }

    /** Own random so gear never shifts the other drops a seed produces. */
    private val gearRng = kotlin.random.Random(config.seed xor 0x6EA2L)
    private var gearSerial = 0L

    /**
     * Elites (sometimes) and bosses (always, several) drop gear. Personal loot: each player rolls
     * their own pieces at their own OP level. Bosses after World 12 / Endless level 120 roll better.
     */
    private fun dropGear(x: Float, y: Float, boss: Boolean, count: Int) {
        if (!config.consumables || mirror || count <= 0) return
        val inferno = config.difficulty.inferno
        val late = boss && (if (campaign) world > 12 else level >= 120)
        val luck = (config.difficulty.luck * 0.3f).coerceAtLeast(0f)
        for (o in ops) {
            if (o.gone) continue
            for (k in 0 until count) {
                val rarity = com.cyberoperative.game.data.Gear.rollRarity(gearRng, boss, inferno, luck, late)
                val item = com.cyberoperative.game.data.Gear.roll(gearRng, ++gearSerial, com.cyberoperative.game.data.Gear.itemLevel(o.gearLevel, gearRng), rarity)
                arena.pushOut(x + (o.index * 2 - (ops.size - 1)) * 30f + (k - (count - 1) / 2f) * 24f, y + 10f, 14f)
                placeLoot(Loot(LootKind.GEAR, arena.out[0], arena.out[1]).also { it.owner = o.index; it.gear = item })
            }
        }
    }

    /** The drop sound: a sheen above BLUE, a bigger one for GOLDEN and up. */
    private fun gearLanded(l: Loot) {
        val r = l.gear?.rarity ?: return
        when (r) {
            // The owner's recorded drop sounds (0.36.0): T1 Forged … T4 Abyssal.
            com.cyberoperative.game.data.GearRarity.FORGED -> { sound(GameSound.GEAR_FORGED); return }
            com.cyberoperative.game.data.GearRarity.VOLTAIC -> { sound(GameSound.GEAR_VOLTAIC); return }
            com.cyberoperative.game.data.GearRarity.CELESTIAL -> { sound(GameSound.GEAR_CELESTIAL); return }
            com.cyberoperative.game.data.GearRarity.ABYSSAL -> { sound(GameSound.GEAR_ABYSSAL); return }
            else -> {}
        }
        if (r >= com.cyberoperative.game.data.GearRarity.LEGENDARY) sound(GameSound.GEAR_LEGENDARY)
        else if (r.sheen) sound(GameSound.GEAR_SHEEN)
    }

    /** Applies a chip's power-up to [o] (swapping out [replace] when its weapon slots are full). */
    private fun applyChip(o: Operative, def: com.cyberoperative.game.data.UpgradeDef, replace: String? = null) {
        cur = o
        try {
            // Not offerable to this operative any more (maxed, or a co-op partner's build): roll a fresh one.
            val d = if (o.build.isEligible(def)) def else o.build.rollOffer(lootRng, count = 1, luck = currentLuck()).firstOrNull()?.def ?: return
            o.offer = listOf(UpgradeOffer(d, if (d.instant) 1 else o.build.level(d.id) + 1))
            o.pendingUpgrades = 1
            takeOffered(0, replace)
            o.offer = emptyList()
            o.pendingUpgrades = 0
            if (o === ops[primary]) { pickupName = d.name; pickupRarity = d.rarity; pickupSerial++ }
            addPulse(o.px, o.py, 60f, 0.35f, d.rarity.color)
        } finally { cur = ops[primary] }
    }

    /** UI: the swap prompt for a weapon chip. [replace] = weapon to give up, or null to keep yours. */
    fun answerChipSwap(replace: String?) {
        val l = swapChip ?: return
        swapChip = null
        val def = chipDef(l) ?: return
        val o = ops[primary]
        if (replace != null && replace in o.build.weapons()) {
            loot.remove(l)
            applyChip(o, def, replace)
            return
        }
        // Kept their weapons: the chip is thrown to the middle of the arena, clear of other pickups.
        throwToMiddle(l)
    }

    private fun throwToMiddle(l: Loot) {
        val cx = arena.width / 2f
        val cy = arena.height / 2f
        for (ring in 0 until 12) {
            val n = if (ring == 0) 1 else ring * 6
            for (i in 0 until n) {
                val a = i * MathUtil.TWO_PI / n
                val x = cx + cos(a) * ring * 56f
                val y = cy + sin(a) * ring * 56f
                if (!arena.isFree(x, y, 22f)) continue
                if (loot.any { it !== l && MathUtil.dist(it.x, it.y, x, y) < 50f }) continue
                l.x = x; l.y = y; l.declined = false
                addPulse(x, y, 50f, 0.3f, 0xFFB8C4D6)
                return
            }
        }
    }

    private fun collect(o: Operative, l: Loot, quiet: Boolean = false) {
        when (l.kind) {
            LootKind.HP_CHARGE -> o.hpCharges++
            LootKind.PACKET_BOMB -> o.packetBombs++
            LootKind.CASH -> eurosEarned += l.amount
            LootKind.UPGRADE -> { chipDef(l)?.let { applyChip(o, it) }; return }
            LootKind.GEAR -> {
                val g = l.gear ?: return
                o.gearFound += g
                addPulse(l.x, l.y, 50f, 0.35f, g.rarity.color)
                return
            }
        }
        if (!quiet) addText(l.x, l.y - 26f, when (l.kind) {
            LootKind.HP_CHARGE -> "+1 HP CHARGE"
            LootKind.PACKET_BOMB -> "+1 PACKET BOMB"
            LootKind.CASH -> "+€${l.amount}"
            LootKind.UPGRADE, LootKind.GEAR -> ""
        }, TextKind.INFO)
    }

    /**
     * Pickups drift to an operative close by and are collected on touch (if there's room in the
     * pouch). Once the room is clear, everything on the floor is pulled in to the operatives.
     */
    private fun updateLoot(dt: Float) {
        for (o in ops) if (o.consumableCooldown > 0f) o.consumableCooldown = max(0f, o.consumableCooldown - dt)
        if (!mirror) updateSpew(dt)
        if (loot.isEmpty()) return
        // Endless never clears: very old bags bank themselves so the floor never fills up.
        while (loot.size > MAX_FLOOR_LOOT) {
            val oldest = loot.firstOrNull { it.kind == LootKind.CASH } ?: break
            eurosEarned += oldest.amount
            loot.remove(oldest)
        }
        val vacuum = phase != Phase.COMBAT
        var chime = false
        val it = loot.iterator()
        while (it.hasNext()) {
            val l = it.next()
            l.age += dt
            if (l.vx != 0f || l.vy != 0f) {
                // Sprayed out of a boss: slide, slowing down, kept inside the room.
                arena.pushOut(l.x + l.vx * dt, l.y + l.vy * dt, 14f)
                l.x = arena.out[0]; l.y = arena.out[1]
                val damp = kotlin.math.exp(-SPEW_DRAG * dt)
                l.vx *= damp; l.vy *= damp
                if (!l.flying) { l.vx = 0f; l.vy = 0f } else continue
            }
            // The nearest operative with room for it (only its owner, in co-op).
            var best: Operative? = null
            var bestD = Float.MAX_VALUE
            for (o in ops) {
                if (!o.alive || full(o, l)) continue
                if (l.owner >= 0 && l.owner != o.index) continue
                val d = MathUtil.dist(o.px, o.py, l.x, l.y)
                if (d < bestD) { bestD = d; best = o }
            }
            if (best == null && l.kind == LootKind.UPGRADE && !l.declined && phase != Phase.COMBAT && swapChip == null && (l.owner < 0 || l.owner == primary)) {
                // Room clear and a weapon chip with full slots underfoot: ask about a swap.
                val me = ops[primary]
                if (me.alive && MathUtil.dist(me.px, me.py, l.x, l.y) < LOOT_PICKUP) { swapChip = l; l.declined = true }
            }
            if (l.declined && l.kind == LootKind.UPGRADE && swapChip !== l) {
                val me = ops[primary]
                if (MathUtil.dist(me.px, me.py, l.x, l.y) > LOOT_PICKUP * 2f) l.declined = false
            }
            val o = best ?: continue
            if (bestD < LOOT_PICKUP) {
                collect(o, l, quiet = l.kind == LootKind.CASH && vacuum)
                chime = true
                it.remove()
            } else if ((vacuum && l.kind != LootKind.UPGRADE && (l.kind != LootKind.GEAR || l.age > GEAR_SHOWCASE)) || bestD < LOOT_MAGNET) {
                // Room clear: everything streams in, faster the longer it flies.
                val speed = if (vacuum) 520f + 900f * min(1f, l.age * 0.5f) else 260f
                val k = min(1f, speed * dt / bestD)
                l.x += (o.px - l.x) * k
                l.y += (o.py - l.y) * k
            }
        }
        if (chime) sound(GameSound.CURRENCY)
    }

    /** Leaving the room: whatever is still on the floor is collected (if there's room for it). */
    private fun sweepLoot() {
        loot += spewQueue
        spewQueue.clear()
        spewing = false
        val fallback = ops.getOrNull(primary)?.takeIf { it.alive } ?: ops.firstOrNull { it.alive }
        for (l in loot) {
            // Each drop to its owner (if they're still in the run), else whoever is.
            val o = ops.getOrNull(l.owner)?.takeIf { !it.gone } ?: fallback ?: continue
            if (!full(o, l)) collect(o, l, quiet = true)
        }
        loot.clear()
    }

    /** Operative [index] drinks an HP charge packet: +[HP_CHARGE_HEAL] of max HP. False if it couldn't. */
    fun useHpCharge(index: Int = primary): Boolean {
        val o = ops.getOrNull(index) ?: return false
        if (!config.consumables || !o.alive || o.hpCharges <= 0 || o.consumableCooldown > 0f) return false
        if (phase == Phase.UPGRADE || phase == Phase.DEAD || phase == Phase.TRANSITION) return false
        if (o.hp >= o.build.stats.maxHp) return false
        o.hpCharges--
        o.consumableCooldown = CONSUMABLE_COOLDOWN
        cur = o
        try {
            heal(stats.maxHp * HP_CHARGE_HEAL, ignoreMul = true)
            addPulse(o.px, o.py, 70f, 0.4f, 0xFF00FF9C)
        } finally { cur = ops[primary] }
        sound(GameSound.REVIVE)
        return true
    }

    /** Operative [index] sets off a Packet Bomb: a big blast around it that also wipes hostile shots. */
    fun usePacketBomb(index: Int = primary): Boolean {
        val o = ops.getOrNull(index) ?: return false
        if (!config.consumables || !o.alive || o.packetBombs <= 0 || o.consumableCooldown > 0f || phase != Phase.COMBAT) return false
        o.packetBombs--
        o.consumableCooldown = CONSUMABLE_COOLDOWN
        cur = o
        try {
            firePulseDamage(o.px, o.py, PACKET_BOMB_RADIUS, stats.damage * PACKET_BOMB_DAMAGE, clearsProjectiles = true)
            addPulse(o.px, o.py, PACKET_BOMB_RADIUS * 0.6f, 0.6f, 0xFF7DF9FF)
            fx.shake(10f, 0.4f)
        } finally { cur = ops[primary] }
        sound(GameSound.EMP)
        return true
    }

    // ======================================================================
    // PC abilities (owner, 2026-10-11): Space dash, R / F class spells
    // ======================================================================

    private fun tickAbilities(o: Operative, dt: Float) {
        if (o.dashCooldown > 0f) o.dashCooldown = max(0f, o.dashCooldown - dt)
        if (o.spellCd[0] > 0f) o.spellCd[0] = max(0f, o.spellCd[0] - dt)
        if (o.spellCd[1] > 0f) o.spellCd[1] = max(0f, o.spellCd[1] - dt)
        if (o.overclock > 0f) o.overclock = max(0f, o.overclock - dt)
        if (o.fortify > 0f) o.fortify = max(0f, o.fortify - dt)
        if (o.veil > 0f) o.veil = max(0f, o.veil - dt)
    }

    private fun dashStep(o: Operative, dt: Float) {
        val step = min(dt, o.dashTime)
        o.dashTime -= step
        arena.pushOut(o.px + o.dashX * DASH_SPEED * step, o.py + o.dashY * DASH_SPEED * step, playerRadius)
        o.px = arena.out[0]
        o.py = arena.out[1]
        o.facing = atan2(o.dashY, o.dashX)
        if (rng.nextFloat() < 0.6f) addParticle(o.px, o.py, 0xFF7DF9FF, 60f, 0.3f, 3f)
    }

    private fun abilityPhase(): Boolean = phase == Phase.COMBAT || phase == Phase.CLEARED || phase == Phase.PORTAL

    /**
     * Space: a short dash where the operative is heading (or facing, standing still), with a
     * moment of invulnerability. A co-op guest dashes on its own screen; the host only grants
     * the invulnerability.
     */
    fun dash(index: Int = primary): Boolean {
        val o = ops.getOrNull(index) ?: return false
        if (!config.consumables || !o.alive || o.dashCooldown > 0f || o.rooted > 0f || o.frozen > 0f || !abilityPhase()) return false
        o.dashCooldown = DASH_COOLDOWN
        o.invuln = max(o.invuln, DASH_TIME + 0.12f)
        if (o.remote) return true
        val m = sqrt(o.inputX * o.inputX + o.inputY * o.inputY)
        if (m > MOVE_DEADZONE) { o.dashX = o.inputX / m; o.dashY = o.inputY / m }
        else { o.dashX = cos(o.facing); o.dashY = sin(o.facing) }
        o.dashTime = DASH_TIME
        if (!mirror) sound(GameSound.UI_CLICK)
        return true
    }

    /** R (slot 0) / F (slot 1): cast this operative's class spell. */
    fun castSpell(slot: Int, index: Int = primary): Boolean {
        val o = ops.getOrNull(index) ?: return false
        if (!config.consumables || !o.alive || slot !in 0..1 || o.spellCd[slot] > 0f || !abilityPhase()) return false
        val spell = com.cyberoperative.game.data.Spells.of(o.classId, slot)
        o.spellCd[slot] = spell.cooldown
        // A guest only runs its own movement spell locally; the host does the rest.
        if (mirror) {
            if (spell == com.cyberoperative.game.data.Spell.SHADOW_STEP) shadowStep(o)
            return true
        }
        cur = o
        try { cast(spell, o) } finally { cur = ops[primary] }
        addText(o.px, o.py - 52f, spell.label, TextKind.INFO)
        return true
    }

    /** Where a spell lands: the cursor / right-stick point, at most [range] away. */
    private fun spellTarget(o: Operative, range: Float): Pair<Float, Float> {
        var dx = o.aimX - o.px
        var dy = o.aimY - o.py
        val d = sqrt(dx * dx + dy * dy)
        if (d < 1f) { dx = cos(o.facing); dy = sin(o.facing); return (o.px + dx * range * 0.6f) to (o.py + dy * range * 0.6f) }
        val k = min(1f, range / d)
        return (o.px + dx * k) to (o.py + dy * k)
    }

    private fun shadowStep(o: Operative) {
        val (tx, ty) = spellTarget(o, SHADOW_STEP_RANGE)
        addPulse(o.px, o.py, 60f, 0.3f, 0xFF8FA7FF)
        // Step along the line so the blink never ends inside a wall.
        var x = o.px; var y = o.py
        val n = 12
        for (i in 1..n) {
            val nx = o.px + (tx - o.px) * i / n
            val ny = o.py + (ty - o.py) * i / n
            if (!arena.isFree(nx, ny, playerRadius)) break
            x = nx; y = ny
        }
        o.px = x; o.py = y
        o.invuln = max(o.invuln, 0.35f)
        addPulse(x, y, 70f, 0.35f, 0xFF8FA7FF)
        repeat(14) { addParticle(x, y, 0xFF8FA7FF, 160f, 0.4f, 3f) }
    }

    /** Shadow Step (owner, 0.35.0): six knives fan out and hunt threats down through walls. */
    private fun shadowKnives(o: Operative) {
        for (i in 0 until 6) {
            val p = spawnBolt(o.px, o.py, o.facing + i * MathUtil.TWO_PI / 6f, ProjKind.MISSILE, stats.damage * 2.2f) ?: break
            p.vx *= 0.7f; p.vy *= 0.7f
            p.homing = 9f; p.radius = 6f; p.life = 3.5f
            p.pierceLeft = 0; p.bounceLeft = 0; p.chainLeft = 0; p.splash = 0f
            p.ghost = true; p.seek = 1400f
            p.tint = 0xFF8FA7FF
        }
    }

    /** The threat with the most others around it within [range] of (x, y), or null. */
    private fun densestThreat(x: Float, y: Float, range: Float): Pair<Float, Float>? {
        var best: Enemy? = null
        var bestN = -1
        for (e in enemies.items) {
            if (!e.active || !e.targetable) continue
            if ((e.x - x) * (e.x - x) + (e.y - y) * (e.y - y) > range * range) continue
            var n = 0
            for (f in enemies.items) if (f.active && f.targetable && (f.x - e.x) * (f.x - e.x) + (f.y - e.y) * (f.y - e.y) < 130f * 130f) n++
            if (n > bestN) { bestN = n; best = e }
        }
        return best?.let { it.x to it.y }
    }

    private fun cast(spell: com.cyberoperative.game.data.Spell, o: Operative) {
        val s = stats
        when (spell) {
            com.cyberoperative.game.data.Spell.OVERCLOCK -> {
                o.overclock = OVERCLOCK_SECONDS
                addPulse(o.px, o.py, 80f, 0.4f, 0xFFFFC233)
                sound(GameSound.ACCESS_GRANTED)
            }
            com.cyberoperative.game.data.Spell.FRAG_GRENADE -> {
                val (tx, ty) = spellTarget(o, 480f)
                firePulseDamage(tx, ty, 170f, s.damage * 14f, clearsProjectiles = false)
                addPulse(tx, ty, 120f, 0.5f, 0xFFFF9A1A)
                repeat(30) { addParticle(tx, ty, if (it % 2 == 0) 0xFFFF9A1A else 0xFFFFE066, 320f, 0.6f, 3.5f) }
                fx.shake(7f, 0.3f)
                sound(GameSound.EMP)
            }
            com.cyberoperative.game.data.Spell.MISSILE_BARRAGE -> {
                for (i in 0 until 8) {
                    val a = o.facing + (i - 3.5f) * 0.32f
                    val p = spawnBolt(o.px, o.py, a, ProjKind.MISSILE, s.damage * 2.4f) ?: break
                    p.vx *= 0.5f; p.vy *= 0.5f
                    p.homing = 8f; p.radius = 7f; p.life = 3.2f
                    p.pierceLeft = 0; p.bounceLeft = 0; p.chainLeft = 0
                    p.splash = 60f
                }
                sound(GameSound.LANCE)
            }
            com.cyberoperative.game.data.Spell.EMP_FIELD -> {
                firePulseDamage(o.px, o.py, 340f, s.damage * 6f, clearsProjectiles = true)
                addPulse(o.px, o.py, 200f, 0.5f, 0xFF7DF9FF)
                sound(GameSound.EMP)
            }
            com.cyberoperative.game.data.Spell.FORTIFY -> {
                o.fortify = FORTIFY_SECONDS
                addPulse(o.px, o.py, 90f, 0.5f, 0xFFFFC233)
                sound(GameSound.FIREWALL_UP)
            }
            com.cyberoperative.game.data.Spell.GROUND_SLAM -> {
                val r = 240f
                firePulseDamage(o.px, o.py, r, s.damage * 12f, clearsProjectiles = false)
                for (e in enemies.items) {
                    if (!e.active || e.boss != null || !e.targetable) continue
                    val dx = e.x - o.px; val dy = e.y - o.py
                    val d = sqrt(dx * dx + dy * dy)
                    if (d < 1f || d > r + e.radius) continue
                    arena.pushOut(e.x + dx / d * 130f, e.y + dy / d * 130f, e.radius)
                    e.x = arena.out[0]; e.y = arena.out[1]
                }
                fx.shake(10f, 0.4f)
                sound(GameSound.EMP)
            }
            com.cyberoperative.game.data.Spell.SHADOW_STEP -> {
                if (o.remote) o.invuln = max(o.invuln, 0.35f) else shadowStep(o)
                shadowKnives(o)
            }
            com.cyberoperative.game.data.Spell.SMOKE_VEIL -> {
                o.veil = VEIL_SECONDS
                repeat(24) { addParticle(o.px, o.py, 0xFF5A6478, 120f, 0.9f, 6f) }
            }
            com.cyberoperative.game.data.Spell.FIRESTORM -> {
                // Owner, 0.35.0: lands on the threats, whichever way you're moving or aiming.
                val (tx, ty) = densestThreat(o.px, o.py, 620f) ?: spellTarget(o, 520f)
                for (i in 0 until 6) {
                    val a = i * 1.047f + rng.nextFloat() * 0.5f
                    val rr = if (i == 0) 0f else 70f + rng.nextFloat() * 50f
                    val x = tx + cos(a) * rr; val y = ty + sin(a) * rr
                    firePulseDamage(x, y, 90f, s.damage * 5f, clearsProjectiles = false)
                    addPulse(x, y, 70f, 0.45f, 0xFFFF5A1F)
                    repeat(8) { addParticle(x, y, 0xFFFF7A1A, 200f, 0.5f, 3f) }
                }
                sound(GameSound.EMP)
            }
            com.cyberoperative.game.data.Spell.ARCANE_NOVA -> {
                for (i in 0 until 16) {
                    val p = spawnBolt(o.px, o.py, i * MathUtil.TWO_PI / 16f, ProjKind.MISSILE, s.damage * 1.8f) ?: break
                    p.vx *= 0.6f; p.vy *= 0.6f
                    // Owner, 0.35.0: tracer bolts pass through walls and cover and hunt for longer.
                    p.homing = 6f; p.radius = 7f; p.life = 5f
                    p.pierceLeft = 0; p.bounceLeft = 0; p.chainLeft = 0
                    p.tint = 0xFFFF7A1A
                    p.ghost = true; p.seek = 1400f
                }
                addPulse(o.px, o.py, 110f, 0.4f, 0xFFFF7A1A)
                sound(GameSound.LANCE)
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
        for ((id, owned) in s.weapons) {
            val w = Weapons.byId(id) ?: continue
            if (w.stillOnly && moving) continue
            // A class's built-in weapon grows with the campaign level instead of picks.
            val level = if (w.innate) Weapons.classWeaponLevel(this.level) else owned
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
    private fun updateBeam(dt: Float, ang: Float): Boolean {
        val s = stats
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
        p.seek = 600f
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
        // Armor (Diablo 3's formula, owner 2026-10-13): armor / (armor + 50 × monster level).
        var dmg = amount * PowerGovernor.threatDamage(config.difficulty) * (1f - Armor.reduction(s.armor, monsterLevel))
        if (cur.fortify > 0f) dmg *= 1f - FORTIFY_REDUCTION
        // No single hit takes more than a set share of full HP: nothing one-shots.
        dmg = min(dmg, PowerGovernor.hitCap(config.difficulty) * s.maxHp)
        sinceDamage = 0f
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
        val armor = (e.def.armor + (if (e.isElite && config.difficulty.eliteArmor) ELITE_ARMOR else 0f)) * sqrt(Scaling.enemyHp(level))
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
        stragglerTimer = 0f
        if (e.isElite) {
            elitesDefeated++
            dropLoot(e.x, e.y, ELITE_HP_DROP, ELITE_BOMB_DROP)
            if (!mirror && gearRng.nextFloat() < ELITE_GEAR_CHANCE + 0.02f * config.difficulty.inferno.coerceAtMost(10)) dropGear(e.x, e.y, boss = false, count = 1)
        }
        lastKillX = e.x; lastKillY = e.y
        if (chipRules && !mirror && chipBudget > 0 && phase == Phase.COMBAT) {
            // Spread this room's planned chips over its kills: each kill rolls for one.
            val left = max(1, levelEnemyTotal - levelKills + 1)
            if (lootRng.nextFloat() < chipBudget.toFloat() / left) {
                chipBudget--
                chipsDropped++
                dropChip(e.x, e.y)
            }
        }
        val s = stats
        val rewardMul = e.rewardMul * (if (plan.kind == LevelKind.EVENT) plan.rules.rewardMul else 1f)
        gainXp(e.def.xp * (if (e.isElite) EliteModifier.REWARD_MUL else 1f) * s.xpMul)
        val euros = max(1, (e.def.euros * rewardMul * s.euroMul * difficultyReward).toInt())
        // PC: kills pay out as a cash bag on the floor (walk over it, or it's pulled in when the room is clear).
        if (config.consumables && !mirror) {
            // A cash bag for whoever is nearest (co-op: your partner's bags aren't yours to take).
            val near = ops.filter { it.alive }.minByOrNull { MathUtil.dist2(it.px, it.py, e.x, e.y) }
            loot += Loot(LootKind.CASH, e.x, e.y, euros).also { it.owner = near?.index ?: -1 }
        } else eurosEarned += euros
        score += (Scoring.kill(e.def.score, level, e.isElite, e.isChild) * difficultyReward).toLong()
        if (s.healOnKill > 0f) heal(s.healOnKill, quiet = true)
        // OP XP (owner, 2026-10-13): every kill, more for elites and bosses.
        if (!mirror) opXpEarned += OpProgress.killXp(monsterLevel, e.isElite, e.boss != null, s.xpMul)
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

    @Suppress("UNUSED_PARAMETER")
    private fun gainXp(amount: Float) {
        // Power-ups come from room clears (upgrade chips); data no longer grants picks.
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
        val hpMul = Scaling.enemyHp(level) * pressureHp() * rules.enemyHpMul * config.difficulty.enemyHp * opHpNow * adaptiveHp * config.coopHpMul *
            (if (elite != null) EliteModifier.BASE_HP_MUL * elite.hpMul * config.difficulty.eliteHpMul else 1f)
        e.maxHp = def.baseHp * hpMul
        e.hp = e.maxHp
        e.radius = def.radius * (if (elite != null) EliteModifier.SIZE_MUL else 1f)
        e.speed = def.baseSpeed * Scaling.enemySpeed(level) * rules.enemySpeedMul * (elite?.speedMul ?: 1f)
        e.damageMul = Scaling.enemyDamage(level) * pressureDamage() * rules.enemyDamageMul * config.difficulty.enemyDamage * opDamageNow * adaptiveDamage
        e.attackRateMul = Scaling.attackRate(level) * (elite?.attackRateMul ?: 1f)
        // Owner, 2026-10-11: elites are armoured (20% less damage taken, plus flat armour in damageEnemy).
        e.damageTakenMul = (elite?.damageTakenMul ?: 1f) * (if (elite != null && config.difficulty.eliteArmor) ELITE_DAMAGE_TAKEN else 1f)
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
            if (p.life <= 0f) {
                p.active = false
                if (!p.friendly && p.splash > 0f) hostileBlast(p.x, p.y, p.splash, p.damage)
                continue
            }
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
                val t = nearestEnemy(p.x, p.y, p.seek)
                if (t != null) {
                    val want = atan2(t.y - p.y, t.x - p.x)
                    val cur = atan2(p.vy, p.vx)
                    val turn = MathUtil.clamp(MathUtil.wrapAngle(want - cur), -p.homing * dt, p.homing * dt)
                    // Missiles accelerate after launch.
                    val sp = min(760f, sqrt(p.vx * p.vx + p.vy * p.vy) + 900f * dt)
                    p.vx = cos(cur + turn) * sp
                    p.vy = sin(cur + turn) * sp
                }
            } else if (p.homing > 0f && !p.friendly && !cur.cloaked) {
                // (A cloaked PHANTOM can't be homed on: the shot flies straight.)
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
                    if (!p.friendly && p.splash > 0f) hostileBlast(ox, oy, p.splash, p.damage)
                    else if (p.kind == ProjKind.MISSILE) detonate(ox, oy, p.splash, p.damage * 0.6f)
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
                    // A blocked shell still bursts, out at the blade.
                    if (p.splash > 0f) { hostileBlast(p.x, p.y, p.splash, p.damage); return }
                    addParticle(p.x, p.y, 0xFF00E5FF, 120f, 0.25f, 2f)
                    sound(GameSound.SHIELD_BLOCK)
                    return
                }
            }
        }
        val rr = playerRadius * 0.8f + p.radius
        if (MathUtil.dist2(p.x, p.y, px, py) < rr * rr) {
            p.active = false
            if (p.splash > 0f) hostileBlast(p.x, p.y, p.splash, p.damage)
            else damagePlayer(p.damage, p.x, p.y)
        }
    }

    /** A hostile shell bursting (GOOD GAME's siege cannon): hurts every operative in [radius]. */
    private fun hostileBlast(x: Float, y: Float, radius: Float, damage: Float) {
        addPulse(x, y, radius, 0.4f, 0xFFFF5A1A)
        addPulse(x, y, radius * 0.55f, 0.25f, 0xFFFFE0A0)
        repeat(12) { addParticle(x, y, 0xFFFF9A1A, 260f, 0.4f, 3.2f) }
        fx.shake(6f, 0.25f)
        sound(GameSound.ELITE_DEATH)
        val keep = cur
        for (o in ops) {
            if (!o.alive) continue
            val r = radius + playerRadius * 0.6f
            if (MathUtil.dist2(x, y, o.px, o.py) >= r * r) continue
            cur = o
            damagePlayer(damage, x, y)
        }
        cur = keep
    }

    private fun onBoltHit(p: Projectile, e: Enemy) {
        val s = stats
        // Never elites or bosses (owner, 2026-10-11: "they cannot be deleted").
        if (p.kind == ProjKind.BOLT && e.boss == null && !e.isElite && s.instantDeleteChance > 0f && rng.nextFloat() < s.instantDeleteChance) {
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

    /** Credits a boss kill; returns the € actually paid. */
    internal fun onBossDefeated(eurosReward: Int, scoreReward: Int, bonusPicks: Int = 0): Int {
        bossesDefeated++
        val b = boss
        // Everything the boss drops goes into a queue it sprays out of during its death.
        spewX = b?.x ?: arena.width / 2f; spewY = b?.y ?: arena.height / 2f
        spewTimer = 0.1f
        spewing = true
        dropLoot(spewX, spewY, BOSS_HP_DROP, BOSS_BOMB_DROP)
        // Gear (owner, 2026-10-13): a world boss drops three pieces per player, more on INFERNO.
        dropGear(spewX, spewY, boss = true, count = (if (campaign) BOSS_GEAR_DROPS else BOSS_GEAR_DROPS - 1) + config.difficulty.inferno.coerceAtMost(10) / 3)
        lastKillX = spewX; lastKillY = spewY
        val paid = (eurosReward * stats.euroMul * difficultyReward).toInt()
        eurosEarned += paid
        score += (scoreReward * difficultyReward).toLong()
        // Boss mods (owner, 2026-10-08): more picks, and rolled with extra luck.
        // Campaign: a world boss pays out like the end of a world (at least four boosted drops).
        grantPick(if (campaign) max(CAMPAIGN_BOSS_PICKS, bossPicks(level) + bonusPicks) else bossPicks(level) + bonusPicks)
        // The room's own clear reward joins the same spray (the room clears after the victory sequence).
        clearPicks()
        spewing = false
        bossLuckPending = true
        purgeHostiles()
        return paid
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
    private var lastHpSerial = 0
    private var lastBombSerial = 0
    private var lastDashSerial = 0
    private var lastSpell1Serial = 0
    private var lastSpell2Serial = 0
    private var lastRerollSerial = 0
    private var lastShopSerial = 0
    private var lastNextWorldSerial = 0
    private var lastTradeSerial = 0

    /** Host: the guest's latest movement and picks. */
    fun applyInput(index: Int, c: CoopInput) {
        val o = ops.getOrNull(index) ?: return
        if (o.gone) return
        o.remote = true
        if (o.alive && c.level == level && phase != Phase.TRANSITION && slideIn <= 0f) {
            o.netReport(c.x, c.y, netClock); o.netFacing = c.facing; o.netMoving = c.moving
            o.aiming = c.aiming; o.aimX = c.aimX; o.aimY = c.aimY
        } else {
            o.aiming = false
            // Still in the previous room on the guest's side: hold the spawn spot.
            o.netX = o.px; o.netY = o.py; o.netMoving = false; o.netValid = false; o.netVX = 0f; o.netVY = 0f
        }
        // PC consumables used by the guest (serials so a lost packet never double-uses one).
        if (c.hpSerial > lastHpSerial) { lastHpSerial = c.hpSerial; useHpCharge(index) }
        if (c.bombSerial > lastBombSerial) { lastBombSerial = c.bombSerial; usePacketBomb(index) }
        if (c.dashSerial > lastDashSerial) { lastDashSerial = c.dashSerial; dash(index) }
        if (c.spell1Serial > lastSpell1Serial) { lastSpell1Serial = c.spell1Serial; castSpell(0, index) }
        if (c.spell2Serial > lastSpell2Serial) { lastSpell2Serial = c.spell2Serial; castSpell(1, index) }
        if (c.pickSerial > lastPickSerial) {
            lastPickSerial = c.pickSerial
            chooseUpgradeFor(index, c.pickIndex, Upgrades.all.getOrNull(c.replaceIndex)?.id)
        }
        if (c.tradeSerial > lastTradeSerial) {
            lastTradeSerial = c.tradeSerial
            TradeAction.entries.getOrNull(c.tradeAction)?.let { tradeAct(index, it, TradeOffer(c.tradeEuros, c.tradePotions, c.tradeItems)) }
        }
        if (c.nextWorldSerial > lastNextWorldSerial) {
            lastNextWorldSerial = c.nextWorldSerial
            pressNextWorld(index)
        }
        if (c.shopSerial > lastShopSerial) {
            lastShopSerial = c.shopSerial
            if (c.shopIndex >= SHOP_GEAR_INDEX) buyShopGearFor(index, c.shopIndex - SHOP_GEAR_INDEX)
            else buyShopItemFor(index, c.shopIndex, Upgrades.all.getOrNull(c.shopReplace)?.id)
        }
        if (c.rerollSerial > lastRerollSerial) {
            lastRerollSerial = c.rerollSerial
            rerollFor(index)
        }
    }

    private val upgradeIndex: Map<String, Int> by lazy { Upgrades.all.withIndex().associate { it.value.id to it.index } }
    private val enemyIndex: Map<String, Int> by lazy { Enemies.all.withIndex().associate { it.value.id to it.index } }

    /** Host: everything the guest needs to draw this moment of the run. [sounds] = sounds since the last snapshot. */
    // --- Co-op trading (owner, 0.35.0; see Trade.kt) -------------------------------------
    val trade = TradeDesk()

    /** Trading needs two players and a room with no threats in it. */
    fun tradeAllowed(): Boolean = ops.size >= 2 && phase != Phase.DEAD && aliveCount() == 0

    /** Host: player [op] does [action]; a finished trade moves the potions here. */
    fun tradeAct(op: Int, action: TradeAction, offer: TradeOffer = TradeOffer()) {
        if (mirror || ops.size < 2) return
        val charges = intArrayOf(ops[0].hpCharges, ops[1].hpCharges)
        if (trade.act(op, action, offer, tradeAllowed(), charges, MAX_HP_CHARGES)) {
            ops[0].hpCharges += trade.done[1].potions - trade.done[0].potions
            ops[1].hpCharges += trade.done[0].potions - trade.done[1].potions
        }
    }

    fun captureWorld(seq: Int, sounds: List<GameSound>): CoopWorld {
        val w = CoopWorld()
        w.seq = seq
        w.level = level; w.levelSeed = levelSeed; w.previousArenaId = previousArenaId; w.previousEvent = previousEvent
        w.campaign = campaign; w.world = world; w.room = room; w.revisit = revisit
        w.vaultCracked = vaultCracked; w.vaultOpening = vaultOpening
        w.phase = phase; w.phaseTimer = phaseTimer; w.slideIn = slideIn; w.slideSide = slideSide
        w.score = score; w.kills = kills; w.euros = eurosEarned; w.diamonds = diamondsEarned
        w.bosses = bossesDefeated; w.elites = elitesDefeated; w.events = eventsCompleted
        w.levelKills = levelKills; w.levelSpawned = levelSpawned; w.waveIndex = waveIndex
        w.runLevel = runLevel; w.xp = xp; w.runSeconds = runSeconds; w.timedRemaining = timedRemaining
        w.portalOpen = portalOpen
        w.banner = banner; w.bannerSub = bannerSub; w.bannerTimer = bannerTimer
        w.bossPhaseLabel = bossPhaseLabel; w.bossUid = boss?.uid ?: -1
        for (o in ops) {
            val n = NetOp()
            n.x = o.px; n.y = o.py; n.hp = o.hp; n.facing = o.facing
            n.moving = o.moving; n.invuln = o.invuln; n.hurtFlash = o.hurtFlash
            n.rooted = o.rooted; n.encrypted = o.encrypted; n.encryptCharge = o.encryptCharge; n.burning = o.burning
            n.pulled = o.pulled; n.pullX = o.pullX; n.pullY = o.pullY; n.pullStrength = o.pullStrength
            n.chill = o.chill; n.frozen = o.frozen
            n.orbAngle = o.orbAngle; n.bladeAngle = o.bladeAngle; n.targetUid = o.targetUid
            n.beamActive = o.beamActive; n.beamX2 = o.beamX2; n.beamY2 = o.beamY2; n.beamHeat = o.beamHeat; n.beamCooldown = o.beamCooldown
            n.downed = o.downed; n.reviveProgress = o.reviveProgress; n.gone = o.gone; n.cloaked = o.cloaked
            n.pending = o.pendingUpgrades; n.rerolls = o.rerollsLeft; n.batchTotal = o.rewardBatchTotal; n.batchTaken = o.rewardBatchTaken
            n.hpCharges = o.hpCharges; n.packetBombs = o.packetBombs
            for ((id, l) in o.build.owned()) upgradeIndex[id]?.let { n.owned[it] = l }
            for (c in o.offer) upgradeIndex[c.def.id]?.let { n.offer += it to c.nextLevel }
            for (s in o.shopItems) upgradeIndex[s.def.id]?.let { n.shop += intArrayOf(it, s.nextLevel, s.price, if (s.sold) 1 else 0) }
            n.shopGear = if (o.shopGear.isEmpty()) "" else com.cyberoperative.game.data.Gear.encode(o.shopGear.map { it.item })
            n.shopGearSold = o.shopGear.foldIndexed(0) { i, m, it -> if (it.sold) m or (1 shl i) else m }
            n.shopReady = o.shopReady
            n.worldReady = o.worldReady
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
        for (l in loot) {
            val extra = l.gear?.let { g -> gearLook(g) } ?: l.upgradeId?.let { id -> Upgrades.all.indexOfFirst { it.id == id } } ?: -1
            w.loot += floatArrayOf(l.kind.ordinal.toFloat(), l.x, l.y, extra.toFloat(), l.owner.toFloat())
        }
        // The guest's newest finds (they bank them on their own device; repeats are merged by id).
        // Shared XP (owner, 0.31.0): the guest banks the same OP XP the host earns, 100%.
        w.opXp = opXpEarned
        if ((trade.open || trade.requestedBy >= 0) && !tradeAllowed()) trade.reset()
        w.tradeRequest = trade.requestedBy; w.tradeOpen = trade.open
        w.tradeOffers[0] = trade.offers[0]; w.tradeOffers[1] = trade.offers[1]
        w.tradeDone = trade.doneSerial; w.tradeDoneOffers[0] = trade.done[0]; w.tradeDoneOffers[1] = trade.done[1]
        w.tradeRefused = trade.refusedSerial; w.tradeRefusedFor = trade.refusedFor
        ops.getOrNull(1)?.let { g -> w.guestGear = if (g.gearFound.isEmpty()) "" else com.cyberoperative.game.data.Gear.encode(g.gearFound.takeLast(GUEST_GEAR_SENT)) }
        for (d in exitDoors) w.exitDoors += floatArrayOf(d.x, d.y, d.side.toFloat())
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
        w.darkness = darkness; w.lightFlicker = lightFlicker; w.bossVeil = bossVeil; w.bossShield = bossShield; w.bossCharge = bossCharge
        w.bossRingAngle = bossRingAngle; w.bossRingFilled = bossRingFilled; w.bossRingOut = bossRingOut; w.bossSync = bossSync; w.bossTrail = bossTrail.copyOf(); w.bossIceShell = bossIceShell; w.bossHeadMask = bossHeadMask
        victory.let { v ->
            w.victoryIndex = if (v == null) -1 else com.cyberoperative.game.data.Bosses.roster.indexOf(v.def)
            if (v != null) { w.victorySerial = v.serial; w.victoryT = v.t; w.victorySeconds = v.seconds; w.victoryBounty = v.bounty; w.victoryX = v.x; w.victoryY = v.y; w.victoryPhase = v.phase }
        }
        w.inShop = inShop; w.shopSeed = shopSeed; w.keeper = keeperLook.ordinal
        w.shopGateOpen = shopGateOpen; w.shopGateRight = shopGateRight; w.shopGateY = shopGateY; w.shopMessageSerial = shopMessageSerial
        w.worldServerOn = worldServerOn; w.worldServerX = worldServerX; w.worldServerY = worldServerY
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
    /** Co-op clock for partner smoothing (seconds this engine has run). */
    private var netClock = 0f

    /** Guest: apply one host snapshot. Older or duplicate snapshots are ignored. */
    fun applyWorld(w: CoopWorld) {
        if (!mirror || w.seq <= mirrorSeq) return
        mirrorSeq = w.seq
        // Co-op shop (v22): in and out of the shop rebuild the room too.
        val newRoom = w.level != level || w.levelSeed != levelSeed || w.room != room || w.world != world ||
            w.inShop != inShop || (w.inShop && w.shopSeed != shopSeed)
        shopGateOpen = w.shopGateOpen; shopGateRight = w.shopGateRight; shopGateY = w.shopGateY; shopMessageSerial = w.shopMessageSerial
        worldServerOn = w.worldServerOn; worldServerX = w.worldServerX; worldServerY = w.worldServerY
        if (newRoom && w.inShop) {
            level = w.level; levelSeed = w.levelSeed
            shopSeed = w.shopSeed
            keeperLook = KeeperLook.entries.getOrElse(w.keeper) { KeeperLook.GOLD }
            val shopRoom = ArenaGenerator.shopRoom(Random(shopSeed))
            plan = LevelPlan(level, LevelKind.SHOP, shopRoom, emptyList())
            arena = Arena(shopRoom)
            netTargets.clear()
            for (e in enemies.items) e.active = false
        } else if (newRoom) {
            level = w.level
            levelSeed = w.levelSeed
            previousArenaId = w.previousArenaId
            previousEvent = w.previousEvent
            if (w.campaign) {
                mode = GameMode.CAMPAIGN
                if (w.world != world) { world = w.world; worldMap = CampaignMap.generate(config.seed, world) }
                room = w.room
            } else mode = GameMode.ENDLESS
            revisit = w.revisit
            plan = if (w.campaign) campaignPlan(world, room, w.revisit)
            else LevelPlanner.plan(level, Random(levelSeed), previousArenaId, previousEvent, config.seed, pressureCount(level), inferno = config.difficulty.inferno > 0)
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
        darkness = w.darkness; darknessTarget = w.darkness; lightFlicker = w.lightFlicker; bossVeil = w.bossVeil; bossShield = w.bossShield; bossCharge = w.bossCharge
        bossRingAngle = w.bossRingAngle; bossRingFilled = w.bossRingFilled; bossRingOut = w.bossRingOut; bossSync = w.bossSync; bossTrail = w.bossTrail; bossIceShell = w.bossIceShell; bossHeadMask = w.bossHeadMask
        val vd = com.cyberoperative.game.data.Bosses.roster.getOrNull(w.victoryIndex)
        victory = if (vd == null) null else {
            val cur = victory
            (if (cur != null && cur.serial == w.victorySerial) cur else BossVictory(vd, w.victoryX, w.victoryY, w.victoryPhase, w.victorySeconds, w.victoryBounty, w.victorySerial)).also { it.t = w.victoryT }
        }
        val solidNow = barriers.count { it.solid }
        if (solidNow != barrierSolidCount) { barrierSolidCount = solidNow; rebuildArena() }
        vaultOpening = w.vaultOpening
        phase = w.phase; phaseTimer = w.phaseTimer; slideIn = w.slideIn; slideSide = w.slideSide
        score = w.score; kills = w.kills; eurosEarned = w.euros; diamondsEarned = w.diamonds
        bossesDefeated = w.bosses; elitesDefeated = w.elites; eventsCompleted = w.events
        levelKills = w.levelKills; levelSpawned = w.levelSpawned; waveIndex = w.waveIndex
        runLevel = w.runLevel; xp = w.xp; runSeconds = w.runSeconds; timedRemaining = w.timedRemaining
        // Shared XP: every kill and room either player makes counts in full for both.
        opXpEarned = maxOf(opXpEarned, w.opXp)
        trade.requestedBy = w.tradeRequest; trade.open = w.tradeOpen
        trade.offers[0] = w.tradeOffers[0]; trade.offers[1] = w.tradeOffers[1]
        trade.done[0] = w.tradeDoneOffers[0]; trade.done[1] = w.tradeDoneOffers[1]; trade.doneSerial = w.tradeDone
        trade.refusedSerial = w.tradeRefused; trade.refusedFor = w.tradeRefusedFor
        portalOpen = w.portalOpen
        if (w.banner != banner || w.bannerTimer > bannerTimer + 0.2f) { banner = w.banner; bannerSub = w.bannerSub }
        bannerTimer = w.bannerTimer
        bossPhaseLabel = w.bossPhaseLabel

        for ((i, n) in w.ops.withIndex()) {
            val o = ops.getOrNull(i) ?: continue
            val local = i == primary
            // The guest drives its own position (except on a new room, while down, or between rooms);
            // everything else comes from the host.
            val snap = newRoom || n.downed || n.gone || w.phase == Phase.TRANSITION || w.slideIn > 0f
            if (!local) {
                // The partner glides between snapshots (mirrorTick) instead of jumping to each.
                if (snap || !o.netValid) { o.px = n.x; o.py = n.y; o.netVX = 0f; o.netVY = 0f; o.netValid = false }
                o.netReport(n.x, n.y, netClock)
                o.facing = n.facing; o.moving = n.moving
            } else if (snap) { o.px = n.x; o.py = n.y; o.facing = n.facing; o.moving = n.moving }
            o.hp = n.hp; o.invuln = n.invuln; o.hurtFlash = n.hurtFlash
            o.rooted = n.rooted; o.encrypted = n.encrypted; o.encryptCharge = n.encryptCharge; o.burning = n.burning
            o.pulled = n.pulled; o.pullX = n.pullX; o.pullY = n.pullY; o.pullStrength = n.pullStrength
            o.chill = n.chill; o.frozen = n.frozen
            if (!local) { o.orbAngle = n.orbAngle; o.bladeAngle = n.bladeAngle }
            o.targetUid = n.targetUid
            o.beamActive = n.beamActive; o.beamX2 = n.beamX2; o.beamY2 = n.beamY2; o.beamHeat = n.beamHeat; o.beamCooldown = n.beamCooldown
            o.downed = n.downed; o.reviveProgress = n.reviveProgress; o.gone = n.gone; o.cloaked = n.cloaked
            o.pendingUpgrades = n.pending; o.rerollsLeft = n.rerolls; o.rewardBatchTotal = n.batchTotal; o.rewardBatchTaken = n.batchTaken
            o.hpCharges = n.hpCharges; o.packetBombs = n.packetBombs
            val owned = HashMap<String, Int>()
            for ((k, l) in n.owned) Upgrades.all.getOrNull(k)?.let { owned[it.id] = l }
            if (owned != o.build.owned()) o.build.restore(owned)
            val offer = n.offer.mapNotNull { (k, l) -> Upgrades.all.getOrNull(k)?.let { UpgradeOffer(it, l) } }
            if (offer.map { it.def.id to it.nextLevel } != o.offer.map { it.def.id to it.nextLevel }) o.offer = offer
            o.shopItems = n.shop.mapNotNull { s -> Upgrades.all.getOrNull(s[0])?.let { ShopItem(it, s[1], s[2], s[3] != 0) } }
            o.shopGear = com.cyberoperative.game.data.Gear.decode(n.shopGear).mapIndexed { gi, item ->
                GearShopItem(item, com.cyberoperative.game.data.Gear.shopPrice(item), (n.shopGearSold shr gi) and 1 != 0)
            }
            o.shopReady = n.shopReady
            o.worldReady = n.worldReady
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
        loot.clear()
        for (l in w.loot) {
            val kind = LootKind.entries.getOrElse(l[0].toInt()) { LootKind.HP_CHARGE }
            loot += if (kind == LootKind.GEAR) Loot(kind, l[1], l[2]).also { it.owner = l.getOrElse(4) { -1f }.toInt(); it.gear = gearFromLook(l[3].toInt()) }
            else Loot(kind, l[1], l[2], upgradeId = Upgrades.all.getOrNull(l[3].toInt())?.id).also { it.owner = l.getOrElse(4) { -1f }.toInt() }
        }
        if (w.guestGear.isNotEmpty()) ops.getOrNull(1)?.let { g ->
            for (item in com.cyberoperative.game.data.Gear.decode(w.guestGear)) if (g.gearFound.none { it.uid == item.uid }) g.gearFound += item
        }
        exitDoors.clear()
        for (d in w.exitDoors) exitDoors += ExitDoor(d[0], d[1], d[2].toInt())
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
            if (me.aiming) me.facing = atan2(me.aimY - me.py, me.aimX - me.px)
            tickAbilities(me, dt)
            if (me.dashTime > 0f) dashStep(me, dt)
        } else me.moving = false
        // The partner: glide toward where it should be by now.
        netClock += dt
        for (o in ops) {
            if (o === me || !o.netValid || o.gone) continue
            o.netAge += dt
            val tx = o.netPredictX(); val ty = o.netPredictY()
            val far = (tx - o.px) * (tx - o.px) + (ty - o.py) * (ty - o.py) > NET_SNAP * NET_SNAP
            val k = if (far) 1f else 1f - kotlin.math.exp(-Operative.NET_FOLLOW * dt)
            o.px += (tx - o.px) * k; o.py += (ty - o.py) * k
        }
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
    val localAiming: Boolean get() = ops[primary].aiming
    val localAimX: Float get() = ops[primary].aimX
    val localAimY: Float get() = ops[primary].aimY
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
                e.damageMul = Scaling.enemyDamage(level) * pressureDamage() * config.difficulty.enemyDamage * opDamageNow * adaptiveDamage
            } else {
                e.speed = (e.boss!!.def.speed) * (1f + 0.05f * e.boss!!.cycle)
                e.damageMul = Scaling.enemyDamage(level) * pressureDamage() * (1f + 0.1f * e.boss!!.cycle) * config.difficulty.enemyDamage * opDamageNow * bossDamageLift
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
        /** Elite armour (Normal/Hard): damage taken multiplier and flat armour added to the def's. */
        const val ELITE_DAMAGE_TAKEN = 0.8f
        const val ELITE_ARMOR = 3f
        /** Victory sequence length (game seconds): shatter, then the THREAT NEUTRALIZED card. */
        const val VICTORY_SECONDS = 4.2f
        /** PHANTOM: a cloak every [CLOAK_CYCLE] s lasting [CLOAK_SECONDS] s. */
        const val CLOAK_CYCLE = 10f
        const val CLOAK_SECONDS = 3.5f
        /** The one boss that sees through the cloak. */
        const val NULLSHADE_ID = "nullshade"
        /** Level-clear bonus-pick odds (were 45% and 30%). */
        const val BONUS_PICK_CAP = 0.3f
        /** Seconds the last 1–3 threats may go unkilled before they are purged. */
        const val STRAGGLER_SECONDS = 20f
        const val DEEP_PICK_CAP = 0.15f
        /** Picks a boss kill grants (was 2 + level/20: 11 at L180, now 6). */
        fun bossPicks(level: Int): Int = 2 + level / 40
        const val TRANSITION_TIME = 0.45f
        const val PORTAL_RADIUS = 46f
        const val GATE_HALF_WIDTH = 80f
        /** Campaign: a door that leads on to the next server world. */
        const val NEXT_WORLD = -10
        const val WORLD_SERVER_RADIUS = 46f
        const val WORLD_SERVER_REACH = 130f
        /** How far up from the bottom edge a near-wall (bottom) door's trigger sits. */
        const val BOTTOM_DOOR_INSET = 40f
        /** Least drops a world boss gives (the old "level 10" haul). */
        const val CAMPAIGN_BOSS_PICKS = 4
        /** The always-open top-centre exit gate, as a door. */
        private val MAIN_DOOR = ExitDoor(-1f, -1f, 0)
        /** Cumulative odds of 0 / 1 / 2 extra exit doors (else 3): 1–4 doors in all. */
        val EXTRA_DOOR_ODDS = floatArrayOf(0.25f, 0.6f, 0.85f)
        const val RING_THICKNESS = 12f
        const val BEAM_MAX_FIRE = 5f
        const val SHOT_CLEARANCE = 4f
        const val SHOP_CHANCE = 1f / 20f
        // PC consumables (owner, 2026-10-11).
        const val START_HP_CHARGES = 3
        const val MAX_HP_CHARGES = 9
        const val MAX_PACKET_BOMBS = 3
        const val HP_CHARGE_HEAL = 0.35f
        const val PACKET_BOMB_RADIUS = 420f
        const val PACKET_BOMB_DAMAGE = 30f
        const val CONSUMABLE_COOLDOWN = 1f
        const val ELITE_HP_DROP = 0.08f
        /** The shop's equipment pieces, and the co-op input index they start at. */
        const val SHOP_GEAR = com.cyberoperative.game.data.Gear.SHOP_GEAR_PIECES
        const val SHOP_GEAR_INDEX = 100
        /** Co-op smoothing: a partner further than this from where it should be jumps there. */
        const val NET_SNAP = 260f
        /** Gear (owner, 2026-10-13): chance an elite drops a piece (+2% per INFERNO tier), pieces per world boss. */
        const val ELITE_GEAR_CHANCE = 0.25f
        const val BOSS_GEAR_DROPS = 3
        /** A gear drop stays put this long after a room clears before it's pulled in (its beam is seen). */
        const val GEAR_SHOWCASE = 1.2f
        const val GUEST_GEAR_SENT = 6

        /** Packs a drop's look into one int for the co-op snapshot: rarity, slot, art. */
        fun gearLook(g: com.cyberoperative.game.data.GearItem): Int = ((g.rarity.ordinal * 8 + g.slot.ordinal) shl 8) or (g.art and 0xFF)
        fun gearFromLook(v: Int): com.cyberoperative.game.data.GearItem {
            val rs = (v shr 8).coerceAtLeast(0)
            return com.cyberoperative.game.data.GearItem(
                0L, com.cyberoperative.game.data.GearSlot.entries[rs % 8],
                com.cyberoperative.game.data.GearRarity.entries.getOrElse(rs / 8) { com.cyberoperative.game.data.GearRarity.COMMON },
                v and 0xFF, 1, 0f
            )
        }
        const val ELITE_BOMB_DROP = 0.02f
        const val BOSS_HP_DROP = 0.5f
        const val BOSS_BOMB_DROP = 0.25f
        const val LOOT_PICKUP = 34f
        const val LOOT_MAGNET = 110f
        const val MAX_FLOOR_LOOT = 160
        /** Boss loot: one piece every half second, sliding 70–170 units out of the boss. */
        const val SPEW_INTERVAL = 0.1f
        const val SPEW_MIN = 70f
        const val SPEW_MAX = 170f
        const val SPEW_DRAG = 4f
        // PC abilities (owner, 2026-10-11).
        const val DASH_TIME = 0.16f
        const val DASH_DISTANCE = 160f
        const val DASH_SPEED = DASH_DISTANCE / DASH_TIME
        const val DASH_COOLDOWN = 1.1f
        const val OVERCLOCK_SECONDS = 5f
        const val OVERCLOCK_RATE = 1.6f
        const val FORTIFY_SECONDS = 4f
        const val FORTIFY_REDUCTION = 0.6f
        const val VEIL_SECONDS = 4f
        const val SHADOW_STEP_RANGE = 380f
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
