package com.cyberoperative.game.data

/**
 * Bosses (§22–25). One every 10 levels; the roster cycles once exhausted and
 * every cycle is stronger (Scaling.bossHp + faster patterns).
 *
 * Identities (name, tag, concept) come from CyOps TD's boss roster, but every
 * boss is mechanically re-designed for real-time arena combat: movement plus
 * phase-specific attack patterns. All patterns are telegraphed or dodgeable —
 * there is no unavoidable damage.
 */
enum class BossMove {
    /** Slowly follows the player. */
    CHASE,
    /** Glides between anchor points in the upper half of the arena. */
    HOVER,
    /** Stands still and blinks to a new anchor between patterns. */
    TELEPORT,
    /** Circles the arena centre. */
    DRIFT
}

sealed class Pattern {
    /** Ring of [count] projectiles; repeated [waves] times, rotating by [rotateDeg]. */
    data class Radial(
        val count: Int, val speed: Float, val damage: Float,
        val waves: Int = 1, val waveGap: Float = 0.35f, val rotateDeg: Float = 0f
    ) : Pattern()

    /** Aimed fan at the player, repeated [bursts] times. */
    data class Aimed(
        val count: Int, val spreadDeg: Float, val speed: Float, val damage: Float,
        val bursts: Int = 1, val burstGap: Float = 0.25f
    ) : Pattern()

    /** Rotating spiral stream. */
    data class Spiral(
        val arms: Int, val duration: Float, val shotsPerSecond: Float,
        val degPerSecond: Float, val speed: Float, val damage: Float
    ) : Pattern()

    /** Telegraphed dash along a line toward the player. */
    data class Charge(val windup: Float, val speed: Float, val distance: Float, val damage: Float, val repeats: Int = 1) : Pattern()

    /** Calls in reinforcements next to the boss. */
    data class Summon(val enemyId: String, val count: Int) : Pattern()

    /** Expanding ring from the boss; outrun it or stand beyond its reach. */
    data class ShockRing(val maxRadius: Float, val speed: Float, val damage: Float) : Pattern()

    /** Telegraphed circular blasts; [targeted] drops one on the player's position. */
    data class Blasts(val count: Int, val radius: Float, val delay: Float, val damage: Float, val targeted: Boolean = true) : Pattern()

    /** Lingering corruption pools. */
    data class Zones(val count: Int, val radius: Float, val duration: Float, val dps: Float) : Pattern()

    /** Blink to another anchor. */
    data object Teleport : Pattern()

    /** Telegraphed data beam toward the player, then a short damaging line. */
    data class Beam(val windup: Float, val duration: Float, val width: Float, val damage: Float, val count: Int = 1, val spreadDeg: Float = 0f) : Pattern()

    /** Slow homing packets. */
    data class Homing(val count: Int, val speed: Float, val turn: Float, val damage: Float, val life: Float = 4.5f) : Pattern()

    /**
     * Barrier cubes rise out of the floor around the player in short wall
     * segments of 1–3 cubes (owner: "barrier blocks out of the floor restricting
     * player movement"). They block movement and shots, both ways.
     */
    data class CoverDeploy(val segments: Int, val life: Float, val rise: Float = 0.9f) : Pattern()

    /** A box of cubes rises around the player, one side open (away from the boss). */
    data class Lockdown(val inner: Float, val life: Float, val rise: Float = 1.1f, val gapCubes: Int = 3) : Pattern()

    /** Telegraphed laser that sweeps [sweepDeg]; [count] beams spaced evenly. Cover stops it. */
    data class SweepBeam(
        val windup: Float, val duration: Float, val sweepDeg: Float, val width: Float, val damage: Float, val count: Int = 1
    ) : Pattern()

    /** Lobbed shells onto marked spots (the first on the player); they fly over cover. */
    data class Mortar(val count: Int, val radius: Float, val flight: Float, val damage: Float, val volleys: Int = 1, val volleyGap: Float = 0.6f) : Pattern()
}

