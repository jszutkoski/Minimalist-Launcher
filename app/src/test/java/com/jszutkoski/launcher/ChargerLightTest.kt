package com.jszutkoski.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargerLightTest {
    @Test
    fun theDarkIsAGlow() {
        assertEquals(ChargerLight.DARK, ChargerLight.brightness(0f))
        assertEquals(ChargerLight.DARK, ChargerLight.brightness(5f))
    }

    @Test
    fun daylightIsTheTop() {
        assertEquals(ChargerLight.BRIGHT, ChargerLight.brightness(5_000f))
        assertEquals(ChargerLight.BRIGHT, ChargerLight.brightness(100_000f))
    }

    @Test
    fun aLitRoomIsInBetweenAndMoreLightIsNeverDimmer() {
        val room = ChargerLight.brightness(100f)
        assertTrue(room > ChargerLight.DARK && room < 0.4f)
        val levels = listOf(1f, 10f, 50f, 200f, 1_000f, 3_000f, 10_000f).map(ChargerLight::brightness)
        assertEquals(levels, levels.sorted())
    }
}
