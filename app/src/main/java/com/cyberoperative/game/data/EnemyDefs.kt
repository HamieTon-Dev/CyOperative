package com.cyberoperative.game.data

/**
 * Data-driven enemy archetypes. Adding an enemy is a new [EnemyDef] in
 * [Enemies.all]; the AI, spawner, renderer and codex read everything from here.
 *
 * Names and concepts are carried over from CyOps TD where they existed
 * (Malware, Bot, SQL Injection, Exploit, Trojan, Worm, DDoS, Rootkit) and
 * re-designed for real-time arena combat.
 */
enum class AiKind {
    /** Walks straight at the player; damages on contact. */
    CHASER,
    /** Fast, weak, wobbling approach; arrives in packs. */
    SWARMER,
    /** Holds a preferred distance, strafes, fires when it has line of sight. */
    SHOOTER,
    /** Closes in, telegraphs a line, then dashes along it. */
    CHARGER,
    /** Stays far away, paints a laser sight, then fires a very fast shot. */
    SNIPER,
    /** Stationary; fires radial rings. */
    TURRET,
    /** Blinks to a new spot near the player, then fires a spread. */
    TELEPORTER,
    /**
     * *GLITCHED* (owner, 2026-10-08): corrupted code that keeps changing shape
     * and colour and picks a different attack every time — aimed bursts,
     * spreads, rings, or a blink to a new spot.
     */
    GLITCHED
}

enum class ShapeKind { CIRCLE, DIAMOND, TRIANGLE, SQUARE, HEXAGON, CROSS, PENTAGON, OCTAGON, STAR }

enum class AttackKind { NONE, AIMED, SPREAD, RADIAL }

data class EnemyDef(
    val id: String,
    val name: String,
    /** Short label drawn on the body (CyOps TD-style ASCII tag). */
    val tag: String,
    val codex: String,
    val shape: ShapeKind,
    /** ARGB colour. Hostiles always use the warm (red/orange/magenta/yellow) family. */
    val color: Long,
    val radius: Float,
    val baseHp: Float,
    /** World units per second at level 1. */
    val baseSpeed: Float,
    val contactDamage: Float,
    val ai: AiKind,
    val attack: AttackKind = AttackKind.NONE,
    val projectileDamage: Float = 0f,
    val projectileSpeed: Float = 0f,
    val projectileRadius: Float = 7f,
    /** Seconds between attacks at level 1. */
    val attackCooldown: Float = 0f,
    /** Projectiles per attack (spread/radial). */
    val projectileCount: Int = 1,
    val spreadDegrees: Float = 0f,
    /** Telegraph time before a charge/snipe/volley fires. */
    val windup: Float = 0f,
    val preferredRange: Float = 0f,
    /** Flat damage removed from each hit (before player crit). */
    val armor: Float = 0f,
    /** On death, spawns this many [splitInto] enemies. */
    val splitInto: String? = null,
    val splitCount: Int = 0,
    /** Spawned together as a pack of this size. */
    val packSize: Int = 1,
    val xp: Float,
    val euros: Int,
    val score: Int,
    /** First level at which the spawner may pick it. */
    val minLevel: Int,
    /** Relative spawn weight; 0 = never spawned directly (children only). */
    val weight: Float,
    /** Extra body detail that tells variant families apart at a glance. */
    val accent: AccentKind = AccentKind.NONE,
    /** Shape and colour flicker constantly; uses [AiKind.GLITCHED]. */
    val glitched: Boolean = false
)

object Enemies {

    val MALWARE = EnemyDef(
        id = "malware", name = "MALWARE CRAWLER", tag = "M",
        codex = "Malicious code that wants one thing: to reach you. Slow-witted, " +
            "relentless, and it hurts on contact.",
        shape = ShapeKind.CIRCLE, color = 0xFFFF2D55, radius = 18f,
        baseHp = 34f, baseSpeed = 92f, contactDamage = 12f, ai = AiKind.CHASER,
        xp = 2f, euros = 2, score = 10, minLevel = 1, weight = 10f
    )

    val SQL_INJECTOR = EnemyDef(
        id = "sql_injector", name = "SQL INJECTOR", tag = "SQL",
        codex = "Keeps its distance and fires malformed queries at you. Break line " +
            "of sight behind a server rack and it has to come looking.",
        shape = ShapeKind.DIAMOND, color = 0xFFFF7A1A, radius = 17f,
        baseHp = 26f, baseSpeed = 70f, contactDamage = 8f, ai = AiKind.SHOOTER,
        attack = AttackKind.AIMED, projectileDamage = 10f, projectileSpeed = 260f,
        attackCooldown = 2.2f, windup = 0.35f, preferredRange = 330f,
        xp = 2.5f, euros = 3, score = 14, minLevel = 1, weight = 7f
    )

