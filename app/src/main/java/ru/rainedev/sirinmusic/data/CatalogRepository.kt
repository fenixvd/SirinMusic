package ru.rainedev.sirinmusic.data

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

internal data class CatalogSnapshot(
    val health: Health,
    val tracks: List<Track>,
    val artists: List<ArtistRow>,
    val albums: List<AlbumRow>,
)

/** No UI or playback side effects; a failed section cancels the entire snapshot. */
internal class CatalogRepository(private val api: MusikApi) {
    suspend fun fetch(): CatalogSnapshot = coroutineScope {
        // Count first, so imports completed during this fetch trigger the next foreground probe.
        val health = api.health()
        val tracks = async { api.library() }
        val artists = async { api.artists() }
        val albums = async { api.albums() }
        CatalogSnapshot(health, tracks.await(), artists.await().artists, albums.await().albums)
    }
}
