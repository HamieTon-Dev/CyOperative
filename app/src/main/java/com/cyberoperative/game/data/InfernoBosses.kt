package com.cyberoperative.game.data

/*
 * INFERNO Boss Packs Alpha, Beta and Gamma (owner sheets, 0.35.0): fifteen bosses that only turn
 * up on INFERNO difficulties (from the sheet's level on), and in Endless boss rounds from level
 * [InfernoBosses.ENDLESS_FROM]. Names, roles, threat tiers and the four named attacks are the
 * owner's; each attack is built from the arena's boss patterns. Bodies: ui/game/InfernoBodies.kt.
 */
object InfernoBosses {
    private const val P1 = 1.0f
    private const val P2 = 0.6f
    private const val P3 = 0.25f

    /** Endless: boss rounds from this level also draw INFERNO bosses (owner). */
    const val ENDLESS_FROM = 160

    private fun boss(
        id: String, name: String, tag: String, title: String, codex: String, color: Long,
        radius: Float, hp: Float, speed: Float, contact: Float, move: BossMove,
        role: BossRole, tier: Int, armor: Float, abilities: List<String>,
        p1: List<Pattern>, p2: List<Pattern>, p3: List<Pattern>, phaseNames: Pair<String, String>
    ) = BossDef(
        id, name, tag, title, codex, color, radius, hp, speed, contact, move,
        listOf(
            BossPhase(P1, 1f, 1.2f, p1, "PHASE 1"),
            BossPhase(P2, 1.12f, 1.05f, p2, phaseNames.first),
            BossPhase(P3, 1.25f, 0.9f, p3, phaseNames.second)
        ),
        euros = if (tier >= 4) 460 else 400, score = if (tier >= 4) 9500 else 8500,
        role = role, tier = tier, armor = armor, abilities = abilities
    )

    // ---- Pack Alpha (L30+) ---------------------------------------------------------------

    val HELLCORE_TYRANT = boss(
        "hellcore_tyrant", "HELLCORE TYRANT", "<Ψ>", "Molten Command Engine",
        "A sentient forge core that rules through fire, with molten edicts and absolute destruction.",
        0xFFFF5A1F, 76f, 2700f, 70f, 30f, BossMove.HOVER, BossRole.COMMANDER, 3, 3f,
        listOf("Flame Barrage", "Magma Pulse", "Ember Mines", "Tyrant Burst"),
        listOf(Pattern.Aimed(7, 60f, 330f, 16f, bursts = 2), Pattern.CorePulse(520f, 300f, 26f, 3f, 14f), Pattern.BishopMines(4, 1f, 9f, 85f, 26f)),
        listOf(Pattern.Aimed(9, 80f, 350f, 17f, bursts = 3), Pattern.BishopMines(5, 0.9f, 9f, 90f, 27f), Pattern.Radial(18, 260f, 18f, waves = 3, rotateDeg = 10f), Pattern.CorePulse(560f, 320f, 27f, 3.5f, 15f)),
        listOf(Pattern.Radial(24, 280f, 19f, waves = 4, rotateDeg = 8f), Pattern.Aimed(11, 100f, 370f, 18f, bursts = 3), Pattern.BishopMines(6, 0.8f, 9f, 95f, 28f), Pattern.CorePulse(600f, 340f, 28f, 4f, 16f)),
        "MOLTEN EDICT" to "ABSOLUTE DESTRUCTION"
    )

    val EMBER_WARDEN = boss(
        "ember_warden", "EMBER WARDEN", "[▲]", "Fortress-like Inferno Sentry",
        "An immovable bastion of living metal, guarding the depths with walls of flame.",
        0xFFFF8A1F, 84f, 3200f, 34f, 34f, BossMove.HOVER, BossRole.TANK, 3, 5f,
        listOf("Firewall Ring", "Sear Beam", "Cinder Wall", "Warden Slam"),
        listOf(Pattern.FirewallRing(2, 2, 260f, 24f), Pattern.Beam(0.9f, 1.2f, 30f, 26f), Pattern.CoverDeploy(4, 8f)),
        listOf(Pattern.FirewallRing(3, 2, 280f, 25f), Pattern.Beam(0.8f, 1.4f, 32f, 27f, count = 2, spreadDeg = 30f), Pattern.CoverDeploy(5, 8f), Pattern.ShockRing(520f, 300f, 28f)),
        listOf(Pattern.FirewallRing(4, 3, 300f, 26f, waveGap = 0.75f), Pattern.Beam(0.7f, 1.6f, 34f, 28f, count = 3, spreadDeg = 40f), Pattern.ShockRing(600f, 340f, 30f), Pattern.CoverDeploy(6, 7f)),
        "BASTION" to "MELTDOWN WATCH"
    )

