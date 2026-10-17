package com.jszutkoski.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalculatorTest {
    private fun br(text: String) = Calculator.evaluate(text, decimal = ',')
    private fun en(text: String) = Calculator.evaluate(text, decimal = '.')

    @Test
    fun sums() {
        assertEquals(84.0, br("12*7")!!, 0.0)
        assertEquals(3.5, br("2,5 + 1")!!, 0.0)
        assertEquals(30.0, br("200 x 15%")!!, 0.0)
        assertEquals(2.5, br("10 ÷ 4")!!, 0.0)
        assertEquals(9.0, br("(1+2)*3")!!, 0.0)
        assertEquals(512.0, br("2^3^2")!!, 0.0)
        assertEquals(0.5, br("2^-1")!!, 0.0)
    }

    @Test
    fun thousandsTheLocalWay() {
        assertEquals(1001.0, br("1.000 + 1")!!, 0.0)
        assertEquals(1800.0, br("1.500 + 300")!!, 0.0)
        assertEquals(1234567.0, br("1.234.567 * 1")!!, 0.0)
        assertEquals(1234.5, br("1.234,5 + 0")!!, 0.0)
        assertEquals(1.5, br("1.5 + 0")!!, 0.0)
        assertEquals(1001.0, en("1,000 + 1")!!, 0.0)
        assertEquals(2.0, en("1.000 + 1")!!, 0.0)
        assertEquals(3.5, en("2,5 + 1")!!, 0.0)
    }

    @Test
    fun usualPrecedence() {
        assertEquals(-4.0, br("-2^2")!!, 0.0)
        assertEquals(7.0, br("1+2*3")!!, 0.0)
    }

    @Test
    fun percentagesOfWhatCameBefore() {
        assertEquals(220.0, br("200 + 10%")!!, 1e-9)
        assertEquals(180.0, br("200 - 10%")!!, 1e-9)
        assertEquals(0.5, br("50% + 0")!!, 1e-9)
    }

    @Test
    fun notSums() {
        assertNull(br("1/0"))
        assertNull(br("(1+2"))
        assertNull(br("-5"))
        assertNull(br("12"))
        assertNull(br("1..2+1"))
        assertNull(br("11 98765-4321"))
        assertNull(br("(11) 98765-4321"))
        assertNull(br("98765-4321"))
        assertNull(br("+55 11 98765-4321"))
        assertNull(br("17/10/2026"))
        assertNull(br("2026-10-17"))
        assertNull(br("whatsapp"))
    }
}
