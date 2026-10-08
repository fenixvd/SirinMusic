package ru.rainedev.sirinmusic

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import ru.rainedev.sirinmusic.ui.ColorPicker
import ru.rainedev.sirinmusic.ui.SettingsMenu
import ru.rainedev.sirinmusic.ui.SettingsPage
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import ru.rainedev.sirinmusic.data.*
import ru.rainedev.sirinmusic.ui.theme.SirinMusicTheme

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        val app = application as SirinApp
        setContent {
            val appearance by app.settings.appearance.collectAsStateWithLifecycle()
            SirinMusicTheme(appearance) { SettingsScreen(app, appearance, onBack = ::finish) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(app: SirinApp, appearance: Appearance, onBack: () -> Unit) {
    var pageName by rememberSaveable { mutableStateOf<String?>(null) }
    val page = pageName?.let { SettingsPage.valueOf(it) }
    var url by rememberSaveable { mutableStateOf(app.settings.baseUrl) }
    // Secret stays out of savedInstanceState and screenshots until explicitly revealed.
    var token by remember { mutableStateOf(app.settings.token) }
    var reveal by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var cacheBytes by remember { mutableLongStateOf(0L) }
    var clearing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { cacheBytes = app.imageCache.diskBytes() }
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val wide = maxWidth >= 840.dp
    BackHandler(enabled = !wide && page != null) { pageName = null }
    val activePage = page ?: if (wide) SettingsPage.APPEARANCE else null
    Scaffold(topBar = { TopAppBar(title = { Text(if (wide) "Настройки" else page?.title ?: "Настройки") }, navigationIcon = {
        IconButton(onClick = { if (!wide && page != null) pageName = null else onBack() }) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад")
        }
    }) }, snackbarHost = { SnackbarHost(snack) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
        Row(Modifier.widthIn(max = 1120.dp).fillMaxSize()) {
            if (wide || activePage == null) {
                Column(Modifier.then(if (wide) Modifier.width(320.dp) else Modifier.weight(1f))
                    .verticalScroll(rememberScrollState()).padding(16.dp)) {
                    Text("Настрой приложение под себя", style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 16.dp))
                    SettingsMenu(activePage, { pageName = it.name },
                        onUpdates = { app.startActivity(android.content.Intent(app, UpdatesActivity::class.java)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) },
                        onAbout = { app.startActivity(android.content.Intent(app, AboutActivity::class.java)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) })
                }
            }
            if (activePage != null) key(activePage) {
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).imePadding().padding(24.dp)) {
            if (activePage == SettingsPage.APPEARANCE) {
            Text("Внешний вид", style = MaterialTheme.typography.titleLarge)
            Text("Тема", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleMedium)
            ThemeMode.entries.forEach { mode ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    RadioButton(selected = appearance.theme == mode, onClick = { app.settings.setAppearance(appearance.copy(theme = mode)) })
                    TextButton(onClick = { app.settings.setAppearance(appearance.copy(theme = mode)) }) {
                        Text(when(mode) { ThemeMode.SYSTEM -> "Как в системе"; ThemeMode.LIGHT -> "Светлая"; ThemeMode.DARK -> "Тёмная" })
                    }
                }
            }
            ListItem(headlineContent = { Text("Цвета из обоев") }, supportingContent = {
                Text(if (Build.VERSION.SDK_INT >= 31) "Material You" else "Доступно с Android 12")
            }, trailingContent = { Switch(checked = appearance.dynamicColor && Build.VERSION.SDK_INT >= 31,
                enabled = Build.VERSION.SDK_INT >= 31, onCheckedChange = { app.settings.setAppearance(appearance.copy(dynamicColor = it)) }) })
            if (!appearance.dynamicColor || Build.VERSION.SDK_INT < 31) {
                Text("Палитра", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Palette.entries.forEach { palette ->
                        FilterChip(selected = appearance.palette == palette,
                            onClick = { app.settings.setAppearance(appearance.copy(palette = palette)) },
                            leadingIcon = { Box(Modifier.size(18.dp).background(
                                Color(if (palette == Palette.CUSTOM) appearance.customColor else palette.seed), CircleShape)) },
                            label = { Text(palette.label) })
                    }
                }
                if (appearance.palette == Palette.CUSTOM) ColorPicker(appearance.customColor) {
                    app.settings.setAppearance(appearance.copy(customColor = it))
                }
            }
            }
            if (activePage == SettingsPage.STORAGE) {
            Text("Хранилище", style = MaterialTheme.typography.titleLarge)
            Text("Кеш обложек: ${android.text.format.Formatter.formatShortFileSize(app, cacheBytes)} из 64 МБ",
                Modifier.padding(top = 12.dp))
            Text("Музыка воспроизводится с сервера без сохранения на диск.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(enabled = !clearing, onClick = {
                clearing = true
                scope.launch {
                    try {
                        app.imageCache.clear()
                        cacheBytes = app.imageCache.diskBytes()
                        snack.showSnackbar("Кеш обложек очищен")
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { snack.showSnackbar("Не удалось очистить кеш") }
                    finally { clearing = false }
                }
            }, modifier = Modifier.padding(top = 12.dp)) { Text(if (clearing) "Очищаю…" else "Очистить кеш") }

            }
            if (activePage == SettingsPage.CONNECTION) {
            Text("Подключение", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(url, { url = it; error = null }, label = { Text("Адрес сервера") }, singleLine = true,
                enabled = !checking, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
            OutlinedTextField(token, { token = it; error = null }, label = { Text("API-токен") }, singleLine = true,
                enabled = !checking, visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), trailingIcon = {
                    IconButton(onClick = { reveal = !reveal }) { Icon(if (reveal) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        if (reveal) "Скрыть токен" else "Показать токен") }
                }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
            Text("Новое подключение сохранится после проверки. Воспроизведение при смене сервера остановится.",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
            Button(enabled = !checking, modifier = Modifier.fillMaxWidth().padding(top = 16.dp), onClick = {
                val validation = validateServerUrl(url) ?: if (token.isBlank()) "Введи токен" else null
                if (validation != null) error = validation else {
                    checking = true; error = null
                    scope.launch {
                        try {
                            val draft = ServerConnection(url.trim().trimEnd('/'), token.trim(), app.settings.clientId)
                            check(MusikApi(draft).health().ok) { "Сервер ответил, но не готов" }
                            app.playback.stop(); app.settings.setConnection(draft.baseUrl, draft.token)
                            snack.showSnackbar("Подключение сохранено")
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) { error = e.message ?: "Не удалось подключиться" }
                        finally { checking = false }
                    }
                }
            }) { Text(if (checking) "Проверяю…" else "Проверить и сохранить") }
            }
            }
            }
        }
        }
    }
    }
}
