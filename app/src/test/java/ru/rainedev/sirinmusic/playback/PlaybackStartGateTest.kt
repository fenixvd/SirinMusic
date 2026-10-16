package ru.rainedev.sirinmusic.playback

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PlaybackStartGateTest {
    @Test fun unplugDuringServerClaimCannotStartAudioAfterDelayedReply() = runBlocking {
        val gate = PlaybackStartGate()
        val response = CompletableDeferred<Unit>()
        val ticket = gate.requestPlay()
        var started = false
        val pending = launch { response.await(); if (gate.allows(ticket)) started = true }
        gate.pause()
        response.complete(Unit)
        pending.join()
        assertFalse(started)
        assertFalse(gate.allows(gate.ticket)) // Reconnecting alone does not request play.
        assertTrue(gate.allows(gate.requestPlay()))
    }

    @Test fun newerSelectionInvalidatesPreviousStartAndPauseAlsoCancelsNewSelection() {
        val gate = PlaybackStartGate()
        val first = gate.requestPlay()
        val second = gate.requestPlay()
        assertFalse(gate.allows(first))
        assertTrue(gate.allows(second))
        gate.pause()
        assertFalse(gate.allows(second))
    }
}