data class BossPhase(
    /** This phase applies while HP fraction is at or below this value. */
    val below: Float,
    val speedMul: Float,
    /** Rest between patterns. */
    val gap: Float,
    val patterns: List<Pattern>,
    val label: String
)

/** What kind of fight a boss is (owner's Boss Expansion sheets, 2026-10-10). */
enum class BossRole(val label: String, val color: Long) {
    BRUISER("BRUISER", 0xFFFF2D55),
    TANK("TANK", 0xFF2E9BFF),
    ASSASSIN("ASSASSIN", 0xFFFF2E6C),
    AREA_CONTROL("AREA CONTROL", 0xFFA259FF),
    SUMMONER("SUMMONER", 0xFFFF2EC4),
    CONTROLLER("CONTROLLER", 0xFFFF2D55),
    STATUS("STATUS", 0xFF7DF9FF),
    COMMANDER("COMMANDER", 0xFFFF4A6A),
    HUNTER("HUNTER", 0xFFFF2EC4)
}

/** Threat tier skulls: 1 LOW · 2 MEDIUM · 3 HIGH · 4 EXTREME. */
object ThreatTier {
    fun label(tier: Int) = when (tier) { 1 -> "LOW"; 2 -> "MEDIUM"; 3 -> "HIGH"; else -> "EXTREME" }
    fun blurb(tier: Int) = when (tier) {
        1 -> "Learn the pattern"; 2 -> "Increased complexity"; 3 -> "Multiple mechanics"; else -> "Relentless pressure"
    }
}

data class BossDef(
    val id: String,
    val name: String,
    val tag: String,
    val title: String,
    val codex: String,
    val color: Long,
    val radius: Float,
    val baseHp: Float,
    val speed: Float,
    val contactDamage: Float,
    val move: BossMove,
    /** Ordered from full HP downward: phase 1 first. */
    val phases: List<BossPhase>,
    val euros: Int,
    val score: Int,
    val role: BossRole = BossRole.BRUISER,
    /** 1 LOW … 4 EXTREME. */
    val tier: Int = 2,
    /** Flat damage removed from each hit (scaled with level like enemy armor). */
    val armor: Float = 0f,
    /** Hidden between eye windows; only targetable while visible (Nullshade). */
    val stealth: Boolean = false,
    /** Named signature abilities for the dossier card and codex; derived from the patterns when empty. */
    val abilities: List<String> = emptyList()
) {
    /** Up to [max] ability names: the named ones, or one per distinct pattern kind. */
    fun abilityNames(max: Int = 4): List<String> =
        abilities.ifEmpty { phases.flatMap { it.patterns }.map { it.displayName }.distinct() }.take(max)
}

/** Player-facing name of a pattern (dossier card, codex). */
val Pattern.displayName: String
    get() = when (this) {
        is Pattern.Radial -> "Burst Ring"
        is Pattern.Aimed -> "Aimed Volley"
        is Pattern.Spiral -> "Spiral Stream"
        is Pattern.Charge -> "Dash Strike"
        is Pattern.Summon -> "Reinforcements"
        is Pattern.ShockRing -> "Shockwave"
        is Pattern.Blasts -> "Strike Markers"
        is Pattern.Zones -> "Corruption Pools"
        Pattern.Teleport -> "Blink"
        is Pattern.Beam -> "Data Beam"
        is Pattern.Homing -> "Homing Packets"
        is Pattern.CoverDeploy -> "Cover Deploy"
        is Pattern.Lockdown -> "Lockdown Cube"
        is Pattern.SweepBeam -> "Laser Sweep"
        is Pattern.Mortar -> "Pulse Mortar"
    }

object Bosses {

    private const val P1 = 1.0f
    private const val P2 = 0.6f
    private const val P3 = 0.25f

