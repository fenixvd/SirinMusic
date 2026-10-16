package ru.rainedev.sirinmusic.playback

/** A pause also invalidates a start still waiting for a server response. Main thread only. */
internal class PlaybackStartGate {
    var ticket: Long = 0
        private set
    private var requested = false

    fun requestPlay(): Long { requested = true; return ++ticket }
    fun pause() { requested = false; ticket++ }
    fun allows(startTicket: Long): Boolean = requested && ticket == startTicket
}
