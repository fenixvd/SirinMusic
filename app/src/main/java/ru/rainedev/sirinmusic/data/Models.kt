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
    val passport: TrackPassport? = null,
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
    val ignored: Boolean = false,
    val reason: String? = null,
)

@Serializable
data class FavoriteRecommendations(
    val tracks: List<Track> = emptyList(),
    val artists: List<ArtistRow> = emptyList(),
    val albums: List<AlbumRow> = emptyList(),
    val empty: Boolean = false,
    val hint: String? = null,
    val explanation: String? = null,
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
    fun asTrack(): Track = track?.let {
        if (it.key != 0L) it else it.copy(trackId = trackId)
    } ?: Track(trackId = trackId, artist = artist, title = title, duration = duration)
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

@Serializable
data class AuthStatus(val ok: Boolean = false,
    @SerialName("auth_enabled") val authEnabled: Boolean = true,
    @SerialName("multi_user") val multiUser: Boolean = false)

@Serializable
data class AccountProfile(val id: String, val name: String,
    @SerialName("is_default") val isDefault: Boolean = false)

@Serializable
data class DeviceToken(
    val id: String,
    @SerialName("profile_id") val profileId: String,
    @SerialName("profile_name") val profileName: String = "",
    @SerialName("expires_at") val expiresAt: String = "")

@Serializable
class TokenReply(val token: String, val device: DeviceToken? = null,
    val profiles: List<AccountProfile> = emptyList())

@Serializable
data class PlaybackState(
    @SerialName("session_id") val sessionId: String = "",
    @SerialName("track_id") val trackId: Long,
    @SerialName("position_sec") val positionSec: Double = 0.0,
    @SerialName("listened_sec") val listenedSec: Double = 0.0,
    val playing: Boolean = false,
    @SerialName("client_id") val clientId: String = "",
    @SerialName("updated_at") val updatedAt: String = "")

@Serializable
data class PlaybackStateReply(val ok: Boolean = false, val state: PlaybackState? = null,
    val track: Track? = null)

@Serializable
data class ArtistPhotosReply(val photos: Map<String, String> = emptyMap(), val count: Int = 0)

fun artistPhotoKey(name: String): String = name.trim().split(Regex("[\\s\\p{Z}]+"))
    .joinToString(" ").lowercase(java.util.Locale.ROOT)

@kotlinx.serialization.Serializable
data class ServerValues(val addr: String = "", @SerialName("public_base_url") val publicUrl: String = "", val library: String = "")
@kotlinx.serialization.Serializable
class ServerPhone(val urls: List<String> = emptyList(), @SerialName("auth_enabled") val authEnabled: Boolean = false, val token: String = "", val openapi: String = "/api/openapi.json")
@kotlinx.serialization.Serializable
class ServerSettingsReply(val running: ServerValues = ServerValues(), val saved: ServerValues = ServerValues(), @SerialName("restart_pending") val restartPending: Boolean = false, @SerialName("restart_supported") val restartSupported: Boolean = false, @SerialName("env_path") val envPath: String = "", @SerialName("local_ips") val localIps: List<String> = emptyList(), val phone: ServerPhone = ServerPhone())
@kotlinx.serialization.Serializable
data class ServerDir(val name: String = "", val path: String = "")
@kotlinx.serialization.Serializable
data class ServerDirs(val path: String = "", val parent: String = "", val dirs: List<ServerDir>? = emptyList(), val roots: List<ServerDir>? = emptyList())
@kotlinx.serialization.Serializable
data class ServerExternalIp(val ip: String = "", val source: String = "", @SerialName("checked_at") val checkedAt: String = "")

/** GET /api/tracks/{id}: measured audio and counters scoped to the authenticated profile. */
@Serializable
data class TrackPassport(
    val format: String? = null,
    val bitrate: Long? = null,
    @SerialName("sample_rate") val sampleRate: Long? = null,
    val channels: Int? = null,
    val lufs: Double? = null,
    val bpm: Double? = null,
    val key: String? = null,
    val mode: String? = null,
    val year: Int? = null,
    val duration: Double? = null,
    val genres: List<String> = emptyList(),
    val plays: Int? = null,
    val finishes: Int? = null,
    @SerialName("early_skips") val earlySkips: Int? = null,
    @SerialName("last_played_at") val lastPlayedAt: String? = null,
)