    val BREACH = BossDef(
        "breach", "BREACH", "[!!!]", "Coordinated Intrusion",
        "A coordinated breach attempt. No tricks, just weight — learn its rhythm: " +
            "a fan, a ring, then a charge when it gets desperate.",
        0xFFFF2D55, 48f, 1400f, 70f, 20f, BossMove.HOVER, listOf(
            BossPhase(P1, 1f, 1.3f, listOf(
                Pattern.Aimed(3, 30f, 250f, 12f, bursts = 2),
                Pattern.Radial(12, 190f, 10f)
            ), "PHASE 1"),
            BossPhase(P2, 1.15f, 1.1f, listOf(
                Pattern.Aimed(5, 50f, 270f, 12f, bursts = 2),
                Pattern.Charge(0.8f, 640f, 520f, 22f),
                Pattern.Radial(14, 200f, 10f, waves = 2, rotateDeg = 12f)
            ), "PHASE 2"),
            BossPhase(P3, 1.3f, 0.9f, listOf(
                Pattern.Radial(16, 210f, 11f, waves = 3, rotateDeg = 11f),
                Pattern.Charge(0.7f, 700f, 560f, 22f, repeats = 2),
                Pattern.Aimed(5, 60f, 290f, 12f, bursts = 3)
            ), "BREACH CRITICAL")
        ), euros = 120, score = 2000, role = BossRole.BRUISER, tier = 1
    )

    val BOTMASTER = BossDef(
        "botmaster", "BOTMASTER", "[B∞]", "Botnet Commander",
        "Commands a botnet and keeps calling it in. Thin the swarm, but keep " +
            "damage on the master.",
        0xFFFF2EC4, 46f, 1500f, 60f, 18f, BossMove.HOVER, listOf(
            BossPhase(P1, 1f, 1.4f, listOf(
                Pattern.Summon("bot", 4),
                Pattern.Aimed(3, 24f, 240f, 11f, bursts = 3, burstGap = 0.3f)
            ), "PHASE 1"),
            BossPhase(P2, 1.1f, 1.2f, listOf(
                Pattern.Summon("bot", 6),
                Pattern.Spiral(3, 2.4f, 10f, 90f, 190f, 10f),
                Pattern.Aimed(5, 40f, 250f, 11f, bursts = 2)
            ), "PHASE 2"),
            BossPhase(P3, 1.2f, 1.0f, listOf(
                Pattern.Summon("malware", 3),
                Pattern.Spiral(4, 3f, 12f, -110f, 200f, 10f),
                Pattern.Radial(18, 210f, 10f)
            ), "BOTNET OVERDRIVE")
        ), euros = 150, score = 2400, role = BossRole.SUMMONER, tier = 1
    )

    val WORM = BossDef(
        "worm_prime", "WORM PRIME", "[~~>]", "Self-Replicating Threat",
        "It leaves corrupted sectors behind and sheds fragments of itself. " +
            "Keep moving — standing in its trail is a mistake.",
        0xFFFF5A36, 44f, 1650f, 95f, 22f, BossMove.CHASE, listOf(
            BossPhase(P1, 1f, 1.4f, listOf(
                Pattern.Zones(3, 70f, 6f, 16f),
                Pattern.Radial(10, 180f, 11f, waves = 2, rotateDeg = 18f)
            ), "PHASE 1"),
            BossPhase(P2, 1.15f, 1.2f, listOf(
                Pattern.Summon("wormlet", 4),
                Pattern.Zones(4, 76f, 6f, 18f),
                Pattern.Aimed(3, 20f, 260f, 12f, bursts = 3)
            ), "PHASE 2"),
            BossPhase(P3, 1.3f, 1.0f, listOf(
                Pattern.Spiral(2, 3f, 12f, 140f, 200f, 11f),
                Pattern.Summon("wormlet", 5),
                Pattern.Zones(5, 80f, 7f, 20f)
            ), "REPLICATION STORM")
        ), euros = 170, score = 2800, role = BossRole.AREA_CONTROL, tier = 2
    )

