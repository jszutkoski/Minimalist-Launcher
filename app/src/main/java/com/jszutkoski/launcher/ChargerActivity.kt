package com.jszutkoski.launcher

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlarmManager
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.WindowInsetsController
import android.view.WindowManager
import android.window.OnBackInvokedDispatcher
import kotlin.math.abs

/**
 * The clock for the night on the charger: once the phone locks there, the screen comes on with
 * the bedside clock, the weather, the next alarm and, when anything is waiting, three dots that
 * never say from which app. Only as bright as the light around it calls for, lit for as long
 * as the phone is plugged in, and somewhere new on the screen every minute. Only over the lock screen (or on a phone with no screen lock): tapping goes to
 * Android's own unlock, unlocking by any means closes it, and so does unplugging.
 */
class ChargerActivity : Activity() {
    private val prefs by lazy { Prefs(this) }
    private val keyguard by lazy { getSystemService(KeyguardManager::class.java) }
    private val power by lazy { getSystemService(PowerManager::class.java) }
    private val screenOn get() = power?.isInteractive == true

    private lateinit var view: StandbyView
    private var unlockAsked = false
    private val locked get() = keyguard?.isKeyguardLocked == true
    /** Plugged in at the last reading: unplugging after that closes it. */
    private var plugged = false

    private val notificationsChanged: () -> Unit = { showWaiting() }

    private val sensors by lazy { getSystemService(SensorManager::class.java) }
    private val light by lazy { sensors?.getDefaultSensor(Sensor.TYPE_LIGHT) }

