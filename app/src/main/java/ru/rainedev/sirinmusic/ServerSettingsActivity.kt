package ru.rainedev.sirinmusic

import ru.rainedev.sirinmusic.R
import ru.rainedev.sirinmusic.strings
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.rainedev.sirinmusic.data.*
import ru.rainedev.sirinmusic.ui.theme.SirinMusicTheme

class ServerSettingsActivity : ru.rainedev.sirinmusic.SirinActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // This screen can reveal a server-wide credential; exclude it from screenshots and recents.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val app = application as SirinApp
        setContent {
            val appearance by app.settings.appearance.collectAsStateWithLifecycle()
            SirinMusicTheme(appearance) { ServerSettingsScreen(app, ::finish) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerSettingsScreen(app: SirinApp, onBack: () -> Unit) {
    var reply by remember { mutableStateOf<ServerSettingsReply?>(null) }
    var addr by remember { mutableStateOf("") }
    var publicUrl by remember { mutableStateOf("") }
    var library by remember { mutableStateOf("") }
    var mode by remember { mutableIntStateOf(0) }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("8787") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var reveal by remember { mutableStateOf(false) }
    var ip by remember { mutableStateOf("") }
    var listing by remember { mutableStateOf<ServerDirs?>(null) }
    var restartDialog by remember { mutableStateOf(false) }
    var qr by remember { mutableStateOf<Bitmap?>(null) }
    var qrUrl by remember { mutableStateOf("") }
    fun install(value: ServerSettingsReply) {
        reply = value; addr = value.saved.addr; publicUrl = value.saved.publicUrl; library = value.saved.library
        host = addr.substringBeforeLast(':').removeSurrounding("[", "]")
        port = addr.substringAfterLast(':', "8787")
        mode = when (host) { "", "0.0.0.0", "::" -> if (publicUrl.isBlank()) 0 else 1; "127.0.0.1", "localhost", "::1" -> 2; else -> 3 }
        reveal = false; qr = null
    }
    val action = ru.rainedev.sirinmusic.ui.rememberScreenAction(
        onStart = { busy = true; error = null },
        onError = { e -> error = when ((e as? ApiException)?.code) {
            403 -> strings(R.string.server_settings_activity_1)
            404 -> strings(R.string.server_settings_activity_2)
            else -> e.message ?: strings(R.string.server_settings_activity_3)
        } },
        onFinish = { busy = false },
    )
    fun copy(text: String, secret: Boolean = false) {
        ru.rainedev.sirinmusic.ui.copyText(app, text,
            if (secret) strings(R.string.account_activity_33) else strings(R.string.server_settings_activity_4), secret)
        notice = strings(R.string.account_activity_3)
    }
    LaunchedEffect(Unit) { action { install(app.api.serverSettings()) } }
    Scaffold(topBar = { TopAppBar(title = { Text(strings(R.string.server_settings_activity_5)) }, navigationIcon = {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, strings(R.string.about_activity_2)) }
    }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            notice?.let { Text(it) }
            val data = reply
            if (data == null) {
                Button(onClick = { action { install(app.api.serverSettings()) } }, enabled = !busy) { Text(strings(R.string.server_settings_activity_6)) }
            } else {
                if (data.restartPending) Text(strings(R.string.server_settings_activity_7))
                Button(onClick = { restartDialog = true }, enabled = !busy && data.restartSupported) { Text(strings(R.string.server_settings_activity_8)) }
                if (!data.restartSupported) Text(strings(R.string.server_settings_activity_9))
                Text(strings(R.string.server_settings_activity_10), style = MaterialTheme.typography.titleLarge)
                listOf(strings(R.string.server_settings_activity_11), strings(R.string.server_settings_activity_12), strings(R.string.server_settings_activity_13), strings(R.string.server_settings_activity_14)).forEachIndexed { index, label ->
                    Row { RadioButton(selected = mode == index, enabled = !busy, onClick = { mode = index }); TextButton(onClick = { mode = index }, enabled = !busy) { Text(label) } }
                }
                if (mode == 3) OutlinedTextField(host, { host = it }, label = { Text(strings(R.string.server_settings_activity_15)) }, enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(port, { port = it }, label = { Text(strings(R.string.server_settings_activity_16)) }, enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(publicUrl, { publicUrl = it }, label = { Text(strings(R.string.server_settings_activity_17)) }, enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (mode == 1) Text(strings(R.string.server_settings_activity_18))
                if (!data.phone.authEnabled) Text(strings(R.string.server_settings_activity_19), color = MaterialTheme.colorScheme.error)
                Text(strings(R.string.server_settings_activity_20), style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(library, { library = it }, label = { Text(strings(R.string.server_settings_activity_21)) }, enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = { action { listing = app.api.serverDirs(library) } }, enabled = !busy) { Text(strings(R.string.server_settings_activity_22)) }
                Text(strings(R.string.server_settings_activity_23))
                Button(onClick = { action {
                    val number = port.toIntOrNull(); require(number != null && number in 1..65535) { strings(R.string.server_settings_activity_24) }
                    val listenHost = when (mode) { 0, 1 -> ""; 2 -> "127.0.0.1"; else -> host.trim().also { require(it.isNotEmpty()) { strings(R.string.server_settings_activity_25) } } }
                    val listen = if (listenHost.contains(':')) "[$listenHost]:$number" else "$listenHost:$number"
                    install(app.api.saveServerSettings(ServerValues(listen, publicUrl.trim(), library.trim())))
                    notice = strings(R.string.server_settings_activity_26)
                } }, enabled = !busy) { Text(strings(R.string.account_activity_58)) }
                Text(data.envPath, style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                Text(strings(R.string.server_settings_activity_27), style = MaterialTheme.typography.titleLarge)
                data.phone.urls.forEach { url ->
                    Text(url)
                    Row {
                        TextButton(onClick = { copy(url) }) { Text(strings(R.string.account_activity_36)) }
                        TextButton(onClick = { action {
                            qr = createConnectionQr(url, data.phone.token); qrUrl = url
                        } }, enabled = !busy) { Text(strings(R.string.server_settings_activity_28)) }
                    }
                }
                if (data.phone.urls.isEmpty()) Text(strings(R.string.server_settings_activity_29))
                OutlinedTextField(data.phone.token, {}, readOnly = true, label = { Text(strings(R.string.account_activity_33)) }, visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                Row { TextButton(onClick = { reveal = !reveal }) { Text(if (reveal) strings(R.string.account_activity_34) else strings(R.string.account_activity_35)) }; TextButton(onClick = { copy(data.phone.token, true) }, enabled = data.phone.token.isNotEmpty()) { Text(strings(R.string.account_activity_36)) } }
                Text(strings(R.string.server_settings_activity_30))
                Text(strings(R.string.server_settings_activity_31), style = MaterialTheme.typography.titleLarge)
                if (ip.isNotEmpty()) { Text(ip); TextButton(onClick = { copy(ip) }) { Text(strings(R.string.account_activity_36)) } }
                OutlinedButton(onClick = { action { ip = app.api.serverExternalIp().ip } }, enabled = !busy) { Text(if (ip.isEmpty()) strings(R.string.server_settings_activity_32) else strings(R.string.account_activity_67)) }
                Text(strings(R.string.server_settings_activity_33))
                TextButton(onClick = { action { app.startActivity(Intent(Intent.ACTION_VIEW, app.api.url(data.phone.openapi).toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }, enabled = !busy) { Text(strings(R.string.server_settings_activity_34)) }
            }
        }
    }
    listing?.let { dirs ->
        AlertDialog(onDismissRequest = { listing = null }, title = { Text(strings(R.string.server_settings_activity_35)) }, text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                Text(dirs.path.ifEmpty { strings(R.string.server_settings_activity_36) })
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (dirs.parent.isNotEmpty()) TextButton(onClick = { action { listing = app.api.serverDirs(dirs.parent) } }, enabled = !busy) { Text(strings(R.string.server_settings_activity_37)) }
                (dirs.dirs ?: emptyList()).forEach { dir -> TextButton(onClick = { action { listing = app.api.serverDirs(dir.path) } }, enabled = !busy) { Text(dir.name) } }
            }
        }, confirmButton = { TextButton(onClick = { library = dirs.path; listing = null }, enabled = !busy && dirs.path.isNotEmpty()) { Text(strings(R.string.server_settings_activity_38)) } }, dismissButton = { TextButton(onClick = { listing = null }) { Text(strings(R.string.account_activity_71)) } })
    }
    if (restartDialog) AlertDialog(onDismissRequest = { restartDialog = false }, title = { Text(strings(R.string.server_settings_activity_39)) }, text = { Text(strings(R.string.server_settings_activity_40)) }, confirmButton = {
        TextButton(onClick = { restartDialog = false; action { app.api.restartServer(); notice = strings(R.string.server_settings_activity_41) } }) { Text(strings(R.string.server_settings_activity_42)) }
    }, dismissButton = { TextButton(onClick = { restartDialog = false }) { Text(strings(R.string.account_activity_71)) } })
    qr?.let { bitmap -> AlertDialog(onDismissRequest = { qr = null }, title = { Text(strings(R.string.server_settings_activity_43)) }, text = {
        Column { Text(qrUrl); Image(bitmap.asImageBitmap(), strings(R.string.server_settings_activity_44), Modifier.fillMaxWidth().aspectRatio(1f)) }
    }, confirmButton = { TextButton(onClick = { qr = null }) { Text(strings(R.string.account_activity_75)) } }) }
}
