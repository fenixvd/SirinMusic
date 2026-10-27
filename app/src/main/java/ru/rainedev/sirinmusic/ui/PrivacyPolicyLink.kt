package ru.rainedev.sirinmusic.ui

import android.widget.Toast
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import ru.rainedev.sirinmusic.R
import ru.rainedev.sirinmusic.strings

/** Public policy; no server token, account session, or app data is added to the URL. */
@Composable
internal fun PrivacyPolicyLink(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val url = strings(R.string.privacy_policy_url)
    TextButton(modifier = modifier, onClick = {
        try { uriHandler.openUri(url) }
        catch (_: Exception) { Toast.makeText(context, strings(R.string.privacy_policy_open_failed), Toast.LENGTH_LONG).show() }
    }) { Text(strings(R.string.privacy_policy)) }
}