    val BOT = EnemyDef(
        id = "bot", name = "BOT DRONE", tag = "B",
        codex = "One compromised machine is harmless. A botnet is not — they " +
            "always arrive in packs.",
        shape = ShapeKind.TRIANGLE, color = 0xFFFF2EC4, radius = 12f,
        baseHp = 12f, baseSpeed = 135f, contactDamage = 7f, ai = AiKind.SWARMER,
        packSize = 3, xp = 1f, euros = 1, score = 5, minLevel = 2, weight = 6f
    )

    val EXPLOIT = EnemyDef(
        id = "exploit", name = "EXPLOIT RUNNER", tag = "X",
        codex = "Finds a gap and rushes it. Watch for the targeting line, then " +
            "step out of the way.",
        shape = ShapeKind.TRIANGLE, color = 0xFFFFD426, radius = 17f,
        baseHp = 30f, baseSpeed = 80f, contactDamage = 16f, ai = AiKind.CHARGER,
        attackCooldown = 2.8f, windup = 0.75f, projectileSpeed = 620f,
        preferredRange = 260f,
        xp = 3f, euros = 3, score = 16, minLevel = 3, weight = 5f
    )

    val TROJAN = EnemyDef(
        id = "trojan", name = "TROJAN BRUTE", tag = "T",
        codex = "Disguised as something harmless and wrapped in armour. Every hit " +
            "loses a little to its plating — heavy shots work best.",
        shape = ShapeKind.SQUARE, color = 0xFFC8102E, radius = 27f,
        baseHp = 120f, baseSpeed = 58f, contactDamage = 22f, ai = AiKind.CHASER,
        armor = 3f, xp = 6f, euros = 6, score = 30, minLevel = 5, weight = 3f
    )

    val PHISHER = EnemyDef(
        id = "phisher", name = "PHISH LURE", tag = "@",
        codex = "Throws a fan of bait. Don't take it — move between the lines.",
        shape = ShapeKind.HEXAGON, color = 0xFFFF7A1A, radius = 18f,
        baseHp = 34f, baseSpeed = 64f, contactDamage = 8f, ai = AiKind.SHOOTER,
        attack = AttackKind.SPREAD, projectileDamage = 9f, projectileSpeed = 230f,
        attackCooldown = 2.8f, projectileCount = 3, spreadDegrees = 34f,
        windup = 0.45f, preferredRange = 300f,
        xp = 3.5f, euros = 4, score = 18, minLevel = 7, weight = 4f
    )

    val SNIFFER = EnemyDef(
        id = "sniffer", name = "PACKET SNIFFER", tag = "<o>",
        codex = "Watches from the far side of the network. When the red sight " +
            "line locks, one very fast packet follows.",
        shape = ShapeKind.DIAMOND, color = 0xFFFF4060, radius = 16f,
        baseHp = 28f, baseSpeed = 55f, contactDamage = 6f, ai = AiKind.SNIPER,
        attack = AttackKind.AIMED, projectileDamage = 20f, projectileSpeed = 640f,
        projectileRadius = 6f, attackCooldown = 3.6f, windup = 1.0f,
        preferredRange = 520f,
        xp = 4f, euros = 4, score = 22, minLevel = 9, weight = 3f
    )

    val WORM = EnemyDef(
        id = "worm", name = "WORM", tag = "~>",
        codex = "Self-replicating. Destroy it and it splits into smaller copies.",
        shape = ShapeKind.CIRCLE, color = 0xFFFF5A36, radius = 22f,
        baseHp = 60f, baseSpeed = 82f, contactDamage = 14f, ai = AiKind.CHASER,
        splitInto = "wormlet", splitCount = 2,
        xp = 3f, euros = 3, score = 18, minLevel = 12, weight = 3f
    )

    val WORMLET = EnemyDef(
        id = "wormlet", name = "WORM FRAGMENT", tag = "~",
        codex = "A copy of a copy. Fast and fragile.",
        shape = ShapeKind.CIRCLE, color = 0xFFFF8A66, radius = 12f,
        baseHp = 18f, baseSpeed = 128f, contactDamage = 8f, ai = AiKind.SWARMER,
        xp = 1f, euros = 1, score = 6, minLevel = 999, weight = 0f
    )

