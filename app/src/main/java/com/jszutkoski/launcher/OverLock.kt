package com.jszutkoski.launcher

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock

/**
 * Android's own unlock asked for over a clock shown on the lock screen. Swiping home over it
 * would land on the home screen, which Android covers with its lock screen; instead the clock
 * comes straight back, asking for the fingerprint or PIN again, as if the swipe did nothing.
 */
object OverLock {
    /** Opened again to ask for the unlock as soon as it shows. */
    const val EXTRA_ASK = "ask_unlock"

    /** For another home app than Minimalist, whose home screen can't bring the clock back itself. */
    private const val BACK_MS = 500L

    /** After the unlock is backed out of, the moment for the clock to have its window again. */
    private const val SETTLE_MS = 800L

    private val handler = Handler(Looper.getMainLooper())
    private var pending: Class<out Activity>? = null
    private var takenAt = 0L

    /** Home or recents while [screen] had Android's unlock up. */
    fun left(screen: Activity) {
        pending = screen.javaClass
        val app = screen.applicationContext
        handler.postDelayed({ takeBack(app) }, BACK_MS)
    }

    /**
     * Android's unlock went away without the phone opening. Backed out of with back, the clock
     * is in front again and nothing happens; swiped away, Android's lock screen shows instead,
     * and the clock comes back.
     */
    fun cancelled(screen: Activity) {
        val app = screen.applicationContext
        val kind = screen.javaClass
        handler.postDelayed({
            if (screen.isFinishing || screen.isDestroyed || screen.hasWindowFocus()) return@postDelayed
            // Already brought back by home or recents.
            if (SystemClock.elapsedRealtime() - takenAt < SETTLE_MS * 2) return@postDelayed
            pending = kind
            takeBack(app)
        }, SETTLE_MS)
    }

    /**
     * The clock left by home, put back over the lock screen; true if there was one. Called by
     * Minimalist's home screen as home comes to it, and by the fallback above.
     */
    fun takeBack(context: Context): Boolean {
        val screen = pending ?: return false
        pending = null
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        val power = context.getSystemService(PowerManager::class.java)
        // Unlocked meanwhile, the screen gone off, or a call: nothing to come back to.
        if (keyguard?.isKeyguardLocked != true || power?.isInteractive != true || LockService.inCall(context)) return false
        takenAt = SystemClock.elapsedRealtime()
        return try {
            context.startActivity(
                Intent(context, screen)
                    .putExtra(EXTRA_ASK, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
            true
        } catch (e: RuntimeException) {
            warn("The clock could not come back over the lock screen", e)
            false
        }
    }
}