    /** The light around the phone, as the sensor reports a change in it. */
    private val lightListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) = followLight(ChargerLight.brightness(event.values[0]))

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private val handler = Handler(Looper.getMainLooper())
    private val unlockUnanswered = Runnable { closeUnlock() }

    /** A step for the log, with the phone's state at that moment. */
    private fun note(step: String) =
        StandbyLog.note(this, "ChargerActivity $step screenOn=$screenOn locked=$locked asking=$unlockAsked")

    private val screenOnReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Android's unlock went with the screen: the next tap asks again.
            unlockAsked = false
            handler.removeCallbacks(unlockUnanswered)
            view.ignoreTaps(WAKE_TAP_MS)
            view.refreshClock()
            showWaiting()
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = updateBattery(intent)
    }

    /** Each minute, the time; and the alarm, which drops its weekday once it is less than a day away. */
    private val minuteReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            view.refreshClock()
            showAlarm()
        }
    }

    private val alarmReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = showAlarm()
    }

    /** Unlocked with the fingerprint or face: the phone is wanted, so step aside. */
    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The screen comes on as it opens: turnScreenOn in the manifest, which Android reads in
        // time, and only for an opening, so pressing power over it later just turns it off.
        setShowWhenLocked(true)
        edgeToEdge()
        view = StandbyView(this, ::leave).apply { wanders = true }
        setContentView(view)
        Notifications.addListener(notificationsChanged)

        // Bars stay, with dark icons on black: nearly invisible.
        val dark = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        window.insetsController?.setSystemBarsAppearance(dark, dark)
        // Dark until the light sensor says otherwise: a glow on the bedside table. With no
        // sensor to tell, the phone's own brightness.
        val start = if (light != null) ChargerLight.DARK else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = window.attributes.apply { screenBrightness = start }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) { leave() }
        }

        // Sticky: registering returns the current charge right away.
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let(::updateBattery)
        registerReceiver(unlockReceiver, IntentFilter(Intent.ACTION_USER_PRESENT))
        registerReceiver(screenOnReceiver, IntentFilter(Intent.ACTION_SCREEN_ON))
        registerReceiver(minuteReceiver, IntentFilter(Intent.ACTION_TIME_TICK))
        registerReceiver(alarmReceiver, IntentFilter(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED))
    }

    override fun onStart() {
        super.onStart()
        light?.let { sensors?.registerListener(lightListener, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    override fun onResume() {
        super.onResume()
        note("resumed")
        // Never lit over an unlocked phone, where anyone could pull down the notifications.
        if (!canShow(this)) {
            finish()
            return
        }
        showing = true
        view.setScreen(prefs.standbyScreen)
        view.refreshClock()
        view.ignoreTaps(WAKE_TAP_MS)
        view.show()
        showWaiting()
        showWeather()
        showAlarm()
    }

    /**
     * The window brightness for the light now. Small changes are let go, so a shadow passing
     * doesn't keep nudging it; Android eases the screen from one level to the next.
     */
    private fun followLight(level: Float) {
        val now = window.attributes.screenBrightness
        if (now >= 0f && abs(now - level) < LIGHT_STEP && level != ChargerLight.DARK) return
        if (now == level) return
        window.attributes = window.attributes.apply { screenBrightness = level }
    }

    /** The next alarm under the weather, so the night's end is there at a glance. */
    private fun showAlarm() = view.showAlarm(nextAlarmTime(this)?.let { alarmLabel(this, it) })

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        note("new intent")
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        note("focus=$hasFocus")
    }

    override fun onPause() {
        super.onPause()
        note("paused")
    }

    override fun onStop() {
        super.onStop()
        note("stopped")
        // Covered with the screen on (a call, an alarm): the next screen off brings it back.
        // Stopped by the screen going off, it is still what the wake will show.
        if (screenOn) showing = false
        view.hold()
        // Nobody sees it: no need to follow the light.
        sensors?.unregisterListener(lightListener)
    }

    override fun onDestroy() {
        note("destroyed")
        handler.removeCallbacksAndMessages(null)
        Notifications.removeListener(notificationsChanged)
        showing = false
        unregisterReceiver(batteryReceiver)
        unregisterReceiver(unlockReceiver)
        unregisterReceiver(screenOnReceiver)
        unregisterReceiver(minuteReceiver)
        unregisterReceiver(alarmReceiver)
        super.onDestroy()
    }

    // Android 11 and 12 only: from 13 on, back reaches the OnBackInvokedCallback instead.
    @SuppressLint("GestureBackNavigation")
    @Deprecated("Only reached below Android 13; newer versions use OnBackInvokedCallback.")
    override fun onBackPressed() = leave()

    /** A tap or back: Android's own unlock, and the phone once it is given. */
    private fun leave() {
        val keyguard = keyguard
        if (keyguard == null || !keyguard.isKeyguardLocked) {
            finish()
            return
        }
        if (unlockAsked) return
        unlockAsked = true
        note("unlock asked")
        handler.postDelayed(unlockUnanswered, UNLOCK_WAIT_MS)
        keyguard.requestDismissKeyguard(
            this,
            object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() {
                    handler.removeCallbacks(unlockUnanswered)
                    note("unlocked")
                    finish()
                }

                // Back out of the PIN pad: the clock stays, and the next tap asks again.
                override fun onDismissCancelled() {
                    handler.removeCallbacks(unlockUnanswered)
                    unlockAsked = false
                    note("unlock cancelled")
                }

                override fun onDismissError() {
                    handler.removeCallbacks(unlockUnanswered)
                    unlockAsked = false
                    note("unlock error")
                }
            },
        )
    }

    /**
     * Android's unlock left unanswered: no app can close it, but Android does as the screen goes
     * off. So the screen goes dark (locking again, through Minimalist's accessibility service)
     * and the clock lights it again a moment later, as after any lock on the charger.
     */
    private fun closeUnlock() {
        if (!unlockAsked || isFinishing || !screenOn || !locked || LockService.inCall(this)) return
        note("unlock unanswered")
        unlockAsked = false
        if (LockService.lock()) startWhenLocked(this, again = true)
    }

    /** Three dots when anything is waiting, whatever it is and from whichever app. */
    private fun showWaiting() {
        val apps = StandbyActivity.waitingApps(this, prefs)
        val dots = if (apps.isEmpty()) emptyList() else listOf(AppGlyphs.Waiting(R.drawable.ic_more, apps))
        view.showWaiting(dots) { getString(R.string.charger_waiting) }
    }

    /** The last reading under the clock, fetched again when it is over half an hour old. */
    private fun showWeather() {
        if (!prefs.weather(ClockScreen.STANDBY) || !Weather.hasAccess(this)) {
            view.showWeather(null)
            return
        }
        view.showWeather(Weather.cached(this))
        if (Weather.isFresh(this)) return
        Weather.refresh(this) { now ->
            if (now != null) runOnUiThread { if (!isDestroyed) view.showWeather(now) }
        }
    }

    private fun updateBattery(intent: Intent) {
        val battery = BatteryState.from(intent) ?: return
        view.updateBattery(battery)
        // Charging or holding at a limit: either way, on the charger.
        if (battery.plugged) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            // Taken off the charger: the phone is picked up, so Android's lock screen is next.
            if (plugged) finish()
        }
        plugged = battery.plugged
    }

    companion object {
        /** The least change in brightness worth making as the light changes. */
        private const val LIGHT_STEP = 0.04f

        /** After the screen comes on, how long a touch is taken for the end of the wake gesture. */
        private const val WAKE_TAP_MS = 700L

        /** How long Android's unlock stays up over the clock unanswered. */
        private const val UNLOCK_WAIT_MS = 10_000L

        /** In front, or was when the screen went off. */
        @Volatile
        var showing = false
            private set

        /** From the screen going off, or the phone locking, to the clock lighting it again. */
        private const val DELAY_MS = 3_000L

        /** On its way: the screen just went off, and the clock follows once the phone is locked. */
        private var pending = false

        private fun plugged(context: Context): Boolean =
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let(BatteryState::from)?.plugged == true

        /** Over the lock screen, or on a phone with no screen lock: nothing there to guard. */
        fun canShow(context: Context): Boolean {
            val keyguard = context.getSystemService(KeyguardManager::class.java) ?: return true
            return keyguard.isKeyguardLocked || !keyguard.isDeviceSecure
        }

        /** Turned on in settings, and on the charger now. */
        fun wanted(context: Context): Boolean = Prefs(context).standbyWhenCharging && plugged(context)

        /**
         * At screen off on the charger (Minimalist's accessibility service hears it): the phone
         * is locked at once if Android would wait a few seconds first, so the screen that comes
         * back on a few seconds later is over the lock screen, with this clock. Nothing to do if it is
         * already there: pressing power over it just turns the screen off; unless it is the clock
         * itself that turned the screen off ([again]), and lights it again.
         */
        fun startWhenLocked(context: Context, again: Boolean = false) {
            if ((showing && !again) || pending) return
            val keyguard = context.getSystemService(KeyguardManager::class.java)
            if (keyguard != null && keyguard.isDeviceSecure && !keyguard.isKeyguardLocked && !LockService.lock()) return
            pending = true
            val app = context.applicationContext
            Handler(Looper.getMainLooper()).postDelayed({
                pending = false
                if ((showing && !again) || LockService.inCall(app) || !wanted(app) || !canShow(app)) return@postDelayed
                try {
                    app.startActivity(
                        Intent(app, ChargerActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    )
                } catch (e: RuntimeException) {
                    warn("The charger's clock could not open", e)
                }
            }, DELAY_MS)
        }

        /** Plugged in while locked: the clock for the night, without waiting for the screen to go off. */
        fun onPlugged(context: Context) {
            val keyguard = context.getSystemService(KeyguardManager::class.java) ?: return
            if (keyguard.isKeyguardLocked && Prefs(context).standbyWhenCharging && !LockService.inCall(context)) {
                startWhenLocked(context)
            }
        }
    }
}
