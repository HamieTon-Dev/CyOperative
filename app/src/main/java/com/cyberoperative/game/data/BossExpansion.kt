package com.cyberoperative.game.data

/**
 * Boss Expansion Vol. 1 (owner, 2026-10-10; sheets in docs/boss-concepts/).
 * Twelve bosses for levels 130–240, ordered by threat tier. Stats are the
 * sheets' numbers mapped onto the game's scale (docs/BOSS_EXPANSION_LOG.md):
 * HP × 1.6, speed × 12, damage as is, armor × 0.3, bounty ÷ 40.
 *
 * A boss joins [all] (and so the roster) once its mechanics are built; its
 * body design needs the owner's approval first (log §6).
 */
object BossExpansion {

    private const val P1 = 1.0f
    private const val P2 = 0.6f
    private const val P3 = 0.25f

    /**
     * Vault Sentinel (Boss Pack Alpha 01, TANK). A fortress server guardian:
     * raises barrier cubes out of the floor that hem you in, sweeps the room
     * with lasers that the cubes stop, boxes you in with a lockdown (one side
     * open, away from it) and shells the box with mortars. Body drawn 20%
     * larger than first shown (owner, 2026-10-10: "can it be slightly larger?").
     */
    val VAULT_SENTINEL = BossDef(
        "vault_sentinel", "VAULT SENTINEL", "[▣]", "Fortress Server Guardian",
        "A fortress-like server guardian that raises barrier cubes out of the floor and sweeps the " +
            "arena with lethal lasers. Its cubes cage you — but they also stop its lasers. Shells fly over them.",
        0xFFFF2D55, 72f, 2560f, 42f, 28f, BossMove.HOVER, listOf(
            BossPhase(P1, 1f, 1.5f, listOf(
                Pattern.CoverDeploy(4, 8f),
                Pattern.SweepBeam(1.1f, 2.4f, 110f, 26f, 26f),
                Pattern.Mortar(3, 80f, 1.3f, 24f)
            ), "PHASE 1"),
            BossPhase(P2, 1.05f, 1.3f, listOf(
                Pattern.Lockdown(110f, 5f),
                Pattern.Mortar(4, 80f, 1.2f, 24f, volleys = 2),
                Pattern.SweepBeam(1.0f, 2.6f, 140f, 26f, 26f, count = 2),
                Pattern.CoverDeploy(5, 8f)
            ), "LOCKDOWN"),
            BossPhase(P3, 1.1f, 1.1f, listOf(
                Pattern.CoverDeploy(6, 7f),
                Pattern.SweepBeam(0.9f, 2.8f, 150f, 28f, 28f, count = 3),
                Pattern.Lockdown(105f, 4.5f, rise = 1.0f),
                Pattern.Mortar(5, 85f, 1.1f, 26f, volleys = 2),
                Pattern.Radial(18, 180f, 12f, waves = 2, rotateDeg = 10f)
            ), "VAULT BREACHED")
        ), euros = 326, score = 7000, role = BossRole.TANK, tier = 3, armor = 6f,
        abilities = listOf("Cover Deploy", "Laser Sweep", "Lockdown Cube", "Pulse Mortar")
    )

