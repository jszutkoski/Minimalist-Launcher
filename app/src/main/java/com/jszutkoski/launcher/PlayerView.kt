package com.jszutkoski.launcher

import android.content.Context
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout

/**
 * Title, artist and three controls. Shown only while something plays; after a pause it
 * lingers a few seconds, so playback can be resumed, then fades away.
 */
class PlayerView(context: Context, private val onOpen: (packageName: String) -> Unit) : LinearLayout(context) {
    private val title = context.text("", 17f, Palette.TEXT, 400)
    private val artist = context.text("", 15f, Palette.SECONDARY, 400)
    private val controls = LinearLayout(context)
    private val playPause: ImageView
    private var track: NowPlaying.Track? = null
    private val hide = Runnable { fade(visible = false) }

    init {
        orientation = VERTICAL
        visibility = GONE
        alpha = 0f

        val full = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        title.setOnClickListener { track?.let { onOpen(it.packageName) } }
        artist.setOnClickListener { track?.let { onOpen(it.packageName) } }
        artist.setPadding(0, dp(2), 0, 0)
        addView(title, full)
        addView(artist, LayoutParams(full))

        controls.orientation = HORIZONTAL
        val previous = button(R.drawable.ic_previous, "Previous") { track?.controls?.skipToPrevious() }
        playPause = button(R.drawable.ic_play, "Play") {
            track?.let { if (it.playing) it.controls.pause() else it.controls.play() }
        }
        val next = button(R.drawable.ic_next, "Next") { track?.controls?.skipToNext() }
        listOf(previous, playPause, next).forEachIndexed { index, button ->
            controls.addView(
                button,
                LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    if (index > 0) leftMargin = dp(BUTTON_GAP_DP)
                },
            )
        }
        addView(
            controls,
            LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
                // Pulled out by the button padding, so the outer glyphs line up with the text.
                leftMargin = -dp(BUTTON_PADDING_DP)
                rightMargin = -dp(BUTTON_PADDING_DP)
            },
        )
    }

    fun setAlignment(gravity: Int) {
        title.gravity = gravity
        artist.gravity = gravity
        (controls.layoutParams as LayoutParams).gravity = gravity
        requestLayout()
    }

    fun setTextScale(scale: Float) {
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f * scale)
        artist.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f * scale)
    }

    fun show(track: NowPlaying.Track?) {
        this.track = track
        removeCallbacks(hide)
        if (track == null) {
            fade(visible = false)
            return
        }
        title.text = track.title
        artist.text = track.artist.orEmpty()
        artist.visibility = if (track.artist != null) VISIBLE else GONE
        playPause.setImageResource(if (track.playing) R.drawable.ic_pause else R.drawable.ic_play)
        playPause.contentDescription = if (track.playing) "Pause" else "Play"
        if (track.playing) fade(visible = true) else if (visibility == VISIBLE) postDelayed(hide, LINGER_MS)
    }

    private fun fade(visible: Boolean) {
        animate().cancel()
        if (visible) {
            if (visibility != VISIBLE) {
                alpha = 0f
                visibility = VISIBLE
            }
            animate().alpha(1f).setDuration(220).start()
        } else if (visibility == VISIBLE) {
            animate().alpha(0f).setDuration(220).withEndAction { visibility = GONE }.start()
        }
    }

    private fun button(drawable: Int, description: String, action: () -> Unit) = ImageView(context).apply {
        setImageResource(drawable)
        imageTintList = pressable(Palette.SECONDARY)
        contentDescription = description
        val inset = dp(BUTTON_PADDING_DP)
        setPadding(inset, inset, inset, inset)
        setOnClickListener { action() }
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(hide)
        super.onDetachedFromWindow()
    }

    private companion object {
        const val BUTTON_PADDING_DP = 10
        const val BUTTON_GAP_DP = 20
        const val LINGER_MS = 4000L
    }
}