    val DDOS_NODE = EnemyDef(
        id = "ddos_node", name = "DDoS NODE", tag = "<<>>",
        codex = "A stationary flood source. Rings of packets pour out of it on a " +
            "steady rhythm — find the gaps.",
        shape = ShapeKind.CROSS, color = 0xFFFF2EC4, radius = 22f,
        baseHp = 80f, baseSpeed = 0f, contactDamage = 10f, ai = AiKind.TURRET,
        attack = AttackKind.RADIAL, projectileDamage = 9f, projectileSpeed = 170f,
        attackCooldown = 3.0f, projectileCount = 10, windup = 0.5f,
        xp = 5f, euros = 5, score = 26, minLevel = 15, weight = 2.5f
    )

    val ROOTKIT = EnemyDef(
        id = "rootkit", name = "ROOTKIT PHANTOM", tag = "r00t",
        codex = "Hides deep in the system and reappears somewhere else. It always " +
            "flickers before it fires.",
        shape = ShapeKind.HEXAGON, color = 0xFFB0304A, radius = 18f,
        baseHp = 46f, baseSpeed = 0f, contactDamage = 10f, ai = AiKind.TELEPORTER,
        attack = AttackKind.SPREAD, projectileDamage = 11f, projectileSpeed = 240f,
        attackCooldown = 3.4f, projectileCount = 5, spreadDegrees = 60f, windup = 0.6f,
        preferredRange = 240f,
        xp = 5f, euros = 5, score = 28, minLevel = 20, weight = 2.5f
    )

    // --- *GLITCHED* (later levels) --------------------------------------------
    val GLITCH_DAEMON = EnemyDef(
        id = "glitch_daemon", name = "*GLITCHED* DAEMON", tag = "#?!",
        codex = "A background process that broke in half. Never the same shape twice; " +
            "fires bursts, spreads or rings at random, and blinks around the room.",
        shape = ShapeKind.STAR, color = 0xFFFF2EC4, radius = 19f,
        baseHp = 90f, baseSpeed = 84f, contactDamage = 14f, ai = AiKind.GLITCHED,
        projectileDamage = 11f, projectileSpeed = 250f, attackCooldown = 2.2f, windup = 0.45f,
        preferredRange = 260f, armor = 1f,
        xp = 7f, euros = 9, score = 45, minLevel = 25, weight = 1.2f, glitched = true
    )
    val GLITCH_SHARD = EnemyDef(
        id = "glitch_shard", name = "*GLITCHED* SHARD", tag = "%#",
        codex = "A splinter of corrupted memory. Quick, erratic, and it never attacks the same way twice.",
        shape = ShapeKind.TRIANGLE, color = 0xFF7DF9FF, radius = 15f,
        baseHp = 55f, baseSpeed = 122f, contactDamage = 11f, ai = AiKind.GLITCHED,
        projectileDamage = 9f, projectileSpeed = 290f, attackCooldown = 1.7f, windup = 0.35f,
        preferredRange = 220f, packSize = 2,
        xp = 5f, euros = 7, score = 34, minLevel = 32, weight = 1f, glitched = true
    )
    val GLITCH_HYDRA = EnemyDef(
        id = "glitch_hydra", name = "*GLITCHED* HYDRA", tag = "<#>",
        codex = "Three corrupted processes fused into one. Huge, unstable, and every attack pattern at once.",
        shape = ShapeKind.OCTAGON, color = 0xFFFFD426, radius = 28f,
        baseHp = 260f, baseSpeed = 58f, contactDamage = 24f, ai = AiKind.GLITCHED,
        projectileDamage = 13f, projectileSpeed = 220f, attackCooldown = 2.6f, windup = 0.6f,
        preferredRange = 300f, armor = 3f, splitInto = "glitch_shard", splitCount = 2,
        xp = 14f, euros = 18, score = 90, minLevel = 42, weight = 0.8f, glitched = true
    )

    /** The eleven hand-made originals. */
    val originals: List<EnemyDef> = listOf(
        MALWARE, SQL_INJECTOR, BOT, EXPLOIT, TROJAN, PHISHER, SNIFFER, WORM, WORMLET,
        DDOS_NODE, ROOTKIT
    )

    /** Originals plus the 338 strain × code variants (see [EnemyVariants]). */
    val glitched: List<EnemyDef> = listOf(GLITCH_DAEMON, GLITCH_SHARD, GLITCH_HYDRA)

    // --- Boss Expansion adds (never spawned on their own; bosses bring them) ---

    /** Worm Queen's brood: a small spiked red orb that rushes in packs. */
    val SWARMLING = EnemyDef(
        id = "swarmling", name = "SWARMLING", tag = "*",
        codex = "Hatched from the Worm Queen's eggs. Tiny, spiked and never alone.",
        shape = ShapeKind.STAR, color = 0xFFFF2D6A, radius = 15f,
        baseHp = 14f, baseSpeed = 150f, contactDamage = 6f, ai = AiKind.SWARMER,
        xp = 1f, euros = 1, score = 6, minLevel = 999, weight = 0f, accent = AccentKind.SPIKES
    )