    /**
     * Rootkit Apostle (Boss Pack Gamma 01, AMBUSHER). Burrows beneath the arena
     * and erupts under you, bursts crystal spikes out of the floor and infects
     * the spots you've been standing in, so you have to keep relocating.
     * Body approved by the owner (rev 2: darker shell, breathing eye).
     */
    val ROOTKIT_APOSTLE = BossDef(
        "rootkit_apostle", "ROOTKIT APOSTLE", "[\\/]", "Corrupted Ambusher",
        "A corrupted entity that burrows beneath the arena, erupts without warning, and infects safe " +
            "areas with rootkit code, forcing you to constantly relocate.",
        0xFFFF2E9A, 76f, 2400f, 72f, 28f, BossMove.HOVER, listOf(
            BossPhase(P1, 1f, 1.3f, listOf(
                Pattern.Burrow(2.0f, 260f, 0.8f, 95f, 30f),
                Pattern.SpikeEruption(1, 8, 0f, 26f),
                Pattern.Infect(2, 100f, 6f, 18f),
                Pattern.SpikeEruption(3, 6, 60f, 26f)
            ), "PHASE 1"),
            BossPhase(P2, 1.1f, 1.15f, listOf(
                Pattern.Burrow(1.8f, 290f, 0.75f, 100f, 30f, bloomRings = 1),
                Pattern.SpikeEruption(3, 8, 50f, 26f),
                Pattern.Infect(3, 105f, 6.5f, 18f),
                Pattern.Bloom(2, 26f)
            ), "INFECTION"),
            BossPhase(P3, 1.2f, 1.0f, listOf(
                Pattern.Burrow(1.6f, 320f, 0.7f, 105f, 32f, bloomRings = 2),
                Pattern.SpikeEruption(8, 7, 0f, 28f),
                Pattern.Infect(4, 110f, 7f, 20f),
                Pattern.Bloom(3, 28f),
                Pattern.Radial(20, 200f, 12f, waves = 2, rotateDeg = 9f)
            ), "ROOTKIT BLOOM")
        ), euros = 326, score = 7000, role = BossRole.AMBUSHER, tier = 3, armor = 4.2f,
        abilities = listOf("Burrow Drift", "Spike Eruption", "Infected Zone", "Rootkit Bloom")
    )

    /**
     * Nullshade Specter (dossier + Boss Pack Gamma 04, BLACKOUT HUNTER, EXTREME).
     * Kills the room lights with EMP bursts; you track it by its eye-glints and
     * can only lock on while its eyes are open. Level 240 and a 5% rare stalker
     * from level 150. EMP Blackout opens the fight (the room goes extremely
     * dark; owner, 2026-10-10), Eye-Glint Lock windows, Ghost Dash, Static
     * Needles, Spark Ambush from server blocks, Grid Reboot Surge in phase 3.
     */
    val NULLSHADE_SPECTER = BossDef(
        "nullshade", "NULLSHADE SPECTER", "[◣◢]", "Elite Blackout Boss",
        "An elite blackout boss that uses EMP bursts to kill the room lights, forcing players to track " +
            "only its eye-glints and faint attack trails while it hunts from the shadows.",
        0xFFD040FF, 70f, 2320f, 114f, 24f, BossMove.HOVER, listOf(
            BossPhase(P1, 1f, 1.2f, listOf(
                Pattern.GhostDash(0.75f, 720f, 520f, 34f),
                Pattern.Needles(5, 40f, 360f, 16f, bursts = 2),
                Pattern.SparkAmbush(0.9f, 90f, 30f, 10),
                Pattern.Needles(7, 70f, 340f, 16f)
            ), "BLACKOUT INITIATION"),
            BossPhase(P2, 1.15f, 1.0f, listOf(
                Pattern.SparkAmbush(0.8f, 95f, 30f, 12),
                Pattern.GhostDash(0.65f, 780f, 560f, 34f, repeats = 2),
                Pattern.Needles(7, 60f, 380f, 16f, bursts = 2),
                Pattern.SparkAmbush(0.8f, 95f, 30f, 12)
            ), "PHANTOM HUNT"),
            BossPhase(P3, 1.3f, 0.8f, listOf(
                Pattern.GridSurge(3, 0.55f, 420f, 270f, 26f),
                Pattern.GhostDash(0.55f, 840f, 600f, 36f, repeats = 3),
                Pattern.Needles(9, 80f, 400f, 17f, bursts = 3, burstGap = 0.25f),
                Pattern.SparkAmbush(0.7f, 100f, 32f, 14)
            ), "GRID REBOOT FRENZY")
        ), euros = 435, score = 9000, role = BossRole.BLACKOUT_HUNTER, tier = 4, armor = 3.6f, stealth = true,
        abilities = listOf("EMP Blackout", "Eye-Glint Lock", "Ghost Dash", "Static Needles")
    )

