package com.jszutkoski.launcher

import android.app.Activity
import android.app.ActivityOptions
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.window.OnBackInvokedDispatcher
import java.text.NumberFormat

/**
 * The bedside clock as its own screen, allowed to show over the lock screen. When the phone
 * locks while it is open, pressing power brings it back instead of the lock screen. It shows
 * only the time, date and battery: tapping asks for the PIN or fingerprint before anything
 * else, and unlocking by any means closes it.
 */
open class StandbyActivity : Activity() {
    private lateinit var view: StandbyView
    private var charging = false
    private var closing = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = updateBattery(intent)
    }

    /** Unlocked with the fingerprint or face while showing: the phone is wanted, so step aside. */
    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = close("unlocked")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs(this).logStandby("standby opened (${if (this is StandbyOverAppsActivity) "over apps" else "from home"})")
        setShowWhenLocked(true)
        edgeToEdge()
        view = StandbyView(this, ::leave)
        setContentView(view)

        // No brightness override: the screen would first light up at the system level and
        // then visibly ramp down to it. The half-strength text on black keeps it dim enough.
        window.insetsController?.let {
            it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            it.hide(WindowInsets.Type.systemBars())
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) { close("back") }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, R.anim.hold, R.anim.fade_out)
        }

        // Sticky: registering returns the current charge right away.
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let(::updateBattery)
        registerReceiver(unlockReceiver, IntentFilter(Intent.ACTION_USER_PRESENT))
    }

    override fun onResume() {
        super.onResume()
        inFront = true
        view.show()
    }

    override fun onDestroy() {
        inFront = false
        if (!closing) Prefs(this).logStandby("standby closed by Android")
        unregisterReceiver(batteryReceiver)
        unregisterReceiver(unlockReceiver)
        super.onDestroy()
    }

    /** Home pressed while unlocked: done, rather than lingering in a background task. */
    /**
     * Android calls this when the user leaves for home, but also right after standby is put
     * on top at screen off. The copy over other apps must survive that, or it closes before
     * the screen ever wakes; it simply waits behind until the next screen off brings it back.
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        inFront = false
        if (this !is StandbyOverAppsActivity) close("left for home")
    }

    /** Every way out goes through here, so the diagnostic log says why standby closed. */
    private fun close(reason: String) {
        Prefs(this).logStandby("standby closed: $reason")
        closing = true
        finish()
    }

    @Deprecated("Only reached below Android 13; newer versions use OnBackInvokedCallback.")
    override fun onBackPressed() = close("back")

    /** A tap: straight home when unlocked, otherwise through the PIN or fingerprint first. */
    private fun leave() {
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard?.isKeyguardLocked == true) {
            keyguard.requestDismissKeyguard(
                this,
                object : KeyguardManager.KeyguardDismissCallback() {
                    override fun onDismissSucceeded() = close("tap, then unlock")
                },
            )
        } else {
            close("tap")
        }
    }

    private fun updateBattery(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (level < 0 || scale <= 0) return
        val fraction = level / scale.toFloat()
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val low = !charging && fraction <= LOW_BATTERY
        view.updateBattery(fraction, charging, low, NumberFormat.getPercentInstance().format(fraction.toDouble()))
        // Awake all night on the charger; on battery the screen times out as usual.
        if (charging) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    companion object {
        /** Standby screens alive right now, of either kind. */
        /** A standby screen is showing, or was when the screen went off. */
        var inFront = false
            private set

        /**
         * From the screen-off receiver: in a task of its own above whatever app was open, so
         * that app returns after unlocking. Nothing to do if standby is already up.
         */
        fun startOnTop(context: Context) {
            try {
                context.startActivity(
                    Intent(context, StandbyOverAppsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            } catch (e: RuntimeException) {
                Prefs(context).logStandby("could not open: ${e.javaClass.simpleName}")
            }
        }

        /**
         * From the home screen: in the home task, so it fades in. A separate task would open
         * with the system's sideways app animation instead.
         */
        fun start(activity: Activity) {
            activity.startActivity(
                Intent(activity, StandbyActivity::class.java),
                ActivityOptions.makeCustomAnimation(activity, R.anim.fade_in, R.anim.hold).toBundle(),
            )
        }
    }
}

/** The same bedside clock in its own task, for when it covers another app at screen off. */
class StandbyOverAppsActivity : StandbyActivity()
