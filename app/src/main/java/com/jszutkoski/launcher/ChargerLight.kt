package com.jszutkoski.launcher

import kotlin.math.log10
import kotlin.math.pow

/**
 * The charger clock's brightness for the light around it, from the phone's light sensor: a
 * glow in a dark bedroom, rising slowly with the room's light, and plainly readable in a car
 * by day.
 */
object ChargerLight {
    /** In the dark: near the lowest the screen goes. */
    const val DARK = 0.02f

    /** In full daylight. */
    const val BRIGHT = 0.8f

    /** At or below this, it is dark. */
    private const val DARK_LUX = 5f

    /** At or above this, full daylight. */
    private const val BRIGHT_LUX = 5000f

    /**
     * The window brightness for [lux] of light. The eye takes light in steps of ten, so the
     * way up follows the lux on a log scale, and holds back at first: a lamp across the room
     * barely lifts it, a sunny windscreen takes it to the top.
     */
    fun brightness(lux: Float): Float {
        if (lux <= DARK_LUX) return DARK
        val share = ((log10(lux) - log10(DARK_LUX)) / (log10(BRIGHT_LUX) - log10(DARK_LUX))).coerceIn(0f, 1f)
        return DARK + (BRIGHT - DARK) * share.pow(1.5f)
    }
}
