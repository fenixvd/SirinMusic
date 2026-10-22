package ru.rainedev.sirinmusic.ui

import ru.rainedev.sirinmusic.trackCount
import ru.rainedev.sirinmusic.R
import ru.rainedev.sirinmusic.strings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.rainedev.sirinmusic.AppState
import ru.rainedev.sirinmusic.data.Playlist

@Composable
fun PlaylistsScreen(state: AppState, artworkUrl: (String?, Int) -> String?, onOpen: (Long) -> Unit,
    onCreate: (String, String) -> Unit, onRefresh: () -> Unit, contentPadding: PaddingValues) {
    var creating by rememberSaveable { mutableStateOf(false) }
    LazyVerticalGrid(columns = GridCells.Adaptive(360.dp), contentPadding = contentPadding) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Button(onClick = { creating = true }, enabled = "playlist-mutation" !in state.busy) {
                    Icon(Icons.Rounded.Add, null); Text(strings(R.string.playlists_screen_1), Modifier.padding(start = 8.dp))
                }
                IconButton(onClick = onRefresh, enabled = !state.playlistsLoading) { Icon(Icons.Rounded.Refresh, strings(R.string.playlists_screen_2)) }
            }
        }
        if (state.playlistsLoading) item(span = { GridItemSpan(maxLineSpan) }) { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (state.playlists.isEmpty() && !state.playlistsLoading) item(span = { GridItemSpan(maxLineSpan) }) { EmptyState(strings(R.string.playlists_screen_3), strings(R.string.playlists_screen_4)) }
        gridItems(state.playlists, key = { it.id }) { p ->
            RowCard(artworkUrl(p.coverArtwork ?: p.coverTrackId?.let { "/api/artwork/$it" }, 96), p.name,
                strings(R.string.playlists_screen_8, trackCount(p.trackCount), when(p.type) { "smart" -> strings(R.string.playlists_screen_5); "generated" -> strings(R.string.playlists_screen_6); else -> strings(R.string.playlists_screen_7) }),
                onClick = { onOpen(p.id) })
        }
    }
    if (creating) PlaylistEditor(null, { creating = false }) { name, description -> creating = false; onCreate(name, description) }
}

