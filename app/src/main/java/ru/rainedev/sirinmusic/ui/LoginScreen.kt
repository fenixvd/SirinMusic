package ru.rainedev.sirinmusic.ui

import ru.rainedev.sirinmusic.R
import ru.rainedev.sirinmusic.strings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.TextButton
import ru.rainedev.sirinmusic.data.AccountProfile
import androidx.compose.material3.Surface
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import ru.rainedev.sirinmusic.data.ConnectLink

@Composable
fun LoginScreen(
    initialUrl: String,
    initialToken: String,
    checking: Boolean,
    error: String?,
    onSubmit: (String, String) -> Unit,
    pendingLink: ConnectLink? = null,
    consumeLink: () -> ConnectLink? = { null },
    onPasswordSubmit: (String, String, String) -> Unit,
    profiles: List<AccountProfile> = emptyList(),
    onChooseProfile: (String) -> Unit,
    onCancelProfiles: () -> Unit,
    onAnonymousSubmit: (String) -> Unit,

) {
    var url by remember { mutableStateOf(initialUrl) }
    var token by remember { mutableStateOf(initialToken) }
    var passwordMode by remember { mutableStateOf(initialToken.isBlank()) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var scanHint by remember { mutableStateOf<String?>(null) }

    // A scanned QR (or a musik:// link) replaces both fields; see LoginForm.withLink.
    fun useLink(link: ConnectLink) {
        onCancelProfiles()
        password = ""
        val next = LoginForm(url, token).withLink(link)
        url = next.form.url
        token = next.form.token
        if (next.signIn) {
            passwordMode = false
            scanHint = null
            onSubmit(next.form.url, next.form.token)
        } else {
            scanHint = strings(R.string.login_screen_1)
        }
    }

    // Taken out of the ViewModel before use, so a screen recreated afterwards
    // does not sign in a second time.
    LaunchedEffect(pendingLink) {
        if (pendingLink != null) consumeLink()?.let(::useLink)
    }

    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        val raw = result.contents ?: return@rememberLauncherForActivityResult // closed without a code
        val link = ConnectLink.parse(raw)
        if (link == null) scanHint = strings(R.string.login_screen_2) else useLink(link)
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Column(
        modifier = Modifier
            .widthIn(max = 560.dp).fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = androidx.compose.ui.graphics.Color(0xFF211C19),
            modifier = Modifier.padding(bottom = 20.dp)) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(ru.rainedev.sirinmusic.R.drawable.ic_launcher_foreground),
                contentDescription = "Sirin Music", modifier = Modifier.size(88.dp))
        }
        Text("Sirin Music", style = MaterialTheme.typography.displaySmall)
        Text(
            strings(R.string.login_screen_3),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp),
        )

        OutlinedButton(
            onClick = {
                scanner.launch(
                    ScanOptions()
                        .setCaptureActivity(ru.rainedev.sirinmusic.ui.qr.QrScanActivity::class.java)
                        .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                        .setPrompt(strings(R.string.login_screen_4))
                        .setBeepEnabled(false)
                        .setOrientationLocked(false)
                )
            },
            enabled = !checking,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(strings(R.string.login_screen_5))
        }
        Text(
            scanHint ?: strings(R.string.login_screen_6),
            color = if (scanHint != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 8.dp),
        )

        if (profiles.isNotEmpty()) {
            Text(strings(R.string.account_activity_19), style = MaterialTheme.typography.titleLarge)
            Text(strings(R.string.login_screen_7),
                style = MaterialTheme.typography.bodyMedium)
            profiles.forEach { profile ->
                OutlinedButton(onClick = { onChooseProfile(profile.id) }, enabled = !checking,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(profile.name + if (profile.isDefault) strings(R.string.account_activity_17) else "")
                }
            }
            TextButton(onClick = { onCancelProfiles(); password = "" }, enabled = !checking) { Text(strings(R.string.about_activity_2)) }
        } else {
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text(strings(R.string.server_settings_activity_10)) },
            supportingText = { Text(strings(R.string.login_screen_8)) },
            singleLine = true,
            enabled = !checking,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )

        TextButton(onClick = { passwordMode = !passwordMode; password = ""; scanHint = null },
            enabled = !checking) {
            Text(if (passwordMode) strings(R.string.login_screen_9) else strings(R.string.login_screen_10))
        }
        if (passwordMode) {
            OutlinedTextField(value = username, onValueChange = { username = it },
                label = { Text(strings(R.string.login_screen_11)) },
                supportingText = { Text(strings(R.string.login_screen_12)) },
                singleLine = true, enabled = !checking, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = password, onValueChange = { password = it },
                label = { Text(strings(R.string.account_activity_9)) }, singleLine = true, enabled = !checking,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
        } else {
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text(strings(R.string.account_activity_33)) },
            enabled = !checking,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        )

        }
        }
        if (error != null) {
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        if (profiles.isEmpty()) Button(
            onClick = {
                if (passwordMode) onPasswordSubmit(url, username, password) else onSubmit(url, token)
            },
            enabled = !checking && url.isNotBlank() && (if (passwordMode) password.isNotEmpty() else token.isNotBlank()),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
        ) {
            if (checking) {
                CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
            }
            Text(strings(R.string.login_screen_13))
        }
        if (profiles.isNotEmpty() && checking) CircularProgressIndicator(Modifier.padding(top = 12.dp))
        if (profiles.isEmpty()) TextButton(onClick = { onAnonymousSubmit(url) },
            enabled = !checking && url.isNotBlank()) {
            Text(strings(R.string.login_screen_14))
        }
    }
    }
}
