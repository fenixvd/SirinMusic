package ru.rainedev.sirinmusic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import ru.rainedev.sirinmusic.data.*
import ru.rainedev.sirinmusic.playback.PlaybackController

// Ratings are session feedback; hearts are persistent server favorites.
data class AppState(
    val loggedIn: Boolean = false,
    val checking: Boolean = false,
    val loginError: String? = null,
    val serverTracks: Int = 0,
    val serverVersion: String? = null,
    val mixes: List<Mix> = emptyList(),
    val hint: String? = null,
    val tracks: List<Track> = emptyList(),
    val artists: List<ArtistRow> = emptyList(),
    val albums: List<AlbumRow> = emptyList(),
    val favorites: List<Track> = emptyList(),
    val favoriteArtists: Set<String> = emptySet(),
    val favoriteAlbums: Set<Pair<String, String>> = emptySet(),
    val favoriteArtistRows: List<ArtistRow> = emptyList(),
    val favoriteAlbumRows: List<AlbumRow> = emptyList(),
    val profile: Profile? = null,
    val libraryLoading: Boolean = false,
    val playlists: List<Playlist> = emptyList(),
    val playlistsLoading: Boolean = false,
    val playlist: Playlist? = null,
    val playlistLoading: Boolean = false,
    val busy: Set<String> = emptySet(),
    val message: String? = null,
)

class AppViewModel(private val settings: Settings, private val api: MusikApi, val playback: PlaybackController) : ViewModel() {
    private val _state = MutableStateFlow(AppState(loggedIn = settings.isConfigured))
    val state = _state.asStateFlow()
    /** musik://connect link from the system camera; kept here to survive Activity recreation. */
    val pendingLink = PendingLink()
    private val favoritesMutex = Mutex()
    private val jobs = TaskJobs(viewModelScope)
    private var connection = settings.baseUrl to settings.token
    private var selectedPlaylistId: Long? = null
    val savedUrl get() = settings.baseUrl
    val savedToken get() = settings.token

    init {
        viewModelScope.launch {
            api.unauthorized.collect {
                // Keep the saved URL for re-authentication, but never keep a rejected token active.
                logout(forgetUrl = false)
                _state.update { it.copy(loginError = "Сервер отклонил токен. Войди снова.") }
            }
        }
        if (settings.isConfigured) refreshAll()
    }

