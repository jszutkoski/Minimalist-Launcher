package com.jszutkoski.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

/**
 * The screen going off, the phone unlocking and the charger plugged in: what brings up the
 * bedside clocks. On the charger the night's clock comes each time the phone locks
 * ([ChargerActivity]); elsewhere, with "Standby on every wake", the bedside clock goes on top
 * each time the screen turns off.
 *
 * Heard by [LockService] when it is on. Without it, by the home app's own process: Android lets
 * the home app open a screen from the background too, but it can't lock the phone, so the
 * clocks come only over a phone already locked, as the power button leaves it.
 */
class ScreenEvents(private val fromHome: Boolean = false) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // The service hears it too, and goes further: it can lock.
        if (fromHome && LockService.running) return
        StandbyLog.note(context, "${if (fromHome) "home" else "service"} ${intent.action?.substringAfterLast('.')}")
        when (intent.action) {
            Intent.ACTION_USER_PRESENT -> {
                MainActivity.onUnlocked()
                return
            }
            Intent.ACTION_POWER_CONNECTED -> {
                ChargerActivity.onPlugged(context)
                return
            }
        }
        if (LockService.inCall(context)) return
        // On the charger, the night's clock comes over the lock screen instead.
        if (ChargerActivity.wanted(context)) {
            ChargerActivity.startWhenLocked(context)
            return
        }
        if (!Prefs(context).standbyOnWake || StandbyActivity.inFront) return
        // Home was on screen: standby joins the home task, so unlocking fades back to home
        // instead of the system's sideways task animation.
        if (MainActivity.inFront) StandbyActivity.startOverHome(context) else StandbyActivity.startOnTop(context)
    }

    companion object {
        // System broadcasts; only a receiver registered at runtime gets them.
        fun filter() = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_POWER_CONNECTED)
        }

        /** The app the home receiver is registered with: one per process. */
        private var homeApp: Context? = null

        /** Once per process, from home: for as long as the process lives, which for home is always. */
        fun listenFromHome(context: Context) {
            val app = context.applicationContext
            if (homeApp === app) return
            homeApp = app
            app.registerReceiver(ScreenEvents(fromHome = true), filter())
        }

        /** Whether the clocks can come without the service: as the phone's home app. */
        fun canCome(context: Context) = LockService.isEnabled(context) || SystemActions.isDefaultHome(context)
    }
}
