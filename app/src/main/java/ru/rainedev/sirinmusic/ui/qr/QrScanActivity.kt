package ru.rainedev.sirinmusic.ui.qr

import ru.rainedev.sirinmusic.R
import ru.rainedev.sirinmusic.strings
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.FlashlightOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.rainedev.sirinmusic.SirinApp
import ru.rainedev.sirinmusic.data.ConnectLink
import com.google.zxing.BarcodeFormat
import com.google.zxing.client.android.Intents
import com.journeyapps.barcodescanner.CameraPreview
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import kotlinx.coroutines.launch
import ru.rainedev.sirinmusic.ui.theme.SirinMusicTheme

/** Камера привязана к видимому экрану; результат совместим с существующим ScanContract. */
class QrScanActivity : ru.rainedev.sirinmusic.SirinActivity() {
    private var completed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by (application as SirinApp).settings.appearance.collectAsStateWithLifecycle()
            SirinMusicTheme(settings) { Scanner() }
        }
    }

    private fun complete(text: String): Boolean {
        if (ConnectLink.parse(text) == null) return false
        if (!completed) {
            completed = true
            setResult(Activity.RESULT_OK, Intent()
                .putExtra(Intents.Scan.RESULT, text)
                .putExtra(Intents.Scan.RESULT_FORMAT, BarcodeFormat.QR_CODE.name))
            finish()
        }
        return true
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Scanner() {
        var permitted by remember { mutableStateOf(ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
        var torch by rememberSaveable { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        var manual by rememberSaveable { mutableStateOf(false) }
        var typed by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permitted = it }
        val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) scope.launch {
                busy = true
                try {
                    val text = readQrImage(this@QrScanActivity, uri)
                    if (text == null || !complete(text)) error = strings(R.string.qr_scan_activity_1)
                } finally { busy = false }
            }
        }
        val camera = remember {
            DecoratedBarcodeView(this).apply {
                viewFinder.visibility = View.GONE
                statusView.visibility = View.GONE
                barcodeView.addStateListener(object : CameraPreview.StateListener {
                    override fun previewSized() = Unit
                    override fun previewStarted() = Unit
                    override fun previewStopped() = Unit
                    override fun cameraClosed() = Unit
                    override fun cameraError(failure: Exception) {
                        error = strings(R.string.qr_scan_activity_2)
                    }
                })
                barcodeView.decoderFactory = DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE))
                decodeContinuous(object : BarcodeCallback {
                    override fun barcodeResult(result: BarcodeResult) {
                        if (manual || busy || completed) return
                        if (!complete(result.text)) error = strings(R.string.qr_scan_activity_3)
                    }
                })
            }
        }
        LifecycleResumeEffect(permitted, manual, busy) {
            if (permitted && !manual && !busy) camera.resume()
            onPauseOrDispose { camera.pause() }
        }
        LaunchedEffect(torch, permitted) {
            if (permitted) { if (torch) camera.setTorchOn() else camera.setTorchOff() }
        }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(strings(R.string.qr_scan_activity_4)) },
                    navigationIcon = { IconButton(onClick = { finish() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, strings(R.string.about_activity_2)) } },
                    actions = {
                        if (packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) IconButton(
                            enabled = permitted, onClick = { torch = !torch },
                        ) { Icon(if (torch) Icons.Outlined.FlashlightOff else Icons.Outlined.FlashlightOn,
                            if (torch) strings(R.string.qr_scan_activity_5) else strings(R.string.qr_scan_activity_6)) }
                    },
                )
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(strings(R.string.qr_scan_activity_7), style = MaterialTheme.typography.titleMedium)
                Box(Modifier.fillMaxWidth().weight(1f).clip(MaterialTheme.shapes.extraLarge).background(Color.Black), contentAlignment = Alignment.Center) {
                    if (permitted) AndroidView(factory = { camera }, modifier = Modifier.fillMaxSize())
                    else Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(strings(R.string.qr_scan_activity_8), color = Color.White)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { cameraPermission.launch(Manifest.permission.CAMERA) }) { Text(strings(R.string.qr_scan_activity_9)) }
                    }
                    if (busy) CircularProgressIndicator()
                }
                if (!manual) error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                OutlinedButton(
                    enabled = !busy, modifier = Modifier.fillMaxWidth(),
                    onClick = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                ) { Text(strings(R.string.qr_scan_activity_10)) }
                TextButton(modifier = Modifier.fillMaxWidth(), onClick = { manual = true; error = null }) { Text(strings(R.string.qr_scan_activity_11)) }
            }
        }
        if (manual) AlertDialog(
            onDismissRequest = { manual = false }, title = { Text(strings(R.string.qr_scan_activity_12)) },
            text = {
                Column {
                    OutlinedTextField(value = typed, onValueChange = { typed = it; error = null },
                        label = { Text(strings(R.string.qr_scan_activity_13)) }, placeholder = { Text("musik://connect?…") }, singleLine = true,
                        isError = error != null)
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { TextButton(onClick = { if (!complete(typed)) error = strings(R.string.qr_scan_activity_14) }) { Text(strings(R.string.qr_scan_activity_15)) } },
            dismissButton = { TextButton(onClick = { manual = false; error = null }) { Text(strings(R.string.account_activity_71)) } },
        )
    }
}
