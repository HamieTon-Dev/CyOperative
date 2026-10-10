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
     * Patterns are provisional until Key Zone / Lock Grid / Royal Seizure /
     * Ransom Pulse are built.
     */
    val RANSOM_KING = BossDef(
        "ransom_king", "RANSOM KING", "[K$]", "Malware King",
        "A malware king that locks down sections of the arena, creates key zones, and punishes movement " +
            "with seizing attacks. Poor positioning leads to confinement and heavy penalties.",
        0xFFFF3B3B, 74f, 2640f, 60f, 29f, BossMove.HOVER, listOf(
            BossPhase(P1, 1f, 1.4f, listOf(
                Pattern.CoverDeploy(4, 7f),
                Pattern.ShockRing(360f, 210f, 24f)
            ), "PHASE 1"),
            BossPhase(P2, 1.1f, 1.2f, listOf(
                Pattern.Lockdown(110f, 5f),
                Pattern.ShockRing(380f, 220f, 24f),
                Pattern.Aimed(5, 40f, 300f, 14f, bursts = 2)
            ), "RANSOM DEMAND"),
            BossPhase(P3, 1.2f, 1.0f, listOf(
                Pattern.Lockdown(105f, 4.5f),
                Pattern.ShockRing(400f, 240f, 26f),
                Pattern.Radial(18, 190f, 13f, waves = 2, rotateDeg = 10f)
            ), "TOTAL LOCKDOWN")
        ), euros = 362, score = 8000, role = BossRole.LOCKDOWN, tier = 3, armor = 5.1f,
        abilities = listOf("Key Zone", "Lock Grid", "Royal Seizure", "Ransom Pulse")
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
    val all: List<BossDef> = listOf(VAULT_SENTINEL, ROOTKIT_APOSTLE, NULLSHADE_SPECTER).sortedBy { SLOTS.indexOf(it.id) }

    /** The built boss for slot [i] (0 = level 130), or null if that boss isn't built yet. */
    fun inSlot(i: Int): BossDef? = SLOTS.getOrNull(i)?.let { id -> all.firstOrNull { it.id == id } }

    /** Every expansion boss defined so far, built or not (design renders). */
    val designed: List<BossDef> = listOf(VAULT_SENTINEL, ROOTKIT_APOSTLE, NULLSHADE_SPECTER, RANSOM_KING)
}
