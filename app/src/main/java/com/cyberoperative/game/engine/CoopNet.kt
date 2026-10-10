package com.cyberoperative.game.engine

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

/**
 * Co-op wire format (owner, 2026-10-08). The host runs the real simulation and
 * sends [CoopWorld] snapshots ~10 times a second; the guest sends [CoopInput]
 * (its own position, picks) back. Both are small deflated binary blobs so a
 * session costs as little mobile data and Firebase bandwidth as possible.
 *
 * Positions are 0.5 px fixed point in 16 bits, angles and fractions in 8–16
 * bits; only what the renderer and HUD draw is sent.
 */
class CoopWorld {
    var seq = 0
    // Run
    var level = 1
    var levelSeed = 0L
    var previousArenaId: String? = null
    var previousEvent = false
    var vaultCracked = false
    var vaultOpening = 0f
    var phase = Phase.COMBAT
    var phaseTimer = 0f
    var slideIn = 0f
    var score = 0L
    var kills = 0
    var euros = 0
    var diamonds = 0
    var bosses = 0
    var elites = 0
    var events = 0
    var levelKills = 0
    var levelSpawned = 0
    var waveIndex = 0
    var runLevel = 1
    var xp = 0f
    var runSeconds = 0f
    var timedRemaining = 0f
    var portalOpen = false
    var banner = ""
    var bannerSub = ""
    var bannerTimer = 0f
    var bossPhaseLabel = ""
    var bossUid = -1
    val ops = ArrayList<NetOp>(2)
    val enemies = ArrayList<NetEnemy>(96)
    val shots = ArrayList<NetShot>(200)
    val hazards = ArrayList<NetHazard>(64)
    val zaps = ArrayList<NetZap>(40)
    val texts = ArrayList<NetText>(32)
    val pulses = ArrayList<NetPulse>(24)
    val sounds = ArrayList<GameSound>(16)
    /** Boss barrier cubes: x, y, half, rise, life, timer per cube (v3). */
    val barriers = ArrayList<FloatArray>(24)
    /** Blackout state (v4): room darkness, flicker and the stealth boss's veil. */
    var darkness = 0f
    var lightFlicker = 0f
    var bossVeil = 0f
    /** Ransom shield (v5): 1 up, 0 broken, -1 none. */
    var bossShield = -1f
    /** Spectral Firewall ring (v6): angle, plates filled (-1 none), launched. */
    var bossRingAngle = 0f
    var bossRingFilled = -1
    var bossRingOut = false
}

class NetOp {
    var x = 0f; var y = 0f; var hp = 0f; var firewall = 0f; var facing = 0f
    var moving = false; var invuln = 0f; var hurtFlash = 0f
    var orbAngle = 0f; var bladeAngle = 0f; var targetUid = -1
    var beamActive = false; var beamX2 = 0f; var beamY2 = 0f; var beamHeat = 0f; var beamCooldown = 0f
    var downed = false; var reviveProgress = 0f; var gone = false
    /** Statuses (v5): SEIZED time, ENCRYPTED time and its burst meter. */
    var rooted = 0f; var encrypted = 0f; var encryptCharge = 0f
    /** ON FIRE time left (v6). */
    var burning = 0f
    /** PULLED (v7): time left, target and strength. */
    var pulled = 0f; var pullX = 0f; var pullY = 0f; var pullStrength = 0f
    var pending = 0; var rerolls = 0; var batchTotal = 0; var batchTaken = 0
    /** Owned upgrades: index into Upgrades.all → level. */
    val owned = LinkedHashMap<Int, Int>()
    /** Current cards: (index into Upgrades.all, next level). */
    val offer = ArrayList<Pair<Int, Int>>(3)
}

class NetEnemy {
    var uid = 0; var def = 0; var elite = -1
    var bossIndex = -1; var bossCycle = 0; var bossPhase = 0; var bossGlitched = false
    var x = 0f; var y = 0f; var hp = 0f; var maxHp = 1f; var radius = 16f
    var state = AiState.MOVE; var stateTimer = 0f; var hitFlash = 0f
}

class NetShot {
    var kind = ProjKind.BOLT; var friendly = true; var x = 0f; var y = 0f; var vx = 0f; var vy = 0f
    var radius = 6f; var crit = false; var homing = false; var tint = 0L; var armTimer = 0f; var life = 1f; var owner = 0
}

