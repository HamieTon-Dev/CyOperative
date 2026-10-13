package com.cyberoperative.game.engine

/**
 * Armor (owner, 2026-10-13; Diablo 3's formula): the share of damage cut is
 * armor / (armor + 50 × monster level). Armor equal to 50 × the monster level cuts 50%;
 * double that cuts 66.7%. It never reaches 100%, and it thins out as monsters out-level you.
 */
object Armor {
    const val K = 50f
    /** Used where no fight is in view (stat sheet, balance estimates). */
    const val NOMINAL_ML = 30

    fun reduction(armor: Float, monsterLevel: Int): Float {
        if (armor <= 0f) return 0f
        return armor / (armor + K * monsterLevel.coerceAtLeast(1))
    }
}
