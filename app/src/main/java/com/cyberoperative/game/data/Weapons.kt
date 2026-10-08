package com.cyberoperative.game.data

/**
 * Auto-weapon arsenal (owner, 2026-10-08: "at least 5 new weapon types of each
 * rarity"). Every weapon here is data on top of ten engine behaviours, so
 * adding one is a single row. They all fire on their own cooldown, moving or
 * not, unless [WeaponSpec.stillOnly] is set.
 */
enum class WeaponKind {
    /** Bolts at the nearest threat (optional spread, homing, splash). */
    VOLLEY,
    /** Bolts in every direction at once. */
    RING,
    /** Bolts emitted one after another while the angle turns. */
    SPIRAL,
    /** Instant blast around the operative. */
    NOVA,
    /** Instant beam toward the target, damaging everything along it. */
    LASER,
    /** Lightning jumping between nearby threats. */
    ARC,
    /** Marks random threats; damage lands after a short telegraph. */
    STRIKE,
    /** Mines dropped at the operative's feet. */
    MINES,
    /** A lingering damaging zone left where the operative stands. */
    FIELD,
    /** A piercing disc that flies out and returns. */
    BOOMERANG
}

data class WeaponSpec(
    val id: String,
    val name: String,
    val glyph: String,
    val rarity: Rarity,
    val kind: WeaponKind,
    val description: String,
    val maxLevel: Int,
    /** Seconds between uses at level 1 (−12% per extra level). */
    val cooldown: Float,
    /** Bolts / targets / beams at level 1, and how many each level adds. */
    val count: Int,
    val countPerLevel: Int = 0,
    /** Damage as a multiple of the operative's damage (+35% per extra level). DPS for FIELD. */
    val damage: Float,
    val speed: Float = 1f,
    val radius: Float = 0f,
    val spreadDegrees: Float = 0f,
    val pierce: Int = 0,
    val homing: Float = 0f,
    val splash: Float = 0f,
    val length: Float = 0f,
    val throughWalls: Boolean = false,
    val stillOnly: Boolean = false,
    val color: Long
) {
    fun cooldownAt(level: Int): Float = cooldown * Math.pow(0.88, (level - 1).toDouble()).toFloat()
    fun countAt(level: Int): Int = count + countPerLevel * (level - 1)
    fun damageAt(level: Int): Float = damage * (1f + 0.35f * (level - 1))

    fun effect(level: Int): String {
        val pct = (damageAt(level) * 100).toInt()
        val cd = "%.1f".format(cooldownAt(level))
        val n = countAt(level)
        return when (kind) {
            WeaponKind.VOLLEY -> "$n bolt${if (n > 1) "s" else ""} · $pct% · every ${cd}s"
            WeaponKind.RING -> "Ring of $n · $pct% · every ${cd}s"
            WeaponKind.SPIRAL -> "Spiral of $n · $pct% · every ${cd}s"
            WeaponKind.NOVA -> "Blast r${radius.toInt()} · $pct% · every ${cd}s"
            WeaponKind.LASER -> "$n beam${if (n > 1) "s" else ""} · $pct% · every ${cd}s"
            WeaponKind.ARC -> "Hits $n threats · $pct% · every ${cd}s"
            WeaponKind.STRIKE -> "$n strike${if (n > 1) "s" else ""} · $pct% · every ${cd}s"
            WeaponKind.MINES -> "Mine $pct% · up to ${n + 2} · every ${cd}s"
            WeaponKind.FIELD -> "Zone r${radius.toInt()} · $pct%/s · every ${cd}s"
            WeaponKind.BOOMERANG -> "$n disc${if (n > 1) "s" else ""} · $pct% · every ${cd}s"
        }
    }
}

object Weapons {