class NetHazard {
    var kind = HazardKind.LINE; var x = 0f; var y = 0f; var x2 = 0f; var y2 = 0f
    var radius = 0f; var maxRadius = 0f; var timer = 0f; var duration = 0f; var windup = 0f; var color = 0L
}

class NetZap {
    var kind = ZapKind.ARC; var x = 0f; var y = 0f; var x2 = 0f; var y2 = 0f
    var radius = 0f; var timer = 0f; var duration = 0f; var landed = false; var seed = 0; var color = 0L
}

class NetText { var x = 0f; var y = 0f; var text = ""; var kind = TextKind.NORMAL; var life = 0f }
class NetPulse { var x = 0f; var y = 0f; var radius = 0f; var maxRadius = 0f; var life = 0f; var maxLife = 0f; var color = 0L }

/** Guest → host: the guest moves its own operative (instant feel); the host trusts it within the arena. */
data class CoopInput(
    val seq: Int = 0,
    val x: Float = 0f,
    val y: Float = 0f,
    val facing: Float = 0f,
    val moving: Boolean = false,
    /** Bumped on every card pick; [pickIndex] is the card. */
    val pickSerial: Int = 0,
    val pickIndex: Int = 0,
    val rerollSerial: Int = 0,
    /** Level the guest's position belongs to: positions from a previous room are ignored. */
    val level: Int = 1,
    /** Weapon swapped out by this pick (index into Upgrades.all), or -1. */
    val replaceIndex: Int = -1
)

object CoopCodec {
    /** Caps keep a snapshot small even in the busiest boss fights. */
    const val MAX_HOSTILE_SHOTS = 150
    const val MAX_FRIENDLY_SHOTS = 70
    const val MAX_TEXTS = 24

    // --- Fixed-point helpers -----------------------------------------------------
    private fun DataOutputStream.pos(v: Float) = writeShort((v * 2f).toInt().coerceIn(-32768, 32767))
    private fun DataInputStream.pos(): Float = readShort() / 2f
    private fun DataOutputStream.vel(v: Float) = writeShort((v / 4f).toInt().coerceIn(-32768, 32767))
    private fun DataInputStream.vel(): Float = readShort() * 4f
    private fun DataOutputStream.ang(v: Float) {
        var a = v % MathUtilTau
        if (a < 0f) a += MathUtilTau
        writeShort((a / MathUtilTau * 65535f).toInt())
    }
    private fun DataInputStream.ang(): Float = readUnsignedShort() / 65535f * MathUtilTau
    /** Seconds 0..~65 at 1 ms. */
    private fun DataOutputStream.sec(v: Float) = writeShort((v * 1000f).toInt().coerceIn(0, 65535))
    private fun DataInputStream.sec(): Float = readUnsignedShort() / 1000f
    private fun DataOutputStream.var32(v: Int) {
        var x = v
        while (x and 0x7F.inv() != 0) { writeByte((x and 0x7F) or 0x80); x = x ushr 7 }
        writeByte(x)
    }
    private fun DataInputStream.var32(): Int {
        var shift = 0
        var r = 0
        while (true) {
            val b = readUnsignedByte()
            r = r or ((b and 0x7F) shl shift)
            if (b and 0x80 == 0) return r
            shift += 7
        }
    }
    private fun DataOutputStream.str(s: String) { val b = s.toByteArray(Charsets.UTF_8); var32(b.size); write(b) }
    private fun DataInputStream.str(): String { val n = var32(); val b = ByteArray(n); readFully(b); return String(b, Charsets.UTF_8) }
    private const val MathUtilTau = (Math.PI * 2).toFloat()

