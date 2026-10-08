package ru.rainedev.sirinmusic.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.rainedev.sirinmusic.data.Profile
import ru.rainedev.sirinmusic.data.RadioShare
import ru.rainedev.sirinmusic.AppState

@Composable
fun ProfileScreen(
    serverUrl: String, serverTracks: Int, serverVersion: String?, maturity: String?,
    profile: Profile?, favorites: Int, onRefresh: () -> Unit, onLogout: () -> Unit,
    contentPadding: PaddingValues, onFavorites: () -> Unit,
    state: AppState, contextIds: List<String>, hasSession: Boolean,
    onExplore: (Double, Double) -> Unit, onCreateContext: (String, String) -> Unit,
    onToggleContext: (String, Boolean) -> Unit, onDeleteContext: (String) -> Unit,
    onRestoreRule: (String?) -> Unit, onCreateShare: () -> Unit,
    onShare: (RadioShare) -> Unit, onRevokeShare: (String) -> Unit,
) {
    var confirmLogout by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(contentPadding).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Radio, null)
                Text("Твой музыкальный вкус", style = MaterialTheme.typography.headlineSmall)
                val currentMaturity = profile?.maturity ?: maturity
                Text(when (currentMaturity) {
                    "ready" -> "Радио знает твой вкус"
                    "forming" -> "Вкус обретает форму"
                    "discovering" -> "Радио знакомится с тобой"
                    else -> "Слушай и отмечай любимую музыку"
                }, style = MaterialTheme.typography.titleMedium)
                Text("Лайки и дослушивания помогают радио выбирать музыку для тебя.",
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (state.profileLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        ProfileError(state, "taste")
        Text("Твой вкус", style = MaterialTheme.typography.titleLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            profile?.likes?.let { ProfileStat("Лайков", it) }
            profile?.skips?.let { ProfileStat("Пропусков", it) }
            profile?.plays?.let { ProfileStat("Прослушано", it) }
            ProfileStat("В избранном", favorites)
        }
        FilledTonalButton(onClick = onFavorites, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Favorite, null); Spacer(Modifier.width(8.dp)); Text("Открыть избранное")
        }
        ProfileDetails(state, contextIds, hasSession, onExplore, onCreateContext, onToggleContext,
            onDeleteContext, onRestoreRule, onCreateShare, onShare, onRevokeShare)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Dns, null); Text("Музыкальный сервер", style = MaterialTheme.typography.titleMedium)
                }
                Text(serverUrl, style = MaterialTheme.typography.bodyMedium)
                Text("${serverTracks} треков · версия ${serverVersion ?: "не указана"}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        OutlinedButton(onClick = onRefresh, enabled = !state.profileLoading, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Refresh, null); Spacer(Modifier.width(8.dp))
            Text("Обновить данные")
        }
        TextButton(onClick = { confirmLogout = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.AutoMirrored.Rounded.ExitToApp, null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(8.dp)); Text("Выйти из профиля", color = MaterialTheme.colorScheme.error)
        }
    }
    if (confirmLogout) AlertDialog(onDismissRequest = { confirmLogout = false },
        title = { Text("Выйти из профиля?") },
        text = { Text("Воспроизведение остановится, а сохранённый токен будет удалён. Избранное и история останутся на сервере.") },
        confirmButton = { TextButton(onClick = { confirmLogout = false; onLogout() }) { Text("Выйти") } },
        dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Отмена") } })
}

@Composable
private fun ProfileStat(label: String, value: Int) {
    Card(Modifier.widthIn(min = 140.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value.toString(), style = MaterialTheme.typography.headlineMedium)
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