    val RANSOM = BossDef(
        "ransom", "RANSOM", "[RM]", "Encryption Extortion",
        "Locks down sections of the arena with encryption blasts. Watch the " +
            "marked sectors and be somewhere else when they detonate.",
        0xFFFFD426, 46f, 1800f, 65f, 20f, BossMove.HOVER, listOf(
            BossPhase(P1, 1f, 1.4f, listOf(
                Pattern.Blasts(3, 90f, 1.2f, 22f),
                Pattern.Aimed(4, 36f, 250f, 12f, bursts = 2)
            ), "PHASE 1"),
            BossPhase(P2, 1.1f, 1.2f, listOf(
                Pattern.Blasts(5, 95f, 1.1f, 24f, targeted = true),
                Pattern.Radial(16, 200f, 11f),
                Pattern.Homing(3, 150f, 1.6f, 14f)
            ), "PHASE 2"),
            BossPhase(P3, 1.25f, 1.0f, listOf(
                Pattern.Blasts(7, 95f, 1.0f, 26f),
                Pattern.Zones(3, 85f, 6f, 20f),
                Pattern.Aimed(6, 70f, 270f, 12f, bursts = 3)
            ), "PAY OR PERISH")
        ), euros = 190, score = 3200, role = BossRole.CONTROLLER, tier = 2
    )

    val ROOTKIT = BossDef(
        "rootkit_king", "ROOTKIT", "[r00t]", "Kernel-Level Phantom",
        "Hides between attacks and reappears somewhere new. It always flickers " +
            "in before it fires — track the flicker.",
        0xFFB0304A, 42f, 1700f, 0f, 18f, BossMove.TELEPORT, listOf(
            BossPhase(P1, 1f, 1.2f, listOf(
                Pattern.Teleport,
                Pattern.Aimed(5, 60f, 250f, 12f, bursts = 2),
                Pattern.Homing(2, 150f, 1.5f, 14f)
            ), "PHASE 1"),
            BossPhase(P2, 1f, 1.0f, listOf(
                Pattern.Teleport,
                Pattern.Radial(14, 210f, 11f, waves = 2, rotateDeg = 13f),
                Pattern.Homing(4, 160f, 1.7f, 14f)
            ), "PHASE 2"),
            BossPhase(P3, 1f, 0.8f, listOf(
                Pattern.Teleport,
                Pattern.Aimed(7, 80f, 280f, 12f, bursts = 3),
                Pattern.Summon("rootkit", 2)
            ), "PRIVILEGE ESCALATION")
        ), euros = 210, score = 3600, role = BossRole.ASSASSIN, tier = 2
    )

    val SYN_STORM = BossDef(
        "syn_storm", "SYN-STORM", "[SS]", "Handshake Flood",
        "Floods the arena with half-open connections. The spirals have gaps; " +
            "find them and stay in them.",
        0xFFFF6A2E, 48f, 1900f, 55f, 20f, BossMove.DRIFT, listOf(
            BossPhase(P1, 1f, 1.2f, listOf(
                Pattern.Spiral(2, 3f, 10f, 100f, 190f, 11f),
                Pattern.Radial(12, 190f, 11f)
            ), "PHASE 1"),
            BossPhase(P2, 1.1f, 1.0f, listOf(
                Pattern.Spiral(3, 3.2f, 12f, -120f, 200f, 11f),
                Pattern.ShockRing(330f, 230f, 18f)
            ), "PHASE 2"),
            BossPhase(P3, 1.2f, 0.8f, listOf(
                Pattern.Spiral(4, 3.5f, 14f, 140f, 210f, 11f),
                Pattern.Radial(20, 220f, 11f, waves = 2, rotateDeg = 9f),
                Pattern.ShockRing(360f, 250f, 18f)
            ), "FULL FLOOD")
        ), euros = 230, score = 4000, role = BossRole.AREA_CONTROL, tier = 2
    )

