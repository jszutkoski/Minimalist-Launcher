package com.jszutkoski.launcher

import android.app.Activity
import android.content.ComponentName
import android.os.Looper
import android.os.Process
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.robolectric.Shadows.shadowOf

/** This view and everything under it, depth first. */
fun View.views(): List<View> =
    if (this is ViewGroup) listOf(this) + (0 until childCount).flatMap { getChildAt(it).views() } else listOf(this)

fun View.textViews() = views().filterIsInstance<TextView>()

/** The activity's own views, without the window's decor. */
fun Activity.content(): View = findViewById<ViewGroup>(android.R.id.content)

/** Runs everything posted to the main thread, and anything due within [millis]. */
fun idle(millis: Long = 0) {
    val looper = shadowOf(Looper.getMainLooper())
    if (millis > 0) looper.idleFor(java.time.Duration.ofMillis(millis)) else looper.idle()
}

fun testApp(label: String, isSystem: Boolean = false) = App(
    key = "pkg.$label/.Main#0",
    label = label,
    originalLabel = label,
    component = ComponentName("pkg.$label", "pkg.$label.Main"),
    user = Process.myUserHandle(),
    isSystem = isSystem,
)
