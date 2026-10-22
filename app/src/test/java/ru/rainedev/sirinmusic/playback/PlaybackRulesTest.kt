package ru.rainedev.sirinmusic.playback

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.rainedev.sirinmusic.data.EventReply
import ru.rainedev.sirinmusic.data.Track

class PlaybackRulesTest {
    @Test fun fixedPlaylistNeverFallsIntoRadio() {
        assertEquals(AdvanceAction.STOP, advanceAction(EventReply(ended = true, fixed = true), true))
        assertEquals(AdvanceAction.STOP, advanceAction(EventReply(), true))
    }
    @Test fun nextTrackWinsAndRadioMayContinue() {
        assertEquals(AdvanceAction.PLAY_NEXT, advanceAction(EventReply(next = Track(id = 42), fixed = true), true))
        assertEquals(AdvanceAction.START_RADIO, advanceAction(EventReply(), false))
        assertEquals(AdvanceAction.STOP, advanceAction(EventReply(ended = true), false))
    }
    @Test fun duplicateCloseKeepsCurrentImpressionAndPosition() {
        val current = Track(id = 42, impressionId = "played-once")
        assertEquals(AdvanceAction.KEEP_CURRENT,
            advanceAction(EventReply(ignored = true, reason = "already_closed", next = current), false, current))
    }
    @Test fun ignoredCloseWithoutNextNeverStartsANewRadioOrStopsAFixedQueue() {
        assertEquals(AdvanceAction.KEEP_CURRENT, advanceAction(EventReply(ignored = true), false, Track(id = 42)))
        assertEquals(AdvanceAction.KEEP_CURRENT, advanceAction(EventReply(ignored = true, fixed = true), true, Track(id = 42)))
    }
    @Test fun ignoredCloseMayReturnTheAuthoritativeNextTrack() {
        assertEquals(AdvanceAction.PLAY_NEXT,
            advanceAction(EventReply(ignored = true, next = Track(id = 43)), false, Track(id = 42)))
        assertEquals(AdvanceAction.STOP, advanceAction(EventReply(ignored = true, ended = true), false, Track(id = 42)))
    }
    @Test fun sameTrackInANewImpressionIsANewPlaybackOccurrence() {
        assertEquals(AdvanceAction.PLAY_NEXT, advanceAction(
            EventReply(ignored = true, next = Track(id = 42, impressionId = "new")), false,
            Track(id = 42, impressionId = "old")))
        assertEquals(AdvanceAction.KEEP_CURRENT, advanceAction(
            EventReply(ignored = true, next = Track(id = 42)), false, Track(id = 42, impressionId = "old")))
    }
    @Test fun legacyDuplicateReplyDoesNotRestartAKnownImpression() {
        val current = Track(id = 42, impressionId = "same")
        assertEquals(AdvanceAction.KEEP_CURRENT, advanceAction(EventReply(next = current), false, current))
        // With no impression IDs a normal same-track playlist entry may be a new occurrence.
        assertEquals(AdvanceAction.PLAY_NEXT, advanceAction(EventReply(next = Track(id = 42)), true, Track(id = 42)))
    }
}
