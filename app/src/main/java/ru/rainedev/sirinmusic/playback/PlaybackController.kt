package ru.rainedev.sirinmusic.playback

import ru.rainedev.sirinmusic.R
import ru.rainedev.sirinmusic.strings
import android.content.Context
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.media.AudioManager
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.rainedev.sirinmusic.data.ApiException
import ru.rainedev.sirinmusic.data.PlaybackState
import ru.rainedev.sirinmusic.data.MusikApi
import ru.rainedev.sirinmusic.data.ServerConnection
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
    private val startGate = PlaybackStartGate()
    private var ticker: Job? = null
    private var listenedSec = 0.0
    private var startedReported = false
    private var ownsPlayback = false
    private var syncUnavailable = false
    private var syncTicker: Job? = null
    private var restoreJob: Job? = null
    private val syncMutex = Mutex()
    private val pendingClose = PendingPlaybackClose()
    private val failures = PlaybackFailures()

    // ExoPlayer's noisy handler pauses its current stream. This receiver also
    // cancels starts that are still awaiting /play or playback ownership.
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) setPlaying(false)
        }
    }

    init {
        ContextCompat.registerReceiver(context.applicationContext, noisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    val player: ExoPlayer by lazy { buildPlayer() }
    val sessionPlayer: SessionPlayer by lazy { SessionPlayer(this) }

    private fun buildPlayer(): ExoPlayer {
        // Bearer нужен и потоку: без него /api/stream отвечает 401.
        val http = AuthenticatedDataSourceFactory(api)
        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .also { it.addListener(PlayerEvents()) }
    }

    private inner class PlayerEvents : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY) {
                startGate.pause()
            }
        }
        override fun onPlayerError(error: PlaybackException) {
            if (_ui.value.busy) {
                _ui.update { it.copy(error = strings(R.string.playback_controller_1, error.errorCodeName)) }
                return // A pending source change or close request owns the transition.
            }
            _ui.update { it.copy(error = strings(R.string.playback_controller_1, error.errorCodeName), busy = false) }
            val current = _ui.value.current ?: return
            if (player.currentMediaItem?.mediaId != current.key.toString()) return
            val mediaFailure = error.errorCode in setOf(
                PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
                PlaybackException.ERROR_CODE_DECODING_FAILED,
                PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
                PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            )
            when (failures.failed(mediaFailure, player.playWhenReady && startGate.allows(startGate.ticket))) {
                PlaybackFailureAction.SHOW_ERROR -> Unit
                PlaybackFailureAction.RETRY_COMPATIBLE -> {
                    val position = player.currentPosition.coerceAtLeast(0)
                    val item = player.currentMediaItem?.buildUpon()?.setUri(api.compatibleStreamUrl(current))?.build() ?: return
                    // Keep this impression, position and listening time; do not emit another track_start.
                    player.setMediaItem(item, position)
                    player.prepare()
                    player.play()
                    _ui.update { it.copy(error = null) }
                }
                PlaybackFailureAction.ADVANCE -> {
                    val expected = generation
                    scope.launch { if (generation == expected) reportAndAdvance("track_end", "unplayable") }
                }
                PlaybackFailureAction.STOP -> {
                    startGate.pause()
                    player.pause()
                    _ui.update { it.copy(error = strings(R.string.playback_controller_2)) }
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _ui.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) { failures.playing(); startTicker(); startSyncTicker() }
            else {
                ticker?.cancel(); syncTicker?.cancel()
                if (ownsPlayback) syncOnce()
            }
        }

        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_ENDED) {
                val expected = generation
                scope.launch { if (generation == expected) reportAndAdvance("track_end") }
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
    fun playFavorite(trackId: Long) = load { api.playFavorite(trackId) }

    fun playArtist(artist: String) = load { api.playArtist(artist) }

    fun playAlbum(artist: String, album: String) = load { api.playAlbum(artist, album) }
    fun playPlaylist(id: Long, index: Int = 0) = load { api.playPlaylist(id, index) }
    fun setContexts(session: String, ids: List<String>) { _ui.update { if (it.sessionId == session) it.copy(contextIds = ids) else it } }
    fun setFavorites(ids: Set<Long>) { _ui.update { it.copy(favorites = ids) } }


    private fun load(block: suspend () -> ru.rainedev.sirinmusic.data.RadioStart) {
        loadJob?.cancel()
        generation++
        val requestGeneration = generation
        val startTicket = startGate.requestPlay()
        loadJob = scope.launch {
            _ui.update { it.copy(busy = true, error = null) }
            runCatching { block() }
                .onSuccess { reply ->
                    if (generation != requestGeneration) return@onSuccess
                    val current = reply.current?.takeIf { it.key > 0 && it.ready }
                    if (current == null) {
                        _ui.update { it.copy(busy = false, error = strings(R.string.playback_controller_3)) }
                        return@onSuccess
                    }
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
                    play(current, startTicket)
                }
                .onFailure { e -> if (e is CancellationException) throw e else if (generation == requestGeneration) _ui.update { it.copy(busy = false, error = e.message) } }
        }
    }

    // ---- управление ----

    fun togglePlay() = setPlaying(!player.playWhenReady)

    fun setPlaying(playing: Boolean) {
        if (!playing) {
            startGate.pause()
            if (_ui.value.current != null) {
                val wasPlaying = player.isPlaying
                player.pause()
                if (!wasPlaying && ownsPlayback) syncOnce()
            }
            return
        }
        if (_ui.value.busy) return
        if (_ui.value.ended) { _ui.value.current?.let { playTrack(it.key) }; return }
        if (_ui.value.current == null) return
        beginPlayback(generation, startGate.requestPlay())
    }

    fun seekTo(sec: Double) {
        val position = safePlaybackPosition(sec, _ui.value.durationSec)
        player.seekTo((position * 1000).toLong())
        _ui.update { it.copy(positionSec = position) }
        if (ownsPlayback) syncOnce()
    }

    fun skip() = scope.launch { reportAndAdvance("skip") }

    fun previous() = scope.launch {
        val snapshot = _ui.value
        val session = snapshot.sessionId ?: return@launch
        if (snapshot.busy) return@launch
        val requestGeneration = generation
        val startTicket = startGate.requestPlay()
        _ui.update { it.copy(busy = true, error = null) }
        try {
            val reply = api.previous(session)
            if (requestGeneration == generation) {
                _ui.update { it.copy(queue = if (reply.fixed) reply.tracks else reply.queue, fixed = reply.fixed) }
                reply.current?.let { play(it, startTicket) }
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
        val startTicket = startGate.requestPlay()
        _ui.update { it.copy(busy = true, error = null) }
        scope.launch {
            try {
                val reply = if (index != null) api.jumpIndex(session, index) else api.jump(session, requireNotNull(trackId))
                if (requestGeneration == generation) {
                    _ui.update { it.copy(queue = if (reply.fixed) reply.tracks else reply.queue) }
                    reply.current?.let { play(it, startTicket) }
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
    private suspend fun reportAndAdvance(type: String, reason: String = if (type == "skip") "skipped" else "completed") {
        val snapshot = _ui.value
        val cur = snapshot.current ?: return
        if (snapshot.busy) return
        val requestGeneration = generation
        val startTicket = startGate.ticket
        _ui.update { it.copy(busy = true, error = null) }
        try {
            if (!saveRemoteState(!ownsPlayback, requestGeneration)) return
            ownsPlayback = true
            val event = pendingClose.event(type, snapshot.sessionId, cur, snapshot.positionSec,
                snapshot.durationSec, listenedSec, reason)
            val reply = api.event(event.type, event.trackId, event.sessionId, event.impressionId,
                event.position, event.duration, event.listened, eventId = event.id, reason = event.reason)
            if (requestGeneration != generation) return
            applyQueue(reply)
            when (advanceAction(reply, snapshot.fixed, cur)) {
                AdvanceAction.PLAY_NEXT -> play(requireNotNull(reply.next), startTicket)
                AdvanceAction.STOP -> {
                    player.pause()
                    _ui.update { it.copy(queue = emptyList(), ended = true) }
                }
                AdvanceAction.START_RADIO -> if (startGate.allows(startTicket)) startRadio()
                AdvanceAction.KEEP_CURRENT -> Unit // Duplicate close: never seek/restart the same impression.
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

    private fun play(track: Track, startTicket: Long) {
        generation++
        installTrack(track, 0.0, 0.0, resumed = false)
        if (startGate.allows(startTicket)) beginPlayback(generation, startTicket)
    }

    private fun installTrack(track: Track, position: Double, listened: Double, resumed: Boolean) {
        pendingClose.clear()
        failures.newTrack()
        ownsPlayback = false
        syncTicker?.cancel()
        listenedSec = listened.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
        startedReported = resumed
        _ui.update { it.copy(current = track, positionSec = position, durationSec = track.duration ?: 0.0,
            ended = false, busy = false) }

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

        player.playWhenReady = false
        player.setMediaItem(item, (position * 1000).toLong())
        player.prepare()
    }

    private fun beginPlayback(expectedGeneration: Int, startTicket: Long) {
        _ui.update { it.copy(busy = true, error = null) }
        scope.launch {
            try {
                if (!startGate.allows(startTicket)) return@launch
                if (saveRemoteState(true, expectedGeneration, playing = true) && generation == expectedGeneration) {
                    ownsPlayback = true
                    if (startGate.allows(startTicket)) {
                        // An error leaves ExoPlayer idle. An explicit Play must prepare it again.
                        if (player.playbackState == Player.STATE_IDLE) player.prepare()
                        player.play()
                        startPlaybackService()
                        if (!startedReported) { startedReported = true; sendSimple("track_start") }
                    } else {
                        // A disconnect/pause arrived while the claim was in flight.
                        saveRemoteState(false, expectedGeneration, playing = false)
                    }
                }
            } finally { if (generation == expectedGeneration) _ui.update { it.copy(busy = false) } }
        }
    }

    private fun startSyncTicker() {
        syncTicker?.cancel()
        syncTicker = scope.launch {
            while (player.isPlaying && ownsPlayback) {
                delay(5000)
                if (player.isPlaying && ownsPlayback) saveRemoteState(false, generation)
            }
        }
    }

    private fun syncOnce() {
        val expected = generation
        scope.launch { if (ownsPlayback) saveRemoteState(false, expected) }
    }

    private suspend fun saveRemoteState(claim: Boolean, expected: Int, playing: Boolean = player.isPlaying): Boolean =
        syncMutex.withLock {
            if (generation != expected) return@withLock false
            val snapshot = _ui.value
            val current = snapshot.current ?: return@withLock false
            if (syncUnavailable) return@withLock true
            val state = PlaybackState(snapshot.sessionId.orEmpty(), current.key,
                safePlaybackPosition(player.currentPosition / 1000.0, snapshot.durationSec),
                listenedSec, playing, settings.clientId)
            try {
                val result = api.savePlaybackState(state, claim)
                if (generation != expected) return@withLock false
                if (!result.ok) {
                    ownsPlayback = false
                    generation++
                    player.pause()
                    syncTicker?.cancel()
                    _ui.update { it.copy(busy = false, error = strings(R.string.playback_controller_4)) }
                    false
                } else true
            } catch (e: CancellationException) { throw e }
            catch (e: ApiException) {
                if (generation != expected) false
                else if (e.code == 404) { syncUnavailable = true; true }
                else { if (generation == expected) _ui.update { it.copy(error = strings(R.string.playback_controller_5, e.message)) }; false }
            } catch (_: Exception) {
                if (generation == expected) _ui.update { it.copy(error = strings(R.string.playback_controller_6)) }
                false
            }
        }

    /** Restore once while idle; no autoplay, claim, or duplicate listening event. */
    fun restoreIfIdle(refreshPaused: Boolean = false) {
        if (player.playWhenReady || _ui.value.busy || restoreJob?.isActive == true || syncUnavailable) return
        if (_ui.value.current != null && !refreshPaused) return
        val expected = generation
        restoreJob = scope.launch {
            try {
                val saved = try { api.playbackState().state }
                    catch (e: ApiException) { if (e.code == 404) { if (generation == expected) syncUnavailable = true; return@launch }; throw e }
                if (saved == null || saved.trackId <= 0 || saved.sessionId.isBlank()) return@launch
                val now = api.now(saved.sessionId)
                val track = restorableTrack(saved, now) ?: return@launch
                if (generation != expected || player.playWhenReady || _ui.value.busy) return@launch
                generation++ // Old pause saves/events must not overwrite the restored session.
                _ui.update { it.copy(sessionId = now.sessionId, queue = if (now.fixed) now.tracks else now.queue,
                    fixed = now.fixed, mode = now.mode.orEmpty(), maturity = now.maturity) }
                installTrack(track, safePlaybackPosition(saved.positionSec, track.duration ?: 0.0), saved.listenedSec, resumed = true)
            } catch (e: CancellationException) { throw e }
            catch (e: ApiException) {
                if (generation == expected && e.code != 404) _ui.update { it.copy(error = strings(R.string.playback_controller_7)) }
            } catch (_: Exception) { /* Offline startup keeps the local player idle. */ }
        }
    }

    fun stop() {
        pendingClose.clear()
        failures.reset()
        startGate.pause()
        val snapshot = _ui.value
        val stoppedState = snapshot.current?.takeIf { ownsPlayback && !syncUnavailable }?.let {
            PlaybackState(snapshot.sessionId.orEmpty(), it.key,
                safePlaybackPosition(player.currentPosition / 1000.0, snapshot.durationSec),
                listenedSec, false, settings.clientId)
        }
        val stoppedConnection = ServerConnection(settings.baseUrl, settings.token, settings.clientId)
        generation++
        val expected = generation
        if (stoppedState != null) scope.launch {
            syncMutex.withLock {
                if (generation == expected) {
                    try { MusikApi(stoppedConnection).savePlaybackState(stoppedState, false) }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { /* Logout/stop is local even when the server is unavailable. */ }
                }
            }
        }
        loadJob?.cancel()
        restoreJob?.cancel()
        ownsPlayback = false
        syncTicker?.cancel()
        syncUnavailable = false
        player.stop()
        player.clearMediaItems()
        ticker?.cancel()
        _ui.value = PlayerUi()
    }

    private fun startPlaybackService() {
        ContextCompat.startForegroundService(context, Intent(context, PlaybackService::class.java))
    }
}
