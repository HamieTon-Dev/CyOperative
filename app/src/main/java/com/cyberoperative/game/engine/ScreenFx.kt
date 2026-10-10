package com.cyberoperative.game.engine

import kotlin.math.max

/**
 * Screen effects (Boss Expansion S12): shake, colour flash, hit-stop and slow
 * motion. Shake and flash are drawn by the renderer; hit-stop and slow motion
 * change how much game time each frame advances. All timers run on real time.
 */
class ScreenFx {
    /** Shake strength in arena units, fading out over [shakeDuration]. */
    var shakeAmount = 0f; private set
    var shakeTime = 0f; private set
    var shakeDuration = 0f; private set
    /** Full-screen flash colour (ARGB) and its alpha right now. */
    var flashColor = 0xFFFFFFFFL; private set
    var flashTime = 0f; private set
    var flashDuration = 0f; private set
    var flashPeak = 0f; private set
    /** Seconds the game is frozen for (impact beat). */
    var hitStop = 0f; private set
    /** Seconds of slow motion left and how fast time runs during it. */
    var slowMo = 0f; private set
    var slowFactor = 1f; private set

    /** Current shake strength (0 when still). */
    val shakeNow: Float get() = if (shakeTime <= 0f || shakeDuration <= 0f) 0f else shakeAmount * (shakeTime / shakeDuration)
    /** Current flash alpha (0 when none). */
    val flashNow: Float get() = if (flashTime <= 0f || flashDuration <= 0f) 0f else flashPeak * (flashTime / flashDuration)

    fun shake(amount: Float, seconds: Float) {
        if (amount < shakeNow) return
        shakeAmount = amount; shakeTime = seconds; shakeDuration = seconds
    }

    fun flash(color: Long, alpha: Float, seconds: Float) {
        if (alpha < flashNow) return
        flashColor = color; flashPeak = alpha; flashTime = seconds; flashDuration = seconds
    }

    fun hitStop(seconds: Float) { hitStop = max(hitStop, seconds) }

    fun slowMotion(seconds: Float, factor: Float) {
        slowMo = max(slowMo, seconds); slowFactor = factor
    }

    /**
     * Advances the effect timers by [real] seconds and returns how much game
     * time should pass this frame.
     */
    fun tick(real: Float): Float {
        if (shakeTime > 0f) shakeTime = max(0f, shakeTime - real)
        if (flashTime > 0f) flashTime = max(0f, flashTime - real)
        if (hitStop > 0f) {
            hitStop = max(0f, hitStop - real)
            return 0f
        }
        if (slowMo > 0f) {
            slowMo = max(0f, slowMo - real)
            // Ease back to full speed over the last third.
            val ease = if (slowMo < 0.4f) 1f - (1f - slowFactor) * (slowMo / 0.4f) else slowFactor
            return real * ease
        }
        return real
    }

    fun clear() {
        shakeTime = 0f; flashTime = 0f; hitStop = 0f; slowMo = 0f
    }
}
