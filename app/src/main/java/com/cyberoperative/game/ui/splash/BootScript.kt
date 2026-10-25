package com.cyberoperative.game.ui.splash

import com.cyberoperative.game.i18n.tr
import kotlin.random.Random

/**
 * The fictional boot log for the INITIALIZING screen (§5, stage 2).
 *
 * VISUAL SIMULATION ONLY. Nothing here is executed; there is no shell,
 * no network access and no scanning. Commands are fictional or harmless.
 */
enum class LineStyle { HEADER, NORMAL, OK, WARN, CMD, STATUS, FINAL }

data class BootLine(val text: String, val style: LineStyle, val delay: Float)

object BootScript {

    fun build(rng: Random): List<BootLine> {
        val lines = ArrayList<BootLine>()
        fun add(t: String, s: LineStyle = LineStyle.NORMAL, d: Float = 0.07f) { lines += BootLine(t, s, d) }
        val hex = { "0x" + rng.nextInt(0x1000, 0xFFFF).toString(16).uppercase() }

        add("[BOOT] CYBER OPERATIVE SYSTEM v0.1", LineStyle.HEADER, 0.15f)
        add("[    0.000000] kernel: combat-kernel ${hex()} loading", d = 0.05f)
        add(tr("Initializing network interface..."))
        add(tr("Loading operative profile..."))
        add(tr("Scanning network topology..."), d = 0.1f)
        add(tr("Establishing encrypted tunnel..."), d = 0.1f)
        add("  handshake ${hex()}:${hex()} ... ok", LineStyle.OK, 0.05f)
        add(tr("Mounting defensive modules..."))
        add(tr("Loading intrusion signatures... {0} loaded", 1200 + rng.nextInt(800)), d = 0.09f)
        add(tr("Synchronizing threat database..."))
        if (rng.nextBoolean()) add(tr("[WARN] signature cache stale, rebuilding"), LineStyle.WARN, 0.12f)
        add(tr("Starting packet inspection engine..."))
        add(tr("Verifying secure channel..."))
        add(tr("Analyzing attack vectors..."), d = 0.1f)
        add(tr("Initializing combat kernel..."))
        add(tr("Deploying countermeasure modules..."), d = 0.1f)
        add("")
        add("eth0 .............. ONLINE", LineStyle.STATUS, 0.06f)
        add("firewall .......... ENABLED", LineStyle.STATUS, 0.06f)
        add("IDS ............... ACTIVE", LineStyle.STATUS, 0.06f)
        add("IPS ............... ACTIVE", LineStyle.STATUS, 0.06f)
        add("encryption ........ VERIFIED", LineStyle.STATUS, 0.06f)
        add("")
        add("> sudo systemctl start cyber-operative", LineStyle.CMD, 0.18f)
        add(tr("> scanning subnet... (simulated)"), LineStyle.CMD, 0.12f)
        add(tr("> establishing secure session..."), LineStyle.CMD, 0.12f)
        add(tr("> loading combat modules..."), LineStyle.CMD, 0.12f)
        add("")
        add(tr("ACCESS GRANTED"), LineStyle.FINAL, 0.3f)
        return lines
    }
}
