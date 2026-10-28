package ru.rainedev.sirinmusic.playback

import ru.rainedev.sirinmusic.data.Track

data class PlayerUi(
    val current: Track? = null,
    val queue: List<Track> = emptyList(),
    val sessionId: String? = null,
    val contextIds: List<String> = emptyList(),
    val maturity: String? = null,
    val mode: String = "",
    val isPlaying: Boolean = false,
    val positionSec: Double = 0.0,
    val durationSec: Double = 0.0,
    val busy: Boolean = false,
    val error: String? = null,
    val favorites: Set<Long> = emptySet(),
    val ratings: Map<Long, String> = emptyMap(),
    val ratingPending: Set<Long> = emptySet(),
    val fixed: Boolean = false,
    val ended: Boolean = false,
)