    val KERNEL_PANIC = BossDef(
        "kernel_panic", "KERNEL PANIC", "[KP]", "System Crash",
        "Every attack sends shockwaves through the system. Rings expand from " +
            "it — step back out of reach or slip between waves.",
        0xFFFF9A1A, 50f, 2100f, 50f, 22f, BossMove.HOVER, listOf(
            BossPhase(P1, 1f, 1.4f, listOf(
                Pattern.ShockRing(300f, 220f, 18f),
                Pattern.Aimed(3, 30f, 250f, 12f, bursts = 2)
            ), "PHASE 1"),
            BossPhase(P2, 1.1f, 1.2f, listOf(
                Pattern.ShockRing(340f, 240f, 20f),
                Pattern.Blasts(4, 85f, 1.1f, 22f),
                Pattern.Radial(16, 200f, 11f)
            ), "PHASE 2"),
            BossPhase(P3, 1.2f, 1.0f, listOf(
                Pattern.ShockRing(380f, 260f, 20f),
                Pattern.Spiral(3, 2.6f, 12f, 130f, 200f, 11f),
                Pattern.Blasts(6, 85f, 1.0f, 24f)
            ), "BLUE SCREEN")
        ), euros = 250, score = 4400, role = BossRole.CONTROLLER, tier = 3
    )

    val EXFIL = BossDef(
        "exfil", "EXFIL", "[XX]", "Data Exfiltration",
        "Fast and greedy: it dashes across the arena to grab what it can. " +
            "Read the charge lines and sidestep.",
        0xFFFF4060, 40f, 1800f, 120f, 22f, BossMove.CHASE, listOf(
            BossPhase(P1, 1f, 1.2f, listOf(
                Pattern.Charge(0.8f, 680f, 520f, 22f),
                Pattern.Aimed(3, 26f, 270f, 12f, bursts = 2)
            ), "PHASE 1"),
            BossPhase(P2, 1.15f, 1.0f, listOf(
                Pattern.Charge(0.7f, 720f, 560f, 22f, repeats = 2),
                Pattern.Radial(14, 210f, 11f)
            ), "PHASE 2"),
            BossPhase(P3, 1.3f, 0.8f, listOf(
                Pattern.Charge(0.6f, 760f, 600f, 24f, repeats = 3),
                Pattern.Homing(4, 170f, 1.8f, 14f)
            ), "SMASH AND GRAB")
        ), euros = 270, score = 4800, role = BossRole.ASSASSIN, tier = 3
    )

    val WHITE_EYE = BossDef(
        "white_eye", "WHITE EYE", "[○_○]", "Offensive Watcher",
        "Watches everything and fires data beams along what it sees. The beam " +
            "sight is shown first — leave the line before it fires.",
        0xFFE6EEFA, 44f, 2000f, 0f, 18f, BossMove.TELEPORT, listOf(
            BossPhase(P1, 1f, 1.3f, listOf(
                Pattern.Beam(0.9f, 0.5f, 34f, 26f),
                Pattern.Teleport,
                Pattern.Aimed(5, 50f, 250f, 12f)
            ), "PHASE 1"),
            BossPhase(P2, 1f, 1.1f, listOf(
                Pattern.Beam(0.85f, 0.5f, 34f, 26f, count = 3, spreadDeg = 40f),
                Pattern.Teleport,
                Pattern.Radial(16, 210f, 11f)
            ), "PHASE 2"),
            BossPhase(P3, 1f, 0.9f, listOf(
                Pattern.Beam(0.8f, 0.55f, 36f, 28f, count = 5, spreadDeg = 72f),
                Pattern.Teleport,
                Pattern.Homing(4, 170f, 1.7f, 14f)
            ), "TOTAL SURVEILLANCE")
        ), euros = 290, score = 5200, role = BossRole.CONTROLLER, tier = 3
    )

    val ZOMBIE = BossDef(
        "zombie", "ZOMBIE", "[ZZ]", "Undead Process",
        "A process that refuses to terminate. It shambles, it spits, and it " +
            "gets angrier the closer it is to dying.",
        0xFFE0405A, 52f, 2400f, 70f, 24f, BossMove.CHASE, listOf(
            BossPhase(P1, 1f, 1.4f, listOf(
                Pattern.Aimed(5, 40f, 230f, 12f, bursts = 2),
                Pattern.Zones(2, 80f, 6f, 18f)
            ), "PHASE 1"),
            BossPhase(P2, 1.2f, 1.2f, listOf(
                Pattern.Summon("malware", 4),
                Pattern.Radial(18, 200f, 11f, waves = 2, rotateDeg = 10f)
            ), "PHASE 2"),
            BossPhase(P3, 1.45f, 0.9f, listOf(
                Pattern.Charge(0.7f, 620f, 480f, 24f, repeats = 2),
                Pattern.Radial(22, 220f, 11f, waves = 3, rotateDeg = 8f),
                Pattern.Summon("worm", 2)
            ), "UNDEAD FRENZY")
        ), euros = 310, score = 5600, role = BossRole.BRUISER, tier = 3
    )

