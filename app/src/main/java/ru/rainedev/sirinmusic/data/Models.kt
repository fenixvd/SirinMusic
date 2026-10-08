package ru.rainedev.sirinmusic.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Поля сняты с живого сервера (GET /api/… на musik 1.0.0), а не придуманы.
// Всё необязательное помечено nullable: сервер опускает часть полей в разных ответах.

@Serializable
data class Health(
    val ok: Boolean = false,
    val tracks: Int = 0,
    val dim: Int = 0,
    val auth: Boolean = false,
    val version: String? = null,
    @SerialName("api_version") val apiVersion: String? = null,
)

@Serializable
data class Track(
    val id: Long = 0,
    @SerialName("track_id") val trackId: Long? = null,
    val artist: String? = null,
    val title: String? = null,
    val album: String? = null,
    val duration: Double? = null,
    val artwork: String? = null,
    val stream: String? = null,
    @SerialName("impression_id") val impressionId: String? = null,
    @SerialName("cluster_id") val clusterId: Int? = null,
    val source: String? = null,
    val explanation: String? = null,
    val score: Double? = null,
    val ready: Boolean = true,
    val position: Int? = null,
) {
    /** В очереди сервер зовёт идентификатор track_id, в «сейчас играет» — id. */
    val key: Long get() = if (id != 0L) id else (trackId ?: 0L)
    val artworkPath: String get() = artwork ?: "/api/artwork/$key"
    val streamPath: String get() = stream ?: "/api/stream/$key"
}

@Serializable
data class RadioStart(
    @SerialName("session_id") val sessionId: String? = null,
    val current: Track? = null,
    val queue: List<Track> = emptyList(),
    val maturity: String? = null,
    val mode: String? = null,
    val tracks: List<Track> = emptyList(),
    val fixed: Boolean = false,
)

@Serializable
data class EventReply(
    @SerialName("session_id") val sessionId: String? = null,
    val next: Track? = null,
    val queue: List<Track> = emptyList(),
    val tracks: List<Track>? = null,
    val index: Int? = null,
    val maturity: String? = null,
    val name: String? = null,
    val ended: Boolean = false,
    val fixed: Boolean = false,
    val rating: String? = null,
)

@Serializable
data class Mix(
    val kind: String = "",
    val name: String = "",
    val title: String? = null,
    val subtitle: String? = null,
    val tracks: Int = 0,
    @SerialName("cover_track_id") val coverTrackId: Long? = null,
    val highlight: Boolean = false,
    val stale: Boolean = false,
    val today: Boolean = false,
    val ready: Boolean = true,
    val position: Int? = null,
)

@Serializable
data class MixesReply(val mixes: List<Mix> = emptyList(), val hint: String? = null)

@Serializable
data class ArtistRow(
    val artist: String = "",
    val tracks: Int = 0,
    @SerialName("cover_track_id") val coverTrackId: Long? = null,
    val artwork: String? = null,
)

@Serializable
data class ArtistsReply(val artists: List<ArtistRow> = emptyList(), val count: Int = 0)

@Serializable
data class AlbumRow(
    val artist: String = "",
    val album: String = "",
    val tracks: Int = 0,
    @SerialName("cover_track_id") val coverTrackId: Long? = null,
    val artwork: String? = null,
)

@Serializable
data class AlbumsReply(val albums: List<AlbumRow> = emptyList(), val count: Int = 0)

@Serializable
data class FavoriteReply(
    val favorited: Boolean = false,
    @SerialName("track_id") val trackId: Long? = null,
    val type: String? = null,
)

/** Чего не хватает в ответе — не беда: экран профиля показывает то, что пришло. */
@Serializable
data class Profile(
    val maturity: String? = null,
    val plays: Int? = null,
    @SerialName("n_positive") val likes: Int? = null,
    @SerialName("n_negative") val skips: Int? = null,
    @SerialName("explore_lo") val exploreLo: Double? = null,
    @SerialName("explore_hi") val exploreHi: Double? = null,
    @SerialName("explore_ratio") val exploreRatio: Double? = null,
    @SerialName("top_artists") val topArtists: List<TopArtist> = emptyList(),
    @SerialName("tracks_ready") val tracksReady: Int? = null,
)