    val CINDER_WIDOW = boss(
        "cinder_widow", "CINDER WIDOW", "}X{", "Inferno Predator",
        "A volcanic huntress that spins molten webs and devours intruders in ash and flame.",
        0xFFFF2448, 70f, 2300f, 120f, 30f, BossMove.CHASE, BossRole.ASSASSIN, 3, 2.4f,
        listOf("Lava Web", "Ash Leap", "Needle Spray", "Burn Bite"),
        listOf(Pattern.Zones(3, 80f, 6f, 14f), Pattern.BacklineDive(0.8f, 900f, 30f), Pattern.Needles(7, 50f, 520f, 15f, bursts = 2)),
        listOf(Pattern.Zones(4, 85f, 6f, 15f), Pattern.BacklineDive(0.7f, 950f, 31f), Pattern.DashSlash(0.55f, 860f, 540f, 31f, repeats = 2), Pattern.Needles(9, 70f, 540f, 16f, bursts = 3)),
        listOf(Pattern.Zones(5, 90f, 6f, 16f), Pattern.DashSlash(0.5f, 900f, 600f, 32f, repeats = 3, trail = true), Pattern.BacklineDive(0.6f, 1000f, 32f), Pattern.Needles(11, 90f, 560f, 17f, bursts = 3)),
        "WEB OF ASH" to "DEVOURING"
    )

    val MAGMA_PULSE = boss(
        "magma_pulse", "MAGMA PULSE", "(◉)", "Volcanic Reactor Core",
        "A destabilized reactor that floods the battlefield with relentless thermal energy.",
        0xFFFF7A10, 80f, 2800f, 40f, 30f, BossMove.DRIFT, BossRole.AREA_CONTROL, 3, 3f,
        listOf("Pulse Nova", "Heat Wave", "Core Vent", "Firestorm Orbs"),
        listOf(Pattern.ShockRing(520f, 300f, 26f), Pattern.BurnSector(2, 50f, 1.1f, 2.5f, 18f), Pattern.Homing(4, 170f, 1.6f, 18f)),
        listOf(Pattern.GridSurge(3, 0.6f, 560f, 320f, 26f), Pattern.BurnSector(3, 50f, 1.0f, 2.5f, 19f), Pattern.Homing(6, 180f, 1.8f, 19f), Pattern.ShockRing(580f, 330f, 27f)),
        listOf(Pattern.GridSurge(4, 0.5f, 600f, 340f, 27f), Pattern.BurnSector(4, 45f, 0.9f, 2.6f, 20f), Pattern.Homing(8, 190f, 2f, 20f), Pattern.ShockRing(640f, 360f, 28f)),
        "CRITICAL MASS" to "MELTDOWN"
    )

    val ASHEN_REAVER = boss(
        "ashen_reaver", "ASHEN REAVER", "/^\\", "Inferno Drone of the Fallen",
        "A vengeful reaper forged in ash, harvesting souls to fuel the eternal fire.",
        0xFFE8483A, 68f, 2200f, 125f, 32f, BossMove.HOVER, BossRole.HUNTER, 3, 2.4f,
        listOf("Scorch Dash", "Ash Trail", "Reaver Slice", "Soul Ember"),
        listOf(Pattern.GhostDash(0.6f, 820f, 520f, 30f), Pattern.Scythes(2, 380f, 1.6f, 22f), Pattern.Homing(4, 160f, 1.7f, 17f)),
        listOf(Pattern.GhostDash(0.55f, 860f, 560f, 31f, repeats = 2), Pattern.CorruptionTrail(3f, 1.4f, 60f, 5f, 15f), Pattern.Scythes(3, 400f, 1.5f, 23f, spreadDeg = 80f), Pattern.Homing(5, 170f, 1.8f, 18f)),
        listOf(Pattern.GhostDash(0.5f, 900f, 600f, 32f, repeats = 3), Pattern.CorruptionTrail(3.5f, 1.5f, 65f, 5f, 16f), Pattern.Scythes(4, 420f, 1.4f, 24f, spreadDeg = 110f), Pattern.Homing(7, 180f, 2f, 19f)),
        "HARVEST" to "ETERNAL FIRE"
    )

