package com.cyberoperative.game.data

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The wider threat roster (owner, 2026-10-08: "at least +300 different enemy
 * types"). Every type is a STRAIN (how it fights: AI, attack pattern, body
 * shape, base stats) crossed with a CODE family (a malware lineage that
 * twists the stats, adds a behaviour such as splitting, packs or extra shots,
 * and gives it a colour and a visual accent). 26 strains × 13 codes = 338
 * distinct enemies, each with its own name, tag, codex entry and unlock level.
 *
 * Variants unlock gradually: early levels stay mostly on the hand-made
 * originals, and the roster keeps widening deep into a run.
 */
enum class AccentKind { NONE, RING, SPIKES, ORBITERS, CORE, STRIPES, HORNS, ANTENNA, PLATES, HALO, CRACKS, VISOR, BITS }

object EnemyVariants {

    private class Strain(
        val id: String, val noun: String, val tag: String, val blurb: String,
        val ai: AiKind, val shape: ShapeKind, val radius: Float,
        val hp: Float, val speed: Float, val contact: Float,
        val attack: AttackKind = AttackKind.NONE, val projDamage: Float = 0f, val projSpeed: Float = 0f,
        val projRadius: Float = 7f, val cooldown: Float = 0f, val count: Int = 1, val spread: Float = 0f,
        val windup: Float = 0f, val range: Float = 0f, val armor: Float = 0f, val pack: Int = 1,
        val xp: Float, val euros: Int, val score: Int, val minLevel: Int
    )

    private class Code(
        val id: String, val adjective: String, val mark: String, val blurb: String,
        val color: Long, val accent: AccentKind,
        val hp: Float = 1f, val speed: Float = 1f, val damage: Float = 1f, val cooldown: Float = 1f,
        val size: Float = 1f, val armor: Float = 0f, val extraShots: Int = 0, val pack: Int = 0,
        val splits: Boolean = false, val reward: Float = 1f, val projSpeed: Float = 1f,
        val shapeOverride: ShapeKind? = null, val minLevel: Int
    )

