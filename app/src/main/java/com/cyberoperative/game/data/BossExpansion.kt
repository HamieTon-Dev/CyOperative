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
     * deploys cover blocks, sweeps the arena with laser barriers, boxes you
     * in and shells marked spots. Patterns here are provisional until the
     * Stage B systems (sweep beams, lobbed shells, dynamic cover) land.
     */
    val VAULT_SENTINEL = BossDef(
        "vault_sentinel", "VAULT SENTINEL", "[▣]", "Fortress Server Guardian",
        "A fortress-like server guardian that deploys cover blocks and sweeps the arena with " +
            "lethal laser barriers. Holds the line, controls space.",
        0xFFFF2D55, 60f, 2560f, 42f, 28f, BossMove.HOVER, listOf(
            BossPhase(P1, 1f, 1.5f, listOf(
                Pattern.Beam(1.0f, 0.5f, 26f, 26f, count = 3, spreadDeg = 40f),
                Pattern.Blasts(4, 90f, 1.2f, 24f)
            ), "PHASE 1"),
            BossPhase(P2, 1.05f, 1.3f, listOf(
                Pattern.Beam(0.9f, 0.5f, 26f, 26f, count = 5, spreadDeg = 70f),
                Pattern.Blasts(6, 90f, 1.1f, 24f),
                Pattern.Radial(16, 170f, 12f, waves = 2, rotateDeg = 11f)
            ), "LOCKDOWN"),
            BossPhase(P3, 1.1f, 1.1f, listOf(
                Pattern.Beam(0.8f, 0.6f, 28f, 28f, count = 7, spreadDeg = 120f),
                Pattern.Blasts(8, 95f, 1.0f, 26f),
                Pattern.ShockRing(340f, 210f, 24f)
            ), "VAULT BREACHED")
        ), euros = 326, score = 7000, role = BossRole.TANK, tier = 3, armor = 6f,
        abilities = listOf("Cover Deploy", "Laser Sweep", "Lockdown Cube", "Pulse Mortar")
    )

    val all: List<BossDef> = emptyList()

    /** Every expansion boss defined so far, built or not (design renders). */
    val designed: List<BossDef> = listOf(VAULT_SENTINEL)
}
