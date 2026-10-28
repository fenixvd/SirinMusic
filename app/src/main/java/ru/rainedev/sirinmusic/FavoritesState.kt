package ru.rainedev.sirinmusic

import ru.rainedev.sirinmusic.data.*

data class FavoritesState(
    val tracks: List<Track> = emptyList(),
    val artists: List<ArtistRow> = emptyList(),
    val albums: List<AlbumRow> = emptyList(),
    val recommendations: FavoriteRecommendations? = null,
    val recommendationsLoading: Boolean = false,
    val recommendationsError: String? = null,
    val busy: Set<String> = emptySet(),
) {
    val trackIds: Set<Long> = tracks.map { it.key }.toSet()
    val artistNames: Set<String> = artists.map { it.artist }.toSet()
    val albumNames: Set<Pair<String, String>> = albums.map { it.artist to it.album }.toSet()
}