@Serializable
data class FavoriteEntry(
    @SerialName("track_id") val trackId: Long = 0,
    val track: Track? = null,
    val artist: String? = null,
    val title: String? = null,
    val duration: Double? = null,
) {
    fun asTrack(): Track = track ?: Track(trackId = trackId, artist = artist, title = title, duration = duration)
}

@Serializable
data class FavoritesReply(
    val tracks: List<FavoriteEntry> = emptyList(),
    val artists: List<ArtistRow> = emptyList(),
    val albums: List<AlbumRow> = emptyList(),
)

@Serializable
data class Playlist(
    val id: Long = 0,
    val name: String = "",
    val description: String = "",
    val type: String = "manual",
    val kind: String = "user",
    @SerialName("track_count") val trackCount: Int = 0,
    @SerialName("cover_track_id") val coverTrackId: Long? = null,
    @SerialName("cover_artwork") val coverArtwork: String? = null,
    @SerialName("sort_mode") val sortMode: String = "manual",
    @SerialName("allow_duplicates") val allowDuplicates: Boolean = false,
    val tracks: List<PlaylistItem> = emptyList(),
) {
    val editable: Boolean get() = type == "manual"
}

@Serializable
data class PlaylistItem(
    @SerialName("item_id") val itemId: String = "",
    @SerialName("track_id") val trackId: Long = 0,
    val artist: String? = null,
    val title: String? = null,
    val album: String? = null,
    val duration: Double? = null,
    val unresolved: Boolean = false,
    @SerialName("unresolved_artist") val unresolvedArtist: String? = null,
    @SerialName("unresolved_title") val unresolvedTitle: String? = null,
) {
    fun asTrack() = Track(id = trackId, artist = artist ?: unresolvedArtist, title = title ?: unresolvedTitle, album = album, duration = duration, ready = !unresolved && trackId > 0)
}

@Serializable
data class PlaylistsReply(val playlists: List<Playlist> = emptyList())

@Serializable
data class Lyrics(
    @SerialName("track_id") val trackId: Long = 0,
    @SerialName("plain_lyrics") val plainLyrics: String = "",
    @SerialName("synced_lyrics") val syncedLyrics: String = "",
    val instrumental: Boolean = false,
    val status: String = "absent",
    val source: String = "",
)

@Serializable data class TopArtist(val artist: String = "", val count: Int = 0)
@Serializable data class WeeklyMetrics(
    @SerialName("window_days") val windowDays: Int? = null,
    val overall: Outcome? = null, val breakdowns: List<Outcome> = emptyList(),
)
@Serializable data class Outcome(
    val dimension: String? = null, val value: String? = null, val played: Int? = null,
    @SerialName("unique_artists") val uniqueArtists: Int? = null,
    @SerialName("finish_rate") val finishRate: Double? = null,
    @SerialName("early_skip_rate") val skipRate: Double? = null,
)
@Serializable data class Recommendations(
    @SerialName("last_policy") val lastPolicy: LastPolicy? = null,
)
@Serializable data class LastPolicy(val policy: RadioPolicy? = null)
@Serializable data class RadioPolicy(
    @SerialName("explore_share") val exploreShare: Double? = null,
    val bounds: List<Double> = emptyList(),
)
@Serializable data class TasteContext(
    @SerialName("context_id") val id: String = "", val name: String = "", val kind: String = "mood",
)
@Serializable data class ContextsReply(val contexts: List<TasteContext> = emptyList())
@Serializable data class ContextActivation(
    @SerialName("context_ids") val contextIds: List<String> = emptyList(),
)
@Serializable data class RadioRule(
    @SerialName("rule_id") val id: String = "",
    @SerialName("target_type") val targetType: String = "",
    @SerialName("target_key") val targetKey: String = "",
    val action: String = "", val remaining: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
)
@Serializable data class RulesReply(val rules: List<RadioRule> = emptyList())
@Serializable data class RadioShare(
    val token: String = "", val name: String = "", val url: String = "", val active: Boolean = true,
    @SerialName("listen_count") val listenCount: Int = 0,
)
@Serializable data class SharesReply(val shares: List<RadioShare> = emptyList())
@Serializable data class MixJob(val id: Long = 0, val status: String = "", val error: String? = null)
