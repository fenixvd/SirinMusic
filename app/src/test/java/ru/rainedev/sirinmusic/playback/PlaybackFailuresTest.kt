package ru.rainedev.sirinmusic.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackFailuresTest {
    @Test fun pausedBrokenTrackIsNeitherRetriedNorSkipped() {
        val failures = PlaybackFailures()
        repeat(5) { assertEquals(PlaybackFailureAction.SHOW_ERROR, failures.failed(true, false)) }
        assertEquals(PlaybackFailureAction.RETRY_COMPATIBLE, failures.failed(true, true))
    }
    @Test fun networkAndAuthenticationFailuresNeverAdvance() {
        val failures = PlaybackFailures()
        repeat(5) { assertEquals(PlaybackFailureAction.SHOW_ERROR, failures.failed(false, true)) }
        assertEquals(PlaybackFailureAction.RETRY_COMPATIBLE, failures.failed(true, true))
    }
    @Test fun retriesCompatibleOncePerTrackAndStopsOnFourthBrokenTrack() {
        val failures = PlaybackFailures()
        repeat(3) {
            assertEquals(PlaybackFailureAction.RETRY_COMPATIBLE, failures.failed(true, true))
            assertEquals(PlaybackFailureAction.ADVANCE, failures.failed(true, true))
            failures.newTrack()
        }
        assertEquals(PlaybackFailureAction.RETRY_COMPATIBLE, failures.failed(true, true))
        assertEquals(PlaybackFailureAction.STOP, failures.failed(true, true))
    }
    @Test fun successfulPlaybackBreaksTheFailureRun() {
        val failures = PlaybackFailures(maxConsecutive = 1)
        assertEquals(PlaybackFailureAction.RETRY_COMPATIBLE, failures.failed(true, true))
        assertEquals(PlaybackFailureAction.ADVANCE, failures.failed(true, true))
        failures.playing()
        failures.newTrack()
        assertEquals(PlaybackFailureAction.RETRY_COMPATIBLE, failures.failed(true, true))
        assertEquals(PlaybackFailureAction.ADVANCE, failures.failed(true, true))
    }
}