    val SPOOFER = BossDef(
        "spoofer", "SPOOFER", "[¿¿]", "Identity Forger",
        "Forges copies of other threats to soak your fire. Kill the decoys " +
            "only when you must — the real target is the forger.",
        0xFFFF55AA, 42f, 2100f, 0f, 18f, BossMove.TELEPORT, listOf(
            BossPhase(P1, 1f, 1.3f, listOf(
                Pattern.Summon("phisher", 2),
                Pattern.Teleport,
                Pattern.Aimed(4, 40f, 260f, 12f, bursts = 2)
            ), "PHASE 1"),
            BossPhase(P2, 1f, 1.1f, listOf(
                Pattern.Summon("sniffer", 2),
                Pattern.Teleport,
                Pattern.Homing(4, 160f, 1.7f, 14f)
            ), "PHASE 2"),
            BossPhase(P3, 1f, 0.9f, listOf(
                Pattern.Summon("rootkit", 2),
                Pattern.Teleport,
                Pattern.Spiral(3, 2.5f, 12f, 150f, 210f, 11f)
            ), "MASS FORGERY")
        ), euros = 330, score = 6000, role = BossRole.SUMMONER, tier = 3
    )

    val GOOD_GAME = BossDef(
        "good_game", "GOOD GAME", "[GG]", "Heavy Siege Platform",
        "Enormous, armoured and slow. Every pattern it has is heavy. Patience " +
            "beats panic.",
        0xFFC8102E, 64f, 3000f, 40f, 30f, BossMove.CHASE, listOf(
            BossPhase(P1, 1f, 1.5f, listOf(
                Pattern.Radial(20, 170f, 13f, waves = 2, rotateDeg = 9f),
                Pattern.ShockRing(320f, 200f, 22f)
            ), "PHASE 1"),
            BossPhase(P2, 1.1f, 1.3f, listOf(
                Pattern.Blasts(6, 100f, 1.2f, 26f),
                Pattern.Radial(24, 180f, 13f, waves = 2, rotateDeg = 7f),
                Pattern.Summon("trojan", 2)
            ), "PHASE 2"),
            BossPhase(P3, 1.25f, 1.1f, listOf(
                Pattern.ShockRing(380f, 230f, 24f),
                Pattern.Spiral(4, 3.5f, 12f, 90f, 190f, 12f),
                Pattern.Blasts(8, 100f, 1.1f, 26f)
            ), "GAME OVER?")
        ), euros = 360, score = 6600, role = BossRole.TANK, tier = 3
    )

    /** The original twelve, levels 10–120. */
    val classics: List<BossDef> = listOf(
        BREACH, BOTMASTER, WORM, RANSOM, ROOTKIT, SYN_STORM,
        KERNEL_PANIC, EXFIL, WHITE_EYE, ZOMBIE, SPOOFER, GOOD_GAME
    )

    /** Boss Expansion Vol. 1 (owner, 2026-10-10), levels 130–240 in threat order. See BossExpansion.kt. */
    val expansion: List<BossDef> get() = BossExpansion.all

    /** Order of appearance: index = (level / 10 - 1) % size. */
    val roster: List<BossDef> by lazy { classics + expansion }

    fun byId(id: String): BossDef? = roster.firstOrNull { it.id == id }

    fun forLevel(level: Int): BossDef {
        val index = ((level / 10) - 1).coerceAtLeast(0)
        return roster[index % roster.size]
    }

    /** How many times the roster has looped (0 on the first pass). */
    fun cycleForLevel(level: Int): Int = ((level / 10) - 1).coerceAtLeast(0) / roster.size
}
