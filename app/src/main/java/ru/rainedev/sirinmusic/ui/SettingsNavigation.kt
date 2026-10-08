package ru.rainedev.sirinmusic.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

enum class SettingsPage(val title: String, val summary: String, val icon: ImageVector) {
    APPEARANCE("Оформление", "Тема, палитры и свой цвет", Icons.Rounded.Palette),
    STORAGE("Хранилище", "Обложки и очистка кеша", Icons.Rounded.Storage),
    CONNECTION("Подключение", "Адрес сервера и учётные данные", Icons.Rounded.Dns),
}

@Composable
fun SettingsMenu(
    selected: SettingsPage?, onSelect: (SettingsPage) -> Unit,
    onUpdates: () -> Unit, onAbout: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsPage.entries.forEach { page ->
            SettingsMenuItem(page.title, page.summary, page.icon, selected == page) { onSelect(page) }
        }
        SettingsMenuItem("Обновления", "Версия приложения и проверки обновлений", Icons.Rounded.SystemUpdate) { onUpdates() }
        SettingsMenuItem("О приложении", "Sirin Music и разработчик", Icons.Rounded.Info) { onAbout() }
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
