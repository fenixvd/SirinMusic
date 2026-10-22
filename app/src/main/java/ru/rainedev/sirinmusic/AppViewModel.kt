package ru.rainedev.sirinmusic

import ru.rainedev.sirinmusic.R
import ru.rainedev.sirinmusic.strings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
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
    val loginProfiles: List<AccountProfile> = emptyList(),
    val serverTracks: Int = 0,
    val serverVersion: String? = null,
    val mixes: List<Mix> = emptyList(),
    val hint: String? = null,
    val tracks: List<Track> = emptyList(),
    val artists: List<ArtistRow> = emptyList(),
    val artistPhotos: Map<String, String> = emptyMap(),
    val albums: List<AlbumRow> = emptyList(),
    val favorites: List<Track> = emptyList(),
    val favoriteArtists: Set<String> = emptySet(),
    val favoriteAlbums: Set<Pair<String, String>> = emptySet(),
    val favoriteArtistRows: List<ArtistRow> = emptyList(),
    val favoriteAlbumRows: List<AlbumRow> = emptyList(),
    val favoriteRecommendations: FavoriteRecommendations? = null,
    val favoriteRecommendationsLoading: Boolean = false,
    val favoriteRecommendationsError: String? = null,
    val profile: Profile? = null,
    val weekly: WeeklyMetrics? = null,
    val recommendations: Recommendations? = null,
    val contexts: List<TasteContext> = emptyList(),
    val rules: List<RadioRule> = emptyList(),
    val shares: List<RadioShare> = emptyList(),
    val profileErrors: Map<String, String> = emptyMap(),
    val profileLoading: Boolean = false,
    val shareToSend: RadioShare? = null,

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
    val pendingPlayerNavigation = PendingPlayerNavigation()
    private val favoritesMutex = Mutex()
    private val jobs = TaskJobs(viewModelScope)
    private var connection = settings.baseUrl to settings.token
    private var selectedPlaylistId: Long? = null
    private var favoriteRecommendationsRequest = 0
    // Credentials exist only while choosing a profile, never in settings or saved state.
    private class PendingAccount(val url: String, val username: String, val password: String, val reply: TokenReply)
    private var pendingAccount: PendingAccount? = null
    val savedUrl get() = settings.baseUrl
    val savedToken get() = settings.token

    init {
        viewModelScope.launch {
            api.unauthorized.collect {
                // Keep the saved URL for re-authentication, but never keep a rejected token active.
                logout(forgetUrl = false)
                _state.update { it.copy(loginError = strings(R.string.app_view_model_1)) }
            }
        }
        if (settings.isConfigured) refreshAll()
    }

    private fun task(key: String, block: suspend () -> Unit) {
        jobs.launch(key) {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.update { it.copy(message = e.message ?: strings(R.string.account_activity_2)) } }
        }
    }

    private fun cancelTasks() = jobs.cancelAll()

    fun refreshPausedPlayback() {
        if (settings.isConfigured) playback.restoreIfIdle(refreshPaused = true)
    }

    fun syncConnection() {
        val current = settings.baseUrl to settings.token
        if (connection == current) return
        cancelTasks(); playback.stop(); connection = current; selectedPlaylistId = null
        _state.value = AppState(loggedIn = settings.isConfigured)
        if (settings.isConfigured) refreshAll()
    }

    fun login(url: String, token: String) = loginWithToken(url, token, false)

    fun loginWithoutToken(url: String) = loginWithToken(url, "", true)

    private fun loginWithToken(url: String, token: String, anonymous: Boolean) {
        cancelAccountLogin()
        val error = validateServerUrl(url) ?: if (!anonymous && token.isBlank()) strings(R.string.app_view_model_2) else null
        if (error != null) { _state.update { it.copy(loginError = error) }; return }
        task("login") {
            _state.update { it.copy(checking = true, loginError = null) }
            try {
                val draft = ServerConnection(url.trim().trimEnd('/'), token.trim(), settings.clientId)
                val client = MusikApi(draft)
                val health = if (anonymous) client.validateAnonymousConnection() else client.validateConnection()
                settings.setConnection(draft.baseUrl, draft.token, authDisabled = anonymous)
                connection = settings.baseUrl to settings.token
                _state.value = AppState(loggedIn = true, serverTracks = health.tracks, serverVersion = health.version)
                refreshAll()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.update { it.copy(checking = false, loginError = e.message) } }
        }
    }

    fun cancelAccountLogin() {
        pendingAccount = null
        _state.update { it.copy(loginProfiles = emptyList()) }
    }

    fun loginPassword(url: String, username: String, password: String) {
        cancelAccountLogin()
        val error = validateServerUrl(url) ?: if (password.isEmpty()) strings(R.string.app_view_model_3) else null
        if (error != null) { _state.update { it.copy(loginError = error) }; return }
        task("login") {
            _state.update { it.copy(checking = true, loginError = null) }
            try {
                val base = url.trim().trimEnd('/')
                val anonymous = MusikApi(ServerConnection(base, "", settings.clientId))
                val status = anonymous.authStatus()
                check(!status.multiUser || username.isNotBlank()) { strings(R.string.app_view_model_4) }
                val reply = anonymous.passwordToken(username, password)
                check(reply.token.isNotBlank()) { strings(R.string.app_view_model_5) }
                if (reply.profiles.size > 1) {
                    pendingAccount = PendingAccount(base, username, password, reply)
                    _state.update { it.copy(loginProfiles = reply.profiles) }
                } else completeAccountLogin(base, reply)
            } catch (e: CancellationException) { pendingAccount = null; throw e }
            catch (e: ApiException) {
                _state.update { it.copy(loginError = when (e.code) {
                    404 -> strings(R.string.app_view_model_6)
                    401 -> strings(R.string.app_view_model_7)
                    409 -> strings(R.string.app_view_model_8)
                    else -> e.message
                }) }
            } catch (e: Exception) { _state.update { it.copy(loginError = e.message) } }
            finally { _state.update { it.copy(checking = false) } }
        }
    }

    fun chooseLoginProfile(id: String) {
        val pending = pendingAccount ?: return
        if (pending.reply.profiles.none { it.id == id }) return
        task("login") {
            _state.update { it.copy(checking = true, loginError = null) }
            try {
                val reply = if (pending.reply.device?.profileId == id) pending.reply else
                    MusikApi(ServerConnection(pending.url, "", settings.clientId))
                        .passwordToken(pending.username, pending.password, id)
                check(reply.device?.profileId == id) { strings(R.string.app_view_model_9) }
                completeAccountLogin(pending.url, reply)
            } catch (e: CancellationException) { pendingAccount = null; throw e }
            catch (e: Exception) { _state.update { it.copy(loginError = e.message) } }
            finally { _state.update { it.copy(checking = false) } }
        }
    }

    private suspend fun completeAccountLogin(url: String, reply: TokenReply) {
        val draft = ServerConnection(url, reply.token, settings.clientId)
        val health = MusikApi(draft).validateConnection()
        settings.setConnection(draft.baseUrl, draft.token)
        pendingAccount = null
        connection = settings.baseUrl to settings.token
        _state.value = AppState(loggedIn = true, serverTracks = health.tracks, serverVersion = health.version)
        refreshAll()
    }

    fun logout(forgetUrl: Boolean = true) {
        pendingAccount = null
        cancelTasks(); playback.stop()
        if (forgetUrl) settings.clear() else settings.token = ""
        connection = settings.baseUrl to settings.token; selectedPlaylistId = null
        _state.value = AppState()
    }

    fun dismissMessage() { _state.update { it.copy(message = null) } }
    fun showMessage(text: String) { _state.update { it.copy(message = text) } }
    fun refreshAll() { playback.restoreIfIdle(); loadArtistPhotos(); loadMixes(); loadLibrary(); loadProfile(); loadFavorites(); loadPlaylists() }
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
    fun loadLibrary() = task("library") {
        _state.update { it.copy(libraryLoading = true) }
        try {
            val tracks = api.library(); val artists = api.artists(); val albums = api.albums()
            _state.update { it.copy(tracks = tracks, artists = artists.artists, albums = albums.albums) }
        } finally { _state.update { it.copy(libraryLoading = false) } }
    }
    fun loadProfile() = task("profile") {
        refreshProfileDetails()
        val h = api.health()
        _state.update { it.copy(serverTracks = h.tracks, serverVersion = h.version) }
    }
    private suspend fun <T> section(key: String, fetch: suspend () -> T, apply: (AppState, T) -> AppState) {
        try {
            val value = fetch()
            _state.update { apply(it, value).copy(profileErrors = it.profileErrors - key) }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            _state.update { it.copy(profileErrors = it.profileErrors + (key to (e.message ?: strings(R.string.app_view_model_11)))) }
        }
    }
    private suspend fun refreshProfileDetails() {
        _state.update { it.copy(profileLoading = true) }
        try {
            section("taste", { api.profile() }) { s, v -> s.copy(profile = v) }
            section("weekly", { api.weeklyMetrics() }) { s, v -> s.copy(weekly = v) }
            section("policy", { api.recommendations() }) { s, v -> s.copy(recommendations = v) }
            section("contexts", { api.contexts() }) { s, v -> s.copy(contexts = v.contexts) }
            section("rules", { api.rules() }) { s, v -> s.copy(rules = v.rules.filter { it.action == "block" }) }
            section("shares", { api.shares() }) { s, v -> s.copy(shares = v.shares.filter { it.active }) }
        } finally { _state.update { it.copy(profileLoading = false) } }
    }
    fun saveExplore(lo: Double, hi: Double) = mutation("explore") {
        api.saveExplore(lo, hi)
        val p = api.profile(); _state.update { it.copy(profile = p, message = strings(R.string.app_view_model_12)) }
    }
    fun createContext(name: String, kind: String) = mutation("contexts") {
        api.createContext(name, kind)
        val r = api.contexts(); _state.update { it.copy(contexts = r.contexts, message = strings(R.string.app_view_model_13)) }
    }
    fun toggleContext(id: String, active: Boolean) = mutation("contexts") {
        val session = playback.ui.value.sessionId ?: error(strings(R.string.app_view_model_14))
        val r = api.activateContext(id, session, active)
        playback.setContexts(session, r.contextIds)
    }
    fun deleteContext(id: String) = mutation("contexts") {
        val snapshot = playback.ui.value
        if (id in snapshot.contextIds && snapshot.sessionId != null) {
            val r = api.activateContext(id, snapshot.sessionId, false)
            playback.setContexts(snapshot.sessionId, r.contextIds)
        }
        api.deleteContext(id)
        val r = api.contexts(); _state.update { it.copy(contexts = r.contexts) }
    }
    fun restoreRule(id: String?) = mutation("rules") {
        if (id == null) api.undoRule() else api.restoreRule(id)
        val r = api.rules(); _state.update { it.copy(rules = r.rules.filter { it.action == "block" }) }
    }
    fun createShare(send: Boolean = false) = mutation("shares") {
        val created = api.createShare()
        val r = api.shares()
        _state.update { it.copy(shares = r.shares.filter { it.active }, shareToSend = if (send) created else null) }
    }
    fun shareRadio() = mutation("shares") {
        val r = api.shares()
        val share = r.shares.firstOrNull { it.active } ?: api.createShare()
        val updated = api.shares()
        _state.update { it.copy(shares = updated.shares.filter { it.active }, shareToSend = share) }
    }
    fun consumeShare() { _state.update { it.copy(shareToSend = null) } }
    fun revokeShare(token: String) = mutation("shares") {
        api.revokeShare(token); val r = api.shares()
        _state.update { it.copy(shares = r.shares.filter { it.active }) }
    }
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

    private suspend fun refreshFavorites() {
        val r = api.favorites(); val tracks = r.tracks.map { it.asTrack() }.filter { it.key > 0 }.distinctBy { it.key }
        playback.setFavorites(tracks.map { it.key }.toSet())
        _state.update { it.copy(favorites = tracks, favoriteArtists = r.artists.map { a -> a.artist }.toSet(),
            favoriteAlbums = r.albums.map { a -> a.artist to a.album }.toSet(), favoriteArtistRows = r.artists, favoriteAlbumRows = r.albums) }
        loadFavoriteRecommendations()
    }
    fun loadFavorites() = task("favorites") { favoritesMutex.withLock { refreshFavorites() } }

    fun loadFavoriteRecommendations() {
        val request = ++favoriteRecommendationsRequest
        task("favorite-recommendations") {
            _state.update { it.copy(favoriteRecommendationsLoading = true, favoriteRecommendationsError = null) }
            try {
                val result = api.favoriteRecommendations()
                if (request == favoriteRecommendationsRequest) _state.update { it.copy(favoriteRecommendations = result) }
            } catch (e: CancellationException) { throw e }
            catch (e: ApiException) {
                if (request == favoriteRecommendationsRequest) _state.update {
                    if (e.code == 404) it.copy(favoriteRecommendations = null, favoriteRecommendationsError = null)
                    else it.copy(favoriteRecommendationsError = strings(R.string.app_view_model_20))
                }
            } catch (_: Exception) {
                if (request == favoriteRecommendationsRequest) _state.update { it.copy(favoriteRecommendationsError = strings(R.string.app_view_model_21)) }
            } finally {
                if (request == favoriteRecommendationsRequest) _state.update { it.copy(favoriteRecommendationsLoading = false) }
            }
        }
    }

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
        require(name.isNotBlank()) { strings(R.string.app_view_model_22) }; api.createPlaylist(name.trim(), description.trim()); refreshPlaylists()
    }
    fun editPlaylist(p: Playlist, name: String, description: String) = mutation("playlist-mutation") {
        require(name.isNotBlank()) { strings(R.string.app_view_model_22) }
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
        _state.update { it.copy(message = strings(R.string.app_view_model_23)) }
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
