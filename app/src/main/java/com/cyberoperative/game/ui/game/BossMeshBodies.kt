package com.cyberoperative.game.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate

/*
 * Mesh rigs for the code-drawn bosses (owner, 0.36.2: "animate all classic bosses this way"). Each
 * frame the boss's own body (with all its existing animation) is drawn into an offscreen image
 * covering its preview square, then shown through a posed [MeshGrid], so its parts also move on
 * their own: legs step, arms and wings swing, rings turn, segments slither, pieces bob, cores
 * breathe. Rig coordinates are 0..1 of the body's preview square (previewSpan radii wide, centred
 * previewDrop radii above the pose centre).
 */

/** Offscreen pixels per arena unit (capped by [MAX_PX]). */
private const val PX_PER_UNIT = 2.2f
private const val MAX_PX = 900
/** The offscreen square is this much wider than the preview square, so tails and orbits aren't clipped. */
private const val MARGIN = 1.5f
private const val RIG_GRID = 20

internal class MeshedBody(private val inner: BossBody, private val parts: List<Deform>) : BossBody {
    override val previewSpan: Float get() = inner.previewSpan
    override val previewDrop: Float get() = inner.previewDrop
    override fun DrawScope.drawHidden(p: BossPose): Boolean = with(inner) { drawHidden(p) }
    override fun DrawScope.drawOverDark(p: BossPose) { with(inner) { drawOverDark(p) } }

    private val grid = MeshGrid(RIG_GRID)
    /** The rig is placed on the preview square; the mesh covers the wider offscreen square. */
    private val zoomed = listOf<Deform>(Deform.Zoom(MARGIN, parts))
    private val offscreen = CanvasDrawScope()
    private var image: ImageBitmap? = null
    private var canvas: androidx.compose.ui.graphics.Canvas? = null

    override fun DrawScope.draw(p: BossPose) {
        val side = p.radius * inner.previewSpan * MARGIN
        val left = p.cx - side / 2f
        val top = p.cy - p.radius * inner.previewDrop - side / 2f
        val px = (side * PX_PER_UNIT).toInt().coerceIn(96, MAX_PX)
        var img = image
        if (img == null || img.width != px) {
            img = ImageBitmap(px, px)
            image = img
            canvas = androidx.compose.ui.graphics.Canvas(img)
        }
        val k = px / side
        offscreen.draw(this, layoutDirection, canvas!!, Size(px.toFloat(), px.toFloat())) {
            drawRect(Color.Transparent, blendMode = BlendMode.Clear)
            scale(k, k, Offset.Zero) { translate(-left, -top) { with(inner) { draw(p) } } }
        }
        grid.pose(zoomed, RigState(p.time, if (p.winding) 1f else 0f, p.phase))
        with(grid) { draw(img, 0f, 0f, px.toFloat(), left, top, side, 1f, null) }
    }
}

private fun feet(vararg f: Pair<Float, Float>, lift: Float, speed: Float, radius: Float = 0.07f): List<Deform> =
    f.mapIndexed { k, (u, v) -> Deform.Step(u, v, radius, lift, speed, k * Math.PI.toFloat()) }

