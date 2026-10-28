package ru.rainedev.sirinmusic

import ru.rainedev.sirinmusic.R
import ru.rainedev.sirinmusic.strings
import android.content.Intent
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.rainedev.sirinmusic.data.*
import ru.rainedev.sirinmusic.ui.theme.SirinMusicTheme

class AccountActivity : ru.rainedev.sirinmusic.SirinActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val app = application as SirinApp
        val session = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = AccountSession(app.settings.baseUrl) as T
        })[AccountSession::class.java]
        setContent {
            val appearance by app.settings.appearance.collectAsStateWithLifecycle()
            SirinMusicTheme(appearance) { AccountScreen(app, session.api, ::finish) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountScreen(app: SirinApp, api: AccountApi?, onBack: () -> Unit) {
    var me by remember { mutableStateOf<AccountMe?>(null) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var newProfile by remember { mutableStateOf("") }
    var profileName by remember { mutableStateOf("") }
    var devices by remember { mutableStateOf<List<ManagedDevice>>(emptyList()) }
    var users by remember { mutableStateOf<List<ManagedUser>>(emptyList()) }
    var invites by remember { mutableStateOf<List<ManagedInvitation>>(emptyList()) }
    var deviceName by remember { mutableStateOf(strings(R.string.account_activity_1)) }
    var deviceProfile by remember { mutableStateOf("") }
    var issuedDevice by remember { mutableStateOf<IssuedDevice?>(null) }
    var issuedInvite by remember { mutableStateOf<IssuedInvitation?>(null) }
    var email by remember { mutableStateOf("") }
    var hours by remember { mutableStateOf("24") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var confirmation by remember { mutableStateOf<Pair<String, suspend () -> Unit>?>(null) }
    var qr by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var phoneUrl by remember { mutableStateOf(api?.origin.orEmpty()) }
    var revealToken by remember { mutableStateOf(false) }
    suspend fun reload() {
        val client = api ?: return
        val account = client.me()
        me = account.takeIf { it.ok && it.multiUser }
        if (me == null) return
        profileName = account.activeProfile?.name.orEmpty()
        if (account.profiles.none { it.id == deviceProfile }) deviceProfile = account.activeProfile?.id.orEmpty()
        devices = client.devices()
        if ("admin" in account.user.roles) {
            users = client.users(); invites = client.invitations()
            phoneUrl = client.settings().phone.urls.firstOrNull() ?: client.origin
        } else { users = emptyList(); invites = emptyList(); phoneUrl = client.origin }
    }
    val action = ru.rainedev.sirinmusic.ui.rememberScreenAction(
        onStart = { busy = true; error = null; notice = null },
        onError = { e ->
            error = e.message ?: strings(R.string.account_activity_2)
            if ((e as? ApiException)?.code == 401) { me = null; issuedDevice = null; issuedInvite = null; qr = null }
        },
        onFinish = { busy = false },
    )
    fun copy(text: String, secret: Boolean = false) {
        ru.rainedev.sirinmusic.ui.copyText(app, text, "Sirin Music", secret)
        notice = strings(R.string.account_activity_3)
    }
    LaunchedEffect(api) { if (api?.base?.isHttps == true) action { reload() } }
    Scaffold(topBar = { TopAppBar(title = { Text(strings(R.string.account_activity_4)) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, strings(R.string.about_activity_2)) } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }; notice?.let { Text(it) }
            if (api == null || !api.base.isHttps) {
                Text(strings(R.string.account_activity_5))
            } else if (me == null) {
                Text(strings(R.string.account_activity_6), style = MaterialTheme.typography.titleLarge)
                Text(strings(R.string.account_activity_7))
                OutlinedTextField(username, { username = it }, label = { Text(strings(R.string.account_activity_8)) }, enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(password, { password = it }, label = { Text(strings(R.string.account_activity_9)) }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password), enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(onClick = { action { api.login(username, password); password = ""; reload() } }, enabled = !busy) { Text(strings(R.string.account_activity_10)) }
            } else {
                val account = me!!
                Text(account.user.displayName, style = MaterialTheme.typography.headlineSmall)
                Text(strings(R.string.account_activity_11))
                if (account.localAccount) {
                    Text(strings(R.string.account_activity_12), style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(currentPassword, { currentPassword = it }, label = { Text(strings(R.string.account_activity_13)) }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password), enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(newPassword, { newPassword = it }, label = { Text(strings(R.string.account_activity_14)) }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password), enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { action { api.password(currentPassword, newPassword); currentPassword = ""; newPassword = ""; notice = strings(R.string.account_activity_15) } }, enabled = !busy && newPassword.length in 8..256 && currentPassword.isNotEmpty()) { Text(strings(R.string.account_activity_12)) }
                }
                HorizontalDivider(); Text(strings(R.string.account_activity_16), style = MaterialTheme.typography.titleLarge)
                account.profiles.forEach { profile ->
                    Row { RadioButton(selected = profile.id == account.activeProfile?.id, enabled = !busy, onClick = { action { api.activateProfile(profile.id); reload() } }); TextButton(onClick = { action { api.activateProfile(profile.id); reload() } }, enabled = !busy) { Text(profile.name + if (profile.isDefault) strings(R.string.account_activity_17) else "") } }
                }
                Text(strings(R.string.account_activity_18))
                OutlinedButton(onClick = { action {
                    val profile = account.activeProfile ?: kotlin.error(strings(R.string.account_activity_19))
                    val issue = api.createDevice("Sirin Music Android", profile.id)
                    MusikApi(ServerConnection(api.origin, issue.token, app.settings.clientId)).validateConnection()
                    app.playback.stop(); app.settings.setConnection(api.origin, issue.token)
                    devices = api.devices(); notice = strings(R.string.account_activity_20, profile.name)
                } }, enabled = !busy && account.activeProfile != null) { Text(strings(R.string.account_activity_21)) }
                OutlinedTextField(newProfile, { newProfile = it }, label = { Text(strings(R.string.account_activity_22)) }, enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(onClick = { action { api.createProfile(newProfile); newProfile = ""; reload() } }, enabled = !busy && newProfile.isNotBlank()) { Text(strings(R.string.account_activity_23)) }
                OutlinedTextField(profileName, { profileName = it }, label = { Text(strings(R.string.account_activity_24)) }, enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row {
                    TextButton(onClick = { action { api.renameProfile(account.activeProfile!!.id, profileName); reload() } }, enabled = !busy && account.activeProfile != null && profileName.isNotBlank()) { Text(strings(R.string.account_activity_25)) }
                    TextButton(onClick = { confirmation = strings(R.string.account_activity_26) to { api.deleteProfile(account.activeProfile!!.id); reload() } }, enabled = !busy && account.activeProfile?.isDefault == false) { Text(strings(R.string.account_activity_27)) }
                }
                HorizontalDivider(); Text(strings(R.string.account_activity_28), style = MaterialTheme.typography.titleLarge)
                Text(strings(R.string.account_activity_29))
                OutlinedTextField(deviceName, { deviceName = it }, label = { Text(strings(R.string.account_activity_30)) }, enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth())
                account.profiles.forEach { profile -> Row { RadioButton(selected = deviceProfile == profile.id, enabled = !busy, onClick = { deviceProfile = profile.id }); TextButton(onClick = { deviceProfile = profile.id }, enabled = !busy) { Text(profile.name + if (profile.isDefault) strings(R.string.account_activity_17) else "") } } }
                Button(onClick = { action { issuedDevice = api.createDevice(deviceName, deviceProfile); revealToken = false; devices = api.devices() } }, enabled = !busy && deviceName.isNotBlank() && deviceProfile.isNotBlank()) { Text(strings(R.string.account_activity_31)) }
                issuedDevice?.let { issued ->
                    Text(strings(R.string.account_activity_32))
                    OutlinedTextField(issued.token, {}, readOnly = true, label = { Text(strings(R.string.account_activity_33)) }, visualTransformation = if (revealToken) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    Row {
                        TextButton(onClick = { revealToken = !revealToken }) { Text(if (revealToken) strings(R.string.account_activity_34) else strings(R.string.account_activity_35)) }
                        TextButton(onClick = { copy(issued.token, true) }) { Text(strings(R.string.account_activity_36)) }
                        TextButton(onClick = { action { qr = createConnectionQr(phoneUrl, issued.token) } }, enabled = !busy) { Text("QR") }
                    }
                }
                devices.forEach { device ->
                    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                        Text(device.name, style = MaterialTheme.typography.titleMedium)
                        Text(strings(R.string.account_activity_37, device.profileName)); Text(strings(R.string.account_activity_38, accountDate(device.createdAt)))
                        Text(strings(R.string.account_activity_40, device.lastUsedAt?.let(::accountDate) ?: strings(R.string.account_activity_39)))
                        Text(strings(R.string.account_activity_41, accountDate(device.expiresAt)))
                        if (device.revokedAt != null) Text(strings(R.string.account_activity_42)) else Row {
                            TextButton(onClick = { confirmation = strings(R.string.account_activity_43, device.name) to { issuedDevice = api.replaceDevice(device.id); revealToken = false; devices = api.devices() } }, enabled = !busy) { Text(strings(R.string.account_activity_44)) }
                            TextButton(onClick = { confirmation = strings(R.string.account_activity_45, device.name) to { api.revokeDevice(device.id); devices = api.devices() } }, enabled = !busy) { Text(strings(R.string.account_activity_46)) }
                        }
                    } }
                }
                if ("admin" in account.user.roles) {
                    HorizontalDivider(); Text(strings(R.string.account_activity_47), style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(email, { email = it }, label = { Text(strings(R.string.account_activity_48)) }, enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(hours, { hours = it }, label = { Text(strings(R.string.account_activity_49)) }, enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { action { issuedInvite = api.invite(email, hours.toIntOrNull() ?: kotlin.error(strings(R.string.account_activity_50))); invites = api.invitations() } }, enabled = !busy) { Text(strings(R.string.account_activity_51)) }
                    issuedInvite?.let { invite -> Text(invite.url); TextButton(onClick = { copy(invite.url, true) }) { Text(strings(R.string.account_activity_52)) } }
                    Text(strings(R.string.account_activity_53), style = MaterialTheme.typography.titleLarge)
                    users.forEach { user -> key(user.id, user.status, user.admin) {
                        var active by remember { mutableStateOf(user.status == "active") }; var admin by remember { mutableStateOf(user.admin) }
                        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                            Text(user.displayName, style = MaterialTheme.typography.titleMedium); Text(strings(R.string.account_activity_54, user.id, user.profiles))
                            Row { Checkbox(active, { active = it }, enabled = !busy); Text(strings(R.string.account_activity_55)) }
                            Row { Checkbox(admin, { admin = it }, enabled = !busy); Text(strings(R.string.account_activity_56)) }
                            Button(onClick = { confirmation = strings(R.string.account_activity_57, user.displayName) to { api.updateUser(user.id, active, admin); reload() } }, enabled = !busy && (active != (user.status == "active") || admin != user.admin)) { Text(strings(R.string.account_activity_58)) }
                        } }
                    } }
                    Text(strings(R.string.account_activity_59), style = MaterialTheme.typography.titleLarge)
                    invites.forEach { invite -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                        Text(invite.email.ifBlank { strings(R.string.account_activity_60) })
                        Text(strings(R.string.account_activity_65, when { invite.revokedAt.isNotBlank() -> strings(R.string.account_activity_61); invite.consumedAt.isNotBlank() -> strings(R.string.account_activity_62); runCatching { java.time.Instant.parse(invite.expiresAt).isBefore(java.time.Instant.now()) }.getOrDefault(false) -> strings(R.string.account_activity_63); else -> strings(R.string.account_activity_64) }, accountDate(invite.expiresAt)))
                        if (invite.revokedAt.isBlank() && invite.consumedAt.isBlank()) TextButton(onClick = { confirmation = strings(R.string.account_activity_66) to { api.revokeInvitation(invite.id); invites = api.invitations() } }, enabled = !busy) { Text(strings(R.string.account_activity_46)) }
                    } } }
                }
                HorizontalDivider()
                TextButton(onClick = { action { reload() } }, enabled = !busy) { Text(strings(R.string.account_activity_67)) }
                TextButton(onClick = { action { api.logout(); me = null; issuedDevice = null; issuedInvite = null; qr = null; password = ""; currentPassword = ""; newPassword = "" } }, enabled = !busy) { Text(strings(R.string.account_activity_68)) }
            }
        }
    }
    confirmation?.let { (text, operation) -> AlertDialog(onDismissRequest = { confirmation = null }, title = { Text(strings(R.string.account_activity_69)) }, text = { Text(text) }, confirmButton = { TextButton(onClick = { confirmation = null; action { operation() } }) { Text(strings(R.string.account_activity_70)) } }, dismissButton = { TextButton(onClick = { confirmation = null }) { Text(strings(R.string.account_activity_71)) } }) }
    qr?.let { bitmap -> AlertDialog(onDismissRequest = { qr = null }, title = { Text(strings(R.string.account_activity_72)) }, text = { Column { Text(phoneUrl); Image(bitmap.asImageBitmap(), strings(R.string.account_activity_73), Modifier.fillMaxWidth().aspectRatio(1f)); Text(strings(R.string.account_activity_74)) } }, confirmButton = { TextButton(onClick = { qr = null }) { Text(strings(R.string.account_activity_75)) } }) }
}

private fun accountDate(value: String): String = runCatching {
    java.time.Instant.parse(value).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofLocalizedDateTime(java.time.format.FormatStyle.MEDIUM).withLocale(appLocale()))
}.getOrDefault(value)