    fun encodeWorld(w: CoopWorld): ByteArray {
        val bytes = ByteArrayOutputStream(4096)
        DataOutputStream(DeflaterOutputStream(bytes)).use { o ->
            o.writeByte(VERSION)
            o.var32(w.seq)
            o.var32(w.level); o.writeLong(w.levelSeed)
            o.str(w.previousArenaId ?: ""); o.writeBoolean(w.previousEvent)
            o.writeBoolean(w.vaultCracked); o.sec(w.vaultOpening)
            o.writeByte(w.phase.ordinal); o.sec(w.phaseTimer); o.sec(w.slideIn)
            o.writeLong(w.score); o.var32(w.kills); o.var32(w.euros); o.var32(w.diamonds)
            o.var32(w.bosses); o.var32(w.elites); o.var32(w.events)
            o.var32(w.levelKills); o.var32(w.levelSpawned); o.var32(w.waveIndex)
            o.var32(w.runLevel); o.writeFloat(w.xp); o.writeFloat(w.runSeconds); o.writeFloat(w.timedRemaining)
            o.writeBoolean(w.portalOpen)
            o.str(w.banner); o.str(w.bannerSub); o.sec(w.bannerTimer)
            o.str(w.bossPhaseLabel); o.var32(w.bossUid + 1)

            o.writeByte(w.ops.size)
            for (p in w.ops) {
                o.pos(p.x); o.pos(p.y); o.writeFloat(p.hp); o.writeFloat(p.firewall); o.ang(p.facing)
                o.writeByte((if (p.moving) 1 else 0) or (if (p.beamActive) 2 else 0) or (if (p.downed) 4 else 0) or (if (p.gone) 8 else 0))
                o.sec(p.invuln); o.sec(p.hurtFlash); o.ang(p.orbAngle); o.ang(p.bladeAngle); o.var32(p.targetUid + 1)
                if (p.beamActive) { o.pos(p.beamX2); o.pos(p.beamY2) }
                o.sec(p.beamHeat); o.sec(p.beamCooldown); o.sec(p.reviveProgress)
                o.sec(p.rooted); o.sec(p.encrypted); o.writeByte((p.encryptCharge * 255f).toInt().coerceIn(0, 255)); o.sec(p.burning)
                o.sec(p.pulled); o.pos(p.pullX); o.pos(p.pullY); o.pos(p.pullStrength)
                o.var32(p.pending); o.var32(p.rerolls); o.var32(p.batchTotal); o.var32(p.batchTaken)
                o.var32(p.owned.size)
                for ((k, v) in p.owned) { o.var32(k); o.var32(v) }
                o.writeByte(p.offer.size)
                for ((k, v) in p.offer) { o.var32(k); o.var32(v) }
            }

            o.var32(w.enemies.size)
            for (e in w.enemies) {
                o.var32(e.uid); o.var32(e.def); o.writeByte(e.elite + 1)
                o.writeByte(e.bossIndex + 1)
                if (e.bossIndex >= 0) { o.var32(e.bossCycle); o.writeByte(e.bossPhase); o.writeBoolean(e.bossGlitched) }
                o.pos(e.x); o.pos(e.y)
                o.writeFloat(e.maxHp)
                o.writeShort((e.hp / e.maxHp.coerceAtLeast(1e-3f) * 65535f).toInt().coerceIn(0, 65535))
                o.writeByte(e.radius.toInt().coerceIn(0, 255))
                o.writeByte(e.state.ordinal); o.sec(e.stateTimer)
                o.writeByte((e.hitFlash * 1000f).toInt().coerceIn(0, 255))
            }

            o.var32(w.shots.size)
            for (s in w.shots) {
                o.writeByte(s.kind.ordinal)
                o.writeByte((if (s.friendly) 1 else 0) or (if (s.crit) 2 else 0) or (if (s.homing) 4 else 0) or (s.owner shl 4))
                o.pos(s.x); o.pos(s.y); o.vel(s.vx); o.vel(s.vy)
                o.writeByte((s.radius * 4f).toInt().coerceIn(0, 255))
                o.writeInt(s.tint.toInt())
                o.sec(s.armTimer); o.sec(s.life)
            }

            o.var32(w.hazards.size)
            for (h in w.hazards) {
                o.writeByte(h.kind.ordinal)
                o.pos(h.x); o.pos(h.y); o.pos(h.x2); o.pos(h.y2)
                o.pos(h.radius); o.pos(h.maxRadius); o.sec(h.timer); o.sec(h.duration); o.sec(h.windup)
                o.writeInt(h.color.toInt())
            }

            o.var32(w.zaps.size)
            for (z in w.zaps) {
                o.writeByte(z.kind.ordinal)
                o.pos(z.x); o.pos(z.y); o.pos(z.x2); o.pos(z.y2)
                o.pos(z.radius); o.sec(z.timer); o.sec(z.duration); o.writeBoolean(z.landed); o.writeInt(z.seed); o.writeInt(z.color.toInt())
            }

            o.var32(w.texts.size)
            for (t in w.texts) { o.pos(t.x); o.pos(t.y); o.str(t.text); o.writeByte(t.kind.ordinal); o.sec(t.life) }

            o.var32(w.pulses.size)
            for (p in w.pulses) {
                o.pos(p.x); o.pos(p.y); o.pos(p.radius); o.pos(p.maxRadius); o.sec(p.life); o.sec(p.maxLife); o.writeInt(p.color.toInt())
            }

            o.var32(w.sounds.size)
            for (s in w.sounds) o.writeByte(s.ordinal)

            o.var32(w.barriers.size)
            for (b in w.barriers) {
                o.pos(b[0]); o.pos(b[1]); o.pos(b[2]); o.sec(b[3]); o.sec(b[4]); o.sec(b[5]); o.writeByte(b.getOrElse(6) { 0f }.toInt())
            }
            o.writeByte((w.darkness * 255f).toInt().coerceIn(0, 255))
            o.sec(w.lightFlicker)
            o.writeByte((w.bossVeil * 255f).toInt().coerceIn(0, 255))
            o.writeByte((w.bossShield + 1f).toInt().coerceIn(0, 2))
            o.ang(w.bossRingAngle); o.writeByte((w.bossRingFilled + 1).coerceIn(0, 255)); o.writeByte(if (w.bossRingOut) 1 else 0)
        }
        return bytes.toByteArray()
    }