    // ---- Pack Beta (L32+) ----------------------------------------------------------------

    val INFERNAL_ROUTER = boss(
        "infernal_router", "INFERNAL ROUTER", "<=◎=>", "Network Fortress of the Burning Grid",
        "A colossal network-router that channels volcanic data through rotating fire ports, controlling the flow of the inferno net.",
        0xFFFF3B1F, 86f, 3000f, 44f, 30f, BossMove.HOVER, BossRole.CONTROLLER, 3, 3.5f,
        listOf("Heat Packet Storm", "Port Sweep", "Burn Signal", "Firewall Surge"),
        listOf(Pattern.Spiral(3, 3f, 10f, 70f, 240f, 16f), Pattern.SweepBeam(1f, 2f, 90f, 28f, 26f), Pattern.Blasts(3, 80f, 1f, 26f)),
        listOf(Pattern.Spiral(4, 3.2f, 12f, 80f, 250f, 17f), Pattern.SweepBeam(0.9f, 2.2f, 120f, 28f, 27f, count = 2), Pattern.Blasts(4, 85f, 0.9f, 27f), Pattern.FirewallRing(2, 2, 270f, 25f)),
        listOf(Pattern.Spiral(5, 3.5f, 14f, 90f, 260f, 18f), Pattern.SweepBeam(0.8f, 2.4f, 150f, 30f, 28f, count = 3), Pattern.FirewallRing(3, 2, 290f, 26f), Pattern.Blasts(5, 90f, 0.8f, 28f)),
        "PORTS OPEN" to "GRID MELTDOWN"
    )

    val PYROCLAST_ORACLE = boss(
        "pyroclast_oracle", "PYROCLAST ORACLE", "<◬>", "Prophet Core of the Ash Vision",
        "A molten seer that reads the combustion of worlds, summoning embodied visions from the ashes of the future.",
        0xFFFF4A3A, 72f, 2500f, 60f, 28f, BossMove.TELEPORT, BossRole.SUMMONER, 3, 2.6f,
        listOf("Oracle Flare", "Shard Rain", "Vision Beam", "Ember Spawn"),
        listOf(Pattern.Radial(16, 260f, 17f, waves = 2, rotateDeg = 11f), Pattern.Mortar(4, 80f, 1.1f, 25f), Pattern.Summon("holo_clone", 2), Pattern.Teleport),
        listOf(Pattern.Beam(0.8f, 1.4f, 30f, 27f, count = 2, spreadDeg = 25f), Pattern.Mortar(5, 85f, 1f, 26f, volleys = 2), Pattern.Summon("holo_clone", 3), Pattern.Radial(20, 270f, 18f, waves = 3, rotateDeg = 9f), Pattern.Teleport),
        listOf(Pattern.Beam(0.7f, 1.6f, 32f, 28f, count = 3, spreadDeg = 35f), Pattern.Mortar(6, 90f, 0.9f, 27f, volleys = 2), Pattern.Summon("holo_clone", 4), Pattern.Radial(24, 280f, 19f, waves = 4, rotateDeg = 7f), Pattern.Teleport),
        "ASH VISION" to "THE FUTURE BURNS"
    )

