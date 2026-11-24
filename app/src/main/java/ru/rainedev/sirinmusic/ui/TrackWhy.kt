package ru.rainedev.sirinmusic.ui

import ru.rainedev.sirinmusic.R
import ru.rainedev.sirinmusic.data.Track
import ru.rainedev.sirinmusic.strings
import androidx.compose.runtime.Composable

fun radioSourceLabelRes(code: String?): Int? = when (code) {
    "exploit" -> R.string.radio_source_exploit
    "transition" -> R.string.radio_source_transition
    "explore", "explore_adjacent" -> R.string.radio_source_explore
    "resurface" -> R.string.radio_source_resurface
    "new_in_library" -> R.string.radio_source_new
    "wildcard" -> R.string.radio_source_wildcard
    "radio_start" -> R.string.radio_source_radio_start
    "refill" -> R.string.radio_source_refill
    "manual" -> R.string.radio_source_manual
    "back" -> R.string.radio_source_back
    else -> null
}

/** Подпись «почему этот трек»: источник очереди, иначе объяснение сервера (имя плейлиста). */
@Composable
fun trackWhyLabel(track: Track?): String? {
    val t = track ?: return null
    return radioSourceLabelRes(t.source)?.let { strings(it) } ?: t.explanation?.takeIf { it.isNotBlank() }
}
