package com.cyberoperative.game.data

/**
 * Special-event levels (§26–28). Fully data driven: an event is a set of
 * [EventRules] applied on top of normal level generation, plus presentation.
 * New seasonal / daily / community events are new rows here, not engine code.
 */
data class EventRules(
    val enemyCountMul: Float = 1f,
    val enemyHpMul: Float = 1f,
    val enemySpeedMul: Float = 1f,
    val enemyDamageMul: Float = 1f,
    val playerDamageMul: Float = 1f,
    val healingMul: Float = 1f,
    /** Multiplies projectile count of enemy volleys. */
    val enemyProjectileMul: Int = 1,
    val eliteChanceBonus: Float = 0f,
    /** Seconds between random arena hazards (0 = none). */
    val hazardInterval: Float = 0f,
    /** >0: survive this long instead of clearing waves. */
    val timedSeconds: Float = 0f,
    /** Used with [timedSeconds]: seconds between continuous spawns. */
    val spawnInterval: Float = 0f,
    val extraWaves: Int = 0,
    /** Restrict spawns to these enemy ids (null = normal pool). */
    val forcedPool: List<String>? = null,
    val rewardMul: Float = 1f,
    /** Chance of a small ◇ reward. 0 until the monetization balance is decided. */
    val diamondChance: Float = 0f,
    /** Data Vault: place the cache at the centre of the arena. */
    val vault: Boolean = false
)

data class EventDef(
    val id: String,
    val name: String,
    val subtitle: String,
    val description: String,
    /** Accent colour of the event (border tint, banner). */
    val accent: Long,
    val minLevel: Int,
    val weight: Float,
    val rules: EventRules,
    /** Zero-Day Anomaly rolls a random subset of these on top of [rules]. */
    val randomModifiers: List<Pair<String, EventRules>> = emptyList()
)

object Events {

    val PACKET_FLOOD = EventDef(
        "packet_flood", "PACKET FLOOD", "Volumetric attack inbound",
        "A massive number of weak packets. Fast combat, bonus €.",
        0xFF2EE6FF, minLevel = 4, weight = 1f,
        rules = EventRules(
            enemyCountMul = 2.6f, enemyHpMul = 0.45f, extraWaves = 1,
            forcedPool = listOf("bot", "malware", "wormlet"), rewardMul = 2f
        )
    )

    val FIREWALL_BREACH = EventDef(
        "firewall_breach", "FIREWALL BREACH", "Perimeter failing",
        "Defences are down: aggressive threats and collapsing safe space.",
        0xFFFF7A1A, minLevel = 6, weight = 1f,
        rules = EventRules(
            enemyCountMul = 1.2f, enemySpeedMul = 1.25f, enemyDamageMul = 1.2f,
            hazardInterval = 3.2f, rewardMul = 2.5f
        )
    )

    val MALWARE_SWARM = EventDef(
        "malware_swarm", "MALWARE SWARM", "Survive the outbreak",
        "Threats spawn continuously. Survive until the timer reaches zero.",
        0xFFFF2EC4, minLevel = 5, weight = 1f,
        rules = EventRules(timedSeconds = 30f, spawnInterval = 1.0f, enemyHpMul = 0.8f, rewardMul = 2f)
    )

    val DATA_VAULT = EventDef(
        "data_vault", "DATA VAULT", "Valuable cache detected",
        "Several waves guard a digital cache. Clear them all for a large reward.",
        0xFFFFD426, minLevel = 8, weight = 0.8f,
        rules = EventRules(enemyCountMul = 1.5f, extraWaves = 2, rewardMul = 3f, vault = true)
    )

    val ZERO_DAY_ANOMALY = EventDef(
        "zero_day", "ZERO-DAY ANOMALY", "Unknown behaviour",
        "Unpredictable conditions. Anything can change. Big rewards.",
        0xFFA259FF, minLevel = 10, weight = 0.7f,
        rules = EventRules(rewardMul = 2.5f),
        randomModifiers = listOf(
            "THREATS ACCELERATED" to EventRules(enemySpeedMul = 1.3f),
            "PAYLOAD AMPLIFIED" to EventRules(playerDamageMul = 1.4f),
            "REPAIR DEGRADED" to EventRules(healingMul = 0.4f),
            "DOUBLE PACKETS" to EventRules(enemyProjectileMul = 2),
            "HARDENED THREATS" to EventRules(eliteChanceBonus = 0.3f),
            "UNSTABLE SECTORS" to EventRules(hazardInterval = 3.5f),
            "THREAT DENSITY UP" to EventRules(enemyCountMul = 1.5f)
        )
    )

    val all: List<EventDef> = listOf(PACKET_FLOOD, FIREWALL_BREACH, MALWARE_SWARM, DATA_VAULT, ZERO_DAY_ANOMALY)

    fun byId(id: String): EventDef? = all.firstOrNull { it.id == id }

    /** Chance that an eligible level becomes an event level. */
    const val EVENT_CHANCE = 0.14f

    /** Combine two rule sets (used by Zero-Day's random modifiers). */
    fun merge(a: EventRules, b: EventRules): EventRules = EventRules(
        enemyCountMul = a.enemyCountMul * b.enemyCountMul,
        enemyHpMul = a.enemyHpMul * b.enemyHpMul,
        enemySpeedMul = a.enemySpeedMul * b.enemySpeedMul,
        enemyDamageMul = a.enemyDamageMul * b.enemyDamageMul,
        playerDamageMul = a.playerDamageMul * b.playerDamageMul,
        healingMul = a.healingMul * b.healingMul,
        enemyProjectileMul = a.enemyProjectileMul * b.enemyProjectileMul,
        eliteChanceBonus = a.eliteChanceBonus + b.eliteChanceBonus,
        hazardInterval = if (a.hazardInterval > 0f && b.hazardInterval > 0f) minOf(a.hazardInterval, b.hazardInterval)
        else maxOf(a.hazardInterval, b.hazardInterval),
        timedSeconds = maxOf(a.timedSeconds, b.timedSeconds),
        spawnInterval = maxOf(a.spawnInterval, b.spawnInterval),
        extraWaves = a.extraWaves + b.extraWaves,
        forcedPool = a.forcedPool ?: b.forcedPool,
        rewardMul = a.rewardMul * b.rewardMul,
        diamondChance = maxOf(a.diamondChance, b.diamondChance),
        vault = a.vault || b.vault
    )
}