    /** Glitch Forge's decoy: a hologram copy of the forge that shoots; one hit pops it. */
    val HOLO_CLONE = EnemyDef(
        id = "holo_clone", name = "HOLO DECOY", tag = "[]",
        codex = "A hologram the Glitch Forge projects to confuse you. It shoots like the real thing, " +
            "but a single hit pops it.",
        shape = ShapeKind.SQUARE, color = 0xFFFF3BD0, radius = 22f,
        baseHp = 1f, baseSpeed = 70f, contactDamage = 8f, ai = AiKind.SHOOTER,
        attack = AttackKind.SPREAD, projectileDamage = 10f, projectileSpeed = 260f, projectileCount = 3, spreadDegrees = 30f,
        attackCooldown = 2.2f, windup = 0.5f, preferredRange = 300f,
        xp = 0f, euros = 0, score = 4, minLevel = 999, weight = 0f, accent = AccentKind.CORE
    )

    /** Botnet Monarch's escort: an armoured drone orb that rides its orbit ring and shoots. */
    val ORBIT_DRONE = EnemyDef(
        id = "orbit_drone", name = "MONARCH DRONE", tag = "o",
        codex = "Rides the Botnet Monarch's orbit ring and fires on its command. Shoot them down to " +
            "break the ring.",
        shape = ShapeKind.CIRCLE, color = 0xFFFF3A3A, radius = 18f,
        baseHp = 30f, baseSpeed = 0f, contactDamage = 10f, ai = AiKind.SHOOTER,
        attack = AttackKind.AIMED, projectileDamage = 11f, projectileSpeed = 300f,
        attackCooldown = 2.6f, windup = 0.45f, preferredRange = 2000f,
        xp = 1f, euros = 1, score = 8, minLevel = 999, weight = 0f, accent = AccentKind.SPIKES
    )

    /** Circuit Hydra's extra beam head; hits on it drain the hydra's shared HP bar. */
    val HYDRA_HEAD = EnemyDef(
        id = "hydra_head", name = "HYDRA HEAD", tag = "Θ",
        codex = "A beam head split off the Circuit Hydra. Damage dealt to it hurts the hydra itself.",
        shape = ShapeKind.CIRCLE, color = 0xFFFF4A2A, radius = 30f,
        baseHp = 60f, baseSpeed = 0f, contactDamage = 14f, ai = AiKind.CHASER,
        xp = 0f, euros = 0, score = 0, minLevel = 999, weight = 0f
    )

    /** Expansion adds, appended last so existing indices (co-op wire, saves) never shift. */
    val expansion: List<EnemyDef> = listOf(SWARMLING, HOLO_CLONE, ORBIT_DRONE, HYDRA_HEAD)

    val all: List<EnemyDef> = originals + glitched + EnemyVariants.all + expansion

    private val byId = all.associateBy { it.id }

    fun byId(id: String): EnemyDef = byId[id] ?: error("Unknown enemy $id")

    /** Enemies the spawner may choose at [level]. */
    fun pool(level: Int): List<EnemyDef> = all.filter { it.weight > 0f && level >= it.minLevel }
}

/**
 * Elite modifiers (§45). An elite is a normal enemy with one modifier rolled on.
 */
enum class EliteModifier(
    val label: String,
    val color: Long,
    val hpMul: Float,
    val speedMul: Float,
    val attackRateMul: Float,
    val damageTakenMul: Float
) {
    ENCRYPTED("ENCRYPTED", 0xFF2EE6FF, 1.4f, 1f, 1f, 0.6f),
    OVERCLOCKED("OVERCLOCKED", 0xFFFFE14D, 1.3f, 1.35f, 1.35f, 1f),
    CORRUPTED("CORRUPTED", 0xFF9B4DFF, 1.5f, 1f, 1f, 1f),
    ARMORED("ARMORED", 0xFFB8C4D6, 2.4f, 0.85f, 1f, 1f),
    REPLICATING("REPLICATING", 0xFF5CFF7A, 1.4f, 1f, 1f, 1f),
    VOLATILE("VOLATILE", 0xFFFF9A1A, 1.4f, 1.1f, 1f, 1f);

    companion object {
        const val REWARD_MUL = 2.5f
        const val SIZE_MUL = 1.22f
        const val BASE_HP_MUL = 1.5f
    }
}
