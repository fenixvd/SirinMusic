package ru.rainedev.sirinmusic.ui

import ru.rainedev.sirinmusic.trackCount
import ru.rainedev.sirinmusic.R
import ru.rainedev.sirinmusic.strings
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
    val context = androidx.compose.ui.platform.LocalContext.current
    var confirmLogout by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(contentPadding).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Radio, null)
                Text(strings(R.string.profile_screen_1), style = MaterialTheme.typography.headlineSmall)
                val currentMaturity = profile?.maturity ?: maturity
                Text(when (currentMaturity) {
                    "ready" -> strings(R.string.home_screen_2)
                    "forming" -> strings(R.string.profile_screen_2)
                    "discovering" -> strings(R.string.profile_screen_3)
                    else -> strings(R.string.profile_screen_4)
                }, style = MaterialTheme.typography.titleMedium)
                Text(strings(R.string.profile_screen_5),
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (state.profileLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        ProfileError(state, "taste")
        Text(strings(R.string.profile_screen_6), style = MaterialTheme.typography.titleLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            profile?.likes?.let { ProfileStat(strings(R.string.profile_screen_7), it) }
            profile?.skips?.let { ProfileStat(strings(R.string.profile_details_16), it) }
            profile?.plays?.let { ProfileStat(strings(R.string.profile_screen_8), it) }
            ProfileStat(strings(R.string.profile_screen_9), favorites)
        }
        FilledTonalButton(onClick = onFavorites, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Favorite, null); Spacer(Modifier.width(8.dp)); Text(strings(R.string.profile_screen_10))
        }
        OutlinedButton(onClick = { context.startActivity(android.content.Intent(context, ru.rainedev.sirinmusic.AccountActivity::class.java)) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.ManageAccounts, null); Spacer(Modifier.width(8.dp)); Text(strings(R.string.account_activity_4))
        }
        OutlinedButton(onClick = { context.startActivity(android.content.Intent(context, ru.rainedev.sirinmusic.ServerSettingsActivity::class.java)) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Dns, null); Spacer(Modifier.width(8.dp)); Text(strings(R.string.server_settings_activity_5))
        }
        ProfileDetails(state, contextIds, hasSession, onExplore, onCreateContext, onToggleContext,
            onDeleteContext, onRestoreRule, onCreateShare, onShare, onRevokeShare)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Dns, null); Text(strings(R.string.profile_screen_11), style = MaterialTheme.typography.titleMedium)
                }
                Text(serverUrl, style = MaterialTheme.typography.bodyMedium)
                Text(strings(R.string.profile_screen_13, trackCount(serverTracks), serverVersion ?: strings(R.string.profile_screen_12)),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        OutlinedButton(onClick = onRefresh, enabled = !state.profileLoading, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Refresh, null); Spacer(Modifier.width(8.dp))
            Text(strings(R.string.profile_screen_14))
        }
        TextButton(onClick = { confirmLogout = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.AutoMirrored.Rounded.ExitToApp, null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(8.dp)); Text(strings(R.string.profile_screen_15), color = MaterialTheme.colorScheme.error)
        }
    }
    if (confirmLogout) AlertDialog(onDismissRequest = { confirmLogout = false },
        title = { Text(strings(R.string.profile_screen_16)) },
        text = { Text(strings(R.string.profile_screen_17)) },
        confirmButton = { TextButton(onClick = { confirmLogout = false; onLogout() }) { Text(strings(R.string.profile_screen_18)) } },
        dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text(strings(R.string.account_activity_71)) } })
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
