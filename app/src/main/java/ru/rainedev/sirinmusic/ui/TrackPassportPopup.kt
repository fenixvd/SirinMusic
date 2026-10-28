package ru.rainedev.sirinmusic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import ru.rainedev.sirinmusic.R
import ru.rainedev.sirinmusic.strings

@Composable
internal fun TrackPassportPopup(
    track: ru.rainedev.sirinmusic.data.Track,
    api: ru.rainedev.sirinmusic.data.MusikApi,
    profileKey: String,
    onDismiss: () -> Unit,
) {
    Popup(
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        // One dismiss target over the whole player: taps on the card and scrim close it
        // without passing the same tap through to playback controls underneath.
        Box(Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
            .clickable(onClickLabel = strings(R.string.track_passport_close)) { onDismiss() }
            .padding(24.dp), contentAlignment = Alignment.Center) {
            Surface(modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
                Column(Modifier.padding(24.dp)) {
                    Text(strings(R.string.track_passport_title), style = MaterialTheme.typography.headlineSmall)
                    Column(Modifier.padding(top = 16.dp).heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                        Text(track.title ?: "—", style = MaterialTheme.typography.titleMedium)
                        TrackPassportCard(api, track.key, profileKey, interactive = false)
                    }
                }
            }
        }
    }
}