    val SEARFANG_COLOSSUS = boss(
        "searfang_colossus", "SEARFANG COLOSSUS", "[VvV]", "Furnace Beast of Unrelenting Impact",
        "A quadruped war engine with lava fangs and burning exhaust vents; its every step turns the ground to molten slag.",
        0xFFFF5020, 90f, 3300f, 80f, 36f, BossMove.CHASE, BossRole.BRUISER, 3, 4f,
        listOf("Sear Charge", "Molten Roar", "Fang Crush", "Cinder Stomp"),
        listOf(Pattern.Charge(0.8f, 760f, 560f, 32f), Pattern.ShockRing(500f, 300f, 26f), Pattern.SpikeEruption(3, 6, 40f, 26f)),
        listOf(Pattern.Charge(0.7f, 800f, 600f, 33f, repeats = 2), Pattern.Blasts(3, 95f, 0.9f, 30f), Pattern.SpikeEruption(5, 7, 70f, 27f), Pattern.ShockRing(560f, 320f, 27f)),
        listOf(Pattern.Charge(0.6f, 850f, 640f, 34f, repeats = 3), Pattern.SpikeEruption(8, 7, 360f, 28f), Pattern.Blasts(4, 100f, 0.8f, 31f), Pattern.ShockRing(620f, 340f, 28f)),
        "SLAG STRIDE" to "UNRELENTING"
    )

    val BURNWIRE_BISHOP = boss(
        "burnwire_bishop", "BURNWIRE BISHOP", "[†]", "High Drone of the Furnace Choir",
        "A hovering inferno bishop that spreads burning wire and sacred heat, fortifying the hellish choir.",
        0xFFFF2E4A, 78f, 2600f, 80f, 30f, BossMove.HOVER, BossRole.SUPPORT, 3, 3f,
        listOf("Wire Lash", "Ember Lattice", "Bishop Mark", "Furnace Beacon"),
        listOf(Pattern.SweepBeam(0.9f, 1.6f, 80f, 24f, 25f), Pattern.CrossBeam(1f, 1.6f, 0f, 26f, 26f), Pattern.BishopMines(4, 1f, 9f, 85f, 26f)),
        listOf(Pattern.SweepBeam(0.8f, 1.8f, 110f, 24f, 26f, count = 2), Pattern.CrossBeam(0.9f, 2.2f, 60f, 26f, 27f), Pattern.BishopMines(5, 0.9f, 9f, 85f, 27f), Pattern.OrbitalBarrage(4, 80f, 1f, 27f)),
        listOf(Pattern.CrossBeam(0.85f, 2.6f, 90f, 28f, 28f, diagonal = true), Pattern.OrbitalBarrage(5, 85f, 0.9f, 28f, volleys = 2), Pattern.BishopMines(6, 0.8f, 9f, 90f, 28f), Pattern.SweepBeam(0.7f, 2f, 140f, 26f, 27f, count = 3)),
        "FURNACE CHOIR" to "SACRED HEAT"
    )

    val FURNACE_MAW = boss(
        "furnace_maw", "FURNACE MAW", "(VVV)", "Devourer of Fuel and Flesh",
        "A titanic furnace-mouth horror that consumes everything, spitting fire, smoke and the remnants of worlds.",
        0xFFFF6A1A, 94f, 3400f, 60f, 36f, BossMove.CHASE, BossRole.HUNTER, 3, 4f,
        listOf("Maw Snap", "Coal Spit", "Draft Pull", "Blast Breath"),
        listOf(Pattern.Charge(0.8f, 700f, 480f, 34f), Pattern.Mortar(4, 85f, 1.1f, 26f), Pattern.BurnSector(1, 60f, 1.1f, 2.4f, 20f)),
        listOf(Pattern.ConvergenceFlash(1.3f, 160f, 200f, 34f), Pattern.Charge(0.7f, 740f, 520f, 35f, repeats = 2), Pattern.Mortar(5, 90f, 1f, 27f, volleys = 2), Pattern.BurnSector(2, 60f, 1f, 2.5f, 21f)),
        listOf(Pattern.ConvergenceFlash(1.4f, 180f, 220f, 36f), Pattern.BurnSector(3, 60f, 0.9f, 2.6f, 22f), Pattern.Charge(0.6f, 780f, 560f, 36f, repeats = 3), Pattern.Mortar(6, 95f, 0.9f, 28f, volleys = 2)),
        "FEEDING" to "CONSUME ALL"
    )

    // ---- Pack Gamma (L35+) ---------------------------------------------------------------

