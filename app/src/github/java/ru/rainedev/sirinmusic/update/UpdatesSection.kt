package ru.rainedev.sirinmusic.update

import ru.rainedev.sirinmusic.R
import ru.rainedev.sirinmusic.strings
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File
import kotlinx.coroutines.launch
import ru.rainedev.sirinmusic.BuildConfig
import ru.rainedev.sirinmusic.SirinApp

@Composable
internal fun UpdatesSection(app: SirinApp) {
    val state by app.updates.manager.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var installHint by remember { mutableStateOf<String?>(null) }
    var permissionGranted by remember { mutableStateOf(Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionGranted = it
        if (it) app.updates.manager.notifyAvailable()
    }
    fun launchInstaller(file: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
            context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            installHint = null
        } catch (_: Exception) { installHint = strings(R.string.updates_section_1) }
    }
    val installPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val file = app.updates.manager.state.value.ready
        if (context.packageManager.canRequestPackageInstalls() && file?.exists() == true) launchInstaller(file)
        else installHint = strings(R.string.updates_section_2)
    }
    fun install(file: File) {
        if (context.packageManager.canRequestPackageInstalls()) launchInstaller(file)
        else try {
            installPermission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
        } catch (_: Exception) { installHint = strings(R.string.updates_section_3) }
    }
    Column(Modifier.fillMaxWidth()) {
        Text(strings(R.string.updates_activity_1), style = MaterialTheme.typography.titleLarge)
        Text(strings(R.string.updates_section_4, BuildConfig.VERSION_NAME), Modifier.padding(top = 12.dp))
        Text(strings(R.string.updates_section_5),
            style = MaterialTheme.typography.bodySmall)
        ListItem(headlineContent = { Text(strings(R.string.updates_section_6)) }, supportingContent = {
            Text(strings(R.string.updates_section_7))
        }, trailingContent = { Checkbox(state.onLaunch, onCheckedChange = app.updates.manager::setOnLaunch) })
        ListItem(headlineContent = { Text(strings(R.string.updates_section_8)) }, supportingContent = {
            Text(strings(R.string.updates_section_9))
        }, trailingContent = { Switch(state.automatic, onCheckedChange = app.updates.manager::setAutomatic) })
        if ((state.automatic || state.onLaunch) && !permissionGranted && Build.VERSION.SDK_INT >= 33) {
            TextButton(onClick = { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text(strings(R.string.updates_section_10)) }
        }
        OutlinedButton(enabled = !state.checking && !state.downloading, onClick = {
            installHint = null
            scope.launch { app.updates.manager.check(notify = true) }
        }) { Text(if (state.checking) strings(R.string.settings_activity_23) else strings(R.string.updates_section_11)) }
        if (state.checking) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.available == null && state.checkedAt > 0 && !state.checking && state.error == null) {
            Text(strings(R.string.updates_section_12), style = MaterialTheme.typography.bodyMedium)
        }
        state.available?.let { update ->
            Text(strings(R.string.updates_section_13, update.version), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
            TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(update.page))) }) { Text(strings(R.string.updates_section_14)) }
            if (state.downloading) {
                LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                Text(strings(R.string.updates_section_15, (state.progress * 100).toInt()))
            }
            Button(enabled = !state.downloading && !state.checking, onClick = {
                scope.launch {
                    val file = state.ready?.takeIf { it.exists() } ?: app.updates.manager.download()
                    if (file != null) install(file)
                }
            }) { Text(if (state.ready?.exists() == true) strings(R.string.updates_section_16) else strings(R.string.updates_section_17)) }
        }
        (state.error ?: installHint)?.let { Text(it, color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
    }
}