    private val strains = listOf(
        // --- Melee chasers -------------------------------------------------
        Strain("crawler", "CRAWLER", "C", "crawls straight for you and bites on contact.",
            AiKind.CHASER, ShapeKind.CIRCLE, 17f, 36f, 90f, 12f, xp = 2f, euros = 2, score = 11, minLevel = 2),
        Strain("leech", "LEECH", "L", "is small and quick, and latches on before you can react.",
            AiKind.CHASER, ShapeKind.PENTAGON, 14f, 24f, 118f, 9f, xp = 1.6f, euros = 2, score = 9, minLevel = 4),
        Strain("brute", "BRUTE", "BR", "is a slab of hostile code that soaks damage and hits hard.",
            AiKind.CHASER, ShapeKind.SQUARE, 27f, 130f, 56f, 22f, armor = 3f, xp = 6f, euros = 6, score = 30, minLevel = 8),
        Strain("husk", "HUSK", "H", "lumbers forward, shrugging off the first hits.",
            AiKind.CHASER, ShapeKind.OCTAGON, 22f, 80f, 70f, 15f, armor = 1.5f, xp = 4f, euros = 4, score = 20, minLevel = 6),
        Strain("grub", "GRUB", "g", "is soft and slow, but there is always another one.",
            AiKind.CHASER, ShapeKind.CIRCLE, 15f, 28f, 74f, 10f, pack = 2, xp = 1.4f, euros = 1, score = 8, minLevel = 3),
        Strain("golem", "GOLEM", "GL", "is a towering stack of compromised services. Slow. Unstoppable.",
            AiKind.CHASER, ShapeKind.OCTAGON, 32f, 220f, 46f, 28f, armor = 5f, xp = 10f, euros = 10, score = 50, minLevel = 18),
        // --- Swarms ----------------------------------------------------------
        Strain("mite", "MITE", "m", "arrives in a cloud of tiny, fast, fragile copies.",
            AiKind.SWARMER, ShapeKind.TRIANGLE, 11f, 10f, 140f, 6f, pack = 4, xp = 0.8f, euros = 1, score = 4, minLevel = 3),
        Strain("gnat", "GNAT", "^", "zig-zags in fast enough to be hard to pin down.",
            AiKind.SWARMER, ShapeKind.DIAMOND, 12f, 14f, 150f, 7f, pack = 3, xp = 1f, euros = 1, score = 5, minLevel = 5),
        Strain("nanite", "NANITE", "n", "is a self-replicating speck. One is nothing; forty are a problem.",
            AiKind.SWARMER, ShapeKind.HEXAGON, 10f, 9f, 132f, 5f, pack = 5, xp = 0.7f, euros = 1, score = 4, minLevel = 9),
        Strain("locust", "LOCUST", "~^", "strips a sector bare, swarming anything that moves.",
            AiKind.SWARMER, ShapeKind.STAR, 13f, 18f, 128f, 8f, pack = 3, xp = 1.2f, euros = 1, score = 6, minLevel = 12),
        // --- Ranged ----------------------------------------------------------
        Strain("spitter", "SPITTER", "sp", "keeps its distance and spits packets at you.",
            AiKind.SHOOTER, ShapeKind.DIAMOND, 16f, 28f, 68f, 8f, AttackKind.AIMED, 10f, 260f,
            cooldown = 2.2f, windup = 0.35f, range = 320f, xp = 2.5f, euros = 3, score = 14, minLevel = 2),
        Strain("gunner", "GUNNER", "gn", "fires quick double taps from mid range.",
            AiKind.SHOOTER, ShapeKind.PENTAGON, 17f, 34f, 64f, 8f, AttackKind.AIMED, 8f, 300f,
            cooldown = 1.6f, count = 2, windup = 0.3f, range = 300f, xp = 3f, euros = 3, score = 16, minLevel = 6),
        Strain("scatter", "SCATTERER", "<:", "sprays a fan of packets across your path.",
            AiKind.SHOOTER, ShapeKind.HEXAGON, 18f, 36f, 60f, 8f, AttackKind.SPREAD, 8f, 230f,
            cooldown = 2.6f, count = 4, spread = 50f, windup = 0.45f, range = 290f, xp = 3.5f, euros = 4, score = 18, minLevel = 7),
        Strain("mortar", "MORTAR", "(O)", "lobs slow, heavy payloads that are easy to see and hard to tank.",
            AiKind.SHOOTER, ShapeKind.OCTAGON, 21f, 60f, 44f, 10f, AttackKind.AIMED, 22f, 150f, projRadius = 13f,
            cooldown = 3.2f, windup = 0.6f, range = 380f, xp = 4.5f, euros = 5, score = 24, minLevel = 11),
        Strain("repeater", "REPEATER", ">>", "unloads a tight burst, then has to cool down.",
            AiKind.SHOOTER, ShapeKind.TRIANGLE, 16f, 30f, 66f, 8f, AttackKind.AIMED, 7f, 320f,
            cooldown = 2.4f, count = 3, windup = 0.4f, range = 310f, xp = 3.2f, euros = 3, score = 17, minLevel = 9),
        // --- Chargers --------------------------------------------------------
        Strain("ram", "RAM", "R>", "lines you up, then rams.",
            AiKind.CHARGER, ShapeKind.TRIANGLE, 18f, 34f, 78f, 17f, projSpeed = 600f,
            cooldown = 2.8f, windup = 0.75f, range = 260f, xp = 3f, euros = 3, score = 16, minLevel = 4),
        Strain("lancer", "LANCER", "->", "charges from far away along a long, straight line.",
            AiKind.CHARGER, ShapeKind.DIAMOND, 16f, 30f, 84f, 15f, projSpeed = 720f,
            cooldown = 2.4f, windup = 0.65f, range = 380f, xp = 3.2f, euros = 3, score = 17, minLevel = 10),
        Strain("bull", "BULL", "B>", "is a heavy charger. Slow to wind up, brutal on impact.",
            AiKind.CHARGER, ShapeKind.SQUARE, 25f, 110f, 60f, 26f, projSpeed = 540f, armor = 2f,
            cooldown = 3.4f, windup = 0.95f, range = 240f, xp = 6f, euros = 6, score = 30, minLevel = 14),
        // --- Snipers ---------------------------------------------------------
        Strain("marksman", "MARKSMAN", "<+>", "paints a sight line, then fires one very fast shot.",
            AiKind.SNIPER, ShapeKind.DIAMOND, 15f, 26f, 54f, 6f, AttackKind.AIMED, 20f, 640f,
            cooldown = 3.6f, windup = 1.1f, range = 520f, xp = 4f, euros = 4, score = 22, minLevel = 10),
        Strain("stalker", "STALKER", "<.>", "snipes from the shadows, a little faster each time.",
            AiKind.SNIPER, ShapeKind.STAR, 15f, 32f, 60f, 6f, AttackKind.AIMED, 17f, 700f,
            cooldown = 3f, windup = 0.95f, range = 480f, xp = 4.2f, euros = 4, score = 23, minLevel = 16),
        // --- Turrets ---------------------------------------------------------
        Strain("beacon", "BEACON", "(*)", "plants itself and pulses rings of packets.",
            AiKind.TURRET, ShapeKind.CROSS, 21f, 70f, 0f, 10f, AttackKind.RADIAL, 8f, 170f,
            cooldown = 2.8f, count = 8, windup = 0.5f, xp = 4.5f, euros = 5, score = 24, minLevel = 12),
        Strain("spire", "SPIRE", "/|\\", "is a slow-firing tower whose rings are dense and wide.",
            AiKind.TURRET, ShapeKind.OCTAGON, 24f, 100f, 0f, 10f, AttackKind.RADIAL, 9f, 140f,
            cooldown = 3.6f, count = 12, windup = 0.7f, xp = 5.5f, euros = 6, score = 28, minLevel = 17),
        Strain("sentry", "SENTRY", "[o]", "is a fixed gun that tracks you and fires aimed bursts.",
            AiKind.TURRET, ShapeKind.SQUARE, 20f, 80f, 0f, 10f, AttackKind.AIMED, 9f, 280f,
            cooldown = 2f, count = 2, windup = 0.45f, xp = 4.5f, euros = 5, score = 24, minLevel = 13),
        // --- Teleporters -----------------------------------------------------
        Strain("blinker", "BLINKER", "*.", "vanishes, reappears beside you, and fires a spread.",
            AiKind.TELEPORTER, ShapeKind.HEXAGON, 17f, 40f, 0f, 10f, AttackKind.SPREAD, 10f, 240f,
            cooldown = 3.2f, count = 4, spread = 55f, windup = 0.55f, range = 230f, xp = 4.5f, euros = 5, score = 26, minLevel = 15),
        Strain("phantom", "PHANTOM", "?", "phases in at long range and fires a single heavy bolt.",
            AiKind.TELEPORTER, ShapeKind.PENTAGON, 16f, 36f, 0f, 10f, AttackKind.AIMED, 18f, 360f,
            cooldown = 3f, windup = 0.6f, range = 300f, xp = 4.5f, euros = 5, score = 26, minLevel = 19),
        Strain("glitch", "GLITCH", "#!", "stutters through space and bursts into a ring when it lands.",
            AiKind.TELEPORTER, ShapeKind.STAR, 17f, 44f, 0f, 10f, AttackKind.RADIAL, 8f, 200f,
            cooldown = 3.4f, count = 7, windup = 0.5f, range = 210f, xp = 5f, euros = 5, score = 28, minLevel = 22)
    )

