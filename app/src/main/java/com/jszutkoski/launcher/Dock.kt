package com.jszutkoski.launcher

import android.app.Activity
import android.content.pm.ShortcutInfo
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.Process
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space

/**
 * The row of line icons along the bottom. Each slot has a default; a long-press
 * swaps in any other app, drawn as its own glyph when there is one, otherwise as
 * its initial in a circle.
 */
class Dock(
    private val activity: Activity,
    private val prefs: Prefs,
    private val repo: AppRepository,
    private val launch: (App, View) -> Unit,
    private val openShortcut: (App, ShortcutInfo) -> Unit,
) {
    private class Slot(val label: String, val packages: List<String>, val glyph: Int) {
        val isCamera get() = packages.isEmpty()
    }

    private val slots = listOf(
        Slot("WhatsApp", Packages.WHATSAPP, R.drawable.ic_chat),
        Slot("Waze", listOf("com.waze"), R.drawable.ic_waze),
        Slot("Google Photos", listOf("com.google.android.apps.photos"), R.drawable.ic_photos),
        Slot("Spotify", listOf("com.spotify.music"), R.drawable.ic_spotify),
        Slot(activity.getString(R.string.dock_camera), emptyList(), R.drawable.ic_camera),
    )

    /** Padding around each glyph; the dock is pulled out by this much so glyphs line up with the text. */
    val iconInset = activity.dp(14)

    private val icons = slots.map { icon() }
    private val dots = icons.map { icon -> Dot(activity, iconInset).also {
        it.shown = false
        icon.foreground = it
    } }
    private val slotApps = arrayOfNulls<App>(slots.size)

    /** Icons spread evenly, the outer two in the corners. */
    val view = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        icons.forEachIndexed { index, icon ->
            if (index > 0) addView(Space(context), LinearLayout.LayoutParams(0, 0, 1f))
            val size = activity.dp(ICON_DP) + 2 * iconInset
            addView(icon, LinearLayout.LayoutParams(size, size))
        }
    }

    fun render() {
        slots.forEachIndexed { index, slot ->
            val icon = icons[index]
            val custom = prefs.dockApp(index)?.let(repo::find)
            val app = custom ?: slot.packages.firstNotNullOfOrNull(::installed)
            slotApps[index] = app
            when {
                custom != null -> {
                    val glyph = GLYPHS[custom.component.packageName]
                    if (glyph != null) icon.setImageResource(glyph) else icon.setImageDrawable(InitialGlyph(activity, custom.label))
                    icon.contentDescription = custom.label
                }
                else -> {
                    icon.setImageResource(slot.glyph)
                    icon.contentDescription = slot.label
                }
            }
            // A missing app leaves an empty slot, so the other icons never shift.
            icon.visibility = if (app != null || (custom == null && slot.isCamera)) View.VISIBLE else View.INVISIBLE
            icon.setOnClickListener { view ->
                when {
                    app != null -> launch(app, view)
                    slot.isCamera -> SystemActions.openCamera(activity)
                }
            }
            icon.setOnLongClickListener {
                showOptions(index, slot, app, custom != null)
                true
            }
        }
    }

    private fun installed(pkg: String): App? =
        repo.apps.filter { it.component.packageName == pkg }.minByOrNull { if (it.user == Process.myUserHandle()) 0 else 1 }

    /** A dot at the glyph's corner for slots whose app has notifications waiting. */
    fun showDots(enabled: Boolean) {
        dots.forEachIndexed { index, dot ->
            dot.shown = enabled && slotApps[index]?.let { Notifications.has(it.component.packageName, it.user) } == true
        }
    }

    private fun showOptions(index: Int, slot: Slot, app: App?, customised: Boolean) {
        Sheet(activity, app?.label ?: slot.label).apply {
            if (app != null) addShortcuts(activity, app) { openShortcut(app, it) }
            option(activity.getString(R.string.dock_change_app)) { pickApp(index) }
            if (customised) option(activity.getString(R.string.dock_back_to, slot.label)) {
                prefs.setDockApp(index, null)
                render()
            }
            if (app != null) option(activity.getString(R.string.menu_app_info)) { SystemActions.openAppDetails(activity, app) }
            show()
        }
    }

    private fun pickApp(index: Int) {
        val sheet = Sheet(activity, activity.getString(R.string.choose_app))
        sheet.appList(repo.visibleApps()) { app ->
            prefs.setDockApp(index, app.key)
            sheet.dialog.dismiss()
            render()
        }
        sheet.show()
    }

    private fun icon() = ImageView(activity).apply {
        imageTintList = pressable(Palette.TEXT)
        isClickable = true
        setPadding(iconInset, iconInset, iconInset, iconInset)
    }

    private companion object {
        /** Glyphs are drawn at 26dp and scaled up crisply to this. */
        const val ICON_DP = 30

        /** Apps that have a hand-drawn glyph, whichever slot they are put in. */
        val GLYPHS = mapOf(
            "com.whatsapp" to R.drawable.ic_chat,
            "com.whatsapp.w4b" to R.drawable.ic_chat,
            "com.waze" to R.drawable.ic_waze,
            "com.google.android.apps.photos" to R.drawable.ic_photos,
            "com.spotify.music" to R.drawable.ic_spotify,
        )
    }
}

/** Fallback glyph: the app's initial inside a thin circle, matching the hand-drawn icons. */
private class InitialGlyph(activity: Activity, label: String) : Drawable() {
    private val size = activity.dp(26)
    private val letter = label.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = size * 1.5f / 24f
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = weight(400)
        textAlign = Paint.Align.CENTER
        textSize = size * 0.42f
    }
    private var tint: ColorStateList? = null

    override fun draw(canvas: Canvas) {
        val color = tint?.getColorForState(state, Palette.TEXT) ?: Palette.TEXT
        ring.color = color
        text.color = color
        val cx = bounds.exactCenterX()
        val cy = bounds.exactCenterY()
        canvas.drawCircle(cx, cy, bounds.width() * 8.5f / 24f, ring)
        canvas.drawText(letter, cx, cy - (text.descent() + text.ascent()) / 2f, text)
    }

    override fun setTintList(tint: ColorStateList?) {
        this.tint = tint
        invalidateSelf()
    }

    override fun isStateful() = true

    override fun onStateChange(state: IntArray): Boolean {
        invalidateSelf()
        return true
    }

    override fun getIntrinsicWidth() = size

    override fun getIntrinsicHeight() = size

    override fun setAlpha(alpha: Int) = Unit

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