    val REDLINE_SOVEREIGN = boss(
        "redline_sovereign", "REDLINE SOVEREIGN", "[♛]", "Regal Inferno Engine",
        "A sovereign machine that commands the inferno, its crown spires burning with absolute authority.",
        0xFFFF1A3A, 90f, 3800f, 50f, 36f, BossMove.HOVER, BossRole.COMMANDER, 4, 5f,
        listOf("Redline Burst", "Crown Lasers", "Ember Decree", "Sovereign Crash"),
        listOf(Pattern.Radial(22, 280f, 19f, waves = 3, rotateDeg = 8f), Pattern.CrossBeam(1f, 2f, 45f, 28f, 28f), Pattern.OrbitalBarrage(4, 85f, 1f, 28f)),
        listOf(Pattern.CrossBeam(0.9f, 2.4f, 90f, 28f, 29f, diagonal = true), Pattern.OrbitalBarrage(5, 90f, 0.9f, 29f, volleys = 2), Pattern.Charge(0.7f, 800f, 560f, 36f), Pattern.ShockRing(600f, 340f, 30f), Pattern.Radial(26, 290f, 20f, waves = 4, rotateDeg = 7f)),
        listOf(Pattern.Charge(0.6f, 860f, 620f, 38f, repeats = 2), Pattern.ShockRing(660f, 360f, 32f), Pattern.CrossBeam(0.8f, 2.8f, 120f, 30f, 30f, diagonal = true), Pattern.OrbitalBarrage(6, 95f, 0.8f, 30f, volleys = 3), Pattern.Radial(30, 300f, 21f, waves = 5, rotateDeg = 6f)),
        "ROYAL DECREE" to "ABSOLUTE AUTHORITY"
    )

    val SCORCH_HALO = boss(
        "scorch_halo", "SCORCH HALO", "(◎)", "Celestial Halo-Wheel of Burning Judgment",
        "A floating halo of blades and flame; its singular eye scorches all who enter its orbit.",
        0xFFFFA020, 80f, 2800f, 60f, 30f, BossMove.DRIFT, BossRole.AREA_DENIAL, 3, 3f,
        listOf("Halo Spin", "Ring of Sear", "Solar Burst", "Orbit Slash"),
        listOf(Pattern.PurgeSpin(3, 0.9f, 2.4f, 240f, 24f), Pattern.FirewallRing(2, 2, 260f, 24f), Pattern.Radial(18, 270f, 18f, waves = 2, rotateDeg = 10f)),
        listOf(Pattern.PurgeSpin(4, 0.8f, 2.6f, 300f, 25f), Pattern.Scythes(3, 420f, 1.5f, 23f, spreadDeg = 90f), Pattern.FirewallRing(3, 2, 280f, 25f), Pattern.Radial(22, 280f, 19f, waves = 3, rotateDeg = 9f)),
        listOf(Pattern.PurgeSpin(5, 0.7f, 2.8f, 360f, 26f), Pattern.Scythes(5, 440f, 1.4f, 24f, spreadDeg = 140f), Pattern.FirewallRing(3, 3, 300f, 26f, waveGap = 0.7f), Pattern.Radial(26, 290f, 20f, waves = 4, rotateDeg = 8f)),
        "JUDGMENT" to "SOLAR ORBIT"
    )

    val VOLCANIC_NULL = boss(
        "volcanic_null", "VOLCANIC NULL", "[▮▮▮]", "Nullfield Monolith",
        "A monolithic engine that erases systems in a field of ash, channeling volcanic energy to silence the battlefield.",
        0xFFD81E5A, 88f, 3200f, 30f, 32f, BossMove.HOVER, BossRole.BLACKOUT_HUNTER, 3, 4.5f,
        listOf("Null Pulse", "Vent Collapse", "Silence Field", "Magma Burst"),
        listOf(Pattern.GridSurge(3, 0.6f, 540f, 320f, 26f), Pattern.Zones(3, 90f, 7f, 15f), Pattern.Mortar(4, 85f, 1.1f, 26f)),
        listOf(Pattern.GridSurge(4, 0.55f, 580f, 330f, 27f), Pattern.HeatCollapse(2, 140f, 26f), Pattern.Zones(4, 95f, 7f, 16f), Pattern.Mortar(5, 90f, 1f, 27f, volleys = 2)),
        listOf(Pattern.HeatCollapse(2, 160f, 28f), Pattern.GridSurge(5, 0.5f, 620f, 350f, 28f), Pattern.Zones(5, 100f, 7f, 17f), Pattern.Mortar(6, 95f, 0.9f, 28f, volleys = 3)),
        "ASHFALL" to "SILENCE"
    )

