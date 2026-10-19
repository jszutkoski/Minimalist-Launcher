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
 * A clock shown on the lock screen, and the home swipe over it. Android sends that swipe to the
 * home screen, which it then covers with its own lock screen; instead the clock comes straight
 * back, asking for the fingerprint or PIN, as the swipe up on the lock screen itself would.
 */
object OverLock {
    /** Opened again to ask for the unlock as soon as it shows. */
    const val EXTRA_ASK = "ask_unlock"

    /** For another home app than Minimalist, whose home screen can't bring the clock back itself. */
    private const val BACK_MS = 500L

    /** After the unlock is backed out of, the moment for the clock to have its window again. */
    private const val SETTLE_MS = 800L

    /** After Android's unlock goes away, how long the clock being covered still counts as the swipe. */
    private const val AFTER_ASKING_MS = 5_000L

    /** Between the clock going out of sight and home coming, either way round, for one swipe. */
    private const val PAIR_MS = 1_500L

    private val handler = Handler(Looper.getMainLooper())
    private var pending: Class<out Activity>? = null
    private var takenAt = 0L

    /** The clock that last went out of sight over the lock screen, and when. */
    private var coveredScreen: Class<out Activity>? = null
    private var coveredAt = 0L

    /** When home last came to Minimalist's home screen with the phone locked. */
    private var homeAt = 0L

    /**
     * [screen] went out of sight with the screen on and the phone still locked. If home comes
     * with it, that was the home swipe, and the clock comes back; anything else (a call, an
     * alarm, the camera) is left alone. With Android's unlock up ([asking]) or only just gone
     * ([answeredAt]) it is the swipe anyway, whichever home app there is.
     */
    fun covered(screen: Activity, asking: Boolean, answeredAt: Long) {
        val now = SystemClock.elapsedRealtime()
        val recent = answeredAt != 0L && now - answeredAt < AFTER_ASKING_MS
        StandbyLog.note(screen, "${screen.javaClass.simpleName} covered asking=$asking recent=$recent")
        coveredScreen = screen.javaClass
        coveredAt = now
        when {
            now - homeAt < PAIR_MS -> {
                pending = screen.javaClass
                takeBack(screen.applicationContext)
            }
            asking || recent -> left(screen)
        }
    }

    /**
     * Home came to Minimalist's home screen with the phone locked: a clock that has just gone
     * out of sight comes back. True if one did.
     */
    fun home(context: Context): Boolean {
        val now = SystemClock.elapsedRealtime()
        homeAt = now
        if (pending == null && now - coveredAt < PAIR_MS) pending = coveredScreen
        coveredScreen = null
        return takeBack(context)
    }

    /** Home or recents while [screen] had Android's unlock up. */
    fun left(screen: Activity) {
        StandbyLog.note(screen, "${screen.javaClass.simpleName} left over the unlock")
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
            StandbyLog.note(app, "${kind.simpleName} unlock cancelled focus=${screen.hasWindowFocus()} finishing=${screen.isFinishing}")
            if (screen.isFinishing || screen.isDestroyed || screen.hasWindowFocus()) return@postDelayed
            // Already brought back by home or recents.
            if (SystemClock.elapsedRealtime() - takenAt < SETTLE_MS * 2) return@postDelayed
            pending = kind
            takeBack(app)
        }, SETTLE_MS)
    }

    /** Nothing covered, pending or just come: for tests, each starting afresh. */
    internal fun forget() {
        handler.removeCallbacksAndMessages(null)
        pending = null
        coveredScreen = null
        coveredAt = 0L
        homeAt = 0L
        takenAt = 0L
    }

    /** The clock left by home, put back over the lock screen, asking; true if there was one. */
    fun takeBack(context: Context): Boolean {
        val screen = pending ?: return false
        pending = null
        coveredScreen = null
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        val power = context.getSystemService(PowerManager::class.java)
        val locked = keyguard?.isKeyguardLocked == true
        val on = power?.isInteractive == true
        StandbyLog.note(context, "take back ${screen.simpleName} locked=$locked screenOn=$on")
        // Unlocked meanwhile, the screen gone off, or a call: nothing to come back to.
        if (!locked || !on || LockService.inCall(context)) return false
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
