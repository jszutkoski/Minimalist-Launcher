package com.jszutkoski.launcher

/** What the clock app can be asked from the search. */
sealed interface ClockCommand {
    /** A countdown of [seconds], started right away. */
    data class Timer(val seconds: Int) : ClockCommand

    /** An alarm at the next [hour]:[minute], 24-hour. */
    data class Alarm(val hour: Int, val minute: Int) : ClockCommand

    /** Just the clock app's timers or alarms, for the word alone. */
    data class Page(val alarms: Boolean) : ClockCommand
}

/**
 * Timers and alarms typed in the search, in English or Portuguese: "timer 10 min", "timer de
 * 1h30", "alarme 7:30", "alarm 7am", "alarme amanhã às 6h". With no word first, only what
 * can't be anything else: "10 min" is a timer and "7:30" an alarm, while "7h" may be either.
 */
object ClockSearch {
    private val timerWords = listOf("timer", "timers", "temporizador", "temporizadores")
    private val alarmWords = listOf("alarme", "alarmes", "alarm", "alarms", "despertador", "despertar")

    /** Little words between the command and the amount, dropped. */
    private val fillers = setOf(
        "de", "do", "da", "para", "pra", "por", "em", "as", "a", "o", "um", "uma", "me", "e",
        "amanha", "hoje", "at", "for", "in", "the", "an", "tomorrow", "today", "set", "start",
    )

    private val hours = "horas|hora|hrs|hr|hours|hour|h"
    private val minutes = "minutos|minuto|mins|min|minutes|minute|m"
    private val seconds = "segundos|segundo|segs|seg|seconds|second|secs|sec|s"
    private val part = Regex("(\\d+(?:[.,]\\d+)?)\\s*($hours|$minutes|$seconds)?(?![a-z])")
    private val colonDuration = Regex("(\\d{1,2}):(\\d{2})(?::(\\d{2}))?")
    private val timeOfDay = Regex("(\\d{1,2})(?:\\s*([:h])\\s*(\\d{2})?)?\\s*(?:min)?\\s*(am|pm)?")

    /** The timers and alarms this query asks for, most likely first; empty for anything else. */
    fun parse(query: String): List<ClockCommand> {
        val words = AppSearch.normalize(query).trim()
            .replace(Regex("[-‐]"), " ")
            .split(Regex("\\s+")).filter(String::isNotEmpty)
        if (words.isEmpty()) return emptyList()
        val first = words.first()
        val last = words.last()
        val kind = when {
            first in timerWords -> timerWords
            first in alarmWords -> alarmWords
            words.size > 1 && last in timerWords -> timerWords
            words.size > 1 && last in alarmWords -> alarmWords
            else -> null
        }
        if (kind == null) {
            // The word alone, or its start from three letters: "tem" offers the timers.
            if (words.size == 1 && first.length >= 3) {
                if (timerWords.any { it.startsWith(first) }) return listOf(ClockCommand.Page(alarms = false))
                if (alarmWords.any { it.startsWith(first) }) return listOf(ClockCommand.Page(alarms = true))
            }
            val rest = amount(words)
            return listOfNotNull(duration(rest, bare = true), alarm(rest, bare = true))
        }
        val rest = amount(if (first in kind) words.drop(1) else words.dropLast(1))
        if (rest.isEmpty()) return listOf(ClockCommand.Page(alarms = kind === alarmWords))
        val command = if (kind === timerWords) duration(rest, bare = false) else alarm(rest, bare = false)
        return listOfNotNull(command)
    }

    /** The words that give the amount, with the fillers gone and the spoken halves as numbers. */
    private fun amount(words: List<String>): String = words
        .filter { it !in fillers }
        .joinToString(" ")
        .replace(Regex("^meia hora$|^half hour$|^half$"), "30 min")
        .replace(Regex("(\\d+)\\s*(?:horas?|h)\\s*meia$"), "$1 h 30 min")
        .replace(Regex("^hora$|^hour$"), "1 h")
        .replace(Regex("(\\d+)\\s*(?:horas?|h)\\s*quinze$"), "$1 h 15 min")

