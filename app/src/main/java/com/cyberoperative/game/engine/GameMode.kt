package com.cyberoperative.game.engine

/**
 * CAMPAIGN is the main game (owner, 2026-10-07): discrete levels with a fixed
 * number of threats, a power-up after every clear, then the gate at the top of
 * the room opens and the next generated room slides in.
 *
 * ENDLESS keeps one room and never stops spawning: survive as long as you can,
 * with upgrades earned from data (XP) mid-fight and a boss every 10 stages.
 */
enum class GameMode(val label: String) { CAMPAIGN("CAMPAIGN"), ENDLESS("ENDLESS") }
