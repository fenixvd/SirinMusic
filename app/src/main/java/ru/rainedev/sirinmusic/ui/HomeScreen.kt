package ru.rainedev.sirinmusic.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.rainedev.sirinmusic.data.Mix
import ru.rainedev.sirinmusic.data.Track

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
) {
    LazyVerticalGrid(columns = GridCells.Adaptive(360.dp), contentPadding = contentPadding) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Card(modifier = Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Radio, contentDescription = null)
                    Text("Музыка для тебя", style = MaterialTheme.typography.headlineMedium)
                    Text(if (maturity == "ready") "Радио знает твой вкус" else "Любимые треки и новые открытия в одном потоке",
                        style = MaterialTheme.typography.bodyLarge)
                    Button(onClick = onRadio, modifier = Modifier.padding(top = 8.dp)) {
                        Text("Запустить радио")
                    }
                }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick = onShare, enabled = !sharing) {
                    Icon(Icons.Rounded.Share, null); Spacer(Modifier.width(8.dp)); Text("Поделиться")
                }
                FilledTonalButton(onClick = onRefreshMixes, enabled = !refreshingMixes) {
                    Icon(Icons.Rounded.Refresh, null); Spacer(Modifier.width(8.dp)); Text(if (refreshingMixes) "Обновляем…" else "Обновить миксы")
                }
            }
        }
        if (mixes.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("Подборки") }
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

        item(span = { GridItemSpan(maxLineSpan) }) { TextButton(onClick = onFavorites, modifier = Modifier.padding(horizontal = 16.dp)) { Text("Всё избранное") } }
        if (favorites.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("Избранное") }
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
                    mix.subtitle ?: "${mix.tracks} треков",
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
    "discovering" -> "знакомится"
    "forming" -> "складывается"
    "ready" -> "сложился"
    else -> value
}
