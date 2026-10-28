package ru.rainedev.sirinmusic

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.*
import ru.rainedev.sirinmusic.data.AccountApi

/** Account cookies live only for the lifetime of this screen's ViewModel. */
class AccountSession(baseUrl: String) : ViewModel() {
    val api = runCatching { AccountApi(baseUrl) }.getOrNull()
    override fun onCleared() {
        api?.let { client ->
            CoroutineScope(Dispatchers.IO).launch {
                try { withTimeoutOrNull(5_000) { runCatching { client.logout() } } }
                finally { client.close() }
            }
        }
    }
}
