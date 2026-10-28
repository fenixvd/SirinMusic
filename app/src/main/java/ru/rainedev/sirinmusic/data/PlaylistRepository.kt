package ru.rainedev.sirinmusic.data

internal interface PlaylistRepository {
    suspend fun list(): List<Playlist>
    suspend fun get(id: Long): Playlist
    suspend fun create(name: String, description: String): Playlist
    suspend fun edit(playlist: Playlist, name: String, description: String): Playlist
    suspend fun delete(id: Long)
    suspend fun add(id: Long, trackId: Long)
    suspend fun remove(id: Long, itemId: String)
    suspend fun reorder(id: Long, itemIds: List<String>): Playlist
}

internal class MusikPlaylistRepository(private val api: MusikApi) : PlaylistRepository {
    override suspend fun list() = api.playlists().playlists
    override suspend fun get(id: Long) = api.playlist(id)
    override suspend fun create(name: String, description: String) = api.createPlaylist(name, description)
    override suspend fun edit(playlist: Playlist, name: String, description: String) = api.editPlaylist(playlist, name, description)
    override suspend fun delete(id: Long) = api.deletePlaylist(id)
    override suspend fun add(id: Long, trackId: Long) = api.addPlaylistTrack(id, trackId)
    override suspend fun remove(id: Long, itemId: String) = api.removePlaylistTrack(id, itemId)
    override suspend fun reorder(id: Long, itemIds: List<String>) = api.reorderPlaylist(id, itemIds)
}
