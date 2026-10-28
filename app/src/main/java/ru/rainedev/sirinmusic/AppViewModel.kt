package ru.rainedev.sirinmusic

import ru.rainedev.sirinmusic.R
import ru.rainedev.sirinmusic.strings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import ru.rainedev.sirinmusic.data.*
import ru.rainedev.sirinmusic.playback.PlaybackController

class AppViewModel(private val settings: Settings, private val api: MusikApi, val playback: PlaybackController) : ViewModel() {
    private val _state = MutableStateFlow(AppState(loggedIn = settings.isConfigured))
    val state = _state.asStateFlow()
    /** musik://connect link from the system camera; kept here to survive Activity recreation. */
    val pendingLink = PendingLink()
    val pendingPlayerNavigation = PendingPlayerNavigation()
    private val jobs = TaskJobs(viewModelScope)
    private val playlists = PlaylistController(viewModelScope, MusikPlaylistRepository(api)) { message ->
        _state.update { it.copy(message = message) }
    }
    private val login = LoginController(viewModelScope, MusikLoginRepository(), { settings.clientId }) { draft, health, anonymous ->
        resetContentTasks()
        playback.stop(connection)
        settings.setConnection(draft.baseUrl, draft.token, authDisabled = anonymous)
        connection = settings.snapshot()
        _state.value = AppState(loggedIn = true, serverTracks = health.tracks, serverVersion = health.version)
        refreshAll()
    }
    private val favorites = FavoritesController(viewModelScope, MusikFavoritesRepository(api), playback::setFavorites, ::showMessage)
    private val profile = ProfileController(viewModelScope, MusikProfileRepository(api), {
        playback.ui.value.let { player -> player.sessionId?.let { ContextSession(it, player.contextIds) } }
    }, playback::setContexts, ::showMessage)
    private val catalog = CatalogRepository(api)
    private var catalogTrackCount: Int? = null
    private val contentRefresh = ContentRefresh(
        scope = viewModelScope,
        now = { android.os.SystemClock.elapsedRealtime() },
        onLoading = { loading -> _state.update { it.copy(libraryLoading = loading) } },
        onError = { e -> _state.update { it.copy(message = e.message ?: strings(R.string.account_activity_2)) } },
        fetch = {
            val snapshot = catalog.fetch()
            currentCoroutineContext().ensureActive()
            catalogTrackCount = snapshot.health.tracks
            _state.update { it.copy(tracks = snapshot.tracks, artists = snapshot.artists, albums = snapshot.albums,
                serverTracks = snapshot.health.tracks, serverVersion = snapshot.health.version) }
            loadArtistPhotos(); loadMixes(); loadFavorites(); loadPlaylists()
        },
    )
    private var connection = settings.snapshot()
    val savedUrl get() = settings.baseUrl
    val savedToken get() = settings.token

    init {
        viewModelScope.launch { favorites.state.collect { snapshot -> _state.update { it.copy(favoritesState = snapshot) } } }
        viewModelScope.launch { profile.state.collect { snapshot -> _state.update { it.copy(profileState = snapshot) } } }
        viewModelScope.launch {
            login.state.collect { snapshot -> _state.update { it.copy(loginState = snapshot) } }
        }
        viewModelScope.launch {
            playlists.state.collect { snapshot -> _state.update { it.copy(playlistState = snapshot) } }
        }
        viewModelScope.launch {
            api.unauthorized.collect { rejected ->
                if (!api.isCurrentConnection(rejected)) return@collect
                // Keep the saved URL for re-authentication, but never keep a rejected token active.
                logout(forgetUrl = false)
                login.cancel(error = strings(R.string.app_view_model_1))
            }
        }
        if (settings.isConfigured) refreshAll()
    }

