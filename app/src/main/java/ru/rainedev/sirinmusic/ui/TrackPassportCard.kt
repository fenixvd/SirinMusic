package ru.rainedev.sirinmusic.ui

import ru.rainedev.sirinmusic.R
import ru.rainedev.sirinmusic.strings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import ru.rainedev.sirinmusic.data.*
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** A separate request per track/profile/visit; cancellation and keys prevent stale details. */
@Composable
internal fun TrackPassportCard(api: MusikApi, trackId: Long?, profileKey: String) {
    var passport by remember(trackId, profileKey) { mutableStateOf<TrackPassport?>(null) }
    var expanded by remember(trackId, profileKey) { mutableStateOf(false) }
    LaunchedEffect(api, trackId, profileKey) {
        passport = null
        if (trackId == null || trackId <= 0) return@LaunchedEffect
        try {
            val details = api.trackDetails(trackId)
            if (details.key == trackId) passport = details.passport
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { /* Optional metadata never interrupts playback. */ }
    }
    val p = passport ?: return
    val chips = passportLabels(p, ru.rainedev.sirinmusic.appLocale())
    if (chips.isEmpty() && p.genres.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            chips.forEach { label ->
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
        }
        val genres = p.genres.filter { it.isNotBlank() }.distinct()
        if (genres.isNotEmpty()) {
            Text(if (expanded) genres.joinToString(" · ") else genres.take(3).joinToString(" · "),
                maxLines = if (expanded) Int.MAX_VALUE else 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            if (genres.size > 3 || genres.any { it.length > 30 }) TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) strings(R.string.track_passport_card_1) else strings(R.string.track_passport_card_2))
            }
        }
    }
}

internal fun passportLabels(p: TrackPassport, locale: Locale): List<String> = buildList {
    val number = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1; isGroupingUsed = false }
    p.format?.takeIf { it.isNotBlank() }?.let { add(it.uppercase(Locale.ROOT)) }
    p.bitrate?.takeIf { it > 0 }?.let { add(strings(R.string.track_passport_card_3, number.format(it / 1000.0))) }
    p.sampleRate?.takeIf { it > 0 }?.let { add(strings(R.string.track_passport_card_4, number.format(it / 1000.0))) }
    p.channels?.takeIf { it > 0 }?.let { add(strings(R.string.track_passport_card_5, it)) }
    p.bpm?.takeIf { it.isFinite() && it > 0 }?.let { add("${number.format(it)} BPM") }
    listOfNotNull(p.key?.takeIf { it.isNotBlank() }, p.mode?.takeIf { it.isNotBlank() }).joinToString(" ").takeIf { it.isNotBlank() }?.let { add(it) }
    p.lufs?.takeIf { it.isFinite() }?.let { add("${number.format(it)} LUFS") }
    p.year?.takeIf { it > 0 }?.let { add(number.format(it)) }
    p.duration?.takeIf { it.isFinite() && it > 0 }?.let { add(formatTime(it)) }
    p.plays?.takeIf { it > 0 }?.let { add(strings(R.string.profile_details_49, it)) }
    p.earlySkips?.takeIf { it > 0 }?.let { add(strings(R.string.track_passport_card_6, it)) }
    p.lastPlayedAt?.takeIf { it.isNotBlank() }?.let { value ->
        runCatching { Instant.parse(value).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)) }
            .getOrNull()?.let { add(strings(R.string.track_passport_card_7, it)) }
    }
}