    val all: List<WeaponSpec> = listOf(
        // --- COMMON ------------------------------------------------------------
        WeaponSpec("ping_blaster", "PING BLASTER", "·>", Rarity.COMMON, WeaponKind.VOLLEY,
            "A quick ping fired at the nearest threat, moving or not.", 3,
            cooldown = 1.3f, count = 1, damage = 0.6f, speed = 1.3f, color = 0xFF9FF6FF),
        WeaponSpec("spam_shotgun", "SPAM SHOTGUN", ":::", Rarity.COMMON, WeaponKind.VOLLEY,
            "A short-range blast of junk packets.", 3,
            cooldown = 2f, count = 4, countPerLevel = 1, damage = 0.35f, spreadDegrees = 40f, speed = 0.9f, color = 0xFFB8C4D6),
        WeaponSpec("bit_spinner", "BIT SPINNER", "@", Rarity.COMMON, WeaponKind.SPIRAL,
            "Spins out a short spiral of bits.", 3,
            cooldown = 3f, count = 6, countPerLevel = 2, damage = 0.3f, speed = 0.8f, color = 0xFFDDE6F0),
        WeaponSpec("static_shock", "STATIC SHOCK", "*", Rarity.COMMON, WeaponKind.NOVA,
            "Discharges static into anything touching you.", 3,
            cooldown = 2.6f, count = 1, damage = 0.7f, radius = 90f, color = 0xFFCFE8FF),
        WeaponSpec("cable_whip", "CABLE WHIP", "~-", Rarity.COMMON, WeaponKind.LASER,
            "A short lashing line toward the nearest threat.", 3,
            cooldown = 2.2f, count = 1, damage = 0.8f, length = 170f, color = 0xFFB8F0FF),
        // --- UNCOMMON ----------------------------------------------------------
        WeaponSpec("heartbeat_pulse", "HEARTBEAT PULSE", "(·)", Rarity.UNCOMMON, WeaponKind.RING,
            "Every beat, a ring of packets bursts outward.", 3,
            cooldown = 3f, count = 8, countPerLevel = 2, damage = 0.45f, speed = 0.85f, color = 0xFF5CFFB0),
        WeaponSpec("captcha_mines", "CAPTCHA MINES", "[?]", Rarity.UNCOMMON, WeaponKind.MINES,
            "Prove you're human: robots that step on these explode.", 3,
            cooldown = 1.6f, count = 1, countPerLevel = 1, damage = 1.4f, splash = 75f, color = 0xFF7DFFB2),
        WeaponSpec("traceroute", "TRACEROUTE", "~>", Rarity.UNCOMMON, WeaponKind.VOLLEY,
            "Seeker packets that trace a route to their target.", 3,
            cooldown = 2.2f, count = 2, countPerLevel = 1, damage = 0.7f, homing = 6f, speed = 0.8f, color = 0xFF00FF9C),
        WeaponSpec("packet_fire", "PACKET FIRE", "^^", Rarity.UNCOMMON, WeaponKind.FIELD,
            "Leaves a patch of burning packets where you stand.", 3,
            cooldown = 3.5f, count = 1, damage = 0.45f, radius = 80f, color = 0xFF3CFF8A),
        WeaponSpec("boomerang_byte", "BOOMERANG BYTE", "<)", Rarity.UNCOMMON, WeaponKind.BOOMERANG,
            "A disc that slices out and comes back, hitting everything twice.", 3,
            cooldown = 2.6f, count = 1, countPerLevel = 1, damage = 0.9f, speed = 0.85f, color = 0xFF66FFC2),
        // --- BLUE RARE -----------------------------------------------------------
        WeaponSpec("bluescreen_nova", "BLUESCREEN NOVA", "[:(]", Rarity.RARE, WeaponKind.NOVA,
            "Crashes every threat nearby in a flash of blue.", 3,
            cooldown = 4f, count = 1, damage = 1.5f, radius = 150f, color = 0xFF2E9BFF),
        WeaponSpec("subnet_laser", "SUBNET LASER", "-==", Rarity.RARE, WeaponKind.LASER,
            "A long beam that cuts down a whole subnet in a line.", 3,
            cooldown = 2.8f, count = 1, damage = 1.6f, length = 560f, color = 0xFF4DB2FF),
        WeaponSpec("cluster_bomb", "CLUSTER BOMB", "(o)", Rarity.RARE, WeaponKind.VOLLEY,
            "A slow, heavy shell that bursts over a wide area.", 3,
            cooldown = 3.2f, count = 1, countPerLevel = 1, damage = 2f, splash = 95f, speed = 0.6f, color = 0xFF5BA8FF),
        WeaponSpec("data_spiral", "DATA SPIRAL", "@@", Rarity.RARE, WeaponKind.SPIRAL,
            "A long spiral of data bolts that sweeps the whole room.", 3,
            cooldown = 3.6f, count = 12, countPerLevel = 4, damage = 0.5f, speed = 0.9f, color = 0xFF2E9BFF),
        WeaponSpec("tesla_relay", "TESLA RELAY", "-z-", Rarity.RARE, WeaponKind.ARC,
            "Relays a jolt from threat to threat.", 3,
            cooldown = 2.6f, count = 3, countPerLevel = 1, damage = 0.9f, color = 0xFF7FC4FF),
        // --- PURPLE ------------------------------------------------------------
        WeaponSpec("helix_cannon", "HELIX CANNON", "8", Rarity.EPIC, WeaponKind.SPIRAL,
            "A double helix of piercing bolts.", 3,
            cooldown = 3.4f, count = 16, countPerLevel = 4, damage = 0.7f, pierce = 1, speed = 1f, color = 0xFFA259FF),
        WeaponSpec("malware_swamp", "MALWARE SWAMP", "~~~", Rarity.EPIC, WeaponKind.FIELD,
            "Floods the floor around you with corrosive code.", 3,
            cooldown = 4f, count = 1, damage = 0.9f, radius = 120f, color = 0xFFB070FF),
        WeaponSpec("seeker_swarm", "SEEKER SWARM", ">>>", Rarity.EPIC, WeaponKind.VOLLEY,
            "Releases a swarm of homing drones.", 3,
            cooldown = 2.8f, count = 5, countPerLevel = 1, damage = 0.8f, homing = 7f, speed = 0.75f, spreadDegrees = 120f, color = 0xFFC08CFF),
        WeaponSpec("shockwave", "SHOCKWAVE", "((·))", Rarity.EPIC, WeaponKind.RING,
            "A dense ring of piercing packets.", 3,
            cooldown = 3.4f, count = 16, countPerLevel = 4, damage = 0.9f, pierce = 2, color = 0xFF9B4DFF),
        WeaponSpec("satellite_lance", "SATELLITE LANCE", "\\|/", Rarity.EPIC, WeaponKind.STRIKE,
            "A satellite spears two marked threats from orbit.", 3,
            cooldown = 4.2f, count = 2, countPerLevel = 1, damage = 4f, radius = 70f, color = 0xFFD0A8FF),
        // --- GOLDEN ------------------------------------------------------------
        WeaponSpec("supernova", "SUPERNOVA", "{*}", Rarity.LEGENDARY, WeaponKind.NOVA,
            "A golden star collapses around you, erasing the area.", 2,
            cooldown = 5f, count = 1, damage = 4f, radius = 230f, color = 0xFFFFD426),
        WeaponSpec("prism_laser", "PRISM LASER", "<|>", Rarity.LEGENDARY, WeaponKind.LASER,
            "Splits one beam into a fan of three.", 2,
            cooldown = 3.2f, count = 3, countPerLevel = 2, damage = 3f, length = 640f, spreadDegrees = 40f, color = 0xFFFFE066),
        WeaponSpec("golden_boomerang", "GOLDEN BOOMERANG", "<))", Rarity.LEGENDARY, WeaponKind.BOOMERANG,
            "Three gilded discs fan out and return.", 2,
            cooldown = 3f, count = 3, countPerLevel = 1, damage = 2.5f, speed = 1f, spreadDegrees = 50f, color = 0xFFFFC94D),
        WeaponSpec("chain_storm", "CHAIN STORM", "zZz", Rarity.LEGENDARY, WeaponKind.ARC,
            "A storm of lightning that leaps through the crowd.", 2,
            cooldown = 2.8f, count = 6, countPerLevel = 2, damage = 2.2f, color = 0xFFFFE680),
        WeaponSpec("carpet_bomb", "CARPET BOMB", "vvv", Rarity.LEGENDARY, WeaponKind.STRIKE,
            "Calls in a bombing run on six threats.", 2,
            cooldown = 5f, count = 6, countPerLevel = 2, damage = 3.5f, radius = 80f, color = 0xFFFFB020),
        // --- TITANIUM ------------------------------------------------------------
        WeaponSpec("black_ice", "BLACK ICE", "[##]", Rarity.TITANIUM, WeaponKind.FIELD,
            "Lethal intrusion countermeasures: a huge field that grinds threats down.", 2,
            cooldown = 4.5f, count = 1, damage = 1.6f, radius = 170f, color = 0xFFDCE8F2),
        WeaponSpec("root_access", "ROOT ACCESS", "#", Rarity.TITANIUM, WeaponKind.RING,
            "Total control: a ring of seeking, piercing root packets.", 2,
            cooldown = 3f, count = 24, countPerLevel = 8, damage = 1.5f, pierce = 3, homing = 2.5f, color = 0xFFEAF2FA),
        WeaponSpec("titan_railstorm", "TITAN RAILSTORM", "=|||=", Rarity.TITANIUM, WeaponKind.LASER,
            "Five full-length rails that ignore walls.", 2,
            cooldown = 4f, count = 5, countPerLevel = 2, damage = 5f, length = 1400f, spreadDegrees = 60f, throughWalls = true, color = 0xFFF0F6FC),
        WeaponSpec("doomsday_daemon", "DOOMSDAY DAEMON", "\\V/!", Rarity.TITANIUM, WeaponKind.STRIKE,
            "Eight strikes from orbit, each enough to end most things.", 2,
            cooldown = 6f, count = 8, countPerLevel = 2, damage = 9f, radius = 95f, color = 0xFFDCE8F2),
        WeaponSpec("kernel_panic", "KERNEL PANIC", "!!!", Rarity.TITANIUM, WeaponKind.NOVA,
            "Crashes the whole room. Everything near you simply stops.", 2,
            cooldown = 6f, count = 1, damage = 9f, radius = 320f, color = 0xFFFFFFFF)
    )

    private val byId = all.associateBy { it.id }

    fun byId(id: String): WeaponSpec? = byId[id]

    /** Upgrade cards for every weapon; the engine reads the level from RunStats.weapons. */
    val upgrades: List<UpgradeDef> = all.map { w ->
        UpgradeDef(
            w.id, w.name, w.glyph, w.rarity, UpgradeCategory.WEAPON, w.maxLevel,
            w.description, { l -> w.effect(l) }, { s, l -> s.weapons[w.id] = l }
        )
    }
}
