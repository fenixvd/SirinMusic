package ru.rainedev.sirinmusic.playback

enum class PlaybackFailureAction { SHOW_ERROR, RETRY_COMPATIBLE, ADVANCE, STOP }

/** Decoder failures may recover; paused tracks and network/auth failures never skip. */
class PlaybackFailures(private val maxConsecutive: Int = 3) {
    private var compatibleTried = false
    private var consecutive = 0

    fun failed(mediaFailure: Boolean, playbackRequested: Boolean): PlaybackFailureAction {
        if (!mediaFailure || !playbackRequested) return PlaybackFailureAction.SHOW_ERROR
        if (!compatibleTried) {
            compatibleTried = true
            return PlaybackFailureAction.RETRY_COMPATIBLE
        }
        consecutive++
        return if (consecutive > maxConsecutive) PlaybackFailureAction.STOP else PlaybackFailureAction.ADVANCE
    }

    fun newTrack() { compatibleTried = false }
    fun playing() { consecutive = 0 }
    fun reset() { newTrack(); consecutive = 0 }
}
