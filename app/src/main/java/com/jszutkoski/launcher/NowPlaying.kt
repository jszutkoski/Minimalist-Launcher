package com.jszutkoski.launcher

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/** Follows whichever app is playing music and reports its title and state. */
class NowPlaying(private val context: Context, private val onChange: (Track?) -> Unit) {
    class Track(
        val title: String,
        val artist: String?,
        val playing: Boolean,
        val packageName: String,
        val controls: MediaController.TransportControls,
    )

    private val sessions = context.getSystemService(MediaSessionManager::class.java)
    private val component = ComponentName(context, MediaListener::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var controller: MediaController? = null
    private var started = false

    private val expire = Runnable { report() }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { pick(it) }

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = report()
        override fun onMetadataChanged(metadata: MediaMetadata?) = report()
        override fun onSessionDestroyed() = pick(activeSessions())
    }

    companion object {
        fun hasAccess(context: Context): Boolean =
            context.getSystemService(NotificationManager::class.java)
                ?.isNotificationListenerAccessGranted(ComponentName(context, MediaListener::class.java)) == true
    }

    fun start() {
        if (started || !hasAccess(context)) {
            if (!started) onChange(null)
            return
        }
        try {
            sessions.addOnActiveSessionsChangedListener(sessionsListener, component, handler)
            started = true
            pick(activeSessions())
        } catch (e: SecurityException) {
            warn("No access to media sessions", e)
            onChange(null)
        }
    }

    fun stop() {
        if (started) sessions.removeOnActiveSessionsChangedListener(sessionsListener)
        started = false
        controller?.unregisterCallback(callback)
        controller = null
        handler.removeCallbacks(expire)
        onChange(null)
    }

    private fun activeSessions(): List<MediaController> =
        try {
            sessions.getActiveSessions(component)
        } catch (_: SecurityException) {
            emptyList()
        }

    /** Prefers a session that is playing; otherwise the most recent one. */
    private fun pick(list: List<MediaController>?) {
        val best = list?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list?.firstOrNull()
        if (best?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(callback)
            controller = best
            best?.registerCallback(callback, handler)
        }
        report()
    }

    private fun report() {
        handler.removeCallbacks(expire)
        val current = controller
        val metadata = current?.metadata
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }
        if (current == null || title == null) {
            onChange(null)
            return
        }
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
        val state = current.playbackState?.state
        // Stopped or never started: the player app is open but has nothing loaded.
        if (state == null || state == PlaybackState.STATE_NONE || state == PlaybackState.STATE_STOPPED ||
            state == PlaybackState.STATE_ERROR
        ) {
            onChange(null)
            return
        }
        val playing = state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING
        Playback.seen(current.packageName, playing)
        // Paused: only while the music is still fresh in mind. A player that keeps a paused
        // session around for hours, or wakes with one on its own, is not "now" playing.
        if (!playing) {
            val left = Playback.pausedFor(current.packageName)
            if (left <= 0) {
                onChange(null)
                return
            }
            handler.postDelayed(expire, left)
        }
        onChange(Track(title, artist?.takeIf { it.isNotBlank() }, playing, current.packageName, current.transportControls))
    }
}

/**
 * When each player was last heard playing, from its notification or the home screen's player,
 * by package. Read and written from the notification listener and the main thread.
 */
object Playback {
    private val lastPlaying = HashMap<String, Long>()
    private val playingNow = HashSet<String>()

    /** How long a paused track stays on the home screen after it stopped playing. */
    const val PAUSED_MS = 30 * 60 * 1000L

    /** [packageName]'s player is [playing] now, or paused after it was. */
    @Synchronized
    fun seen(packageName: String, playing: Boolean, now: Long = SystemClock.elapsedRealtime()) {
        if (playing) {
            playingNow += packageName
            lastPlaying[packageName] = now
        } else if (playingNow.remove(packageName)) {
            // It played until this moment.
            lastPlaying[packageName] = now
        }
    }

    /** How much longer a paused [packageName] may show: zero once it is stale, or if it never played. */
    @Synchronized
    fun pausedFor(packageName: String, now: Long = SystemClock.elapsedRealtime()): Long {
        val last = lastPlaying[packageName] ?: return 0
        return (PAUSED_MS - (now - last)).coerceAtLeast(0)
    }
}
