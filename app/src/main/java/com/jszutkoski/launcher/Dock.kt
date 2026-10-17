package com.jszutkoski.launcher

import android.app.Activity
import android.content.Context
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
 * The bottom icons as a list kept beside its length, so that fewer icons and then more again
 * brings the same ones back. Entries are app keys or [Prefs.DOCK_CAMERA]. Plain lists in and
 * out, so the rules can be checked without a phone.
 */
object DockList {
    /** [stored] without repeats, then [suggestions] it lacks, up to [max]. */
    fun fill(stored: List<String>, suggestions: List<String>, max: Int = Prefs.MAX_DOCK): List<String> {
        val list = stored.distinct().take(max).toMutableList()
        for (entry in suggestions) {
            if (list.size >= max) break
            if (entry !in list) list += entry
        }
        return list
    }

    /** [entry] at [index], swapping places with it if it is somewhere else already. */
    fun replace(list: List<String>, index: Int, entry: String): List<String> {
        val result = list.toMutableList()
        val at = result.indexOf(entry)
        if (at >= 0) result[at] = result[index]
        result[index] = entry
        return result
    }

    /** [entry] added after the [count] shown, which become one more. */
    fun add(list: List<String>, count: Int, entry: String): Pair<List<String>, Int> {
        val shown = list.take(count)
        if (entry in shown) return list to count
        val rest = list.drop(count) - entry
        return (shown + entry + rest) to count + 1
    }

    /** The shown entry at [index] gone, and one fewer shown; the hidden ones keep their order. */
    fun remove(list: List<String>, count: Int, index: Int): Pair<List<String>, Int> =
        list.filterIndexed { i, _ -> i != index } to count - 1

    /** The entry at [index] swapped with its neighbour [by] away. */
    fun move(list: List<String>, index: Int, by: Int): List<String> {
        val to = index + by
        if (index !in list.indices || to !in list.indices) return list
        return list.toMutableList().apply { this[index] = this[to].also { this[to] = this[index] } }
    }
}

/**
 * Which apps are along the bottom, and changing them, from home and from settings. Until first
 * changed, the defaults stand, with any icon swapped in before the icons became a list.
 */
class DockApps(private val context: Context, private val prefs: Prefs, private val repo: AppRepository) {
    /** All the remembered entries, those shown first. */
    fun all(): List<String> = DockList.fill(prefs.dockApps ?: earlier(), suggestions())

    fun shown(): List<String> = all().take(prefs.dockCount)

    fun contains(app: App) = app.key in shown()

    val isFull get() = shown().size >= Prefs.MAX_DOCK

    fun label(entry: String): String = when (entry) {
        Prefs.DOCK_CAMERA -> context.getString(R.string.dock_camera)
        else -> repo.find(entry)?.label ?: context.getString(R.string.uninstalled_app)
    }

    /** Its glyph, or null for one drawn from its initial. */
    fun glyph(entry: String, glyphs: Map<String, Int>): Int? = when (entry) {
        Prefs.DOCK_CAMERA -> R.drawable.ic_camera
        else -> repo.find(entry)?.let { glyphs[it.component.packageName] }
    }

    fun setCount(count: Int) = save(all(), count)

    fun replace(index: Int, entry: String) = save(DockList.replace(all(), index, entry), prefs.dockCount)

    fun add(entry: String) = DockList.add(all(), shown().size, entry).let { (list, count) -> save(list, count) }

    fun remove(index: Int) = DockList.remove(all(), shown().size, index).let { (list, count) -> save(list, count) }

    fun move(index: Int, by: Int) = save(DockList.move(all(), index, by), prefs.dockCount)

    private fun save(list: List<String>, count: Int) {
        // Before the apps are known the suggestions are missing: nothing is written from that.
        if (!repo.loaded) return
        prefs.dockApps = list
        prefs.dockCount = count
    }

    /** The five first icons: WhatsApp, Waze, Google Photos, Spotify and the camera, or what replaced them. */
    private fun earlier(): List<String> = DEFAULTS.mapIndexedNotNull { index, packages ->
        prefs.oldDockApp(index)?.takeIf { repo.find(it) != null }
            ?: if (packages.isEmpty()) Prefs.DOCK_CAMERA else packages.firstNotNullOfOrNull(::installed)
    }

    /** To fill new places: the defaults, then the phone's own dialer, messages, browser and a few well known apps. */
    private fun suggestions(): List<String> {
        val roles = AppGlyphs.lookup(context)
        fun byGlyph(drawable: Int) = repo.apps.firstOrNull { roles[it.component.packageName] == drawable && it.user == Process.myUserHandle() }?.key
        return DEFAULTS.mapNotNull { packages ->
            if (packages.isEmpty()) Prefs.DOCK_CAMERA else packages.firstNotNullOfOrNull(::installed)
        } + listOfNotNull(
            byGlyph(R.drawable.ic_phone),
            byGlyph(R.drawable.ic_message),
            byGlyph(R.drawable.ic_chrome) ?: byGlyph(R.drawable.ic_browser),
            byGlyph(R.drawable.ic_maps),
            byGlyph(R.drawable.ic_instagram),
            byGlyph(R.drawable.ic_youtube),
        )
    }

    private fun installed(pkg: String): String? = repo.apps
        .filter { it.component.packageName == pkg }
        .minByOrNull { if (it.user == Process.myUserHandle()) 0 else 1 }
        ?.key