    /**
     * Ransom King (Boss Pack Gamma 02, LOCKDOWN). A malware king that locks down
     * sections of the arena, creates key zones and punishes movement with
     * seizing attacks; poor positioning means confinement and heavy penalties.
     * Starts ENCRYPTED (takes a quarter damage) until you capture every key
     * zone; Lock Grid walls seal sections, Royal Seizure roots and cages you,
     * Ransom Pulse ENCRYPTS you so moving bursts the ransom on you.
     */
    val RANSOM_KING = BossDef(
        "ransom_king", "RANSOM KING", "[K$]", "Malware King",
        "A malware king that locks down sections of the arena, creates key zones, and punishes movement " +
            "with seizing attacks. Poor positioning leads to confinement and heavy penalties.",
        0xFFFF3B3B, 74f, 2640f, 60f, 29f, BossMove.HOVER, listOf(
            BossPhase(P1, 1f, 1.4f, listOf(
                Pattern.KeyZone(2, 58f, 11f),
                Pattern.RansomPulse(2, 0.7f, 420f, 230f, 22f),
                Pattern.LockGrid(1, 7f),
                Pattern.RoyalSeizure(105f, 1.1f, 28f, 1.0f, 3.5f)
            ), "PHASE 1"),
            BossPhase(P2, 1.1f, 1.2f, listOf(
                Pattern.KeyZone(3, 58f, 11f),
                Pattern.LockGrid(2, 7f),
                Pattern.RoyalSeizure(110f, 1.0f, 28f, 1.1f, 3.5f),
                Pattern.RansomPulse(3, 0.6f, 440f, 240f, 22f),
                Pattern.Aimed(5, 40f, 300f, 14f, bursts = 2)
            ), "RANSOM DEMAND"),
            BossPhase(P3, 1.2f, 1.0f, listOf(
                Pattern.KeyZone(3, 56f, 10f),
                Pattern.LockGrid(3, 6.5f),
                Pattern.RoyalSeizure(115f, 0.9f, 30f, 1.2f, 3.5f),
                Pattern.RansomPulse(3, 0.5f, 460f, 250f, 24f),
                Pattern.Radial(18, 190f, 13f, waves = 2, rotateDeg = 10f)
            ), "TOTAL LOCKDOWN")
        ), euros = 362, score = 8000, role = BossRole.LOCKDOWN, tier = 3, armor = 5.1f,
        abilities = listOf("Key Zone", "Lock Grid", "Royal Seizure", "Ransom Pulse"), keyShield = true
    )

    /**
     * Spectral Firewall (Boss Pack Gamma 03, AREA DENIAL). A rotating firewall
     * boss that constricts the arena with searing flame walls and heat zones,
     * steadily shrinking safe space until only a small area remains. Its
     * orbiting ring blocks shots (fire through the gaps: 3/2/1 by phase) and
     * is launched outward as the Firewall Ring (owner spec), leaving the core
     * exposed; Burn Sector, Heat Collapse and Purge Spin set you ON FIRE.
     */
    val SPECTRAL_FIREWALL = BossDef(
        "spectral_firewall", "SPECTRAL FIREWALL", "[(#)]", "Rotating Firewall",
        "A rotating firewall boss that constricts the arena with searing flame walls and heat zones, " +
            "steadily shrinking safe space until only a small area remains.",
        0xFFFF5A1F, 74f, 2720f, 60f, 31f, BossMove.DRIFT, listOf(
            BossPhase(P1, 1f, 1.4f, listOf(
                Pattern.FirewallRing(1, 3, 170f, 26f),
                Pattern.BurnSector(1, 70f, 1.2f, 2.5f, 24f),
                Pattern.PurgeSpin(3, 0.9f, 3.0f, 240f, 24f),
                Pattern.Spiral(3, 2.4f, 9f, 100f, 190f, 11f)
            ), "PHASE 1"),
            BossPhase(P2, 1.1f, 1.2f, listOf(
                // Owner, 2026-10-10: build to 3 rings in phase 2 and 4 in phase 3, spread out (~200 units apart).
                Pattern.FirewallRing(3, 2, 180f, 27f, waveGap = 1.15f),
                Pattern.BurnSector(2, 70f, 1.1f, 2.6f, 25f),
                Pattern.HeatCollapse(2, 150f, 28f),
                Pattern.PurgeSpin(4, 0.85f, 3.2f, 300f, 25f)
            ), "HEAT RISING"),
            BossPhase(P3, 1.2f, 1.0f, listOf(
                Pattern.FirewallRing(4, 2, 190f, 28f, waveGap = 1.05f),
                Pattern.BurnSector(3, 60f, 1.0f, 2.8f, 26f),
                Pattern.HeatCollapse(2, 165f, 30f),
                Pattern.PurgeSpin(4, 0.8f, 3.4f, 360f, 26f),
                Pattern.Spiral(4, 3f, 12f, -120f, 200f, 12f)
            ), "MELTDOWN")
        ), euros = 380, score = 8500, role = BossRole.AREA_DENIAL, tier = 3, armor = 5.4f,
        abilities = listOf("Firewall Ring", "Burn Sector", "Heat Collapse", "Purge Spin"), firewallRing = true
    )

