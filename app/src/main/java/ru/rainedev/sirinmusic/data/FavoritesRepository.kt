package ru.rainedev.sirinmusic.data

internal interface FavoritesRepository {
    suspend fun list(): FavoritesReply
    suspend fun recommendations(): FavoriteRecommendations
    suspend fun toggleTrack(id: Long): FavoriteReply
    suspend fun toggleArtist(artist: String): FavoriteReply
    suspend fun toggleAlbum(artist: String, album: String): FavoriteReply
}

internal class MusikFavoritesRepository(private val api: MusikApi) : FavoritesRepository {
    override suspend fun list() = api.favorites()
    override suspend fun recommendations() = api.favoriteRecommendations()
    override suspend fun toggleTrack(id: Long) = api.toggleFavorite(id)
    override suspend fun toggleArtist(artist: String) = api.toggleArtistFavorite(artist)
    override suspend fun toggleAlbum(artist: String, album: String) = api.toggleAlbumFavorite(artist, album)
}
