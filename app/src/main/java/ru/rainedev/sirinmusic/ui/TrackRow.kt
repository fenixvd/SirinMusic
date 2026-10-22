package ru.rainedev.sirinmusic.ui

import ru.rainedev.sirinmusic.R
import ru.rainedev.sirinmusic.strings
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import ru.rainedev.sirinmusic.data.Track

@Composable
fun FavoriteButton(selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(if (selected) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            contentDescription = if (selected) strings(R.string.track_row_1) else strings(R.string.track_row_2),
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun TrackRow(track: Track, artworkUrl: (String?, Int) -> String?, favorite: Boolean,
    rating: String?, favoriteBusy: Boolean, ratingEnabled: Boolean,
    onPlay: () -> Unit, onFavorite: () -> Unit, onRate: (String) -> Unit, onAddToPlaylist: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    RowCard(artworkUrl(track.artworkPath, 96), track.title ?: strings(R.string.track_row_3),
        listOfNotNull(track.artist, track.album, if (!track.ready) strings(R.string.track_row_4) else null).joinToString(" · "),
        trailing = {
            Row {
                FavoriteButton(favorite, !favoriteBusy, onFavorite)
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, strings(R.string.track_row_5)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(if (rating == "like") strings(R.string.track_row_6) else strings(R.string.playback_service_2)) },
                        leadingIcon = { Icon(Icons.Rounded.ThumbUp, null) }, enabled = ratingEnabled && rating != "like",
                        onClick = { menu = false; onRate("like") })
                    DropdownMenuItem(text = { Text(if (rating == "dislike") strings(R.string.track_row_7) else strings(R.string.now_playing_screen_3)) },
                        leadingIcon = { Icon(Icons.Rounded.ThumbDown, null) }, enabled = ratingEnabled && rating != "dislike",
                        onClick = { menu = false; onRate("dislike") })
                    DropdownMenuItem(text = { Text(strings(R.string.now_playing_screen_4)) }, leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null) },
                        onClick = { menu = false; onAddToPlaylist() })
                }
            }
        }, onClick = { if (track.ready) onPlay() })
}