    /** A sheet of the camera and every visible app; [onPick] gets the chosen entry. */
    fun choose(activity: Activity, title: String, onPick: (String) -> Unit) {
        val sheet = Sheet(activity, title)
        sheet.option(activity.getString(R.string.dock_camera)) { onPick(Prefs.DOCK_CAMERA) }
        sheet.divider()
        sheet.appList(repo.visibleApps()) { app ->
            sheet.dialog.dismiss()
            onPick(app.key)
        }
        sheet.show()
    }

    /**
     * Puts [app] along the bottom: after the others while there is room, otherwise in place of
     * the one chosen from a sheet. [done] runs once it is there.
     */
    fun offer(activity: Activity, app: App, done: () -> Unit) {
        if (!isFull) {
            add(app.key)
            done()
            return
        }
        Sheet(activity, activity.getString(R.string.dock_replace_title, app.label)).apply {
            shown().forEachIndexed { index, entry ->
                option(label(entry)) {
                    replace(index, app.key)
                    done()
                }
            }
            show()
        }
    }

    private companion object {
        /** The five first icons; an empty list is the camera. */
        val DEFAULTS = listOf(
            Packages.WHATSAPP,
            listOf("com.waze"),
            listOf("com.google.android.apps.photos"),
            listOf("com.spotify.music"),
            emptyList(),
        )
    }
}

/**
 * The row of line icons along the bottom, as many as chosen. Each is drawn as its own glyph
 * when there is one, otherwise as its initial in a circle; a long-press changes, moves or
 * removes it.
 */
class Dock(
    private val activity: Activity,
    private val prefs: Prefs,
    private val repo: AppRepository,
    private val launch: (App, View) -> Unit,
    private val openShortcut: (App, ShortcutInfo) -> Unit,
) {
    val apps = DockApps(activity, prefs, repo)

    /** Padding around each glyph; the dock is pulled out by this much so glyphs line up with the text. */
    val iconInset = activity.dp(INSET_DP)

    private class Icon(val view: ImageView, val dot: Dot, val app: App?)

    private var icons: List<Icon> = emptyList()
    private var dotsEnabled = false

    /** Icons spread evenly, the outer two in the corners; a lone one in the middle. */
    val view = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
    }

    fun render() {
        val entries = apps.shown()
        val glyphs = AppGlyphs.lookup(activity)
        view.removeAllViews()
        view.visibility = if (entries.isEmpty()) View.GONE else View.VISIBLE
        icons = entries.mapIndexed { index, entry ->
            val app = repo.find(entry)
            val camera = entry == Prefs.DOCK_CAMERA
            val label = apps.label(entry)
            val icon = icon()
            val glyph = apps.glyph(entry, glyphs)
            if (glyph != null) icon.setImageResource(glyph) else icon.setImageDrawable(InitialGlyph(activity, label))
            icon.contentDescription = label
            // A missing app leaves an empty place, so the other icons never shift.
            icon.visibility = if (app != null || camera) View.VISIBLE else View.INVISIBLE
            icon.setOnClickListener { view ->
                when {
                    app != null -> launch(app, view)
                    camera -> SystemActions.openCamera(activity)
                }
            }
            icon.setOnLongClickListener {
                showOptions(index, entries.size, label, app)
                true
            }
            val dot = Dot(activity, iconInset).also {
                it.shown = false
                icon.foreground = it
            }
            if (index > 0) view.addView(Space(activity), LinearLayout.LayoutParams(0, 0, 1f))
            val size = activity.dp(ICON_DP) + 2 * iconInset
            view.addView(icon, LinearLayout.LayoutParams(size, size))
            Icon(icon, dot, app)
        }
        showDots(dotsEnabled)
    }

    /** A dot at the glyph's corner for icons whose app has notifications waiting. */
    fun showDots(enabled: Boolean) {
        dotsEnabled = enabled
        icons.forEach { icon ->
            icon.dot.shown = enabled && icon.app?.let { Notifications.has(it.component.packageName, it.user) } == true
        }
    }

    private fun showOptions(index: Int, count: Int, label: String, app: App?) {
        Sheet(activity, label).apply {
            if (app != null) addShortcuts(activity, app) { openShortcut(app, it) }
            option(activity.getString(R.string.dock_change_app)) {
                apps.choose(activity, activity.getString(R.string.choose_app)) { entry ->
                    apps.replace(index, entry)
                    render()
                }
            }
            if (index > 0) option(activity.getString(R.string.dock_move_left)) { change { apps.move(index, -1) } }
            if (index < count - 1) option(activity.getString(R.string.dock_move_right)) { change { apps.move(index, 1) } }
            option(activity.getString(R.string.dock_remove)) { change { apps.remove(index) } }
            if (app != null) option(activity.getString(R.string.menu_app_info)) { SystemActions.openAppDetails(activity, app) }
            show()
        }
    }

    private fun change(edit: () -> Unit) {
        edit()
        render()
    }

    private fun icon() = ImageView(activity).apply {
        imageTintList = pressable(Palette.TEXT)
        isClickable = true
        setPadding(iconInset, iconInset, iconInset, iconInset)
    }

    companion object {
        /** Glyphs are drawn at 26dp and scaled up crisply to this. */
        const val ICON_DP = 30
        const val INSET_DP = 14
    }
}

/** Fallback glyph: the app's initial inside a thin circle, matching the hand-drawn icons. */
class InitialGlyph(context: Context, label: String) : Drawable() {
    private val size = context.dp(26)
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