    /**
     * Pulse Bishop (Boss Pack Beta 01, CONTROLLER). A teleporting prelate that
     * controls space with precision beams and punishes predictable movement:
     * warps onto your lane, fires cross beams, seeds mines that arm and
     * detonate, and drags you in before a blinding flash.
     */
    val PULSE_BISHOP = BossDef(
        "pulse_bishop", "PULSE BISHOP", "[+]", "Teleporting Prelate",
        "A teleporting prelate that controls space with precision beams. Manipulates the battlefield and " +
            "punishes predictable movement.",
        0xFFFF2D6A, 80f, 2240f, 84f, 30f, BossMove.HOVER, listOf(
            BossPhase(P1, 1f, 1.3f, listOf(
                Pattern.LineWarp(0.7f, 26f),
                Pattern.CrossBeam(1.0f, 1.6f, 0f, 26f, 26f),
                Pattern.BishopMines(4, 1.0f, 9f, 85f, 26f),
                Pattern.Aimed(5, 40f, 300f, 14f, bursts = 2)
            ), "PHASE 1"),
            BossPhase(P2, 1.1f, 1.15f, listOf(
                Pattern.LineWarp(0.6f, 27f),
                Pattern.CrossBeam(0.9f, 2.4f, 60f, 26f, 27f),
                Pattern.ConvergenceFlash(1.3f, 150f, 190f, 34f),
                Pattern.BishopMines(5, 0.9f, 9f, 85f, 27f)
            ), "SACRED GEOMETRY"),
            BossPhase(P3, 1.2f, 1.0f, listOf(
                Pattern.LineWarp(0.55f, 28f),
                Pattern.CrossBeam(0.85f, 2.8f, 90f, 28f, 28f, diagonal = true),
                Pattern.BishopMines(6, 0.8f, 9f, 90f, 28f),
                Pattern.ConvergenceFlash(1.4f, 170f, 210f, 36f),
                Pattern.LineWarp(0.5f, 28f)
            ), "EXCOMMUNICATION")
        ), euros = 344, score = 7500, role = BossRole.CONTROLLER, tier = 3, armor = 3f,
        abilities = listOf("Line Warp", "Cross Beam", "Bishop Mines", "Convergence Flash")
    )

    /**
     * Packet Reaper (Boss Pack Alpha 02, ASSASSIN, "fast, relentless, hunts
     * backlines"). A rapid assassin mech that dashes through lanes and throws
     * packet scythes, punishing isolated targets.
     */
    val PACKET_REAPER = BossDef(
        "packet_reaper", "PACKET REAPER", "[)>", "Backline Assassin",
        "A rapid assassin that dashes through lanes and throws packet scythes, punishing isolated targets.",
        0xFFFF2E9A, 66f, 1920f, 120f, 32f, BossMove.HOVER, listOf(
            BossPhase(P1, 1f, 1.1f, listOf(
                Pattern.DashSlash(0.6f, 820f, 520f, 32f),
                Pattern.Scythes(2, 380f, 1.6f, 22f),
                Pattern.BacklineDive(0.75f, 900f, 32f),
                Pattern.Scythes(3, 400f, 1.6f, 22f, spreadDeg = 70f)
            ), "PHASE 1"),
            BossPhase(P2, 1.15f, 0.95f, listOf(
                Pattern.DashSlash(0.55f, 860f, 560f, 33f, repeats = 2, trail = true),
                Pattern.Scythes(4, 420f, 1.5f, 23f, spreadDeg = 90f),
                Pattern.BacklineDive(0.65f, 950f, 33f)
            ), "LANE HUNT"),
            BossPhase(P3, 1.3f, 0.8f, listOf(
                Pattern.DashSlash(0.5f, 900f, 600f, 34f, repeats = 3, trail = true),
                Pattern.BacklineDive(0.55f, 1000f, 34f),
                Pattern.Scythes(5, 440f, 1.4f, 24f, spreadDeg = 120f),
                Pattern.BacklineDive(0.55f, 1000f, 34f)
            ), "NO ESCAPE")
        ), euros = 308, score = 7000, role = BossRole.ASSASSIN, tier = 3, armor = 2.4f,
        abilities = listOf("Dash Slash", "Packet Scythes", "Backline Dive", "Trail Burst")
    )

