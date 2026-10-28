package ru.rainedev.sirinmusic

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.rainedev.sirinmusic.data.*

internal class FavoritesController(
    scope: CoroutineScope,
    private val repository: FavoritesRepository,
    private val onFavorites: (Set<Long>) -> Unit,
    private val onMessage: (String) -> Unit,
) {
    private val jobs = TaskJobs(scope)
    private val mutex = Mutex()
    private val _state = MutableStateFlow(FavoritesState())
    val state = _state.asStateFlow()
    private suspend fun current(key: String) = jobs.isCurrent(key, currentCoroutineContext()[Job])
    private fun publish() = onFavorites(_state.value.trackIds)

    fun load(refreshRecommendations: Boolean = true): Job = jobs.launch("list") {
        try {
            mutex.withLock {
                val reply = repository.list()
                currentCoroutineContext().ensureActive()
                if (!current("list")) return@withLock
                val tracks = reply.tracks.map { it.asTrack() }.filter { it.key > 0 }.distinctBy { it.key }
                _state.update { it.copy(tracks = tracks, artists = reply.artists, albums = reply.albums) }
                publish()
                if (refreshRecommendations) loadRecommendations()
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { if (current("list")) onMessage(e.message ?: strings(R.string.account_activity_2)) }
    }

    fun loadRecommendations(): Job = jobs.launch("recommendations") {
        _state.update { it.copy(recommendationsLoading = true, recommendationsError = null) }
        try {
            val result = repository.recommendations()
            currentCoroutineContext().ensureActive()
            if (current("recommendations")) _state.update { it.copy(recommendations = result) }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            if (current("recommendations")) _state.update {
                when {
                    e is ApiException && e.code == 404 -> it.copy(recommendations = null, recommendationsError = null)
                    e is ApiException -> it.copy(recommendationsError = strings(R.string.app_view_model_20))
                    else -> it.copy(recommendationsError = strings(R.string.app_view_model_21))
                }
            }
        } finally { if (current("recommendations")) _state.update { it.copy(recommendationsLoading = false) } }
    }

    fun toggleTrack(track: Track): Job? = mutate("favorite:${track.key}") {
        val reply = repository.toggleTrack(track.key)
        currentCoroutineContext().ensureActive()
        _state.update { it.copy(tracks = if (reply.favorited) {
            if (it.tracks.any { row -> row.key == track.key }) it.tracks else it.tracks + track
        } else it.tracks.filterNot { row -> row.key == track.key }) }
        publish()
    }
    fun toggleArtist(artist: ArtistRow): Job? = mutate("artist:${artist.artist}") {
        val reply = repository.toggleArtist(artist.artist)
        currentCoroutineContext().ensureActive()
        _state.update { it.copy(artists = if (reply.favorited) {
            if (it.artists.any { row -> row.artist == artist.artist }) it.artists else it.artists + artist
        } else it.artists.filterNot { row -> row.artist == artist.artist }) }
    }
    fun toggleAlbum(album: AlbumRow): Job? = mutate("album:${album.artist}/${album.album}") {
        val reply = repository.toggleAlbum(album.artist, album.album)
        currentCoroutineContext().ensureActive()
        _state.update { it.copy(albums = if (reply.favorited) {
            if (it.albums.any { row -> row.artist == album.artist && row.album == album.album }) it.albums else it.albums + album
        } else it.albums.filterNot { row -> row.artist == album.artist && row.album == album.album }) }
    }

    private fun mutate(key: String, write: suspend () -> Unit): Job? {
        if (key in _state.value.busy) return null
        _state.update { it.copy(busy = it.busy + key) }
        return jobs.launchIfIdle(key) {
            try {
                mutex.withLock {
                    write()
                    currentCoroutineContext().ensureActive()
                    // Follow-up reads have their own error handling; never retry a confirmed toggle.
                    jobs.cancel("list")
                    loadRecommendations()
                    load(refreshRecommendations = false)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (current(key)) onMessage(e.message ?: strings(R.string.account_activity_2)) }
            finally { if (current(key)) _state.update { it.copy(busy = it.busy - key) } }
        }
    }

    fun reset() {
        jobs.cancelAll()
        _state.value = FavoritesState()
        publish()
    }
}