    fun decodeWorld(data: ByteArray): CoopWorld? = try {
        DataInputStream(InflaterInputStream(ByteArrayInputStream(data))).use { i ->
            if (i.readUnsignedByte() != VERSION) return null
            val w = CoopWorld()
            w.seq = i.var32()
            w.level = i.var32(); w.levelSeed = i.readLong()
            w.previousArenaId = i.str().ifEmpty { null }; w.previousEvent = i.readBoolean()
            w.vaultCracked = i.readBoolean(); w.vaultOpening = i.sec()
            w.phase = Phase.entries[i.readUnsignedByte()]; w.phaseTimer = i.sec(); w.slideIn = i.sec()
            w.score = i.readLong(); w.kills = i.var32(); w.euros = i.var32(); w.diamonds = i.var32()
            w.bosses = i.var32(); w.elites = i.var32(); w.events = i.var32()
            w.levelKills = i.var32(); w.levelSpawned = i.var32(); w.waveIndex = i.var32()
            w.runLevel = i.var32(); w.xp = i.readFloat(); w.runSeconds = i.readFloat(); w.timedRemaining = i.readFloat()
            w.portalOpen = i.readBoolean()
            w.banner = i.str(); w.bannerSub = i.str(); w.bannerTimer = i.sec()
            w.bossPhaseLabel = i.str(); w.bossUid = i.var32() - 1

            repeat(i.readUnsignedByte()) {
                val p = NetOp()
                p.x = i.pos(); p.y = i.pos(); p.hp = i.readFloat(); p.firewall = i.readFloat(); p.facing = i.ang()
                val f = i.readUnsignedByte()
                p.moving = f and 1 != 0; p.beamActive = f and 2 != 0; p.downed = f and 4 != 0; p.gone = f and 8 != 0
                p.invuln = i.sec(); p.hurtFlash = i.sec(); p.orbAngle = i.ang(); p.bladeAngle = i.ang(); p.targetUid = i.var32() - 1
                if (p.beamActive) { p.beamX2 = i.pos(); p.beamY2 = i.pos() }
                p.beamHeat = i.sec(); p.beamCooldown = i.sec(); p.reviveProgress = i.sec()
                p.rooted = i.sec(); p.encrypted = i.sec(); p.encryptCharge = i.readUnsignedByte() / 255f; p.burning = i.sec()
                p.pulled = i.sec(); p.pullX = i.pos(); p.pullY = i.pos(); p.pullStrength = i.pos()
                p.pending = i.var32(); p.rerolls = i.var32(); p.batchTotal = i.var32(); p.batchTaken = i.var32()
                repeat(i.var32()) { val k = i.var32(); p.owned[k] = i.var32() }
                repeat(i.readUnsignedByte()) { val k = i.var32(); p.offer += k to i.var32() }
                w.ops += p
            }

            repeat(i.var32()) {
                val e = NetEnemy()
                e.uid = i.var32(); e.def = i.var32(); e.elite = i.readUnsignedByte() - 1
                e.bossIndex = i.readUnsignedByte() - 1
                if (e.bossIndex >= 0) { e.bossCycle = i.var32(); e.bossPhase = i.readUnsignedByte(); e.bossGlitched = i.readBoolean() }
                e.x = i.pos(); e.y = i.pos()
                e.maxHp = i.readFloat()
                e.hp = i.readUnsignedShort() / 65535f * e.maxHp
                e.radius = i.readUnsignedByte().toFloat()
                e.state = AiState.entries[i.readUnsignedByte()]; e.stateTimer = i.sec()
                e.hitFlash = i.readUnsignedByte() / 1000f
                w.enemies += e
            }

            repeat(i.var32()) {
                val s = NetShot()
                s.kind = ProjKind.entries[i.readUnsignedByte()]
                val f = i.readUnsignedByte()
                s.friendly = f and 1 != 0; s.crit = f and 2 != 0; s.homing = f and 4 != 0; s.owner = f shr 4
                s.x = i.pos(); s.y = i.pos(); s.vx = i.vel(); s.vy = i.vel()
                s.radius = i.readUnsignedByte() / 4f
                s.tint = i.readInt().toLong() and 0xFFFFFFFFL
                s.armTimer = i.sec(); s.life = i.sec()
                w.shots += s
            }

            repeat(i.var32()) {
                val h = NetHazard()
                h.kind = HazardKind.entries[i.readUnsignedByte()]
                h.x = i.pos(); h.y = i.pos(); h.x2 = i.pos(); h.y2 = i.pos()
                h.radius = i.pos(); h.maxRadius = i.pos(); h.timer = i.sec(); h.duration = i.sec(); h.windup = i.sec()
                h.color = i.readInt().toLong() and 0xFFFFFFFFL
                w.hazards += h
            }

            repeat(i.var32()) {
                val z = NetZap()
                z.kind = ZapKind.entries[i.readUnsignedByte()]
                z.x = i.pos(); z.y = i.pos(); z.x2 = i.pos(); z.y2 = i.pos()
                z.radius = i.pos(); z.timer = i.sec(); z.duration = i.sec(); z.landed = i.readBoolean(); z.seed = i.readInt()
                z.color = i.readInt().toLong() and 0xFFFFFFFFL
                w.zaps += z
            }

            repeat(i.var32()) {
                val t = NetText()
                t.x = i.pos(); t.y = i.pos(); t.text = i.str(); t.kind = TextKind.entries[i.readUnsignedByte()]; t.life = i.sec()
                w.texts += t
            }

            repeat(i.var32()) {
                val p = NetPulse()
                p.x = i.pos(); p.y = i.pos(); p.radius = i.pos(); p.maxRadius = i.pos(); p.life = i.sec(); p.maxLife = i.sec()
                p.color = i.readInt().toLong() and 0xFFFFFFFFL
                w.pulses += p
            }

            repeat(i.var32()) { w.sounds += GameSound.entries[i.readUnsignedByte()] }
            repeat(i.var32()) { w.barriers += floatArrayOf(i.pos(), i.pos(), i.pos(), i.sec(), i.sec(), i.sec(), i.readUnsignedByte().toFloat()) }
            w.darkness = i.readUnsignedByte() / 255f
            w.lightFlicker = i.sec()
            w.bossVeil = i.readUnsignedByte() / 255f
            w.bossShield = i.readUnsignedByte() - 1f
            w.bossRingAngle = i.ang(); w.bossRingFilled = i.readUnsignedByte() - 1; w.bossRingOut = i.readUnsignedByte() == 1
            w
        }
    } catch (_: Exception) {
        null
    }

    fun encodeInput(c: CoopInput): ByteArray {
        val bytes = ByteArrayOutputStream(32)
        DataOutputStream(bytes).use { o ->
            o.writeByte(VERSION)
            o.var32(c.seq); o.pos(c.x); o.pos(c.y); o.ang(c.facing); o.writeBoolean(c.moving)
            o.var32(c.pickSerial); o.writeByte(c.pickIndex); o.var32(c.rerollSerial); o.var32(c.level); o.var32(c.replaceIndex + 1)
        }
        return bytes.toByteArray()
    }

    fun decodeInput(data: ByteArray): CoopInput? = try {
        DataInputStream(ByteArrayInputStream(data)).use { i ->
            if (i.readUnsignedByte() != VERSION) return null
            CoopInput(
                seq = i.var32(), x = i.pos(), y = i.pos(), facing = i.ang(), moving = i.readBoolean(),
                pickSerial = i.var32(), pickIndex = i.readUnsignedByte(), rerollSerial = i.var32(), level = i.var32(), replaceIndex = i.var32() - 1
            )
        }
    } catch (_: Exception) {
        null
    }

    /** Bump when the format changes; mismatched builds refuse each other's packets. */
    const val VERSION = 7
}