    private val codes = listOf(
        Code("poly", "POLYMORPHIC", "", "It rewrites itself to move faster, at the cost of some hull.",
            0xFFFF4A6A, AccentKind.STRIPES, hp = 0.85f, speed = 1.25f, minLevel = 0),
        Code("enc", "ENCRYPTED", "", "Wrapped in encryption: armored, hard to crack.",
            0xFFFF6A3D, AccentKind.PLATES, hp = 1.2f, armor = 2.5f, reward = 1.2f, minLevel = 3),
        Code("botnet", "BOTNET", "", "Never alone: it brings friends.",
            0xFFFF2E9A, AccentKind.ANTENNA, hp = 0.8f, pack = 1, minLevel = 2),
        Code("ransom", "RANSOM", "$", "Bloated and greedy. Worth a lot of € if you can bring it down.",
            0xFFFFB020, AccentKind.CORE, hp = 1.5f, speed = 0.9f, size = 1.1f, reward = 1.8f, minLevel = 6),
        Code("zeroday", "ZERO-DAY", "0", "An unpatched exploit: everything it does hits harder.",
            0xFFFF2D3D, AccentKind.HORNS, damage = 1.35f, cooldown = 0.9f, reward = 1.3f, minLevel = 10),
        Code("adware", "ADWARE", "+", "Spams more projectiles than it has any right to.",
            0xFFFF7AC8, AccentKind.BITS, hp = 0.9f, extraShots = 2, cooldown = 1.1f, minLevel = 8),
        Code("spyware", "SPYWARE", "o", "Watches from further away and shoots faster packets.",
            0xFFFF8F5A, AccentKind.VISOR, projSpeed = 1.3f, speed = 1.05f, minLevel = 5),
        Code("cryptojack", "CRYPTOJACK", "#", "Overclocked on stolen cycles: quicker attacks, hotter shell.",
            0xFFFFC94D, AccentKind.HALO, cooldown = 0.75f, hp = 0.9f, reward = 1.2f, minLevel = 12),
        Code("forkbomb", "FORKBOMB", "%", "Kill it and it forks into fragments.",
            0xFFFF5A36, AccentKind.CRACKS, hp = 0.9f, splits = true, minLevel = 14),
        Code("keylogger", "KEYLOGGER", "k", "Twitchy and precise. Smaller, faster, harder to hit.",
            0xFFFF4D8D, AccentKind.ORBITERS, size = 0.85f, speed = 1.15f, hp = 0.8f, minLevel = 9),
        Code("backdoor", "BACKDOOR", "=", "Reinforced at every hinge. Big, slow and very hard to shift.",
            0xFFD0303F, AccentKind.RING, hp = 1.9f, speed = 0.8f, size = 1.18f, armor = 1.5f, reward = 1.5f, minLevel = 16),
        Code("stealth", "STEALTH", "~", "Thin and quiet: it closes the gap before you notice.",
            0xFFC24A7A, AccentKind.SPIKES, hp = 0.75f, speed = 1.35f, damage = 1.1f, minLevel = 20),
        Code("kernel", "KERNEL-MODE", "K", "Runs with full privileges. Elite-grade toughness and damage.",
            0xFFFF1F1F, AccentKind.HALO, hp = 2.3f, damage = 1.3f, size = 1.15f, armor = 3f, reward = 2.2f,
            shapeOverride = ShapeKind.STAR, minLevel = 30)
    )

