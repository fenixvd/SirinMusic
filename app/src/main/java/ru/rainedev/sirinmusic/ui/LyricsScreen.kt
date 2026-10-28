package ru.rainedev.sirinmusic.ui

import ru.rainedev.sirinmusic.R
import ru.rainedev.sirinmusic.strings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import ru.rainedev.sirinmusic.data.*
import ru.rainedev.sirinmusic.playback.PlayerUi

@Composable
fun LyricsScreen(api: MusikApi, player: PlayerUi, onSeek: (Double) -> Unit, contentPadding: PaddingValues, connectionKey: String) {
    val id = player.current?.key
    var lyrics by remember(id, connectionKey) { mutableStateOf<Lyrics?>(null) }
    var loading by remember(id, connectionKey) { mutableStateOf(id != null) }
    var error by remember(id, connectionKey) { mutableStateOf<String?>(null) }
    var retry by remember(id, connectionKey) { mutableIntStateOf(0) }
    var follow by rememberSaveable { mutableStateOf(true) }
    val list = rememberLazyListState()
    val dragged by list.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(dragged) { if (dragged) follow = false }
    LaunchedEffect(api, connectionKey, id, retry) {
        if (id == null) return@LaunchedEffect
        loading = true; error = null; lyrics = null
        try { lyrics = api.lyrics(id) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: strings(R.string.lyrics_screen_1) }
        finally { loading = false }
    }
    val lines = remember(lyrics) { parseLrc(lyrics?.syncedLyrics.orEmpty()) }
    val active = activeLyricIndex(lines, player.positionSec)
    LaunchedEffect(id, active, follow) {
        if (follow && active >= 0) list.animateScrollToItem(active + 1)
    }
    LazyColumn(state = list, contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(16.dp)) {
                Text(player.current?.title ?: strings(R.string.lyrics_screen_2), style = MaterialTheme.typography.titleLarge)
                player.current?.artist?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (lines.isNotEmpty()) ListItem(headlineContent = { Text(strings(R.string.lyrics_screen_3)) },
                    supportingContent = { Text(strings(R.string.lyrics_screen_4)) },
                    trailingContent = { Switch(follow, onCheckedChange = { follow = it }) })
                if (lyrics?.source?.isNotBlank() == true) Text(strings(R.string.lyrics_screen_5, lyrics!!.source), style = MaterialTheme.typography.labelMedium)
            }
        }
        when {
            id == null -> item { EmptyState(strings(R.string.lyrics_screen_6), strings(R.string.lyrics_screen_7)) }
            loading -> item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            error != null -> item {
                EmptyState(strings(R.string.lyrics_screen_1), error!!)
                TextButton(onClick = { retry++ }, modifier = Modifier.padding(horizontal = 16.dp)) { Text(strings(R.string.lyrics_screen_8)) }
            }
            lyrics?.instrumental == true -> item { EmptyState(strings(R.string.lyrics_screen_9), strings(R.string.lyrics_screen_10)) }
            lines.isNotEmpty() -> itemsIndexed(lines) { index, line ->
                Surface(color = if (index == active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth().clickable { onSeek(line.seconds) }) {
                    Text(line.text.ifBlank { "♪" }, style = MaterialTheme.typography.titleLarge,
                        color = if (index == active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp))
                }
            }
            lyrics?.plainLyrics?.isNotBlank() == true -> item {
                SelectionContainer { Text(lyrics!!.plainLyrics, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(24.dp)) }
            }
            else -> item { EmptyState(strings(R.string.lyrics_screen_11), strings(R.string.lyrics_screen_12)) }
        }
    }
}
