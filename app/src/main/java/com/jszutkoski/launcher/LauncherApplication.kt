package com.jszutkoski.launcher

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager

/**
 * Lives as long as the launcher's process, which Android keeps running for the home app.
 * When the screen turns off it puts standby on top, so the next wake shows the bedside
 * clock over the lock screen. Home apps may start screens from the background; other apps
 * may not, so this only works while Minimalist is the home app.
 */
class LauncherApplication : Application() {
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (Prefs(context).standbyOnWake && !inCall()) StandbyActivity.startOnTop(context)
        }
    }

    override fun onCreate() {
        super.onCreate()
        // A system broadcast; it can only be received by a receiver registered at runtime.
        registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF))
    }

    /** The proximity sensor turns the screen off during calls: never cover the call screen. */
    private fun inCall(): Boolean {
        val mode = getSystemService(AudioManager::class.java)?.mode ?: return false
        return mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION ||
            mode == AudioManager.MODE_RINGTONE
    }
}
