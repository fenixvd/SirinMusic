package ru.rainedev.sirinmusic.playback

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import ru.rainedev.sirinmusic.data.*

class PlaybackRestorerTest {
    private class Harness(scope: CoroutineScope) {
        var saved: suspend () -> PlaybackState? = { PlaybackState("session", 7, 12.5, 5.0, false) }
        var session: suspend (String) -> RadioStart = { RadioStart(sessionId = it, current = Track(id = 7, impressionId = "current")) }
        var allowed = true
        var unavailable = 0
        var reads = 0
        val restored = mutableListOf<RestoredPlayback>()
        val errors = mutableListOf<Exception>()
        val restorer = PlaybackRestorer(scope, { reads++; saved() }, { session(it) }, { allowed }, restored::add, { unavailable++ }, errors::add)
    }
    @Test fun restoresPositionAndLiveImpressionOnlyAfterBothReads() = runBlocking {
        val h = Harness(this)
        h.restorer.request()!!.join()
        val result = h.restored.single()
        assertEquals(12.5, result.saved.positionSec, 0.0)
        assertEquals(5.0, result.saved.listenedSec, 0.0)
        assertEquals("current", result.track.impressionId)
        assertEquals("session", result.session.sessionId)
    }
    @Test fun repeatedForegroundRequestsShareOneRestore() = runBlocking {
        val h = Harness(this); val gate = CompletableDeferred<Unit>()
        h.saved = { gate.await(); PlaybackState("session", 7) }
        val first = h.restorer.request()!!
        assertSame(first, h.restorer.request())
        gate.complete(Unit); first.join()
        assertEquals(1, h.reads)
        assertEquals(1, h.restored.size)
    }
    @Test fun localSeekCancellationWinsOverLateSessionResponse() = runBlocking {
        val h = Harness(this); val gate = CompletableDeferred<Unit>()
        h.session = { withContext(NonCancellable) { gate.await() }; RadioStart(sessionId = "session", current = Track(id = 7)) }
        val job = h.restorer.request()!!; yield()
        h.restorer.cancel(); gate.complete(Unit); job.join()
        assertTrue(h.restored.isEmpty())
        assertTrue(h.errors.isEmpty())
    }
    @Test fun cancelBeforeSavedStateArrivesDoesNotRequestLiveSession() = runBlocking {
        val h = Harness(this); val gate = CompletableDeferred<Unit>()
        h.saved = { withContext(NonCancellable) { gate.await() }; PlaybackState("session", 7) }
        h.session = { fail("Cancelled restore must not read session"); error("Unexpected") }
        val job = h.restorer.request()!!; yield()
        h.restorer.cancel(); gate.complete(Unit); job.join()
        assertTrue(h.restored.isEmpty())
    }
    @Test fun lateFailureAfterNewSourceCannotShowOldErrorOrDisableSync() = runBlocking {
        for (code in listOf(404, 500)) {
            val h = Harness(this); val gate = CompletableDeferred<Unit>()
            h.saved = { withContext(NonCancellable) { gate.await() }; throw ApiException(code, "Old failure") }
            val job = h.restorer.request()!!; yield()
            h.restorer.cancel(); gate.complete(Unit); job.join()
            assertTrue(h.errors.isEmpty()); assertEquals(0, h.unavailable)
        }
    }
    @Test fun cancelledRestoreCanBeReplacedBeforeOldRequestFinishes() = runBlocking {
        val h = Harness(this); val gate = CompletableDeferred<Unit>()
        h.saved = { withContext(NonCancellable) { gate.await() }; PlaybackState("old", 8) }
        val old = h.restorer.request()!!; yield()
        h.restorer.cancel()
        h.saved = { PlaybackState("session", 7) }
        val next = h.restorer.request()!!; next.join()
        gate.complete(Unit); old.join()
        assertEquals(7L, h.restored.single().track.key)
        assertEquals(2, h.reads)
    }
    @Test fun playbackStartingWhileRequestIsPendingPreventsRestore() = runBlocking {
        val h = Harness(this); val gate = CompletableDeferred<Unit>()
        h.session = { gate.await(); RadioStart(sessionId = "session", current = Track(id = 7)) }
        val job = h.restorer.request()!!; yield()
        h.allowed = false; gate.complete(Unit); job.join()
        assertTrue(h.restored.isEmpty())
        assertNull(h.restorer.request())
    }
    @Test fun ownershipGenerationChangePreventsRestoreWithoutExplicitCancellation() = runBlocking {
        val h = Harness(this); val gate = CompletableDeferred<Unit>()
        var generation = 0
        h.session = { gate.await(); RadioStart(sessionId = "session", current = Track(id = 7)) }
        val job = h.restorer.request { generation == 0 }!!; yield()
        generation++
        gate.complete(Unit); job.join()
        assertTrue(h.restored.isEmpty())
    }

    @Test fun unavailableStateEndpointDisablesSyncButMissingSessionDoesNot() = runBlocking {
        val h = Harness(this)
        h.saved = { throw ApiException(404, "Unsupported") }
        h.restorer.request()!!.join()
        assertEquals(1, h.unavailable)
        h.saved = { PlaybackState("session", 7) }
        h.session = { throw ApiException(404, "Expired") }
        h.restorer.request()!!.join()
        assertEquals(1, h.unavailable)
        assertEquals(1, h.errors.size)
    }
    @Test fun mismatchedSessionTrackAndUnresolvedTrackAreNotRestored() = runBlocking {
        val h = Harness(this)
        for (reply in listOf(RadioStart(sessionId = "other", current = Track(id = 7)),
            RadioStart(sessionId = "session", current = Track(id = 8)),
            RadioStart(sessionId = "session", current = Track(id = 7, ready = false)))) {
            h.session = { reply }; h.restorer.request()!!.join()
        }
        assertTrue(h.restored.isEmpty())
    }
    @Test fun emptyAndInvalidStateDoNotFetchSession() = runBlocking {
        val h = Harness(this)
        h.session = { fail("Invalid state must not fetch session"); error("Unexpected") }
        for (state in listOf(null, PlaybackState("", 7), PlaybackState("session", 0))) {
            h.saved = { state }; h.restorer.request()!!.join()
        }
        assertTrue(h.restored.isEmpty())
    }
    @Test fun errorInMediaPreparationIsHandledAndRestoreCanBeRetried() = runBlocking {
        var errors = 0; var attempts = 0
        val restorer = PlaybackRestorer(this, { PlaybackState("session", 7) },
            { RadioStart(sessionId = it, current = Track(id = 7)) }, { true },
            { attempts++; if (attempts == 1) error("Invalid stream URL") }, {}, { errors++ })
        restorer.request()!!.join(); restorer.request()!!.join()
        assertEquals(1, errors); assertEquals(2, attempts)
    }
}
