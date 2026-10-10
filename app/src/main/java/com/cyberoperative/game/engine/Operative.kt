package com.cyberoperative.game.engine

import com.cyberoperative.game.core.MathUtil

/**
 * Everything that belongs to one operative on the field. Solo runs have one;
 * co-op runs (owner, 2026-10-08) have two, each with its own build, picks and
 * weapon timers. [GameEngine] works on one of them at a time ("current"), so
 * all the single-player rules apply to either operative unchanged.
 */
class Operative(val index: Int, baseStats: RunStats) {
    val build = RunBuild(baseStats)

    var px = 0f
    var py = 0f
    var hp = 100f
    var firewall = 0f
    var facing = -MathUtil.PI / 2f
    var moving = false
    var invuln = 0f
    var hurtFlash = 0f
    /** SEIZED by Royal Seizure: can't move for this long. */
    var rooted = 0f
    /** ENCRYPTED by Ransom Pulse: time left, movement charge 0..1 and the burst it will deal. */
    var encrypted = 0f
    var encryptCharge = 0f
    var encryptDamage = 0f
    var inputX = 0f
    var inputY = 0f
    var fireCooldown = 0f
    var stillTime = 0f
    var followUpLeft = 0
    var followUpTimer = 0f
    var lanceTimer = 0f
    var empTimer = 0f
    var sinceDamage = 99f
    var firewallWasUp = false
    var orbAngle = 0f
    var bladeAngle = 0f
    var orbBoltTimer = 0f
    var targetUid = -1
    var beamActive = false
    var beamX2 = 0f
    var beamY2 = 0f
    var beamTick = 0f
    var beamHeat = 0f
    var beamCooldown = 0f
    var mineTimer = 0f
    var missileTimer = 0f
    var arcTimer = 0f
    var railTimer = 0f
    var strikeTimer = 0f
    val weaponTimers = HashMap<String, Float>()
    internal val spirals = ArrayList<Any>()

    /** Enemy navigation toward this operative. */
    val path = Pathfinder()

    // --- Level-up picks (each operative chooses its own cards) ---------------
    var pendingUpgrades = 0
    var offer: List<UpgradeOffer> = emptyList()
        set(value) {
            field = value
            offerSerial++
        }
    var offerSerial = 0
    var rewardBatchTotal = 0
    var rewardBatchTaken = 0
    var rerollsLeft = 0

    // --- Co-op ------------------------------------------------------------------
    /** HP ran out while the partner is still up: waits for a revive. */
    var downed = false
    /** Seconds the partner has stood next to this downed operative. */
    var reviveProgress = 0f
    /** Partner left the game: removed from the field for good. */
    var gone = false

    /** Moved by a remote device (co-op guest): [netX]/[netY] is where it says it is. */
    var remote = false
    var netX = 0f
    var netY = 0f
    var netFacing = 0f
    var netMoving = false

    val alive: Boolean get() = !downed && !gone
}
