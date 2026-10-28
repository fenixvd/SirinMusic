package ru.rainedev.sirinmusic.sharing

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import ru.rainedev.sirinmusic.R
import ru.rainedev.sirinmusic.data.Track
import ru.rainedev.sirinmusic.strings
import java.io.File

/** Kept separate from the GitHub updater so sharing is also available in Play builds. */
class TrackShareProvider : FileProvider()

internal fun trackShareText(track: Track): String =
    listOf(track.title, track.artist, track.album)
        .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
        .joinToString("\n")
        .ifEmpty { "—" }

internal fun shareTrackText(context: Context, track: Track) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, trackShareText(track))
    }
    context.startActivity(Intent.createChooser(intent, strings(R.string.track_share_title)))
}

internal fun shareTrackImage(context: Context, track: Track, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.trackshare", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, trackShareText(track))
        clipData = ClipData.newUri(context.contentResolver, strings(R.string.track_share_image), uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, strings(R.string.track_share_title)))
}
