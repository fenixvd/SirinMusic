package ru.rainedev.sirinmusic.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

private class ToastPresenter(private val context: Context) {
    private var current: Toast? = null
    fun show(message: String) {
        close()
        current = Toast.makeText(context, message, Toast.LENGTH_LONG).also { it.show() }
    }
    fun close() { current?.cancel(); current = null }
}

@Composable
internal fun FeedbackToast(
    message: String?,
    playbackError: String?,
    onMessageConsumed: () -> Unit,
    onErrorConsumed: (String) -> Unit,
) {
    val context = LocalContext.current
    val presenter = remember(context) { ToastPresenter(context) }
    DisposableEffect(presenter) { onDispose { presenter.close() } }
    LaunchedEffect(message, playbackError, presenter) {
        val messages = listOfNotNull(playbackError, message).distinct()
        if (messages.isNotEmpty()) {
            presenter.show(messages.joinToString("\n"))
            if (message != null) onMessageConsumed()
            playbackError?.let(onErrorConsumed)
        }
    }
}
