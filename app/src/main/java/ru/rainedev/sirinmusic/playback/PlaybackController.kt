package ru.rainedev.sirinmusic.playback

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.rainedev.sirinmusic.data.MusikApi
import ru.rainedev.sirinmusic.data.Settings
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

/**
 * Мозг воспроизведения. Очередь не строится на клиенте: ответ /api/events
 * содержит next — сервер сам решает, что играть дальше, и так работает обучение
 * вкуса. Клиент обязан честно присылать track_start / track_end / skip.
 */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class PlaybackController(
    private val context: Context,
    private val api: MusikApi,
    private val settings: Settings,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _ui = MutableStateFlow(PlayerUi())
    val ui: StateFlow<PlayerUi> = _ui.asStateFlow()

    private var loadJob: Job? = null
    private var generation = 0
    private var ticker: Job? = null
    private var listenedSec = 0.0
    private var startedReported = false

    val player: ExoPlayer by lazy { buildPlayer() }
    val sessionPlayer: SessionPlayer by lazy { SessionPlayer(this) }

    private fun buildPlayer(): ExoPlayer {
        // Bearer нужен и потоку: без него /api/stream отвечает 401.
        val http = AuthenticatedDataSourceFactory(api)
        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .also { it.addListener(PlayerEvents()) }
    }

    private inner class PlayerEvents : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            _ui.update { it.copy(error = "Не удалось воспроизвести: ${error.errorCodeName}", busy = false) }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _ui.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) startTicker() else ticker?.cancel()
        }

        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_ENDED) {
                scope.launch { reportAndAdvance("track_end") }
            }
        }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (true) {
                val pos = player.currentPosition.coerceAtLeast(0) / 1000.0
                val dur = player.duration.let { if (it == C.TIME_UNSET) 0L else it } / 1000.0
                if (player.isPlaying) listenedSec += 0.5
                _ui.update { it.copy(positionSec = pos, durationSec = dur) }
                delay(500)
            }
        }
    }

    // ---- запуск источников ----

    fun startRadio(seed: Long? = null) = load { api.startRadio(seed) }

    fun playMix(kind: String) = load { api.playMix(kind) }

    fun playTrack(trackId: Long) = load { api.playTrack(trackId) }

    fun playArtist(artist: String) = load { api.playArtist(artist) }

    fun playAlbum(artist: String, album: String) = load { api.playAlbum(artist, album) }
    fun playPlaylist(id: Long, index: Int = 0) = load { api.playPlaylist(id, index) }
    fun setContexts(session: String, ids: List<String>) { _ui.update { if (it.sessionId == session) it.copy(contextIds = ids) else it } }
    fun setFavorites(ids: Set<Long>) { _ui.update { it.copy(favorites = ids) } }


    private fun load(block: suspend () -> ru.rainedev.sirinmusic.data.RadioStart) {
        loadJob?.cancel()
        generation++
        loadJob = scope.launch {
            _ui.update { it.copy(busy = true, error = null) }
            runCatching { block() }
                .onSuccess { reply ->
                    _ui.update {
                        it.copy(
                            sessionId = reply.sessionId ?: it.sessionId,
                            contextIds = emptyList(),
                            queue = if (reply.fixed) reply.tracks else reply.queue,
                            fixed = reply.fixed,
                            ended = false,
                            ratings = emptyMap(),
                            ratingPending = emptySet(),
                            maturity = reply.maturity ?: it.maturity,
                            mode = reply.mode ?: it.mode,
                            busy = false,
                        )
                    }
                    reply.current?.let { play(it) }
                }
                .onFailure { e -> if (e is CancellationException) throw e else _ui.update { it.copy(busy = false, error = e.message) } }
        }
    }

    // ---- управление ----

    fun togglePlay() {
        if (_ui.value.ended) _ui.value.current?.let { playTrack(it.key) }
        else if (player.isPlaying) player.pause() else {
            player.play()
            startPlaybackService()
        }
    }

    fun seekTo(sec: Double) = player.seekTo((sec * 1000).toLong())

    fun skip() = scope.launch { reportAndAdvance("skip") }

    fun previous() = scope.launch {
        val snapshot = _ui.value
        val session = snapshot.sessionId ?: return@launch
        if (snapshot.busy) return@launch
        val requestGeneration = generation
        _ui.update { it.copy(busy = true, error = null) }
        try {
            val reply = api.previous(session)
            if (requestGeneration == generation) {
                _ui.update { it.copy(queue = if (reply.fixed) reply.tracks else reply.queue, fixed = reply.fixed) }
                reply.current?.let { play(it) }
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { if (requestGeneration == generation) _ui.update { it.copy(error = e.message) } }
        finally { if (requestGeneration == generation) _ui.update { it.copy(busy = false) } }
    }

    fun like() { _ui.value.current?.let { rate(it.key, "like") } }
    fun dislike() { _ui.value.current?.let { rate(it.key, "dislike") } }

    fun rate(id: Long, type: String) {
        require(type == "like" || type == "dislike")
        val snapshot = _ui.value
        val session = snapshot.sessionId ?: return
        if (id in snapshot.ratingPending || snapshot.ratings[id] == type) return
        val current = snapshot.current?.takeIf { it.key == id }
        val requestGeneration = generation
        _ui.update { it.copy(ratingPending = it.ratingPending + id) }
        scope.launch {
            try {
                val reply = api.event(type, id, session, current?.impressionId,
                    if (current != null) snapshot.positionSec else 0.0,
                    if (current != null) snapshot.durationSec else 0.0,
                    if (current != null) listenedSec else 0.0)
                if (generation == requestGeneration) {
                    applyQueue(reply)
                    _ui.update { it.copy(ratings = it.ratings + (id to (reply.rating ?: type))) }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (generation == requestGeneration) _ui.update { it.copy(error = e.message) } }
            finally { if (generation == requestGeneration) _ui.update { it.copy(ratingPending = it.ratingPending - id) } }
        }
    }

    fun jumpTo(trackId: Long) = jump(trackId = trackId)
    fun jumpToIndex(index: Int) = jump(index = index)
    private fun jump(trackId: Long? = null, index: Int? = null) {
        val session = _ui.value.sessionId ?: return
        if (_ui.value.busy) return
        val requestGeneration = generation
        _ui.update { it.copy(busy = true, error = null) }
        scope.launch {
            try {
                val reply = if (index != null) api.jumpIndex(session, index) else api.jump(session, requireNotNull(trackId))
                if (requestGeneration == generation) {
                    _ui.update { it.copy(queue = if (reply.fixed) reply.tracks else reply.queue) }
                    reply.current?.let { play(it) }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (requestGeneration == generation) _ui.update { it.copy(error = e.message) } }
            finally { if (requestGeneration == generation) _ui.update { it.copy(busy = false) } }
        }
    }

    private fun sendSimple(type: String) {
        val snapshot = _ui.value
        val cur = snapshot.current ?: return
        val requestGeneration = generation
        scope.launch {
            runCatching {
                api.event(
                    type = type,
                    trackId = cur.key,
                    sessionId = snapshot.sessionId,
                    impressionId = cur.impressionId,
                    positionSec = snapshot.positionSec,
                    durationSec = snapshot.durationSec,
                    listenedSec = listenedSec,
                )
            }.onSuccess { if (generation == requestGeneration) applyQueue(it) }
                .onFailure { e -> if (e is CancellationException) throw e else if (generation == requestGeneration) _ui.update { it.copy(error = e.message) } }
        }
    }

    /** Only skip/end advance a session; ratings leave the current track in place. */
    private suspend fun reportAndAdvance(type: String) {
        val snapshot = _ui.value
        val cur = snapshot.current ?: return
        if (snapshot.busy) return
        val requestGeneration = generation
        _ui.update { it.copy(busy = true, error = null) }
        try {
            val reply = api.event(type, cur.key, snapshot.sessionId, cur.impressionId,
                snapshot.positionSec, snapshot.durationSec, listenedSec)
            if (requestGeneration != generation) return
            applyQueue(reply)
            when (advanceAction(reply, snapshot.fixed)) {
                AdvanceAction.PLAY_NEXT -> play(requireNotNull(reply.next))
                AdvanceAction.STOP -> {
                    player.pause()
                    _ui.update { it.copy(queue = emptyList(), ended = true) }
                }
                AdvanceAction.START_RADIO -> startRadio()
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { if (requestGeneration == generation) _ui.update { it.copy(error = e.message) } }
        finally { if (requestGeneration == generation) _ui.update { it.copy(busy = false) } }
    }

    private fun applyQueue(reply: ru.rainedev.sirinmusic.data.EventReply) {
        _ui.update {
            it.copy(
                sessionId = reply.sessionId ?: it.sessionId,
                queue = if (reply.fixed || it.fixed) reply.tracks ?: it.queue else reply.queue,
                maturity = reply.maturity ?: it.maturity,
            )
        }
    }

    private fun play(track: Track) {
        listenedSec = 0.0
        startedReported = false
        _ui.update { it.copy(current = track, positionSec = 0.0, durationSec = track.duration ?: 0.0, ended = false) }

        // Метаданные нужны не для UI, а для экрана блокировки и шторки.
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title.orEmpty())
            .setArtist(track.artist.orEmpty())
            .setAlbumTitle(track.album.orEmpty())
            .setArtworkUri(android.net.Uri.parse(api.url(track.artworkPath)))
            .build()
        val item = MediaItem.Builder()
            .setUri(api.url(track.streamPath))
            .setMediaId(track.key.toString())
            .setMediaMetadata(metadata)
            .build()

        player.setMediaItem(item)
        player.prepare()
        player.play()
        startPlaybackService()

        if (!startedReported) {
            startedReported = true
            sendSimple("track_start")
        }
    }

    fun stop() {
        generation++
        loadJob?.cancel()
        player.stop()
        player.clearMediaItems()
        ticker?.cancel()
        _ui.value = PlayerUi()
    }

    private fun startPlaybackService() {
        ContextCompat.startForegroundService(context, Intent(context, PlaybackService::class.java))
    }
}
