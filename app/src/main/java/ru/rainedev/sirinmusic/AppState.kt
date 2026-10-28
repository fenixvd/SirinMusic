package ru.rainedev.sirinmusic

import ru.rainedev.sirinmusic.data.*

// Ratings are session feedback; hearts are persistent server favorites.
data class AppState(
    val loggedIn: Boolean = false,
    val loginState: LoginState = LoginState(),
    val serverTracks: Int = 0,
    val serverVersion: String? = null,
    val mixes: List<Mix> = emptyList(),
    val hint: String? = null,
    val tracks: List<Track> = emptyList(),
    val artists: List<ArtistRow> = emptyList(),
    val artistPhotos: Map<String, String> = emptyMap(),
    val albums: List<AlbumRow> = emptyList(),
    val favoritesState: FavoritesState = FavoritesState(),
    val profileState: ProfileState = ProfileState(),

    val libraryLoading: Boolean = false,
    val playlistState: PlaylistState = PlaylistState(),
    val operations: Set<String> = emptySet(),
    val message: String? = null,
) {
    val checking get() = loginState.checking
    val loginError get() = loginState.error
    val loginProfiles get() = loginState.profiles
    val playlists get() = playlistState.playlists
    val playlistsLoading get() = playlistState.loading
    val playlist get() = playlistState.playlist
    val playlistLoading get() = playlistState.detailLoading
    val favorites get() = favoritesState.tracks
    val favoriteArtists get() = favoritesState.artistNames
    val favoriteAlbums get() = favoritesState.albumNames
    val favoriteArtistRows get() = favoritesState.artists
    val favoriteAlbumRows get() = favoritesState.albums
    val favoriteRecommendations get() = favoritesState.recommendations
    val favoriteRecommendationsLoading get() = favoritesState.recommendationsLoading
    val favoriteRecommendationsError get() = favoritesState.recommendationsError
    val profile get() = profileState.profile
    val weekly get() = profileState.weekly
    val recommendations get() = profileState.recommendations
    val contexts get() = profileState.contexts
    val rules get() = profileState.rules
    val shares get() = profileState.shares
    val profileErrors get() = profileState.errors
    val profileLoading get() = profileState.loading.isNotEmpty()
    val shareToSend get() = profileState.shareToSend
    val busy get() = operations + favoritesState.busy + profileState.busy +
        if (playlistState.busy) setOf("playlist-mutation") else emptySet()
}
