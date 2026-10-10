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

    val all: List<BossDef> = listOf(VAULT_SENTINEL)

    /** Every expansion boss defined so far, built or not (design renders). */
    val designed: List<BossDef> = listOf(VAULT_SENTINEL)
}
