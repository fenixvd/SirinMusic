package ru.rainedev.sirinmusic.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.rainedev.sirinmusic.AppState
import ru.rainedev.sirinmusic.data.artistPhotoKey
import ru.rainedev.sirinmusic.data.filterLibrary
import ru.rainedev.sirinmusic.data.trackMatches
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun LibraryScreen(state: AppState, ratings: Map<Long, String>, ratingPending: Set<Long>, hasSession: Boolean, artworkUrl: (String?, Int) -> String?,
    onTrack: (Long) -> Unit, onArtist: (String) -> Unit, onAlbum: (String, String) -> Unit,
    onFavorite: (Long) -> Unit, onArtistFavorite: (String) -> Unit, onAlbumFavorite: (String, String) -> Unit,
    onRate: (Long, String) -> Unit, onAddToPlaylist: (Long) -> Unit, contentPadding: PaddingValues,
    favoritesOnly: Boolean = false, selectionMode: Boolean = false, onFavoriteTrack: (Long) -> Unit = onTrack) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var favoritesFilter by rememberSaveable { mutableStateOf(favoritesOnly) }
    val tracks = if (favoritesFilter) state.favorites else state.tracks
    val artists = if (favoritesFilter) state.favoriteArtistRows else state.artists
    val albums = if (favoritesFilter) state.favoriteAlbumRows else state.albums
    val favoriteIds = remember(state.favorites) { state.favorites.map { it.key }.toSet() }
    val needle = query.trim()
    // Только открытая вкладка; быстрый ввод отменяет предыдущий поиск.
    val shownTracks by produceState(initialValue = tracks, tracks, needle, tab) {
        value = if (tab != 0) emptyList() else if (needle.isEmpty()) tracks else {
            delay(200)
            withContext(Dispatchers.Default) { filterLibrary(tracks, needle, ::trackMatches) }
        }
    }
    val shownArtists by produceState(initialValue = artists, artists, needle, tab) {
        value = if (tab != 1) emptyList() else if (needle.isEmpty()) artists else {
            delay(200)
            withContext(Dispatchers.Default) { filterLibrary(artists, needle) { item, q -> item.artist.contains(q, ignoreCase = true) } }
        }
    }
    val shownAlbums by produceState(initialValue = albums, albums, needle, tab) {
        value = if (tab != 2) emptyList() else if (needle.isEmpty()) albums else {
            delay(200)
            withContext(Dispatchers.Default) { filterLibrary(albums, needle) { item, q ->
                item.album.contains(q, ignoreCase = true) || item.artist.contains(q, ignoreCase = true) } }
        }
    }
    Column(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Поиск по библиотеке") },
            singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
        if (!favoritesOnly) FilterChip(selected = favoritesFilter, onClick = { favoritesFilter = !favoritesFilter },
            label = { Text("Избранное") }, modifier = Modifier.padding(horizontal = 16.dp))
        if (selectionMode) Text("Нажми на трек, чтобы добавить его в плейлист", modifier = Modifier.padding(16.dp))
        PrimaryTabRow(selectedTabIndex = tab) {
            (if (selectionMode) listOf("Треки") else listOf("Треки", "Артисты", "Альбомы")).forEachIndexed { index, title ->
                Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title) })
            }
        }
        if (state.libraryLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyVerticalGrid(columns = GridCells.Adaptive(360.dp), modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding())) {
            val empty = when(tab) { 0 -> shownTracks.isEmpty(); 1 -> shownArtists.isEmpty(); else -> shownAlbums.isEmpty() }
            if (empty && !state.libraryLoading) item(span = { GridItemSpan(maxLineSpan) }) { EmptyState(if (favoritesFilter) "В избранном пока пусто" else "Ничего не найдено", "Измени поиск или обнови библиотеку в профиле.") }
            when (tab) {
                0 -> items(shownTracks, key = { it.key }, span = { GridItemSpan(maxLineSpan) }) { t ->
                    TrackRow(t, artworkUrl, t.key in favoriteIds, ratings[t.key], "favorite:${t.key}" in state.busy,
                        hasSession && t.key !in ratingPending,
                        { if (favoritesFilter && !selectionMode) onFavoriteTrack(t.key) else onTrack(t.key) },
                        { onFavorite(t.key) }, { onRate(t.key, it) }, { onAddToPlaylist(t.key) })
                }
                1 -> items(shownArtists, key = { it.artist }) { a ->
                    val cover = artworkUrl(a.artwork ?: a.coverTrackId?.let { "/api/artwork/$it" }, 96)
                    val photo = artworkUrl(state.artistPhotos[artistPhotoKey(a.artist)], 96)
                    RowCard(photo ?: cover, a.artist, "${a.tracks} треков", fallbackArtworkUrl = cover,
                        trailing = { FavoriteButton(a.artist in state.favoriteArtists, "artist:${a.artist}" !in state.busy) { onArtistFavorite(a.artist) } },
                        onClick = { onArtist(a.artist) })
                }
                else -> items(shownAlbums, key = { "${it.artist.length}:${it.artist}${it.album}" }) { a ->
                    RowCard(artworkUrl(a.artwork ?: a.coverTrackId?.let { "/api/artwork/$it" }, 96), a.album, "${a.artist} · ${a.tracks} треков",
                        trailing = { FavoriteButton((a.artist to a.album) in state.favoriteAlbums, "album:${a.artist}/${a.album}" !in state.busy) { onAlbumFavorite(a.artist, a.album) } },
                        onClick = { onAlbum(a.artist, a.album) })
                }
            }
        }
    }
}

@Composable
fun EmptyState(title: String, description: String) {
    Column(Modifier.fillMaxWidth().padding(24.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp))
    }
}
