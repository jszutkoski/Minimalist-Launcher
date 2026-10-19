package com.jszutkoski.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

/** When a paused player may still show on the home screen. */
class PlaybackTest {
    @Test
    fun aPlayerNeverHeardPlayingDoesNotShowPaused() {
        Playback.seen("org.example.never", playing = false, now = 1_000L)
        assertEquals(0L, Playback.pausedFor("org.example.never", now = 1_000L))
    }

    @Test
    fun aPausedTrackShowsForHalfAnHourAfterItStopped() {
        Playback.seen("org.example.music", playing = true, now = 1_000L)
        // Played for an hour, then paused.
        val paused = 1_000L + 60 * 60 * 1000L
        Playback.seen("org.example.music", playing = false, now = paused)
        assertEquals(Playback.PAUSED_MS, Playback.pausedFor("org.example.music", now = paused))
        assertEquals(1_000L, Playback.pausedFor("org.example.music", now = paused + Playback.PAUSED_MS - 1_000L))
        assertEquals(0L, Playback.pausedFor("org.example.music", now = paused + Playback.PAUSED_MS))
        // Still paused later on: the time it stopped stays what counts.
        Playback.seen("org.example.music", playing = false, now = paused + Playback.PAUSED_MS)
        assertEquals(0L, Playback.pausedFor("org.example.music", now = paused + Playback.PAUSED_MS))
    }
}