    /**
     * "10", "10 min", "1h30", "1 h 30 min", "1,5 h", "90 s", "1:30" (a minute and a half). A bare
     * number is minutes after a timer word, and nothing on its own. Up to a day, as Android allows.
     */
    private fun duration(text: String, bare: Boolean): ClockCommand.Timer? {
        if (text.isEmpty()) return null
        colonDuration.matchEntire(text)?.let { m ->
            if (bare) return null
            val (a, b, c) = m.destructured
            val total = if (c.isEmpty()) a.toInt() * 60 + b.toInt() else a.toInt() * 3600 + b.toInt() * 60 + c.toInt()
            return timer(total, b.toInt() < 60 && (c.isEmpty() || c.toInt() < 60))
        }
        var position = 0
        var total = 0.0
        var lastUnit = Int.MAX_VALUE
        var anyUnit = false
        while (position < text.length) {
            if (text[position] == ' ') {
                position++
                continue
            }
            val m = part.matchAt(text, position) ?: return null
            val value = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
            val unit = when {
                m.groupValues[2].isEmpty() -> when (lastUnit) {
                    // "1h30": what follows the hours is minutes, and after minutes, seconds.
                    3600 -> 60
                    60 -> 1
                    Int.MAX_VALUE -> if (bare) return null else 60
                    else -> return null
                }
                m.groupValues[2].matches(Regex(hours)) -> 3600
                m.groupValues[2].matches(Regex(minutes)) -> 60
                else -> 1
            }
            if (m.groupValues[2].isNotEmpty()) anyUnit = true
            // Biggest unit first, each once: "30 min 1 h" is no duration.
            if (unit >= lastUnit) return null
            lastUnit = unit
            total += value * unit
            position = m.range.last + 1
        }
        if (bare && !anyUnit) return null
        return timer(Math.round(total).toInt(), true)
    }

    private fun timer(total: Int, valid: Boolean) =
        if (valid && total in 1..MAX_TIMER_SECONDS) ClockCommand.Timer(total) else null

    /**
     * "7", "7:30", "7h", "7h30", "19:45", "7am", "7:30 pm", "7 da noite", "meio-dia". A bare
     * number is an hour after an alarm word, and nothing on its own.
     */
    private fun alarm(text: String, bare: Boolean): ClockCommand.Alarm? {
        when (text) {
            "meio dia", "noon" -> return ClockCommand.Alarm(12, 0)
            "meia noite", "midnight" -> return ClockCommand.Alarm(0, 0)
        }
        var rest = text
        var later = false
        var earlier = false
        for (suffix in listOf("noite", "tarde", "night", "evening", "afternoon")) {
            if (rest.endsWith(" $suffix")) {
                rest = rest.removeSuffix(" $suffix")
                later = true
            }
        }
        for (suffix in listOf("manha", "morning")) {
            if (rest.endsWith(" $suffix")) {
                rest = rest.removeSuffix(" $suffix")
                earlier = true
            }
        }
        val m = timeOfDay.matchEntire(rest.trim()) ?: return null
        val (h, mark, mm, meridiem) = m.destructured
        // "7" alone could be anything; "7h" or "7:30" is a time.
        if (bare && mark.isEmpty() && meridiem.isEmpty() && !later && !earlier) return null
        var hour = h.toInt()
        val minute = mm.ifEmpty { "0" }.toInt()
        if (minute > 59) return null
        when {
            meridiem.isNotEmpty() || later || earlier -> {
                if (hour !in 1..12) return null
                val afternoon = meridiem == "pm" || later
                hour = when {
                    hour == 12 -> if (afternoon) 12 else 0
                    afternoon -> hour + 12
                    else -> hour
                }
            }
            hour > 23 -> return null
        }
        return ClockCommand.Alarm(hour, minute)
    }

    /** "1 h 30 min", "45 s": the same short units in English and Portuguese. */
    fun describe(seconds: Int): String = listOfNotNull(
        (seconds / 3600).takeIf { it > 0 }?.let { "$it h" },
        (seconds % 3600 / 60).takeIf { it > 0 }?.let { "$it min" },
        (seconds % 60).takeIf { it > 0 }?.let { "$it s" },
    ).joinToString(" ")

    /** Android's limit for a timer: a day. */
    const val MAX_TIMER_SECONDS = 24 * 60 * 60
}
