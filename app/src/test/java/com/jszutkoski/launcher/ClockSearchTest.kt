package com.jszutkoski.launcher

import com.jszutkoski.launcher.ClockCommand.Alarm
import com.jszutkoski.launcher.ClockCommand.Page
import com.jszutkoski.launcher.ClockCommand.Timer
import org.junit.Assert.assertEquals
import org.junit.Test

class ClockSearchTest {
    private fun parse(query: String) = ClockSearch.parse(query)

    @Test
    fun timersInEitherLanguage() {
        assertEquals(listOf(Timer(600)), parse("timer 10"))
        assertEquals(listOf(Timer(600)), parse("timer 10 min"))
        assertEquals(listOf(Timer(600)), parse("Timer de 10 minutos"))
        assertEquals(listOf(Timer(5400)), parse("timer 1h30"))
        assertEquals(listOf(Timer(5400)), parse("temporizador 1 hora e meia"))
        assertEquals(listOf(Timer(5400)), parse("timer 1,5 h"))
        assertEquals(listOf(Timer(1800)), parse("timer meia hora"))
        assertEquals(listOf(Timer(1800)), parse("timer for half an hour"))
        assertEquals(listOf(Timer(90)), parse("timer 90 s"))
        assertEquals(listOf(Timer(90)), parse("timer 1:30"))
        assertEquals(listOf(Timer(330)), parse("timer 5 min 30 s"))
        assertEquals(listOf(Timer(600)), parse("10 min timer"))
    }

    @Test
    fun alarmsInEitherLanguage() {
        assertEquals(listOf(Alarm(7, 0)), parse("alarme 7"))
        assertEquals(listOf(Alarm(7, 30)), parse("alarme 7:30"))
        assertEquals(listOf(Alarm(7, 30)), parse("Alarme às 7h30"))
        assertEquals(listOf(Alarm(6, 0)), parse("alarme amanhã às 6h"))
        assertEquals(listOf(Alarm(19, 45)), parse("despertador 19:45"))
        assertEquals(listOf(Alarm(19, 0)), parse("alarme 7 da noite"))
        assertEquals(listOf(Alarm(7, 0)), parse("alarm 7am"))
        assertEquals(listOf(Alarm(19, 30)), parse("alarm at 7:30 pm"))
        assertEquals(listOf(Alarm(0, 15)), parse("alarm 12:15 am"))
        assertEquals(listOf(Alarm(12, 0)), parse("alarme meio-dia"))
    }

    @Test
    fun withNoWordOnlyWhatCantBeAnythingElse() {
        assertEquals(listOf(Timer(600)), parse("10 min"))
        assertEquals(listOf(Alarm(7, 30)), parse("7:30"))
        // A time of day or a duration: both are offered.
        assertEquals(listOf(Timer(7 * 3600), Alarm(7, 0)), parse("7h"))
        assertEquals(emptyList<ClockCommand>(), parse("10"))
        assertEquals(listOf(Alarm(5, 0)), parse("5am"))
        assertEquals(emptyList<ClockCommand>(), parse("spotify"))
        assertEquals(emptyList<ClockCommand>(), parse("12*7"))
    }

    @Test
    fun theWordAloneOpensTheClockApp() {
        assertEquals(listOf(Page(alarms = false)), parse("timer"))
        assertEquals(listOf(Page(alarms = true)), parse("alarmes"))
        assertEquals(listOf(Page(alarms = true)), parse("desp"))
        // Two letters stay with the apps.
        assertEquals(emptyList<ClockCommand>(), parse("ti"))
    }

    @Test
    fun nonsenseIsNoCommand() {
        assertEquals(emptyList<ClockCommand>(), parse("timer 30 min 1 h"))
        assertEquals(emptyList<ClockCommand>(), parse("timer 25 h"))
        assertEquals(emptyList<ClockCommand>(), parse("timer 0"))
        assertEquals(emptyList<ClockCommand>(), parse("alarme 25:00"))
        assertEquals(emptyList<ClockCommand>(), parse("alarme 7:75"))
        assertEquals(emptyList<ClockCommand>(), parse("alarme 13 pm"))
        assertEquals(emptyList<ClockCommand>(), parse("timer banana"))
    }

    @Test
    fun durationsReadShort() {
        assertEquals("10 min", ClockSearch.describe(600))
        assertEquals("1 h 30 min", ClockSearch.describe(5400))
        assertEquals("1 min 30 s", ClockSearch.describe(90))
        assertEquals("2 h", ClockSearch.describe(7200))
    }

    @Test
    fun twelveAtNightIsMidnight() {
        assertEquals(listOf(Alarm(0, 0)), parse("alarme 12 da noite"))
        assertEquals(listOf(Alarm(12, 0)), parse("alarme 12 da tarde"))
    }

    @Test
    fun aHugeTimerIsNoTimer() {
        // 2^32 + 60 seconds: as an Int it would wrap round to a one-minute timer.
        assertEquals(emptyList<ClockCommand>(), parse("timer 4294967356 s"))
    }
}