    private fun task(key: String, block: suspend () -> Unit) {
        jobs.launch(key) {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (taskIsCurrent(key)) _state.update { it.copy(message = e.message ?: strings(R.string.account_activity_2)) }
            }
        }
    }

    private suspend fun taskIsCurrent(key: String): Boolean = jobs.isCurrent(key, currentCoroutineContext()[Job])

    private fun cancelTasks() {
        login.cancel()
        resetContentTasks()
    }

    private fun resetContentTasks() {
        jobs.cancelAll()
        playlists.reset()
        favorites.reset()
        profile.reset()
        contentRefresh.reset()
        catalogTrackCount = null
    }

    fun refreshPausedPlayback() {
        if (settings.isConfigured) playback.restoreIfIdle(refreshPaused = true)
    }

    fun onForeground() {
        syncConnection()
        refreshPausedPlayback()
        refreshContent(force = false)
    }

    fun syncConnection() {
        val current = settings.snapshot()
        if (connection == current) return
        cancelTasks(); playback.stop(connection); connection = current
        _state.value = AppState(loggedIn = settings.isConfigured)
        if (settings.isConfigured) refreshAll()
    }

    fun login(url: String, token: String) { login.token(url, token) }
    fun loginWithoutToken(url: String) { login.token(url, "", anonymous = true) }
    fun loginPassword(url: String, username: String, password: String) { login.password(url, username, password) }
    fun chooseLoginProfile(id: String) { login.chooseProfile(id) }
    fun cancelAccountLogin() = login.cancel()

    fun logout(forgetUrl: Boolean = true) {
        cancelTasks(); playback.stop(connection)
        if (forgetUrl) settings.clear() else settings.token = ""
        connection = settings.snapshot()
        _state.value = AppState()
    }

    fun dismissMessage() { _state.update { it.copy(message = null) } }
    fun showMessage(text: String) { _state.update { it.copy(message = text) } }
    fun refreshAll() { playback.restoreIfIdle(); refreshContent(); loadProfile() }

    /** Refresh the server catalogue without changing the active playback session or queue. */
    fun refreshContent(force: Boolean = true) {
        if (settings.isConfigured) contentRefresh.request(force)
    }

    /** Cheap foreground probe; a periodic full fetch also catches edits with an unchanged count. */
    fun checkLibraryChanges() = task("catalog-check") {
        if (!settings.isConfigured) return@task
        try {
            val health = api.health()
            if (health.tracks != catalogTrackCount || contentRefresh.isOlderThan(5 * 60_000)) refreshContent()
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { /* A background probe must not replace usable content with an error. */ }
    }
    fun loadArtistPhotos() = task("artistPhotos") {
        try {
            val photos = api.artistPhotos().photos.filterValues { path ->
                path.startsWith("/api/artist-photos/") && runCatching { api.url(path) }.isSuccess
            }
            _state.update { it.copy(artistPhotos = photos) }
        } catch (e: CancellationException) { throw e }
        catch (e: ApiException) {
            if (e.code == 404) _state.update { it.copy(artistPhotos = emptyMap()) }
            else _state.update { it.copy(message = strings(R.string.app_view_model_10)) }
        } catch (_: Exception) { /* Covers remain available when photos cannot be loaded. */ }
    }
    fun loadMixes() = task("mixes") { val r = api.mixes(); _state.update { it.copy(mixes = r.mixes, hint = r.hint) } }
    fun loadLibrary() = refreshContent()
    fun loadProfile() {
        profile.load()
        task("profile-health") {
            val h = api.health()
            currentCoroutineContext().ensureActive()
            _state.update { it.copy(serverTracks = h.tracks, serverVersion = h.version) }
        }
    }
    fun saveExplore(lo: Double, hi: Double) { profile.saveExplore(lo, hi) }
    fun createContext(name: String, kind: String) { profile.createContext(name, kind) }
    fun toggleContext(id: String, active: Boolean) { profile.toggleContext(id, active) }
    fun deleteContext(id: String) { profile.deleteContext(id) }
    fun restoreRule(id: String?) { profile.restoreRule(id) }
    fun createShare(send: Boolean = false) { profile.createShare(send) }
    fun shareRadio() { profile.shareRadio() }
    fun consumeShare() = profile.consumeShare()
    fun revokeShare(token: String) { profile.revokeShare(token) }
    fun refreshServerMixes() = mutation("mix-refresh") {
        val job = api.refreshMixes()
        require(job.id > 0) { strings(R.string.app_view_model_15) }
        _state.update { it.copy(message = strings(R.string.app_view_model_16)) }
        repeat(90) {
            delay(2000)
            val current = api.mixJob(job.id)
            when (current.status) {
                "done" -> {
                    val mixes = api.mixes()
                    _state.update { it.copy(mixes = mixes.mixes, hint = mixes.hint, message = strings(R.string.app_view_model_17)) }
                    return@mutation
                }
                "failed", "error" -> error(strings(R.string.app_view_model_18))
            }
        }
        _state.update { it.copy(message = strings(R.string.app_view_model_19)) }
    }

    fun loadFavorites() { favorites.load() }
    fun loadFavoriteRecommendations() { favorites.loadRecommendations() }

    private fun mutation(key: String, block: suspend () -> Unit) {
        if (key in _state.value.busy) return
        _state.update { it.copy(operations = it.operations + key) }
        task(key) {
            try { block() }
            finally { if (taskIsCurrent(key)) _state.update { it.copy(operations = it.operations - key) } }
        }
    }
    fun toggleFavorite(id: Long) {
        val track = _state.value.tracks.firstOrNull { it.key == id }
            ?: _state.value.favorites.firstOrNull { it.key == id }
            ?: playback.ui.value.current?.takeIf { it.key == id } ?: Track(id = id)
        favorites.toggleTrack(track)
    }
    fun toggleArtistFavorite(artist: String) {
        favorites.toggleArtist(_state.value.artists.firstOrNull { it.artist == artist } ?: ArtistRow(artist = artist))
    }
    fun toggleAlbumFavorite(artist: String, album: String) {
        favorites.toggleAlbum(_state.value.albums.firstOrNull { it.artist == artist && it.album == album }
            ?: AlbumRow(artist = artist, album = album))
    }

    fun loadPlaylists() { playlists.load() }
    fun openPlaylist(id: Long) { playlists.open(id) }
    fun closePlaylist() = playlists.close()
    fun openPlaylistEditor(playlist: Playlist? = null) = playlists.edit(playlist)
    fun changePlaylistName(name: String) = playlists.changeName(name)
    fun changePlaylistDescription(description: String) = playlists.changeDescription(description)
    fun dismissPlaylistEditor() = playlists.dismissEditor()
    fun savePlaylistEditor() { playlists.save() }
    fun consumePlaylistDeletion(id: Long) = playlists.consumeDeletion(id)
    fun deletePlaylist(id: Long) { playlists.delete(id) }
    fun addToPlaylist(id: Long, trackId: Long) { playlists.add(id, trackId) }
    fun removeFromPlaylist(id: Long, itemId: String) { playlists.remove(id, itemId) }
    fun movePlaylistItem(p: Playlist, index: Int, delta: Int) { playlists.move(p, index, delta) }
    fun artworkUrl(path: String?, width: Int): String? = path?.let {
        runCatching { api.url(it) + (if (it.contains('?')) "&" else "?") + "w=$width" }.getOrNull()
    }

    class Factory(private val app: SirinApp) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(app.settings, app.api, app.playback) as T
    }
}
