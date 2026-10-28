package ru.rainedev.sirinmusic.ui

import ru.rainedev.sirinmusic.R
import ru.rainedev.sirinmusic.strings
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.rainedev.sirinmusic.playback.PlayerUi

/** Мини-плеер над нижней навигацией — как у любого телефонного плеера. */
@Composable
fun MiniPlayer(
    ui: PlayerUi,
    artworkUrl: (String?, Int) -> String?,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onSkip: () -> Unit,
) {
    val track = ui.current ?: return
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        androidx.compose.foundation.layout.Column {
            if (ui.durationSec > 0) {
                LinearProgressIndicator(
                    progress = { (ui.positionSec / ui.durationSec).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(onClick = onOpen, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Artwork(artworkUrl(track.artworkPath, 96), 44.dp, corner = 8.dp)
                        TwoLine(
                            title = track.title ?: "—",
                            subtitle = track.artist,
                            modifier = Modifier
                                .padding(start = 10.dp)
                                .weight(1f),
                        )
                    }
                }
                IconButton(onClick = onToggle, enabled = !ui.busy || ui.isPlaying) {
                    Icon(
                        if (ui.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (ui.isPlaying) strings(R.string.mini_player_1) else strings(R.string.mini_player_2),
                    )
                }
                IconButton(onClick = onSkip, enabled = !ui.busy) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = strings(R.string.mini_player_3))
                }
            }
        }
    }
}
