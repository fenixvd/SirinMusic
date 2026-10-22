package ru.rainedev.sirinmusic.playback

import ru.rainedev.sirinmusic.data.Track
import java.util.UUID

/** A retry reuses the complete close event, including its verdict and timing. */
data class PlaybackCloseEvent(
    val id: String,
    val type: String,
    val sessionId: String?,
    val trackId: Long,
    val impressionId: String?,
    val position: Double,
    val duration: Double,
    val listened: Double,
    val reason: String,
)

class PendingPlaybackClose(private val newId: () -> String = { UUID.randomUUID().toString() }) {
    private var pending: PlaybackCloseEvent? = null

    fun event(type: String, sessionId: String?, track: Track, position: Double, duration: Double,
              listened: Double, reason: String): PlaybackCloseEvent {
        require(type == "track_end" || type == "skip")
        return pending?.takeIf { it.sessionId == sessionId && it.trackId == track.key && it.impressionId == track.impressionId }
            ?: PlaybackCloseEvent(newId(), type, sessionId, track.key, track.impressionId, position, duration, listened, reason)
                .also { pending = it }
    }

    /** Installing even the same track starts a new playback occurrence. */
    fun clear() { pending = null }
}
