package com.cyberoperative.game.ui.game

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.cyberoperative.game.core.MathUtil
import com.cyberoperative.game.data.AiKind
import com.cyberoperative.game.data.EliteModifier
import com.cyberoperative.game.data.EnemyDef
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Threat sprites (owner, 2026-10-11: "upgrade all of the enemy textures for the small ones and
 * elites… many many different colors… highest ones could even have a sheen… keep the glitched
 * ones changing textures on a loop"). Textures only: stats, AI and hitboxes are untouched.
 *
 * Every threat is a neon emblem creature: a dark core with a bright, glowing trim. Its
 * silhouette comes from how it fights ([Form], one per strain), its colour from its code
 * family ([palette]), and its finish from how deep it unlocks ([tier]): a rotating inner ring,
 * then an orbiting spark, then a moving metallic sheen; KERNEL-MODE cycles the spectrum.
 */
internal object EnemySprites {

    enum class Form { CRAWLER, GRUB, NANITE, LEECH, BUG, LOCUST, BLOCK, HUSK, GOLEM, TURRET, TWIN, FAN, MORTAR, GATLING,
        SENTRY, HORN, LANCE, BULL, SCOPE, STALKER, BEACON, SPIRE, BLINKER, WRAITH, GLITCH, DRONE, WORM }

    class Look(val form: Form, val color: Color, val accent: Color, val tier: Int, val spectrum: Boolean)

    /** Code family → trim colour and accent (a wide spread instead of all-red). */
    private val families: Map<String, Pair<Long, Long>> = mapOf(
        "poly" to (0xFFFF3D7F to 0xFFFFB3CC),
        "enc" to (0xFF2EE6FF to 0xFFB5F6FF),
        "botnet" to (0xFFFF2EC4 to 0xFFFFB0EC),
        "ransom" to (0xFFFFC21A to 0xFFFFF0A8),
        "zeroday" to (0xFFA259FF to 0xFFDCC2FF),
        "adware" to (0xFF9BFF3D to 0xFFE2FFB8),
        "spyware" to (0xFFFF8A1A to 0xFFFFD2A8),
        "cryptojack" to (0xFFFFE14D to 0xFFFFFFFF),
        "forkbomb" to (0xFFFF4A1A to 0xFFFFC4A8),
        "keylogger" to (0xFF1FF2B0 to 0xFFC4FFEC),
        "backdoor" to (0xFFE0203A to 0xFFFF9EAB),
        "stealth" to (0xFF6A5CFF to 0xFFCBC4FF),
        "kernel" to (0xFFFF2D55 to 0xFFFFFFFF)
    )

    private val strainForms: Map<String, Form> = mapOf(
        "crawler" to Form.CRAWLER, "leech" to Form.LEECH, "brute" to Form.BLOCK, "husk" to Form.HUSK,
        "grub" to Form.GRUB, "golem" to Form.GOLEM, "mite" to Form.BUG, "gnat" to Form.BUG,
        "nanite" to Form.NANITE, "locust" to Form.LOCUST, "spitter" to Form.TURRET, "gunner" to Form.TWIN,
        "scatter" to Form.FAN, "mortar" to Form.MORTAR, "repeater" to Form.GATLING, "ram" to Form.HORN,
        "lancer" to Form.LANCE, "bull" to Form.BULL, "marksman" to Form.SCOPE, "stalker" to Form.STALKER,
        "beacon" to Form.BEACON, "spire" to Form.SPIRE, "sentry" to Form.SENTRY, "blinker" to Form.BLINKER,
        "phantom" to Form.WRAITH, "glitch" to Form.GLITCH
    )

    private val originals: Map<String, Form> = mapOf(
        "malware" to Form.CRAWLER, "sql_injector" to Form.TURRET, "bot" to Form.DRONE, "exploit" to Form.HORN,
        "trojan" to Form.BLOCK, "phisher" to Form.FAN, "sniffer" to Form.SCOPE, "worm" to Form.WORM,
        "wormlet" to Form.GRUB, "ddos_node" to Form.BEACON, "rootkit" to Form.WRAITH, "swarmling" to Form.BUG,
        "orbit_drone" to Form.DRONE
    )

    private val cache = HashMap<String, Look>()

    fun look(def: EnemyDef): Look = cache.getOrPut(def.id) {
        val us = def.id.indexOf('_')
        val fam = if (us > 0) def.id.substring(0, us) else ""
        val strain = if (us > 0) def.id.substring(us + 1) else ""
        val famCol = families[fam]
        val form = when {
            def.glitched -> Form.GLITCH
            famCol != null && strainForms.containsKey(strain) -> strainForms.getValue(strain)
            else -> originals[def.id] ?: byAi(def)
        }
        val color = Color(famCol?.first ?: def.color)
        val accent = Color(famCol?.second ?: 0xFFFFF2B0)
        val tier = when {
            def.minLevel >= 999 -> 1
            def.minLevel >= 30 -> 3
            def.minLevel >= 20 -> 2
            def.minLevel >= 10 -> 1
            else -> 0
        }
        Look(form, color, accent, tier, fam == "kernel")
    }

    private fun byAi(def: EnemyDef): Form = when (def.ai) {
        AiKind.CHASER -> if (def.radius >= 25f) Form.BLOCK else Form.CRAWLER
        AiKind.SWARMER -> Form.BUG
        AiKind.SHOOTER -> Form.TURRET
        AiKind.SNIPER -> Form.SCOPE
        AiKind.CHARGER -> Form.HORN
        AiKind.TURRET -> Form.BEACON
        AiKind.TELEPORTER -> Form.WRAITH
        AiKind.GLITCHED -> Form.GLITCH
        else -> Form.CRAWLER
    }

    private val path = Path()
    private val glitchForms = Form.entries.filter { it != Form.GLITCH }.toTypedArray()

    private fun mix(a: Color, b: Color, t: Float) =
        Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, a.alpha)

    /**
     * Draws threat [def] centred at ([cx], [cy]) with radius [r]. [aim] (radians) points at the
     * operative it is fighting; [winding] flashes the trim before an attack; [hit] whitens it.
     */
    fun DrawScope.draw(def: EnemyDef, cx: Float, cy: Float, r: Float, time: Float, uid: Int, aim: Float, winding: Boolean, hit: Boolean,
                       elite: EliteModifier? = null) {
        val lk = look(def)
        val glitch = lk.form == Form.GLITCH
        var trim = when {
            glitch -> Color.hsv((time * 220f + uid * 47f) % 360f, 0.85f, 1f)
            // Elites wear their modifier's colour (owner, 2026-10-11: "more distinct").
            elite != null -> eliteTrim(elite, time, uid)
            lk.spectrum -> Color.hsv((time * 90f + uid * 31f) % 360f, 0.75f, 1f)
            else -> lk.color
        }
        if (winding && ((time * 16f).toInt() % 2 == 0)) trim = Color.White
        val form = if (glitch) glitchForms[((time * 4f).toInt() + uid) % glitchForms.size] else lk.form
        val c = Offset(cx, cy)
        val core = if (hit) Color.White else mix(Color(0xFF07090F), trim, 0.16f)
        val coreLit = if (hit) Color.White else mix(Color(0xFF151A26), trim, 0.32f)
        val sw = max(1.6f, r * 0.11f)
        val pulse = 0.75f + 0.25f * sin(time * 4f + uid)

        if (glitch) {
            // RGB-split ghosts that jump a few pixels every few frames.
            val j = ((time * 14f).toInt() + uid) % 5 - 2
            drawCircle(Color(0xFF00FFFF).copy(alpha = 0.28f), r, Offset(cx - 4f + j, cy))
            drawCircle(Color(0xFFFF00FF).copy(alpha = 0.28f), r, Offset(cx + 4f - j, cy))
        }
        // Glow behind everything.
        drawCircle(trim.copy(alpha = 0.10f * pulse), r * 1.55f, c)
        drawCircle(trim.copy(alpha = 0.16f * pulse), r * 1.18f, c)

        when (form) {
            Form.CRAWLER -> crawler(c, r, trim, core, coreLit, sw, time, uid)
            Form.GRUB -> grub(c, r, trim, core, coreLit, sw, time, uid)
            Form.NANITE -> nanite(c, r, trim, core, coreLit, sw, time, uid)
            Form.LEECH -> leech(c, r, trim, core, coreLit, sw, time, uid, aim)
            Form.BUG -> bug(c, r, trim, core, coreLit, sw, time, uid, 2)
            Form.LOCUST -> bug(c, r, trim, core, coreLit, sw, time, uid, 4)
            Form.BLOCK -> block(c, r, trim, core, coreLit, sw)
            Form.HUSK -> husk(c, r, trim, core, coreLit, sw)
            Form.GOLEM -> golem(c, r, trim, core, coreLit, sw, time, uid)
            Form.TURRET -> turret(c, r, trim, core, coreLit, sw, aim, 1, 0f)
            Form.TWIN -> turret(c, r, trim, core, coreLit, sw, aim, 2, 0f)
            Form.FAN -> turret(c, r, trim, core, coreLit, sw, aim, 3, 0.45f)
            Form.MORTAR -> mortar(c, r, trim, core, coreLit, sw, aim)
            Form.GATLING -> gatling(c, r, trim, core, coreLit, sw, aim, time)
            Form.SENTRY -> sentry(c, r, trim, core, coreLit, sw, aim)
            Form.HORN -> horn(c, r, trim, core, coreLit, sw, aim, 0.5f)
            Form.LANCE -> lance(c, r, trim, core, coreLit, sw, aim)
            Form.BULL -> horn(c, r, trim, core, coreLit, sw, aim, 0.9f)
            Form.SCOPE -> scope(c, r, trim, core, coreLit, sw, aim, time)
            Form.STALKER -> stalker(c, r, trim, core, coreLit, sw, aim, time)
            Form.BEACON -> beacon(c, r, trim, core, coreLit, sw, time, uid)
            Form.SPIRE -> spire(c, r, trim, core, coreLit, sw, time, uid)
            Form.BLINKER -> blinker(c, r, trim, core, coreLit, sw, time, uid)
            Form.WRAITH -> wraith(c, r, trim, core, coreLit, sw, time, uid)
            Form.DRONE -> drone(c, r, trim, core, coreLit, sw, time, uid)
            Form.WORM -> worm(c, r, trim, core, coreLit, sw, time, uid, aim)
            Form.GLITCH -> crawler(c, r, trim, core, coreLit, sw, time, uid)
        }

        // The face: two glowing eyes that track the operative (turret forms use their lens).
        if (form !in LENS_FORMS) eyes(if (form == Form.GOLEM) Offset(cx, cy - r * 0.5f) else c, if (form == Form.GOLEM) r * 0.8f else r,
            aim, if (winding) Color.White else lk.accent, form)

        // Tier finish.
        if (lk.tier >= 1) {
            val rot = time * 70f + uid * 13f
            for (k in 0 until 3) drawArc(lk.accent.copy(alpha = 0.55f), rot + k * 120f, 50f, false,
                Offset(cx - r * 0.62f, cy - r * 0.62f), Size(r * 1.24f, r * 1.24f), style = Stroke(max(1f, r * 0.06f)))
        }
        if (lk.tier >= 2) {
            val a = time * 3.2f + uid
            val sp = Offset(cx + cos(a) * r * 1.3f, cy + sin(a) * r * 0.7f)
            drawCircle(trim.copy(alpha = 0.3f), r * 0.16f, sp)
            drawCircle(lk.accent, r * 0.08f, sp)
        }
        if (lk.tier >= 3 || lk.spectrum) sheen(c, r, time, uid)
        if (glitch && ((time * 9f).toInt() + uid) % 4 == 0) {
            drawRect(Color.White.copy(alpha = 0.5f), Offset(cx - r * 1.2f, cy - r * 0.1f), Size(r * 2.4f, 3f))
        }
    }

    private val LENS_FORMS = setOf(Form.TURRET, Form.TWIN, Form.FAN, Form.MORTAR, Form.GATLING, Form.SENTRY, Form.SCOPE, Form.STALKER, Form.BEACON, Form.SPIRE, Form.DRONE)

    // --- shared pieces -------------------------------------------------------------------

    private fun DrawScope.body(p: Path, core: Color, lit: Color, trim: Color, sw: Float, top: Float, bottom: Float) {
        drawPath(p, core)
        drawPath(p, trim.copy(alpha = 0.35f), style = Stroke(sw * 2.4f, join = StrokeJoin.Round))
        drawPath(p, trim, style = Stroke(sw, join = StrokeJoin.Round))
    }

    private fun DrawScope.disc(c: Offset, r: Float, core: Color, lit: Color, trim: Color, sw: Float) {
        drawCircle(core, r, c)
        drawCircle(lit, r * 0.55f, Offset(c.x - r * 0.25f, c.y - r * 0.28f))
        drawCircle(trim.copy(alpha = 0.35f), r, c, style = Stroke(sw * 2.4f))
        drawCircle(trim, r, c, style = Stroke(sw))
    }

    private fun poly(c: Offset, r: Float, sides: Int, rot: Float, squash: Float = 1f, inner: Float = 0f): Path {
        path.reset()
        val n = if (inner > 0f) sides * 2 else sides
        for (i in 0 until n) {
            val a = rot + i * MathUtil.TWO_PI / n
            val rr = if (inner > 0f && i % 2 == 1) r * inner else r
            val x = c.x + cos(a) * rr
            val y = c.y + sin(a) * rr * squash
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }

    private fun DrawScope.eyes(c: Offset, r: Float, aim: Float, col: Color, form: Form) {
        val ex = cos(aim) * r * 0.16f
        val ey = sin(aim) * r * 0.12f
        val gap = if (form == Form.BLOCK || form == Form.GOLEM || form == Form.HUSK) r * 0.3f else r * 0.27f
        val er = max(1.8f, r * 0.14f)
        for (si in 0..1) { val s = if (si == 0) -1f else 1f
            val o = Offset(c.x + s * gap + ex, c.y - r * 0.08f + ey)
            drawCircle(Color(0xFF020306), er * 1.6f, o)
            drawCircle(col.copy(alpha = 0.35f), er * 1.5f, o)
            drawCircle(col, er, o)
        }
    }

    private fun DrawScope.lens(c: Offset, r: Float, aim: Float, trim: Color) {
        drawCircle(Color(0xFF020306), r, c)
        drawCircle(trim.copy(alpha = 0.45f), r * 0.9f, c, style = Stroke(r * 0.18f))
        val p = Offset(c.x + cos(aim) * r * 0.3f, c.y + sin(aim) * r * 0.3f)
        drawCircle(trim, r * 0.42f, p)
        drawCircle(Color.White.copy(alpha = 0.85f), r * 0.16f, p + Offset(-r * 0.1f, -r * 0.1f))
    }

    private fun DrawScope.barrel(c: Offset, r: Float, aim: Float, len: Float, w: Float, trim: Color, core: Color) {
        val e = Offset(c.x + cos(aim) * len, c.y + sin(aim) * len)
        drawLine(trim.copy(alpha = 0.4f), c, e, w * 1.8f, StrokeCap.Round)
        drawLine(core, c, e, w, StrokeCap.Round)
        drawCircle(trim, w * 0.55f, e)
    }

    /** A bright band sweeping across the body every couple of seconds (top tiers). */
    private fun DrawScope.sheen(c: Offset, r: Float, time: Float, uid: Int) {
        val ph = ((time * 0.45f + uid * 0.37f) % 1f)
        if (ph > 0.55f) return
        val d = (ph / 0.55f * 2f - 1f) * r * 0.95f
        val half = sqrt(max(0f, r * r - d * d))
        val k = 0.7071f
        val mid = Offset(c.x + d * k, c.y - d * k)
        val a = Offset(mid.x - half * k, mid.y - half * k)
        val b = Offset(mid.x + half * k, mid.y + half * k)
        drawLine(Color.White.copy(alpha = 0.18f), a, b, r * 0.36f, StrokeCap.Round)
        drawLine(Color.White.copy(alpha = 0.55f), a, b, r * 0.1f, StrokeCap.Round)
    }

    // --- forms (all in world units around c, radius r) ------------------------------------

    private fun DrawScope.crawler(c: Offset, r: Float, trim: Color, core: Color, lit: Color, sw: Float, time: Float, uid: Int) {
        for (i in 0 until 6) {
            val s = if (i < 3) -1f else 1f
            val k = i % 3
            val wig = sin(time * 14f + i + uid) * 0.12f
            val a = (if (s < 0) MathUtil.PI else 0f) + (k - 1) * 0.55f * s + wig
            val p1 = Offset(c.x + cos(a) * r * 0.8f, c.y + sin(a) * r * 0.6f)
            val p2 = Offset(c.x + cos(a) * r * 1.35f, c.y + sin(a) * r * 0.9f + r * 0.25f)
            drawLine(trim.copy(alpha = 0.8f), p1, p2, max(1.4f, r * 0.09f), StrokeCap.Round)
        }
        disc(c, r * 0.9f, core, lit, trim, sw)
        for (i in 0 until 6) {
            val a = i * MathUtil.TWO_PI / 6 - MathUtil.PI / 2f
            drawLine(trim, Offset(c.x + cos(a) * r * 0.9f, c.y + sin(a) * r * 0.9f), Offset(c.x + cos(a) * r * 1.12f, c.y + sin(a) * r * 1.12f), max(1.4f, r * 0.1f), StrokeCap.Round)
        }
    }

    private fun DrawScope.grub(c: Offset, r: Float, trim: Color, core: Color, lit: Color, sw: Float, time: Float, uid: Int) {
        val wob = sin(time * 6f + uid) * r * 0.05f
        drawOval(core, Offset(c.x - r - wob, c.y - r * 0.8f), Size(r * 2f + wob * 2, r * 1.6f))
        drawOval(trim, Offset(c.x - r - wob, c.y - r * 0.8f), Size(r * 2f + wob * 2, r * 1.6f), style = Stroke(sw))
        for (k in -1..1) drawArc(trim.copy(alpha = 0.6f), 200f, 140f, false, Offset(c.x - r * 0.75f + k * r * 0.35f, c.y - r * 0.2f), Size(r * 0.7f, r * 0.9f), style = Stroke(max(1f, sw * 0.6f)))
        drawLine(trim, Offset(c.x - r * 0.25f, c.y + r * 0.62f), Offset(c.x - r * 0.4f, c.y + r * 0.95f), sw, StrokeCap.Round)
        drawLine(trim, Offset(c.x + r * 0.25f, c.y + r * 0.62f), Offset(c.x + r * 0.4f, c.y + r * 0.95f), sw, StrokeCap.Round)
    }

    private fun DrawScope.nanite(c: Offset, r: Float, trim: Color, core: Color, lit: Color, sw: Float, time: Float, uid: Int) {
        body(poly(c, r, 6, time * 1.2f), core, lit, trim, sw, c.y - r, c.y + r)
        for (i in 0 until 3) {
            val a = time * 4f + i * MathUtil.TWO_PI / 3 + uid
            drawRect(trim, Offset(c.x + cos(a) * r * 1.35f - r * 0.12f, c.y + sin(a) * r * 1.35f - r * 0.12f), Size(r * 0.24f, r * 0.24f))
        }
    }

    private fun DrawScope.leech(c: Offset, r: Float, trim: Color, core: Color, lit: Color, sw: Float, time: Float, uid: Int, aim: Float) {
        path.reset()
        val tail = aim + MathUtil.PI
        val tx = c.x + cos(tail) * r * 1.5f + sin(time * 8f + uid) * r * 0.15f
        val ty = c.y + sin(tail) * r * 1.5f
        val px = -sin(aim) * r * 0.9f
        val py = cos(aim) * r * 0.9f
        path.moveTo(c.x + px, c.y + py)
        path.quadraticTo(c.x + cos(aim) * r * 1.5f, c.y + sin(aim) * r * 1.5f, c.x - px, c.y - py)
        path.quadraticTo(c.x - px * 0.4f + cos(tail) * r * 0.6f, c.y - py * 0.4f + sin(tail) * r * 0.6f, tx, ty)
        path.quadraticTo(c.x + px * 0.4f + cos(tail) * r * 0.6f, c.y + py * 0.4f + sin(tail) * r * 0.6f, c.x + px, c.y + py)
        path.close()
        body(path, core, lit, trim, sw, c.y - r, c.y + r)
        val m = Offset(c.x + cos(aim) * r * 0.85f, c.y + sin(aim) * r * 0.85f)
        drawCircle(Color(0xFF020306), r * 0.28f, m)
        drawCircle(trim, r * 0.28f, m, style = Stroke(max(1f, sw * 0.7f)))
    }

    private fun DrawScope.bug(c: Offset, r: Float, trim: Color, core: Color, lit: Color, sw: Float, time: Float, uid: Int, wings: Int) {
        val flap = 0.55f + 0.45f * abs(sin(time * 22f + uid))
        for (i in 0 until wings) {
            val s = if (i % 2 == 0) -1f else 1f
            val up = if (i < 2) -1f else 0.4f
            drawOval(trim.copy(alpha = 0.22f), Offset(c.x + s * r * 0.35f - (if (s < 0) r * 1.3f else 0f), c.y + up * r * 0.55f - r * 0.4f * flap),
                Size(r * 1.3f, r * 0.8f * flap))
            drawOval(trim.copy(alpha = 0.7f), Offset(c.x + s * r * 0.35f - (if (s < 0) r * 1.3f else 0f), c.y + up * r * 0.55f - r * 0.4f * flap),
                Size(r * 1.3f, r * 0.8f * flap), style = Stroke(max(1f, sw * 0.6f)))
        }
        body(poly(c, r * 0.95f, 3, MathUtil.PI / 2f, 1f), core, lit, trim, sw, c.y - r, c.y + r)
        drawLine(trim, Offset(c.x - r * 0.25f, c.y - r * 0.5f), Offset(c.x - r * 0.5f, c.y - r * 1.05f), max(1f, sw * 0.7f), StrokeCap.Round)
        drawLine(trim, Offset(c.x + r * 0.25f, c.y - r * 0.5f), Offset(c.x + r * 0.5f, c.y - r * 1.05f), max(1f, sw * 0.7f), StrokeCap.Round)
    }

    private fun DrawScope.block(c: Offset, r: Float, trim: Color, core: Color, lit: Color, sw: Float) {
        val s = r * 0.92f
        drawRoundRect(core, Offset(c.x - s, c.y - s), Size(s * 2, s * 2), CornerRadius(r * 0.22f))
        drawRoundRect(trim.copy(alpha = 0.35f), Offset(c.x - s, c.y - s), Size(s * 2, s * 2), CornerRadius(r * 0.22f), style = Stroke(sw * 2.4f))
        drawRoundRect(trim, Offset(c.x - s, c.y - s), Size(s * 2, s * 2), CornerRadius(r * 0.22f), style = Stroke(sw))
        // Shoulder plates and a visor band.
        drawRect(trim.copy(alpha = 0.55f), Offset(c.x - s * 1.18f, c.y - s * 0.6f), Size(s * 0.24f, s * 0.9f))
        drawRect(trim.copy(alpha = 0.55f), Offset(c.x + s * 0.94f, c.y - s * 0.6f), Size(s * 0.24f, s * 0.9f))
        drawRoundRect(Color(0xFF020306), Offset(c.x - s * 0.7f, c.y - s * 0.38f), Size(s * 1.4f, s * 0.5f), CornerRadius(r * 0.15f))
        for (k in -1..1) drawCircle(trim, r * 0.05f, Offset(c.x + k * s * 0.5f, c.y + s * 0.62f))
    }

    private fun DrawScope.husk(c: Offset, r: Float, trim: Color, core: Color, lit: Color, sw: Float) {
        body(poly(c, r, 8, MathUtil.PI / 8f), core, lit, trim, sw, c.y - r, c.y + r)
        drawLine(trim.copy(alpha = 0.6f), Offset(c.x - r * 0.1f, c.y - r * 0.9f), Offset(c.x + r * 0.12f, c.y - r * 0.5f), max(1f, sw * 0.6f))
        drawLine(trim.copy(alpha = 0.6f), Offset(c.x + r * 0.55f, c.y + r * 0.2f), Offset(c.x + r * 0.8f, c.y + r * 0.5f), max(1f, sw * 0.6f))
        for (i in 0 until 8) {
            val a = MathUtil.PI / 8f + i * MathUtil.TWO_PI / 8
            drawCircle(trim, r * 0.05f, Offset(c.x + cos(a) * r * 0.78f, c.y + sin(a) * r * 0.78f))
        }
    }

    private fun DrawScope.golem(c: Offset, r: Float, trim: Color, core: Color, lit: Color, sw: Float, time: Float, uid: Int) {
        val lower = Offset(c.x, c.y + r * 0.35f)
        drawRoundRect(core, Offset(c.x - r * 1.05f, lower.y - r * 0.6f), Size(r * 2.1f, r * 1.25f), CornerRadius(r * 0.2f))
        drawRoundRect(trim, Offset(c.x - r * 1.05f, lower.y - r * 0.6f), Size(r * 2.1f, r * 1.25f), CornerRadius(r * 0.2f), style = Stroke(sw))
        drawRoundRect(core, Offset(c.x - r * 0.65f, c.y - r * 1.05f), Size(r * 1.3f, r * 0.95f), CornerRadius(r * 0.18f))
        drawRoundRect(trim, Offset(c.x - r * 0.65f, c.y - r * 1.05f), Size(r * 1.3f, r * 0.95f), CornerRadius(r * 0.18f), style = Stroke(sw))
        val p = 0.6f + 0.4f * sin(time * 3f + uid)
        drawCircle(trim.copy(alpha = 0.35f * p), r * 0.38f, lower)
        drawCircle(trim.copy(alpha = p), r * 0.18f, lower)
    }

    private fun DrawScope.turret(c: Offset, r: Float, trim: Color, core: Color, lit: Color, sw: Float, aim: Float, barrels: Int, spread: Float) {
        for (i in 0 until barrels) {
            val off = if (barrels == 1) 0f else (i / (barrels - 1f) - 0.5f) * (if (spread > 0f) spread * 2f else 0f)
            if (barrels == 2 && spread == 0f) {
                val px = -sin(aim) * r * 0.32f * (if (i == 0) -1f else 1f)
                val py = cos(aim) * r * 0.32f * (if (i == 0) -1f else 1f)
                barrel(Offset(c.x + px, c.y + py), r, aim, r * 1.45f, r * 0.26f, trim, core)
            } else barrel(c, r, aim + off, r * 1.5f, r * 0.3f, trim, core)
        }
        body(poly(c, r, if (barrels == 3) 6 else 5, -MathUtil.PI / 2f), core, lit, trim, sw, c.y - r, c.y + r)
        lens(c, r * 0.45f, aim, trim)
    }

    private fun DrawScope.mortar(c: Offset, r: Float, trim: Color, core: Color, lit: Color, sw: Float, aim: Float) {
        body(poly(c, r, 8, MathUtil.PI / 8f), core, lit, trim, sw, c.y - r, c.y + r)
        val tube = Offset(c.x + cos(aim) * r * 0.2f, c.y - r * 0.35f)
        drawRoundRect(core, Offset(tube.x - r * 0.42f, tube.y - r * 0.7f), Size(r * 0.84f, r * 0.8f), CornerRadius(r * 0.12f))
        drawRoundRect(trim, Offset(tube.x - r * 0.42f, tube.y - r * 0.7f), Size(r * 0.84f, r * 0.8f), CornerRadius(r * 0.12f), style = Stroke(sw))
        drawOval(Color(0xFF020306), Offset(tube.x - r * 0.34f, tube.y - r * 0.8f), Size(r * 0.68f, r * 0.24f))
        lens(Offset(c.x, c.y + r * 0.35f), r * 0.32f, aim, trim)
    }

    private fun DrawScope.gatling(c: Offset, r: Float, trim: Color, core: Color, lit: Color, sw: Float, aim: Float, time: Float) {
        for (k in 0 until 3) {
            val off = (k - 1) * r * 0.22f
            val spin = sin(time * 18f + k * 2.1f) * r * 0.06f
            barrel(Offset(c.x - sin(aim) * (off + spin), c.y + cos(aim) * (off + spin)), r, aim, r * 1.55f, r * 0.16f, trim, core)
        }
        body(poly(c, r * 1.05f, 3, aim), core, lit, trim, sw, c.y - r, c.y + r)
        lens(c, r * 0.36f, aim, trim)
    }

    private fun DrawScope.sentry(c: Offset, r: Float, trim: Color, core: Color, lit: Color, sw: Float, aim: Float) {
        for (si in -1..1) { val s = si.toFloat(); drawLine(trim.copy(alpha = 0.8f), c, Offset(c.x + s * r * 1.05f, c.y + r * 1.05f), max(1.2f, sw * 0.8f), StrokeCap.Round) }
        barrel(c, r, aim, r * 1.55f, r * 0.3f, trim, core)
        drawRoundRect(core, Offset(c.x - r * 0.8f, c.y - r * 0.75f), Size(r * 1.6f, r * 1.5f), CornerRadius(r * 0.2f))
        drawRoundRect(trim, Offset(c.x - r * 0.8f, c.y - r * 0.75f), Size(r * 1.6f, r * 1.5f), CornerRadius(r * 0.2f), style = Stroke(sw))
        lens(c, r * 0.42f, aim, trim)
    }

    private fun DrawScope.horn(c: Offset, r: Float, trim: Color, core: Color, lit: Color, sw: Float, aim: Float, horn: Float) {
        val dx = cos(aim); val dy = sin(aim)
        val nx = -dy; val ny = dx
        // Two short solid horns raking forward from the shoulders.
        for (si in 0..1) { val s = if (si == 0) -1f else 1f
            val b1 = Offset(c.x + nx * s * r * 0.45f + dx * r * 0.15f, c.y + ny * s * r * 0.45f + dy * r * 0.15f)
            val b2 = Offset(c.x + nx * s * r * 0.75f - dx * r * 0.25f, c.y + ny * s * r * 0.75f - dy * r * 0.25f)
            val tip = Offset(c.x + nx * s * r * (0.85f + horn * 0.35f) + dx * r * (0.75f + horn * 0.45f),
                c.y + ny * s * r * (0.85f + horn * 0.35f) + dy * r * (0.75f + horn * 0.45f))
            path.reset(); path.moveTo(b1.x, b1.y); path.lineTo(tip.x, tip.y); path.lineTo(b2.x, b2.y); path.close()
            drawPath(path, lit)
            drawPath(path, trim, style = Stroke(max(1.2f, sw * 0.8f), join = StrokeJoin.Round))
        }
        path.reset()
        path.moveTo(c.x + dx * r * 1.1f, c.y + dy * r * 1.1f)
        path.lineTo(c.x + nx * r * 0.9f - dx * r * 0.55f, c.y + ny * r * 0.9f - dy * r * 0.55f)
        path.lineTo(c.x - dx * r * 0.3f, c.y - dy * r * 0.3f)
        path.lineTo(c.x - nx * r * 0.9f - dx * r * 0.55f, c.y - ny * r * 0.9f - dy * r * 0.55f)
        path.close()
        body(path, core, lit, trim, sw, c.y - r, c.y + r)
    }

    private fun DrawScope.lance(c: Offset, r: Float, trim: Color, core: Color, lit: Color, sw: Float, aim: Float) {
        val dx = cos(aim); val dy = sin(aim)
        drawLine(trim.copy(alpha = 0.4f), c, Offset(c.x + dx * r * 2f, c.y + dy * r * 2f), r * 0.35f, StrokeCap.Round)
        drawLine(Color.White.copy(alpha = 0.9f), Offset(c.x + dx * r * 0.8f, c.y + dy * r * 0.8f), Offset(c.x + dx * r * 2f, c.y + dy * r * 2f), r * 0.12f, StrokeCap.Round)
        body(poly(c, r, 4, aim, 1f), core, lit, trim, sw, c.y - r, c.y + r)
    }

    private fun DrawScope.scope(c: Offset, r: Float, trim: Color, core: Color, lit: Color, sw: Float, aim: Float, time: Float) {
        barrel(c, r, aim, r * 1.9f, r * 0.2f, trim, core)
        disc(c, r * 0.95f, core, lit, trim, sw)
        lens(c, r * 0.52f, aim, trim)
        val p = Offset(c.x + cos(aim) * r * 0.15f, c.y + sin(aim) * r * 0.15f)
        drawLine(Color.White.copy(alpha = 0.6f), Offset(p.x - r * 0.3f, p.y), Offset(p.x + r * 0.3f, p.y), 1f)
        drawLine(Color.White.copy(alpha = 0.6f), Offset(p.x, p.y - r * 0.3f), Offset(p.x, p.y + r * 0.3f), 1f)
        drawArc(trim.copy(alpha = 0.7f), time * 90f, 70f, false, Offset(c.x - r * 1.15f, c.y - r * 1.15f), Size(r * 2.3f, r * 2.3f), style = Stroke(max(1f, sw * 0.6f)))
    }

    private fun DrawScope.stalker(c: Offset, r: Float, trim: Color, core: Color, lit: Color, sw: Float, aim: Float, time: Float) {
        barrel(c, r, aim, r * 1.7f, r * 0.16f, trim, core)
        path.reset()
        path.moveTo(c.x, c.y - r * 1.25f)
        path.quadraticTo(c.x + r * 1.1f, c.y - r * 0.6f, c.x + r * 0.85f, c.y + r * 0.9f)
        path.lineTo(c.x - r * 0.85f, c.y + r * 0.9f)
        path.quadraticTo(c.x - r * 1.1f, c.y - r * 0.6f, c.x, c.y - r * 1.25f)
        path.close()
        body(path, core, lit, trim, sw, c.y - r, c.y + r)
        lens(Offset(c.x, c.y - r * 0.05f), r * 0.36f, aim, trim)
    }

    private fun DrawScope.beacon(c: Offset, r: Float, trim: Color, core: Color, lit: Color, sw: Float, time: Float, uid: Int) {
        val ph = (time * 0.8f + uid * 0.2f) % 1f
        drawCircle(trim.copy(alpha = 0.5f * (1f - ph)), r * (1f + ph * 0.8f), c, style = Stroke(max(1f, sw * 0.7f)))
        body(poly(c, r, 4, -MathUtil.PI / 2f), core, lit, trim, sw, c.y - r, c.y + r)
        drawCircle(trim.copy(alpha = 0.4f), r * 0.42f, c)
        drawCircle(Color.White.copy(alpha = 0.85f), r * 0.18f, c)
    }

    private fun DrawScope.spire(c: Offset, r: Float, trim: Color, core: Color, lit: Color, sw: Float, time: Float, uid: Int) {
        body(poly(Offset(c.x, c.y + r * 0.15f), r * 1.05f, 3, -MathUtil.PI / 2f), core, lit, trim, sw, c.y - r, c.y + r)
        for (k in 0 until 3) {
            val y = c.y - r * 0.55f + k * r * 0.42f
            val w = r * (0.35f + k * 0.28f)
            val on = ((time * 3f + uid).toInt() % 3) == k
            drawLine(if (on) Color.White else trim.copy(alpha = 0.7f), Offset(c.x - w, y), Offset(c.x + w, y), max(1.2f, sw * 0.7f), StrokeCap.Round)
        }
        drawCircle(trim, r * 0.16f, Offset(c.x, c.y - r * 1.05f))
    }

    private fun DrawScope.blinker(c: Offset, r: Float, trim: Color, core: Color, lit: Color, sw: Float, time: Float, uid: Int) {
        val g = (time * 2f + uid) % 1f
        drawPath(poly(Offset(c.x - r * 0.5f * g, c.y), r, 4, -MathUtil.PI / 2f, 1.25f), trim.copy(alpha = 0.25f * (1f - g)))
        body(poly(c, r, 4, -MathUtil.PI / 2f, 1.25f), core, lit, trim, sw, c.y - r, c.y + r)
        drawCircle(trim.copy(alpha = 0.6f), r * 0.3f, Offset(c.x, c.y + r * 0.55f), style = Stroke(max(1f, sw * 0.6f)))
    }

    private fun DrawScope.wraith(c: Offset, r: Float, trim: Color, core: Color, lit: Color, sw: Float, time: Float, uid: Int) {
        path.reset()
        path.moveTo(c.x, c.y - r * 1.15f)
        path.quadraticTo(c.x + r * 1.15f, c.y - r * 0.9f, c.x + r * 0.95f, c.y + r * 0.6f)
        for (k in 0 until 4) {
            val x = c.x + r * 0.95f - (k + 1) * r * 0.475f
            val w = sin(time * 6f + k + uid) * r * 0.12f
            path.quadraticTo(x + r * 0.24f, c.y + r * (if (k % 2 == 0) 1.15f else 0.75f) + w, x, c.y + r * 0.7f + w)
        }
        path.quadraticTo(c.x - r * 1.15f, c.y - r * 0.9f, c.x, c.y - r * 1.15f)
        path.close()
        body(path, core, lit, trim, sw, c.y - r, c.y + r)
    }

    private fun DrawScope.drone(c: Offset, r: Float, trim: Color, core: Color, lit: Color, sw: Float, time: Float, uid: Int) {
        for (si in 0..1) { val s = if (si == 0) -1f else 1f
            val p = Offset(c.x + s * r * 1.05f, c.y - r * 0.5f)
            drawLine(trim.copy(alpha = 0.7f), Offset(c.x + s * r * 0.5f, c.y - r * 0.2f), p, max(1f, sw * 0.7f))
            val sp = abs(sin(time * 30f + uid + s))
            drawOval(trim.copy(alpha = 0.55f), Offset(p.x - r * 0.55f * sp, p.y - r * 0.08f), Size(r * 1.1f * sp + 1f, r * 0.16f))
        }
        disc(c, r * 0.8f, core, lit, trim, sw)
        lens(c, r * 0.36f, 0f, trim)
    }

    private fun DrawScope.worm(c: Offset, r: Float, trim: Color, core: Color, lit: Color, sw: Float, time: Float, uid: Int, aim: Float) {
        val back = aim + MathUtil.PI
        for (k in 3 downTo 1) {
            val w = sin(time * 6f + k + uid) * r * 0.25f
            val p = Offset(c.x + cos(back) * r * 0.75f * k - sin(back) * w, c.y + sin(back) * r * 0.75f * k + cos(back) * w)
            disc(p, r * (0.75f - k * 0.1f), core, lit, trim.copy(alpha = 0.8f), sw * 0.8f)
        }
        disc(c, r * 0.9f, core, lit, trim, sw)
    }

    // --- elites --------------------------------------------------------------------------

    /** On-screen size of an elite next to a regular threat (hitbox unchanged). */
    const val ELITE_DRAW_SCALE = 1.3f

    /** The body trim an elite wears: its modifier colour (Corrupted flickers, Volatile throbs). */
    private fun eliteTrim(m: EliteModifier, time: Float, uid: Int): Color = when (m) {
        EliteModifier.CORRUPTED -> if (((time * 11f).toInt() + uid) % 7 == 0) Color(0xFFFF2EC4) else Color(m.color)
        EliteModifier.VOLATILE -> mix(Color(m.color), Color(0xFFFF2D2D), 0.5f + 0.5f * sin(time * 9f + uid))
        EliteModifier.ARMORED -> Color(0xFFDCE6F0)
        else -> Color(m.color)
    }

    /** Behind the body: each modifier's own frame. */
    fun DrawScope.eliteBack(m: EliteModifier, c: Offset, r: Float, time: Float, uid: Int) {
        val col = Color(m.color)
        val p = 0.5f + 0.5f * sin(time * 5f + uid)
        // A strong floor halo in the modifier colour so elites pop out of any crowd.
        drawCircle(col.copy(alpha = 0.10f + 0.08f * p), r * 2.1f, c)
        drawCircle(col.copy(alpha = 0.16f + 0.1f * p), r * 1.65f, c)
        when (m) {
            EliteModifier.ENCRYPTED -> {
                val cage = poly(c, r * 1.55f, 6, time * 0.6f)
                drawPath(cage, col.copy(alpha = 0.14f))
                drawPath(cage, col.copy(alpha = 0.35f), style = Stroke(7f, join = StrokeJoin.Round))
                drawPath(cage, col, style = Stroke(2.6f, join = StrokeJoin.Round))
                val inner = poly(c, r * 1.3f, 6, -time * 0.9f)
                drawPath(inner, col.copy(alpha = 0.6f), style = Stroke(1.4f, join = StrokeJoin.Round))
            }
            EliteModifier.OVERCLOCKED -> {
                // Speed after-images trailing behind, then crackling arcs.
                for (k in 1..2) drawCircle(col.copy(alpha = 0.22f / k), r * (1f - k * 0.08f), Offset(c.x - k * r * 0.55f, c.y + k * r * 0.1f))
                drawCircle(col.copy(alpha = 0.9f), r * 1.45f, c, style = Stroke(2.2f))
                for (k in 0 until 4) {
                    val a = time * 6f + k * MathUtil.TWO_PI / 4 + uid
                    val p1 = Offset(c.x + cos(a) * r * 1.45f, c.y + sin(a) * r * 1.45f)
                    val p2 = Offset(c.x + cos(a + 0.22f) * r * 1.8f, c.y + sin(a + 0.22f) * r * 1.8f)
                    val p3 = Offset(c.x + cos(a + 0.42f) * r * 1.4f, c.y + sin(a + 0.42f) * r * 1.4f)
                    drawLine(Color.White, p1, p2, 2.4f, StrokeCap.Round); drawLine(col, p2, p3, 2.4f, StrokeCap.Round)
                }
            }
            EliteModifier.CORRUPTED -> {
                drawCircle(col.copy(alpha = 0.75f), r * 1.5f, c, style = Stroke(3f))
                for (k in 0 until 10) {
                    val a = k * MathUtil.TWO_PI / 10 + uid
                    val drip = ((time * 0.8f + k * 0.31f) % 1f)
                    val s = 5f * (1f - drip) + 2f
                    drawRect(col.copy(alpha = 1f - drip), Offset(c.x + cos(a) * r * 1.5f - s / 2, c.y + sin(a) * r * 1.5f + drip * r * 0.8f), Size(s, s))
                }
            }
            EliteModifier.ARMORED -> {
                for (k in 0 until 4) {
                    val a = k * 90f + 45f + time * 20f
                    drawArc(Color(0xFF3A4452), a - 36f, 72f, false, Offset(c.x - r * 1.5f, c.y - r * 1.5f), Size(r * 3f, r * 3f), style = Stroke(r * 0.42f))
                    drawArc(Color(0xFFDCE6F0), a - 36f, 72f, false, Offset(c.x - r * 1.5f, c.y - r * 1.5f), Size(r * 3f, r * 3f), style = Stroke(2.2f))
                    val ra = Math.toRadians(a.toDouble()).toFloat()
                    drawCircle(Color(0xFFDCE6F0), r * 0.07f, Offset(c.x + cos(ra) * r * 1.5f, c.y + sin(ra) * r * 1.5f))
                }
            }
            EliteModifier.REPLICATING -> {
                // Two ghost clones peeling off the body.
                for (k in 0 until 2) {
                    val a = time * 2.4f + k * MathUtil.PI
                    val o = Offset(c.x + cos(a) * r * 0.55f, c.y + sin(a) * r * 0.3f)
                    drawCircle(col.copy(alpha = 0.18f), r * 0.95f, o)
                    drawCircle(col.copy(alpha = 0.45f), r * 0.95f, o, style = Stroke(1.6f))
                }
                for (k in 0 until 3) {
                    val a = -time * 1.8f + k * MathUtil.TWO_PI / 3
                    val o = Offset(c.x + cos(a) * r * 1.6f, c.y + sin(a) * r * 0.95f)
                    drawCircle(col.copy(alpha = 0.35f), r * 0.26f, o)
                    drawCircle(col, r * 0.26f, o, style = Stroke(1.6f))
                }
            }
            EliteModifier.VOLATILE -> {
                // Hazard-striped warning ring and rising embers.
                for (k in 0 until 16) {
                    val a = k * 22.5f + time * 40f
                    drawArc(if (k % 2 == 0) col else Color(0xFF1A0E04), a, 22.5f, false, Offset(c.x - r * 1.45f, c.y - r * 1.45f), Size(r * 2.9f, r * 2.9f), style = Stroke(r * 0.2f))
                }
                for (k in 0 until 6) {
                    val f = ((time * 0.9f + k * 0.17f + uid * 0.05f) % 1f)
                    val x = c.x + sin(k * 2.3f + uid) * r * 1.1f
                    drawCircle(Color(0xFFFFE14D).copy(alpha = 1f - f), 2.6f * (1f - f) + 1f, Offset(x, c.y - f * r * 2f))
                }
            }
        }
    }

    /** In front of the body: a big badge with the modifier's symbol, readable in a crowd. */
    fun DrawScope.eliteFront(m: EliteModifier, c: Offset, r: Float, time: Float) {
        val col = Color(m.color)
        val s = max(7f, r * 0.4f)
        val b = Offset(c.x, c.y - r * 1.25f - s)
        val pulse = 0.75f + 0.25f * sin(time * 6f)
        drawCircle(col.copy(alpha = 0.3f * pulse), s * 1.5f, b)
        drawPath(poly(b, s * 1.1f, 6, -MathUtil.PI / 2f), Color(0xFF05070C))
        drawPath(poly(b, s * 1.1f, 6, -MathUtil.PI / 2f), col, style = Stroke(2f, join = StrokeJoin.Round))
        val w = max(1.5f, s * 0.2f)
        val st = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (m) {
            EliteModifier.ENCRYPTED -> { // padlock
                drawArc(col, 180f, 180f, false, Offset(b.x - s * 0.35f, b.y - s * 0.65f), Size(s * 0.7f, s * 0.7f), style = st)
                drawRoundRect(col, Offset(b.x - s * 0.5f, b.y - s * 0.3f), Size(s, s * 0.75f), CornerRadius(s * 0.12f))
                drawCircle(Color(0xFF05070C), s * 0.12f, Offset(b.x, b.y + s * 0.05f))
            }
            EliteModifier.OVERCLOCKED -> { // lightning bolt
                path.reset(); path.moveTo(b.x + s * 0.15f, b.y - s * 0.7f); path.lineTo(b.x - s * 0.35f, b.y + s * 0.08f)
                path.lineTo(b.x + s * 0.02f, b.y + s * 0.08f); path.lineTo(b.x - s * 0.15f, b.y + s * 0.7f)
                path.lineTo(b.x + s * 0.38f, b.y - s * 0.12f); path.lineTo(b.x, b.y - s * 0.12f); path.close()
                drawPath(path, col)
            }
            EliteModifier.CORRUPTED -> { // glitched X
                drawLine(col, Offset(b.x - s * 0.45f, b.y - s * 0.45f), Offset(b.x + s * 0.45f, b.y + s * 0.45f), w * 1.3f, StrokeCap.Round)
                drawLine(col, Offset(b.x + s * 0.45f, b.y - s * 0.45f), Offset(b.x - s * 0.45f, b.y + s * 0.45f), w * 1.3f, StrokeCap.Round)
                drawRect(Color.White.copy(alpha = 0.7f), Offset(b.x + s * 0.2f, b.y - s * 0.1f), Size(s * 0.25f, s * 0.12f))
            }
            EliteModifier.ARMORED -> { // shield
                path.reset(); path.moveTo(b.x, b.y - s * 0.65f); path.lineTo(b.x + s * 0.5f, b.y - s * 0.4f)
                path.lineTo(b.x + s * 0.4f, b.y + s * 0.25f); path.lineTo(b.x, b.y + s * 0.65f)
                path.lineTo(b.x - s * 0.4f, b.y + s * 0.25f); path.lineTo(b.x - s * 0.5f, b.y - s * 0.4f); path.close()
                drawPath(path, col)
            }
            EliteModifier.REPLICATING -> { // two overlapping squares
                drawRect(col, Offset(b.x - s * 0.55f, b.y - s * 0.55f), Size(s * 0.7f, s * 0.7f), style = st)
                drawRect(col, Offset(b.x - s * 0.15f, b.y - s * 0.15f), Size(s * 0.7f, s * 0.7f))
            }
            EliteModifier.VOLATILE -> { // warning !
                drawLine(col, Offset(b.x, b.y - s * 0.6f), Offset(b.x, b.y + s * 0.15f), w * 1.4f, StrokeCap.Round)
                drawCircle(col, w * 0.8f, Offset(b.x, b.y + s * 0.5f))
            }
        }
    }
}
