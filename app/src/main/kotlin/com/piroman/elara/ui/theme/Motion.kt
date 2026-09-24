package com.aesthetic.spotify.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing

/**
 * Shared motion tokens — deliberately short.
 *
 * The first version of this used 180/300/380 ms plus full-screen
 * `scaleIn`/`scaleOut` on every navigation. On a real phone that read as lag,
 * for two reasons that are worth remembering:
 *
 *  1. Scaling a whole screen forces a GPU layer the size of the display, and
 *     re-rasterising it every frame is expensive on a mid-range device. A fade
 *     is an alpha change on a layer that already exists, so it is far cheaper.
 *  2. Longer durations do not make a transition *smoother*, they just mean more
 *     frames spent doing the expensive thing. 180 ms of a cheap effect hides the
 *     cut just as well as 380 ms of an expensive one.
 *
 * So: nothing here runs past ~250 ms, and nothing scales. Slides are kept only
 * where a surface genuinely travels in from off-screen — the full-screen player
 * and the queue panel — because those read as broken without the movement.
 *
 * The remaining rule of thumb: an entrance is longer than the exit that replaces
 * it, so the outgoing thing is gone before the incoming one has settled.
 */
object Motion {

    /** Small state changes: a tint, a colour, a progress bar. */
    const val Quick = 120

    /** The default for a surface appearing or a screen cross-fading. */
    const val Standard = 180

    /** Only for movement across a large distance, e.g. the player sliding up. */
    const val Expressive = 240

    /** Entrances: quick to start, gentle to settle. */
    val Decelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** Exits: unhurried start, quick departure. Pair with [Quick]. */
    val Accelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** Symmetric movement where nothing enters or leaves — a cross-fade. */
    val StandardEasing: Easing = FastOutSlowInEasing
}