    /** Spawn weight of each variant (originals use 2.5–10). */
    const val VARIANT_WEIGHT = 0.45f

    val all: List<EnemyDef> = buildList {
        for (code in codes) for (s in strains) add(make(s, code))
    }

    private fun make(s: Strain, c: Code): EnemyDef {
        val ranged = s.attack != AttackKind.NONE
        val stationary = s.ai == AiKind.TURRET || s.ai == AiKind.TELEPORTER
        val count = when {
            !ranged -> 1
            s.attack == AttackKind.RADIAL -> s.count + c.extraShots * 2
            else -> s.count + c.extraShots
        }
        val tag = (c.mark + s.tag).take(5)
        val reward = c.reward
        return EnemyDef(
            id = "${c.id}_${s.id}",
            name = "${c.adjective} ${s.noun}",
            tag = tag,
            codex = "A ${s.noun.lowercase()} that ${s.blurb} ${c.blurb}",
            shape = c.shapeOverride ?: s.shape,
            color = c.color,
            radius = s.radius * c.size,
            baseHp = (s.hp * c.hp).roundToInt().toFloat().coerceAtLeast(6f),
            baseSpeed = if (stationary) 0f else s.speed * c.speed,
            contactDamage = s.contact * c.damage,
            ai = s.ai,
            attack = s.attack,
            projectileDamage = s.projDamage * c.damage,
            // Chargers use projectileSpeed as their dash speed.
            projectileSpeed = s.projSpeed * (if (s.ai == AiKind.CHARGER) c.speed.coerceIn(0.9f, 1.2f) else c.projSpeed),
            projectileRadius = s.projRadius,
            attackCooldown = s.cooldown * c.cooldown,
            projectileCount = count,
            spreadDegrees = if (s.attack == AttackKind.SPREAD) s.spread + c.extraShots * 8f else s.spread,
            windup = s.windup,
            preferredRange = s.range * (if (c.id == "spyware") 1.2f else 1f),
            armor = s.armor + c.armor,
            splitInto = if (c.splits && !stationary) "wormlet" else null,
            splitCount = if (c.splits && !stationary) 2 else 0,
            packSize = if (stationary) 1 else s.pack + c.pack,
            xp = s.xp * reward,
            euros = max(1, (s.euros * reward).roundToInt()),
            score = (s.score * reward).roundToInt(),
            minLevel = s.minLevel + c.minLevel,
            weight = VARIANT_WEIGHT,
            accent = c.accent
        )
    }
}
