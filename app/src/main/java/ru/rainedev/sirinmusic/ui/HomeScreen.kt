package ru.rainedev.sirinmusic.ui

import ru.rainedev.sirinmusic.trackCount
import ru.rainedev.sirinmusic.R
import ru.rainedev.sirinmusic.strings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.rainedev.sirinmusic.data.Mix
import ru.rainedev.sirinmusic.data.Track
import ru.rainedev.sirinmusic.data.FavoriteRecommendations

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    mixes: List<Mix>,
    favorites: List<Track>,
    maturity: String?,
    artworkUrl: (String?, Int) -> String?,
    onRadio: () -> Unit,
    onMix: (String) -> Unit,
    onTrack: (Long) -> Unit,
    contentPadding: PaddingValues,
    onFavorites: () -> Unit,
    onShare: () -> Unit, onRefreshMixes: () -> Unit, refreshingMixes: Boolean, sharing: Boolean,
    recommendations: FavoriteRecommendations?,
    recommendationsLoading: Boolean,
    recommendationsError: String?,
    onRefreshRecommendations: () -> Unit,
    onRecommendationTrack: (Long) -> Unit,
    onRecommendationArtist: (String) -> Unit,
    onRecommendationAlbum: (String, String) -> Unit,
    refreshing: Boolean,
    onRefresh: () -> Unit,
) {
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
    LazyVerticalGrid(columns = GridCells.Adaptive(360.dp), modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding())) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Card(modifier = Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Radio, contentDescription = null)
                    Text(strings(R.string.home_screen_1), style = MaterialTheme.typography.headlineMedium)
                    Text(if (maturity == "ready") strings(R.string.home_screen_2) else strings(R.string.home_screen_3),
                        style = MaterialTheme.typography.bodyLarge)
                    Button(onClick = onRadio, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text(strings(R.string.home_screen_4))
                    }
                }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick = onShare, enabled = !sharing, modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
                    Icon(Icons.Rounded.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                    Text(strings(R.string.home_screen_5), modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                }
                FilledTonalButton(onClick = onRefreshMixes, enabled = !refreshingMixes, modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
                    Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                    Text(if (refreshingMixes) strings(R.string.home_screen_6) else strings(R.string.home_screen_7), modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                }
            }
        }
        if (mixes.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle(strings(R.string.home_screen_8)) }
            item(span = { GridItemSpan(maxLineSpan) }) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(mixes, key = { it.kind }) { mix ->
                        MixCard(
                            mix = mix,
                            url = artworkUrl(mix.coverTrackId?.let { "/api/artwork/$it" }, 256),
                            onClick = { onMix(mix.kind) },
                        )
                    }
                }
            }
        }

        if (recommendations != null || recommendationsLoading || recommendationsError != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(strings(R.string.home_screen_9), style = MaterialTheme.typography.titleLarge)
                    val noResults = recommendations?.let { it.tracks.isEmpty() && it.artists.isEmpty() && it.albums.isEmpty() } == true
                    val description = when {
                        recommendationsError != null -> recommendationsError
                        recommendationsLoading && recommendations == null -> strings(R.string.home_screen_10)
                        noResults -> recommendations.hint ?: strings(R.string.home_screen_11)
                        else -> recommendations?.explanation ?: strings(R.string.home_screen_12)
                    }
                    Text(description, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onRefreshRecommendations, enabled = !recommendationsLoading) {
                        Text(if (recommendationsLoading) strings(R.string.home_screen_6) else strings(R.string.home_screen_13))
                    }
                }
            }
            recommendations?.takeIf { !it.empty }?.let { result ->
                if (result.tracks.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle(strings(R.string.home_screen_14)) }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(result.tracks.filter { it.key > 0 && it.ready }.distinctBy { it.key }.take(12), key = { it.key }) { track ->
                                Box(Modifier.width(280.dp)) {
                                    RowCard(artworkUrl(track.artworkPath, 96), track.title ?: "—", track.artist, onClick = { onRecommendationTrack(track.key) })
                                }
                            }
                        }
                    }
                }
                if (result.artists.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle(strings(R.string.home_screen_15)) }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(result.artists.filter { it.artist.isNotBlank() }.distinctBy { it.artist }.take(6), key = { it.artist }) { artist ->
                                Box(Modifier.width(280.dp)) {
                                    RowCard(artworkUrl(artist.artwork ?: artist.coverTrackId?.let { "/api/artwork/$it" }, 96), artist.artist,
                                        trackCount(artist.tracks), onClick = { onRecommendationArtist(artist.artist) })
                                }
                            }
                        }
                    }
                }
                if (result.albums.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle(strings(R.string.home_screen_17)) }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(result.albums.filter { it.album.isNotBlank() }.distinctBy { it.artist to it.album }.take(6), key = { it.artist to it.album }) { album ->
                                Box(Modifier.width(280.dp)) {
                                    RowCard(artworkUrl(album.artwork ?: album.coverTrackId?.let { "/api/artwork/$it" }, 96), album.album,
                                        album.artist, onClick = { onRecommendationAlbum(album.artist, album.album) })
                                }
                            }
                        }
                    }
                }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) { TextButton(onClick = onFavorites, modifier = Modifier.padding(horizontal = 16.dp)) { Text(strings(R.string.home_screen_18)) } }
        if (favorites.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle(strings(R.string.main_activity_8)) }
            gridItems(favorites.take(20), key = { it.key }) { track ->
                RowCard(
                    artworkUrl = artworkUrl(track.artworkPath, 96),
                    title = track.title ?: "—",
                    subtitle = track.artist,
                    onClick = { onTrack(track.key) },
                )
            }
        }
    }
    }
}

@Composable
private fun MixCard(mix: Mix, url: String?, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.width(176.dp), shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column {
            Artwork(url, 176.dp, corner = 0.dp)
            Text(
                mix.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp),
            )
            Row {
                Text(
                    mix.subtitle ?: trackCount(mix.tracks),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                )
            }
        }
    }
}

fun maturityRu(value: String): String = when (value) {
    "discovering" -> strings(R.string.home_screen_19)
    "forming" -> strings(R.string.home_screen_20)
    "ready" -> strings(R.string.home_screen_21)
    else -> value
}
