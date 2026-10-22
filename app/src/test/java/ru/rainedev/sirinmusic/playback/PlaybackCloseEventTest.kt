package ru.rainedev.sirinmusic.playback

import org.junit.Assert.*
import org.junit.Test
import ru.rainedev.sirinmusic.data.Track

class PlaybackCloseEventTest {
    @Test fun retryAfterLostResponseReusesPayloadEvenIfAnotherCallbackArrives() {
        var ids = 0
        val pending = PendingPlaybackClose { "event-${++ids}" }
        val track = Track(id = 7, impressionId = "impression")
        val first = pending.event("track_end", "session", track, 100.0, 100.0, 90.0, "completed")
        val retry = pending.event("skip", "session", track, 101.0, 100.0, 91.0, "skipped")
        assertEquals(first, retry)
        assertEquals(1, ids)
    }
    @Test fun replayAndNewSessionOrImpressionGetTheirOwnId() {
        var ids = 0
        val pending = PendingPlaybackClose { "event-${++ids}" }
        val track = Track(id = 7, impressionId = "first")
        fun close(session: String, item: Track = track) = pending.event("skip", session, item, 0.0, 100.0, 0.0, "skipped")
        val first = close("s")
        pending.clear()
        assertNotEquals(first.id, close("s").id)
        assertNotEquals(close("s").id, close("other-session").id)
        assertNotEquals(close("s").id, close("s", track.copy(impressionId = "second")).id)
    }
}