    private fun task(key: String, block: suspend () -> Unit) {
        jobs.launch(key) {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.update { it.copy(message = e.message ?: "Не удалось выполнить запрос") } }
        }
    }

    private fun cancelTasks() = jobs.cancelAll()

    fun syncConnection() {
        val current = settings.baseUrl to settings.token
        if (connection == current) return
        cancelTasks(); playback.stop(); connection = current; selectedPlaylistId = null
        _state.value = AppState(loggedIn = settings.isConfigured)
        if (settings.isConfigured) refreshAll()
    }

    fun login(url: String, token: String) {
        val error = validateServerUrl(url) ?: if (token.isBlank()) "Введи токен" else null
        if (error != null) { _state.update { it.copy(loginError = error) }; return }
        task("login") {
            _state.update { it.copy(checking = true, loginError = null) }
            try {
                val draft = ServerConnection(url.trim().trimEnd('/'), token.trim(), settings.clientId)
                val health = MusikApi(draft).health()
                check(health.ok) { "Сервер ответил, но не готов" }
                settings.setConnection(draft.baseUrl, draft.token)
                connection = settings.baseUrl to settings.token
                _state.value = AppState(loggedIn = true, serverTracks = health.tracks, serverVersion = health.version)
                refreshAll()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.update { it.copy(checking = false, loginError = e.message) } }
        }
    }

    fun logout(forgetUrl: Boolean = true) {
        cancelTasks(); playback.stop()
        if (forgetUrl) settings.clear() else settings.token = ""
        connection = settings.baseUrl to settings.token; selectedPlaylistId = null
        _state.value = AppState()
    }

    fun dismissMessage() { _state.update { it.copy(message = null) } }
    fun showMessage(text: String) { _state.update { it.copy(message = text) } }
    fun refreshAll() { loadMixes(); loadLibrary(); loadProfile(); loadFavorites(); loadPlaylists() }
    fun loadMixes() = task("mixes") { val r = api.mixes(); _state.update { it.copy(mixes = r.mixes, hint = r.hint) } }
    fun loadLibrary() = task("library") {
        _state.update { it.copy(libraryLoading = true) }
        try {
            val tracks = api.library(); val artists = api.artists(); val albums = api.albums()
            _state.update { it.copy(tracks = tracks, artists = artists.artists, albums = albums.albums) }
        } finally { _state.update { it.copy(libraryLoading = false) } }
    }
    fun loadProfile() = task("profile") {
        val p = api.profile(); val h = api.health()
        _state.update { it.copy(profile = p, serverTracks = h.tracks, serverVersion = h.version) }
    }
    private suspend fun refreshFavorites() {
        val r = api.favorites(); val tracks = r.tracks.map { it.asTrack() }.filter { it.key > 0 }.distinctBy { it.key }
        playback.setFavorites(tracks.map { it.key }.toSet())
        _state.update { it.copy(favorites = tracks, favoriteArtists = r.artists.map { a -> a.artist }.toSet(),
            favoriteAlbums = r.albums.map { a -> a.artist to a.album }.toSet(), favoriteArtistRows = r.artists, favoriteAlbumRows = r.albums) }
    }
    fun loadFavorites() = task("favorites") { favoritesMutex.withLock { refreshFavorites() } }

    private fun mutation(key: String, block: suspend () -> Unit) {
        if (key in _state.value.busy) return
        _state.update { it.copy(busy = it.busy + key) }
        task(key) {
            try { block() }
            finally { _state.update { it.copy(busy = it.busy - key) } }
        }
    }
    fun toggleFavorite(id: Long) = mutation("favorite:$id") { favoritesMutex.withLock { api.toggleFavorite(id); refreshFavorites() } }
    fun toggleArtistFavorite(artist: String) = mutation("artist:$artist") { favoritesMutex.withLock { api.toggleArtistFavorite(artist); refreshFavorites() } }
    fun toggleAlbumFavorite(artist: String, album: String) = mutation("album:$artist/$album") {
        favoritesMutex.withLock { api.toggleAlbumFavorite(artist, album); refreshFavorites() }
    }

    private suspend fun refreshPlaylists() { val r = api.playlists(); _state.update { it.copy(playlists = r.playlists) } }
    fun loadPlaylists() = task("playlists") {
        _state.update { it.copy(playlistsLoading = true) }
        try { refreshPlaylists() } finally { _state.update { it.copy(playlistsLoading = false) } }
    }
    fun openPlaylist(id: Long) {
        selectedPlaylistId = id
        _state.update { it.copy(playlist = null, playlistLoading = true) }
        task("playlist") {
            try { val p = api.playlist(id); if (selectedPlaylistId == id) _state.update { it.copy(playlist = p) } }
            finally { if (selectedPlaylistId == id) _state.update { it.copy(playlistLoading = false) } }
        }
    }
    fun closePlaylist() { selectedPlaylistId = null; jobs.cancel("playlist"); _state.update { it.copy(playlist = null, playlistLoading = false) } }
    fun createPlaylist(name: String, description: String) = mutation("playlist-mutation") {
        require(name.isNotBlank()) { "Введи название" }; api.createPlaylist(name.trim(), description.trim()); refreshPlaylists()
    }
    fun editPlaylist(p: Playlist, name: String, description: String) = mutation("playlist-mutation") {
        require(name.isNotBlank()) { "Введи название" }
        val updated = api.editPlaylist(p, name.trim(), description.trim())
        if (selectedPlaylistId == p.id) _state.update { it.copy(playlist = updated) }
        refreshPlaylists()
    }
    fun deletePlaylist(id: Long) = mutation("playlist-mutation") {
        api.deletePlaylist(id)
        if (selectedPlaylistId == id) closePlaylist()
        refreshPlaylists()
    }
    fun addToPlaylist(id: Long, trackId: Long) = mutation("playlist-mutation") {
        api.addPlaylistTrack(id, trackId); refreshPlaylists()
        if (selectedPlaylistId == id) { val p = api.playlist(id); _state.update { it.copy(playlist = p) } }
        _state.update { it.copy(message = "Трек добавлен в плейлист") }
    }
    fun removeFromPlaylist(id: Long, itemId: String) = mutation("playlist-mutation") {
        api.removePlaylistTrack(id, itemId); val p = api.playlist(id)
        if (selectedPlaylistId == id) _state.update { it.copy(playlist = p) }
        refreshPlaylists()
    }
    fun movePlaylistItem(p: Playlist, index: Int, delta: Int) = mutation("playlist-mutation") {
        val next = index + delta
        if (next !in p.tracks.indices) return@mutation
        val ids = p.tracks.map { it.itemId }.toMutableList(); val item = ids.removeAt(index); ids.add(next, item)
        val updated = api.reorderPlaylist(p.id, ids)
        if (selectedPlaylistId == p.id) _state.update { it.copy(playlist = updated) }
    }
    fun artworkUrl(path: String?, width: Int): String? = path?.let {
        runCatching { api.url(it) + (if (it.contains('?')) "&" else "?") + "w=$width" }.getOrNull()
    }

    class Factory(private val app: SirinApp) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(app.settings, app.api, app.playback) as T
    }
}