@Composable
fun PlaylistDetailScreen(state: AppState, artworkUrl: (String?, Int) -> String?, onPlay: (Long, Int) -> Unit,
    onEdit: (Playlist, String, String) -> Unit, onDelete: (Long) -> Unit, onRemove: (Long, String) -> Unit,
    onMove: (Playlist, Int, Int) -> Unit, onAdd: () -> Unit, onRetry: () -> Unit, contentPadding: PaddingValues) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    val p = state.playlist
    val busy = "playlist-mutation" in state.busy
    LazyColumn(contentPadding = contentPadding) {
        if (state.playlistLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (p == null && !state.playlistLoading) item {
            EmptyState(strings(R.string.playlists_screen_9), strings(R.string.playlists_screen_10))
            TextButton(onClick = onRetry, modifier = Modifier.padding(horizontal = 16.dp)) { Text(strings(R.string.lyrics_screen_8)) }
        }
        if (p != null) {
            item {
                Column(Modifier.padding(16.dp)) {
                    Text(p.name, style = MaterialTheme.typography.headlineSmall)
                    if (p.description.isNotBlank()) Text(p.description, Modifier.padding(top = 8.dp))
                    Text(trackCount(p.trackCount), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        Button(onClick = { onPlay(p.id, 0) }, enabled = p.tracks.any { it.trackId > 0 && !it.unresolved }) {
                            Icon(Icons.Rounded.PlayArrow, null); Text(strings(R.string.playlists_screen_11))
                        }
                        if (p.editable) IconButton(onClick = onAdd, enabled = !busy) { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, strings(R.string.main_activity_9)) }
                        IconButton(onClick = { editing = true }, enabled = !busy) { Icon(Icons.Rounded.Edit, strings(R.string.playlists_screen_12)) }
                        IconButton(onClick = { deleting = true }, enabled = !busy) { Icon(Icons.Rounded.DeleteOutline, strings(R.string.playlists_screen_13)) }
                    }
                }
            }
            if (p.tracks.isEmpty()) item { EmptyState(strings(R.string.playlists_screen_14), strings(R.string.playlists_screen_15)) }
            itemsIndexed(p.tracks, key = { _, t -> t.itemId }) { index, item ->
                var menu by remember { mutableStateOf(false) }
                val t = item.asTrack()
                RowCard(if (t.ready) artworkUrl(t.artworkPath, 96) else null, t.title ?: strings(R.string.playlists_screen_16),
                    listOfNotNull(t.artist, if (!t.ready) strings(R.string.playlists_screen_17) else null).joinToString(" · "),
                    trailing = if (p.editable) ({
                        IconButton(onClick = { menu = true }, enabled = !busy) { Icon(Icons.Rounded.MoreVert, strings(R.string.playlists_screen_18)) }
                        DropdownMenu(menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(strings(R.string.playlists_screen_19)) }, enabled = index > 0, onClick = { menu = false; onMove(p, index, -1) })
                            DropdownMenuItem(text = { Text(strings(R.string.playlists_screen_20)) }, enabled = index < p.tracks.lastIndex, onClick = { menu = false; onMove(p, index, 1) })
                            DropdownMenuItem(text = { Text(strings(R.string.playlists_screen_21)) }, onClick = { menu = false; onRemove(p.id, item.itemId) })
                        }
                    }) else null,
                    // Server indexes only resolved tracks. Imported unresolved rows cannot shift playback.
                    onClick = { if (t.ready) onPlay(p.id, p.tracks.take(index).count { it.trackId > 0 }) })
            }
        }
    }
    if (editing && p != null) PlaylistEditor(p, { editing = false }) { name, description -> editing = false; onEdit(p, name, description) }
    if (deleting && p != null) AlertDialog(onDismissRequest = { deleting = false }, title = { Text(strings(R.string.playlists_screen_22, p.name)) },
        text = { Text(strings(R.string.playlists_screen_23)) },
        confirmButton = { TextButton(onClick = { deleting = false; onDelete(p.id) }) { Text(strings(R.string.playlists_screen_24)) } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text(strings(R.string.account_activity_71)) } })
}

@Composable
private fun PlaylistEditor(playlist: Playlist?, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(playlist?.name.orEmpty()) }
    var description by rememberSaveable { mutableStateOf(playlist?.description.orEmpty()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (playlist == null) strings(R.string.playlists_screen_25) else strings(R.string.playlists_screen_26)) },
        text = { Column {
            OutlinedTextField(name, { name = it }, label = { Text(strings(R.string.playlists_screen_27)) }, singleLine = true)
            OutlinedTextField(description, { description = it }, label = { Text(strings(R.string.playlists_screen_28)) }, maxLines = 4, modifier = Modifier.padding(top = 8.dp))
        } }, confirmButton = { TextButton(onClick = { onSave(name, description) }, enabled = name.isNotBlank()) { Text(strings(R.string.account_activity_58)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings(R.string.account_activity_71)) } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(playlists: List<Playlist>, busy: Boolean, onSelect: (Long) -> Unit, onCreate: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { SectionTitle(strings(R.string.playlists_screen_29)) }
            if (playlists.none { it.editable }) item { EmptyState(strings(R.string.playlists_screen_30), strings(R.string.playlists_screen_31)) }
            items(playlists.filter { it.editable }, key = { it.id }) { p ->
                ListItem(headlineContent = { Text(p.name) }, supportingContent = { Text(trackCount(p.trackCount)) },
                    trailingContent = { TextButton(onClick = { onSelect(p.id) }, enabled = !busy) { Text(strings(R.string.playlists_screen_32)) } })
            }
            item { TextButton(onClick = onCreate, enabled = !busy, modifier = Modifier.padding(horizontal = 16.dp)) { Text(strings(R.string.playlists_screen_33)) } }
        }
    }
}
