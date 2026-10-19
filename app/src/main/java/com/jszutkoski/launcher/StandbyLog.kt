package com.jszutkoski.launcher

import android.content.Context
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The bedside clocks' last steps (shown, covered, unlock asked for, brought back), to find out
 * what a phone's own Android does around them. Only the steps and the phone's state, never
 * anything on the screen; kept in the app's own files and copied only from settings.
 */
object StandbyLog {
    private const val FILE = "standby_log.txt"
    private const val MAX_LINES = 150

    private val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT)

    @Synchronized
    fun note(context: Context, step: String) {
        val file = File(context.applicationContext.filesDir, FILE)
        try {
            val lines = if (file.exists()) file.readLines() else emptyList()
            file.writeText((lines + "${time.format(Date())} $step").takeLast(MAX_LINES).joinToString("\n"))
        } catch (e: IOException) {
            warn("Could not keep the standby log", e)
        }
    }

    @Synchronized
    fun read(context: Context): String = try {
        File(context.applicationContext.filesDir, FILE).takeIf { it.exists() }?.readText().orEmpty()
    } catch (e: IOException) {
        ""
    }
}
