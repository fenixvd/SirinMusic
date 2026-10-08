package ru.rainedev.sirinmusic.ui

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
) {
    var url by remember { mutableStateOf(initialUrl) }
    var token by remember { mutableStateOf(initialToken) }
    var scanHint by remember { mutableStateOf<String?>(null) }

    // A scanned QR (or a musik:// link) replaces both fields; see LoginForm.withLink.
    fun useLink(link: ConnectLink) {
        val next = LoginForm(url, token).withLink(link)
        url = next.form.url
        token = next.form.token
        if (next.signIn) {
            scanHint = null
            onSubmit(next.form.url, next.form.token)
        } else {
            scanHint = "В QR-коде только адрес сервера — введи API-токен."
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
        if (link == null) scanHint = "Это не QR-код musik" else useLink(link)
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
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.padding(bottom = 20.dp)) {
            Icon(Icons.Rounded.MusicNote, null, Modifier.padding(20.dp).size(40.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Text("Sirin Music", style = MaterialTheme.typography.displaySmall)
        Text(
            "Подключение к своему musik",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp),
        )

        OutlinedButton(
            onClick = {
                scanner.launch(
                    ScanOptions()
                        .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                        .setPrompt("Наведи камеру на QR-код: веб-интерфейс musik → Профиль → Настройки")
                        .setBeepEnabled(false)
                        .setOrientationLocked(false)
                )
            },
            enabled = !checking,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Сканировать QR-код")
        }
        Text(
            scanHint ?: "Или введи данные вручную:",
            color = if (scanHint != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 8.dp),
        )

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Адрес сервера") },
            supportingText = { Text("например https://music.example.org или http://192.168.1.10:8787") },
            singleLine = true,
            enabled = !checking,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("API-токен") },
            enabled = !checking,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        )

        if (error != null) {
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        Button(
            onClick = { onSubmit(url, token) },
            enabled = !checking && url.isNotBlank() && token.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
        ) {
            if (checking) {
                CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
            }
            Text("Проверить и войти")
        }
    }
    }
}