    /**
     * Worm Queen (Boss Pack Alpha 04, SUMMONER, "spawns, corrupts, overwhelms").
     * A broodmother that spawns corruption swarms and leaves trails that infest
     * the arena over time.
     */
    val WORM_QUEEN = BossDef(
        "worm_queen", "WORM QUEEN", "[@@]", "Broodmother",
        "A broodmother that spawns corruption swarms and leaves trails that infest the arena over time.",
        0xFFFF2D9A, 80f, 2880f, 60f, 26f, BossMove.CHASE, listOf(
            BossPhase(P1, 1f, 1.4f, listOf(
                Pattern.SwarmHatch(6, 2.2f, 4),
                Pattern.CorruptionTrail(3f, 1.6f, 55f, 7f, 16f),
                Pattern.Bloom(2, 24f, lanes = 3),
                Pattern.Radial(14, 180f, 11f, waves = 2, rotateDeg = 12f)
            ), "PHASE 1"),
            BossPhase(P2, 1.1f, 1.2f, listOf(
                Pattern.SwarmHatch(8, 2.0f, 5),
                Pattern.QueenRoar(380f, 230f, 24f, 1.6f, 4f),
                Pattern.CorruptionTrail(3.5f, 1.7f, 60f, 8f, 18f),
                Pattern.Bloom(3, 25f, lanes = 3)
            ), "BROOD SURGE"),
            BossPhase(P3, 1.2f, 1.0f, listOf(
                Pattern.SwarmHatch(10, 1.8f, 5),
                Pattern.QueenRoar(420f, 250f, 26f, 1.8f, 5f),
                Pattern.CorruptionTrail(4f, 1.8f, 62f, 9f, 20f),
                Pattern.Bloom(3, 26f, lanes = 2)
            ), "INFESTATION")
        ), euros = 326, score = 7500, role = BossRole.SUMMONER, tier = 3, armor = 4.8f,
        abilities = listOf("Swarm Hatch", "Corruption Trail", "Spike Burst", "Queen Roar"), layEggs = true
    )

    /**
     * Glitch Forge (Boss Pack Beta 02, SUMMONER). A corrupted server core that
     * warps reality with holograms and unstable terrain: decoy clones, corrupted
     * floor tiles, homing data-cube barrages and a pulse that corrupts the floor.
     */
    val GLITCH_FORGE = BossDef(
        "glitch_forge", "GLITCH FORGE", "[#!]", "Reality Forge",
        "A corrupted server core that warps reality with holograms and unstable terrain. Floods the " +
            "arena with decoys and data hazards.",
        0xFFFF2E8A, 80f, 2480f, 48f, 24f, BossMove.HOVER, listOf(
            BossPhase(P1, 1f, 1.4f, listOf(
                Pattern.Summon("holo_clone", 2),
                Pattern.CorruptFloor(5, 1.2f, 3f, 18f),
                Pattern.CubeBarrage(2, 3, 220f, 2.0f, 16f),
                Pattern.CorePulse(380f, 230f, 22f, 2.5f, 16f)
            ), "PHASE 1"),
            BossPhase(P2, 1.1f, 1.2f, listOf(
                Pattern.Summon("holo_clone", 3),
                Pattern.CorruptFloor(7, 1.1f, 3.2f, 19f),
                Pattern.CubeBarrage(3, 4, 230f, 2.2f, 17f),
                Pattern.CorePulse(420f, 240f, 24f, 2.8f, 18f)
            ), "REALITY FRACTURE"),
            BossPhase(P3, 1.2f, 1.0f, listOf(
                Pattern.Summon("holo_clone", 4),
                Pattern.CorruptFloor(9, 1.0f, 3.4f, 20f),
                Pattern.CubeBarrage(4, 5, 240f, 2.4f, 18f, gap = 0.4f),
                Pattern.CorePulse(460f, 250f, 26f, 3f, 20f)
            ), "TOTAL CORRUPTION")
        ), euros = 344, score = 7500, role = BossRole.SUMMONER, tier = 3, armor = 5.4f,
        abilities = listOf("Decoy Clone", "Corrupt Floor", "Cube Barrage", "Core Pulse")
    )

