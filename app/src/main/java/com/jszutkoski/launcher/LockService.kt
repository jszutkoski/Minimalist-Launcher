package com.jszutkoski.launcher

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * The only way for an ordinary app to lock the screen, and to open a screen from the
 * background: Android lets an app whose accessibility service is on do both. It never reads
 * what is on screen and hears nothing about other apps. It locks on request; on the charger it
 * brings up the night's clock each time the phone locks ([ChargerActivity]), and elsewhere, with
 * "Standby on every wake", it puts the bedside clock on top each time the screen turns off.
 */
class LockService : AccessibilityService() {
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            StandbyLog.note(context, "service ${intent.action?.substringAfterLast('.')}")
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
            if (inCall(context)) return
            // On the charger, the night's clock comes over the lock screen instead.
            if (ChargerActivity.wanted(context)) {
                ChargerActivity.startWhenLocked(context)
                return
            }
            if (!Prefs(context).standbyOnWake || StandbyActivity.inFront || StandbyActivity.returning) return
            // Home was on screen: standby joins the home task, so unlocking fades back to home
            // instead of the system's sideways task animation.
            if (MainActivity.inFront) StandbyActivity.startOverHome(context) else StandbyActivity.startOnTop(context)
        }
    }
    private var listening = false

    override fun onServiceConnected() {
        instance = this
        // A system broadcast; only a receiver registered at runtime gets it.
        registerReceiver(
            screenOff,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_POWER_CONNECTED)
            },
        )
        listening = true
    }

    override fun onUnbind(intent: Intent?): Boolean {
        stopListening()
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        stopListening()
        instance = null
        super.onDestroy()
    }

    private fun stopListening() {
        if (listening) unregisterReceiver(screenOff)
        listening = false
    }

    /** Subscribed to nothing that ever comes: see lock_service.xml. */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    companion object {
        @Volatile
        private var instance: LockService? = null

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?: return false
            val me = ComponentName(context, LockService::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }

        /** How the screen is locked: the service's global action. Replaced only by tests. */
        internal var lockAction: () -> Boolean = { instance?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) == true }

        /** True when the screen was locked. */
        fun lock(): Boolean = lockAction()

        /** Ringing or on a call: the call screen must never be covered, nor the phone locked under it. */
        fun inCall(context: Context): Boolean {
            val mode = context.getSystemService(AudioManager::class.java)?.mode ?: return false
            return mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION ||
                mode == AudioManager.MODE_RINGTONE
        }
    }
}
