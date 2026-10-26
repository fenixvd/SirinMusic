package ru.rainedev.sirinmusic.ui

import ru.rainedev.sirinmusic.R
import ru.rainedev.sirinmusic.strings
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

enum class SettingsPage(private val titleRes: Int, private val summaryRes: Int, val icon: ImageVector) {
    APPEARANCE(R.string.settings_navigation_1, R.string.settings_navigation_2, Icons.Rounded.Palette),
    STORAGE(R.string.settings_activity_10, R.string.settings_navigation_3, Icons.Rounded.Storage),
    CONNECTION(R.string.settings_activity_17, R.string.settings_navigation_4, Icons.Rounded.Dns);
    val title: String get() = strings(titleRes)
    val summary: String get() = strings(summaryRes)
}

@Composable
fun SettingsMenu(
    selected: SettingsPage?, onSelect: (SettingsPage) -> Unit,
    onUpdates: () -> Unit, onAbout: () -> Unit, onServer: () -> Unit, onAccount: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsPage.entries.forEach { page ->
            SettingsMenuItem(page.title, page.summary, page.icon, selected == page) { onSelect(page) }
        }
        SettingsMenuItem(strings(R.string.account_activity_4), strings(R.string.settings_navigation_5), Icons.Rounded.ManageAccounts) { onAccount() }
        SettingsMenuItem(strings(R.string.server_settings_activity_5), strings(R.string.settings_navigation_6), Icons.Rounded.Dns) { onServer() }
        if (ru.rainedev.sirinmusic.AppUpdates.available) SettingsMenuItem(strings(R.string.updates_activity_1), strings(R.string.settings_navigation_7), Icons.Rounded.SystemUpdate) { onUpdates() }
        SettingsMenuItem(strings(R.string.about_activity_1), strings(R.string.settings_navigation_8), Icons.Rounded.Info) { onAbout() }
    }
}

@Composable
private fun SettingsMenuItem(
    title: String, summary: String, icon: ImageVector,
    selected: Boolean = false, onClick: () -> Unit,
) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
        ListItem(headlineContent = { Text(title) }, supportingContent = { Text(summary) },
            leadingContent = { Icon(icon, null) },
            trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) },
            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent))
    }
}