    /**
     * Botnet Monarch (Boss Pack Beta 04, COMMANDER). A supreme drone commander that
     * overwhelms with numbers: an orbiting ring of armoured drones, drone waves,
     * orbital strikes and a synchronised burst from every linked drone.
     */
    val BOTNET_MONARCH = BossDef(
        "botnet_monarch", "BOTNET MONARCH", "[ooO]", "Supreme Drone Commander",
        "A supreme drone commander that overwhelms with numbers. Orchestrates attack patterns, summons " +
            "swarms, and controls the skies.",
        0xFFFF3A3A, 76f, 2640f, 60f, 27f, BossMove.HOVER, listOf(
            BossPhase(P1, 1f, 1.4f, listOf(
                Pattern.DroneRing(4),
                Pattern.OrbitalBarrage(3, 80f, 1.2f, 26f),
                Pattern.Summon("bot", 5),
                Pattern.SyncBurst(1.2f, 2, 260f, 16f)
            ), "PHASE 1"),
            BossPhase(P2, 1.1f, 1.2f, listOf(
                Pattern.DroneRing(6),
                Pattern.OrbitalBarrage(4, 85f, 1.1f, 27f, volleys = 2),
                Pattern.SyncBurst(1.1f, 3, 280f, 17f),
                Pattern.Summon("bot", 7)
            ), "COMMAND NETWORK"),
            BossPhase(P3, 1.2f, 1.0f, listOf(
                Pattern.DroneRing(8),
                Pattern.SyncBurst(1.0f, 3, 300f, 18f),
                Pattern.OrbitalBarrage(5, 90f, 1.0f, 28f, volleys = 3, gap = 0.6f),
                Pattern.Summon("bot", 8)
            ), "TOTAL BOTNET")
        ), euros = 380, score = 8000, role = BossRole.COMMANDER, tier = 3, armor = 4.5f,
        abilities = listOf("Drone Ring", "Summon Wave", "Orbital Barrage", "Sync Burst")
    )

    /**
     * Circuit Hydra (Boss Pack Alpha 03, AREA CONTROL, "splits, beams, dominates
     * space"). A multi-core serpent that splits into beam heads, filling the
     * arena with overlapping fire lanes.
     */
    val CIRCUIT_HYDRA = BossDef(
        "circuit_hydra", "CIRCUIT HYDRA", "[Θ~]", "Multi-Core Serpent",
        "A multi-core serpent that splits into beam heads, filling the arena with overlapping fire lanes.",
        0xFF4CFF6A, 66f, 3400f, 72f, 30f, BossMove.SWAY, listOf(
            BossPhase(P1, 1f, 1.4f, listOf(
                Pattern.BeamArc(1, 1.0f, 1.8f, 60f, 0f, 26f),
                Pattern.SegmentBurst(6, 190f, 13f),
                Pattern.HeadBite(1, 0.9f, 34f)
            ), "PHASE 1"),
            BossPhase(P2, 1.1f, 1.2f, listOf(
                Pattern.BeamArc(1, 0.9f, 2.0f, 70f, 0f, 27f),
                Pattern.SegmentBurst(7, 200f, 14f),
                Pattern.HeadBite(2, 0.8f, 36f)
            ), "FOURTH HEAD"),
            BossPhase(P3, 1.2f, 1.0f, listOf(
                Pattern.BeamArc(1, 0.85f, 2.2f, 80f, 0f, 28f),
                Pattern.SegmentBurst(8, 210f, 15f),
                Pattern.HeadBite(2, 0.7f, 38f)
            ), "HYDRA STORM")
        ), euros = 362, score = 8000, role = BossRole.AREA_CONTROL, tier = 3, armor = 4.2f,
        abilities = listOf("Head Shield", "Beam Arc", "Neck Volley", "Head Bite"), segments = 10, headShield = true
    )

