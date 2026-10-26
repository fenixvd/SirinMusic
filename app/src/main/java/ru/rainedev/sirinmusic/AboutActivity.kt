package ru.rainedev.sirinmusic

import ru.rainedev.sirinmusic.R
import ru.rainedev.sirinmusic.strings
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.rainedev.sirinmusic.ui.theme.SirinMusicTheme

class AboutActivity : ru.rainedev.sirinmusic.SirinActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        val app = application as SirinApp
        val info = packageManager.getPackageInfo(packageName, 0)
        setContent {
            val appearance by app.settings.appearance.collectAsStateWithLifecycle()
            SirinMusicTheme(appearance) {
                Scaffold(topBar = { TopAppBar(title = { Text(strings(R.string.about_activity_1)) }, navigationIcon = {
                    IconButton(onClick = ::finish) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, strings(R.string.about_activity_2)) }
                }) }) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                    Column(Modifier.widthIn(max = 760.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(shape = RoundedCornerShape(24.dp), color = Color(0xFF211C19)) {
                            Image(painterResource(R.drawable.ic_launcher_foreground), "Sirin Music", Modifier.size(108.dp))
                        }
                        Text("Sirin Music", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 16.dp))
                        Text(strings(R.string.about_activity_3, info.versionName), style = MaterialTheme.typography.bodyMedium)
                        HorizontalDivider(Modifier.padding(vertical = 24.dp))
                        Text("RaineDev", style = MaterialTheme.typography.titleLarge)
                        Text(strings(R.string.about_activity_4),
                            modifier = Modifier.padding(top = 12.dp))
                        TextButton(onClick = {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(strings(R.string.privacy_policy_url))))
                        }) { Text(strings(R.string.privacy_policy)) }
                        Text(strings(R.string.about_activity_5),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 24.dp))
                    }
                    }
                }
            }
        }
    }
}
