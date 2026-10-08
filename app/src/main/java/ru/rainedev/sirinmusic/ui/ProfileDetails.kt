package ru.rainedev.sirinmusic.ui

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
    ProfileSection("Сколько нового в радио", Icons.Rounded.Tune) {
        Text("Знакомое — похожее на лайки. Новое — соседнее, забытое и случайное.")
        profile?.exploreRatio?.let { Text("Сейчас нового: ${percent(it)}", style = MaterialTheme.typography.titleMedium) }
        ProfileError(state, "taste")
        val lo = profile?.exploreLo
        val hi = profile?.exploreHi
        if (lo != null && hi != null) {
            var lower by rememberSaveable { mutableFloatStateOf(lo.toFloat()) }
            var upper by rememberSaveable { mutableFloatStateOf(hi.toFloat()) }
            var edited by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(lo, hi) { if (abs(lower.toDouble() - lo) < 0.0001 && abs(upper.toDouble() - hi) < 0.0001) edited = false; if (!edited) { lower = lo.toFloat(); upper = hi.toFloat() } }
            val saving = "explore" in state.busy
            Text("Не меньше ${percent(lower.toDouble())}")
            Slider(value = lower, onValueChange = { lower = it; if (upper < it) upper = it; edited = true },
                valueRange = 0f..0.6f, steps = 11, enabled = !saving)
            Text("Не больше ${percent(upper.toDouble())}")
            Slider(value = upper, onValueChange = { upper = it; if (lower > it) lower = it; edited = true },
                valueRange = 0.1f..0.8f, steps = 13, enabled = !saving)
            Text("Нового будет от ${percent(lower.toDouble())} до ${percent(upper.toDouble())}")
            Button(onClick = { onExplore(lower.toDouble(), upper.toDouble()) }, enabled = !saving) {
                Icon(Icons.Rounded.Save, null); Spacer(Modifier.width(8.dp)); Text(if (saving) "Сохраняем…" else "Сохранить")
            }
        } else if (!state.profileLoading && "taste" !in state.profileErrors) Text("Сервер не передал границы новизны")
        if (!profile?.topArtists.isNullOrEmpty()) {
            Text("Часто играет", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                profile.topArtists.forEach { artist -> SuggestionChip(onClick = {}, label = { Text(artist.artist) }) }
            }
        }
    }
    ProfileSection("Как играет радио", Icons.Rounded.Insights) {
        ProfileError(state, "weekly")
        state.weekly?.let { weekly ->
            Text("За ${weekly.windowDays?.let { "$it дней" } ?: "последний период"}")
            weekly.overall?.let { metrics ->
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    metrics.played?.let { ProfileMetric("Треков", it.toString()) }
                    metrics.finishRate?.let { ProfileMetric("Дослушано", percent(it)) }
                    metrics.skipRate?.let { ProfileMetric("Пропусков", percent(it)) }
                    metrics.uniqueArtists?.let { ProfileMetric("Артистов", it.toString()) }
                }
            }
            val sources = weekly.breakdowns.filter { it.dimension == "source" }
            if (sources.isNotEmpty()) Text("Откуда берутся треки", style = MaterialTheme.typography.titleMedium)
            sources.forEach { source ->
                val name = when (source.value) {
                    "exploit" -> "Похоже на тебя"
                    "radio_start" -> "Старт радио"
                    "transition" -> "Часто после этой"
                    "back" -> "Возврат"
                    else -> source.value ?: "Другой источник"
                }
                Text(name, style = MaterialTheme.typography.titleSmall)
                Text(listOfNotNull(source.played?.let { "$it треков" }, source.finishRate?.let { "${percent(it)} дослушано" },
                    source.skipRate?.let { "${percent(it)} пропусков" }).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                source.finishRate?.let { LinearProgressIndicator(progress = { it.toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth()) }
            }
        }
        ProfileError(state, "policy")
        state.recommendations?.lastPolicy?.policy?.let { policy ->
            policy.exploreShare?.let { share ->
                Text("В последнем наборе нового было ${percent(share)}.")
                if (policy.bounds.size == 2) {
                    Text(if (share in policy.bounds[0]..policy.bounds[1]) "Новизна в заданных границах. Чем больше слушаешь, тем точнее подбор."
                        else "В последнем наборе новизна вышла за заданные границы.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    var createContext by rememberSaveable { mutableStateOf(false) }
    var deleteContext by remember { mutableStateOf<ru.rainedev.sirinmusic.data.TasteContext?>(null) }
    ProfileSection("Настроение", Icons.Rounded.AutoAwesome) {
        Text("Подсказка радио: ночь, дорога, уборка. Можно включить несколько сразу.")
        ProfileError(state, "contexts")
        if (state.contexts.isEmpty() && "contexts" !in state.profileErrors && !state.profileLoading)
            Text("Пока пусто — добавь, если хочешь сменить настроение")
        if (!hasSession && state.contexts.isNotEmpty()) Text("Запусти радио, чтобы включить настроение", style = MaterialTheme.typography.bodySmall)
        state.contexts.forEach { context ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(context.name, style = MaterialTheme.typography.titleMedium)
                    Text(when (context.kind) { "place" -> "Место"; "activity" -> "Занятие"; else -> "Настроение" }, style = MaterialTheme.typography.bodySmall)
                }
                Switch(modifier = Modifier.semantics { contentDescription = context.name }, checked = context.id in contextIds, onCheckedChange = { onToggleContext(context.id, it) },
                    enabled = hasSession && "contexts" !in state.busy)
                IconButton(onClick = { deleteContext = context }, enabled = "contexts" !in state.busy) {
                    Icon(Icons.Rounded.DeleteOutline, "Удалить ${context.name}")
                }
            }
        }
        FilledTonalButton(onClick = { createContext = true }, enabled = "contexts" !in state.busy) {
            Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text("Добавить")
        }
    }
    if (createContext) {
        var name by rememberSaveable { mutableStateOf("") }
        var kind by rememberSaveable { mutableStateOf("mood") }
        AlertDialog(onDismissRequest = { createContext = false }, title = { Text("Добавить настроение") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Название") }, singleLine = true)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("mood" to "Настроение", "place" to "Место", "activity" to "Занятие").forEach { (id, label) ->
                        FilterChip(selected = kind == id, onClick = { kind = id }, label = { Text(label) })
                    }
                }
            } }, confirmButton = { TextButton(onClick = { onCreateContext(name, kind); createContext = false }, enabled = name.isNotBlank()) { Text("Добавить") } },
            dismissButton = { TextButton(onClick = { createContext = false }) { Text("Отмена") } })
    }
    deleteContext?.let { context ->
        AlertDialog(onDismissRequest = { deleteContext = null }, title = { Text("Удалить «${context.name}»?") },
            text = { Text("Настроение будет выключено и удалено из списка.") },
            confirmButton = { TextButton(onClick = { onDeleteContext(context.id); deleteContext = null }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { deleteContext = null }) { Text("Отмена") } })
    }
    ProfileSection("Скрыто", Icons.Rounded.VisibilityOff) {
        Text("Не попадает в радио, миксы, сгенерированные плейлисты и рекомендации, пока запрет не закончится.")
        ProfileError(state, "rules")
        if (state.rules.isEmpty() && "rules" !in state.profileErrors && !state.profileLoading) Text("Ничего не скрыто")
        state.rules.forEach { rule ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    val track = rule.targetKey.toLongOrNull()?.let { id -> state.tracks.firstOrNull { it.key == id } }
                    Text(if (rule.targetType == "track" && track != null) listOfNotNull(track.artist, track.title).joinToString(" — ")
                        else rule.targetKey.replace("|", " — "))
                    Text(listOfNotNull(when (rule.targetType) { "artist" -> "Артист"; "album" -> "Альбом"; else -> "Трек" },
                        rule.remaining?.takeIf { it.isNotBlank() }).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { onRestoreRule(rule.id) }, enabled = "rules" !in state.busy) { Text("Вернуть") }
            }
        }
        OutlinedButton(onClick = { onRestoreRule(null) }, enabled = "rules" !in state.busy && "rules" !in state.profileErrors) { Text("Вернуть последнее") }
    }
    var revokeShare by remember { mutableStateOf<RadioShare?>(null) }
    ProfileSection("Ссылка на радио", Icons.Rounded.Share) {
        Text("Откроется в VLC, браузере или на телефоне. Слушатели не меняют твой вкус.")
        ProfileError(state, "shares")
        if (state.shares.isEmpty() && "shares" !in state.profileErrors && !state.profileLoading) Text("Пока нет ссылок — нажми «Создать»")
        state.shares.forEach { share ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(share.name, style = MaterialTheme.typography.titleMedium)
                Text("Прослушиваний: ${share.listenCount}", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onShare(share) }) { Icon(Icons.Rounded.Share, null); Spacer(Modifier.width(8.dp)); Text("Поделиться") }
                    TextButton(onClick = { revokeShare = share }, enabled = "shares" !in state.busy) { Text("Отозвать") }
                }
            }
        }
        FilledTonalButton(onClick = onCreateShare, enabled = "shares" !in state.busy) { Icon(Icons.Rounded.AddLink, null); Spacer(Modifier.width(8.dp)); Text("Создать ссылку") }
    }
    revokeShare?.let { share ->
        AlertDialog(onDismissRequest = { revokeShare = null }, title = { Text("Отозвать ссылку?") },
            text = { Text("Слушатели больше не смогут открыть радио по этой ссылке.") },
            confirmButton = { TextButton(onClick = { onRevokeShare(share.token); revokeShare = null }) { Text("Отозвать") } },
            dismissButton = { TextButton(onClick = { revokeShare = null }) { Text("Отмена") } })
    }
}

@Composable
private fun ProfileMetric(label: String, value: String) {
    Column(Modifier.widthIn(min = 112.dp).padding(vertical = 4.dp)) {
        Text(value, style = MaterialTheme.typography.headlineSmall)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}
