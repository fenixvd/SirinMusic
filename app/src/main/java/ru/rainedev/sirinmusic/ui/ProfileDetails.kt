package ru.rainedev.sirinmusic.ui

import ru.rainedev.sirinmusic.dayCount
import ru.rainedev.sirinmusic.trackCount
import ru.rainedev.sirinmusic.R
import ru.rainedev.sirinmusic.strings
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlin.math.abs
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import ru.rainedev.sirinmusic.AppState
import ru.rainedev.sirinmusic.data.RadioShare

internal fun percent(value: Double): String = "${(value * 100).roundToInt()}%"

@Composable
internal fun ProfileSection(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(icon, null); Text(title, style = MaterialTheme.typography.titleLarge)
            }
            content()
        }
    }
}

@Composable
internal fun ProfileError(state: AppState, key: String) {
    state.profileErrors[key]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
}

@Composable
internal fun ProfileDetails(
    state: AppState, contextIds: List<String>, hasSession: Boolean,
    onExplore: (Double, Double) -> Unit,
    onCreateContext: (String, String) -> Unit, onToggleContext: (String, Boolean) -> Unit,
    onDeleteContext: (String) -> Unit, onRestoreRule: (String?) -> Unit,
    onCreateShare: () -> Unit, onShare: (RadioShare) -> Unit, onRevokeShare: (String) -> Unit,
) {
    val profile = state.profile
    ProfileSection(strings(R.string.profile_details_1), Icons.Rounded.Tune) {
        Text(strings(R.string.profile_details_2))
        profile?.exploreRatio?.let { Text(strings(R.string.profile_details_3, percent(it)), style = MaterialTheme.typography.titleMedium) }
        ProfileError(state, "taste")
        val lo = profile?.exploreLo
        val hi = profile?.exploreHi
        if (lo != null && hi != null) {
            var lower by rememberSaveable { mutableFloatStateOf(lo.toFloat()) }
            var upper by rememberSaveable { mutableFloatStateOf(hi.toFloat()) }
            var edited by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(lo, hi) { if (abs(lower.toDouble() - lo) < 0.0001 && abs(upper.toDouble() - hi) < 0.0001) edited = false; if (!edited) { lower = lo.toFloat(); upper = hi.toFloat() } }
            val saving = "explore" in state.busy
            Text(strings(R.string.profile_details_4, percent(lower.toDouble())))
            Slider(value = lower, onValueChange = { lower = it; if (upper < it) upper = it; edited = true },
                valueRange = 0f..0.6f, steps = 11, enabled = !saving)
            Text(strings(R.string.profile_details_5, percent(upper.toDouble())))
            Slider(value = upper, onValueChange = { upper = it; if (lower > it) lower = it; edited = true },
                valueRange = 0.1f..0.8f, steps = 13, enabled = !saving)
            Text(strings(R.string.profile_details_6, percent(lower.toDouble()), percent(upper.toDouble())))
            Button(onClick = { onExplore(lower.toDouble(), upper.toDouble()) }, enabled = !saving) {
                Icon(Icons.Rounded.Save, null); Spacer(Modifier.width(8.dp)); Text(if (saving) strings(R.string.profile_details_7) else strings(R.string.account_activity_58))
            }
        } else if (!state.profileLoading && "taste" !in state.profileErrors) Text(strings(R.string.profile_details_8))
        if (!profile?.topArtists.isNullOrEmpty()) {
            Text(strings(R.string.profile_details_9), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                profile.topArtists.forEach { artist -> SuggestionChip(onClick = {}, label = { Text(artist.artist) }) }
            }
        }
    }
    ProfileSection(strings(R.string.profile_details_10), Icons.Rounded.Insights) {
        ProfileError(state, "weekly")
        state.weekly?.let { weekly ->
            Text(strings(R.string.profile_details_13, weekly.windowDays?.let { dayCount(it) } ?: strings(R.string.profile_details_12)))
            weekly.overall?.let { metrics ->
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    metrics.played?.let { ProfileMetric(strings(R.string.profile_details_14), it.toString()) }
                    metrics.finishRate?.let { ProfileMetric(strings(R.string.profile_details_15), percent(it)) }
                    metrics.skipRate?.let { ProfileMetric(strings(R.string.profile_details_16), percent(it)) }
                    metrics.uniqueArtists?.let { ProfileMetric(strings(R.string.profile_details_17), it.toString()) }
                }
            }
            val sources = weekly.breakdowns.filter { it.dimension == "source" }
            if (sources.isNotEmpty()) Text(strings(R.string.profile_details_18), style = MaterialTheme.typography.titleMedium)
            sources.forEach { source ->
                val name = when (source.value) {
                    "exploit" -> strings(R.string.profile_details_19)
                    "radio_start" -> strings(R.string.profile_details_20)
                    "transition" -> strings(R.string.profile_details_21)
                    "back" -> strings(R.string.profile_details_22)
                    else -> source.value ?: strings(R.string.profile_details_23)
                }
                Text(name, style = MaterialTheme.typography.titleSmall)
                Text(listOfNotNull(source.played?.let { trackCount(it) }, source.finishRate?.let { strings(R.string.profile_details_24, percent(it)) },
                    source.skipRate?.let { strings(R.string.profile_details_25, percent(it)) }).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                source.finishRate?.let { LinearProgressIndicator(progress = { it.toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth()) }
            }
        }
        ProfileError(state, "policy")
        state.recommendations?.lastPolicy?.policy?.let { policy ->
            policy.exploreShare?.let { share ->
                Text(strings(R.string.profile_details_26, percent(share)))
                if (policy.bounds.size == 2) {
                    Text(if (share in policy.bounds[0]..policy.bounds[1]) strings(R.string.profile_details_27)
                        else strings(R.string.profile_details_28), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    var createContext by rememberSaveable { mutableStateOf(false) }
    var createContextRevision by rememberSaveable { mutableLongStateOf(0L) }
    LaunchedEffect(state.profileState.contextCreated) {
        if (createContext && state.profileState.contextCreated != createContextRevision) createContext = false
    }
    var deleteContext by remember { mutableStateOf<ru.rainedev.sirinmusic.data.TasteContext?>(null) }
    ProfileSection(strings(R.string.profile_details_29), Icons.Rounded.AutoAwesome) {
        Text(strings(R.string.profile_details_30))
        ProfileError(state, "contexts")
        if (state.contexts.isEmpty() && "contexts" !in state.profileErrors && !state.profileLoading)
            Text(strings(R.string.profile_details_31))
        if (!hasSession && state.contexts.isNotEmpty()) Text(strings(R.string.profile_details_32), style = MaterialTheme.typography.bodySmall)
        state.contexts.forEach { context ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(context.name, style = MaterialTheme.typography.titleMedium)
                    Text(when (context.kind) { "place" -> strings(R.string.profile_details_33); "activity" -> strings(R.string.profile_details_34); else -> strings(R.string.profile_details_29) }, style = MaterialTheme.typography.bodySmall)
                }
                Switch(modifier = Modifier.semantics { contentDescription = context.name }, checked = context.id in contextIds, onCheckedChange = { onToggleContext(context.id, it) },
                    enabled = hasSession && "contexts" !in state.busy)
                IconButton(onClick = { deleteContext = context }, enabled = "contexts" !in state.busy) {
                    Icon(Icons.Rounded.DeleteOutline, strings(R.string.profile_details_35, context.name))
                }
            }
        }
        FilledTonalButton(onClick = { createContextRevision = state.profileState.contextCreated; createContext = true }, enabled = "contexts" !in state.busy) {
            Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text(strings(R.string.playlists_screen_32))
        }
    }
    if (createContext) {
        var name by rememberSaveable { mutableStateOf("") }
        var kind by rememberSaveable { mutableStateOf("mood") }
        val saving = "contexts" in state.busy
        AlertDialog(onDismissRequest = { if (!saving) createContext = false }, title = { Text(strings(R.string.profile_details_36)) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, enabled = !saving, label = { Text(strings(R.string.playlists_screen_27)) }, singleLine = true)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("mood" to strings(R.string.profile_details_29), "place" to strings(R.string.profile_details_33), "activity" to strings(R.string.profile_details_34)).forEach { (id, label) ->
                        FilterChip(selected = kind == id, onClick = { kind = id }, enabled = !saving, label = { Text(label) })
                    }
                }
                ProfileError(state, "contexts")
                if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
            } }, confirmButton = { TextButton(onClick = { onCreateContext(name, kind) }, enabled = !saving && name.isNotBlank()) { Text(strings(R.string.playlists_screen_32)) } },
            dismissButton = { TextButton(onClick = { createContext = false }, enabled = !saving) { Text(strings(R.string.account_activity_71)) } })
    }
    deleteContext?.let { context ->
        AlertDialog(onDismissRequest = { deleteContext = null }, title = { Text(strings(R.string.playlists_screen_22, context.name)) },
            text = { Text(strings(R.string.profile_details_37)) },
            confirmButton = { TextButton(onClick = { onDeleteContext(context.id); deleteContext = null }) { Text(strings(R.string.playlists_screen_24)) } },
            dismissButton = { TextButton(onClick = { deleteContext = null }) { Text(strings(R.string.account_activity_71)) } })
    }
    ProfileSection(strings(R.string.profile_details_38), Icons.Rounded.VisibilityOff) {
        Text(strings(R.string.profile_details_39))
        ProfileError(state, "rules")
        if (state.rules.isEmpty() && "rules" !in state.profileErrors && !state.profileLoading) Text(strings(R.string.profile_details_40))
        state.rules.forEach { rule ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    val track = rule.targetKey.toLongOrNull()?.let { id -> state.tracks.firstOrNull { it.key == id } }
                    Text(if (rule.targetType == "track" && track != null) listOfNotNull(track.artist, track.title).joinToString(" — ")
                        else rule.targetKey.replace("|", " — "))
                    Text(listOfNotNull(when (rule.targetType) { "artist" -> strings(R.string.profile_details_41); "album" -> strings(R.string.profile_details_42); else -> strings(R.string.profile_details_43) },
                        rule.remaining?.takeIf { it.isNotBlank() }).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { onRestoreRule(rule.id) }, enabled = "rules" !in state.busy) { Text(strings(R.string.profile_details_44)) }
            }
        }
        OutlinedButton(onClick = { onRestoreRule(null) }, enabled = "rules" !in state.busy && "rules" !in state.profileErrors) { Text(strings(R.string.profile_details_45)) }
    }
    var revokeShare by remember { mutableStateOf<RadioShare?>(null) }
    ProfileSection(strings(R.string.profile_details_46), Icons.Rounded.Share) {
        Text(strings(R.string.profile_details_47))
        ProfileError(state, "shares")
        if (state.shares.isEmpty() && "shares" !in state.profileErrors && !state.profileLoading) Text(strings(R.string.profile_details_48))
        state.shares.forEach { share ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(share.name, style = MaterialTheme.typography.titleMedium)
                Text(strings(R.string.profile_details_49, share.listenCount), style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onShare(share) }) { Icon(Icons.Rounded.Share, null); Spacer(Modifier.width(8.dp)); Text(strings(R.string.home_screen_5)) }
                    TextButton(onClick = { revokeShare = share }, enabled = "shares" !in state.busy) { Text(strings(R.string.account_activity_46)) }
                }
            }
        }
        FilledTonalButton(onClick = onCreateShare, enabled = "shares" !in state.busy) { Icon(Icons.Rounded.AddLink, null); Spacer(Modifier.width(8.dp)); Text(strings(R.string.profile_details_50)) }
    }
    revokeShare?.let { share ->
        AlertDialog(onDismissRequest = { revokeShare = null }, title = { Text(strings(R.string.profile_details_51)) },
            text = { Text(strings(R.string.profile_details_52)) },
            confirmButton = { TextButton(onClick = { onRevokeShare(share.token); revokeShare = null }) { Text(strings(R.string.account_activity_46)) } },
            dismissButton = { TextButton(onClick = { revokeShare = null }) { Text(strings(R.string.account_activity_71)) } })
    }
}

@Composable
private fun ProfileMetric(label: String, value: String) {
    Column(Modifier.widthIn(min = 112.dp).padding(vertical = 4.dp)) {
        Text(value, style = MaterialTheme.typography.headlineSmall)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}