    val SMOLDER_HEX = boss(
        "smolder_hex", "SMOLDER HEX", "<⬡>", "Hexagonal Curse Engine",
        "A forbidden engine that channels ancient runes through six ember turrets, cursing the battlefield with fire.",
        0xFFFF6A2A, 82f, 3000f, 40f, 30f, BossMove.HOVER, BossRole.RITUALIST, 3, 3.5f,
        listOf("Hex Barrage", "Sigil Burn", "Ritual Sweep", "Ash Lances"),
        listOf(Pattern.CubeBarrage(2, 6, 200f, 1.6f, 18f), Pattern.CorruptFloor(5, 1.1f, 2.4f, 18f), Pattern.Beam(0.9f, 1.2f, 28f, 26f, count = 3, spreadDeg = 50f)),
        listOf(Pattern.CubeBarrage(3, 6, 210f, 1.8f, 19f), Pattern.CorruptFloor(6, 1f, 2.5f, 19f), Pattern.SweepBeam(0.9f, 2f, 120f, 26f, 26f, count = 2), Pattern.Beam(0.8f, 1.3f, 28f, 27f, count = 4, spreadDeg = 70f)),
        listOf(Pattern.CubeBarrage(3, 8, 220f, 2f, 20f), Pattern.SweepBeam(0.8f, 2.2f, 180f, 28f, 27f, count = 3), Pattern.CorruptFloor(7, 0.9f, 2.6f, 20f), Pattern.Beam(0.7f, 1.4f, 30f, 28f, count = 6, spreadDeg = 300f)),
        "SIX SIGILS" to "RITUAL FIRE"
    )

    val COMBUSTION_MONARCH = boss(
        "combustion_monarch", "COMBUSTION MONARCH", "}Ж{", "Inferno Moth-Queen and Living Reactor",
        "A revered queen of flame; her wings birth storms of ember as her furnace core threatens to consume all.",
        0xFFFF4030, 92f, 4000f, 90f, 36f, BossMove.HOVER, BossRole.APEX, 4, 5f,
        listOf("Monarch Dive", "Wing Flare", "Pyre Storm", "Combustion Bloom"),
        listOf(Pattern.BacklineDive(0.8f, 950f, 34f), Pattern.Aimed(13, 120f, 360f, 19f, bursts = 2), Pattern.Spiral(4, 3f, 12f, 90f, 250f, 18f)),
        listOf(Pattern.BacklineDive(0.7f, 1000f, 35f), Pattern.Aimed(15, 150f, 370f, 20f, bursts = 3), Pattern.Spiral(5, 3.4f, 14f, 100f, 260f, 19f), Pattern.Bloom(2, 30f, lanes = 3)),
        listOf(Pattern.Bloom(3, 32f, lanes = 3), Pattern.BacklineDive(0.6f, 1050f, 36f), Pattern.Spiral(6, 3.8f, 16f, 110f, 270f, 20f), Pattern.Aimed(17, 180f, 380f, 21f, bursts = 3), Pattern.Bloom(3, 32f, lanes = 2)),
        "EMBER STORM" to "COMBUSTION"
    )

    val alpha = listOf(HELLCORE_TYRANT, EMBER_WARDEN, CINDER_WIDOW, MAGMA_PULSE, ASHEN_REAVER)
    val beta = listOf(INFERNAL_ROUTER, PYROCLAST_ORACLE, SEARFANG_COLOSSUS, BURNWIRE_BISHOP, FURNACE_MAW)
    val gamma = listOf(REDLINE_SOVEREIGN, SCORCH_HALO, VOLCANIC_NULL, SMOLDER_HEX, COMBUSTION_MONARCH)
    val all: List<BossDef> = alpha + beta + gamma

    /** The run level each pack turns up from on INFERNO (owner's sheets: L30+, L32+, L35+). */
    fun minLevel(b: BossDef): Int = when (b) {
        in alpha -> 30
        in beta -> 32
        else -> 35
    }

    /** INFERNO world 15's guardian: the apex boss. */
    val FINALE get() = COMBUSTION_MONARCH
}
