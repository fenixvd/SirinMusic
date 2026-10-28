package ru.rainedev.sirinmusic

import ru.rainedev.sirinmusic.data.Playlist

data class PlaylistDraft(
    val original: Playlist? = null,
    val name: String = original?.name.orEmpty(),
    val description: String = original?.description.orEmpty(),
    val error: String? = null,
)

data class PlaylistState(
    val playlists: List<Playlist> = emptyList(),
    val loading: Boolean = false,
    val selectedId: Long? = null,
    val playlist: Playlist? = null,
    val detailLoading: Boolean = false,
    val busy: Boolean = false,
    val editor: PlaylistDraft? = null,
    val deletedId: Long? = null,
)
