package ru.rainedev.sirinmusic.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.automirrored.outlined.Subject
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import ru.rainedev.sirinmusic.R
import ru.rainedev.sirinmusic.data.Track
import ru.rainedev.sirinmusic.sharing.createTrackShareCard
import ru.rainedev.sirinmusic.sharing.shareTrackImage
import ru.rainedev.sirinmusic.sharing.shareTrackText
import ru.rainedev.sirinmusic.strings

@Composable
internal fun TrackShareButton(track: Track?, artworkUrl: (String?, Int) -> String?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val shareLabel = strings(R.string.track_share_title)
    var expanded by remember(track?.key) { mutableStateOf(false) }
    var preparing by remember { mutableStateOf(false) }
    fun showError() {
        Toast.makeText(context, strings(R.string.track_share_error), Toast.LENGTH_LONG).show()
    }
    Box {
        IconButton(onClick = { expanded = true }, enabled = track != null && !preparing,
            modifier = Modifier.semantics { contentDescription = shareLabel }) {
            if (preparing) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            else Icon(Icons.Outlined.Share, strings(R.string.track_share_title))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(strings(R.string.track_share_text)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Subject, null) },
                onClick = {
                    expanded = false
                    track?.let {
                        try { shareTrackText(context, it) }
                        catch (_: Exception) { showError() }
                    }
                },
            )
            DropdownMenuItem(
                text = { Text(strings(R.string.track_share_image)) },
                leadingIcon = { Icon(Icons.Outlined.Image, null) },
                onClick = {
                    expanded = false
                    val snapshot = track ?: return@DropdownMenuItem
                    preparing = true
                    scope.launch {
                        try {
                            val file = createTrackShareCard(context, snapshot, artworkUrl(snapshot.artworkPath, 1024))
                            shareTrackImage(context, snapshot, file)
                        } catch (e: CancellationException) { throw e }
                        catch (_: Exception) { showError() }
                        finally { preparing = false }
                    }
                },
            )
            DropdownMenuItem(
                text = {
                    Column {
                        Text(strings(R.string.track_share_link), color = MaterialTheme.colorScheme.onSurface)
                        Text(strings(R.string.track_share_link_soon),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                leadingIcon = { Icon(Icons.Outlined.Link, null) },
                enabled = false,
                onClick = {},
            )
        }
    }
}