/** Part lists for the twelve classics, placed on their preview squares. */
internal val classicRigs: Map<String, List<Deform>> = mapOf(
    // Breach: thrusters fire and bob, the hull rocks, the breach ring breathes, the drill tip sways.
    "breach" to listOf(
        Deform.Bob(0.28f, 0.63f, 0.06f, 0.02f, 5f, 0f), Deform.Bob(0.72f, 0.63f, 0.06f, 0.02f, 5f, 1.6f),
        Deform.Bend(0.5f, 0.42f, -1, 0.3f, 2f, 1.8f), Deform.Bend(0.5f, 0.42f, 1, 0.3f, 2f, 1.8f, 0.4f),
        Deform.Breathe(0.5f, 0.57f, 0.1f, 0.08f, 3f),
        Deform.Bob(0.5f, 0.8f, 0.07f, 0.015f, 2.4f, 0f, 1.2f),
        Deform.Ripple(0.66f, 0.012f, 4f, 10f)
    ),
    // Botmaster: antennas whip, the drone ring turns, each drone bobs on its own beat, the core breathes.
    "botmaster" to listOf(
        Deform.Bend(0.5f, 0.3f, -1, 0.15f, 6f, 3f), Deform.Bend(0.5f, 0.3f, 1, 0.15f, 6f, 3f, 1.1f),
        Deform.Bob(0.5f, 0.1f, 0.06f, 0.02f, 3.4f, 0.5f, 0.8f),
        Deform.Spin(0.5f, 0.53f, 0.38f, 0.13f, 0.75f, 1.25f, 9f, 1.3f),
        Deform.Bob(0.14f, 0.53f, 0.05f, 0.02f, 2.6f, 0f), Deform.Bob(0.28f, 0.38f, 0.05f, 0.02f, 2.6f, 1f),
        Deform.Bob(0.4f, 0.62f, 0.05f, 0.02f, 2.6f, 2f), Deform.Bob(0.75f, 0.6f, 0.05f, 0.02f, 2.6f, 3f),
        Deform.Bob(0.86f, 0.45f, 0.05f, 0.02f, 2.6f, 4f),
        Deform.Breathe(0.5f, 0.42f, 0.15f, 0.04f, 2.2f)
    ),
    // Worm Prime: the body slithers in a travelling wave, the maw gapes, the tail flicks.
    "worm_prime" to listOf(
        Deform.Breathe(0.25f, 0.47f, 0.13f, 0.07f, 3f),
        Deform.Bob(0.4f, 0.4f, 0.07f, 0.022f, 4f, 0f, 0.6f), Deform.Bob(0.5f, 0.43f, 0.07f, 0.022f, 4f, 0.9f, 0.6f),
        Deform.Bob(0.58f, 0.47f, 0.07f, 0.022f, 4f, 1.8f, 0.6f), Deform.Bob(0.66f, 0.52f, 0.06f, 0.022f, 4f, 2.7f, 0.6f),
        Deform.Bob(0.72f, 0.58f, 0.06f, 0.022f, 4f, 3.6f, 0.6f), Deform.Bob(0.78f, 0.66f, 0.06f, 0.026f, 4f, 4.5f, 0.8f)
    ),
    // Ransom: the shackle jolts against its lock, the chains swing, the keyhole throbs, the glyphs drift.
    "ransom" to listOf(
        Deform.Bob(0.5f, 0.25f, 0.13f, 0.018f, 6f, 0f),
        Deform.Bob(0.15f, 0.73f, 0.06f, 0.02f, 2f, 0f, 1f), Deform.Bob(0.88f, 0.73f, 0.06f, 0.02f, 2f, 1.5f, 1f),
        Deform.Breathe(0.5f, 0.6f, 0.08f, 0.1f, 3.2f),
        Deform.Bob(0.21f, 0.42f, 0.05f, 0.03f, 1.4f, 0f), Deform.Bob(0.21f, 0.56f, 0.05f, 0.03f, 1.4f, 2f),
        Deform.Bob(0.79f, 0.42f, 0.05f, 0.03f, 1.4f, 1f), Deform.Bob(0.79f, 0.56f, 0.05f, 0.03f, 1.4f, 3f)
    ),
    // Rootkit King: the tendril arms reach and curl, the robe ripples, the hood nods.
    "rootkit_king" to listOf(
        Deform.Bend(0.34f, 0.52f, -1, 0.2f, 8f, 1.8f, 0f, 10f), Deform.Bend(0.66f, 0.52f, 1, 0.2f, 8f, 1.8f, 1.2f, 10f),
        Deform.Ripple(0.55f, 0.02f, 2.6f, 9f),
        Deform.Bob(0.5f, 0.35f, 0.1f, 0.012f, 1.5f, 0f)
    ),
    // Syn Storm: the packet ring turns, the reactor breathes, the dish rocks.
    "syn_storm" to listOf(
        Deform.Spin(0.5f, 0.5f, 0.4f, 0.13f, 0.75f, 1.25f, 12f, 1.6f),
        Deform.Breathe(0.5f, 0.47f, 0.12f, 0.08f, 3.4f),
        Deform.Bend(0.5f, 0.47f, -1, 0.15f, 2f, 2f), Deform.Bend(0.5f, 0.47f, 1, 0.15f, 2f, 2f, 1f)
    ),
    // Kernel Panic: pin rows twitch on their own beats, the die throbs, the stray glyphs jump.
    "kernel_panic" to listOf(
        Deform.Bob(0.4f, 0.27f, 0.06f, 0.01f, 9f, 0f), Deform.Bob(0.6f, 0.27f, 0.06f, 0.01f, 9f, 2f),
        Deform.Bob(0.4f, 0.67f, 0.06f, 0.01f, 9f, 1f), Deform.Bob(0.6f, 0.67f, 0.06f, 0.01f, 9f, 3f),
        Deform.Bob(0.27f, 0.45f, 0.06f, 0.01f, 8f, 0.5f, 1.5f), Deform.Bob(0.73f, 0.45f, 0.06f, 0.01f, 8f, 2.5f, 1.5f),
        Deform.Breathe(0.5f, 0.47f, 0.12f, 0.06f, 4f),
        Deform.Bob(0.18f, 0.6f, 0.04f, 0.025f, 5f, 0f, 1f), Deform.Bob(0.62f, 0.67f, 0.04f, 0.025f, 5f, 2f, 1f)
    ),
    // Exfil: the wings bank, the twin thrusters flare, the nose sways.
    "exfil" to listOf(
        Deform.Bend(0.42f, 0.45f, -1, 0.3f, 6f, 1.7f, 0f, 8f), Deform.Bend(0.58f, 0.45f, 1, 0.3f, 6f, 1.7f, Math.PI.toFloat(), 8f),
        Deform.Bob(0.43f, 0.14f, 0.05f, 0.02f, 7f, 0f), Deform.Bob(0.57f, 0.14f, 0.05f, 0.02f, 7f, 1.3f),
        Deform.Bob(0.5f, 0.72f, 0.07f, 0.015f, 2f, 0f, 1.2f)
    ),
    // White Eye: the spiked ring turns, the pupil dilates, the eye rolls.
    "white_eye" to listOf(
        Deform.Spin(0.5f, 0.5f, 0.42f, 0.42f, 0.78f, 1.25f, 10f, 1.2f),
        Deform.Breathe(0.5f, 0.6f, 0.1f, 0.15f, 2.2f),
        Deform.Bob(0.5f, 0.55f, 0.18f, 0.012f, 1.3f, 0f, 1.5f)
    ),
    // Zombie: the arms swing from the shoulders, the legs shuffle, the head lolls.
    "zombie" to listOf(
        Deform.Bend(0.36f, 0.42f, -1, 0.15f, 7f, 2.4f, 0f, 10f), Deform.Bend(0.64f, 0.42f, 1, 0.15f, 7f, 2.4f, Math.PI.toFloat(), 10f)
    ) + feet(0.4f to 0.86f, 0.58f to 0.86f, lift = 0.02f, speed = 2.4f) + listOf(
        Deform.Bob(0.48f, 0.23f, 0.08f, 0.015f, 1.2f, 0f, 1.2f)
    ),
    // Spoofer: the mask drifts and tilts, the echo ghosts slide apart, the face breathes.
    "spoofer" to listOf(
        Deform.Bob(0.5f, 0.47f, 0.12f, 0.015f, 1.6f, 0f, 1f),
        Deform.Slide(-1, 0.4f, 0.015f, 0.03f, 2f), Deform.Slide(1, 0.6f, 0.015f, 0.03f, 2f),
        Deform.Breathe(0.5f, 0.47f, 0.12f, 0.05f, 2.4f)
    ),
    // Good Game: the treads judder, the turret ring turns, the cannon recoils, the exhaust puffs.
    "good_game" to listOf(
        Deform.Bob(0.15f, 0.55f, 0.07f, 0.008f, 14f, 0f), Deform.Bob(0.15f, 0.7f, 0.07f, 0.008f, 14f, 1.5f),
        Deform.Bob(0.85f, 0.55f, 0.07f, 0.008f, 14f, 0.7f), Deform.Bob(0.85f, 0.7f, 0.07f, 0.008f, 14f, 2.2f),
        Deform.Spin(0.5f, 0.57f, 0.12f, 0.12f, 0.6f, 1.2f, 10f, 1.4f),
        Deform.Bob(0.5f, 0.78f, 0.05f, 0.02f, 3f, 0f),
        Deform.Bob(0.4f, 0.2f, 0.05f, 0.02f, 2f, 0f, 1f), Deform.Bob(0.6f, 0.18f, 0.05f, 0.02f, 2f, 2f, 1f)
    )
)