    /**
     * Black Ice Overlord (Boss Pack Beta 03, STATUS). An ancient AI sealed in ice,
     * wielding absolute cold: slows movement, blankets the arena in frost and
     * rains crystal artillery. CHILL stacks slow you; five stacks freeze you.
     */
    val BLACK_ICE_OVERLORD = BossDef(
        "black_ice_overlord", "BLACK ICE OVERLORD", "[*]", "Frozen Ancient AI",
        "An ancient AI sealed in ice, wielding absolute cold. Slows movement, blankets the arena in frost, " +
            "and rains crystal artillery.",
        0xFF3AB8FF, 80f, 2800f, 48f, 31f, BossMove.HOVER, listOf(
            BossPhase(P1, 1f, 1.4f, listOf(
                Pattern.IceLaser(2, 1.0f, 2.2f, 90f, 24f),
                Pattern.FreezePatch(3, 105f, 6f),
                Pattern.CrystalVolley(3, 80f, 1.2f, 24f),
                Pattern.Radial(16, 170f, 12f, waves = 2, rotateDeg = 11f)
            ), "PHASE 1"),
            BossPhase(P2, 1.05f, 1.25f, listOf(
                Pattern.PermafrostShell(0.08f, 8f, 230f),
                Pattern.IceLaser(3, 0.95f, 2.4f, 110f, 25f),
                Pattern.FreezePatch(4, 110f, 6.5f),
                Pattern.CrystalVolley(4, 85f, 1.1f, 25f, volleys = 2)
            ), "DEEP FREEZE"),
            BossPhase(P3, 1.1f, 1.05f, listOf(
                Pattern.PermafrostShell(0.07f, 7f, 260f),
                Pattern.IceLaser(4, 0.9f, 2.6f, 130f, 26f),
                Pattern.CrystalVolley(5, 85f, 1.0f, 26f, volleys = 3),
                Pattern.FreezePatch(5, 115f, 7f)
            ), "ABSOLUTE ZERO")
        ), euros = 380, score = 8500, role = BossRole.STATUS, tier = 3, armor = 5.4f,
        abilities = listOf("Ice Laser", "Freeze Patch", "Crystal Volley", "Permafrost Shell")
    )

    /**
     * Planned levels 130–240 (log §1 D1, threat order), one slot per 10 levels.
     * Nullshade Specter closes the run at 240 (and stalks from 150 as a rare boss).
     */
    val SLOTS: List<String> = listOf(
        "vault_sentinel", "rootkit_apostle", "pulse_bishop", "packet_reaper", "worm_queen", "glitch_forge",
        "botnet_monarch", "circuit_hydra", "ransom_king", "black_ice_overlord", "spectral_firewall", "nullshade"
    )

    /** Built bosses, in slot order. */
    val all: List<BossDef> = listOf(VAULT_SENTINEL, ROOTKIT_APOSTLE, PULSE_BISHOP, PACKET_REAPER, WORM_QUEEN, GLITCH_FORGE, BOTNET_MONARCH, CIRCUIT_HYDRA, RANSOM_KING, BLACK_ICE_OVERLORD, SPECTRAL_FIREWALL, NULLSHADE_SPECTER).sortedBy { SLOTS.indexOf(it.id) }

    /** The built boss for slot [i] (0 = level 130), or null if that boss isn't built yet. */
    fun inSlot(i: Int): BossDef? = SLOTS.getOrNull(i)?.let { id -> all.firstOrNull { it.id == id } }

    /** Every expansion boss defined so far, built or not (design renders). */
    val designed: List<BossDef> get() = all
}