private const val PI_F = Math.PI.toFloat()

/**
 * Part lists for the Boss Expansion (owner, 0.37.0: mesh rigs like the classics). Circuit Hydra is
 * left out on purpose: its heads are placed by the engine's HydraRig (they are the hitboxes and
 * the beams leave their mouths), and they already sway and strike on their own.
 */
internal val expansionRigs: Map<String, List<Deform>> = mapOf(
    // Vault Sentinel: the turret bobs, the vault core breathes, the shoulder blocks brace outward
    // on wind-up, the treads judder.
    "vault_sentinel" to listOf(
        Deform.Bob(0.5f, 0.22f, 0.07f, 0.012f, 3f, 0f, 0.6f),
        Deform.Breathe(0.5f, 0.47f, 0.12f, 0.07f, 3f),
        Deform.Slide(-1, 0.31f, 0.008f, 0.035f, 1.4f), Deform.Slide(1, 0.69f, 0.008f, 0.035f, 1.4f),
        Deform.Bob(0.3f, 0.74f, 0.06f, 0.006f, 12f, 0f), Deform.Bob(0.7f, 0.74f, 0.06f, 0.006f, 12f, 1.5f)
    ),
    // Rootkit Apostle: the spike crown turns, the eye dilates, the root ring on the floor turns the
    // other way, the orb floats.
    "rootkit_apostle" to listOf(
        Deform.Spin(0.5f, 0.45f, 0.3f, 0.3f, 0.55f, 1.3f, 8f, 1.1f),
        Deform.Breathe(0.5f, 0.47f, 0.1f, 0.12f, 2.6f),
        Deform.Spin(0.5f, 0.66f, 0.33f, 0.1f, 0.6f, 1.1f, 6f, 0.8f, PI_F),
        Deform.Bob(0.5f, 0.45f, 0.2f, 0.01f, 1.4f, 0f)
    ),
    // Pulse Bishop: the mitre sways and its sigil pulses, the wing blades beat, the lower shards bob.
    "pulse_bishop" to listOf(
        Deform.Bob(0.5f, 0.18f, 0.08f, 0.012f, 2f, 0f, 0.5f),
        Deform.Breathe(0.5f, 0.18f, 0.07f, 0.1f, 3f),
        Deform.Bend(0.4f, 0.42f, -1, 0.18f, 4f, 1.6f, 0f, 5f), Deform.Bend(0.6f, 0.42f, 1, 0.18f, 4f, 1.6f, 0.8f, 5f),
        Deform.Bob(0.3f, 0.6f, 0.06f, 0.02f, 2.2f, 0f), Deform.Bob(0.7f, 0.6f, 0.06f, 0.02f, 2.2f, 1.7f)
    ),
    // Packet Reaper: both scythes swing from the shoulders (wide on wind-up), the head bobs, the legs stride.
    "packet_reaper" to listOf(
        Deform.Bend(0.36f, 0.42f, -1, 0.3f, 8f, 1.4f, 0f, 14f), Deform.Bend(0.62f, 0.42f, 1, 0.33f, 8f, 1.4f, PI_F, 14f),
        Deform.Bob(0.5f, 0.32f, 0.07f, 0.012f, 2f, 0f),
        Deform.Bob(0.33f, 0.38f, 0.05f, 0.012f, 2f, 1f), Deform.Bob(0.67f, 0.38f, 0.05f, 0.012f, 2f, 2.5f)
    ) + feet(0.43f to 0.66f, 0.57f to 0.66f, lift = 0.015f, speed = 2f),
    // Worm Queen: her spikes turn, the brood core throbs, the hatchlings crawl, the whole hive pulses.
    "worm_queen" to listOf(
        Deform.Spin(0.5f, 0.48f, 0.3f, 0.3f, 0.6f, 1.3f, 7f, 0.9f),
        Deform.Breathe(0.5f, 0.48f, 0.12f, 0.12f, 2.2f),
        Deform.Bob(0.45f, 0.3f, 0.04f, 0.02f, 3f, 0f, 1f), Deform.Bob(0.69f, 0.52f, 0.04f, 0.02f, 3f, 1f, 1f),
        Deform.Bob(0.32f, 0.58f, 0.04f, 0.02f, 3f, 2f, 1f), Deform.Bob(0.33f, 0.62f, 0.04f, 0.02f, 3f, 3f, 1f),
        Deform.Bob(0.5f, 0.48f, 0.25f, 0.008f, 1.2f, 0f)
    ),
    // Glitch Forge: the furnace eye breathes, the side units hop, the panels shove out and glitch-twitch.
    "glitch_forge" to listOf(
        Deform.Breathe(0.5f, 0.33f, 0.1f, 0.12f, 3.4f),
        Deform.Bob(0.21f, 0.62f, 0.06f, 0.015f, 5f, 0f), Deform.Bob(0.83f, 0.58f, 0.06f, 0.015f, 5f, 1.3f),
        Deform.Bob(0.5f, 0.5f, 0.1f, 0.008f, 9f, 0f, 1.5f),
        Deform.Slide(-1, 0.35f, 0.006f, 0.03f, 6f), Deform.Slide(1, 0.66f, 0.006f, 0.03f, 6f)
    ),
    // Botnet Monarch: the command ring turns, the spiked shell turns the other way, the core
    // breathes, the antennas bob.
    "botnet_monarch" to listOf(
        Deform.Spin(0.5f, 0.5f, 0.5f, 0.4f, 0.85f, 1.1f, 10f, 1f),
        Deform.Spin(0.5f, 0.45f, 0.27f, 0.27f, 0.6f, 1.3f, 7f, 1.3f, PI_F),
        Deform.Breathe(0.5f, 0.45f, 0.1f, 0.12f, 2.8f),
        Deform.Bob(0.42f, 0.18f, 0.04f, 0.015f, 3f, 0f), Deform.Bob(0.5f, 0.12f, 0.04f, 0.015f, 3f, 1f), Deform.Bob(0.6f, 0.18f, 0.04f, 0.015f, 3f, 2f)
    ),
    // Ransom King: the crown bobs, the arms swing, the padlocked chests sway on their chains, the legs stomp.
    "ransom_king" to listOf(
        Deform.Bob(0.5f, 0.12f, 0.1f, 0.015f, 2.4f, 0f),
        Deform.Bend(0.36f, 0.4f, -1, 0.15f, 6f, 1.8f, 0f, 10f), Deform.Bend(0.64f, 0.4f, 1, 0.15f, 6f, 1.8f, PI_F, 10f),
        Deform.Bob(0.1f, 0.5f, 0.07f, 0.025f, 1.6f, 0f, 0.6f), Deform.Bob(0.9f, 0.53f, 0.07f, 0.025f, 1.6f, 1.4f, 0.6f)
    ) + feet(0.42f to 0.7f, 0.58f to 0.7f, lift = 0.012f, speed = 1.8f),
    // Black Ice Overlord: the ice crown bobs, the spiked arms swing, the core breathes, the legs
    // step, the floating shards drift.
    "black_ice_overlord" to listOf(
        Deform.Bob(0.5f, 0.14f, 0.1f, 0.012f, 2f, 0f),
        Deform.Bend(0.4f, 0.4f, -1, 0.15f, 6f, 1.6f, 0f, 10f), Deform.Bend(0.6f, 0.4f, 1, 0.15f, 6f, 1.6f, 1.2f, 10f),
        Deform.Breathe(0.5f, 0.5f, 0.1f, 0.08f, 2.6f),
        Deform.Bob(0.22f, 0.65f, 0.04f, 0.025f, 2.5f, 0f), Deform.Bob(0.75f, 0.68f, 0.04f, 0.025f, 2.5f, 1.5f), Deform.Bob(0.46f, 0.74f, 0.04f, 0.02f, 2.5f, 3f)
    ) + feet(0.43f to 0.73f, 0.57f to 0.73f, lift = 0.012f, speed = 1.6f),
    // Spectral Firewall: the spiked shell turns, the core breathes, the flame wall segments flicker in sequence.
    "spectral_firewall" to listOf(
        Deform.Spin(0.5f, 0.47f, 0.27f, 0.27f, 0.6f, 1.3f, 8f, 1f),
        Deform.Breathe(0.5f, 0.47f, 0.1f, 0.12f, 3f),
        Deform.Bob(0.16f, 0.68f, 0.06f, 0.012f, 6f, 0f), Deform.Bob(0.33f, 0.72f, 0.06f, 0.012f, 6f, 1f), Deform.Bob(0.5f, 0.74f, 0.06f, 0.012f, 6f, 2f),
        Deform.Bob(0.68f, 0.72f, 0.06f, 0.012f, 6f, 3f), Deform.Bob(0.84f, 0.68f, 0.06f, 0.012f, 6f, 4f)
    ),
    // Nullshade Specter: the hood nods, the cloak billows, the void ring turns, the eyes flare.
    "nullshade" to listOf(
        Deform.Bob(0.5f, 0.28f, 0.12f, 0.012f, 1.3f, 0f, 1f),
        Deform.Ripple(0.45f, 0.02f, 2.4f, 9f),
        Deform.Spin(0.5f, 0.77f, 0.38f, 0.08f, 0.7f, 1.2f, 6f, 0.8f),
        Deform.Breathe(0.5f, 0.31f, 0.08f, 0.08f, 2.2f)
    )
)
